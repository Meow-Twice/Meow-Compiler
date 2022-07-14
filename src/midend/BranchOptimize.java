package midend;

import mir.Function;

import java.util.ArrayList;

public class BranchOptimize {

    private ArrayList<Function> functions;

    public BranchOptimize(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        RemoveUselessJump();
    }

    private void RemoveUselessJump() {
        for (Function function: functions) {
            removeUselessJumpForFunc(function);
        }
    }

    private void removeUselessJumpForFunc(Function function) {

    }
}
