package backend;

import frontend.lexer.Lexer;
import frontend.semantic.Initial;
import lir.*;
import lir.Machine.Operand;
import manage.Manager;
import mir.*;
import mir.type.DataType;
import mir.type.Type;

import java.math.BigInteger;
import java.util.*;

import static lir.Arm.Cond.*;
import static lir.Arm.Cond.Eq;
import static lir.Arm.Cond.Ge;
import static lir.Arm.Cond.Gt;
import static lir.Arm.Cond.Le;
import static lir.Arm.Cond.Lt;
import static lir.Arm.Cond.Ne;
import static lir.Arm.Regs.FPRs.*;
import static lir.Arm.Regs.GPRs.*;
import static lir.MachineInst.Tag.*;
import static mir.type.DataType.F32;
import static mir.type.DataType.I32;

public class CodeGen {

    public static final CodeGen CODEGEN = new CodeGen();
    public static boolean _DEBUG_OUTPUT_MIR_INTO_COMMENT = true;
    public static boolean needFPU = false;
    public static boolean optMulDiv = true;

    // 当前的Machine.McFunction
    private static Machine.McFunction curMF;

    // 当前的mir.Function
    private static mir.Function curFunc;

    // mir中func的名字到Function的Map
    private final HashMap<String, Function> fMap;

    // 每个LLVM IR为一个值, 需要有一个虚拟寄存器(或常数->立即数)与之一一对应
    // Value到Operand的Map
    public HashMap<Value, Operand> value2opd;
    public HashMap<GlobalVal.GlobalValue, Arm.Glob> globPtr2globOpd = new HashMap<>();
    public final ArrayList<Arm.Glob> globList = new ArrayList<>();

    // 如名
    public HashMap<Function, Machine.McFunction> f2mf;

    // 如名
    public HashMap<BasicBlock, Machine.Block> bb2mb;

    // 全局变量
    private final HashMap<GlobalVal.GlobalValue, Initial> globalMap;
    private Machine.Block curMB;

    // 整数数传参可使用最大个数
    public static final int rParamCnt = 4;
    // 浮点数传参可使用最大个数
    public static final int sParamCnt = 16;

    private CodeGen() {
        curFunc = null;
        curMF = null;
        fMap = Manager.MANAGER.getFunctions();
        globalMap = Manager.MANAGER.globals;
        value2opd = new HashMap<>();
        f2mf = new HashMap<>();
        bb2mb = new HashMap<>();
    }

    public void gen() {
        needFPU = Lexer.detectFloat;
        // curMachineFunc = new Machine.McFunction();
        genGlobal();
        // TODO

        for (Function func : fMap.values()) {
            Machine.McFunction mcFunc = new Machine.McFunction(func);
            f2mf.put(func, mcFunc);
            for (Function.Param p : func.getParams()) {
                if (p.getType().isFloatType()) {
                    mcFunc.floatParamCount++;
                } else {
                    mcFunc.intParamCount++;
                }
            }
        }
        for (Function func : fMap.values()) {
            // Machine.McFunction mcFunc = new Machine.McFunction(func);
            // func2mcFunc.put(func, mcFunc);
            if (func.isExternal) {
                continue;
            }
            curMF = f2mf.get(func);
            curMF.setUseLr();
            Machine.Program.PROGRAM.funcList.insertAtEnd(curMF);
            curFunc = func;
            boolean isMain = false;
            if (curFunc.getName().equals("main")) {
                isMain = true;
                Machine.Program.PROGRAM.mainMcFunc = curMF;
            }
            curMF.clearVRCount();
            curMF.clearSVRCount();
            value2opd = new HashMap<>();
            cmpInst2MICmpMap = new HashMap<>();

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
            Push(curMF);
            // Operand rOp = new Operand(I32, 0);
            // Operand mvDst = newVR();
            // I.Mov mv = new I.Mov(mvDst, rOp, curMB);
            // mv.setNeedFix(STACK_FIX.VAR_STACK);
            I.Binary bino = new I.Binary(MachineInst.Tag.Sub, Arm.Reg.getR(sp), Arm.Reg.getR(sp), new Operand(I32, 0), curMB);
            bino.setNeedFix(STACK_FIX.VAR_STACK);
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
        if (needFPU) new StackCtl.VPush(mf, curMB);
        new StackCtl.Push(mf, curMB);
    }

    private void Pop(Machine.McFunction mf) {
        new StackCtl.Pop(mf, curMB);
        if (needFPU) new StackCtl.VPop(mf, curMB);
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
                Operand opd = newSVR();
                value2opd.put(param, opd);
                if (sIdx < sParamCnt) {
                    new V.Mov(opd, Arm.Reg.getS(sIdx), curMB);
                    sTop = sIdx + 1;
                } else {
                    /**
                     * (rIdx - rTop) * 4: 通用参数栈的大小, rIdx <= 3 时 = 0; rIdx > 3 时恰为已使用的栈大小
                     * (sIdx - sTop) * 4: 浮点参数栈的大小, sIdx <= 16 时 = 0; sIdx > 16 时恰为已使用的栈大小
                     * 所以需要再减1
                     */
                    Operand offImm = new Operand(I32, 4 * (rTop + sTop - (rIdx + sIdx) - 1));
                    // 修栈时窥孔消除
                    Operand imm = newVR();
                    I.Mov mv = new I.Mov(imm, offImm, curMB);
                    mv.setNeedFix(STACK_FIX.FLOAT_TOTAL_STACK);
                    Operand dstAddr = newVR();
                    new I.Binary(MachineInst.Tag.Add, dstAddr, Arm.Reg.getR(sp), imm, curMB);
                    new V.Ldr(opd, dstAddr, curMB);
                    curMF.addParamStack(4);
                }
                sIdx++;
            } else {
                Operand opd = newVR();
                value2opd.put(param, opd);
                if (rIdx < rParamCnt) {
                    new I.Mov(opd, Arm.Reg.getR(rIdx), curMB);
                    rTop = rIdx + 1;
                } else {
                    /**
                     * (rIdx - rTop) * 4: 通用参数栈的大小, rIdx <= 3 时 = 0; rIdx > 3 时恰为已使用的栈大小
                     * (sIdx - sTop) * 4: 浮点参数栈的大小, sIdx <= 16 时 = 0; sIdx > 16 时恰为已使用的栈大小
                     * 所以需要再减1
                     */
                    Operand offImm = new Operand(I32, 4 * (rTop + sTop - (rIdx + sIdx) - 1));
                    Operand dst = newVR();
                    I.Mov mv = new I.Mov(dst, offImm, curMB);
                    mv.setNeedFix(STACK_FIX.INT_TOTAL_STACK);
                    // 栈顶向下的偏移, 第四个参数 -4, 第五个参数 -8 ...修的时候只需要把这个立即数的值取出来加上getStackSize获取的栈大小即可
                    new I.Ldr(opd, Arm.Reg.getR(sp), dst, curMB);
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
        INT_TOTAL_STACK, // 函数有超过四个的参数时ldr参数用(move vrx, #xxx那条)
        FLOAT_TOTAL_STACK
    }

    HashSet<Machine.Block> visitBBSet = new HashSet<>();

    public void genBB(BasicBlock bb) {
        curMB = bb.getMb();
        visitBBSet.add(curMB);
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
                    curMB.succMBs.add(mb);
                    if (!visitBBSet.contains(mb)) nextBBList.push(mb.bb);
                    new MIJump(mb, curMB);
                }
                case icmp, fcmp -> genCmp(instr);
                case branch -> {
                    Arm.Cond cond;
                    Instr.Branch brInst = (Instr.Branch) instr;
                    Instr condValue = (Instr) brInst.getCond();
                    Machine.Block trueBlock = brInst.getThenTarget().getMb();
                    Machine.Block falseBlock = brInst.getElseTarget().getMb();
                    curMB.succMBs.add(trueBlock);
                    curMB.succMBs.add(falseBlock);
                    if (!visitBBSet.contains(falseBlock)) nextBBList.push(falseBlock.bb);
                    if (!visitBBSet.contains(trueBlock)) nextBBList.push(trueBlock.bb);
                    CMPAndArmCond t = cmpInst2MICmpMap.get(condValue);
                    if (t != null) {
                        cond = t.ArmCond;
                    } else {
                        Operand condVR = getVR_may_imm(condValue);
                        new I.Cmp(condVR, new Operand(I32, 0), curMB);
                        cond = Ne;
                    }
                    new MIBranch(cond, trueBlock, falseBlock, curMB);
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
                            new I.Mov(Arm.Reg.getR(r0), retOpd, curMB);
                        } else if (returnInst.getRetValue().getType().isFloatType()) {
                            assert needFPU;
                            retDataType = F32;
                            Operand retOpd = getVR_may_imm(returnInst.getRetValue());
                            new V.Mov(Arm.Reg.getS(s0), retOpd, curMB);
                        }
                    }
                    // Operand rOp = new Operand(I32, 0);
                    // Operand mvDst = newVR();
                    // I.Mov mv = new I.Mov(mvDst, rOp, curMB);
                    // mv.setNeedFix(STACK_FIX.VAR_STACK);
                    I.Binary bino = new I.Binary(MachineInst.Tag.Add, Arm.Reg.getR(sp), Arm.Reg.getR(sp), new Operand(I32, 0), curMB);
                    bino.setNeedFix(STACK_FIX.VAR_STACK);
                    Pop(curMF);
                    if (retDataType == I32) {
                        new I.Ret(Arm.Reg.getR(r0), curMB);
                    } else if (retDataType == F32) {
                        assert needFPU;
                        new V.Ret(Arm.Reg.getS(s0), curMB);
                    } else {
                        new I.Ret(curMB);
                    }
                }
                case zext -> {
                    Operand dst = getVR_no_imm(instr);
                    Operand src = getVR_may_imm(((Instr.Zext) instr).getRVal1());
                    // TODO 已经在上一个cmp生成好了并转移到虚拟寄存器中了, 应该不用管, 待优化
                    new I.Mov(dst, src, curMB);
                }
                case fptosi -> {
                    assert needFPU;
                    // TODO 待改成立即数
                    Operand src = getVR_may_imm(((Instr.FPtosi) instr).getRVal1());
                    Operand tmp = newSVR();
                    new V.Cvt(V.CvtType.f2i, tmp, src, curMB);
                    Operand dst = getVR_no_imm(instr);
                    new V.Mov(dst, tmp, curMB);
                }
                case sitofp -> {
                    assert needFPU;
                    // TODO 待改成立即数
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
                        assert false;
                        break;
                    }
                    // 这里已经不可能Alloc一个Int或者Float了
                    assert contentType.isArrType();
                    Operand addr = getVR_no_imm(allocInst);
                    Operand spReg = Arm.Reg.getR(sp);
                    Operand offset = new Operand(I32, curMF.getVarStack());
                    Operand mvDst = newVR();
                    new I.Mov(mvDst, offset, curMB);
                    new I.Binary(MachineInst.Tag.Add, addr, spReg, mvDst, curMB);
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
                        new I.Ldr(data, addrOpd, curMB);
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
                        new I.Str(data, addr, curMB);
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
                    assert !ptrValue.isConstant();
                    Operand dstVR = getVR_no_imm(gep);
                    // Machine.Operand curAddrVR = getVR_no_imm(ptrValue);
                    Operand curAddrVR = getVR_from_ptr(ptrValue);
                    // Operand curAddrVR = newVR();
                    // new I.Mov(curAddrVR, basePtrVR, curMB);
                    int totalConstOff = 0;
                    for (int i = 0; i < offsetCount; i++) {
                        Value curIdxValue = gep.getIdxValueOf(i);
                        int offUnit = 4;
                        if (curBaseType.isArrType()) {
                            offUnit = 4 * ((Type.ArrayType) curBaseType).getFlattenSize();
                            curBaseType = ((Type.ArrayType) curBaseType).getBaseType();
                        }
                        if (curIdxValue.isConstantInt()) {
                            // 每一个常数下标都会累加到 totalConstOff 中
                            totalConstOff += offUnit * (int) ((Constant.ConstantInt) curIdxValue).getConstVal();
                            if (i == offsetCount - 1) {
                                // 这里的设计比较微妙
                                if (totalConstOff == 0) {
                                    value2opd.put(gep, curAddrVR);
                                    // new I.Mov(dstVR, curAddrVR, curMB);
                                } else {
                                    Operand imm;
                                    if (immCanCode(totalConstOff)) {
                                        imm = new Operand(I32, totalConstOff);
                                    } else {
                                        imm = getImmVR(totalConstOff);
                                    }
                                    new I.Binary(MachineInst.Tag.Add, dstVR, curAddrVR, imm, curMB);
                                }
                            }
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
                                    new I.Binary(MachineInst.Tag.Add, dst, curAddrVR, immVR, curMB);
                                    curAddrVR = dst;
                                }
                                new I.Fma(true, false, dstVR, curIdxVR, offUnitImmVR, curAddrVR, curMB);
                            } else {
                                Machine.Operand dst = newVR();
                                new I.Fma(true, false, dst, curIdxVR, offUnitImmVR, curAddrVR, curMB);
                                curAddrVR = dst;
                            }
                        }
                    }

                }
                case bitcast -> {
                    Instr.Bitcast bitcast = (Instr.Bitcast) instr;
                    Operand src = getVR_no_imm(bitcast.getSrcValue());
                    value2opd.put(bitcast, src);
                    // Operand dst = getVR_no_imm(bitcast);
                    // new I.Mov(dst, src, curMB);
                }
                case call -> {
                    dealCall((Instr.Call) instr);
                }
                case phi -> throw new AssertionError("Backend has phi: " + instr);
                case pcopy -> {
                }
                case move -> {
                    Operand source = getVR_may_imm(((Instr.Move) instr).getSrc());
                    Operand target = getVR_no_imm(((Instr.Move) instr).getDst());
                    if (source.isF32() || target.isF32()) {
                        assert needFPU;
                        new V.Mov(target, source, curMB);
                    } else {
                        new I.Mov(target, source, curMB);
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

    private void dealCall(Instr.Call call_inst) {
        // TODO : 函数内部可能调用其他函数, 但是在函数内部已经没有了Caller使用哪些寄存器的信息, 目前影响未知, 可能有bug
        // TODO: ayame解决方案是在 callee 头 push 和 callee 尾部 pop 那些 callee 要用到的寄存器, 暂定此方案
        // TODO: 如果有返回值, 则由caller保护r0或s0, 如果没有返回值则有callee保护
        ArrayList<Value> param_list = call_inst.getParamList();
        // ArrayList<Operand> paramVRList = new ArrayList<>();
        // ArrayList<Operand> paramSVRList = new ArrayList<>();
        int rIdx = 0;
        int sIdx = 0;
        int rParamTop = rIdx;
        int sParamTop = sIdx;
        for (Value p : param_list) {
            if (p.getType().isFloatType()) {
                assert needFPU;
                if (sIdx < sParamCnt) {
                                /*Operand tmpDst = newSVR();
                                paramSVRList.add(tmpDst);*/
                    Operand fpr = Arm.Reg.getS(sIdx);
                    /*new V.Mov(tmpDst, fpr, curMB);*/
                    new V.Mov(fpr, getVR_may_imm(p), curMB);
                    sParamTop = sIdx + 1;
                } else {
                    int offset_imm = (sParamTop + rParamTop - (rIdx + sIdx) - 1) * 4;
                    Operand data = getVR_may_imm(p);
                    Operand off = new Operand(I32, offset_imm);
                    if (vLdrStrImmEncode(offset_imm)) {
                        new V.Str(data, Arm.Reg.getR(sp), off, curMB);
                    } else if (immCanCode(offset_imm)) {
                        // TODO 取反
                        Operand dstAddr = newVR();
                        new I.Binary(MachineInst.Tag.Sub, dstAddr, Arm.Reg.getR(sp), new Operand(I32, offset_imm), curMB);
                        new V.Str(data, dstAddr, curMB);
                    } else if (immCanCode(-offset_imm)) {
                        // TODO 取反
                        Operand dstAddr = newVR();
                        new I.Binary(MachineInst.Tag.Sub, dstAddr, Arm.Reg.getR(sp), new Operand(I32, -offset_imm), curMB);
                        new V.Str(data, dstAddr, curMB);
                    } else {
                        Operand imm = newVR();
                        new I.Mov(imm, new Operand(I32, -offset_imm), curMB);
                        Operand dstAddr = newVR();
                        new I.Binary(MachineInst.Tag.Sub, dstAddr, Arm.Reg.getR(sp), imm, curMB);
                        new V.Str(data, dstAddr, curMB);
                    }
                }
                sIdx++;
            } else {
                if (rIdx < rParamCnt) {
                                /*Operand tmpDst = newVR();
                                paramVRList.add(tmpDst);*/
                    Operand gpr = Arm.Reg.getR(rIdx);
                    /*new I.Mov(tmpDst, gpr, curMB);*/
                    new I.Mov(gpr, getVR_may_imm(p), curMB);
                    rParamTop = rIdx + 1;
                } else {
                    int offset_imm = (sParamTop + rParamTop - (rIdx + sIdx) - 1) * 4;
                    Operand data = getVR_may_imm(p);
                    Operand addr = Arm.Reg.getR(sp);
                    // TODO 小心函数参数个数超级多, 超过立即数可以表示的大小导致的错误
                    Operand off = new Operand(I32, offset_imm);
                    if (!LdrStrImmEncode(offset_imm)) {
                        Operand immDst = newVR();
                        new I.Mov(immDst, off, curMB);
                        off = immDst;
                    }
                    new I.Str(data, addr, off, curMB);
                }
                rIdx++;
            }
        }
        // 栈空间移位
        Function callFunc = call_inst.getFunc();
        Machine.McFunction callMcFunc = f2mf.get(callFunc);
        if (callFunc.isExternal) {
            /**
             * r0实际上依据设计不一定需要保护, 因为一定是最后ret语句才会有r0的赋值
             */
            // TODO: return getint();
                        /*while (rIdx < 2) {
                            Operand tmpDst = newVR();
                            paramVRList.add(tmpDst);
                            new I.Mov(tmpDst, Arm.Reg.getR(rIdx++), curMB);
                        }
                        if (needFPU) {
                            while (sIdx < 2) {
                                Operand tmpDst = newSVR();
                                paramSVRList.add(tmpDst);
                                new V.Mov(tmpDst, Arm.Reg.getS(sIdx++), curMB);
                            }
                        }*/
            Machine.McFunction mf = f2mf.get(callFunc);
            assert mf != null;
            // Push(mf);
            new MICall(mf, curMB);
            // Pop(mf);
        } else {
            if (callMcFunc == null) {
                throw new AssertionError("Callee is null");
            }
            /**
             * r0实际上依据设计不一定需要保护, 因为一定是最后ret语句才会有r0的赋值
             */
            // TODO: return getint();
                        /*if (rIdx == 0 && call_inst.getType().isInt32Type()) {
                            Operand tmpDst = newVR();
                            paramVRList.add(tmpDst);
                            new I.Mov(tmpDst, Arm.Reg.getR(r0), curMB);
                        }
                        if (sIdx == 0 && call_inst.getType().isFloatType()) {
                            Operand tmpDst = newSVR();
                            paramSVRList.add(tmpDst);
                            new V.Mov(tmpDst, Arm.Reg.getS(s0), curMB);
                        }*/
            // Operand rOp1 = new Operand(I32, 0);
            // Operand mvDst1 = newVR();
            // I.Mov mv1 = new I.Mov(mvDst1, rOp1, curMB);
            // mv1.setNeedFix(callMcFunc, STACK_FIX.ONLY_PARAM);
            I.Binary bino = new I.Binary(MachineInst.Tag.Sub, Arm.Reg.getR(sp), Arm.Reg.getR(sp), new Operand(I32, 0), curMB);
            bino.setNeedFix(callMcFunc, STACK_FIX.ONLY_PARAM);
            // call
            new MICall(callMcFunc, curMB);
            // Operand rOp2 = new Operand(I32, 0);
            // Operand mvDst2 = newVR();
            // I.Mov mv2 = new I.Mov(mvDst2, rOp2, curMB);
            // mv2.setNeedFix(callMcFunc, STACK_FIX.ONLY_PARAM);
            I.Binary bino2 = new I.Binary(MachineInst.Tag.Add, Arm.Reg.getR(sp), Arm.Reg.getR(sp), new Operand(I32, 0), curMB);
            bino2.setNeedFix(callMcFunc, STACK_FIX.ONLY_PARAM);
        }
        if (call_inst.getType().isInt32Type()) {
            new I.Mov(getVR_no_imm(call_inst), Arm.Reg.getR(r0), curMB);
        } else if (call_inst.getType().isFloatType()) {
            assert needFPU;
            new V.Mov(getVR_no_imm(call_inst), Arm.Reg.getS(s0), curMB);
        } else if (!call_inst.getType().isVoidType()) {
            throw new AssertionError("Wrong ret type");
        }
                    /*// 需要把挪走的r0-rx再挪回来
                    for (int i = 0; i < paramVRList.size(); i++) {
                        new I.Mov(Arm.Reg.getR(i), paramVRList.get(i), curMB);
                    }
                    // 需要把挪走的s0-sx再挪回来
                    for (int i = 0; i < paramSVRList.size(); i++) {
                        assert needFPU;
                        new V.Mov(Arm.Reg.getS(i), paramSVRList.get(i), curMB);
                    }*/
    }

    public static boolean LdrStrImmEncode(int off) {
        return off < 4096 && off > -4096;
    }

    public static boolean vLdrStrImmEncode(int off) {
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
            globPtr2globOpd.put(globalValue, glob);
        }
    }

    private boolean isFBino(Instr.Alu.Op op) {
        return op == Instr.Alu.Op.FSUB || op == Instr.Alu.Op.FADD || op == Instr.Alu.Op.FDIV || op == Instr.Alu.Op.FMUL || op == Instr.Alu.Op.FREM;
    }

    private void genBinaryInst(Instr.Alu instr) {
        final int bitsOfInt = 32;
        MachineInst.Tag tag = MachineInst.Tag.map.get(instr.getOp());
        Value lhs = instr.getRVal1();
        Value rhs = instr.getRVal2();
        // instr不可能是Constant
        Machine.Operand dVR = getVR_no_imm(instr);
        if (tag == Mod) {
            Machine.Operand lVR = getVR_may_imm(lhs);
            Machine.Operand rVR = getVR_may_imm(rhs);
            Machine.Operand dst1 = newVR();
            new I.Binary(MachineInst.Tag.Div, dst1, lVR, rVR, curMB);
            Machine.Operand dst2 = newVR();
            new I.Binary(MachineInst.Tag.Mul, dst2, dst1, rVR, curMB);
            new I.Binary(MachineInst.Tag.Sub, dVR, lVR, dst2, curMB);
        } else if (optMulDiv && tag == Mul && (lhs.isConstantInt() || rhs.isConstantInt())) {
            // 不考虑双立即数: r*i, i*r, r*r
            if (lhs.isConstantInt() && rhs.isConstantInt()) {
                System.err.println("[MUL dst, imm, imm] should never occur @ " + instr);
                System.exit(91);
            }
            Machine.Operand srcOp;
            int imm;
            if (lhs.isConstantInt()) {
                srcOp = getVR_may_imm(rhs);
                imm = (int) ((Constant.ConstantInt) lhs).getConstVal();
            } else {
                srcOp = getVR_may_imm(lhs);
                imm = (int) ((Constant.ConstantInt) rhs).getConstVal();
            }
            int abs = (imm < 0) ? (-imm) : imm;
            if (abs == 0) {
                new I.Mov(dVR, new Operand(I32, 0), curMB); // dst = 0
            } else if ((abs & (abs - 1)) == 0) {
                // imm 是 2 的幂
                int sh = bitsOfInt - 1 - Integer.numberOfLeadingZeros(abs);
                // dst = src << sh
                if (sh > 0) {
                    new I.Mov(dVR, srcOp, new Arm.Shift(Arm.ShiftType.Lsl, sh), curMB);
                } else {
                    new I.Mov(dVR, srcOp, curMB);
                }
                if (imm < 0) {
                    // dst = -dst
                    // TODO: 源操作数和目的操作数虚拟寄存器相同，不一定不会出 bug
                    new I.Binary(Rsb, dVR, dVR, new Operand(I32, 0), curMB); // dst = 0 - dst
                }
            } else {
                new I.Binary(tag, dVR, srcOp, getImmVR(imm), curMB);
            }
        } else if (optMulDiv && tag == Div && rhs.isConstantInt()) {
            // 不允许双立即数, 只能是 r/i
            // TODO: ayame 的 divMap 机制目前未实现
            if (lhs.isConstantInt()) {
                System.err.println("[DIV dst, imm, imm] should never occur @ " + instr);
                System.exit(94);
            }
            Machine.Operand lVR = getVR_may_imm(lhs);
            int imm = (int) ((Constant.ConstantInt) rhs).getConstVal();
            int abs = (imm < 0) ? (-imm) : imm;
            if (abs == 0) {
                System.exit(94);
            } else if (imm == 1) {
                new I.Mov(dVR, lVR, curMB);
            } else if (imm == -1) {
                new I.Binary(Rsb, dVR, lVR, new Operand(I32, 0), curMB);
            } else if ((abs & (abs - 1)) == 0) {
                // 除以 2 的幂
                // src < 0 且不整除，则 (lhs >> sh) + 1 == (lhs / div)，需要修正
                int sh = bitsOfInt - 1 - Integer.numberOfLeadingZeros(abs);
                // sgn = (lhs >>> 31), (lhs < 0 ? -1 : 0)
                Operand sgn = newVR();
                new I.Mov(sgn, lVR, new Arm.Shift(Arm.ShiftType.Asr, bitsOfInt - 1), curMB);
                // 修正负数右移和除法的偏差
                // tmp = lhs + (sgn >> (32 - sh))
                Operand tmp = newVR();
                new I.Binary(Add, tmp, lVR, sgn, new Arm.Shift(Arm.ShiftType.Lsr, bitsOfInt - sh), curMB);
                // quo = tmp >>> sh
                new I.Mov(dVR, tmp, new Arm.Shift(Arm.ShiftType.Asr, sh), curMB);
            } else {
                /*
                 * Reference: https://github.com/ridiculousfish/libdivide
                 *   - struct libdivide_s32_branchfree_t
                 *   - libdivide_s32_branchfree_gen
                 *   - libdivide_internal_s32_gen
                 *   - libdivide_s32_branchfree_do
                 *
                 * In SysY, we only consider signed-32bit division
                 *
                 * Use branch-free version to avoid generating new basic blocks
                 */
                int magic, more; // struct libdivide_s32_t {int magic; uint8_t more;}
                int log2d = bitsOfInt - 1 - Integer.numberOfLeadingZeros(abs);
                // libdivide_s32_branchfree_gen => process in compiler
                // libdivide_internal_s32_gen(d, 1) => {magic, more}
                final int negativeDivisor = 128;
                final int addMarker = 64;
                final int s32ShiftMask = 31;
                // masks for type cast
                final long uint32Mask = 0xFFFFFFFFL;
                final int uint8Mask = 0xFF;
                if ((abs & (abs - 1)) == 0) {
                    magic = 0;
                    more = (imm < 0 ? (log2d | negativeDivisor) : log2d) & uint8Mask; // more is uint8_t
                } else {
                    assert log2d >= 1;
                    int rem, proposed;
                    // proposed = libdivide_64_div_32_to_32((uint32_t)1 << (log2d - 1), 0, abs, &rem);
                    // q = libdivide_64_div_32_to_32(u1, u0, v, &r)
                    // n = {u1, u0}, u1 = ((uint32_t)1 << (log2d - 1))
                    BigInteger n = BigInteger.valueOf((1 << (log2d - 1)) & uint32Mask).shiftLeft(bitsOfInt).or(BigInteger.valueOf(0));
                    BigInteger[] div = n.divideAndRemainder(BigInteger.valueOf(abs));
                    proposed = div[0].intValueExact();
                    rem = div[1].intValueExact();
                    proposed += proposed;
                    int twiceRem = rem + rem;
                    // twice_rem, absD, rem in libdivide is uint32, so the compare below should also base on uint32
                    if ((twiceRem & uint32Mask) >= (abs & uint32Mask) || (twiceRem & uint32Mask) < (rem & uint32Mask)) {
                        proposed += 1;
                    }
                    more = (log2d | addMarker) & uint8Mask;
                    proposed += 1;
                    magic = proposed;
                    if (imm < 0) {
                        more |= negativeDivisor;
                    }
                }
                // {magic, more} got
                int sh = more & s32ShiftMask;
                int mask = (1 << sh), sign = ((more & (0x80)) != 0) ? -1 : 0, isPower2 = (magic == 0) ? 1 : 0;
                // libdivide_s32_branchfree_do => process in runtime, use hardware instruction
                Operand q = newVR(); // quotient
                new I.Binary(LongMul, q, lVR, getImmVR(magic), curMB); // q = mulhi(dividend, magic)
                new I.Binary(Add, q, q, lVR, curMB); // q += dividend
                // q += (q >>> 31) & (((uint32_t)1 << shift) - is_power_of_2)
                Operand q1 = newVR();
                new I.Binary(And, q1, getImmVR(mask - isPower2), q, new Arm.Shift(Arm.ShiftType.Asr, 31), curMB);
                new I.Binary(Add, q, q, q1, curMB);
                // q = q >>> shift
                new I.Mov(q, q, new Arm.Shift(Arm.ShiftType.Asr, sh), curMB);
                if (sign < 0) {
                    new I.Binary(Rsb, q, q, new Operand(I32, 0), curMB);
                }
                new I.Mov(dVR, q, curMB); // store result
                // new I.Binary(tag, dVR, lVR, getImmVR(imm), curMB);
            }
        } else {
            Machine.Operand lVR = getVR_may_imm(lhs);
            Machine.Operand rVR = getVR_may_imm(rhs);
            if (tag == FMod) {
                assert needFPU;
                Operand dst1 = newSVR();
                new V.Binary(MachineInst.Tag.FDiv, dst1, lVR, rVR, curMB);
                Operand dst2 = newSVR();
                new V.Binary(MachineInst.Tag.FMul, dst2, dst1, rVR, curMB);
                new V.Binary(MachineInst.Tag.FSub, dVR, lVR, dst2, curMB);
            } else if (isFBino(instr.getOp())) {
                assert needFPU;
                new V.Binary(tag, dVR, lVR, rVR, curMB);
            } else {
                new I.Binary(tag, dVR, lVR, rVR, curMB);
            }
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
            I.Cmp cmp = new I.Cmp(lVR, rVR, curMB);
            int condIdx = icmp.getOp().ordinal();
            Arm.Cond cond = Arm.Cond.values()[condIdx];
            // Icmp或Fcmp后紧接着BranchInst，而且前者的结果仅被后者使用，那么就可以不用计算结果，而是直接用bxx的指令
            if (((Instr) icmp.getNext()).isBranch()
                    && icmp.onlyOneUser()
                    && icmp.getBeginUse().getUser().isBranch() && icmp.getNext().equals(icmp.getBeginUse().getUser())) {
                cmpInst2MICmpMap.put(instr, new CMPAndArmCond(cmp, cond));
            } else {
                new I.Mov(cond, dst, new Operand(I32, 1), curMB);
                new I.Mov(getIcmpOppoCond(cond), dst, new Operand(I32, 0), curMB);
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
                new I.Mov(cond, dst, new Operand(I32, 1), curMB);
                new I.Mov(getFcmpOppoCond(cond), dst, new Operand(I32, 0), curMB);
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
        int i = 0;
        do {
            int n = (imm << (2 * i)) | (imm >>> (32 - 2 * i));
            if ((n & ~0xFF) == 0) return true;
        } while (i++ < 16);
        return false;
    }

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
        new I.Mov(dst, immOpd, curMB);
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
        new I.Mov(labelAddr, glob, curMB);
        new V.Ldr(dst, labelAddr, curMB);
        return dst;
    }

    /**
     * 可能是立即数的 Operand 获取
     */
    public Operand getOp_may_imm(Value value) {
        if (value instanceof Constant.ConstantInt || value instanceof Constant.ConstantBool) {
            return new Operand(I32, (int) ((Constant) value).getConstVal());
        } else {
            return getVR_may_imm(value);
        }
    }

    /**
     * 可能是立即数的vr获取
     */
    public Operand getVR_may_imm(Value value) {
        if (value instanceof Constant.ConstantInt || value instanceof Constant.ConstantBool) {
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
            Arm.Glob glob = globPtr2globOpd.get((GlobalVal.GlobalValue) value);
            new I.Mov(addr, glob, curMB);
            // 取出来的Operand 是立即数类型
            return addr;
        } else {
            // TODO 这里应该不可能是常数
            return getVR_no_imm(value);
        }
    }

}
