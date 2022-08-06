package midend;

import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class AggressiveFuncGVN {

    //激进的GVN认为没有自定义函数调用的函数都可以GVN,
    // 即使它的传参有数组
    private ArrayList<Function> functions;
    private HashSet<Function> canGVN = new HashSet<>();
    private HashMap<Function, Value> use = new HashMap<>();
    private HashMap<Function, Value> def = new HashMap<>();


    public AggressiveFuncGVN(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        init();
        GVN();
    }

    private void init() {
        for (Function function: functions) {
           if (check(function)) {
               canGVN.add(function);
           }
        }
    }

    private boolean check(Function function) {
//        for (Function.Param param: function.getParams()) {
//            if (param.getType().isPointerType()) {
//                return false;
//            }
//        }
        for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
            for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                for (Value value: instr.getUseValueList()) {
                    if (value instanceof GlobalVal.GlobalValue) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void GVN() {
        for (Function function: functions) {
            BasicBlock bb = function.getBeginBB();
            RPOSearch(bb);
        }
    }

    private void RPOSearch(BasicBlock bb) {

    }
}
