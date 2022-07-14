package midend;

import mir.BasicBlock;
import mir.Function;
import mir.Instr;
import mir.Value;

import java.util.ArrayList;
import java.util.HashSet;

public class BranchOptimize {

    private ArrayList<Function> functions;

    public BranchOptimize(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        RemoveUselessPHI();
        RemoveUselessJump();
    }

    //删除只有一个use的PHI(冗余PHI)
    private void RemoveUselessPHI() {
        for (Function function: functions) {
            removeUselessPHIForFunc(function);
        }
    }

    private void RemoveUselessJump() {
        for (Function function: functions) {
            removeUselessJumpForFunc(function);
        }
    }

    private void removeUselessPHIForFunc(Function function) {
        BasicBlock bb = function.getBeginBB();
        while (bb.getNext() != null) {
            Instr instr = bb.getBeginInstr();
            while (instr.getNext() != null) {
                if (!(instr instanceof Instr.Phi)) {
                    break;
                }
                if (instr.getUseValueList().size() == 1) {
                    Value value = instr.getUseValueList().get(0);
                    instr.modifyAllUseThisToUseA(value);
                    instr.remove();
                }

                instr = (Instr) instr.getNext();
            }

            bb = (BasicBlock) bb.getNext();
        }
    }

    private void removeUselessJumpForFunc(Function function) {
        HashSet<BasicBlock> bbCanRemove = new HashSet<>();
        BasicBlock bb = function.getBeginBB();
        while (bb.getNext() != null) {
            if (bb.getPrecBBs().size() == 1) {
                bbCanRemove.add(bb);
            }
            bb = (BasicBlock) bb.getNext();
        }
        for (BasicBlock mid: bbCanRemove) {
            // bbs -> pre -> mid -> bbs
            // bbs -> pre -> bbs
            // move all instr in mid to pre
            BasicBlock pre = mid.getPrecBBs().get(0);
            if (pre.getSuccBBs().size() != 1) {
                continue;
            }
            pre.getEndInstr().remove();
            ArrayList<Instr> instrInMid = new ArrayList<>();
            for (Instr instr = mid.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                instrInMid.add(instr);
            }
            for (Instr instr: instrInMid) {
                instr.setBb(pre);
                pre.insertAtEnd(instr);
            }
            pre.setSuccBBs(mid.getSuccBBs());
            for (BasicBlock temp: mid.getSuccBBs()) {
                temp.modifyPre(mid, pre);
            }
            mid.remove();
        }
    }
}
