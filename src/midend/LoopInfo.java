package midend;

import mir.Function;
import mir.Loop;

import java.util.ArrayList;

public class LoopInfo {

    private ArrayList<Function> functions;

    public LoopInfo(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        makeInfo();

    }

    private void makeInfo() {
        for (Function function: functions) {
            makeInfoForFunc(function);
        }
    }

    //标定loop的 entering header exiting latch exit
    private void makeInfoForFunc(Function function) {

    }
}
