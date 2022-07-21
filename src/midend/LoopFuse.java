package midend;

import mir.Function;

import java.util.ArrayList;

public class LoopFuse {

    private ArrayList<Function> functions;

    public LoopFuse(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        for (Function function: functions) {
            loopFuseForFunc(function);
        }
    }

    private void loopFuseForFunc(Function function) {

    }

}
