package midend;

import mir.BasicBlock;
import mir.Function;
import mir.Instr;
import mir.Value;

import java.util.ArrayList;

public class RemovePhi {

    private ArrayList<Function> functions;

    public RemovePhi(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        for (Function function: functions) {
            removeFuncPhi(function);
        }
    }

    private void removeFuncPhi(Function function) {
        BasicBlock bb = function.getBeginBB();
        while (bb.getNext() != null) {
            ArrayList<BasicBlock> pres = bb.getPrecBBs();
            ArrayList<Value> PCopys = new ArrayList<>();
            for (BasicBlock incomeBB: pres) {
                Instr.PCopy pCopy = new Instr.PCopy(new ArrayList<>(), new ArrayList<>(), incomeBB);
                PCopys.add(pCopy);
                if (incomeBB.getSuccBBs().size() > 1) {
                    BasicBlock mid = new BasicBlock(function);
                    pCopy.setBb(mid);
                }

            }

            bb = (BasicBlock) bb.getNext();
        }
    }

    private void addMidBB(BasicBlock src, BasicBlock mid, BasicBlock tag) {
        src.getSuccBBs().remove(tag);
        src.getSuccBBs().add(mid);
        mid.getSuccBBs().add(tag);
        tag.getPrecBBs().remove(src);
        tag.getPrecBBs().add(mid);

        int oldEdgeType = edgeType(src, tag);
        if (oldEdgeType != 0) {

        }
    }

    private int edgeType(BasicBlock src, BasicBlock tag) {
        BasicBlock srcNxt = (BasicBlock) src.getNext();
        if (srcNxt.equals(tag)) {
            return 0;
        } else {
            return 1;
        }
    }
}
