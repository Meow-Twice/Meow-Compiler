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
        RemovePhiAddPCopy();
        ReplacePCopy();
    }

    private void RemovePhiAddPCopy() {
        for (Function function: functions) {
            removeFuncPhi(function);
        }
    }

    private void ReplacePCopy() {
        for (Function function: functions) {

        }
    }


    private void removeFuncPhi(Function function) {
        BasicBlock bb = function.getBeginBB();
        while (bb.getNext() != null) {
            ArrayList<BasicBlock> pres = bb.getPrecBBs();
            ArrayList<Instr.PCopy> PCopys = new ArrayList<>();
            for (BasicBlock incomeBB: pres) {


                if (incomeBB.getSuccBBs().size() > 1) {
                    BasicBlock mid = new BasicBlock(function);
                    Instr.PCopy pCopy = new Instr.PCopy(new ArrayList<>(), new ArrayList<>(), mid);
                    PCopys.add(pCopy);
                    addMidBB(incomeBB, mid, bb);
                } else {
                    Instr endInstr = incomeBB.getEndInstr();
                    Instr.PCopy pCopy = new Instr.PCopy(new ArrayList<>(), new ArrayList<>(), incomeBB);
                    endInstr.insertBefore(pCopy);
                    PCopys.add(pCopy);
                }

            }

            Instr instr = bb.getBeginInstr();
            while (instr instanceof Instr.Phi) {
                ArrayList<Value> phiRHS = instr.getUseValueList();
                for (int i = 0; i < phiRHS.size(); i++) {
                    PCopys.get(i).addToPC(instr, phiRHS.get(i));
                }
                instr = (Instr) instr.getNext();
            }

            instr = bb.getBeginInstr();
            while (instr instanceof Instr.Phi) {
                Instr temp = instr;
                instr = (Instr) instr.getNext();
                temp.remove();
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

        Instr instr = src.getEndInstr();
        assert instr instanceof Instr.Branch;
        BasicBlock thenBB = ((Instr.Branch) instr).getThenTarget();
        BasicBlock elseBB = ((Instr.Branch) instr).getElseTarget();

        if (tag.equals(thenBB)) {
            ((Instr.Branch) instr).setThenTarget(mid);
            Instr.Jump jump = new Instr.Jump(tag, mid);
        } else if (tag.equals(elseBB)) {
            ((Instr.Branch) instr).setElseTarget(mid);
            Instr.Jump jump = new Instr.Jump(tag, mid);
        } else {
            System.err.println("Panic At Remove PHI addMidBB");
        }

    }

    private void replacePCopyForFunc(Function function) {
        BasicBlock bb = function.getBeginBB();
        while (bb.getNext() != null) {
            Instr instr = bb.getBeginInstr();
            while (instr.getNext() != null) {
                if (!(instr instanceof Instr.PCopy)) {
                    continue;
                }
                ArrayList<Value> tags = ((Instr.PCopy) instr).getLHS();
                ArrayList<Value> srcs = ((Instr.PCopy) instr).getRHS();
                while (!checkPCopy(tags, srcs)) {

                }
                instr = (Instr) instr.getNext();
            }


            bb = (BasicBlock) bb.getNext();
        }
    }

    private boolean checkPCopy(ArrayList<Value> tag, ArrayList<Value> src) {
        for (int i = 0; i < tag.size(); i++) {
            if (!tag.get(i).getName().equals(src.get(i).getName())) {
                return false;
            }
        }
        return true;
    }
}
