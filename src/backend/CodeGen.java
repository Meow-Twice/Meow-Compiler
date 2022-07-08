package backend;

import frontend.semantic.Initial;
import lir.*;
import manage.Manager;
import mir.*;

import java.util.*;
import java.util.HashMap;

public class CodeGen {

    public static boolean _DEBUG_OUTPUT_MIR_INTO_COMMENT = true;

    private static Machine.McFunction curMachineFunc;
    private static Function curFunc;
    private HashMap<String, Function> midFuncMap;
    public HashMap<Value, Machine.Operand> value2opd;
    public HashMap<Machine.Operand, Value> opd2value;
    public ArrayList<Machine.McFunction> mcFuncList;
    public HashMap<Function, Machine.McFunction> func2mcFunc;
    public HashMap<BasicBlock, Machine.Block> bb2mb;
    private HashMap<GlobalVal.GlobalValue, Initial> globalMap;
    private Machine.Block curMB;
    private int virtual_cnt = 0;


    public CodeGen() {
        curFunc = null;
        curMachineFunc = null;
        midFuncMap = Manager.MANAGER.getFunctions();
        globalMap = Manager.MANAGER.globals;
        value2opd = new HashMap<>();
        opd2value = new HashMap<>();
        mcFuncList = new ArrayList<>();
        func2mcFunc = new HashMap<>();
        bb2mb = new HashMap<>();
    }

    public void gen() {
        genGlobal();
        // TODO
        for (Function func : midFuncMap.values()) {

            Machine.McFunction mcFunc = new Machine.McFunction(func);
            curFunc = func;
            curMachineFunc = mcFunc;
            mcFuncList.add(mcFunc);
            func2mcFunc.put(func, mcFunc);

            BasicBlock bb = func.getBeginBB();
            BasicBlock endBB = func.getEndBB();
            // 先造出来防止br找不到目标
            while (!bb.equals(endBB)) {
                Machine.Block mb = new Machine.Block(bb, curMachineFunc);
                bb.setMB(mb);
                bb2mb.put(bb, mb);
                bb = (BasicBlock) bb.getNext();
            }
            bb = func.getBeginBB();
            endBB = func.getEndBB();
            while (!bb.equals(endBB)) {
                curMB = bb.getMb();
                genBB(bb);
                bb = (BasicBlock) bb.getNext();
            }
        }
    }

    public void genBB(BasicBlock bb) {
        Instr instr = bb.getBeginInstr();
        Instr endInstr = bb.getEndInstr();
        while (!instr.equals(endInstr)) {
            if (_DEBUG_OUTPUT_MIR_INTO_COMMENT) {
                new MIComment(instr.toString(), curMB);
            }
            if (instr instanceof Instr.Alu) {
                genBinaryInst((Instr.Alu) instr);
            } else if (instr instanceof Instr.Jump) {
                BasicBlock tarBB = ((Instr.Jump) instr).getTarget();
                new MIJump(tarBB.getMb(), curMB);
            } else if(instr instanceof Instr.Branch){

            }
            instr = (Instr) instr.getNext();
        }
    }

    public Machine.Operand newVR() {
        return new Machine.Operand(virtual_cnt++);
    }

    public Machine.Operand newVR(Value value) {
        Machine.Operand opd = new Machine.Operand(virtual_cnt++);
        opd2value.put(opd, value);
        value2opd.put(value, opd);
        return opd;
    }

    public Machine.Operand getVR_no_imm(Value value) {
        Machine.Operand opd = value2opd.get(value);
        return opd == null ? newVR(value) : opd;
    }

    public Machine.Operand getVR_may_imm(Value value) {
        if (value instanceof Constant) {
            // TODO for yyf, 目前是无脑用一条move指令把立即数转到寄存器
            assert value instanceof Constant.ConstantInt;
            Machine.Operand dst = newVR();
            Machine.Operand imm = new Machine.Operand((int) ((Constant) value).getConstVal());
            new MIMove(dst, imm, curMB);
            return dst;
        } else {
            return getVR_no_imm(value);
        }
    }

    private void genBinaryInst(Instr.Alu instr) {
        Value lhs = instr.getRVal1();
        Value rhs = instr.getRVal2();
        Machine.Operand lVR = getVR_may_imm(lhs);
        Machine.Operand rVR = getVR_may_imm(rhs);
        Machine.Operand dVR = getVR_no_imm(instr);
        Tag tag = Tag.map.get(instr.getOp());
        new MachineBinary(tag, dVR, lVR, rVR, curMB);
    }

    public void genGlobal() {
        for (Map.Entry<GlobalVal.GlobalValue, Initial> entry : globalMap.entrySet()) {
            GlobalVal.GlobalValue glob = entry.getKey();
            Initial init = entry.getValue();
            // TODO for yyf
        }
    }

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
}
