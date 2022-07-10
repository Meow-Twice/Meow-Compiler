package midend;

import mir.BasicBlock;
import mir.Function;
import mir.Instr;
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

    //标记loop的 entering header exiting latch exit
    private void makeInfoForFunc(Function function) {
        HashSet<BasicBlock> know = new HashSet<>();
        BasicBlock entry = function.getBeginBB();
        DFS(entry, know);
    }

    private void DFS(BasicBlock bb, HashSet<BasicBlock> know) {
        if (know.contains(bb)) {
            return;
        }

        know.add(bb);

        if (bb.getLoopDep() > 0) {
            Instr instr = bb.getBeginInstr();
            Loop loop = bb.getLoop();
            while (instr.getNext() != null) {
                if (instr.isInWhileCond()) {
                    loop.addCond(instr);
                }
                instr = (Instr) instr.getNext();
            }
        }

        //entering
        if (bb.isLoopHeader()) {
            for (BasicBlock pre: bb.getPrecBBs()) {
                Loop loop = bb.getLoop();
                loop.addEntering(pre);
            }
        }

        for (BasicBlock next:bb.getSuccBBs()) {
            //后向边 latch
            if (know.contains(next)) {
                assert next.isLoopHeader();
                Loop loop = bb.getLoop();
                loop.addLatch(bb);
            }
            //出循环的边 exiting和exit
            if (next.getLoopDep() == bb.getLoopDep() - 1) {
                Loop loop = bb.getLoop();
                loop.addExiting(bb);
                loop.addExit(next);
            }
        }
    }
}
