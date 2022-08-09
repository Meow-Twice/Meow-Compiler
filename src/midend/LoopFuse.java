package midend;

import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
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

        if (preLoop.getNowLevelBB().size() > 2 || sucLoop.getNowLevelBB().size() > 2) {
            return;
        }

        BasicBlock preLatch = null, sucLatch = null, preHead = preLoop.getHeader(), sucHead = sucLoop.getHeader();
        HashSet<Instr> preIdcInstrs = new HashSet<>(), sucIdcInstrs = new HashSet<>();
        for (Instr instr = preHead.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {

        }
        for (Instr instr = sucHead.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {

        }
        HashSet<Value> preLoad = new HashSet<>(), preStore = new HashSet<>();
        HashSet<Value> sucLoad = new HashSet<>(), sucStore = new HashSet<>();
        HashSet<Instr> preLatchInstrs = new HashSet<>();
        HashSet<Instr> sucLatchInstrs = new HashSet<>();
        HashMap<Value, Value> map = new HashMap<>();
        for (BasicBlock bb: preLoop.getLatchs()) {
            preLatch = bb;
        }
        for (BasicBlock bb: sucLoop.getLatchs()) {
            sucLatch = bb;
        }
        for (Instr instr = preLatch.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
            preLatchInstrs.add(instr);
        }
        for (Instr instr = sucLatch.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
            sucLatchInstrs.add(instr);
        }

    }

    private boolean hasReflectInstr(HashMap<Value, Value> map, Instr instr, HashSet<Instr> instrs) {
        return false;
    }

}
