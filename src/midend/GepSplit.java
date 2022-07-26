package midend;

import mir.BasicBlock;
import mir.Function;
import mir.Instr;

import java.util.ArrayList;

public class GepSplit {
    private ArrayList<Function> functions;
    public GepSplit(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        gepSplit();
    }

    private void gepSplit() {
        for (Function function: functions) {

        }
    }

    private void gepSplitForFunc(Function function) {
        for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
            for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                if (instr instanceof Instr.GetElementPtr && ((Instr.GetElementPtr) instr).getIdxList().size() > 2) {
                    split((Instr.GetElementPtr) instr);
                }
            }
        }
    }

    private void split(Instr.GetElementPtr gep) {

    }
}
