package midend;

import frontend.semantic.Initial;
import mir.*;
import mir.type.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class DeadCodeDelete {
    //TODO:naive:只被store,但是没有被load的数组/全局变量可以删除
    private ArrayList<Function> functions;
    private HashMap<GlobalVal.GlobalValue, Initial> globalValues;

    private HashMap<Instr, Instr> root;
    private HashMap<Instr, Integer> deep;

    public DeadCodeDelete(ArrayList<Function> functions, HashMap<GlobalVal.GlobalValue, Initial> globalValues) {
        this.functions = functions;
        this.globalValues = globalValues;
    }

    public void Run(){
        noUserCodeDelete();
        deadCodeElimination();
        removeUselessGlobalVal();
        removeUselessLocalArray();
        noUserCodeDelete();
        removeUselessLoop();
    }

    //TODO:对于指令闭包
    // 指令闭包的算法: 必须被保留的指令  函数的return,函数调用,对全局变量的store,数组  从这些指令dfs use
    //  其余全部删除
    //  wait memorySSA for 数组级别数据流分析
    public void noUserCodeDelete() {
        boolean changed = true;
        while(changed)
        {
            changed = false;
            for (Function function : functions) {
                BasicBlock beginBB = function.getBeginBB();
                BasicBlock end = function.getEnd();

                BasicBlock pos = beginBB;
                while (!pos.equals(end)) {

                    Instr instr = pos.getBeginInstr();
                    //Instr endInst = pos.getEndInstr();
                    // 一个基本块至少有一条跳转指令
                    try {
                        while (instr.getNext() != null) {
                            if (!(instr instanceof Instr.Terminator) && !(instr instanceof Instr.Call) && !(instr.getType().isVoidType()) &&
                                    instr.isNoUse()) {
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

    //fixme:对于SSA格式,这个算法貌似是多余的?
    private void deadCodeElimination() {
        for (Function function: functions) {
            deadCodeEliminationForFunc(function);
        }
    }

    private void deadCodeEliminationForFunc(Function function) {
        HashSet<Instr> know = new HashSet<>();
        HashMap<Instr, HashSet<Instr>> closureMap = new HashMap<>();
        root = new HashMap<>();
        deep = new HashMap<>();
        for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
            for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                deep.put(instr, -1);
                root.put(instr, instr);
            }
        }

        for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
            for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                for (Value value: instr.getUseValueList()) {
                    if (value instanceof Instr) {
                        Instr x = find(instr);
                        Instr y = find((Instr) value);
                        if (!x.equals(y)) {
                            union(x, y);
                        }
                    }
                }
                know.add(instr);
            }
        }

        //构建引用闭包
        for (Instr instr: know) {
            Instr root = find(instr);
            if (!closureMap.containsKey(root)) {
                closureMap.put(root, new HashSet<>());
            }
            closureMap.get(root).add(instr);
        }

        for (Instr instr: closureMap.keySet()) {
            HashSet<Instr> instrs = closureMap.get(instr);
            boolean canRemove = true;
            for (Instr i: instrs) {
                if (hasEffect(i)) {
                    canRemove = false;
                    break;
                }
            }
            if (canRemove) {
                for (Instr i: instrs) {
                    i.remove();
                }
            }
        }

    }

    private Instr find(Instr x) {
        if (root.get(x).equals(x)) {
            return x;
        } else {
            root.put(x, find(root.get(x)));
            return root.get(x);
        }
    }

    private void union(Instr a, Instr b) {
        if (deep.get(b) < deep.get(a)) {
            root.put(a, b);
        } else {
            if (deep.get(a) == deep.get(b)) {
                deep.put(a, deep.get(a) - 1);
            }
            root.put(b, a);
        }
    }

    private boolean hasEffect(Instr instr) {
        //TODO:GEP?
        return instr instanceof Instr.Jump || instr instanceof Instr.Branch
                || instr instanceof Instr.Return || instr instanceof Instr.Call
                || instr instanceof Instr.Store || instr instanceof Instr.Load;
    }

    //for i=1:n
    //  a[i] = getint()
    //return 0

    private void removeUselessGlobalVal() {
        HashSet<GlobalVal> remove = new HashSet<>();
        for (GlobalVal val: globalValues.keySet()) {
            if (((Type.PointerType) val.getType()).getInnerType().isArrType()) {
                boolean ret = tryRemoveUselessArray(val);
                if (ret) {
                    remove.add(val);
                }
                continue;
            }
            boolean canRemove = true;
            Use use = val.getBeginUse();
            while (use.getNext() != null) {
                if (!(use.getUser() instanceof Instr.Store)) {
                    canRemove = false;
                }
                use = (Use) use.getNext();
            }

            if (canRemove) {
                use = val.getBeginUse();
                while (use.getNext() != null) {
                    Instr user = use.getUser();
                    assert user instanceof Instr.Store;
                    deleteInstr(user, false);
                    use = (Use) use.getNext();
                }
                remove.add(val);
            }
        }
        for (Value value: remove) {
            globalValues.remove(value);
        }
    }

    private void deleteInstr(Instr instr, boolean tag) {
        if (tag && hasEffect(instr)) {
            return;
        }
        for (Value value: instr.getUseValueList()) {
            if (value instanceof Instr) {
                deleteInstr((Instr) value, true);
            }
        }
        instr.remove();
    }

    private void removeUselessLocalArray() {
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (instr instanceof Instr.Alloc) {
                        tryRemoveUselessArray(instr);
                    }
                }
            }
        }
    }


    //删除无用的全局/局部数组 global-init local-alloc
    private boolean tryRemoveUselessArray(Value value) {
        HashSet<Instr> instrs = new HashSet<>();
        boolean ret = check(value, instrs);
        if (ret) {
            for (Instr instr: instrs) {
                instr.remove();
            }
        }
        return ret;
    }

    private boolean check(Value value, HashSet<Instr> know) {
        if (value instanceof Instr) {
            know.add((Instr) value);
        }
        for (Use use = value.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
            Instr user = use.getUser();
            if (user instanceof Instr.GetElementPtr) {
                boolean ret = check(user, know);
                if (!ret) {
                    return false;
                }
            } else {
                //只被store 而且store所用的值没有effect
                if (user instanceof Instr.Store) {
                    Value storeValue = user.getUseValueList().get(0);
//                    if (storeValue instanceof Instr && hasEffect((Instr) storeValue)) {
//                        return false;
//                    }
                    know.add(user);
                } else if (user instanceof Instr.Call) {
                    know.add(user);
                    Value val = user.getUseValueList().get(0);
                    if (val instanceof Function) {
                        if (!val.getName().equals("memset")) {
                            return false;
                        }
                    }
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    private void removeUselessLoop() {
        for (Function function: functions) {
            Loop loop = function.getBeginBB().getLoop();
            tryRemoveLoop(loop);
        }
    }

    private void tryRemoveLoop(Loop loop) {
        for (Loop next: loop.getChildrenLoops()) {
            tryRemoveLoop(next);
        }
        if (loopCanRemove(loop)) {
            //TODO:REMOVE
            HashSet<BasicBlock> enterings = loop.getEnterings();
            HashSet<BasicBlock> exits = loop.getExits();
            ArrayList<BasicBlock> pres = new ArrayList<>();
            BasicBlock head = loop.getHeader();
            BasicBlock exit = null;
            for (BasicBlock bb: exits) {
                exit = bb;
            }
            for (BasicBlock entering: enterings) {
                entering.modifyBrAToB(head, exit);
                entering.modifySuc(head, exit);
                pres.add(entering);
            }
            exit.modifyPres(pres);
            loop.getParentLoop().getChildrenLoops().remove(loop);
            HashSet<BasicBlock> bbRemove = new HashSet<>();
            for (BasicBlock bb: loop.getNowLevelBB()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    instr.remove();
                }
                bbRemove.add(bb);
            }
            for (BasicBlock bb: bbRemove) {
                bb.remove();
            }

        }
    }

    private boolean loopCanRemove(Loop loop) {
        if (loop.getChildrenLoops().size() != 0) {
            return false;
        }
        if (loop.getExits().size() != 1) {
            return false;
        }
        BasicBlock exit = null;
        for (BasicBlock bb: loop.getExits()) {
            exit = bb;
        }
        if (exit.getBeginInstr() instanceof Instr.Phi) {
            return false;
        }
        //TODO:trivial 待强化
        if (loop.getExitings().size() != exit.getPrecBBs().size()) {
            return false;
        }
        for (BasicBlock bb: loop.getNowLevelBB()) {
            for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                for (Use use = instr.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
                    Instr user = use.getUser();
                    if (!user.parentBB().getLoop().equals(loop) || hasStrongEffect(user)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean hasStrongEffect(Instr instr) {
        return instr instanceof Instr.Return || instr instanceof Instr.Call
                || instr instanceof Instr.Store || instr instanceof Instr.Load;
    }



}
