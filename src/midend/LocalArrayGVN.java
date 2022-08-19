package midend;

import mir.*;
import mir.type.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class LocalArrayGVN {

    //TODO:GCM更新phi,删除无用phi,添加数组相关分析,
    // 把load,store,get_element_ptr也纳入GCM考虑之中

    private static boolean _STRONG_CHECK_ = true;

    private static HashSet<Instr> know;
    private BasicBlock root;


    HashMap<String, Instr> GvnMap = new HashMap<>();
    HashMap<String, Integer> GvnCnt = new HashMap<>();
    private ArrayList<Function> functions;
    private HashSet<Instr> instrCanGCM = new HashSet<>();
    //private HashMap<GlobalVal.GlobalValue, Initial> globalValues;
    private String label;

    HashMap<BasicBlock, HashMap<String, Instr>> GvnMapByBB = new HashMap<>();
    HashMap<BasicBlock, HashMap<String, Integer>> GvnCntByBB = new HashMap<>();


    public LocalArrayGVN(ArrayList<Function> functions, String label) {
        this.functions = functions;
        this.label = label;

    }

    public void Run() {
//        Init();
//        GVN();
        //GCM();

        if (label.equals("GVN")) {
            Init();
            GVN();
        } else if (label.equals("GCM")) {
            Init();
            GCM();
        }
    }

    private void Init() {
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (instr instanceof Instr.Alloc) {
                        ((Instr.Alloc) instr).clearLoads();
                    } else if (instr instanceof Instr.Load) {
                        ((Instr.Load) instr).clear();
                    } else if (instr instanceof Instr.Store) {
                        ((Instr.Store) instr).clear();
                    }
                }
            }
            for (Value param: function.getParams()) {
                if (param.getType() instanceof Type.PointerType) {
                    ((Function.Param) param).clearLoads();
                }
            }
        }
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (instr instanceof Instr.Alloc) {
                        initAlloc(instr);
                    }
                }
            }
            for (Value param: function.getParams()) {
                if (param.getType() instanceof Type.PointerType) {
                    initAlloc(param);
                }
            }
        }


    }

    private void initAlloc(Value alloc) {
        for (Use use = alloc.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
            Instr user = use.getUser();
            allocDFS(alloc, user);
        }
    }

    private void allocDFS(Value alloc, Instr instr) {
        if (instr instanceof Instr.GetElementPtr) {
            for (Use use = instr.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
                Instr user = use.getUser();
                allocDFS(alloc, user);
            }
        } else if (instr instanceof Instr.Load) {
            ((Instr.Load) instr).setAlloc(alloc);
            if (alloc instanceof Instr.Alloc) {
                ((Instr.Alloc) alloc).addLoad(instr);
            } else if (alloc instanceof Function.Param) {
                ((Function.Param) alloc).addLoad(instr);
            }
            //TODO:待强化
            if (alloc instanceof Instr.Alloc) {
                instrCanGCM.add(instr);
            }
        } else if (instr instanceof Instr.Store) {
            ((Instr.Store) instr).setAlloc(alloc);

            if (alloc instanceof Instr.Alloc) {
                instrCanGCM.add(instr);
            }
        } else {
            //assert false;
        }
    }

    private void GVN() {
        GvnMap.clear();
        GvnCnt.clear();
        GVNInit();
        for (Function function: functions) {
            localArrayGVN(function);
        }
    }


    private void localArrayGVN(Function function) {
        BasicBlock bb = function.getBeginBB();
        RPOSearch(bb);
    }


    private HashSet<Function> goodFuncs = new HashSet<>();

    private void GVNInit() {
        for (Function function: functions) {
            if (check_good_func(function)) {
                goodFuncs.add(function);
            }
        }
    }

    private boolean check_good_func(Function function) {
        for (Value value: function.getParams()) {
            if (value.getType().isPointerType()) {
                return false;
            }
        }
        for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
            for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                for (Value value: instr.getUseValueList()) {
                    if (value instanceof GlobalVal.GlobalValue) {
                        return false;
                    }
                }
            }
        }
        return true;
    }


    private void RPOSearch(BasicBlock bb) {
//        if (_STRONG_CHECK_) {
//            if (bb.getPrecBBs().size() > 1) {
//                GvnCnt.clear();
//                GvnMap.clear();
//            }
//            if (bb.getPrecBBs().size() == 1 && !bb.getIDominator().equals(bb.getPrecBBs().get(0))) {
//                GvnCnt.clear();
//                GvnMap.clear();
//            }
//        }
        HashMap<String, Integer> tempGvnCnt = new HashMap<>();
        HashMap<String, Instr> tempGvnMap = new HashMap<>();
        for (String key: GvnCnt.keySet()) {
            tempGvnCnt.put(key, GvnCnt.get(key));
        }
        for (String key: GvnMap.keySet()) {
            tempGvnMap.put(key, GvnMap.get(key));
        }
        //GvnCntByBB.put(bb, tempGvnCnt);
        if (_STRONG_CHECK_) {
            if (bb.getPrecBBs().size() > 1) {
                tempGvnCnt.clear();
                tempGvnMap.clear();
            }
            if (bb.getPrecBBs().size() == 1 && !bb.getIDominator().equals(bb.getPrecBBs().get(0))) {
                tempGvnCnt.clear();
                tempGvnMap.clear();
            }
        }


        Instr instr = bb.getBeginInstr();
        while (instr.getNext() != null) {
            if (instr instanceof Instr.Load && ((Instr.Load) instr).getAlloc() != null) {
                addLoadToGVN(instr);
            } else if (instr instanceof Instr.Store && ((Instr.Store) instr).getAlloc() != null) {
                Value alloc = ((Instr.Store) instr).getAlloc();
                if (alloc instanceof Instr.Alloc) {
                    for (Instr load: ((Instr.Alloc) alloc).getLoads()) {
                        assert load instanceof Instr.Load;
                        try {
                            removeLoadFromGVN(load);
                        } catch (Exception e) {
                            System.err.println("err");
                        }
                    }
                } else if (alloc instanceof Function.Param) {
                    for (Instr load: ((Function.Param) alloc).getLoads()) {
                        assert load instanceof Instr.Load;
                        removeLoadFromGVN(load);
                    }
                } else {
                    assert false;
                }

            } else if (instr instanceof Instr.Call) {
                //判断函数的传参有没有数组
//                if (((Instr.Call) instr).getFunc().isExternal) {
//
//                } else if (((Instr.Call) instr).getFunc().getName().equals("memset")) {
//
//                }
                //TODO:待强化,根据函数传入的指针,判断修改了哪个Alloc/参数
                if (goodFuncs.contains(((Instr.Call) instr).getFunc())) {

                } else {
                    GvnMap.clear();
                    GvnCnt.clear();
                }

            }
            instr = (Instr) instr.getNext();
        }

        for (BasicBlock next : bb.getIdoms()) {
            RPOSearch(next);
        }

//        for (Instr temp : adds) {
//            removeLoadFromGVN(temp);
//        }
        GvnMap.clear();
        GvnCnt.clear();
        GvnMap.putAll(tempGvnMap);
        GvnCnt.putAll(tempGvnCnt);
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
        if (!GvnCnt.containsKey(str) || GvnCnt.get(str) == 0) {
            return;
        }
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
            //load.remove();
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

    private HashMap<Value, Instr> reachDef = new HashMap<>();

    private HashMap<Function, HashSet<Instr>> pinnedInstrMap = new HashMap<>();

    //TODO:GVM添加store
    //      存在一条A(store)到B(load)的路径,认为存在user/use关系
    //      考虑循环的数据流
    private void GCM() {
        GCMInit();
        for (Function function: functions) {
            scheduleEarlyForFunc(function);
        }

        for (Function function: functions) {
            scheduleLateForFunc(function);
        }
    }

    private void GCMInit() {
        for (Function function: functions) {
            pinnedInstrMap.put(function, new HashSet<>());
            reachDef.clear();
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (!instrCanGCM.contains(instr)) {
                        pinnedInstrMap.get(function).add(instr);
                    }
                    if (instr instanceof Instr.Alloc) {
                        reachDef.put(instr, instr);
                    }
                }
            }
//            for (Value param: function.getParams()) {
//                if (param.getType() instanceof Type.PointerType) {
//                    reachDef.put(param, function.getBeginBB().getBeginInstr());
//                }
//            }
            DFS(function.getBeginBB());
        }

    }

    private void DFS(BasicBlock bb) {
//        if (bb.getLabel().equals("b25")) {
//            System.err.println("DFS_B25");
//        }
        for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
            if (instr instanceof Instr.Store && ((Instr.Store) instr).getAlloc() != null) {
                reachDef.put(((Instr.Store) instr).getAlloc(), instr);
            } else if (instr instanceof Instr.Load && ((Instr.Load) instr).getAlloc() != null) {
                ((Instr.Load) instr).setUseStore(reachDef.get(((Instr.Load) instr).getAlloc()));
                Instr def = reachDef.get(((Instr.Load) instr).getAlloc());
                if (def instanceof Instr.Store) {
                    ((Instr.Store) def).addUser(instr);
                }
            } else if (instr instanceof Instr.Call) {
                for (Value value: reachDef.keySet()) {
                    reachDef.put(value, instr);
                }
            }
        }
        for (BasicBlock next: bb.getIdoms()) {
            DFS(next);
        }
    }


    private void scheduleEarlyForFunc(Function function) {
        HashSet<Instr> pinnedInstr = pinnedInstrMap.get(function);
        know = new HashSet<>();
        root = function.getBeginBB();
        for (Instr instr: pinnedInstr) {
            instr.setEarliestBB(instr.parentBB());
            know.add(instr);
        }
//        BasicBlock bb = function.getBeginBB();
//        while (bb.getNext() != null) {
//            Instr instr = bb.getBeginInstr();
//            while (instr.getNext() != null) {
//                if (!know.contains(instr)) {
//                    scheduleEarly(instr);
//                } else if (pinnedInstr.contains(instr)) {
//                    for (Value value: instr.getUseValueList()) {
//                        if (!(value instanceof Instr)) {
//                            continue;
//                        }
//                        scheduleEarly((Instr) value);
//                    }
//                }
//                instr = (Instr) instr.getNext();
//            }
//            bb = (BasicBlock) bb.getNext();
//        }
        for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
           for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
               if (!know.contains(instr)) {
                   scheduleEarly(instr);
               } else if (pinnedInstr.contains(instr)) {
                   for (Value value: instr.getUseValueList()) {
                       if (!(value instanceof Instr)) {
                           continue;
                       }
                       scheduleEarly((Instr) value);
                   }
               }
           }
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
                scheduleEarly((Instr) X);
                if (instr.getEarliestBB().getDomTreeDeep() < ((Instr) X).getEarliestBB().getDomTreeDeep()) {
                    instr.setEarliestBB(((Instr) X).getEarliestBB());
                }
            }
        }
        if (instr instanceof Instr.Load) {
            if (((Instr.Load) instr).getUseStore() == null) {
                System.err.println("err");
            }
            Value X = ((Instr.Load) instr).getUseStore();
            scheduleEarly((Instr) X);
            if (instr.getEarliestBB().getDomTreeDeep() < ((Instr) X).getEarliestBB().getDomTreeDeep()) {
                instr.setEarliestBB(((Instr) X).getEarliestBB());
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
        for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
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
        }
    }

    private void scheduleLate(Instr instr) {
        if (instr.toString().equals("store i32 %v242, i32* %v239")) {
            System.err.println("err");
        }

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
        if (instr instanceof Instr.Store) {
            for (Instr y :((Instr.Store) instr).getUsers()) {
                scheduleLate(y);
                BasicBlock use = y.getLatestBB();
                if (y instanceof Instr.Phi) {
                    int j = usePos.getIdx();
                    use = y.getLatestBB().getPrecBBs().get(j);
                }
                lca = findLCA(lca, use);
            }
        }
        // use the latest and earliest blocks to pick final positing
        // now latest is lca
//        if (lca == null) {
//            instr.setLatestBB(instr.getEarliestBB());
//            return;
//        }
        BasicBlock best = lca;
        if (lca == null) {
            instr.setLatestBB(instr.parentBB());
            //System.err.println("err_GCM " + instr.toString());
        } else {
            while (!lca.equals(instr.getEarliestBB())) {
                if (lca.getLoopDep() < best.getLoopDep()) {
                    best = lca;
                }
                lca = lca.getIDominator();
                if (lca == null) {
                    System.err.println("err_GCM " + instr.toString());
                }
            }
            if (lca.getLoopDep() < best.getLoopDep()) {
                best = lca;
            }
            instr.setLatestBB(best);
        }

        if (!instr.getLatestBB().equals(instr.parentBB())) {
            System.err.println("Array GCM Move");
            instr.delFromNowBB();
            //TODO:检查 insert 位置 是在头部还是尾部
            Instr pos = findInsertPos(instr, instr.getLatestBB());
            pos.insertBefore(instr);
            instr.setBb(instr.getLatestBB());
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
        HashSet<Value> users = new HashSet<>();
        Use use = instr.getBeginUse();
        while (use.getNext() != null) {
            users.add(use.getUser());
            use = (Use) use.getNext();
        }
        if (instr instanceof Instr.Store) {
            users.addAll(((Instr.Store) instr).getUsers());
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

    //只移动Load,Store
    //  store的user认为是store的数组的所有被store支配的load
    //  load的use除了基本的use还有 所有支配它的store
    private boolean isPinned(Instr instr) {
        return !(instr instanceof Instr.Store) && !(instr instanceof Instr.Load);
    }
}
