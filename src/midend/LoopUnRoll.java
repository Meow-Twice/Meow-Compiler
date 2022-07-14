package midend;

import mir.Function;

import java.util.ArrayList;

public class LoopUnRoll {

    private ArrayList<Function> functions;

    public LoopUnRoll(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        GetInductionVar();
    }

    private void GetInductionVar() {
        for (Function function: functions) {
            GetInductionVarForFunc(function);
        }
    }

    private void GetInductionVarForFunc(Function function) {

    }
}
