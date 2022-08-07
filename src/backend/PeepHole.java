package backend;

import lir.*;
import lir.MC.Operand;
import lir.Arm.Regs.*;
import util.ILinkNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import static backend.CodeGen.needFPU;
import static lir.Arm.Regs.GPRs.cspr;
import static lir.Arm.Regs.GPRs.sp;
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
                        if (mi.isNoCond() && ((MIJump) mi).getTarget().equals(curMB.getNext())) {
                            unDone = true;
                            mi.remove();
                        }
                    }
                    case Branch -> {
                        // TODO 可能改变branch方式
                        if (((MIBranch) mi).getFalseTargetBlock().equals(curMB.getNext())) {
                            unDone = true;
                            new MIJump(mi.getCond(), ((MIBranch) mi).getTrueTargetBlock(), mi);
                            mi.remove();
                        }
                    }
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
                    if (mi.theLastUserOfDef == null && !mi.getShift().hasShift() && defNoSp) {
                        mi.remove();
                        unDone = true;
                        continue;
                    }

                    if (mi.isIMov()) {
                        if (!CodeGen.immCanCode(((I.Mov) mi).getSrc().getValue())) {
                            continue;
                        }
                    }

                    if (!mi.getShift().hasShift()) {
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
                                if (!mi.getNext().equals(mb.miList.tail)
                                        && mi.lastUserIsNext()) {
                                    MachineInst nextInst = (MachineInst) mi.getNext();
                                    if (nextInst.isOf(Ldr)) {
                                        I.Ldr ldr = (I.Ldr) nextInst;
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
                                    } else if (nextInst.isOf(Str)) {
                                        I.Str str = (I.Str) nextInst;
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
                                    } else if (nextInst.isIMov()) {
                                        // 怀疑已经被fixStack的时候消除了
                                        if (!mi.getNext().equals(mb.miList.tail)
                                                && mi.lastUserIsNext()) {
                                            I.Mov iMov = (I.Mov) nextInst;
                                            MachineInst secondNextMI = (MachineInst) mi.getNext();
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
                                }
                            }
                        } else if (mi.isOf(IMov)) {
                            I.Mov iMov = (I.Mov) mi;
                            if (!iMov.getSrc().isImm()) {
                                if (!mi.getNext().equals(mb.miList.tail)) {
                                    MachineInst nextMI = (MachineInst) mi.getNext();
                                    if (nextMI instanceof I
                                            && mi.lastUserIsNext()
                                            && !nextMI.isOf(Call, IRet, VRet)) {
                                        for (int i = 0; i < nextMI.useOpds.size(); i++) {
                                            if (nextMI.useOpds.get(i).equals(iMov.getDst())) {
                                                nextMI.useOpds.set(i, iMov.getSrc());
                                                unDone = true;
                                            }
                                        }
                                        mi.remove();
                                    }
                                }
                            }
                        } else if (mi instanceof MachineInst.ActualDefMI) {
                            if (!mi.getNext().equals(mb.miList.tail)) {
                                MachineInst nextMI = (MachineInst) mi.getNext();
                                if (nextMI instanceof I
                                        && mi.lastUserIsNext()
                                        && isSimpleIMov(nextMI)) {
                                    Operand def = ((MachineInst.ActualDefMI) mi).getDef();
                                    I.Mov iMov = (I.Mov) nextMI;
                                    if (def.equals(iMov.getSrc())) {
                                        mi.setDef(iMov.getDst());
                                        iMov.remove();
                                        unDone = true;
                                    }
                                }
                            }
                        }
                    } else {

                    }
                }
            }
        }
        return unDone;
    }

    private boolean isSimpleIMov(MachineInst mi) {
        return mi.isIMov() && mi.noShift();
    }
}