package midend;

import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class LoopInVarCodeLift {

    private ArrayList<Function> functions;
    private HashMap<Instr.Alloc, HashSet<Instr>> defs = new HashMap<>();
    private HashMap<Instr.Alloc, HashSet<Instr>> users = new HashMap<>();

    public LoopInVarCodeLift(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        init();
        arrayConstDefLift();
    }

    private void init() {
        defs.clear();
        users.clear();
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
                    for (Use use = instr.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
                        DFS((Instr.Alloc) instr, use.getUser());
                    }
                }
            }
        }
        //
        for (Instr.Alloc alloc: defs.keySet()) {
            tryLift(alloc);
        }
    }

    private void tryLift(Instr.Alloc alloc) {
        HashSet<Instr> defForThisAlloc = defs.get(alloc);
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
            if (users.get(alloc).contains(instr)) {
                tag = true;
            }
            //在use之后def
            if (tag && defForThisAlloc.contains(instr)) {
                return;
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
            if (!defs.containsKey(alloc)) {
                defs.put(alloc, new HashSet<>());
            }
            defs.get(alloc).add(instr);
        } else if (instr instanceof Instr.Load) {
            if (!users.containsKey(alloc)) {
                users.put(alloc, new HashSet<>());
            }
            users.get(alloc).add(instr);
        } else if (instr instanceof Instr.Call) {
            if (((Instr.Call) instr).getFunc().getName().equals("memset") ||
                    ((Instr.Call) instr).getFunc().getName().equals("getarray")) {
                if (!defs.containsKey(alloc)) {
                    defs.put(alloc, new HashSet<>());
                }
                defs.get(alloc).add(instr);
            } else {
                if (!users.containsKey(alloc)) {
                    users.put(alloc, new HashSet<>());
                }
                users.get(alloc).add(instr);
            }
        } else {
            //bitcast
            assert false;
        }
    }

}
