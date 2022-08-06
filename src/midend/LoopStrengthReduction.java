package midend;

import frontend.Visitor;
import lir.V;
import mir.*;
import mir.type.Type;

import java.util.ArrayList;

public class LoopStrengthReduction {

    //TODO:识别循环中,使用了迭代变量idc的数学运算
    //      可以考虑任何一个常数*idc相关的ADD/SUB指令再进行ADD会/SUB
    //      即(i + A) * const + B
    //      A和B是循环不变量(def不在当前循环)
    //      const为常数
    //      循环展开前做

    //fixme:必须紧挨LoopFold调用
    private ArrayList<Function> functions;

    public LoopStrengthReduction(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        divToShift();
        addAndModToMulAndMod();
    }

    private void addAndModToMulAndMod() {
        //TODO:针对除法优化进行优化
    }

    private void divToShift() {
        for (Function function: functions) {
            divToShiftForFunc(function);
        }
    }

    private void divToShiftForFunc(Function function) {
        for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
            if (bb.isLoopHeader() && bb.getLoop().isCalcLoop()) {
                tryReduceLoop(bb.getLoop());
            }
        }
    }

    private void tryReduceLoop(Loop loop) {
//        if (loop.getHash() != 13) {
//            return;
//        }
        //要求了迭代变量是整数
        if (!(loop.getIdcInit() instanceof Constant.ConstantInt) || !(loop.getIdcStep() instanceof Constant.ConstantInt)) {
            return;
        }
        int idcInit = (int) ((Constant.ConstantInt) loop.getIdcInit()).getConstVal();
        int idcStep = (int) ((Constant.ConstantInt) loop.getIdcStep()).getConstVal();
        if (idcInit != 0 || idcStep != 1) {
            return;
        }

        //要求了除法是整数
        if (!((Instr.Alu) loop.getCalcAlu()).getOp().equals(Instr.Alu.Op.DIV) ||
                !(((Instr.Alu) loop.getCalcAlu()).getRVal2() instanceof Constant)) {
            return;
        }
        int div = (int) ((Constant) ((Instr.Alu) loop.getCalcAlu()).getRVal2()).getConstVal();
        double a = Math.log(div) / Math.log(2);
        if (Math.pow(2, a) != div) {
            return;
        }
        int shift = (int) a;

        if (!((Instr.Icmp) loop.getIdcCmp()).getOp().equals(Instr.Icmp.Op.SLT)) {
            return;
        }

        Type type = ((Instr.Alu) loop.getCalcAlu()).getType();
        //TODO:只考虑整数,待强化
        if (!type.isInt32Type()) {
            return;
        }
        BasicBlock head = loop.getHeader();
        BasicBlock entering = null;
        for (BasicBlock bb: loop.getEnterings()) {
            entering = bb;
        }
        BasicBlock exit = null;
        for (BasicBlock bb: loop.getExits()) {
            exit = bb;
        }
        BasicBlock exiting = null;
        for (BasicBlock bb: loop.getExitings()) {
            exiting = bb;
        }
        Function function = loop.getHeader().getFunction();
        Loop parentLoop = loop.getParentLoop();
        Value times = loop.getIdcEnd();
        Value reach = loop.getAluPhiEnterValue();
        Instr.Alu calcAlu = (Instr.Alu) loop.getCalcAlu();
        Instr.Phi calcPhi = (Instr.Phi) loop.getCalcPhi();

        BasicBlock A = new BasicBlock(function, parentLoop);
        BasicBlock B = new BasicBlock(function, parentLoop);
//        BasicBlock C = new BasicBlock(function, parentLoop);
//        BasicBlock D = new BasicBlock(function, parentLoop);
//        BasicBlock E = new BasicBlock(function, parentLoop);

        //A
        ArrayList<BasicBlock> Apres = new ArrayList<>();
        Apres.add(entering);
        ArrayList<BasicBlock> Asucc = new ArrayList<>();
        Asucc.add(B);
        Asucc.add(head);

        A.modifyPres(Apres);
        A.modifySucs(Asucc);

        Instr.Alu mul = new Instr.Alu(type, Instr.Alu.Op.MUL, times, new Constant.ConstantInt(shift), A);
        Instr.Ashr ashr = new Instr.Ashr(type, reach, mul, A);
        Instr.Icmp icmp = new Instr.Icmp(Instr.Icmp.Op.SGE, reach, new Constant.ConstantInt(0), A);
        Instr.Branch br = new Instr.Branch(icmp, B, head, A);

        int cnt = ++Visitor.VISITOR.curLoopCondCount;
        icmp.setCondCount(cnt);
        br.setCondCount(cnt);
        if (parentLoop.getLoopDepth() > 0) {
            icmp.setInLoopCond();
            br.setInLoopCond();
        }

        //B
        //Instr.Alu rem = new Instr.Alu(type, Instr.Alu.Op.REM, )
        ArrayList<BasicBlock> Bpres = new ArrayList<>();
        Bpres.add(A);
        Bpres.add(head);
        ArrayList<BasicBlock> Bsucc = new ArrayList<>();
        Bsucc.add(exit);

        B.modifyPres(Bpres);
        B.modifySucs(Bsucc);

        ArrayList<Value> values = new ArrayList<>();
        values.add(ashr);
        values.add(calcPhi);
        Instr.Phi mergePhi = new Instr.Phi(type, values, B);
        Instr.Jump jump = new Instr.Jump(exit, B);

        //C

        //D

        //E

        head.modifyPre(entering, A);
        head.modifyBrAToB(exit, B);
        head.modifySuc(exit, B);

        entering.modifyBrAToB(head, A);
        entering.modifySuc(head, A);

        exit.modifyPre(exiting, B);

        for (Use use = calcPhi.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
            Instr user = use.getUser();
            if (!user.equals(calcAlu) && !user.equals(mergePhi)) {
                user.modifyUse(mergePhi, use.getIdx());
            }
        }
    }

//    private void loopConstLift() {
//
//    }


}
