package midend;

import mir.*;

import java.util.ArrayList;

public class GepFuse {

    //考虑不能融合的情况?
    //TODO:是否存在不能融合的情况?
    private ArrayList<Function> functions;

    public GepFuse(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        gepFuse();
    }

    private void gepFuse() {
        for (Function function: functions) {
            gepFuseForFunc(function);
        }
    }

    private void gepFuseForFunc(Function function) {
        for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
            for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                if (instr instanceof Instr.GetElementPtr && isZeroOffsetGep((Instr.GetElementPtr) instr)) {
                    instr.modifyAllUseThisToUseA(((Instr.GetElementPtr) instr).getPtr());
                }
            }
        }
    }

    private boolean isZeroOffsetGep(Instr.GetElementPtr gep) {
        if (!(gep.getPtr() instanceof Instr.GetElementPtr)) {
            return false;
        }
        ArrayList<Value> values = gep.getIdxList();
        for (Value value: values) {
            if (!(value instanceof Constant)) {
                return false;
            }
            int val = (int) ((Constant) value).getConstVal();
            if (val != 0) {
                return false;
            }
        }
        return true;
    }
}
