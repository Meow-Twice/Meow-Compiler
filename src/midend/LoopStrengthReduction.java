package midend;

import frontend.Visitor;
import mir.*;
import mir.type.Type;

import java.util.ArrayList;
import java.util.HashSet;

public class LoopStrengthReduction {
    //fixme:记得打开优化

    //TODO:识别循环中,使用了迭代变量idc的数学运算
    //      可以考虑任何一个常数*idc相关的ADD/SUB指令再进行ADD会/SUB
    //      即(i + A) * const + B
    //      A和B是循环不变量(def不在当前循环)
    //      const为常数
    //      循环展开前做

    //fixme:必须紧挨LoopFold调用
    private ArrayList<Function> functions;
    private static final int lift_times = 4;

    public LoopStrengthReduction(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        divToShift();
//        addConstAndModToMulAndMod();
//        addIdcAndModToMulAndMod();
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

    private void addConstAndModToMulAndMod() {
        //TODO: ret += const; ret %= mod; i++;
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                if (bb.isLoopHeader()) {
                    addConstAndModLoopInit(bb.getLoop());
                }
            }
        }
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                if (bb.isLoopHeader() && bb.getLoop().isAddAndModLoop()) {
                    addConstAndModToMulAndModForLoop(bb.getLoop());
                }
            }
        }
    }

    private void addConstAndModLoopInit(Loop loop) {
        loop.clearAddAndModLoopInfo();
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
        Instr.Alu add = null;
        Instr.Alu rem = null;
        Instr.Phi phi = null;
        int add_cnt = 0, phi_cnt = 0, rem_cnt = 0;
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
                        if (((Instr.Alu) instr).getOp().equals(Instr.Alu.Op.ADD)) {
                            add = (Instr.Alu) instr;
                            add_cnt++;
                        } else if (((Instr.Alu) instr).getOp().equals(Instr.Alu.Op.REM)) {
                            rem = (Instr.Alu) instr;
                            rem_cnt++;
                        } else {
                            return;
                        }
                    } else if (instr instanceof Instr.Phi) {
                        phi = (Instr.Phi) instr;
                        phi_cnt++;
                    } else {
                        return;
                    }
                }
            }
        }
        if (add_cnt != 1 || rem_cnt != 1 || phi_cnt != 1) {
            return;
        }
        if (useOutLoop(add, loop) || useOutLoop(rem, loop) || !useOutLoop(phi, loop)) {
            return;
        }
        if (!(rem.getRVal2() instanceof Constant.ConstantInt) || !rem.getRVal1().equals(add)) {
            return;
        }
        int latchIndex = head.getPrecBBs().indexOf(latch);
        int enteringIndex = 1 - latchIndex;
        if (!phi.getUseValueList().get(latchIndex).equals(rem)) {
            return;
        }
        if (!add.getUseValueList().contains(phi)) {
            return;
        }
        int addConstIndex = 1 - add.getUseValueList().indexOf(phi);
        //TODO:待强化,使用的只要是同一个值就可以?
        //      且当前没有考虑float!!!
        if (!(add.getUseValueList().get(addConstIndex) instanceof Constant.ConstantInt)) {
            return;
        }
        //此限制是否必须,计算值的初始值为常数
        if (!(phi.getUseValueList().get(enteringIndex) instanceof Constant.ConstantInt)) {
            return;
        }
        int base = (int) ((Constant.ConstantInt) add.getUseValueList().get(addConstIndex)).getConstVal();
        int mod = (int) ((Constant.ConstantInt) rem.getRVal2()).getConstVal();
        int init = (int) ((Constant.ConstantInt) phi.getUseValueList().get(enteringIndex)).getConstVal();
        loop.setAddAndModLoopInfo(phi, add, rem, init, base ,mod, addConstIndex);
//        Value aluPhiEnterValue = phi.getUseValueList().get(enteringIndex);
//        loop.setCalcLoopInfo(aluPhiEnterValue, alu, phi, addConstIndex);
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

    private void addConstAndModToMulAndModForLoop(Loop loop) {
        if (!(loop.getIdcInit() instanceof Constant.ConstantInt) || !(loop.getIdcStep() instanceof Constant.ConstantInt)) {
            return;
        }
        if (!((Instr.Alu) loop.getIdcAlu()).getOp().equals(Instr.Alu.Op.ADD)) {
            return;
        }
        //get index
        BasicBlock loopLatch = null;
        for (BasicBlock bb: loop.getLatchs()) {
            loopLatch = bb;
        }
        BasicBlock loopExit = null;
        for (BasicBlock bb: loop.getExits()) {
            loopExit = bb;
        }
        int index = 1 - loop.getHeader().getPrecBBs().indexOf(loopLatch);
        int i_init = (int) ((Constant.ConstantInt) loop.getIdcInit()).getConstVal();
        int i_step = (int) ((Constant.ConstantInt) loop.getIdcStep()).getConstVal();
        Value i_end = loop.getIdcEnd();

        Loop preLoop = new Loop(loop.getParentLoop());
        Function func = loop.getHeader().getFunction();


        int base = loop.getBase();
        int mod = loop.getMod();
        int ans_init = loop.getInit();

        if (base < 0) {
            return;
        }

        int time_init = (mod - ans_init) / base + 1;
        BasicBlock entering = null;
        for (BasicBlock bb: loop.getEnterings()) {
            entering = bb;
        }
        BasicBlock head = new BasicBlock(func, preLoop);
        BasicBlock latch = new BasicBlock(func, preLoop);
        BasicBlock exit = new BasicBlock(func, preLoop);
        head.setLoopHeader();
        preLoop.addBB(head);
        preLoop.addBB(latch);
        preLoop.setHeader(head);

        //entering
        entering.modifyBrAToB(loop.getHeader(), head);
        entering.modifySuc(loop.getHeader(), head);

        //head
        Instr.Phi i_phi = new Instr.Phi(Type.BasicType.getI32Type(), new ArrayList<>(), head);
        Instr.Phi time_phi = new Instr.Phi(Type.BasicType.getI32Type(), new ArrayList<>(), head);
        Instr.Phi ans_phi = new Instr.Phi(Type.BasicType.getI32Type(), new ArrayList<>(), head);
        Instr.Alu head_add = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.ADD, i_phi, time_phi, head);
        Instr.Icmp icmp = new Instr.Icmp(((Instr.Icmp) loop.getIdcCmp()).getOp(), head_add, i_end, head);
        Instr.Branch head_br = new Instr.Branch(icmp, latch, exit, head);

        head.addPre(entering);
        head.addPre(latch);
        head.addSuc(latch);
        head.addSuc(exit);

        //latch
        Instr.Alu latch_mul = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.MUL, new Constant.ConstantInt(base), time_phi, latch);
        Instr.Alu latch_add_1 = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.ADD, ans_phi, latch_mul, latch);
        Instr.Alu latch_rem = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.REM, latch_add_1, new Constant.ConstantInt(mod), latch);
        Instr.Alu latch_sub = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.SUB, new Constant.ConstantInt(mod), latch_rem, latch);
        Instr.Alu latch_div = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.DIV, latch_sub, new Constant.ConstantInt(15), latch);
        Instr.Alu latch_add_2 = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.ADD, latch_div, new Constant.ConstantInt(1), latch);
        Instr.Jump latch_jump = new Instr.Jump(head, latch);

        //exit
        Instr.Alu exit_sub = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.SUB, i_end, i_phi, exit);
        Instr.Alu exit_mul = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.MUL, new Constant.ConstantInt(15), exit_sub, exit);
        Instr.Alu exit_add = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.ADD, ans_phi, exit_mul, exit);
        Instr.Alu exit_rem = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.REM, exit_add, new Constant.ConstantInt(1500000001), exit);
        Instr.Jump exit_jump = new Instr.Jump(loopExit, exit);
        exit.addPre(head);
        exit.addSuc(loopExit);

        //head-phi
        i_phi.addOptionalValue(new Constant.ConstantInt(i_init));
        i_phi.addOptionalValue(head_add);
        ans_phi.addOptionalValue(new Constant.ConstantInt(ans_init));
        ans_phi.addOptionalValue(latch_rem);
        time_phi.addOptionalValue(new Constant.ConstantInt(time_init));
        time_phi.addOptionalValue(latch_add_2);

        //next-loop-head
        //loop.getHeader().modifyPre(entering, exit);
        loopExit.modifyPre(loop.getHeader(), exit);


        ((Instr) loop.getIdcPHI()).modifyUse(i_phi, index);
        ((Instr) loop.getModPhi()).modifyUse(ans_phi, index);

        loop.getModPhi().modifyAllUseThisToUseA(exit_rem);

        //remove loop
        HashSet<BasicBlock> removes = new HashSet<>();
        removes.addAll(loop.getNowLevelBB());
        for (BasicBlock bb: removes) {
            for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                instr.remove();
            }
            bb.remove();
        }

    }

    private void addIdcAndModToMulAndMod() {
        //TODO: ret += const; ret %= mod; i++;
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                if (bb.isLoopHeader()) {
                    addIdcAndModLoopInit(bb.getLoop());
                }
            }
        }
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                if (bb.isLoopHeader() && bb.getLoop().isAddAndModLoop()) {
                    addIdcAndModToMulAndModForLoop(bb.getLoop());
                }
            }
        }
    }


    private void addIdcAndModLoopInit(Loop loop) {
        loop.clearAddAndModLoopInfo();
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
        Instr.Alu add = null;
        Instr.Alu rem = null;
        Instr.Phi phi = null;
        int add_cnt = 0, phi_cnt = 0, rem_cnt = 0;
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
                        if (((Instr.Alu) instr).getOp().equals(Instr.Alu.Op.ADD)) {
                            add = (Instr.Alu) instr;
                            add_cnt++;
                        } else if (((Instr.Alu) instr).getOp().equals(Instr.Alu.Op.REM)) {
                            rem = (Instr.Alu) instr;
                            rem_cnt++;
                        } else {
                            return;
                        }
                    } else if (instr instanceof Instr.Phi) {
                        phi = (Instr.Phi) instr;
                        phi_cnt++;
                    } else {
                        return;
                    }
                }
            }
        }
        if (add_cnt != 1 || rem_cnt != 1 || phi_cnt != 1) {
            return;
        }
        if (useOutLoop(add, loop) || useOutLoop(rem, loop) || !useOutLoop(phi, loop)) {
            return;
        }
        if (!(rem.getRVal2() instanceof Constant.ConstantInt) || !rem.getRVal1().equals(add)) {
            return;
        }
        int latchIndex = head.getPrecBBs().indexOf(latch);
        int enteringIndex = 1 - latchIndex;
        if (!phi.getUseValueList().get(latchIndex).equals(rem)) {
            return;
        }
        if (!add.getUseValueList().contains(phi)) {
            return;
        }
        int addIdcIndex = 1 - add.getUseValueList().indexOf(phi);
        //TODO:待强化,使用的只要是同一个值就可以?
        //      且当前没有考虑float!!!
        if (!(add.getUseValueList().get(addIdcIndex).equals(loop.getIdcPHI()))) {
            return;
        }
        //此限制是否必须,计算值的初始值为常数
        if (!(phi.getUseValueList().get(enteringIndex) instanceof Constant.ConstantInt)) {
            return;
        }
        int base = 0;
        int mod = (int) ((Constant.ConstantInt) rem.getRVal2()).getConstVal();
        int init = (int) ((Constant.ConstantInt) phi.getUseValueList().get(enteringIndex)).getConstVal();
        loop.setAddAndModLoopInfo(phi, add, rem, init, base ,mod, addIdcIndex);
//        Value aluPhiEnterValue = phi.getUseValueList().get(enteringIndex);
//        loop.setCalcLoopInfo(aluPhiEnterValue, alu, phi, addConstIndex);
    }

    private void addIdcAndModToMulAndModForLoop(Loop loop) {
        if (!(loop.getIdcInit() instanceof Constant.ConstantInt) || !(loop.getIdcStep() instanceof Constant.ConstantInt)) {
            return;
        }
        if (!((Instr.Alu) loop.getIdcAlu()).getOp().equals(Instr.Alu.Op.ADD)) {
            return;
        }
        if (!((Instr.Icmp) loop.getIdcCmp()).getOp().equals(Instr.Icmp.Op.SLT)) {
            return;
        }
        //get index
        BasicBlock loopLatch = loop.getLatchs().iterator().next(),
                loopExit = loop.getExits().iterator().next(),
                loopHead = loop.getHeader();
        int index = 1 - loop.getHeader().getPrecBBs().indexOf(loopLatch);
        long i_init = (int) ((Constant.ConstantInt) loop.getIdcInit()).getConstVal();
        long i_step = (int) ((Constant.ConstantInt) loop.getIdcStep()).getConstVal();
        Value i_end = loop.getIdcEnd();
        long ret_init = loop.getInit();
        long mod = loop.getMod();
        BasicBlock entering = loop.getEnterings().iterator().next();
        if (i_step < 0 || i_init < 0 || ret_init < 0) {
            return;
        }

        if (mod > 2147483647 / 2) {
            addIdcAndModToMulAndModForLoopForBigMod(loop);
            return;
        }

        Function fuc = entering.getFunction();
        Loop enterLoop = entering.getLoop();

        BasicBlock transBB = new BasicBlock(fuc, enterLoop);
        BasicBlock phiBB = new BasicBlock(fuc, enterLoop);

        entering.modifyBrAToB(loopHead, transBB);
        entering.modifySuc(loopHead, transBB);
        transBB.addPre(entering);


        phiBB.addSuc(loopHead);
        loopHead.modifyPre(entering, phiBB);

        Instr.Phi idc_phi = new Instr.Phi(Type.BasicType.getI32Type(), new ArrayList<>(), phiBB);
        Instr.Phi ret_phi = new Instr.Phi(Type.BasicType.getI32Type(), new ArrayList<>(), phiBB);
        Instr.Jump PhiBB_Jump = new Instr.Jump(loopHead, phiBB);

        Instr.Alu nn = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.MUL, i_end, i_end, transBB);
        Instr.Alu _2_i_sub_step_n = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.MUL,
                new Constant.ConstantInt((int) (2 * i_init - i_step)), i_end, transBB);
        Instr.Alu nn_step = nn;
        if (i_step != 1) {
            nn_step = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.MUL,
                    new Constant.ConstantInt((int) i_step), nn, transBB);
        }
        Instr.Alu add = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.ADD,
                nn_step, _2_i_sub_step_n, transBB);
        Instr.Alu div = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.DIV,
                add, new Constant.ConstantInt(2), transBB);

        for (int cnt = 0; cnt < lift_times; cnt++) {
            double a = (double) i_step, b = ((double) 2 * i_init - i_step), c = ((double) 2 * (ret_init - mod));
            double det  = (b * b - 4 * a * c);
            if (det <= 0) {
                return;
            }
            double ans1 = (-b - Math.sqrt(det)) / (2 * a), ans2 = (-b + Math.sqrt(det)) / (2 * a);
            long length = ans1 < -1? (long) ans2 + 1 : (long) ans1 + 1;
            //insert cmp br
            BasicBlock bb = new BasicBlock(fuc, enterLoop);
            Instr.Icmp icmp = new Instr.Icmp(Instr.Icmp.Op.SLT, new Constant.ConstantInt((int) (i_init + length)), i_end, transBB);
            Instr.Branch br = new Instr.Branch(icmp, bb, phiBB, transBB);
            transBB.addSuc(bb);
            transBB.addSuc(phiBB);
            bb.addPre(transBB);

            phiBB.addPre(transBB);
            //TODO:考虑溢出,先除
            if (cnt == 0) {
                idc_phi.addOptionalValue(i_end);
                ret_phi.addOptionalValue(div);
            } else {
                idc_phi.addOptionalValue(new Constant.ConstantInt((int) i_init));
                ret_phi.addOptionalValue(new Constant.ConstantInt((int) ret_init));
            }
            //calc in bb
            long val1 = (i_init + i_init + (length - 1) * i_step), val2 = length;
            if (val1 % 2 == 0) {
                val1 /= 2;
            } else {
                val2 /= 2;
            }
            //assert val1 * val2 > 0;
            ret_init = (int) ((ret_init + (val1 * val2) % mod) % mod);
            i_init = i_init + length * i_step;
            //
            transBB = bb;
        }

        //transBB -> phiBB
        Instr.Jump jump = new Instr.Jump(phiBB, transBB);
        transBB.addSuc(phiBB);
        phiBB.addPre(transBB);
        idc_phi.addOptionalValue(new Constant.ConstantInt((int) i_init));
        ret_phi.addOptionalValue(new Constant.ConstantInt((int) ret_init));


        ((Instr.Phi) loop.getIdcPHI()).modifyUse(idc_phi, index);
        ((Instr.Phi) loop.getModPhi()).modifyUse(ret_phi, index);

        //loop.getRemIS().modifyAllUseThisToUseA(loop.getAddIS());

    }

    private void addIdcAndModToMulAndModForLoopForBigMod(Loop loop) {
        if (!(loop.getIdcInit() instanceof Constant.ConstantInt) || !(loop.getIdcStep() instanceof Constant.ConstantInt)) {
            return;
        }
        if (!((Instr.Alu) loop.getIdcAlu()).getOp().equals(Instr.Alu.Op.ADD)) {
            return;
        }
        if (!((Instr.Icmp) loop.getIdcCmp()).getOp().equals(Instr.Icmp.Op.SLT)) {
            return;
        }
        //get index
        BasicBlock loopLatch = loop.getLatchs().iterator().next(),
                loopExit = loop.getExits().iterator().next(),
                loopHead = loop.getHeader();
        int index = 1 - loop.getHeader().getPrecBBs().indexOf(loopLatch);
        long i_init = (int) ((Constant.ConstantInt) loop.getIdcInit()).getConstVal();
        long i_step = (int) ((Constant.ConstantInt) loop.getIdcStep()).getConstVal();
        Value i_end = loop.getIdcEnd();
        long ret_init = loop.getInit();
        long mod = loop.getMod();
        BasicBlock entering = loop.getEnterings().iterator().next();
        if (i_step < 0 || i_init < 0 || ret_init < 0) {
            return;
        }

        //TODO:待加强,如果放开这个限制,需要修改 Begin/end/then/else BB 等
        if (i_init != 0 || i_step != 1 || ret_init != 0) {
            return;
        }

        Function fuc = entering.getFunction();
        Loop enterLoop = entering.getLoop();

        BasicBlock transBB = new BasicBlock(fuc, enterLoop);
        BasicBlock phiBB = new BasicBlock(fuc, enterLoop);

        entering.modifyBrAToB(loopHead, transBB);
        entering.modifySuc(loopHead, transBB);
        transBB.addPre(entering);


        phiBB.addSuc(loopHead);
        loopHead.modifyPre(entering, phiBB);

        Instr.Phi idc_phi = new Instr.Phi(Type.BasicType.getI32Type(), new ArrayList<>(), phiBB);
        Instr.Phi ret_phi = new Instr.Phi(Type.BasicType.getI32Type(), new ArrayList<>(), phiBB);
        Instr.Jump PhiBB_Jump = new Instr.Jump(loopHead, phiBB);

//        Instr.Alu nn = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.MUL, i_end, i_end, transBB);
//        Instr.Alu _2_i_sub_step_n = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.MUL,
//                new Constant.ConstantInt((int) (2 * i_init - i_step)), i_end, transBB);
//        Instr.Alu nn_step = nn;
//        if (i_step != 1) {
//            nn_step = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.MUL,
//                    new Constant.ConstantInt((int) i_step), nn, transBB);
//        }
//        Instr.Alu add = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.ADD,
//                nn_step, _2_i_sub_step_n, transBB);
//        Instr.Alu div = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.DIV,
//                add, new Constant.ConstantInt(2), transBB);

        BasicBlock beginBB = new BasicBlock(fuc, enterLoop);
        BasicBlock thenBB = new BasicBlock(fuc, enterLoop);
        BasicBlock elseBB = new BasicBlock(fuc, enterLoop);
        BasicBlock endBB = new BasicBlock(fuc, enterLoop);
        //TODO:维护第一个

        //int val1 = n, val2 = n - 1; if (n % 2 == 0)
        Instr.Alu val2 = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.SUB, i_end, new Constant.ConstantInt(1), beginBB);
        Instr.Alu n_rem_2 = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.REM, i_end, new Constant.ConstantInt(2), beginBB);
        Instr.Icmp icmp_begin_bb = new Instr.Icmp(Instr.Icmp.Op.EQ, n_rem_2, new Constant.ConstantInt(0), beginBB);
        Instr.Branch br_begin_bb = new Instr.Branch(icmp_begin_bb, thenBB, elseBB, beginBB);
        beginBB.addSuc(thenBB);
        beginBB.addSuc(elseBB);


        //
        thenBB.addPre(beginBB);
        Instr.Alu val1_half = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.DIV, i_end, new Constant.ConstantInt(2), thenBB);
        Instr.Jump jump_then_BB = new Instr.Jump(endBB, thenBB);
        thenBB.addSuc(endBB);


        //
        elseBB.addPre(beginBB);
        Instr.Alu val2_half = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.DIV, val2, new Constant.ConstantInt(2), elseBB);
        Instr.Jump jump_else_BB = new Instr.Jump(endBB, elseBB);
        elseBB.addSuc(endBB);

        //
        endBB.addPre(thenBB);
        endBB.addPre(elseBB);
        ArrayList<Value> values1 = new ArrayList<>();
        values1.add(val1_half);
        values1.add(i_end);
        Instr.Phi val1_phi = new Instr.Phi(Type.BasicType.getI32Type(), values1, endBB);
        ArrayList<Value> values2 = new ArrayList<>();
        values2.add(val2);
        values2.add(val2_half);
        Instr.Phi val2_phi = new Instr.Phi(Type.BasicType.getI32Type(), values2, endBB);
        Instr.Alu val1_val2_half = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.MUL, val1_phi, val2_phi, endBB);
        Instr.Jump jump_end_bb = new Instr.Jump(phiBB, endBB);
        endBB.addSuc(phiBB);
        phiBB.addPre(endBB);


        for (int cnt = 0; cnt < lift_times; cnt++) {
            double a = (double) i_step, b = ((double) 2 * i_init - i_step), c = ((double) 2 * (ret_init - mod));
            double det  = (b * b - 4 * a * c);
            if (det <= 0) {
                return;
            }
            double ans1 = (-b - Math.sqrt(det)) / (2 * a), ans2 = (-b + Math.sqrt(det)) / (2 * a);
            long length = ans1 < -1? (long) ans2 + 1 : (long) ans1 + 1;
            //insert cmp br
            BasicBlock bb = new BasicBlock(fuc, enterLoop);
            Instr.Icmp icmp = new Instr.Icmp(Instr.Icmp.Op.SLT, new Constant.ConstantInt((int) (i_init + length)), i_end, transBB);
            if (cnt == 0) {
                Instr.Branch br = new Instr.Branch(icmp, bb, beginBB, transBB);
                transBB.addSuc(bb);
                transBB.addSuc(beginBB);
                bb.addPre(transBB);
                beginBB.addPre(transBB);
            } else {
                Instr.Branch br = new Instr.Branch(icmp, bb, phiBB, transBB);
                transBB.addSuc(bb);
                transBB.addSuc(phiBB);
                bb.addPre(transBB);
                phiBB.addPre(transBB);
            }

            if (cnt == 0) {
                idc_phi.addOptionalValue(i_end);
                ret_phi.addOptionalValue(val1_val2_half);
            } else {
                idc_phi.addOptionalValue(new Constant.ConstantInt((int) i_init));
                ret_phi.addOptionalValue(new Constant.ConstantInt((int) ret_init));
            }
            //calc in bb
            long value1 = (i_init + i_init + (length - 1) * i_step), value2 = length;
            if (value1 % 2 == 0) {
                value1 /= 2;
            } else {
                value2 /= 2;
            }
            //assert val1 * val2 > 0;
            ret_init = (int) ((ret_init + (value1 * value2) % mod) % mod);
            i_init = i_init + length * i_step;
            //
            transBB = bb;
        }

        //transBB -> phiBB
        Instr.Jump jump = new Instr.Jump(phiBB, transBB);
        transBB.addSuc(phiBB);
        phiBB.addPre(transBB);
        idc_phi.addOptionalValue(new Constant.ConstantInt((int) i_init));
        ret_phi.addOptionalValue(new Constant.ConstantInt((int) ret_init));


        ((Instr.Phi) loop.getIdcPHI()).modifyUse(idc_phi, index);
        ((Instr.Phi) loop.getModPhi()).modifyUse(ret_phi, index);

    }
}
