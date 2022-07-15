package midend;

import mir.*;

import java.util.ArrayList;
import java.util.HashSet;

public class LoopIdcVarInfo {

    private ArrayList<Function> functions;

    public LoopIdcVarInfo(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        GetInductionVar();
    }

    private void GetInductionVar() {
        for (Function function: functions) {
            GetInductionVarForFunc(function);
        }
    }

    private void GetInductionVarForFunc(Function function) {
        HashSet<Loop> loops = new HashSet<>();
        for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
            loops.add(bb.getLoop());
        }
        for (Loop loop: loops) {
            GetInductionVarForLoop(loop);
        }
    }

    private void GetInductionVarForLoop(Loop loop) {
        if (!loop.isSimpleLoop()) {
            return;
        }

        Instr headBr = loop.getHeader().getEndInstr(); // br i1 %v6, label %b3, label %b4
        assert headBr instanceof Instr.Branch;
        Value headBrCond = ((Instr.Branch) headBr).getCond(); // %v6 = icmp slt i32 %v19, 10
        Value idcPHI, idcEnd;
        if (((Instr.Icmp) headBrCond).getRVal1() instanceof Instr.Phi) {
            idcPHI = ((Instr.Icmp) headBrCond).getRVal1();
            idcEnd = ((Instr.Icmp) headBrCond).getRVal2();
        } else if ((((Instr.Icmp) headBrCond).getRVal2() instanceof Instr.Phi)) {
            idcPHI = ((Instr.Icmp) headBrCond).getRVal2();
            idcEnd = ((Instr.Icmp) headBrCond).getRVal1();
        } else {
            return;
        }
        assert idcPHI instanceof Instr.Phi;
        Value phiRVal1 = ((Instr.Phi) idcPHI).getUseValueList().get(0);
        Value phiRVal2 = ((Instr.Phi) idcPHI).getUseValueList().get(1);
        BasicBlock head = loop.getHeader();
        Value idcAlu, idcInit;
        if (loop.getLatchs().contains(head.getPrecBBs().get(0))) {
            idcAlu = phiRVal1;
            idcInit = phiRVal2;
        } else {
            idcAlu = phiRVal2;
            idcInit = phiRVal1;
        }
        Value idcStep;
        if (((Instr.Alu) idcAlu).getRVal1().equals(idcPHI)) {
            idcStep = ((Instr.Alu) idcAlu).getRVal2();
        } else if (((Instr.Alu) idcAlu).getRVal2().equals(idcPHI)) {
            idcStep = ((Instr.Alu) idcAlu).getRVal1();
        } else {
            return;
        }

//        if (((Instr.Alu) idcAlu).getOp().equals(Instr.Alu.Op.ADD) || ((Instr.Alu) idcAlu).getOp().equals(Instr.Alu.Op.SUB)) {
//            loop.setIdc(idcAlu, idcPHI, idcInit, idcEnd, idcStep);
//        }
        loop.setIdc(idcAlu, idcPHI, idcInit, idcEnd, idcStep);
    }
}
