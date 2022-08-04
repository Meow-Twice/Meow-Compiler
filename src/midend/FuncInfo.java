package midend;

import mir.*;

import java.util.ArrayList;

public class FuncInfo {

    private ArrayList<Function> functions;

    public FuncInfo(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        for (Function function: functions) {
            boolean ret = checkCanGVN(function);
            function.setCanGVN(ret);
        }
    }

    private boolean checkCanGVN(Function function) {
        for (Function.Param param: function.getParams()) {
            if (param.getType().isPointerType()) {
                return false;
            }
        }
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
}
