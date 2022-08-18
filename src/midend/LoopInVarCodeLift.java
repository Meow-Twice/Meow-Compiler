package midend;

import frontend.semantic.Initial;
import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class LoopInVarCodeLift {

    //TODO:load外提 -- spmv
    private ArrayList<Function> functions;
    private HashMap<GlobalVal.GlobalValue, Initial> globalValues;
    private HashMap<Instr.Alloc, HashSet<Instr>> allocDefs = new HashMap<>();
    private HashMap<Instr.Alloc, HashSet<Instr>> allocUsers = new HashMap<>();

    private HashMap<Value, HashSet<Instr>> defs = new HashMap<>();
    private HashMap<Value, HashSet<Instr>> users = new HashMap<>();

    private HashMap<Value, HashSet<Loop>> defLoops = new HashMap<>();
    private HashMap<Value, HashSet<Loop>> useLoops = new HashMap<>();
    private HashSet<Instr> loadCanGCM = new HashSet<>();


    public LoopInVarCodeLift(ArrayList<Function> functions, HashMap<GlobalVal.GlobalValue, Initial> globalValues) {
        this.functions = functions;
        this.globalValues = globalValues;
    }

    public void Run() {
        init();
        arrayConstDefLift();
        init();
        loopInVarLoadLift();

    }

    private void init() {
        loadCanGCM.clear();
        allocDefs.clear();
        allocUsers.clear();
        defs.clear();
        users.clear();
        defLoops.clear();
        useLoops.clear();
    }

    private void arrayConstDefLift() {
        for (Function function: functions) {
            arrayConstDefLiftForFunc(function);
        }
    }

    //alloc的所有def都在一个块,且为常数,且该块中的load均在store之后
    private void arrayConstDefLiftForFunc(Function function) {
        //init
        for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
            for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                if (instr instanceof Instr.Alloc) {
                    //TODO:待加强,当前只考虑int数组
                    for (Use use = instr.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
                        DFS((Instr.Alloc) instr, use.getUser());
                    }
                }
            }
        }
        //
        for (Instr.Alloc alloc: allocDefs.keySet()) {
            tryLift(alloc);
        }
    }

    private void tryLift(Instr.Alloc alloc) {
        HashSet<Instr> defForThisAlloc = allocDefs.get(alloc);
        BasicBlock bb = null;
        for (Instr def: defForThisAlloc) {
            if (bb == null) {
                bb = def.parentBB();
            } else {
                if (!bb.equals(def.parentBB())) {
                    return;
                }
            }
        }

        //所有def都在一个bb内
        boolean tag = false;
        for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
            if (allocUsers.get(alloc).contains(instr)) {
                tag = true;
            }
            //在use之后def
            if (tag && defForThisAlloc.contains(instr)) {
                return;
            }
        }

        //def 都是常数
        for (Instr def: defForThisAlloc) {
            if (def instanceof Instr.Call) {
                if (((Instr.Call) def).getFunc().getName().equals("memset")) {
                    if (((Instr.Call) def).getParamList().get(1) instanceof Constant.ConstantInt &&
                            ((Instr.Call) def).getParamList().get(2) instanceof Constant.ConstantInt) {
                        int val = (int) ((Constant.ConstantInt) ((Instr.Call) def).getParamList().get(1)).getConstVal();
                        if (val != 0) {
                            return;
                        }
                    } else {
                        return;
                    }
                } else {
                    return;
                }
            } else {
                assert def instanceof Instr.Store;
                Value ptr = ((Instr.Store) def).getPointer();
                if (!(((Instr.Store) def).getValue() instanceof Constant.ConstantInt)) {
                    return;
                }
                if (ptr instanceof Instr.GetElementPtr) {
                    for (Value index: ((Instr.GetElementPtr) ptr).getIdxList()) {
                        if (!(index instanceof Constant.ConstantInt)) {
                            return;
                        }
                    }
                } else {
                    return;
                }
            }
        }

        //do lift
        if (bb.getLoop().getLoopDepth() == 0) {
            return;
        }
        Loop loop = bb.getLoop();
        //TODO:多入口也可以?
        if (loop.getEnterings().size() > 1) {
            return;
        }
        ArrayList<Instr> move = new ArrayList<>();
        for (BasicBlock entering: loop.getEnterings()) {
            move.clear();
            for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                if (defForThisAlloc.contains(instr)) {
                    move.add(instr);
                }
            }
            for (Instr instr: move) {
                instr.delFromNowBB();
                entering.getEndInstr().insertBefore(instr);
                instr.setBb(entering);
                //instr.setLatestBB(entering);
            }
        }

    }


    private void DFS(Instr.Alloc alloc, Instr instr) {
        if (instr instanceof Instr.GetElementPtr) {
            for (Use use = instr.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
                Instr user = use.getUser();
                DFS(alloc, user);
            }
        } else if (instr instanceof Instr.Store) {
            if (!allocDefs.containsKey(alloc)) {
                allocDefs.put(alloc, new HashSet<>());
            }
            allocDefs.get(alloc).add(instr);
        } else if (instr instanceof Instr.Load) {
            if (!allocUsers.containsKey(alloc)) {
                allocUsers.put(alloc, new HashSet<>());
            }
            allocUsers.get(alloc).add(instr);
        } else if (instr instanceof Instr.Call) {
            //这两个函数的正确执行,不依赖于数组的原始值,所以只被认为是def
            if (((Instr.Call) instr).getFunc().getName().equals("memset") ||
                    ((Instr.Call) instr).getFunc().getName().equals("getarray")) {
                if (!allocDefs.containsKey(alloc)) {
                    allocDefs.put(alloc, new HashSet<>());
                }
                allocDefs.get(alloc).add(instr);
            } else {
                if (!allocUsers.containsKey(alloc)) {
                    allocUsers.put(alloc, new HashSet<>());
                }
                if (!allocDefs.containsKey(alloc)) {
                    allocDefs.put(alloc, new HashSet<>());
                }
                allocUsers.get(alloc).add(instr);
                allocDefs.get(alloc).add(instr);
            }
        } else if (instr instanceof Instr.Bitcast) {
            //bitcast
            for (Use use = instr.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
                Instr user = use.getUser();
                DFS(alloc, user);
            }
        } else {
            assert false;
        }
    }

    private void loopInVarLoadLift() {
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (instr instanceof Instr.Alloc) {
                        //TODO:待加强,当前只考虑int数组
                        for (Use use = instr.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
                            DFSArray(instr, use.getUser());
                        }
                    }
                }
            }
        }
        for (GlobalVal.GlobalValue globalVal: globalValues.keySet()) {
            for (Use use = globalVal.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
                DFSArray(globalVal, use.getUser());
            }
        }

        for (Value value: users.keySet()) {
            useLoops.put(value, new HashSet<>());
            defLoops.put(value, new HashSet<>());
            for (Instr instr: users.get(value)) {
                useLoops.get(value).add(instr.parentBB().getLoop());
            }
        }

        for (Value value: defs.keySet()) {
            defLoops.put(value, new HashSet<>());
            for (Instr instr: defs.get(value)) {
                defLoops.get(value).add(instr.parentBB().getLoop());
            }
        }



        for (Value array: users.keySet()) {
            loopInVarLoadLiftForArray(array);
        }
    }

    private void DFSArray(Value value, Instr instr) {
        if (instr instanceof Instr.GetElementPtr) {
            for (Use use = instr.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
                Instr user = use.getUser();
                DFSArray(value, user);
            }
        } else if (instr instanceof Instr.Store) {
            if (!defs.containsKey(value)) {
                defs.put(value, new HashSet<>());
            }
            defs.get(value).add(instr);
        } else if (instr instanceof Instr.Load) {
            if (!users.containsKey(value)) {
                users.put(value, new HashSet<>());
            }
            users.get(value).add(instr);
        } else if (instr instanceof Instr.Call) {
            //这两个函数的正确执行,不依赖于数组的原始值,所以只被认为是def
            if (((Instr.Call) instr).getFunc().getName().equals("memset") ||
                    ((Instr.Call) instr).getFunc().getName().equals("getarray")) {
                if (!defs.containsKey(value)) {
                    defs.put(value, new HashSet<>());
                }
                defs.get(value).add(instr);
            } else {
                if (!users.containsKey(value)) {
                    users.put(value, new HashSet<>());
                }
                if (!defs.containsKey(value)) {
                    defs.put(value, new HashSet<>());
                }
                users.get(value).add(instr);
                defs.get(value).add(instr);
            }
        } else if (instr instanceof Instr.Bitcast) {
            //bitcast
            for (Use use = instr.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
                Instr user = use.getUser();
                DFSArray(value, user);
            }
        } else {
            assert false;
        }
    }

    private void loopInVarLoadLiftForArray(Value array) {
        for (Instr user: users.get(array)) {
            if (!(user instanceof Instr.Load)) {
                continue;
            }
            Loop loop = user.parentBB().getLoop();
            if (loop.getEnterings().size() > 1) {
                continue;
            }
            //处在0层循环的code不需要提升-->不能(entering为null)
            if (loop.getLoopDepth() == 0) {
                continue;
            }
            BasicBlock entering = null;
            for (BasicBlock bb: loop.getEnterings()) {
                entering = bb;
            }
            if (!defLoops.get(array).contains(loop)) {
                if (((Instr.Load) user).getPointer() instanceof Instr.GetElementPtr &&
                        ((Instr.GetElementPtr) ((Instr.Load) user).getPointer()).parentBB().getLoop().equals(loop)) {
                    continue;
                }
                user.delFromNowBB();
                entering.getEndInstr().insertBefore(user);
                user.setBb(entering);
            }
        }
    }

}
