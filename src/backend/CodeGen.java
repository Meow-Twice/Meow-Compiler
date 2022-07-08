package backend;

import frontend.semantic.Initial;
import lir.Machine;
import manage.Manager;
import mir.BasicBlock;
import mir.Function;
import mir.GlobalVal;
import mir.Value;
import util.DoublelyLinkedList;

import java.util.*;
import java.util.HashMap;

public class CodeGen {

    private static Machine.McFunction curMachineFunc;
    private static Function curFunc;
    private HashMap<String, Function> midFuncMap;
    public HashMap<Value, Machine.Operand> value2opd;
    public HashMap<Value, Machine.Operand> opd2value;
    public ArrayList<Machine.McFunction> mcFuncList;
    public HashMap<Function, Machine.McFunction> func2mcFunc;
    public HashMap<BasicBlock, Machine.Block> bb2mb;
    private HashMap<GlobalVal.GlobalValue, Initial> globalMap;
    private Machine.Block curMB;

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
            while (!bb.equals(endBB)) {
                Machine.Block mb = new Machine.Block(bb, curMachineFunc);
                bb.setMB(mb);
                // bb2mb.put(bb, mb);
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
