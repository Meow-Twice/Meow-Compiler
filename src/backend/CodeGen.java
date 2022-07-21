package backend;

import frontend.semantic.Initial;
import lir.*;
import manage.Manager;
import mir.*;
import mir.type.Type;

import java.util.*;

import static lir.Arm.Cond.*;
import static lir.Arm.Regs.GPRs.*;
import static mir.type.DataType.I32;

public class CodeGen {

    public static final CodeGen CODEGEN = new CodeGen();
    public static boolean _DEBUG_OUTPUT_MIR_INTO_COMMENT = true;

    // 当前的Machine.McFunction
    private static Machine.McFunction curMachineFunc;

    // 当前的mir.Function
    private static mir.Function curFunc;

    // mir中func的名字到Function的Map
    private HashMap<String, Function> midFuncMap;

    // 每个LLVM IR为一个值, 需要有一个虚拟寄存器(或常数->立即数)与之一一对应
    // Value到Operand的Map
    public HashMap<Value, Machine.Operand> value2opd;
    public HashMap<GlobalVal.GlobalValue, Arm.Glob> globptr2globOpd = new HashMap<>();
    public final ArrayList<Arm.Glob> globList = new ArrayList<>();

    // Operand到Value的Map, 这个map不应该存在 , 消phi时产生的move可能造成一条Instr对应多个opd
    // public HashMap<Machine.Operand, Value> opd2value;

//    public ArrayList<Machine.McFunction> mcFuncList;

    // 如名
    public HashMap<Function, Machine.McFunction> func2mcFunc;

    // 如名
    public HashMap<BasicBlock, Machine.Block> bb2mb;

    // 全局变量
    private HashMap<GlobalVal.GlobalValue, Initial> globalMap;
    private Machine.Block curMB;
    // private int virtual_cnt = 0;
    private int curStackTop = 0;

    private HashMap<Instr.Load, Instr.Alloc> load2alloc = new HashMap<>();

    // div+mod optimize
    public static class Multiplier {
        long m;
        int l;
        int sh_post;

        public Multiplier(long m, int l, int sh_post) {
            this.m = m;
            this.l = l;
            this.sh_post = sh_post;
        }
    }

    public Multiplier chooseMultiplier(int d, int prec) {
      //  prec = 32;
        int N = 32;
        assert d != 0;
        assert prec >= 1 && prec <= N;

        int l = (int) Math.ceil(Math.log(d) / Math.log(2));
        int sh_post = l;
        long m_low = ((long) 1 << (N + l)) / d;
        long m_high = (((long) 1 << (N + l)) + ((long) 1 << (N + l - prec))) / d;
        while (((m_low >> 1) < (m_high >> 1)) && sh_post > 0) {
            m_low >>= 1;
            m_high >>= 1;
            sh_post -= 1;
        }

        return new Multiplier(m_high, l, sh_post);

    }

    private CodeGen() {
        curFunc = null;
        curMachineFunc = null;
        midFuncMap = Manager.MANAGER.getFunctions();
        globalMap = Manager.MANAGER.globals;
        value2opd = new HashMap<>();
        // opd2value = new HashMap<>();
//        mcFuncList = new ArrayList<>();
        func2mcFunc = new HashMap<>();
        bb2mb = new HashMap<>();
    }

    public void gen() {
        // curMachineFunc = new Machine.McFunction();
        genGlobal();
        // TODO

        for (Function func : midFuncMap.values()) {
            // if (func.isExternal) {
            Machine.McFunction mcFunc = new Machine.McFunction(func);
            func2mcFunc.put(func, mcFunc);
            // }
        }
        for (Function func : midFuncMap.values()) {
            // Machine.McFunction mcFunc = new Machine.McFunction(func);
            // func2mcFunc.put(func, mcFunc);
            if (func.isExternal) {
                continue;
            }
            curMachineFunc = func2mcFunc.get(func);
            Machine.Program.PROGRAM.funcList.insertAtEnd(curMachineFunc);
            curFunc = func;
            boolean isMain = false;
            if (curFunc.getName().equals("main")) {
                isMain = true;
                Machine.Program.PROGRAM.mainMcFunc = curMachineFunc;
            }
            // curMachineFunc = mcFunc;
            curMachineFunc.clearVRCount();
            value2opd = new HashMap<>();

            BasicBlock bb = func.getBeginBB();
            BasicBlock endBB = func.getEnd();
            // 先造出来防止br找不到目标
            while (!bb.equals(endBB)) {
                Machine.Block mb = new Machine.Block(bb);
                bb.setMB(mb);
                bb2mb.put(bb, mb);
                bb = (BasicBlock) bb.getNext();
            }
            bb = func.getBeginBB();
            curMB = bb.getMb();
            // 这里不可使用单例
            Machine.Operand rOp = new Machine.Operand(I32, 0);
            Machine.Operand mvDst = newVR();
            MIMove mv = new MIMove(mvDst, rOp, curMB);
            mv.setNeedFix(STACK_FIX.VAR_STACK);
            new MIBinary(MachineInst.Tag.Sub, Arm.Reg.getR(sp), Arm.Reg.getR(sp), mvDst, curMB);
            if (!isMain) {
                dealParam();
            }
            // 改写为循环加运行速度
            nextBBList = new LinkedList<>();
            nextBBList.push(bb);
            while (nextBBList.size() > 0) {
                BasicBlock visitBB = nextBBList.pop();
                genBB(visitBB);
            }
        }
    }

    LinkedList<BasicBlock> nextBBList;

    private void dealParam() {
        int idx = 0;
        for (Function.Param param : curFunc.getParams()) {
            Machine.Operand opd = curMachineFunc.newVR();
            value2opd.put(param, opd);
            if (idx <= 3) {
                new MIMove(opd, Arm.Reg.getR(idx), curMB);
            } else {
                // 这里因为无法确认栈的大小(参数栈空间, 所用寄存器push和pop的栈空间, 数组申请栈空间, 寄存器分配时溢出所需栈空间)是否超过了立即数编码, 因此一律用move指令处理
                Machine.Operand offImm = new Machine.Operand(I32, 4 * (3 - idx));
                Machine.Operand dst = newVR();
                MIMove mv = new MIMove(dst, offImm, curMB);
                mv.setNeedFix(STACK_FIX.TOTAL_STACK);
                // 栈顶向下的偏移, 第四个参数 -4, 第五个参数 -8 ...修的时候只需要把这个立即数的值取出来加上getStackSize获取的栈大小即可
                new MILoad(opd, Arm.Reg.getR(sp), dst, curMB);
                curMachineFunc.addParamStack(4);
            }
            idx++;
        }
    }

    public enum STACK_FIX {
        NO_NEED, // 无需修栈
        VAR_STACK, // 函数push语句后的第一条的sub sp, sp, varStack用, 函数pop语句前的最后一条add sp, sp, varStack用
        ONLY_PARAM, // 函数调用前的sub sp, sp, paramStack用, 函数调用后的add sp, sp, paramStack用
        TOTAL_STACK // 函数有超过四个的参数时ldr参数用(move vrx, #xxx那条)
    }

    HashSet<Machine.Block> dfsBBSet = new HashSet<>();

    public void genBB(BasicBlock bb) {
        curMB = bb.getMb();
        dfsBBSet.add(curMB);
        curMB.setMcFunc(curMachineFunc);
        // ArrayList<BasicBlock> nextBlockList = new ArrayList<>();
        Instr instr = bb.getBeginInstr();
        while (!instr.equals(bb.getEnd())) {
            if (_DEBUG_OUTPUT_MIR_INTO_COMMENT) {
                new MIComment(instr.toString(), curMB);
            }
            switch (instr.tag) {
                case value, func, bb -> throw new AssertionError("Damaged wrong: try gen: " + instr);
                case bino -> genBinaryInst((Instr.Alu) instr);
                case jump -> {
                    Machine.Block mb = ((Instr.Jump) instr).getTarget().getMb();
                    curMB.succMB.add(mb);
                    if (!dfsBBSet.contains(mb)) nextBBList.push(mb.bb);
                    new MIJump(mb, curMB);
                }
                case icmp, fcmp -> genCmp(instr);
                case branch -> {
                    Arm.Cond cond;
                    Instr.Branch brInst = (Instr.Branch) instr;
                    Instr condValue = (Instr) brInst.getCond();
                    Machine.Block trueBlock = brInst.getThenTarget().getMb();
                    Machine.Block falseBlock = brInst.getElseTarget().getMb();
                    curMB.succMB.add(trueBlock);
                    curMB.succMB.add(falseBlock);
                    if (!dfsBBSet.contains(falseBlock)) nextBBList.push(falseBlock.bb);
                    if (!dfsBBSet.contains(trueBlock)) nextBBList.push(trueBlock.bb);
                    CMPAndArmCond t = cmpInst2MICmpMap.get(condValue);
                    if (t != null) {
                        curMB.firstMIForBJ = t.CMP;
                        cond = t.ArmCond;
                    } else {
                        Machine.Operand condVR = getVR_no_imm(condValue);
                        // TODO fcmp无法直接转换?
                        // cond = Arm.Cond.values()[((Instr.Icmp) condValue).getOp().ordinal()];
                        Machine.Operand dst = newVR();
                        Machine.Operand immOpd = new Machine.Operand(I32, 0);
                        if (curMB.firstMIForBJ != null) {
                            new MIMove(dst, immOpd, curMB.firstMIForBJ);
                            new MICompare(condVR, dst, curMB);
                        } else {
                            new MIMove(dst, immOpd, curMB);
                            curMB.firstMIForBJ = new MICompare(condVR, dst, curMB);
                        }
                        cond = Arm.Cond.Ne;
                    }
                    new MIBranch(cond, trueBlock, falseBlock, curMB);
                    // new MIJump()
                }
                case fneg -> {
                    // TODO
                    Instr.Fneg fneginst = (Instr.Fneg) instr;
                    Value value = fneginst.getRVal1();
                    // 0 sub value ->dst
                    Machine.Operand lOpd = getVR_may_imm(new Constant.ConstantFloat(0));
                    Machine.Operand rOpd = getVR_may_imm(value);
                    Machine.Operand dVR = getVR_no_imm(fneginst);
                    new MIBinary(MachineInst.Tag.FSub, dVR, lOpd, rOpd, curMB);

                }
                case ret -> {
                    Instr.Return returnInst = (Instr.Return) instr;
                    if (returnInst.hasValue()) {
                        Machine.Operand retOpd = getVR_may_imm(returnInst.getRetValue());
                        curMB.firstMIForBJ = new MIMove(Arm.Reg.getR(r0), retOpd, curMB);
                    }
                    Machine.Operand rOp = new Machine.Operand(I32, 0);
                    Machine.Operand mvDst = newVR();
                    MIMove mv = new MIMove(mvDst, rOp, curMB);
                    mv.setNeedFix(STACK_FIX.VAR_STACK);
                    new MIBinary(MachineInst.Tag.Add, Arm.Reg.getR(sp), Arm.Reg.getR(sp), mvDst, curMB);
                    // miBinary.setNeedFix(STACK_FIX.VAR_STACK);
                    new MIReturn(curMB);
                }
                case zext -> {
                    Machine.Operand dst = getVR_no_imm(instr);
                    Machine.Operand src = getVR_may_imm(((Instr.Zext) instr).getRVal1());
                    // 现阶段应该已经在上一个cmp生成好了并转移到虚拟寄存器中了, 应该不用管, 待优化
                    new MIMove(dst, src, curMB);
                }
                case fptosi -> {
                }
                case sitofp -> {
                }
                case alloc -> {
                    Instr.Alloc allocInst = (Instr.Alloc) instr;
                    Type contentType = allocInst.getContentType();
                    if (contentType.isPointerType()) {
                        break;
                    }
                    // 这里已经不可能Alloc一个Int或者Float了
                    assert contentType.isArrType();
                    Machine.Operand addr = getVR_no_imm(allocInst);
                    Machine.Operand spReg = Arm.Reg.getR(sp);
                    Machine.Operand offset = new Machine.Operand(I32, curMachineFunc.getVarStack());
                    Machine.Operand mvDst = newVR();
                    new MIMove(mvDst, offset, curMB);
                    new MIBinary(MachineInst.Tag.Add, addr, spReg, mvDst, curMB);
                    // 栈空间移位
                    curMachineFunc.addVarStack(((Type.ArrayType) contentType).getFlattenSize() * 4);
                }
                case load -> {
                    Instr.Load loadInst = (Instr.Load) instr;
                    Value addrValue = loadInst.getPointer();
                    Machine.Operand addrOpd = getVR_from_ptr(addrValue);
                    Machine.Operand data = getVR_no_imm(instr);
                    // assert addrValue.getType().isPointerType();
                    // if(((Type.PointerType) addrValue.getType()).getInnerType().isPointerType()){
                    //     // 前端消多了,这个本来不应该消的,但是全消了
                    //     load2alloc.put(loadInst, (Instr.Alloc) addrValue);
                    //     break;
                    // }
                    Machine.Operand offsetOpd = new Machine.Operand(I32, 0);
                    new MILoad(data, addrOpd, offsetOpd, curMB);
                }
                case store -> {
                    Instr.Store storeInst = (Instr.Store) instr;
                    Machine.Operand data = getVR_may_imm(storeInst.getValue());
                    Machine.Operand addr = getVR_from_ptr(storeInst.getPointer());
                    Machine.Operand offset = new Machine.Operand(I32, 0);
                    new MIStore(data, addr, offset, curMB);
                }
                case gep -> {
                    Instr.GetElementPtr gep = (Instr.GetElementPtr) instr;
                    Value ptrValue = gep.getPtr();
                    int offsetCount = gep.getOffsetCount();
                    /**
                     * 当前的 baseType
                     */
                    Type curBaseType = ((Type.PointerType) ptrValue.getType()).getInnerType();
                    // if (curBaseType.isBasicType()) {
                    //     offsetCount = 1;
                    // } else {
                    //     assert curBaseType.isArrType();
                    //     offsetCount = ((Type.ArrayType) curBaseType).getDimSize();
                    // }
                    assert !ptrValue.isConstant();
                    Machine.Operand dstVR = getVR_no_imm(gep);
                    // Machine.Operand curAddrVR = getVR_no_imm(ptrValue);
                    Machine.Operand basePtrVR = getVR_from_ptr(ptrValue);
                    Machine.Operand curAddrVR = newVR();
                    new MIMove(curAddrVR, basePtrVR, curMB);
                    int totalConstOff = 0;
                    for (int i = 0; i < offsetCount; i++) {
                        Value curIdxValue = gep.getIdxValueOf(i);
                        int offUnit = 4;
                        if (curBaseType.isArrType()) {
                            offUnit = 4 * ((Type.ArrayType) curBaseType).getFlattenSize();
                            // offUnit = 4 * ((Type.ArrayType) curBaseType).getBaseFlattenSize();
                            curBaseType = ((Type.ArrayType) curBaseType).getBaseType();
                        }
                        if (curIdxValue.isConstantInt()) {
                            // 每一个常数下标都会累加到 totalConstOff 中
                            totalConstOff += offUnit * (int) ((Constant.ConstantInt) curIdxValue).getConstVal();
                            if (i == offsetCount - 1) {
                                // 这里的设计比较微妙
                                if (totalConstOff == 0) {
                                    new MIMove(dstVR, curAddrVR, curMB);
                                } else {
                                    Machine.Operand immVR = getImmVR(totalConstOff);
                                    new MIBinary(MachineInst.Tag.Add, dstVR, curAddrVR, immVR, curMB);
                                }
                                // 由于已经是最后一个偏移,所以不需要
                                // curAddrVR = dstVR;
                            }
                            /*} else if ((offUnit & (offUnit - 1)) == 0) {*/
                            // TODO
                        } else {
                            // TODO 是否需要避免寄存器分配时出现use的VR与def的VR相同的情况
                            assert !curIdxValue.isConstant();
                            Machine.Operand curIdxVR = getVR_no_imm(curIdxValue);
                            Machine.Operand offUnitImmVR = getImmVR(offUnit);
                            /**
                             * Fma Rd, Rm, Rs, Rn
                             * smmla:Rn + (Rm * Rs)[63:32] or smmls:Rd := Rn – (Rm * Rs)[63:32]
                             * mla:Rn + (Rm * Rs)[31:0] or mls:Rd := Rn – (Rm * Rs)[31:0]
                             * dst = acc +(-) lhs * rhs
                             */
                            // Machine.Operand tmpDst = newVR();
                            // new MIFma(true,false, tmpDst, curAddrVR, curIdxVR ,offUnitImmVR, curMB);
                            // curAddrVR = tmpDst;
                            if (i == offsetCount - 1) {
                                if (totalConstOff != 0) {
                                    Machine.Operand immVR = getImmVR(totalConstOff);
                                    new MIBinary(MachineInst.Tag.Add, curAddrVR, curAddrVR, immVR, curMB);
                                }
                                new MIFma(true, false, dstVR, curIdxVR, offUnitImmVR, curAddrVR, curMB);
                            } else {
                                new MIFma(true, false, curAddrVR, curIdxVR, offUnitImmVR, curAddrVR, curMB);
                            }
                        }
                    }

                }
                case bitcast -> {/*不用管*/}
                case call -> {
                    // move caller's r0-r3  to VR
                    Instr.Call call_inst = (Instr.Call) instr;
                    // TODO : 函数内部可能调用其他函数, 但是在函数内部已经没有了Caller使用哪些寄存器的信息, 目前影响未知, 可能有bug
                    // TODO: ayame解决方案是在 callee 头 push 和 callee 尾部 pop 那些 callee 要用到的寄存器, 暂定此方案
                    ArrayList<Value> param_list = call_inst.getParamList();
                    ArrayList<Machine.Operand> paramVRList = new ArrayList<>();
                    boolean r0SpecialProtected = call_inst.getFunc().hasRet();
                    if (param_list.size() > 0) {
                        // move r0 to VR0
                        Machine.Operand vr0 = newVR();
                        paramVRList.add(vr0);
                        Machine.Operand r0 = Arm.Reg.getR(GPRs.r0);
                        new MIMove(vr0, r0, curMB);
                        // move param0 to r0
                        // Value param0 = param_list.get(0);
                        // assert param0 instanceof Function.Param;
                        Machine.Operand opd = getVR_may_imm(param_list.get(0));
                        assert opd != null;
                        new MIMove(r0, opd, curMB);
                    } else if (r0SpecialProtected) {
                        // move r0 to VR0
                        Machine.Operand vr0 = newVR();
                        paramVRList.add(vr0);
                        Machine.Operand r0 = Arm.Reg.getR(GPRs.r0);
                        new MIMove(vr0, r0, curMB);
                    }
                    if (param_list.size() > 1) {
                        // move r1 to VR1
                        Machine.Operand vr1 = newVR();
                        paramVRList.add(vr1);
                        Machine.Operand r1 = Arm.Reg.getR(GPRs.r1);
                        new MIMove(vr1, r1, curMB);
                        // move param1 to r1
                        // Value v1 = param_list.get(1);
                        // assert v1 instanceof Function.Param;
                        Machine.Operand param1 = getVR_may_imm(param_list.get(1));
                        assert param1 != null;
                        new MIMove(r1, param1, curMB);
                    }
                    if (param_list.size() > 2) {
                        // move r2 to VR2
                        Machine.Operand vr2 = newVR();
                        paramVRList.add(vr2);
                        Machine.Operand r2 = Arm.Reg.getR(GPRs.r2);
                        new MIMove(vr2, r2, curMB);
                        // move param2 to r2
                        // Value v2 = param_list.get(2);
                        // assert v2 instanceof Function.Param;
                        Machine.Operand param2 = getVR_may_imm(param_list.get(2));
                        assert param2 != null;
                        new MIMove(r2, param2, curMB);
                    }
                    if (param_list.size() > 3) {
                        // move r3 to VR3
                        Machine.Operand vr3 = newVR();
                        paramVRList.add(vr3);
                        Machine.Operand r3 = Arm.Reg.getR(GPRs.r3);
                        new MIMove(vr3, r3, curMB);
                        // move param3 to r3
                        // Value v3 = param_list.get(3);
                        // assert v3 instanceof Function.Param;
                        Machine.Operand param3 = getVR_may_imm(param_list.get(3));
                        assert param3 != null;
                        new MIMove(r3, param3, curMB);
                    }
                    if (param_list.size() > 4) {
                        // push
                        for (int i = 4; i < param_list.size(); i++) {
                            Value param = param_list.get(i);
                            int offset_imm = (i - 3) * -4;
                            Machine.Operand data = getVR_may_imm(param);
                            Machine.Operand addr = Arm.Reg.getR(GPRs.sp);
                            // TODO 小心函数参数个数超级多, 超过立即数可以表示的大小导致的错误
                            Machine.Operand offset = new Machine.Operand(I32, offset_imm);
                            new MIStore(data, addr, offset, curMB);
                        }
                    }
                    // 栈空间移位
                    Function callFunc = call_inst.getFunc();
                    Machine.McFunction callMcFunc = func2mcFunc.get(callFunc);
                    if (call_inst.getFunc().isExternal) {
                        Machine.McFunction mf = func2mcFunc.get(call_inst.getFunc());
                        assert mf != null;
                        new MICall(mf, curMB);
                    } else {
                        if (callMcFunc == null) {
                            throw new AssertionError("Callee is null");
                        }
                        // assert callMcFunc != null;
                        Machine.Operand rOp1 = new Machine.Operand(I32, 0);
                        Machine.Operand mvDst1 = newVR();
                        MIMove mv1 = new MIMove(mvDst1, rOp1, curMB);
                        mv1.setNeedFix(callMcFunc, STACK_FIX.ONLY_PARAM);
                        new MIBinary(MachineInst.Tag.Sub, Arm.Reg.getR(sp), Arm.Reg.getR(sp), mvDst1, curMB);
                        // 设置一个boolean表示需要修复方便output .S时及时修复
                        // miBinary.setNeedFix(callMcFunc, STACK_FIX.ONLY_PARAM);
                        // call
                        new MICall(callMcFunc, curMB);
                        Machine.Operand rOp2 = new Machine.Operand(I32, 0);
                        Machine.Operand mvDst2 = newVR();
                        MIMove mv2 = new MIMove(mvDst2, rOp2, curMB);
                        mv2.setNeedFix(callMcFunc, STACK_FIX.ONLY_PARAM);
                        new MIBinary(MachineInst.Tag.Add, Arm.Reg.getR(sp), Arm.Reg.getR(sp), mvDst2, curMB);
                        // miBinary.setNeedFix(callMcFunc, STACK_FIX.ONLY_PARAM);

                    }
                    // 这行是取返回值
                    if (callFunc.hasRet()) {
                        new MIMove(getVR_no_imm(call_inst), Arm.Reg.getR(r0), curMB);
                    }
                    // 需要把挪走的r0-rx再挪回来
                    for (int i = 0; i < paramVRList.size(); i++) {
                        new MIMove(Arm.Reg.getR(i), paramVRList.get(i), curMB);
                    }
                }
                case phi -> throw new AssertionError("Backend has phi: " + instr);
                case pcopy -> {
                    // 前端已经消完了
                    // Instr.PCopy pCopy = (Instr.PCopy) instr;
                    // ArrayList<Value> rList = pCopy.getRHS();
                    // ArrayList<Value> lList = pCopy.getLHS();
                    // assert rList.size() == lList.size();
                    // for (int i = 0; i < lList.size(); i++) {
                    //     Value lhs = lList.get(i);
                    //     Value rhs = rList.get(i);
                    //     Machine.Operand source = getVR_may_imm(rhs);
                    //     assert !(lhs instanceof Constant);
                    //     Machine.Operand target = getVR_no_imm(lhs);
                    //     new MIMove(target, source, curMB);
                    // }
                }
                case move -> {
                    Machine.Operand source = getVR_may_imm(((Instr.Move) instr).getSrc());
                    Machine.Operand target = getVR_no_imm(((Instr.Move) instr).getDst());
                    new MIMove(target, source, curMB);
                }
            }
            instr = (Instr) instr.getNext();
        }
        // return nextBlockList;
        // for (Machine.Block mb : nextBlockList) {
        //     genBB(mb.bb);
        // }
    }

    public void genGlobal() {
        for (Map.Entry<GlobalVal.GlobalValue, Initial> entry : globalMap.entrySet()) {
            GlobalVal.GlobalValue globalValue = entry.getKey();
            // TODO 目前采用了新的写法
            // load global addr at the head of the entry bb
            // MIGlobal new_inst = new MIGlobal(globalValue, curMachineFunc.getBeginMB());
            // allocate virtual reg
            // Machine.Operand vr = newVR();
            // new_inst.dOpd = vr;
            Arm.Glob glob = new Arm.Glob(globalValue);
            globList.add(glob);
            globptr2globOpd.put(globalValue, glob);
        }
    }

    private void genBinaryInst(Instr.Alu instr) {
        MachineInst.Tag tag = MachineInst.Tag.map.get(instr.getOp());
        Value lhs = instr.getRVal1();
        Value rhs = instr.getRVal2();
        if (tag == MachineInst.Tag.Mod) {
            Machine.Operand q = getVR_no_imm(instr);
            // q = a%b = a-(a/b)*b
            // dst1 = a/b
            Machine.Operand a = getVR_may_imm(lhs);
            Machine.Operand b = getVR_may_imm(rhs);
            Machine.Operand dst1 = newVR();
            if (rhs.isConstantInt()) {
                divOptimize_mod(lhs, rhs, dst1);
            } else {
                new MIBinary(MachineInst.Tag.Div, dst1, a, b, curMB);
            }
            // dst2 = dst1*b
            Machine.Operand dst2 = newVR();
            new MIBinary(MachineInst.Tag.Mul, dst2, dst1, b, curMB);
            // q = a - dst2
            new MIBinary(MachineInst.Tag.Sub, q, a, dst2, curMB);
            return;

        }
        // div+mode optimize
        //这里需要确定一下lhs不是浮点类型
        if (tag == MachineInst.Tag.Div && rhs.isConstantInt()) {
            divOptimize(instr);
            return;
        }
        if (tag == MachineInst.Tag.Mul && rhs.isConstantInt() && is2power(Math.abs(((Constant.ConstantInt) rhs).constIntVal))) {
            mulOptimize(instr);
            return;
        }
        Machine.Operand lVR = getVR_may_imm(lhs);
        Machine.Operand rVR = getVR_may_imm(rhs);
        // instr不可能是Constant
        Machine.Operand dVR = getVR_no_imm(instr);
        new MIBinary(tag, dVR, lVR, rVR, curMB);
    }

    public boolean is2power(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }

    public void mulOptimize(Instr.Alu instr) {
        Value lhs = instr.getRVal1();
        Machine.Operand n = getVR_may_imm(lhs);
        Value rhs = instr.getRVal2();
        Machine.Operand q = getVR_no_imm(instr);
        int d = ((Constant.ConstantInt) rhs).constIntVal;
        int k = Integer.toBinaryString(Math.abs(d)).length() - 1;
        //q = n*d
        //q = n<<k
        Arm.Shift shift = new Arm.Shift(Arm.ShiftType.Lsl, k);
        new MIMove(q, n, shift, curMB);

    }

    public void divOptimize_mod(Value lhs, Value rhs, Machine.Operand q) {
        // q = n/d
        Machine.Operand n = getVR_may_imm(lhs);
        int d = ((Constant.ConstantInt) rhs).constIntVal;
        int N = 32;
        Multiplier multiplier = chooseMultiplier(Math.abs(d), N-1);
        int l = multiplier.l;
        int sh_post = multiplier.sh_post;
        long m = multiplier.m;
        if (Math.abs(d) == 1) {
            // q = n
            new MIMove(q, n, curMB);

        } else if (Math.abs(d) == (1 << l)) {
            // q = SRA(n+SRL(SRA(n,l-1),N-l),l)
            // dst1 = SRA(n,l-1)
            Machine.Operand dst1 = newVR();
            Arm.Shift shift1 = new Arm.Shift(Arm.ShiftType.Asr, l - 1);
            new MIMove(dst1, n, shift1, curMB);
            // dst2 = SRL(dst1,N-l)
            Machine.Operand dst2 = newVR();
            Arm.Shift shift2 = new Arm.Shift(Arm.ShiftType.Lsr, N - l);
            new MIMove(dst2, dst1, shift2, curMB);
            // dst3 = n+dst2
            Machine.Operand dst3 = newVR();
            new MIBinary(MachineInst.Tag.Add, dst3, n, dst2, curMB);
            // q = SRA(dst3,l)
            Arm.Shift shift3 = new Arm.Shift(Arm.ShiftType.Asr, l);
            new MIMove(q, dst3, shift3, curMB);
        } else if (m < ((long)1 << (N - 1))) {
            // q = SRA(MULSH(m,n),sh_post)-XSIGN(n)
            // dst1 = MULSH(m,n)
            Machine.Operand m_op = new Machine.Operand(I32, (int) m);
            Machine.Operand dst1 = newVR();
            new MILongMul(dst1, n, m_op, curMB);
            // dst2 = SRA(dst1,sh_post)
            Machine.Operand dst2 = newVR();
            Arm.Shift shift2 = new Arm.Shift(Arm.ShiftType.Asr, sh_post);
            new MIMove(dst2, dst1, shift2, curMB);
            // dst3 = -XSIGN(n)
            Machine.Operand dst3 = newVR();
            new MICompare(n, new Machine.Operand(I32, 0), curMB);
            new MIMove(Lt, dst3, new Machine.Operand(I32, 1), curMB);
            new MIMove(Ge, dst3, new Machine.Operand(I32, 0), curMB);
            // q = dst2+dst3
            new MIBinary(MachineInst.Tag.Add, q, dst2, dst3, curMB);
        } else {
            // q = SRA(n+MULSH(m-2^N,n),sh_post)-XSIGN(n)
            // dst1 = MULSH(m-2^N,n)
            Machine.Operand m_op = new Machine.Operand(I32, (int) (m - ((long)(1 << N))));
            Machine.Operand dst1 = newVR();
            new MILongMul(dst1, n, m_op, curMB);
            // dst2 = n+dst1
            Machine.Operand dst2 = newVR();
            new MIBinary(MachineInst.Tag.Add, dst2, n, dst1, curMB);
            // dst3 = SRA(dst2,sh_post)
            Machine.Operand dst3 = newVR();
            Arm.Shift shift = new Arm.Shift(Arm.ShiftType.Asr, sh_post);
            new MIMove(dst3, dst2, shift, curMB);
            // dst4 = -XSIGN(n)
            Machine.Operand dst4 = newVR();
            new MICompare(n, new Machine.Operand(I32, 0), curMB);
            new MIMove(Lt, dst4, new Machine.Operand(I32, 1), curMB);
            new MIMove(Ge, dst4, new Machine.Operand(I32, 0), curMB);
            // q = dst3+dst4
            new MIBinary(MachineInst.Tag.Add, q, dst3, dst4, curMB);
        }
        if (d < 0) {
            // q=-q
            new MIBinary(MachineInst.Tag.Rsb, q, q, new Machine.Operand(I32, 0), curMB);
        }
    }

    public void divOptimize(Instr.Alu instr) {
        // q = n/d
        Value lhs = instr.getRVal1();
        Machine.Operand n = getVR_may_imm(lhs);
        Value rhs = instr.getRVal2();
        Machine.Operand q = getVR_no_imm(instr);
        int d = ((Constant.ConstantInt) rhs).constIntVal;
        int N = 32;
        Multiplier multiplier = chooseMultiplier(Math.abs(d), N-1);
        int l = multiplier.l;
        int sh_post = multiplier.sh_post;
        long m = multiplier.m;
        if (Math.abs(d) == 1) {
            // q = n
            new MIMove(q, n, curMB);

        } else if (Math.abs(d) == (1 << l)) {
            // q = SRA(n+SRL(SRA(n,l-1),N-l),l)
            // dst1 = SRA(n,l-1)
            Machine.Operand dst1 = newVR();
            Arm.Shift shift1 = new Arm.Shift(Arm.ShiftType.Asr, l - 1);
            new MIMove(dst1, n, shift1, curMB);
            // dst2 = SRL(dst1,N-l)
            Machine.Operand dst2 = newVR();
            Arm.Shift shift2 = new Arm.Shift(Arm.ShiftType.Lsr, N - l);
            new MIMove(dst2, dst1, shift2, curMB);
            // dst3 = n+dst2
            Machine.Operand dst3 = newVR();
            new MIBinary(MachineInst.Tag.Add, dst3, n, dst2, curMB);
            // q = SRA(dst3,l)
            Arm.Shift shift3 = new Arm.Shift(Arm.ShiftType.Asr, l);
            new MIMove(q, dst3, shift3, curMB);
        } else if (m < ((long)1 << (N - 1))) {
            // q = SRA(MULSH(m,n),sh_post)-XSIGN(n)
            // dst1 = MULSH(m,n)
            Machine.Operand m_op = new Machine.Operand(I32, (int) m);
            Machine.Operand dst1 = newVR();
            new MILongMul(dst1, n, m_op, curMB);
            // dst2 = SRA(dst1,sh_post)
            Machine.Operand dst2 = newVR();
            Arm.Shift shift2 = new Arm.Shift(Arm.ShiftType.Asr, sh_post);
            new MIMove(dst2, dst1, shift2, curMB);
            // dst3 = -XSIGN(n)
            Machine.Operand dst3 = newVR();
            new MICompare(n, new Machine.Operand(I32, 0), curMB);
            new MIMove(Lt, dst3, new Machine.Operand(I32, 1), curMB);
            new MIMove(Ge, dst3, new Machine.Operand(I32, 0), curMB);
            // q = dst2+dst3
            new MIBinary(MachineInst.Tag.Add, q, dst2, dst3, curMB);
        } else {
            // q = SRA(n+MULSH(m-2^N,n),sh_post)-XSIGN(n)
            // dst1 = MULSH(m-2^N,n)
            Machine.Operand m_op = new Machine.Operand(I32, (int) (m - ((long)(1 << N))));
            Machine.Operand dst1 = newVR();
            new MILongMul(dst1, n, m_op, curMB);
            // dst2 = n+dst1
            Machine.Operand dst2 = newVR();
            new MIBinary(MachineInst.Tag.Add, dst2, n, dst1, curMB);
            // dst3 = SRA(dst2,sh_post)
            Machine.Operand dst3 = newVR();
            Arm.Shift shift = new Arm.Shift(Arm.ShiftType.Asr, sh_post);
            new MIMove(dst3, dst2, shift, curMB);
            // dst4 = -XSIGN(n)
            Machine.Operand dst4 = newVR();
            new MICompare(n, new Machine.Operand(I32, 0), curMB);
            new MIMove(Lt, dst4, new Machine.Operand(I32, 1), curMB);
            new MIMove(Ge, dst4, new Machine.Operand(I32, 0), curMB);
            // q = dst3+dst4
            new MIBinary(MachineInst.Tag.Add, q, dst3, dst4, curMB);
        }
        if (d < 0) {
            // q=-q
            new MIBinary(MachineInst.Tag.Rsb, q, q, new Machine.Operand(I32, 0), curMB);
        }
    }


    /**
     * 条件相关
     */
    private static class CMPAndArmCond {
        public MICompare CMP;
        public Arm.Cond ArmCond;

        public CMPAndArmCond(MICompare cmp, Arm.Cond cond) {
            CMP = cmp;
            ArmCond = cond;
        }


        @Override
        public int hashCode() {
            return CMP.hashCode();
        }
    }

    /**
     * 条件相关
     */
    private HashMap<Instr, CMPAndArmCond> cmpInst2MICmpMap = new HashMap<>();

    /**
     * 条件相关
     */
    private void genCmp(Instr instr) {
        // 这个不可能是global, param 之类 TODO 常数(?)
        Machine.Operand dst = getVR_may_imm(instr);
        if (instr.isIcmp()) {
            Instr.Icmp icmp = ((Instr.Icmp) instr);
            Value lhs = icmp.getRVal1();
            Value rhs = icmp.getRVal2();
            Machine.Operand lVR = getVR_may_imm(lhs);
            Machine.Operand rVR = getVR_may_imm(rhs);
            MICompare cmp = new MICompare(lVR, rVR, curMB);
            int condIdx = icmp.getOp().ordinal();
            Arm.Cond cond = Arm.Cond.values()[condIdx];
            // Icmp或Fcmp后紧接着BranchInst，而且前者的结果仅被后者使用，那么就可以不用计算结果，而是直接用bxx的指令
            if (((Instr) icmp.getNext()).isBranch()
                    && icmp.onlyOneUser()
                    && icmp.getBeginUse().getUser().isBranch()) {
                cmpInst2MICmpMap.put(instr, new CMPAndArmCond(cmp, cond));
            } else {
                new MIMove(cond, dst, new Machine.Operand(I32, 1), curMB);
                new MIMove(getOppoCond(cond), dst, new Machine.Operand(I32, 0), curMB);
            }
        }
    }

    public Arm.Cond getOppoCond(Arm.Cond cond) {
        return switch (cond) {
            case Eq -> Ne;
            case Ne -> Eq;
            case Ge -> Lt;
            case Gt -> Le;
            case Le -> Gt;
            case Lt -> Ge;
            case Any -> Any;
        };
    }

    /**
     * 立即数编码
     *
     * @param imm
     * @return
     */
    boolean immCanCode(int imm) {
        int encoding = imm;
        for (int i = 0; i < 32; i += 2) {
            if ((encoding & ~((-1) >>> 24)) != 0) {
                return true;
            }
            encoding = (encoding << 2) | (encoding >> 30);
        }
        return false;
    }


    // private Machine.Operand genOpdFromValue(Value value) {
    //     return getVR_may_imm(value);
    // }

    /**
     * 直接生成新的 virtual reg
     * 没有value与之对应时使用
     *
     * @return 新生成的 virtual reg
     */
    public Machine.Operand newVR() {
        return curMachineFunc.newVR();
    }

    /***
     * value为待寻求虚拟寄存器的value
     * @param value
     * @return 如果value没有生成过vr, 则生成并放到map里并返回, 如果生成过直接返回
     */
    public Machine.Operand newVR(Value value) {
        Machine.Operand opd = curMachineFunc.newVR();
        // opd2value.put(opd, value);
        value2opd.put(value, opd);
        return opd;
    }

    /**
     * 不可能是立即数的vr获取
     */
    public Machine.Operand getVR_no_imm(Value value) {
        Machine.Operand opd = value2opd.get(value);
        return opd == null ? newVR(value) : opd;
    }

    /**
     * 直接mov一个立即数到寄存器(暂定用 movw 和 movt, 无脑不管多少位)
     *
     * @param imm
     * @return
     */
    public Machine.Operand getImmVR(int imm) {
        // 暴力用两条指令mov一个立即数到寄存器
        Machine.Operand dst = newVR();
        // TODO: 目前没有考虑浮点
        Machine.Operand immOpd = new Machine.Operand(I32, imm);
        new MIMove(dst, immOpd, curMB);
        return dst;
    }

    /**
     * 可能是立即数的vr获取
     */
    public Machine.Operand getVR_may_imm(Value value) {
        if (value instanceof Constant) {
            // TODO 目前是无脑用两条mov指令把立即数转到寄存器
            assert value instanceof Constant.ConstantInt;
            return getImmVR((int) ((Constant) value).getConstVal());
        } else {
            return getVR_no_imm(value);
        }
    }

    public Machine.Operand getVR_from_ptr(Value value) {
        if (value instanceof GlobalVal.GlobalValue) {
            Machine.Operand addr = newVR();
            Arm.Glob glob = globptr2globOpd.get((GlobalVal.GlobalValue) value);
            new MIMove(addr, glob, curMB);
            // 取出来的Operand 是立即数类型
            return addr;
        } else {
            // TODO 这里应该不可能是常数
            return getVR_no_imm(value);
        }
    }

}
