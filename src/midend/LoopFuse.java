package midend;

import mir.Function;
import mir.Loop;
import mir.Value;

import java.util.ArrayList;
import java.util.HashSet;

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
        Loop loop = function.getBeginBB().getLoop();
        solve(loop);
    }

    private void solve(Loop loop) {
        fuseChildrenLoopInLoop(loop);
        for (Loop next: loop.getChildrenLoops()) {
            solve(next);
        }
    }

    private void fuseChildrenLoopInLoop(Loop loop) {
        HashSet<Loop> ret = new HashSet<>();
        for (Loop temp: loop.getChildrenLoops()) {
            mergeInto(temp, ret);
        }
    }

    private void mergeInto(Loop loop, HashSet<Loop> loops) {
        if (!loop.isIdcSet()) {
            loops.add(loop);
            return;
        }
        for (Loop temp: loops) {
            if (!temp.isIdcSet()) {
                continue;
            }
            if (tryMergeAtoB(loop, temp)) {
                return;
            }
        }
        loops.add(loop);
    }

    //成功merge则返回true
    private boolean tryMergeAtoB(Loop A, Loop B) {
        Value idcInitA = A.getIdcInit();

        return true;
    }

}
