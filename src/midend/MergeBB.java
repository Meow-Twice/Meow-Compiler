package midend;

import mir.BasicBlock;
import mir.Function;
import mir.Instr;

import java.util.ArrayList;
import java.util.HashSet;

public class MergeBB {

    private ArrayList<Function> functions;

    public MergeBB(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        HashSet<BasicBlock> removes = new HashSet<>();
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                if (bb.getBeginInstr().equals(bb.getEndInstr()) && bb.getBeginInstr() instanceof Instr.Jump && !bb.getFunction().getBeginBB().equals(bb)) {
                    removes.add(bb);
                }
            }
        }
        for (BasicBlock bb: removes) {
            Instr.Jump jump = (Instr.Jump) bb.getBeginInstr();
            BasicBlock target = jump.getTarget();
            for (BasicBlock pre: bb.getPrecBBs()) {
                pre.modifyBrAToB(bb, target);
                target.getPrecBBs().add(pre);
            }
            target.getPrecBBs().remove(bb);
            bb.remove();
        }
    }
}
