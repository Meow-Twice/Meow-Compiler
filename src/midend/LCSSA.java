package midend;

import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Stack;

public class LCSSA {

    private ArrayList<Function> functions;

    public LCSSA(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        addPhi();
    }

    private void addPhi() {
        for (Function function: functions) {
            addPhiForFunc(function);
        }
    }

    private void addPhiForFunc(Function function) {
        for (BasicBlock bb: function.getLoopHeads()) {
            addPhiForLoop(bb.getLoop());
        }
    }

    private void addPhiForLoop(Loop loop) {
        HashSet<BasicBlock> exits = loop.getExits();
        for (BasicBlock bb: loop.getNowLevelBB()) {
            Instr instr = bb.getBeginInstr();
            while (instr.getNext() != null) {
                if (usedOutLoop(instr, loop)) {
                    for (BasicBlock exit: exits) {
                        addPhiAtExitBB(instr, exit, loop);
                    }
                }
                instr = (Instr) instr.getNext();
            }
        }
    }

    private void addPhiAtExitBB(Value value, BasicBlock bb, Loop loop) {
        ArrayList<Value> optionalValues = new ArrayList<>();
        for (int i = 0; i < bb.getPrecBBs().size(); i++) {
            optionalValues.add(value);
        }
        Instr.Phi phi = new Instr.Phi(value.getType(), optionalValues, bb);
        //TODO:ReName
//        HashSet<BasicBlock> domBB = getDomBB(bb);
//        Use use = value.getBeginUse();
//        while (use.getNext() != null) {
//            Instr user = use.getUser();
//            if (!user.parentBB().getLoop().equals(loop) && !(user instanceof Instr.Phi) && domBB.contains(user.parentBB())) {
//                user.modifyUse(value, phi, use.getIdx());
//            }
//            use = (Use) use.getNext();
//        }
        HashMap<Instr, Integer> useInstrMap = new HashMap<>();
        HashSet<Instr> defInstrs = new HashSet<>();
        Stack<Value> S = new Stack<>();
        defInstrs.add(phi);
        defInstrs.add((Instr) value);
        Use use = value.getBeginUse();
        while (use.getNext() != null) {
            Instr user = use.getUser();
            BasicBlock userBB = user.parentBB();
            //fixme:time 07-18-00:15 考虑正确性
            //PHI对其的使用其实是在PHI的前驱对它的使用
            //与GCM的scheduleLate采用同一思想
            //对于正常的PHI不能重新计算到达定义,因为有些定义已经没有了
            //初始化S?
//            if (user instanceof Instr.Phi) {
//                if (!user.equals(phi)) {
//                    use = (Use) use.getNext();
//                    continue;
//                }
//            }
//
//            useInstrMap.put(use.getUser(), use.getIdx());

            if (user instanceof Instr.Phi) {
                if (userBB.getLoop().equals(loop)) {
                    use = (Use) use.getNext();
                    continue;
                }
            }

            if (user instanceof Instr.Phi) {
                int index = use.getIdx();
                userBB = userBB.getPrecBBs().get(index);
            }
            if (!userBB.getLoop().equals(loop)) {
                useInstrMap.put(use.getUser(), use.getIdx());
            }


            use = (Use) use.getNext();
        }
        RenameDFS(S, bb.getFunction().getBeginBB(), useInstrMap, defInstrs);
    }

    public void RenameDFS(Stack<Value> S, BasicBlock X, HashMap<Instr, Integer> useInstrMap, HashSet<Instr> defInstrs) {
        int cnt = 0;
        Instr A = X.getBeginInstr();
        while (A.getNext() != null) {
            if (!(A instanceof Instr.Phi) && useInstrMap.containsKey(A)) {
                A.modifyUse(getStackTopValue(S), useInstrMap.get(A));
            }
            if (defInstrs.contains(A)) {
                S.push(A);
                cnt++;
            }
            A = (Instr) A.getNext();
        }

        ArrayList<BasicBlock> Succ = X.getSuccBBs();
        for (int i = 0; i < Succ.size(); i++) {
            BasicBlock bb = Succ.get(i);
            Instr instr = Succ.get(i).getBeginInstr();
            while (instr.getNext() != null) {
                if (!(instr instanceof Instr.Phi)) {
                    break;
                }
                if (useInstrMap.containsKey(instr)) {
                    instr.modifyUse(getStackTopValue(S), bb.getPrecBBs().indexOf(X));
                }
                instr = (Instr) instr.getNext();
            }
        }

        for (BasicBlock next: X.getIdoms()) {
            RenameDFS(S, next, useInstrMap, defInstrs);
        }

        for (int i = 0; i < cnt; i++) {
            S.pop();
        }
    }

    public Value getStackTopValue(Stack<Value> S) {
        if (S.empty()) {
            return new GlobalVal.UndefValue();
        }
        return S.peek();
    }


    //判断value是否在loop外被使用
    private boolean usedOutLoop(Value value, Loop loop) {
        if (value instanceof Instr.Load
                || value instanceof Instr.GetElementPtr || value instanceof Instr.Store) {
            return false;
        }
        Use use = value.getBeginUse();
        while (use.getNext() != null) {
            Instr user = use.getUser();
            BasicBlock userBB = user.parentBB();
            //PHI对其的使用其实是在PHI的前驱对它的使用
            //与GCM的scheduleLate采用同一思想
            if (user instanceof Instr.Phi) {
                int index = use.getIdx();
                userBB = userBB.getPrecBBs().get(index);
            }
            if (!userBB.getLoop().equals(loop)) {
                return true;
            }
            use = (Use) use.getNext();
        }
        return false;
    }


}
