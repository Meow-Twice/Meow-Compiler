package midend;

import mir.*;
import util.CenterControl;

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


    private HashMap<Function, HashSet<Integer>> use = new HashMap<>();
    private HashMap<Function, HashSet<Integer>> def = new HashMap<>();

    private HashMap<Function, HashSet<Value>> useGlobals = new HashMap<>();
    private HashMap<Function, HashSet<Value>> defGlobals = new HashMap<>();

    public AggressiveFuncGCM(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        init();
        GCM();
    }

    private void init() {
        for (Function function : functions) {
            def.put(function, new HashSet<>());
            defGlobals.put(function, new HashSet<>());
            use.put(function, new HashSet<>());
            useGlobals.put(function, new HashSet<>());
        }

        for (Function function : functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (instr instanceof Instr.Store) {
                        Value val = ((Instr.Store) instr).getPointer();
                        while (val instanceof Instr.GetElementPtr) {
                            val = ((Instr.GetElementPtr) val).getPtr();
                        }
                        if (val instanceof Function.Param) {
                            def.get(function).add(function.getParams().indexOf(val));
                        } else if (val instanceof GlobalVal.GlobalValue) {
                            defGlobals.get(function).add(val);
                        }
                    } else if (instr instanceof Instr.Load) {
                        Value val = ((Instr.Load) instr).getPointer();
                        while (val instanceof Instr.GetElementPtr) {
                            val = ((Instr.GetElementPtr) val).getPtr();
                        }
                        if (val instanceof Function.Param) {
                            use.get(function).add(function.getParams().indexOf(val));
                        }
                        if (val instanceof GlobalVal.GlobalValue) {
                            useGlobals.get(function).add(val);
                        }
                    }
                }
            }
        }


        for (Function function : functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (instr instanceof Instr.Call) {
                        Function callFunc = ((Instr.Call) instr).getFunc();
                        for (Value val : ((Instr.Call) instr).getParamList()) {
                            if (val instanceof Function.Param) {
                                int thisFuncIndex = function.getParams().indexOf(val);
                                int callFuncIndex = ((Instr.Call) instr).getParamList().indexOf(val);
                                if (callFunc.isExternal) {
                                    use.get(function).add(thisFuncIndex);
                                } else {
                                    if (use.get(callFunc).contains(callFuncIndex)) {
                                        use.get(function).add(thisFuncIndex);
                                    }
                                    if (def.get(callFunc).contains(callFuncIndex)) {
                                        def.get(function).add(thisFuncIndex);
                                    }
                                    useGlobals.get(function).addAll(useGlobals.get(callFunc));
                                    defGlobals.get(function).addAll(defGlobals.get(callFunc));
                                }
                            }
                        }
                    }
                }
            }
        }

        boolean change = true;
        while (change) {
            change = false;
            for (Function function : functions) {
                if (!canGCMFunc.contains(function) && check(function)) {
                    canGCMFunc.add(function);
                    change = true;
                }
            }
        }

    }

    private boolean check(Function function) {
        if (!CenterControl._STRONG_FUNC_GCM) {
            for (Function.Param param : function.getParams()) {
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
                    for (Value value : instr.getUseValueList()) {
                        if (value instanceof GlobalVal.GlobalValue) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }
        if (defGlobals.get(function).size() > 0) {
            return false;
        }
        for (Function.Param param : function.getParams()) {
            if (param.getType().isPointerType() && def.get(function).contains(function.getParams().indexOf(param))) {
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
//                for (Value value: instr.getUseValueList()) {
//                    if (value instanceof GlobalVal.GlobalValue) {
//                        return false;
//                    }
//                }
            }
        }
        return true;
    }

    private void GCM() {
        noUserCallDelete();
        for (Function function : functions) {
            pinnedInstrMap.put(function, new HashSet<>());
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (isPinned(instr)) {
                        pinnedInstrMap.get(function).add(instr);
                    }
//                    else {
//                        if (instr.isNoUse()) {
//                            instr.remove();
//                        }
//                    }
                }
            }
        }
        for (Function function : functions) {
            scheduleEarlyForFunc(function);
        }
        for (Function function : functions) {
            scheduleLateForFunc(function);
        }
        //printBeforeMove();
        move();
    }


    public void noUserCallDelete() {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Function function : functions) {
                BasicBlock beginBB = function.getBeginBB();
                BasicBlock end = function.getEnd();

                BasicBlock pos = beginBB;
                while (!pos.equals(end)) {

                    Instr instr = pos.getBeginInstr();
                    try {
                        while (instr.getNext() != null) {
                            if ((instr instanceof Instr.Call) && instr.isNoUse() && canGCM(instr)) {
                                instr.remove();
                                changed = true;
                            }
                            instr = (Instr) instr.getNext();
                        }
                    } catch (Exception e) {
                        System.out.println(instr.toString());
                    }

                    pos = (BasicBlock) pos.getNext();
                }
            }
        }
    }

    private void scheduleEarlyForFunc(Function function) {
        HashSet<Instr> pinnedInstr = pinnedInstrMap.get(function);
        know = new HashSet<>();
        root = function.getBeginBB();
        for (Instr instr : pinnedInstr) {
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
                    for (Value value : instr.getUseValueList()) {
                        if (!(value instanceof Instr)) {
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
        for (Value X : instr.getUseValueList()) {
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
        for (Instr instr : pinnedInstr) {
            instr.setLatestBB(instr.parentBB());
            know.add(instr);
        }
        for (Instr instr : pinnedInstr) {
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
            for (Instr instr1 : instrs) {
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
        if (lca == null) {
            System.err.println("err_GCM");
        }
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
        for (Function function : functions) {
            BasicBlock bb = function.getBeginBB();
            while (bb.getNext() != null) {
                Instr instr = bb.getBeginInstr();
                ArrayList<Instr> instrs = new ArrayList<>();
                while (instr.getNext() != null) {
                    instrs.add(instr);
                    instr = (Instr) instr.getNext();
                }
                for (Instr i : instrs) {
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
            if (pos instanceof Instr.Call && ((Instr.Call) pos).getFunc().getName().equals("_sysy_stoptime")) {
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
