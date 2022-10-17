package midend;

import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class ArrayGVNGCM {


    //TODO:GCM更新phi,删除无用phi,添加数组相关分析,
    // 把load,store,get_element_ptr也纳入GCM考虑之中
    private HashSet<Instr> pinnedInstr;
    private HashMap<BasicBlock, Instr> insertPos;

    private static HashSet<Instr> know;
    private BasicBlock root;


    HashMap<String, Instr> GvnMap = new HashMap<>();
    HashMap<String, Integer> GvnCnt = new HashMap<>();
    private Function function;
    private Value array;
    private HashSet<Instr> canGVNGCM;

    public ArrayGVNGCM(Function function, HashSet<Instr> canGVNGCM) {
        this.function = function;
        this.pinnedInstr = new HashSet<>();
        this.insertPos = new HashMap<>();
        this.canGVNGCM = canGVNGCM;
    }

    public void Run() {
        Init();
        GVN();
        GCM();
    }

    private void Init() {
        BasicBlock bb = function.getBeginBB();
        while (bb.getNext() != null) {
            Instr instr = bb.getBeginInstr();
            while (instr.getNext() != null) {
                if (isPinned(instr)) {
                    pinnedInstr.add(instr);
                }
                instr = (Instr) instr.getNext();
            }
            insertPos.put(bb, (Instr) bb.getEndInstr().getPrev());
            bb = (BasicBlock) bb.getNext();
        }
    }

    private void GCM() {
        scheduleEarlyForFunc(function);
        scheduleLateForFunc(function);
        move();
    }

    private void GVN() {
        GvnMap.clear();
        GvnCnt.clear();
        globalArrayGVN(array, function);
    }

    private void globalArrayGVN(Value array, Function function) {
        BasicBlock bb = function.getBeginBB();
        RPOSearch(bb);
    }

    private void RPOSearch(BasicBlock bb) {
        HashSet<Instr> loads = new HashSet<>();
        Instr load = bb.getBeginInstr();
        while (load.getNext() != null) {
            if (canGVNGCM.contains(load)) {
                if (!addLoadToGVN(load)) {
                    loads.add(load);
                }
            }
            load = (Instr) load.getNext();
        }

        for (BasicBlock next : bb.getIdoms()) {
            RPOSearch(next);
        }

        for (Instr temp : loads) {
            removeLoadFromGVN(temp);
        }
    }

    private void add(String str, Instr instr) {
        if (!GvnCnt.containsKey(str)) {
            GvnCnt.put(str, 1);
        } else {
            GvnCnt.put(str, GvnCnt.get(str) + 1);
        }
        if (!GvnMap.containsKey(str)) {
            GvnMap.put(str, instr);
        }
    }

    private void remove(String str) {
        GvnCnt.put(str, GvnCnt.get(str) - 1);
        if (GvnCnt.get(str) == 0) {
            GvnMap.remove(str);
        }
    }

    private boolean addLoadToGVN(Instr load) {
        //进行替换
        assert load instanceof Instr.Load;
        String hash = ((Instr.Load) load).getPointer().getName();
        if (GvnMap.containsKey(hash)) {
            load.modifyAllUseThisToUseA(GvnMap.get(hash));
            load.remove();
            return true;
        }
        add(hash, load);
        return false;
    }

    private void removeLoadFromGVN(Instr load) {
        assert load instanceof Instr.Load;
        String hash = ((Instr.Load) load).getPointer().getName();
        remove(hash);
    }


    private void scheduleEarlyForFunc(Function function) {
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
        for (Value X : instr.getUseValueList()) {
            if (X instanceof Instr) {
                scheduleEarly((Instr) X);
                if (instr.getEarliestBB().getDomTreeDeep() < ((Instr) X).getEarliestBB().getDomTreeDeep()) {
                    instr.setEarliestBB(((Instr) X).getEarliestBB());
                }
            }
        }
    }


    private void scheduleLateForFunc(Function function) {
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
        BasicBlock best = lca;
        if (lca == null) {
            System.err.println("err_GCM");
            assert true;
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
    private boolean isPinned(Instr instr) {
        if (canGVNGCM.contains(instr)) {
            return true;
        }
        return instr instanceof Instr.Jump || instr instanceof Instr.Branch ||
                instr instanceof Instr.Phi || instr instanceof Instr.Return ||
                instr instanceof Instr.Store || instr instanceof Instr.Load ||
                // instr instanceof Instr.Icmp || instr instanceof Instr.Fcmp ||
                //instr instanceof Instr.GetElementPtr ||
                instr instanceof Instr.Call;
    }
}
