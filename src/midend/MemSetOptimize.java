package midend;

import mir.Function;

import java.util.ArrayList;

public class MemSetOptimize {

    private ArrayList<Function> functions;

    public MemSetOptimize(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        initLoopToMemSet();
    }

    private void initLoopToMemSet() {
        for (Function function: functions) {

        }
    }


}
