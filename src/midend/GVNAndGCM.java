package midend;

import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class GVNAndGCM {

    private ArrayList<Function> functions;
    HashMap<Function, HashSet<Instr>> pinnedInstrMap;

    public GVNAndGCM(ArrayList<Function> functions) {
        this.functions = functions;
        this.pinnedInstrMap = new HashMap<>();
//        for (Function function: functions) {
//            this.pinnedInstrMap.put(function, new HashSet<>());
//        }
    }

    public void Run() {
        Init();
        GCM();
        GVN();
    }

    private void Init() {
        for (Function function: functions) {
            HashSet<Instr> pinnedInstr = new HashSet<>();
            BasicBlock bb = function.getBeginBB();
            while (bb.getNext() != null) {
                Instr instr = bb.getBeginInstr();
                while (instr.getNext() != null) {
                    if (isPinned(instr)) {
                        pinnedInstr.add(instr);
                    }
                    instr = (Instr) instr.getNext();
                }
                bb = (BasicBlock) bb.getNext();
            }
            pinnedInstrMap.put(function, pinnedInstr);
        }
    }

    private void GCM() {
        for (Function function: functions) {
            scheduleEarlyForFunc(function);
        }
        for (Function function: functions) {
            scheduleLateForFunc(function);
        }
        printBeforeMove();
        move();
    }

    private void GVN() {

    }

    private void scheduleEarlyForFunc(Function function) {
        HashSet<Instr> pinnedInstr = pinnedInstrMap.get(function);
        HashSet<Instr> know = new HashSet<>();
        BasicBlock root = function.getBeginBB();
        for (Instr instr: pinnedInstr) {
            instr.setEarliestBB(instr.parentBB());
            know.add(instr);
        }
        for (Instr instr: pinnedInstr) {
            for (Value value: instr.getUseValueList()) {
                if (value instanceof Constant || value instanceof BasicBlock ||
                        value instanceof GlobalVal || value instanceof Function || value instanceof Function.Param) {
                    continue;
                }
                assert value instanceof Instr;
                scheduleEarly((Instr) value, root, know);
            }
        }
        BasicBlock bb = function.getBeginBB();
        while (bb.getNext() != null) {
            Instr instr = bb.getBeginInstr();
            while (instr.getNext() != null) {
                if (!know.contains(instr)) {
                    scheduleEarly(instr, root, know);
                }
                instr = (Instr) instr.getNext();
            }
            bb = (BasicBlock) bb.getNext();
        }
    }

    private void scheduleEarly(Instr instr, BasicBlock root, HashSet<Instr> know) {
        if (know.contains(instr)) {
            return;
        }
        know.add(instr);
        instr.setEarliestBB(root);
        for (Value X: instr.getUseValueList()) {
            if (X instanceof Constant || X instanceof BasicBlock ||
                    X instanceof GlobalVal || X instanceof Function || X instanceof Function.Param) {
                continue;
            }
            assert X instanceof Instr;
            scheduleEarly((Instr) X, root, know);
            if (instr.getEarliestBB().getDomTreeDeep() < ((Instr) X).getEarliestBB().getDomTreeDeep()) {
                instr.setEarliestBB(((Instr) X).getEarliestBB());
            }
        }
    }


    private void scheduleLateForFunc(Function function) {
        HashSet<Instr> pinnedInstr = pinnedInstrMap.get(function);
        HashSet<Instr> know = new HashSet<>();
        for (Instr instr: pinnedInstr) {
            instr.setLatestBB(instr.parentBB());
            know.add(instr);
        }
        for (Instr instr: pinnedInstr) {
            Use use = instr.getBeginUse();
            while (use.getNext() != null) {
                scheduleLate(use.getUser(), know);
                use = (Use) use.getNext();
            }
        }
        BasicBlock bb = function.getBeginBB();
        while (bb.getNext() != null) {
            Instr instr = bb.getBeginInstr();
            while (instr.getNext() != null) {


                if (!know.contains(instr)) {
                    scheduleLate(instr, know);
                }
                instr = (Instr) instr.getNext();
            }
            bb = (BasicBlock) bb.getNext();
        }
    }

    private void scheduleLate(Instr instr, HashSet<Instr> know) {
        if (know.contains(instr)) {
            return;
        }
        know.add(instr);
        BasicBlock lca = null;
        Use usePos = instr.getBeginUse();
        while (usePos.getNext() != null) {
            Instr y = usePos.getUser();

            scheduleLate(y, know);
            BasicBlock use = y.getLatestBB();
            if (y instanceof Instr.Phi) {
                int j = ((Instr.Phi) y).getValueIndexInUseValueList(instr);
                use = y.getLatestBB().getPrecBBs().get(j);
            }
            lca = findLCA(lca, use);
            usePos = (Use) usePos.getNext();
        }
        // use the latest and earliest blocks to pick final positing
        // now latest is lca
        BasicBlock best = lca;
        while (!lca.equals(instr.getEarliestBB())) {
            if (lca.getLoopDep() < best.getLoopDep()) {
                best = lca;
            }
            lca = lca.getIDominator();
        }
        if (lca.getLoopDep() < best.getLoopDep()) {
            best = lca;
        }
        instr.setLatestBB(best);
    }

    private void move() {
        for (Function function: functions) {
            BasicBlock bb = function.getBeginBB();
            while (bb.getNext() != null) {
                Instr instr = bb.getBeginInstr();
                ArrayList<Instr> instrs = new ArrayList<>();
                while (instr.getNext() != null) {
                    instrs.add(instr);
                    instr = (Instr) instr.getNext();
                }
                for (Instr i: instrs) {
                    if (!i.getLatestBB().equals(bb)) {
//                        i.getPrev().setNext(i.getNext());
//                        i.getNext().setPrev(i.getPrev());
                        i.delFromNowBB();
                        i.getLatestBB().getEndInstr().insertBefore(i);
                        i.setBb(i.getLatestBB());
                    }
                }

                bb = (BasicBlock) bb.getNext();
            }
        }
    }

    private BasicBlock findLCA(BasicBlock a, BasicBlock b) {
        if (a == null) {
            return b;
        }
        while (a.getDomTreeDeep() > b.getDomTreeDeep()) {
            a = a.getIDominator();
        }
        while (b.getDomTreeDeep() > a.getDomTreeDeep()) {
            b = b.getIDominator();
        }
        while (!a.equals(b)) {
            a = a.getIDominator();
            b = b.getIDominator();
        }
        return a;
    }





    private boolean isPinned(Instr instr) {
        return instr instanceof Instr.Jump || instr instanceof Instr.Branch ||
                instr instanceof Instr.Phi || instr instanceof Instr.Return ||
                instr instanceof Instr.Store || instr instanceof Instr.Load || instr instanceof Instr.GetElementPtr || instr instanceof Instr.Call;
    }

    private void printBeforeMove() {
        for (Function function: functions) {
            BasicBlock bb = function.getBeginBB();
            System.err.println(function.getName());
            while (bb.getNext() != null) {
                Instr instr = bb.getBeginInstr();
                System.err.println(bb.toString());
                while (instr.getNext() != null) {
                    System.err.println("    " + instr.toString() + " early:" + instr.getEarliestBB().toString() + " last:" + instr.getLatestBB());
                    instr = (Instr) instr.getNext();
                }
                bb = (BasicBlock) bb.getNext();
            }
        }
    }

}
