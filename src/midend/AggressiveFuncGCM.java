package midend;

import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class AggressiveFuncGCM {

    private ArrayList<Function> functions;
    private HashSet<Function> canGCMFunc = new HashSet<>();
    private HashSet<Instr> canGCMInstr = new HashSet<>();

    private static HashSet<Instr> know;
    private BasicBlock root;
    private HashMap<Function, HashSet<Instr>> pinnedInstrMap = new HashMap<>();

    public AggressiveFuncGCM(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        init();
        GCM();
    }

    private void init() {
        boolean change = true;
        while (change) {
            change = false;
            for (Function function: functions) {
                if (!canGCMFunc.contains(function) && check(function)) {
                    canGCMFunc.add(function);
                    change = true;
                }
            }
        }

    }

    private boolean check(Function function) {
        for (Function.Param param: function.getParams()) {
            if (param.getType().isPointerType()) {
                return false;
            }
        }
        for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
            for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                if (instr instanceof Instr.Call) {
                    if (!((Instr.Call) instr).getFunc().equals(function) && !canGCMFunc.contains(((Instr.Call) instr).getFunc())) {
                        return false;
                    }
                }
                for (Value value: instr.getUseValueList()) {
                    if (value instanceof GlobalVal.GlobalValue) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void GCM() {
        for (Function function: functions) {
            pinnedInstrMap.put(function, new HashSet<>());
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (isPinned(instr)) {
                        pinnedInstrMap.get(function).add(instr);
                    }
                }
            }
        }
        for (Function function: functions) {
            scheduleEarlyForFunc(function);
        }
        for (Function function: functions) {
            scheduleLateForFunc(function);
        }
        //printBeforeMove();
        move();
    }


    private void scheduleEarlyForFunc(Function function) {
        HashSet<Instr> pinnedInstr = pinnedInstrMap.get(function);
        know = new HashSet<>();
        root = function.getBeginBB();
        for (Instr instr: pinnedInstr) {
            instr.setEarliestBB(instr.parentBB());
            know.add(instr);
        }
        BasicBlock bb = function.getBeginBB();
        while (bb.getNext() != null) {
            Instr instr = bb.getBeginInstr();
            while (instr.getNext() != null) {
                if (!know.contains(instr)) {
                    scheduleEarly(instr);
                } else if (pinnedInstr.contains(instr)) {
                    for (Value value: instr.getUseValueList()) {
                        if (value instanceof Constant || value instanceof BasicBlock ||
                                value instanceof GlobalVal || value instanceof Function || value instanceof Function.Param) {
                            continue;
                        }
                        assert value instanceof Instr;
                        scheduleEarly((Instr) value);
                    }
                }
                instr = (Instr) instr.getNext();
            }
            bb = (BasicBlock) bb.getNext();
        }
    }

    private void scheduleEarly(Instr instr) {
        if (know.contains(instr)) {
            return;
        }
        know.add(instr);
        instr.setEarliestBB(root);
        for (Value X: instr.getUseValueList()) {
            if (X instanceof Instr) {
                //assert X instanceof Instr;
                scheduleEarly((Instr) X);
                if (instr.getEarliestBB().getDomTreeDeep() < ((Instr) X).getEarliestBB().getDomTreeDeep()) {
                    instr.setEarliestBB(((Instr) X).getEarliestBB());
                }
            }
        }
    }


    private void scheduleLateForFunc(Function function) {
        HashSet<Instr> pinnedInstr = pinnedInstrMap.get(function);
        know = new HashSet<>();
        for (Instr instr: pinnedInstr) {
            instr.setLatestBB(instr.parentBB());
            know.add(instr);
        }
        for (Instr instr: pinnedInstr) {
            Use use = instr.getBeginUse();
            while (use.getNext() != null) {
                scheduleLate(use.getUser());
                use = (Use) use.getNext();
            }
        }
        BasicBlock bb = function.getBeginBB();
        while (bb.getNext() != null) {
            ArrayList<Instr> instrs = new ArrayList<>();
            Instr instr = bb.getEndInstr();
            while (instr.getPrev() != null) {
                instrs.add(instr);
                instr = (Instr) instr.getPrev();
            }
            for (Instr instr1: instrs) {
                if (!know.contains(instr1)) {
                    scheduleLate(instr1);
                }
            }
            bb = (BasicBlock) bb.getNext();
        }
    }

    private void scheduleLate(Instr instr) {
        if (know.contains(instr)) {
            return;
        }
        know.add(instr);
        BasicBlock lca = null;
        Use usePos = instr.getBeginUse();
        while (usePos.getNext() != null) {
            Instr y = usePos.getUser();

            scheduleLate(y);
            BasicBlock use = y.getLatestBB();
            if (y instanceof Instr.Phi) {
                int j = usePos.getIdx();
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

        if (!instr.getLatestBB().equals(instr.parentBB())) {
            instr.delFromNowBB();
            //TODO:检查 insert 位置 是在头部还是尾部
            Instr pos = null;
            pos = findInsertPos(instr, instr.getLatestBB());
            pos.insertBefore(instr);
            instr.setBb(instr.getLatestBB());
        }
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
                        assert false;
                        i.delFromNowBB();
                        //TODO:检查 insert 位置 是在头部还是尾部
                        Instr pos = findInsertPos(i, i.getLatestBB());
                        pos.insertBefore(i);
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

    private Instr findInsertPos(Instr instr, BasicBlock bb) {
        ArrayList<Value> users = new ArrayList<>();
        Use use = instr.getBeginUse();
        while (use.getNext() != null) {
            users.add(use.getUser());
            use = (Use) use.getNext();
        }
        Instr later = null;
        Instr pos = bb.getBeginInstr();
        while (pos.getNext() != null) {
            if (pos instanceof Instr.Phi) {
                pos = (Instr) pos.getNext();
                continue;
            }
            if (users.contains(pos)) {
                later = pos;
                break;
            }
            pos = (Instr) pos.getNext();
        }

        if (later != null) {
            return later;
        }

        return bb.getEndInstr();
    }




    // TODO:考虑数组变量读写的GCM 指针是SSA形式 但是内存不是
    //  考虑移动load,store是否会产生影响
    //  移动的上下限是对同一个数组的最近的load/store?
    private boolean canGCM(Instr instr) {
        if (instr instanceof Instr.Call) {
            return canGCMFunc.contains(((Instr.Call) instr).getFunc());
        } else {
            return false;
        }
    }

    private boolean isPinned(Instr instr) {
        return !canGCM(instr);
    }
}