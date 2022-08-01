package midend;

import lir.V;
import mir.*;

import java.util.ArrayList;
import java.util.HashSet;

public class LoopFold {

    private ArrayList<Function> functions;
    private HashSet<Loop> loops = new HashSet<>();
    private HashSet<Loop> removes = new HashSet<>();

    public LoopFold(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                if (bb.isLoopHeader()) {
//                    if (bb.getLabel().equals("b61")) {
//                        System.err.println("b61");
//                    }
                    loops.add(bb.getLoop());
                    calcLoopInit(bb.getLoop());
                }
            }
        }
        for (Loop loop: loops) {
            if (!removes.contains(loop)) {
                tryFoldLoop(loop);
            }
        }
        for (Loop loop: removes) {
            BasicBlock entering = null;
            for (BasicBlock bb: loop.getEnterings()) {
                entering = bb;
            }
            BasicBlock exit = null;
            for (BasicBlock bb: loop.getExits()) {
                exit = bb;
            }
            loop.getParentLoop().getChildrenLoops().remove(loop);
            entering.modifyBrAToB(loop.getHeader(), exit);
            exit.modifyPre(loop.getHeader(), entering);
        }
    }

    private void calcLoopInit(Loop loop) {
        if (!loop.isSimpleLoop() || !loop.isIdcSet()) {
            return;
        }
        if (loop.hasChildLoop()) {
            return;
        }
        //只有head和latch的简单循环
        for (BasicBlock bb: loop.getNowLevelBB()) {
            if (!bb.isLoopHeader() && !bb.isLoopLatch()) {
                return;
            }
        }
        if (!loop.getHeader().isLoopExiting()) {
            return;
        }
        BasicBlock latch = null;
        for (BasicBlock bb: loop.getLatchs()) {
            latch = bb;
        }
        BasicBlock head = loop.getHeader();
        HashSet<Instr> idcInstrs = new HashSet<>();
        Instr.Alu alu = null;
        Instr.Phi phi = null;
        int alu_cnt = 0, phi_cnt = 0;
        idcInstrs.add((Instr) loop.getIdcPHI());
        idcInstrs.add((Instr) loop.getIdcCmp());
        idcInstrs.add((Instr) loop.getIdcAlu());
        idcInstrs.add(head.getEndInstr());
        idcInstrs.add(latch.getEndInstr());
        for (Instr idcInstr: idcInstrs) {
            if (useOutLoop(idcInstr, loop)) {
                return;
            }
        }
        for (BasicBlock bb: loop.getNowLevelBB()) {
            for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                if (!idcInstrs.contains(instr)) {
                    if (instr instanceof Instr.Alu) {
                        alu = (Instr.Alu) instr;
                        alu_cnt++;
                    } else if (instr instanceof Instr.Phi) {
                        phi = (Instr.Phi) instr;
                        phi_cnt++;
                    } else {
                        return;
                    }
                }
            }
        }
        if (alu_cnt != 1 || phi_cnt != 1) {
            return;
        }
        if (useOutLoop(alu, loop) || !useOutLoop(phi, loop)) {
            return;
        }
        int latchIndex = head.getPrecBBs().indexOf(latch);
        int enteringIndex = 1 - latchIndex;
        if (!phi.getUseValueList().get(latchIndex).equals(alu)) {
            return;
        }
        if (!alu.getUseValueList().contains(phi)) {
            return;
        }
        int aluOtherIndex = 1 - alu.getUseValueList().indexOf(phi);
        //TODO:待强化,使用的只要是同一个值就可以?
        //      且当前没有考虑float!!!
        if (!(alu.getUseValueList().get(aluOtherIndex) instanceof Constant.ConstantInt)) {
            return;
        }
        Value aluPhiEnterValue = phi.getUseValueList().get(enteringIndex);
        loop.setCalcLoopInfo(aluPhiEnterValue, alu, phi, aluOtherIndex);
    }

    private void tryFoldLoop(Loop loop) {
        if (!loop.isCalcLoop()) {
            return;
        }
        if (loop.getExits().size() != 1) {
            return;
        }
        BasicBlock exit = null;
        for (BasicBlock bb: loop.getExits()) {
            exit = bb;
        }
        if (exit.getSuccBBs().size() != 1) {
            return;
        }
        BasicBlock exitNext = exit.getSuccBBs().get(0);
        if (!exitNext.isLoopHeader()) {
            return;
        }
        Loop nextLoop = exitNext.getLoop();
        if (!nextLoop.isCalcLoop()) {
            return;
        }
        if (nextLoop.getEnterings().size() != 1) {
            return;
        }
        if (!loop.getIdcInit().equals(nextLoop.getIdcInit()) ||
                !loop.getIdcStep().equals(nextLoop.getIdcStep()) ||
                !loop.getIdcEnd().equals(nextLoop.getIdcEnd())) {
            return;
        }
        if (loop.getIdcCmp() instanceof Instr.Icmp) {
            if (!((Instr.Icmp) loop.getIdcCmp()).getOp().equals(((Instr.Icmp) loop.getIdcCmp()).getOp())) {
                return;
            }
        } else if (loop.getIdcCmp() instanceof Instr.Fcmp) {
            if (!((Instr.Fcmp) loop.getIdcCmp()).getOp().equals(((Instr.Fcmp) loop.getIdcCmp()).getOp())) {
                return;
            }
        } else {
            assert false;
        }
        Instr.Alu preLoopAlu = (Instr.Alu) loop.getCalcAlu();
        Instr.Phi preLoopPhi = (Instr.Phi) loop.getCalcPhi();
        Value preLoopEnterValue = loop.getAluPhiEnterValue();
        int index1 = loop.getAluOtherIndex();

        Instr.Alu sucLoopAlu = (Instr.Alu) nextLoop.getCalcAlu();
        Instr.Phi sucLoopPhi = (Instr.Phi) nextLoop.getCalcPhi();
        Value sucLoopEnterValue = nextLoop.getAluPhiEnterValue();
        int index2 = loop.getAluOtherIndex();

        if (!preLoopEnterValue.equals(sucLoopEnterValue)) {
            return;
        }

        int val1 = (int) ((Constant.ConstantInt) preLoopAlu.getUseValueList().get(index1)).getConstVal();
        int val2 = (int) ((Constant.ConstantInt) sucLoopAlu.getUseValueList().get(index2)).getConstVal();

        if (!preLoopAlu.getOp().equals(sucLoopAlu.getOp()) || val1 != val2 || index1 != index2) {
            return;
        }

        sucLoopPhi.modifyAllUseThisToUseA(preLoopPhi);
        removes.add(nextLoop);

    }

    private boolean useOutLoop(Instr instr, Loop loop) {
        for (Use use = instr.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
            Instr user = use.getUser();
            if (!user.parentBB().getLoop().equals(loop)) {
                return true;
            }
        }
        return false;
    }

}
