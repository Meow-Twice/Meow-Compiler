package backend;

import frontend.semantic.Initial;
import lir.*;
import manage.Manager;
import mir.*;
import mir.type.DataType;
import mir.type.Type;

import java.util.*;
import java.util.HashMap;

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

    // Operand到Value的Map, 这个map不应该存在 , 消phi时产生的move可能造成一条Instr对应多个opd
    // public HashMap<Machine.Operand, Value> opd2value;

    public ArrayList<Machine.McFunction> mcFuncList;

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

    //div+mod optimize
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
        int N = 32;
        assert d != 0;
        assert prec >= 1 && prec <= N;

        int l = (int) Math.ceil(Math.log(d) / Math.log(2));
        int sh_post = l;
        long m_low = ((long) 1 << (N + l)) / d;
        long m_high = (((long) 1 << (N + l)) + ((long) 1 << (N + l - prec))) / d;
        while ((m_low >> 1) < (m_high >> 1) && sh_post > 0) {
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
        mcFuncList = new ArrayList<>();
        func2mcFunc = new HashMap<>();
        bb2mb = new HashMap<>();
    }

    public void gen() {
        // curMachineFunc = new Machine.McFunction();
        // genGlobal();
        // TODO

        for (Function func : midFuncMap.values()) {
            if (func.isExternal) {
                Machine.McFunction mcFunc = new Machine.McFunction(func);
                func2mcFunc.put(func, mcFunc);
            }
        }
        for (Function func : midFuncMap.values()) {
            if (func.isExternal) {
                continue;
            }
            Machine.McFunction mcFunc = new Machine.McFunction(func);
            Machine.Program.PROGRAM.funcList.insertAtEnd(mcFunc);
            curFunc = func;
            if (curFunc.getName().equals("main")) Machine.Program.PROGRAM.mainMcFunc = mcFunc;
            curMachineFunc = mcFunc;
            curMachineFunc.setVRCount(0);
            mcFuncList.add(mcFunc);
            func2mcFunc.put(func, mcFunc);

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
            // endBB = func.getEndBB();
            // while (!bb.equals(endBB)) {
            genBB(bb);
            // bb = (BasicBlock) bb.getNext();
            // }
        }
    }

    HashSet<Machine.Block> dfsBBSet = new HashSet<>();

    public void genBB(BasicBlock bb) {
        curMB = bb.getMb();
        dfsBBSet.add(curMB);
        curMB.setMcFunc(curMachineFunc);
        ArrayList<Machine.Block> nextBlockList = new ArrayList<>();
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
                    if (!dfsBBSet.contains(mb)) nextBlockList.add(mb);
                    new MIJump(mb, curMB);
                }
                case icmp, fcmp -> genCmp(instr);
                case branch -> {
                    Arm.Cond cond;
                    Instr.Branch brInst = (Instr.Branch) instr;
                    Instr condValue = (Instr) brInst.getCond();
                    Machine.Block trueBlock = brInst.getThenTarget().getMb();
                    Machine.Block falseBlock = brInst.getElseTarget().getMb();
                    if (!dfsBBSet.contains(trueBlock)) nextBlockList.add(trueBlock);
                    if (!dfsBBSet.contains(falseBlock)) nextBlockList.add(falseBlock);
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
                    //0 sub value ->dst
                    Machine.Operand lOpd = getVR_may_imm(new Constant.ConstantFloat(0));
                    Machine.Operand rOpd = getVR_may_imm(value);
                    Machine.Operand dVR = getVR_no_imm(fneginst);
                    new MIBinary(MachineInst.Tag.FSub, dVR, lOpd, rOpd, curMB);

                }
                case ret -> {
                    Instr.Return returnInst = (Instr.Return) instr;
                    if (returnInst.hasValue()) {
                        Machine.Operand retOpd = getVR_may_imm(returnInst.getRetValue());
                        curMB.firstMIForBJ = new MIMove(new Machine.Operand(Arm.Reg.getR(0)), retOpd, curMB);
                        new MIReturn(curMB);
                    }
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
                    Machine.Operand spReg = new Machine.Operand(Arm.Reg.getR(sp));
                    Machine.Operand offset = new Machine.Operand(I32, curMachineFunc.getStackSize());
                    new MIBinary(MachineInst.Tag.Add, addr, spReg, offset, curMB);
                    // 栈空间移位
                    curMachineFunc.addStack(((Type.ArrayType) contentType).getFlattenSize() * 4);
                }
                case load -> {
                    Machine.Operand data = getVR_no_imm(instr);
                    Instr.Load loadInst = (Instr.Load) instr;
                    Value addrValue = loadInst.getPointer();
                    // assert addrValue.getType().isPointerType();
                    // if(((Type.PointerType) addrValue.getType()).getInnerType().isPointerType()){
                    //     // 前端消多了,这个本来不应该消的,但是全消了
                    //     load2alloc.put(loadInst, (Instr.Alloc) addrValue);
                    //     break;
                    // }
                    Machine.Operand addrOpd = getVR_no_imm(addrValue);
                    Machine.Operand offsetOpd = new Machine.Operand(I32, 0);
                    new MILoad(data, addrOpd, offsetOpd, curMB);
                }
                case store -> {
                    Instr.Store storeInst = (Instr.Store) instr;
                    Machine.Operand data = getVR_may_imm(storeInst.getValue());
                    Machine.Operand addr = getVR_no_imm(storeInst.getPointer());
                    Machine.Operand offset = new Machine.Operand(I32, 0);
                    new MIStore(data, addr, offset, curMB);
                }
                case gep -> {
                    int offsetCount;
                    Instr.GetElementPtr gepInst = (Instr.GetElementPtr) instr;
                    Value ptrValue = gepInst.getPtr();
                    Type.PointerType addrValueType = ((Type.PointerType) ptrValue.getType());
                    if (addrValueType.isBasicType()) {
                        offsetCount = 1;
                    } else {
                        assert addrValueType.getInnerType().isArrType();
                        offsetCount = ((Type.ArrayType) addrValueType.getInnerType()).getDimSize();
                    }
                    assert !ptrValue.isConstant();
                    Machine.Operand dstVR = getVR_no_imm(gepInst);
                    Machine.Operand curAddrVR = getVR_no_imm(ptrValue);
                    Type curType = addrValueType.getInnerType();
                    int totalConstOff = 0;
                    for (int i = 0; i < offsetCount; i++) {
                        Value curIdxValue = gepInst.getIdxValueOf(i);
                        int offUnit = 4;
                        if (curType.isArrType()) {
                            offUnit = 4 * ((Type.ArrayType) curType).getBaseFlattenSize();
                        }
                        if (curIdxValue.isConstantInt()) {
                            totalConstOff += offUnit * (int) ((Constant.ConstantInt) curIdxValue).getConstVal();
                            if (i == offsetCount - 1) {
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
                            Machine.Operand curIdxVR = getVR_may_imm(curIdxValue);
                            if (i == offsetCount - 1 && totalConstOff != 0) {
                                Machine.Operand immVR = getImmVR(totalConstOff);
                                // TODO 是否需要避免寄存器分配时出现use的VR与def的VR相同的情况
                                new MIBinary(MachineInst.Tag.Add, curAddrVR, curAddrVR, immVR, curMB);
                            }
                            Machine.Operand offUnitImmVR = getImmVR(offUnit);

                            /**
                             * Fma
                             * smmla:Rn + (Rm * Rs)[63:32] or smmls:Rd := Rn – (Rm * Rs)[63:32]
                             * mla:Rn + (Rm * Rs)[31:0] or mls:Rd := Rn – (Rm * Rs)[31:0]
                             * dst = acc +(-) lhs * rhs
                             */
                            // Machine.Operand tmpDst = newVR();
                            // new MIFma(true,false, tmpDst, curAddrVR, curIdxVR ,offUnitImmVR, curMB);
                            // curAddrVR = tmpDst;
                            // TODO 是否需要避免寄存器分配时出现use的VR与def的VR相同的情况
                            new MIFma(true, false, curAddrVR, curAddrVR, curIdxVR, offUnitImmVR, curMB);
                        }
                    }

                }
                case bitcast -> {/*不用管*/}
                case call -> {
                    //move caller's r0-r3  to VR
                    Instr.Call call_inst = (Instr.Call) instr;
                    // TODO : 函数内部可能调用其他函数, 但是在函数内部已经没有了使用哪些寄存器的信息, 目前影响未知, 可能有bug
                    ArrayList<Value> param_list = call_inst.getParamList();
                    if (!param_list.isEmpty()) {
                        //move r0 to VR0
                        Machine.Operand vr0 = newVR();
                        Machine.Operand r0 = new Machine.Operand(new Arm.Reg(I32, Arm.Regs.GPRs.r0));
                        new MIMove(vr0, r0, curMB);
                        //move param0 to r0
                        if (value2opd.containsKey(param_list.get(0))) {
                            new MIMove(r0, value2opd.get(param_list.get(0)), curMB);
                        }
                    }
                    if (call_inst.getFunc().isExternal) {
                        // getint()临时用
                        dealExternalFunc(call_inst, call_inst.getFunc());
                        if (call_inst.getFunc().hasRet())
                            new MIMove(getVR_may_imm(call_inst), Arm.Reg.getR(r0), curMB);
                        break;
                    }


                    if (param_list.size() > 1) {
                        //move r1 to VR1
                        Machine.Operand vr1 = newVR();
                        Machine.Operand r1 = Arm.Reg.getR(GPRs.r1);
                        new MIMove(vr1, r1, curMB);
                        //move param1 to r1
                        if (value2opd.containsKey(param_list.get(1))) {
                            new MIMove(r1, value2opd.get(param_list.get(1)), curMB);
                        }
                    }
                    if (param_list.size() > 2) {
                        //move r2 to VR2
                        Machine.Operand vr2 = newVR();
                        Machine.Operand r2 = Arm.Reg.getR(GPRs.r2);
                        new MIMove(vr2, r2, curMB);
                        //move param2 to r2
                        if (value2opd.containsKey(param_list.get(2))) {
                            new MIMove(r2, value2opd.get(param_list.get(2)), curMB);
                        }
                    }
                    if (param_list.size() > 3) {
                        //move r3 to VR3
                        Machine.Operand vr3 = newVR();
                        Machine.Operand r3 = Arm.Reg.getR(GPRs.r3);
                        new MIMove(vr3, r3, curMB);
                        //move param3 to r3
                        if (value2opd.containsKey(param_list.get(3))) {
                            new MIMove(r3, value2opd.get(param_list.get(3)), curMB);
                        }
                    }
                    if (param_list.size() > 4) {
                        //push
                        for (int i = 4; i < param_list.size(); i++) {
                            Value param = param_list.get(i);
                            int offset_imm = (i - 3) * -4;
                            Machine.Operand data = value2opd.get(param);
                            Machine.Operand addr = Arm.Reg.getR(GPRs.sp);
                            Machine.Operand offset = new Machine.Operand(I32, offset_imm);
                            new MIStore(data, addr, offset, curMB);
                        }
                    }
                    // 栈空间移位
                    func2mcFunc.get(call_inst.getFunc()).addStack((param_list.size() - 4) * 4);
                    Machine.Operand dOp = new Machine.Operand(new Arm.Reg(I32, sp));
                    Machine.Operand lOp = dOp;
                    Machine.Operand rOp = new Machine.Operand(I32, (param_list.size() - 4) * 4);
                    new MIBinary(MachineInst.Tag.Sub, dOp, lOp, rOp, curMB);
                    // call
                    new MICall(func2mcFunc.get(call_inst.getFunc()), curMB);
                    // 这行是取返回值
                    new MIMove(getVR_may_imm(call_inst), Arm.Reg.getR(r0), curMB);
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
        for (Machine.Block mb : nextBlockList) {
            genBB(mb.bb);
        }
    }

    private void dealExternalFunc(Instr.Call call, Function func) {
        // getint()临时用
        Machine.McFunction mf = func2mcFunc.get(func);
        assert mf != null;
        // 应该在外面写下面这一段
        // if (func.hasRet()) {
        //     Machine.Operand vr = getVR_no_imm(call);
        //     new MIMove(vr, Arm.Reg.getR(r0), curMB);
        // }
        new MICall(mf, curMB);
        // return;
    }

    public void genGlobal() {
        for (Map.Entry<GlobalVal.GlobalValue, Initial> entry : globalMap.entrySet()) {
            GlobalVal.GlobalValue globalValue = entry.getKey();
            // Initial init = entry.getValue();
            // TODO for yyf
            //load global addr at the head of the entry bb
            MIGlobal new_inst = new MIGlobal(globalValue, curMachineFunc.getBeginMB());
            //allocate virtual reg
            Machine.Operand vr = newVR(globalValue);
            new_inst.dOpd = vr;
        }
    }

    private void genBinaryInst(Instr.Alu instr) {
        MachineInst.Tag tag = MachineInst.Tag.map.get(instr.getOp());
        Value lhs = instr.getRVal1();
        Value rhs = instr.getRVal2();
        //div+mode optimize
        if (tag == MachineInst.Tag.Div && rhs.isConstantInt()) {
            divOptimize(instr);
            return;
        }
        Machine.Operand lVR = getVR_may_imm(lhs);
        Machine.Operand rVR = getVR_may_imm(rhs);
        // instr不可能是Constant
        Machine.Operand dVR = getVR_no_imm(instr);
        new MIBinary(tag, dVR, lVR, rVR, curMB);
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
            //q = n
            new MIMove(q, n, curMB);

        } else if (Math.abs(d) == (2 << l)) {
            //q = SRA(n+SRL(SRA(n,l-1),N-l),l)
            //dst1 = SRA(n,l-1)
            Machine.Operand dst1 = newVR();
            Arm.Shift shift1 = new Arm.Shift(Arm.ShiftType.Asr, l - 1);
            new MIMove(dst1, n, shift1, curMB);
            //dst2 = SRL(dst1,N-l)
            Machine.Operand dst2 = newVR();
            Arm.Shift shift2 = new Arm.Shift(Arm.ShiftType.Lsr, N - l);
            new MIMove(dst2, dst1, shift2, curMB);
            //dst3 = n+dst2
            Machine.Operand dst3 = newVR();
            new MIBinary(MachineInst.Tag.Add, dst3, n, dst2, curMB);
            //q = SRA(dst3,l)
            Arm.Shift shift3 = new Arm.Shift(Arm.ShiftType.Asr, l);
            new MIMove(q, dst3, shift3, curMB);
        } else if (m < (2 << (N - 1))) {
            //q = SRA(MULSH(m,n),sh_post)-XSIGN(n)
            //dst1 = MULSH(m,n)
            Machine.Operand m_op = new Machine.Operand(I32, (int) m);
            Machine.Operand dst1 = newVR();
            new MILongMul(dst1, n, m_op, curMB);
            //dst2 = SRA(dst1,sh_post)
            Machine.Operand dst2 = newVR();
            Arm.Shift shift2 = new Arm.Shift(Arm.ShiftType.Asr, sh_post);
            new MIMove(dst2, dst1, shift2, curMB);
            //dst3 = -XSIGN(n)
            Machine.Operand dst3 = newVR();
            new MICompare(n, new Machine.Operand(I32, 0), curMB);
            new MIMove(Lt, dst3, new Machine.Operand(I32, 1), curMB);
            new MIMove(Lt, dst3, new Machine.Operand(I32, 0), curMB);
            //q = dst2+dst3
            new MIBinary(MachineInst.Tag.Add, q, dst2, dst3, curMB);
        } else {
            //q = SRA(n+MULSH(m-2^N,n),sh_post)-XSIGN(n)
            //dst1 = MULSH(m-2^N,n)
            Machine.Operand m_op = new Machine.Operand(I32, (int) (m - (2 << N)));
            Machine.Operand dst1 = newVR();
            new MILongMul(dst1, n, m_op, curMB);
            //dst2 = n+dst1
            Machine.Operand dst2 = newVR();
            new MIBinary(MachineInst.Tag.Add, dst2, n, dst1, curMB);
            //dst3 = SRA(dst2,sh_post)
            Machine.Operand dst3 = newVR();
            Arm.Shift shift = new Arm.Shift(Arm.ShiftType.Asr, sh_post);
            new MIMove(dst3, dst2, shift, curMB);
            //dst4 = -XSIGN(n)
            Machine.Operand dst4 = newVR();
            new MICompare(n, new Machine.Operand(I32, 0), curMB);
            new MIMove(Lt, dst4, new Machine.Operand(I32, 1), curMB);
            new MIMove(Lt, dst4, new Machine.Operand(I32, 0), curMB);
            //q = dst3+dst4
            new MIBinary(MachineInst.Tag.Add, q, dst3, dst4, curMB);
        }
        if (d < 0) {
            //q=-q
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
        // if(value instanceof GlobalVal.GlobalValue){
        //
        // }
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
            // 目前是无脑用两条mov指令把立即数转到寄存器
            assert value instanceof Constant.ConstantInt;
            return getImmVR((int) ((Constant) value).getConstVal());
        } else {
            return getVR_no_imm(value);
        }
    }

}
