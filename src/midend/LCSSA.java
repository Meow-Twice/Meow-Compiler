package midend;

import mir.*;

import java.util.ArrayList;
import java.util.HashSet;

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
    }

    //TODO:获取某个value到达某个bb的定义
    private HashSet<BasicBlock> getDomBB(BasicBlock bb) {
        HashSet<BasicBlock> know = new HashSet<>();
        DFS(bb, know);
        return know;
    }

    private void DFS(BasicBlock bb, HashSet<BasicBlock> know) {
        if (know.contains(bb)) {
            return;
        }
        know.add(bb);

        for (BasicBlock next: bb.getIdoms()) {
            DFS(next, know);
        }
    }

    //判断value是否在loop外被使用
    private boolean usedOutLoop(Value value, Loop loop) {
        if (value instanceof Instr.Load) {
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
//            if (!use.getUser().parentBB().getLoop().equals(loop) && !(use.getUser() instanceof Instr.Phi)) {
//                return true;
//            }
            use = (Use) use.getNext();
        }
        return false;
    }
}
