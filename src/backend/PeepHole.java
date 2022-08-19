package backend;

import lir.*;
import lir.MC.Operand;
import lir.Arm.Regs.*;
import util.ILinkNode;

import java.util.ArrayList;
import java.util.HashSet;

import static backend.CodeGen.*;
import static lir.Arm.Regs.GPRs.cspr;
import static lir.Arm.Regs.GPRs.sp;
import static lir.BJ.*;
import static lir.MachineInst.MachineMemInst;
import static lir.MachineInst.MachineMove;
import static lir.MachineInst.Tag.*;
import static mir.type.DataType.I32;

public class PeepHole {
    final MC.Program p;

    public PeepHole(MC.Program p) {
        this.p = p;
    }

    public void run() {
        for (MC.McFunction mf : p.funcList) {
            boolean unDone = true;
            while (unDone) {
                unDone = oneStage(mf);
                if (twoStage(mf))
                    unDone = true;
            }
        }
    }

    MC.Block curMB = null;

    // 注意不能用unDone当条件来判断是否remove之类, 可能是上一次结果的残留
    public boolean oneStage(MC.McFunction mf) {
        boolean unDone = false;
        for (MC.Block mb : mf.mbList) {
            curMB = mb;
            for (ILinkNode i = mb.miList.getBegin(); !i.equals(mb.miList.tail); i = i.getNext()) {
                MachineInst mi = (MachineInst) i;
                MachineInst prevInst = mi.getPrev() == mb.miList.head ? MachineInst.emptyInst : (MachineInst) mi.getPrev();
                MachineInst nextInst = mi.getNext() == mb.miList.tail ? MachineInst.emptyInst : (MachineInst) mi.getNext();
                switch (mi.getTag()) {
                    case Add, Sub -> {
                        I.Binary bino = (I.Binary) mi;
                        if (bino.getROpd().equals(Operand.I_ZERO)) {
                            unDone = true;
                            if (bino.getDst().equals(bino.getLOpd())) {
                                bino.remove();
                            } else {
                                new I.Mov(bino.getDst(), bino.getLOpd(), bino);
                                bino.remove();
                            }
                        }
                    }
                    case Jump -> {
                        if (mi.isNoCond() && ((GDJump) mi).getTarget().equals(curMB.getNext())) {
                            unDone = true;
                            mi.remove();
                        }
                    }
                    // case Branch -> {
                    //     // TODO 可能改变branch方式
                    //     if (((GDBranch) mi).getFalseTargetBlock().equals(curMB.getNext())) {
                    //         unDone = true;
                    //         new GDJump(mi.getCond(), ((GDBranch) mi).getTrueTargetBlock(), mi);
                    //         mi.remove();
                    //     }
                    // }
                    case Ldr -> {
                        if (oneStageLdr(Str, mi, prevInst)) unDone = true;
                    }
                    case VLdr -> {
                        if (oneStageLdr(VStr, mi, prevInst)) unDone = true;
                    }
                    case IMov, VMov -> {
                        if (oneStageMov(mi, nextInst)) unDone = true;
                    }
                }
            }
        }
        return unDone;
    }

    /**
     * move a a (to be remove)
     * <p>
     * move a b (cur, to be remove)
     * move a c
     * move a b (b != a)
     *
     * @param mi
     * @param nextInst
     * @return
     */
    private boolean oneStageMov(MachineInst mi, MachineInst nextInst) {
        if (!mi.getShift().hasShift()) {
            MachineMove curMov = (MachineMove) mi;
            if (curMov.getDst().equals(curMov.getSrc())) {
                curMov.remove();
                return true;
            } else if (curMov.isNoCond() && nextInst.isOf(mi.getTag())) {
                MachineMove nextMov = (MachineMove) nextInst;
                if (nextMov.getDst().equals(nextMov.getSrc())) {
                    nextMov.remove();
                    return true;
                } else if (nextMov.getDst().equals(curMov.getDst())) {
                    curMov.remove();
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * str a, [b, x]
     * ldr c, [b, x] (cur, to be replaced)
     * =>
     * str a, [b, x]
     * mov c, a
     *
     * @param tag
     * @param mi
     * @param prevInst
     * @return
     */
    private boolean oneStageLdr(MachineInst.Tag tag, MachineInst mi, MachineInst prevInst) {

        MachineMemInst ldr = (MachineMemInst) mi;
        if (prevInst.isOf(tag)) {
            // TODO ldr 和 str 不可能有条件
            MachineMemInst str = (MachineMemInst) prevInst;
            assert ldr.isNoCond() && str.isNoCond();
            if (ldr.getAddr().equals(str.getAddr())
                    && ldr.getOffset().equals(str.getOffset())
                    && ldr.getShift().equals(str.getShift())) {
                if (tag == Str) new I.Mov(ldr.getData(), str.getData(), (MachineInst) ldr);
                else if (tag == VStr) new V.Mov(ldr.getData(), str.getData(), (MachineInst) ldr);
                else System.exit(151);
                ldr.remove();
                return true;
            }
        }
        return false;
    }

    MachineInst[] lastGPRsDefMI = new MachineInst[GPRs.values().length];
    MachineInst[] lastFPRsDefMI = new MachineInst[FPRs.values().length];

    private MachineInst getLastDefiner(Operand opd) {
        if (!(opd instanceof Arm.Reg)) return null;
        if (needFPU && opd.isF32()) {
            return lastFPRsDefMI[opd.getValue()];
        }
        return lastGPRsDefMI[opd.getValue()];
    }

    private void putLastDefiner(Operand opd, MachineInst mi) {
        if (!(opd instanceof Arm.Reg)) return;
        if (needFPU && opd.isF32()) lastFPRsDefMI[opd.getValue()] = mi;
        else lastGPRsDefMI[opd.getValue()] = mi;
    }


    private boolean twoStage(MC.McFunction mf) {
        boolean unDone = false;
        for (MC.Block mb : mf.mbList) {
            curMB = mb;
            // TODO MergeBlock的时候自己修吧
            // mb.succMBs = new ArrayList<>();
            mb.liveUseSet = new HashSet<>();
            mb.liveDefSet = new HashSet<>();
            for (MachineInst mi : mb.miList) {
                for (Operand use : mi.useOpds)
                    if (use instanceof Arm.Reg && !mb.liveDefSet.contains(use)) mb.liveUseSet.add(use);
                for (Operand def : mi.defOpds)
                    if (def instanceof Arm.Reg && !mb.liveUseSet.contains(def)) mb.liveDefSet.add(def);

                // TODO MergeBlock的时候自己修吧
                // if (mi.isBranch()) {
                //     mb.succMBs.add(((MIBranch) mi).getTrueTargetBlock());
                //     mb.succMBs.add(((MIBranch) mi).getFalseTargetBlock());
                // } else if (mi.isJump()) {
                //     mb.succMBs.add(((MIJump) mi).getTarget());
                // }
            }
            mb.liveInSet = new HashSet<>(mb.liveUseSet);
            mb.liveOutSet = new HashSet<>();
        }
        RegAllocator.liveInOutAnalysis(mf);

        // HashMap<Operand, MachineInst> lastDefMI = new HashMap<>();
        // HashMap<MachineInst, MachineInst> defMI2lastUserMI = new HashMap<>();
        for (MC.Block mb : mf.mbList) {
            // System.err.println(mb.getLabel());
            // System.err.println("liveIn:\t" + mb.liveInSet);
            // System.err.println("liveOut:\t" + mb.liveOutSet);
            lastGPRsDefMI = new MachineInst[GPRs.values().length];
            lastFPRsDefMI = new MachineInst[FPRs.values().length];
            for (MachineInst mi : mb.miList) {
                mi.theLastUserOfDef = null; // to be removed
                ArrayList<Operand> uses = mi.useOpds;
                ArrayList<Operand> defs = mi.defOpds;
                if (mi.isOf(ICmp, VCmp)) {
                    defs.add(Arm.Reg.getRSReg(cspr));

                }
                if (mi.isCall()) {
                    defs.add(Arm.Reg.getRSReg(cspr));
                    uses.add(Arm.Reg.getRSReg(sp));
                }
                if (!mi.isNoCond()) {
                    uses.add(Arm.Reg.getRSReg(cspr));
                }
                for (Operand use : mi.useOpds) {
                    if (!(use instanceof Arm.Reg)) continue;
                    // TODO r15
                    MachineInst lastDefMI = getLastDefiner(use);
                    if (lastDefMI != null) {
                        lastDefMI.theLastUserOfDef = mi;
                        // defMI2lastUserMI.put(lastDefMI, mi);
                    }
                }
                for (Operand def : mi.defOpds) {
                    if (!(def instanceof Arm.Reg)) continue;
                    // TODO r15
                    putLastDefiner(def, mi);
                }
                if (mi.sideEff()) mi.theLastUserOfDef = mi;
                else mi.theLastUserOfDef = null;
            }

            for (MachineInst mi : mb.miList) {
                // MachineInst lastUser = defMI2lastUserMI.get(mi);
                boolean isLastDefMI = true;
                boolean defRegInLiveOut = false;
                boolean defNoSp = true;
                for (Operand def : mi.defOpds) {
                    if (!mi.equals(getLastDefiner(def))) isLastDefMI = false;
                    if (mb.liveOutSet.contains(def)) defRegInLiveOut = true;
                    if (Arm.Reg.getRSReg(sp).equals(def)) defNoSp = false;
                }
                if (!(isLastDefMI && defRegInLiveOut) && mi.isNoCond()) {
                    if (mi instanceof StackCtl) continue;
                    if (mi.theLastUserOfDef == null && mi.noShift() && defNoSp) {
                        mi.remove();
                        unDone = true;
                        continue;
                    }

                    if (!mi.isNotLastInst()) continue;
                    MachineInst nextMI = (MachineInst) mi.getNext();

                    if (mi.isIMov()) {
                        if (!CodeGen.immCanCode(((I.Mov) mi).getSrc().getValue())) {
                            continue;
                        }
                    }
                    // TODO sub x, a, a
                    // TODO reluctantStr ldr - mla - ldr - str

                    if (mi.noShift()) {
                        // add/sub a c #i
                        // ldr b [a, #x]
                        // =>
                        // ldr b [c, #x+i]
                        // ---------------
                        // add/sub a c #i
                        // move b x
                        // str b [a, #x]
                        // =>
                        // move b x
                        // str b [c, #x+i]
                        if (mi.isOf(Add, Sub)) {
                            I.Binary binary = (I.Binary) mi;
                            if (binary.getROpd().isPureImmWithOutGlob(I32)) {
                                int imm = binary.getROpd().getValue();
                                // MachineInst nextMI = (MachineInst) mi.getNext();
                                if (mi.lastUserIsNext()) {
                                    switch (nextMI.getTag()) {
                                        case Ldr -> {
                                            // add/sub a c #i
                                            // ldr b [a, #x]
                                            // =>
                                            // ldr b [c, #x+i]
                                            I.Ldr ldr = (I.Ldr) nextMI;
                                            if (ldr.getAddr().equals(binary.getDst())
                                                    && ldr.getOffset().isPureImmWithOutGlob(I32)) {
                                                assert !ldr.getShift().hasShift();
                                                if (mi.isOf(Add)) {
                                                    imm += ldr.getOffset().get_I_Imm();
                                                } else if (mi.isOf(Sub)) {
                                                    imm -= ldr.getOffset().get_I_Imm();
                                                } else {
                                                    System.exit(181);
                                                }
                                                if (CodeGen.LdrStrImmEncode(imm)) {
                                                    unDone = true;
                                                    ldr.setAddr(binary.getLOpd());
                                                    ldr.setOffSet(new Operand(I32, imm));
                                                    binary.remove();
                                                }
                                            }
                                        }
                                        case Str -> {
                                            I.Str str = (I.Str) nextMI;
                                            if (str.getAddr().equals(binary.getDst())
                                                    && str.getOffset().isPureImmWithOutGlob(I32)) {
                                                assert !str.getShift().hasShift();
                                                if (mi.isOf(Add)) {
                                                    imm += str.getOffset().get_I_Imm();
                                                } else if (mi.isOf(Sub)) {
                                                    imm -= str.getOffset().get_I_Imm();
                                                } else {
                                                    System.exit(182);
                                                }
                                                if (CodeGen.LdrStrImmEncode(imm)) {
                                                    unDone = true;
                                                    str.setAddr(binary.getLOpd());
                                                    str.setOffSet(new Operand(I32, imm));
                                                    binary.remove();
                                                }
                                            }
                                        }
                                        // case Add -> {
                                        //     I.Binary add2 = (I.Binary) nextMI;
                                        //     // add/sub a c #i
                                        //     // add/sub a c #i
                                        //
                                        // }
                                        // case Sub -> {
                                        //     I.Binary sub2 = (I.Binary) nextMI;
                                        //
                                        //
                                        // }
                                    }
                                } else if (nextMI.isIMov()) {
                                    // add/sub a c #i
                                    // move b x
                                    // str b [a, #x]
                                    // =>
                                    // move b x
                                    // str b [c, #x+i]
                                    // TODO 这个原来会跳过, 现在慎用
                                    // 怀疑已经被fixStack的时候消除了
                                    MachineInst secondNextMI = (MachineInst) mi.getNext();
                                    if (mi.theLastUserOfDef != null
                                            && mi.theLastUserOfDef.equals(secondNextMI)) {
                                        I.Mov iMov = (I.Mov) nextMI;
                                        if (secondNextMI.isOf(Str)) {
                                            I.Str str = (I.Str) secondNextMI;
                                            if (iMov.getDst().equals(str.getData())
                                                    && binary.getDst().equals(str.getAddr())
                                                    && !str.getData().equals(binary.getLOpd())
                                                    && str.getOffset().isPureImmWithOutGlob(I32)) {
                                                assert !str.getShift().hasShift();
                                                if (CodeGen.LdrStrImmEncode(imm)) {
                                                    unDone = true;
                                                    str.setAddr(binary.getLOpd());
                                                    str.setOffSet(new Operand(I32, imm));
                                                    binary.remove();
                                                }
                                            }
                                        }
                                    }
                                }
                            } else if (mi.isOf(Sub) && nextMI.isOf(Sub)
                                    && mi.lastUserIsNext()
                                    && nextMI.getCond() == mi.getCond()
                                    && nextMI.noShift()) {
                                // sub a, b, a
                                // sub b, b, a
                                // a2 = b - a1
                                // b = b - a2 = b - (b - a1) = a1
                                I.Binary sub1 = binary;
                                I.Binary sub2 = (I.Binary) nextMI;
                                HashSet<Operand> aSet = new HashSet<>();
                                aSet.add(sub1.getDst());
                                aSet.add(sub1.getROpd());
                                aSet.add(sub2.getROpd());
                                HashSet<Operand> bSet = new HashSet<>();
                                bSet.add(sub1.getLOpd());
                                bSet.add(sub2.getDst());
                                bSet.add(sub2.getLOpd());
                                if (aSet.size() + bSet.size() <= 2) {
                                    unDone = true;
                                    I.Mov iMov = new I.Mov(sub2.getDst(), sub1.getDst(), sub2);
                                    sub1.remove();
                                    // fixme
                                    sub2.remove();
                                    iMov.theLastUserOfDef = sub2.theLastUserOfDef;
                                    if (lastGPRsDefMI[iMov.getDst().getValue()].equals(sub2)) {
                                        lastGPRsDefMI[iMov.getDst().getValue()] = iMov;
                                    }
                                }
                            }
                        } else if (mi.isOf(IMov)) {
                            I.Mov iMov = (I.Mov) mi;
                            // MachineInst nextMI = (MachineInst) mi.getNext();
                            if (!iMov.getSrc().isImm()
                                    && nextMI instanceof I
                                    && mi.lastUserIsNext()
                                    && !nextMI.isOf(Call, IRet, VRet)) {
                                // mov a, b
                                // anything
                                // =>
                                // anything (replaced)
                                boolean isDeleted = false;
                                for (int i = 0; i < nextMI.useOpds.size(); i++) {
                                    if (nextMI.useOpds.get(i).equals(iMov.getDst())) {
                                        nextMI.useOpds.set(i, iMov.getSrc());
                                        isDeleted = true;
                                    }
                                }
                                if (isDeleted) {
                                    unDone = true;
                                    mi.remove();
                                }
                            } else if (iMov.getSrc().isPureImmWithOutGlob(I32)) {
                                int imm = iMov.getSrc().getValue();
                                if (nextMI.isOf(ICmp) && mi.lastUserIsNext() && nextMI.noShift()) {
                                    // mov a imm
                                    // cmp b a
                                    // =>
                                    // cmp b imm
                                    // TODO cmp a b
                                    I.Cmp icmp = (I.Cmp) nextMI;
                                    Operand dst = iMov.getDst();
                                    if (dst.equals(icmp.getROpd()) && !dst.equals(icmp.getLOpd())) {
                                        if (immCanCode(imm)) {
                                            icmp.setROpd(iMov.getSrc());
                                            unDone = true;
                                            iMov.remove();
                                        } else if (immCanCode(-imm)) {
                                            icmp.setROpd(new Operand(I32, -imm));
                                            icmp.cmn = true;
                                            unDone = true;
                                            iMov.remove();
                                        }
                                    }
                                } else {
                                    // TODO
                                    //  mov Rd, #imm
                                    //  inst use Rd

                                }
                            }
                        } else if (mi instanceof MachineInst.ActualDefMI) {
                            if (mi.lastUserIsNext()) {
                                // MachineInst nextMI = (MachineInst) mi.getNext();
                                if (isSimpleIMov(nextMI)) {
                                    // anything (dst is a)
                                    // move b a (to be remove)
                                    // =>
                                    // anything (replace dst)
                                    Operand def = ((MachineInst.ActualDefMI) mi).getDef();
                                    I.Mov iMov = (I.Mov) nextMI;
                                    if (def.equals(iMov.getSrc())) {
                                        mi.setDef(iMov.getDst());
                                        iMov.remove();
                                        unDone = true;
                                    }
                                } else if (mi.isOf(Mul)
                                        && nextMI.isOf(Add, Sub)
                                        && mi.getCond().equals(nextMI.getCond())/*noCond*/) {
                                    // mul mulDst, mulLOpd, mulROpd
                                    // add/sub asDst, asLOpd, asROpd
                                    // =>
                                    // mla/mls asDst, mulLOpd, mulROpd, (asLOpd / asROpd) <=> mulDst
                                    I.Binary mul = ((I.Binary) mi);
                                    I.Binary addOrSub = (I.Binary) nextMI;
                                    Operand mulDst = mul.getDst();
                                    Operand mulLOpd = mul.getLOpd();
                                    Operand mulROpd = mul.getROpd();
                                    Operand asDst = addOrSub.getDst();
                                    Operand asLOpd = addOrSub.getLOpd();
                                    Operand asROpd = addOrSub.getROpd();
                                    boolean flag1 = mulDst.equals(asLOpd);
                                    boolean flag2 = mulDst.equals(asROpd);
                                    I.Fma fma = null;
                                    if (flag1 && !flag2 && !asROpd.isImm()) {
                                        fma = new I.Fma(addOrSub, nextMI.isOf(Add), false,
                                                asDst, mulLOpd, mulROpd, asROpd);
                                    } else if (flag2 && !flag1/* && !asLOpd.isImm()*/) {
                                        fma = new I.Fma(addOrSub, nextMI.isOf(Add), false,
                                                asDst, mulLOpd, mulROpd, asLOpd);
                                    }
                                    if (fma != null) {
                                        unDone = true;
                                        fma.theLastUserOfDef = addOrSub.theLastUserOfDef;
                                        if(lastGPRsDefMI[asDst.getValue()].equals(addOrSub)){
                                            lastGPRsDefMI[asDst.getValue()] = fma;
                                        }
                                        mul.remove();
                                        // fixme
                                        addOrSub.remove();
                                    }
                                }
                            }
                        }
                    } else {
                        if (mi.isOf(Add)) {
                            I.Binary addMI = (I.Binary) mi;
                            if (!(addMI.getROpd().isImm() && LdrStrImmEncode(addMI.getROpd().getValue()))) {
                                // MachineInst nextMI = (MachineInst) mi.getNext();
                                switch (nextMI.getTag()) {
                                    case Ldr, Str -> {
                                        // add a, b, c, shift
                                        // ldr/str x, [a, #0]
                                        // =>
                                        // ldr/str x, [b, c, shift]
                                        if (mi.lastUserIsNext()) {
                                            MachineMemInst mem = (MachineMemInst) nextMI;
                                            if (addMI.getDst().equals(mem.getAddr())
                                                    && mem.getOffset().equals(Operand.I_ZERO)
                                                    && mem.isNoCond()) {
                                                unDone = true;
                                                mem.setAddr(addMI.getLOpd());
                                                mem.setOffSet(addMI.getROpd());
                                                mem.addShift(addMI.getShift());
                                                addMI.remove();
                                            }
                                        }
                                    }
                                    case IMov -> {
                                        // add a, b, c, shift
                                        // move d y
                                        // str d [a, #0]
                                        // =>
                                        // move d y
                                        // str d [b, c shift]
                                        // ----------------------------------
                                        // this situation should be avoided:
                                        // add a, d, c, shift
                                        // move d y
                                        // str d [d, c shift]
                                        if (nextMI.isNotLastInst() && nextMI.isNoCond()) {
                                            MachineInst secondNextMI = (MachineInst) nextMI.getNext();
                                            if (secondNextMI.isNotLastInst() && secondNextMI.isNoCond()
                                                    && mi.theLastUserOfDef != null
                                                    && mi.theLastUserOfDef.equals(secondNextMI)
                                                    && secondNextMI.isOf(Str)) {
                                                I.Str str = (I.Str) secondNextMI;
                                                I.Mov mov = (I.Mov) nextMI;
                                                if (mov.getDst().equals(str.getData())
                                                        && str.getAddr().equals(addMI.getDst())
                                                        && !str.getData().equals(addMI.getLOpd())
                                                        && !str.getData().equals(addMI.getROpd())
                                                        && str.getOffset().equals(Operand.I_ZERO)) {
                                                    str.setAddr(addMI.getLOpd());
                                                    str.setOffSet(addMI.getROpd());
                                                    str.addShift(addMI.getShift());
                                                    addMI.remove();
                                                    unDone = true;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (mi.isOf(IMov)
                                && mi.lastUserIsNext()
                                && nextMI.getCond() == mi.getCond()
                                && nextMI.noShift()) {
                            // mov a b shift
                            // instr c a
                            // =>
                            // instr c b shift
                            I.Mov iMov = (I.Mov) mi;
                            switch (nextMI.getTag()) {
                                case Add, Sub -> {
                                    // mov a b shift
                                    // add c d a
                                    // =>
                                    // add c d b shift
                                    I.Binary binary = (I.Binary) nextMI;
                                    Operand lOpd = binary.getLOpd();
                                    Operand rOpd = binary.getROpd();
                                    if (rOpd.equals(iMov.getDst()) && !lOpd.equals(rOpd)) {
                                        // assert false;
                                        assert !rOpd.isImm();
                                        binary.setROpd(iMov.getSrc());
                                        binary.addShift(iMov.getShift());
                                        unDone = true;
                                        iMov.remove();
                                    }
                                }
                                case Ldr, Str -> {
                                    // mov a b shift
                                    // ldr c, [d a]
                                    // =>
                                    // ldr c, [d b shift]

                                    // mov a b shift
                                    // str c, [d a]
                                    // =>
                                    // str c, [d b shift]

                                    MachineMemInst memLdrStr = (MachineMemInst) nextMI;
                                    if (memLdrStr.getOffset().equals(iMov.getDst())
                                            && memLdrStr.getShift().noShift()
                                            && !memLdrStr.getAddr().equals(iMov.getDst())
                                            && ((nextMI.isOf(Ldr) || memLdrStr.getData().equals(iMov.getDst())))) {
                                        unDone = true;
                                        memLdrStr.setOffSet(iMov.getDst());
                                        memLdrStr.addShift(iMov.getShift());
                                        iMov.remove();
                                    }
                                }
                                case IMov -> {
                                    // mov a b shift
                                    // mov c a
                                    // =>
                                    // mov c b shift
                                    I.Mov nextMov = (I.Mov) nextMI;
                                    if (nextMov.getSrc().equals(iMov.getDst())) {
                                        unDone = true;
                                        nextMov.setSrc(iMov.getSrc());
                                        nextMov.addShift(iMov.getShift());
                                        iMov.remove();
                                    }
                                }
                            }
                        }
                    }

                }
            }
        }
        return unDone;
    }

    private boolean isSimpleIMov(MachineInst mi) {
        return mi.isIMov() && mi.noShiftAndCond();
    }
}