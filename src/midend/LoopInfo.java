package midend;

import mir.BasicBlock;
import mir.Function;
import mir.Loop;

import java.util.ArrayList;
import java.util.HashSet;

public class LoopInfo {

    //TODO:

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
        HashSet<BasicBlock> know = new HashSet<>();
        BasicBlock entry = function.getBeginBB();
        DFS(entry, know);
    }

    private void DFS(BasicBlock bb, HashSet<BasicBlock> know) {


        for (BasicBlock next:bb.getSuccBBs()) {
            if (know.contains(next)) {
                assert next.isLoopHeader();
                Loop loop = bb.getLoop();
                loop.addLatch(bb);
            }
        }
    }
}
