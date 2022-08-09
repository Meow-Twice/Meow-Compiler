package midend;

import mir.BasicBlock;
import mir.Function;
import mir.Loop;
import mir.Value;

import java.util.ArrayList;
import java.util.HashSet;

public class LoopFuse {

    private ArrayList<Function> functions;
    private HashSet<Loop> loops = new HashSet<>();
    private HashSet<Loop> removes = new HashSet<>();

    public LoopFuse(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        init();
        loopFuse();
    }

    private void init() {
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                if (bb.isLoopHeader()) {
                    loops.add(bb.getLoop());
                }
            }
        }
    }

    private void loopFuse() {
        for (Loop loop: loops) {
            tryFuseLoop(loop);
        }
    }

    private void tryFuseLoop(Loop preLoop) {
        if (!preLoop.isSimpleLoop() || !preLoop.isIdcSet()) {
            return;
        }
        BasicBlock preExit = null;
        for (BasicBlock bb: preLoop.getExits()) {
            preExit = bb;
        }
        if (preExit.getSuccBBs().size() != 1) {
            return;
        }
        if (!preExit.getSuccBBs().get(0).isLoopHeader()) {
            return;
        }
        Loop sucLoop = preExit.getSuccBBs().get(0).getLoop();
        if (!sucLoop.isSimpleLoop() || !sucLoop.isIdcSet()) {
            return;
        }
        if (!preLoop.getIdcInit().equals(sucLoop.getIdcInit()) ||
                !preLoop.getIdcStep().equals(sucLoop.getIdcStep()) ||
                !preLoop.getIdcEnd().equals(sucLoop.getIdcEnd())) {
            return;
        }

    }

}
