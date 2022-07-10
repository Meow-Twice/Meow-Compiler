package midend;

import mir.*;

import java.util.ArrayList;

public class LCSSA {

    private ArrayList<Function> functions;

    public LCSSA(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {

    }

    private void addPhi() {
        for (Function function: functions) {
            addPhiForFunc(function);
        }
    }

    private void addPhiForFunc(Function function) {
        for (BasicBlock bb: function.getLoopHeads()) {
            addPhiForLoop(bb.getLoop());
        }
    }

    private void addPhiForLoop(Loop loop) {

    }

    //判断value是否在loop外被使用
    private boolean usedOutLoop(Value value, Loop loop) {
        Use use = value.getBeginUse();
        while (use.getNext() != null) {
            if (!use.getUser().parentBB().getLoop().equals(loop)) {
                return true;
            }
            use = (Use) use.getNext();
        }
        return false;
    }
}
