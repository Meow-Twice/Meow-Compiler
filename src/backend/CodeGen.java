package backend;

import frontend.lexer.Lexer;
import frontend.semantic.Initial;
import frontend.syntax.Parser;
import lir.*;
import lir.Machine.Operand;
import manage.Manager;
import mir.*;
import mir.type.DataType;
import mir.type.Type;
import util.FileDealer;

import java.util.*;

import static lir.Arm.Cond.*;
import static lir.Arm.Regs.GPRs.*;
import static lir.Arm.Regs.FPRs.*;
import static mir.type.DataType.F32;
import static mir.type.DataType.I32;

public class CodeGen {

    public static final CodeGen CODEGEN = new CodeGen();
    public static boolean _DEBUG_OUTPUT_MIR_INTO_COMMENT = true;
    public static boolean needFPU = false;
    boolean _DEBUG_MUL_DIV = false;

    // 当前的Machine.McFunction
    private static Machine.McFunction curMF;

    // 当前的mir.Function
    private static mir.Function curFunc;

    // mir中func的名字到Function的Map
    private HashMap<String, Function> midFuncMap;

    // 每个LLVM IR为一个值, 需要有一个虚拟寄存器(或常数->立即数)与之一一对应
    // Value到Operand的Map
    public HashMap<Value, Operand> value2opd;
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
    // 整数数传参可使用最大个数
    public static final int rParamCnt = 4;
    // 浮点数传参可使用最大个数
    public static final int sParamCnt = 16;

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
        curMF = null;
        midFuncMap = Manager.MANAGER.getFunctions();
        globalMap = Manager.MANAGER.globals;
        value2opd = new HashMap<>();
        // opd2value = new HashMap<>();
//        mcFuncList = new ArrayList<>();
        func2mcFunc = new HashMap<>();
        bb2mb = new HashMap<>();
    }

    public void gen() {
        needFPU = Lexer.detectFloat;
        // curMachineFunc = new Machine.McFunction();
        genGlobal();
        // TODO

        for (Function func : midFuncMap.values()) {
            // if (func.isExternal) {
            Machine.McFunction mcFunc = new Machine.McFunction(func);
            func2mcFunc.put(func, mcFunc);
            for (Function.Param p : func.getParams()) {
                if (p.getType().isFloatType()) {
                    mcFunc.floatParamCount++;
                } else {
                    mcFunc.intParamCount++;
                }
            }
            // }
        }
        for (Function func : midFuncMap.values()) {
            // Machine.McFunction mcFunc = new Machine.McFunction(func);
            // func2mcFunc.put(func, mcFunc);
            if (func.isExternal) {
                continue;
            }
            curMF = func2mcFunc.get(func);
            Machine.Program.PROGRAM.funcList.insertAtEnd(curMF);
            curFunc = func;
            boolean isMain = false;
            if (curFunc.getName().equals("main")) {
                isMain = true;
                Machine.Program.PROGRAM.mainMcFunc = curMF;
            }
            // curMachineFunc = mcFunc;
            curMF.clearVRCount();
            curMF.clearSVRCount();
            value2opd = new HashMap<>();

            BasicBlock bb = func.getBeginBB();
            BasicBlock endBB = func.getEnd();
            // 先造出来防止br找不到目标
            while (!bb.equals(endBB)) {
//                if (bb.isNoUse()) {
//                    System.err.println("err_code_gen");
//                }
                Machine.Block mb = new Machine.Block(bb);
                bb.setMB(mb);
                bb2mb.put(bb, mb);
                bb = (BasicBlock) bb.getNext();
            }
            bb = func.getBeginBB();
            curMB = bb.getMb();
            // 这里不可使用单例
            Push(curMF);
            Operand rOp = new Operand(I32, 0);
            Operand mvDst = newVR();
            MIMove mv = new MIMove(mvDst, rOp, curMB);
            mv.setNeedFix(STACK_FIX.VAR_STACK);
            new MIBinary(MachineInst.Tag.Sub, Arm.Reg.getR(sp), Arm.Reg.getR(sp), mvDst, curMB);
            if (!isMain) {
                dealParam();
            }
            // 改写为循环加运行速度
            nextBBList = new LinkedList<>();
            // TODO 改写为Stack尝试加快性能
            nextBBList.push(bb);
            while (nextBBList.size() > 0) {
                BasicBlock visitBB = nextBBList.pop();
                genBB(visitBB);
            }
        }
    }

    private void Push(Machine.McFunction mf) {
        if (needFPU) {
            new StackCtl.VPush(mf, curMB);
        }
        new StackCtl.MIPush(mf, curMB);
    }

    private void Pop(Machine.McFunction curMachineFunc) {
        new StackCtl.MIPop(curMachineFunc, curMB);
        if (needFPU) {
            new StackCtl.VPop(curMachineFunc, curMB);
        }
    }

    LinkedList<BasicBlock> nextBBList;

    private void dealParam() {
        int rIdx = 0;
        int sIdx = 0;
        int rTop = rIdx;
        int sTop = sIdx;
        for (Function.Param param : curFunc.getParams()) {
            if (param.getType().isFloatType()) {
                assert needFPU;
                Operand opd = curMF.newSVR();
                value2opd.put(param, opd);
                if (sIdx < sParamCnt) {
                    new V.Mov(opd, Arm.Reg.getS(sIdx), curMB);
                    sTop = sIdx + 1;
                } else {
                    // 这里因为无法确认栈的大小(参数栈空间, 所用寄存器push和pop的栈空间, 数组申请栈空间, 寄存器分配时溢出所需栈空间)是否超过了立即数编码, 因此一律用move指令处理
                    Operand offImm = new Operand(I32, 4 * (rTop + sTop - (rIdx + sIdx) - 1));
                    // 栈顶向下的偏移, 第四个参数 -4, 第五个参数 -8 ...修的时候只需要把这个立即数的值取出来加上getStackSize获取的栈大小即可

                    Operand imm = newVR();
                    MIMove mv = new MIMove(imm, offImm, curMB);
                    mv.setNeedFix(STACK_FIX.TOTAL_STACK);
                    Operand dstAddr = newVR();
                    new MIBinary(MachineInst.Tag.Add, dstAddr, Arm.Reg.getR(sp), imm, curMB);
                    new V.Ldr(opd, dstAddr, curMB);

                    curMF.addParamStack(4);
                }
                sIdx++;
            } else {
                Operand opd = curMF.newVR();
                value2opd.put(param, opd);
                if (rIdx < rParamCnt) {
                    new MIMove(opd, Arm.Reg.getR(rIdx), curMB);
                    rTop = rIdx + 1;
                } else {
                    // 这里因为无法确认栈的大小(参数栈空间, 所用寄存器push和pop的栈空间, 数组申请栈空间, 寄存器分配时溢出所需栈空间)是否超过了立即数编码, 因此一律用move指令处理
                    Operand offImm = new Operand(I32, 4 * (rTop + sTop - (rIdx + sIdx) - 1));
                    Operand dst = newVR();
                    MIMove mv = new MIMove(dst, offImm, curMB);
                    mv.setNeedFix(STACK_FIX.TOTAL_STACK);
                    // 栈顶向下的偏移, 第四个参数 -4, 第五个参数 -8 ...修的时候只需要把这个立即数的值取出来加上getStackSize获取的栈大小即可
                    new MILoad(opd, Arm.Reg.getR(sp), dst, curMB);
                    curMF.addParamStack(4);
                }
                rIdx++;
            }
        }
        curMF.alignParamStack();
        // curMF.floatParamCount = sIdx;
        // curMF.intParamCount = rIdx;
    }

    public enum STACK_FIX {
        NO_NEED, // 无需修栈
        VAR_STACK, // 函数push语句后的第一条的sub sp, sp, varStack用, 函数pop语句前的最后一条add sp, sp, varStack用
        ONLY_PARAM, // 函数调用前的sub sp, sp, paramStack用, 函数调用后的add sp, sp, paramStack用
        TOTAL_STACK, // 函数有超过四个的参数时ldr参数用(move vrx, #xxx那条)
        FLOAT_TOTAL_STACK
    }

    HashSet<Machine.Block> dfsBBSet = new HashSet<>();

    public void genBB(BasicBlock bb) {
        curMB = bb.getMb();
        dfsBBSet.add(curMB);
        curMB.setMcFunc(curMF);
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
                        Operand condVR = getVR_no_imm(condValue);
                        // TODO fcmp无法直接转换?
                        // cond = Arm.Cond.values()[((Instr.Icmp) condValue).getOp().ordinal()];
                        Operand dst = newVR();
                        Operand immOpd = new Operand(I32, 0);
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
                    assert needFPU;
                    Instr.Fneg fnegInst = (Instr.Fneg) instr;
                    Operand src = getVR_may_imm(fnegInst.getRVal1());
                    Operand dst = getVR_no_imm(fnegInst);
                    new V.Neg(dst, src, curMB);
                }
                case ret -> {
                    Instr.Return returnInst = (Instr.Return) instr;
                    DataType retDataType = null;
                    if (returnInst.hasValue()) {
                        if (returnInst.getRetValue().getType().isInt32Type()) {
                            retDataType = I32;
                            Operand retOpd = getVR_may_imm(returnInst.getRetValue());
                            curMB.firstMIForBJ = new MIMove(Arm.Reg.getR(r0), retOpd, curMB);
                        } else if (returnInst.getRetValue().getType().isFloatType()) {
                            assert needFPU;
                            retDataType = F32;
                            Operand retOpd = getVR_may_imm(returnInst.getRetValue());
                            curMB.firstMIForBJ = new V.Mov(Arm.Reg.getS(s0), retOpd, curMB);
                        }
                    }
                    Operand rOp = new Operand(I32, 0);
                    Operand mvDst = newVR();
                    MIMove mv = new MIMove(mvDst, rOp, curMB);
                    mv.setNeedFix(STACK_FIX.VAR_STACK);
                    new MIBinary(MachineInst.Tag.Add, Arm.Reg.getR(sp), Arm.Reg.getR(sp), mvDst, curMB);
                    // miBinary.setNeedFix(STACK_FIX.VAR_STACK);
                    Pop(curMF);
                    if (retDataType == I32) {
                        new MIReturn(Arm.Reg.getR(r0), curMB);
                    } else if (retDataType == F32) {
                        assert needFPU;
                        new V.Ret(Arm.Reg.getS(s0), curMB);
                    } else {
                        new MIReturn(curMB);
                    }
                }
                case zext -> {
                    Operand dst = getVR_no_imm(instr);
                    Operand src = getVR_may_imm(((Instr.Zext) instr).getRVal1());
                    // 现阶段应该已经在上一个cmp生成好了并转移到虚拟寄存器中了, 应该不用管, 待优化
                    new MIMove(dst, src, curMB);
                }
                case fptosi -> {
                    assert needFPU;
                    Operand src = getVR_may_imm(((Instr.FPtosi) instr).getRVal1());
                    Operand tmp = newSVR();
                    new V.Cvt(V.CvtType.f2i, tmp, src, curMB);
                    Operand dst = getVR_no_imm(instr);
                    new V.Mov(dst, tmp, curMB);
                }
                case sitofp -> {
                    assert needFPU;
                    Operand src = getVR_may_imm(((Instr.SItofp) instr).getRVal1());
                    Operand tmp = newSVR();
                    new V.Mov(tmp, src, curMB);
                    Operand dst = getVR_no_imm(instr);
                    new V.Cvt(V.CvtType.i2f, dst, tmp, curMB);
                }
                case alloc -> {
                    Instr.Alloc allocInst = (Instr.Alloc) instr;
                    Type contentType = allocInst.getContentType();
                    if (contentType.isPointerType()) {
                        break;
                    }
                    // 这里已经不可能Alloc一个Int或者Float了
                    assert contentType.isArrType();
                    Operand addr = getVR_no_imm(allocInst);
                    Operand spReg = Arm.Reg.getR(sp);
                    Operand offset = new Operand(I32, curMF.getVarStack());
                    Operand mvDst = newVR();
                    new MIMove(mvDst, offset, curMB);
                    new MIBinary(MachineInst.Tag.Add, addr, spReg, mvDst, curMB);
                    // 栈空间移位
                    curMF.addVarStack(((Type.ArrayType) contentType).getFlattenSize() * 4);
                }
                case load -> {
                    Instr.Load loadInst = (Instr.Load) instr;
                    Value addrValue = loadInst.getPointer();
                    Operand addrOpd = getVR_from_ptr(addrValue);
                    Operand data = getVR_no_imm(instr);
                    if (loadInst.getType().isFloatType()) {
                        assert needFPU;
                        new V.Ldr(data, addrOpd, curMB);
                    } else {
                        Operand offsetOpd = new Operand(I32, 0);
                        new MILoad(data, addrOpd, offsetOpd, curMB);
                    }
                }
                case store -> {
                    Instr.Store storeInst = (Instr.Store) instr;
                    Operand data = getVR_may_imm(storeInst.getValue());
                    Operand addr = getVR_from_ptr(storeInst.getPointer());
                    if (storeInst.getValue().getType().isFloatType()) {
                        assert needFPU;
                        new V.Str(data, addr, curMB);
                    } else {
                        Operand offset = new Operand(I32, 0);
                        new MIStore(data, addr, offset, curMB);
                    }
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
                    Operand dstVR = getVR_no_imm(gep);
                    // Machine.Operand curAddrVR = getVR_no_imm(ptrValue);
                    Operand basePtrVR = getVR_from_ptr(ptrValue);
                    Operand curAddrVR = newVR();
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
                                    Operand immVR = getImmVR(totalConstOff);
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
                            Operand curIdxVR = getVR_no_imm(curIdxValue);
                            Operand offUnitImmVR = getImmVR(offUnit);
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
                                    Machine.Operand dst = newVR();
                                    new MIBinary(MachineInst.Tag.Add, dst, curAddrVR, immVR, curMB);
                                    curAddrVR = dst;
                                }
                                new MIFma(true, false, dstVR, curIdxVR, offUnitImmVR, curAddrVR, curMB);
                            } else {
                                Machine.Operand dst = newVR();
                                new MIFma(true, false, dst, curIdxVR, offUnitImmVR, curAddrVR, curMB);
                                curAddrVR = dst;
                            }
                        }
                    }

                }
                case bitcast -> {
                    Instr.Bitcast bitcast = (Instr.Bitcast) instr;
                    Operand src = getVR_no_imm(bitcast.getSrcValue());
                    Operand dst = getVR_no_imm(bitcast);
                    new MIMove(dst, src, curMB);
                }
                case call -> {
                    Instr.Call call_inst = (Instr.Call) instr;
                    // TODO : 函数内部可能调用其他函数, 但是在函数内部已经没有了Caller使用哪些寄存器的信息, 目前影响未知, 可能有bug
                    // TODO: ayame解决方案是在 callee 头 push 和 callee 尾部 pop 那些 callee 要用到的寄存器, 暂定此方案
                    // TODO: 如果有返回值, 则由caller保护r0或s0, 如果没有返回值则有callee保护
                    ArrayList<Value> param_list = call_inst.getParamList();
                    ArrayList<Operand> paramVRList = new ArrayList<>();
                    ArrayList<Operand> paramSVRList = new ArrayList<>();
                    int rIdx = 0;
                    int sIdx = 0;
                    int rParamTop = rIdx;
                    int sParamTop = sIdx;
                    for (Value p : param_list) {
                        if (p.getType().isFloatType()) {
                            assert needFPU;
                            if (sIdx < sParamCnt) {
                                Operand tmpDst = newSVR();
                                paramSVRList.add(tmpDst);
                                Operand fpr = Arm.Reg.getS(sIdx);
                                new V.Mov(tmpDst, fpr, curMB);
                                new V.Mov(fpr, getVR_may_imm(p), curMB);
                                sParamTop = sIdx + 1;
                            } else {
                                int offset_imm = (sParamTop + rParamTop - (rIdx + sIdx) - 1) * 4;
                                Operand data = getVR_may_imm(p);
                                Operand off = new Operand(I32, offset_imm);
                                if (fpOffEncode(offset_imm)) {
                                    new V.Str(data, Arm.Reg.getR(sp), off, curMB);
                                } else if (immCanCode(-offset_imm)) {
                                    // TODO 取反
                                    Operand dstAddr = newVR();
                                    new MIBinary(MachineInst.Tag.Sub, dstAddr, Arm.Reg.getR(sp), new Operand(I32, -offset_imm), curMB);
                                    new V.Str(data, dstAddr, curMB);
                                } else {
                                    Operand imm = newVR();
                                    new MIMove(imm, new Operand(I32, -offset_imm), curMB);
                                    Operand dstAddr = newVR();
                                    new MIBinary(MachineInst.Tag.Sub, dstAddr, Arm.Reg.getR(sp), imm, curMB);
                                    new V.Str(data, dstAddr, curMB);
                                }
                            }
                            sIdx++;
                        } else {
                            if (rIdx < rParamCnt) {
                                Operand tmpDst = newVR();
                                paramVRList.add(tmpDst);
                                Operand gpr = Arm.Reg.getR(rIdx);
                                new MIMove(tmpDst, gpr, curMB);
                                new MIMove(gpr, getVR_may_imm(p), curMB);
                                rParamTop = rIdx + 1;
                            } else {
                                int offset_imm = (sParamTop + rParamTop - (rIdx + sIdx) - 1) * 4;
                                Operand data = getVR_may_imm(p);
                                Operand addr = Arm.Reg.getR(sp);
                                // TODO 小心函数参数个数超级多, 超过立即数可以表示的大小导致的错误
                                Operand off = new Operand(I32, offset_imm);
                                if (!immCanCode(offset_imm)) {
                                    Operand immDst = newVR();
                                    new MIMove(immDst, off, curMB);
                                    off = immDst;
                                }
                                new MIStore(data, addr, off, curMB);
                            }
                            rIdx++;
                        }
                    }
                    // 栈空间移位
                    Function callFunc = call_inst.getFunc();
                    Machine.McFunction callMcFunc = func2mcFunc.get(callFunc);
                    if (callFunc.isExternal) {
                        /**
                         * r0实际上依据设计不一定需要保护, 因为一定是最后ret语句才会有r0的赋值
                         */
                        // TODO: return getint();
                        while (rIdx < 2) {
                            Operand tmpDst = newVR();
                            paramVRList.add(tmpDst);
                            new MIMove(tmpDst, Arm.Reg.getR(rIdx++), curMB);
                        }
                        if (needFPU) {
                            while (sIdx < 2) {
                                Operand tmpDst = newSVR();
                                paramSVRList.add(tmpDst);
                                new V.Mov(tmpDst, Arm.Reg.getS(sIdx++), curMB);
                            }
                        }
                        Machine.McFunction mf = func2mcFunc.get(callFunc);
                        assert mf != null;
                        Push(mf);
                        new MICall(mf, curMB);
                        Pop(mf);
                        if (call_inst.getType().isInt32Type()) {
                            new MIMove(getVR_no_imm(call_inst), Arm.Reg.getR(r0), curMB);
                        } else if (call_inst.getType().isFloatType()) {
                            assert needFPU;
                            new V.Mov(getVR_no_imm(call_inst), Arm.Reg.getS(s0), curMB);
                        } else if (!call_inst.getType().isVoidType()) {
                            throw new AssertionError("Wrong ret type");
                        }
                    } else {
                        if (callMcFunc == null) {
                            throw new AssertionError("Callee is null");
                        }
                        /**
                         * r0实际上依据设计不一定需要保护, 因为一定是最后ret语句才会有r0的赋值
                         */
                        // TODO: return getint();
                        if (rIdx == 0 && call_inst.getType().isInt32Type()) {
                            Operand tmpDst = newVR();
                            paramVRList.add(tmpDst);
                            new MIMove(tmpDst, Arm.Reg.getR(r0), curMB);
                        }
                        if (sIdx == 0 && call_inst.getType().isFloatType()) {
                            Operand tmpDst = newSVR();
                            paramSVRList.add(tmpDst);
                            new V.Mov(tmpDst, Arm.Reg.getS(s0), curMB);
                        }
                        Operand rOp1 = new Operand(I32, 0);
                        Operand mvDst1 = newVR();
                        MIMove mv1 = new MIMove(mvDst1, rOp1, curMB);
                        mv1.setNeedFix(callMcFunc, STACK_FIX.ONLY_PARAM);
                        new MIBinary(MachineInst.Tag.Sub, Arm.Reg.getR(sp), Arm.Reg.getR(sp), mvDst1, curMB);
                        // 设置一个boolean表示需要修复方便output .S时及时修复
                        // miBinary.setNeedFix(callMcFunc, STACK_FIX.ONLY_PARAM);
                        // call
                        new MICall(callMcFunc, curMB);
                        Operand rOp2 = new Operand(I32, 0);
                        Operand mvDst2 = newVR();
                        MIMove mv2 = new MIMove(mvDst2, rOp2, curMB);
                        mv2.setNeedFix(callMcFunc, STACK_FIX.ONLY_PARAM);
                        new MIBinary(MachineInst.Tag.Add, Arm.Reg.getR(sp), Arm.Reg.getR(sp), mvDst2, curMB);
                        // miBinary.setNeedFix(callMcFunc, STACK_FIX.ONLY_PARAM);
                        // 这是取返回值
                        if (call_inst.getType().isInt32Type()) {
                            new MIMove(getVR_no_imm(call_inst), Arm.Reg.getR(r0), curMB);
                        } else if (call_inst.getType().isFloatType()) {
                            assert needFPU;
                            new V.Mov(getVR_no_imm(call_inst), Arm.Reg.getS(s0), curMB);
                        } else if (!call_inst.getType().isVoidType()) {
                            throw new AssertionError("Wrong ret type");
                        }
                    }
                    // 需要把挪走的r0-rx再挪回来
                    for (int i = 0; i < paramVRList.size(); i++) {
                        new MIMove(Arm.Reg.getR(i), paramVRList.get(i), curMB);
                    }
                    // 需要把挪走的s0-sx再挪回来
                    for (int i = 0; i < paramSVRList.size(); i++) {
                        assert needFPU;
                        new V.Mov(Arm.Reg.getS(i), paramSVRList.get(i), curMB);
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
                    Operand source = getVR_may_imm(((Instr.Move) instr).getSrc());
                    Operand target = getVR_no_imm(((Instr.Move) instr).getDst());
                    if (source.isF32() || target.isF32()) {
                        assert needFPU;
                        new V.Mov(target, source, curMB);
                    } else {
                        new MIMove(target, source, curMB);
                    }
                }
            }
            instr = (Instr) instr.getNext();
        }
        // return nextBlockList;
        // for (Machine.Block mb : nextBlockList) {
        //     genBB(mb.bb);
        // }
    }

    public static boolean fpOffEncode(int off) {
        return off <= 1020 && off >= -1020;
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
            assert globalValue.getType().isPointerType();
            Arm.Glob glob;
            // if (!((Type.PointerType) globalValue.getType()).getInnerType().isFloatType()) {
            glob = new Arm.Glob(globalValue);
            // } else {
            //     glob = new Arm.Glob(globalValue, F32);
            // }
            globList.add(glob);
            globptr2globOpd.put(globalValue, glob);
        }
    }

    private boolean isFBino(Instr.Alu.Op op) {
        return op == Instr.Alu.Op.FSUB || op == Instr.Alu.Op.FADD || op == Instr.Alu.Op.FDIV || op == Instr.Alu.Op.FMUL || op == Instr.Alu.Op.FREM;
    }

    private void genBinaryInst(Instr.Alu instr) {
        MachineInst.Tag tag = MachineInst.Tag.map.get(instr.getOp());
        Value lhs = instr.getRVal1();
        Value rhs = instr.getRVal2();
        // boolean notFloat = isFBino(instr.getOp());

        if (tag == MachineInst.Tag.Mod) {
            Machine.Operand q = getVR_no_imm(instr);
            // q = a%b = a-(a/b)*b
            // dst1 = a/b
            Machine.Operand a = getVR_may_imm(lhs);
            Machine.Operand b = getVR_may_imm(rhs);
            Machine.Operand dst1 = newVR();
            if (_DEBUG_MUL_DIV && rhs.isConstantInt()) {
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
        } else if (tag == MachineInst.Tag.FMod) {
            assert needFPU;
            Operand q = getVR_no_imm(instr);
            // q = a%b = a-(a/b)*b
            // dst1 = a/b
            Operand a = getVR_may_imm(lhs);
            Operand b = getVR_may_imm(rhs);
            Operand dst1 = newSVR();
            new V.Binary(MachineInst.Tag.FDiv, dst1, a, b, curMB);
            // dst2 = dst1*b
            Operand dst2 = newSVR();
            new V.Binary(MachineInst.Tag.FMul, dst2, dst1, b, curMB);
            // q = a - dst2
            new V.Binary(MachineInst.Tag.FSub, q, a, dst2, curMB);
            return;
        }
        if (isFBino(instr.getOp())) {
            assert needFPU;
            Operand lVR = getVR_may_imm(lhs);
            Operand rVR = getVR_may_imm(rhs);
            // instr不可能是Constant
            Operand dVR = getVR_no_imm(instr);
            new V.Binary(tag, dVR, lVR, rVR, curMB);
            return;
        }
        if (!isFBino(instr.getOp()) && _DEBUG_MUL_DIV) {
            // div+mode optimize
            //这里需要确定一下lhs不是浮点类型
            if (tag == MachineInst.Tag.Div && rhs.isConstantInt()) {
                divOptimize(instr);
                return;
            }
            if(tag == MachineInst.Tag.Mul) {
                if(lhs.isConstantInt()){
                    Value tmp = rhs;
                    rhs = lhs;
                    lhs = tmp;
                }
                if (rhs.isConstantInt() && is2power(Math.abs(((Constant.ConstantInt) rhs).constIntVal))) {
                    mulOptimize(lhs,rhs,instr);
                    return;
                }
            }
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

    public void mulOptimize(Value lhs,Value rhs,Instr.Alu instr) {
        Machine.Operand n = getVR_may_imm(lhs);
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
        Multiplier multiplier = chooseMultiplier(Math.abs(d), N - 1);
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
        } else if (m < ((long) 1 << (N - 1))) {
            // q = SRA(MULSH(m,n),sh_post)-XSIGN(n)
            // dst1 = MULSH(m,n)
            Machine.Operand m_op = new Machine.Operand(I32, (int) m);
            Machine.Operand dst1 = newVR();
            Machine.Operand move_dst = newVR();
            new MIMove(move_dst,m_op,curMB);
            new MILongMul(dst1, n,move_dst, curMB);
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
            Machine.Operand m_op = new Machine.Operand(I32, (int) (m - (((long) 1 << N))));
            Machine.Operand dst1 = newVR();
            Machine.Operand move_dst = newVR();
            new MIMove(move_dst,m_op,curMB);
            new MILongMul(dst1, n,move_dst, curMB);
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
        Multiplier multiplier = chooseMultiplier(Math.abs(d), N - 1);
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
        } else if (m < ((long) 1 << (N - 1))) {
            // q = SRA(MULSH(m,n),sh_post)-XSIGN(n)
            // dst1 = MULSH(m,n)
            Machine.Operand m_op = new Machine.Operand(I32, (int) m);
            Machine.Operand dst1 = newVR();
            Machine.Operand move_dst = newVR();
            new MIMove(move_dst,m_op,curMB);
            new MILongMul(dst1, n,move_dst, curMB);
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
            Machine.Operand m_op = new Machine.Operand(I32, (int) (m - (((long) 1 << N))));
            Machine.Operand dst1 = newVR();
            Machine.Operand move_dst = newVR();
            new MIMove(move_dst,m_op,curMB);
            new MILongMul(dst1, n,move_dst, curMB);
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
        public MachineInst CMP;
        public Arm.Cond ArmCond;

        public CMPAndArmCond(MachineInst cmp, Arm.Cond cond) {
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
        Operand dst = getVR_may_imm(instr);
        if (instr.isIcmp()) {
            Instr.Icmp icmp = ((Instr.Icmp) instr);
            Value lhs = icmp.getRVal1();
            Value rhs = icmp.getRVal2();
            Operand lVR = getVR_may_imm(lhs);
            Operand rVR = getVR_may_imm(rhs);
            MICompare cmp = new MICompare(lVR, rVR, curMB);
            int condIdx = icmp.getOp().ordinal();
            Arm.Cond cond = Arm.Cond.values()[condIdx];
            // Icmp或Fcmp后紧接着BranchInst，而且前者的结果仅被后者使用，那么就可以不用计算结果，而是直接用bxx的指令
            if (((Instr) icmp.getNext()).isBranch()
                    && icmp.onlyOneUser()
                    && icmp.getBeginUse().getUser().isBranch() && icmp.getNext().equals(icmp.getBeginUse().getUser())) {
                cmpInst2MICmpMap.put(instr, new CMPAndArmCond(cmp, cond));
            } else {
                new MIMove(cond, dst, new Operand(I32, 1), curMB);
                new MIMove(getIcmpOppoCond(cond), dst, new Operand(I32, 0), curMB);
            }
        } else if (instr.isFcmp()) {
            assert needFPU;
            Instr.Fcmp fcmp = ((Instr.Fcmp) instr);
            Value lhs = fcmp.getRVal1();
            Value rhs = fcmp.getRVal2();
            Operand lSVR = getVR_may_imm(lhs);
            Operand rSVR = getVR_may_imm(rhs);
            V.Cmp vcmp = new V.Cmp(lSVR, rSVR, curMB);
            Arm.Cond cond = switch (fcmp.getOp()) {
                case OEQ -> Eq;
                case ONE -> Ne;
                case OGT -> Hi;
                case OGE -> Pl;
                case OLT -> Lt;
                case OLE -> Le;
            };
            // Icmp或Fcmp后紧接着BranchInst，而且前者的结果仅被后者使用，那么就可以不用计算结果，而是直接用bxx的指令
            if (((Instr) fcmp.getNext()).isBranch()
                    && fcmp.onlyOneUser()
                    && fcmp.getBeginUse().getUser().isBranch() && fcmp.getNext().equals(fcmp.getBeginUse().getUser())) {
                cmpInst2MICmpMap.put(instr, new CMPAndArmCond(vcmp, cond));
            } else {
                // TODO 这里不是很确定能不能执行
                new MIMove(cond, dst, new Operand(I32, 1), curMB);
                new MIMove(getFcmpOppoCond(cond), dst, new Operand(I32, 0), curMB);
            }
        }
    }

    public Arm.Cond getIcmpOppoCond(Arm.Cond cond) {
        return switch (cond) {
            case Eq -> Ne;
            case Ne -> Eq;
            case Ge -> Lt;
            case Gt -> Le;
            case Le -> Gt;
            case Lt -> Ge;
            case Hi, Pl, Any -> throw new AssertionError("Wrong Icmp oppo cond");
        };
    }

    public Arm.Cond getFcmpOppoCond(Arm.Cond cond) {
        return switch (cond) {
            case Eq -> Ne;
            case Ne -> Eq;
            case Hi -> Le;
            case Pl -> Lt;
            case Le -> Hi;
            case Lt -> Pl;
            case Ge, Gt, Any -> throw new AssertionError("Wrong Fcmp oppo cond");
        };
    }

    /**
     * 立即数编码
     *
     * @param imm
     * @return
     */
    public static boolean immCanCode(int imm) {
        int n = imm;
        for (int i = 0; i < 32; i += 2) {
            if ((n & ~0xFF) == 0) {
                return true;
            }
            n = (n << 2) | (n >>> 30);
        }
        return false;
    }


    // private Machine.Operand genOpdFromValue(Value value) {
    // private Machine.Operand genOpdFromValue(Value value) {
    //     return getVR_may_imm(value);
    // }

    public Operand newSVR() {
        return curMF.newSVR();
    }

    /**
     * 直接生成新的 virtual reg
     * 没有value与之对应时使用
     *
     * @return 新生成的 virtual reg
     */
    public Operand newVR() {
        return curMF.newVR();
    }

    public Operand newSVR(Value value) {
        Operand opd = curMF.newSVR();
        value2opd.put(value, opd);
        return opd;
    }

    /***
     * value为待寻求虚拟寄存器的value
     * @param value
     * @return 如果value没有生成过vr, 则生成并放到map里并返回, 如果生成过直接返回
     */
    public Operand newVR(Value value) {
        Operand opd = curMF.newVR();
        // opd2value.put(opd, value);
        value2opd.put(value, opd);
        return opd;
    }

    /**
     * 不可能是立即数的vr获取
     */
    public Operand getVR_no_imm(Value value) {
        Operand opd = value2opd.get(value);
        return opd == null ? (value.getType().isFloatType() ? newSVR(value) : newVR(value)) : opd;
    }

    /**
     * 直接mov一个立即数到寄存器(暂定用 movw 和 movt, 无脑不管多少位)
     *
     * @param imm
     * @return
     */
    public Operand getImmVR(int imm) {
        // 暴力用两条指令mov一个立即数到寄存器
        Operand dst = newVR();
        Operand immOpd = new Operand(I32, imm);
        new MIMove(dst, immOpd, curMB);
        return dst;
    }

    public static final HashMap<String, Operand> name2constFOpd = new HashMap<>();

    public Operand getFConstVR(Constant.ConstantFloat constF) {
        assert needFPU;
        Operand dst = newSVR();
        String name = constF.getAsmName();
        Operand addr = name2constFOpd.get(name);
        if (addr == null) {
            addr = new Operand(F32, constF);
            name2constFOpd.put(name, addr);
        }
        Arm.Glob glob = new Arm.Glob(name);
        // globList.add(glob);
        Operand labelAddr = newVR();
        new MIMove(labelAddr, glob, curMB);
        new V.Ldr(dst, labelAddr, curMB);
        return dst;
    }

    /**
     * 可能是立即数的vr获取
     */
    public Operand getVR_may_imm(Value value) {
        if (value instanceof Constant.ConstantInt) {
            return getImmVR((int) ((Constant) value).getConstVal());
        } else if (value instanceof Constant.ConstantFloat) {
            return getFConstVR((Constant.ConstantFloat) value);
        } else {
            return getVR_no_imm(value);
        }
    }

    public Operand getVR_from_ptr(Value value) {
        if (value instanceof GlobalVal.GlobalValue) {
            Operand addr = newVR();
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
