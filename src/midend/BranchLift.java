package midend;

import mir.BasicBlock;
import mir.Function;

import java.util.ArrayList;

public class BranchLift {

    private ArrayList<Function> functions;

    public BranchLift(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        branchLift();
    }

    private void branchLift() {
        for (Function function: functions) {
            branchLiftForFunc(function);
        }
    }

    private void branchLiftForFunc(Function function) {
        for (BasicBlock head: function.getLoopHeads()) {
            head.getLoop().cloneToFunc(function);
            head.getLoop().fix();
        }
    }
}
