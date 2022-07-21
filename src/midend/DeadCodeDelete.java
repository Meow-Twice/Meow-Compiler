package midend;

import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class DeadCodeDelete {
    private ArrayList<Function> functions;

    private HashMap<Instr, Instr> root;
    private HashMap<Instr, Integer> deep;

    public DeadCodeDelete(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run(){
        noUserCodeDelete();
        deadCodeElimination();
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
        if (deep.get(x) < 0) {
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


}
