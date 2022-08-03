package backend;

import lir.*;
import lir.Machine.Operand;
import util.ILinkNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import static lir.MachineInst.MachineMemInst;
import static lir.MachineInst.MachineMove;
import static lir.MachineInst.Tag.*;
import static mir.type.DataType.I32;

public class PeepHole {
    final Machine.Program p;

    public PeepHole(Machine.Program p) {
        this.p = p;
    }

    public void run() {
        for (Machine.McFunction mf : p.funcList) {
            boolean unDone = true;
            while (unDone) {
                unDone = oneStage(mf);
//                if (twoStage(mf))
//                    unDone = true;
            }
        }
    }

    Machine.Block curMB = null;

    // 注意不能用unDone当条件来判断是否remove之类, 可能是上一次结果的残留
    public boolean oneStage(Machine.McFunction mf) {
        boolean unDone = false;
        for (Machine.Block mb : mf.mbList) {
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

    private static class LiveRange {
        HashMap<Operand, MachineInst> lastDefMI = new HashMap<>();
        HashMap<Operand, MachineInst> lastUseMI = new HashMap<>();
    }

    private boolean twoStage(Machine.McFunction mf) {
        boolean unDone = false;
        for (Machine.Block mb : mf.mbList) {
            curMB = mb;
            mb.succMBs = new ArrayList<>();
            mb.liveUseSet = new HashSet<>();
            mb.liveDefSet = new HashSet<>();
            for (MachineInst mi : mb.miList) {
                for (Operand use : mi.useOpds) {
                    if (!mb.liveDefSet.contains(use)) mb.liveUseSet.add(use);
                }
                for (Operand def : mi.defOpds) {
                    if (!mb.liveUseSet.contains(def)) mb.liveDefSet.add(def);
                }
                if (mi.isBranch()) {
                    mb.succMBs.add(((MIBranch) mi).getTrueTargetBlock());
                    mb.succMBs.add(((MIBranch) mi).getFalseTargetBlock());
                } else if (mi.isJump()) {
                    mb.succMBs.add(((MIJump) mi).getTarget());
                }
            }
            mb.liveInSet = new HashSet<>(mb.liveUseSet);
            mb.liveOutSet = new HashSet<>();
        }
        RegAllocator.liveInOutAnalysis(mf);

        HashMap<Operand, MachineInst> lastDefMI = new HashMap<>();
        HashMap<MachineInst, MachineInst> lastUseMI = new HashMap<>();
        for (Machine.Block mb : mf.mbList) {
            for (MachineInst mi : mb.miList) {
                for (Operand use : mi.useOpds) {
                    if (lastDefMI.containsKey(use)) {
                        lastUseMI.put(lastDefMI.get(use), mi);
                    }
                }
                for (Operand def : mi.defOpds) {
                    lastDefMI.put(def, mi);
                }
                if (mi.sideEff()) {
                    lastUseMI.put(mi, mi);
                } else {
                    lastUseMI.put(mi, null);
                }
            }

            for (MachineInst mi : mb.miList) {
                MachineInst lastUser = lastUseMI.get(mi);
                boolean isLastDefMI = true;
                boolean defRegInLiveOut = false;
                boolean defNoSp = true;
                for (Operand def : mi.defOpds) {
                    if (!lastDefMI.get(def).equals(mi)) isLastDefMI = false;
                    if (mb.liveOutSet.contains(def)) defRegInLiveOut = true;
                    if (Arm.Reg.getRSReg(Arm.Regs.GPRs.sp).equals(def)) defNoSp = false;
                }
                if (!(isLastDefMI && defRegInLiveOut) && mi.isNoCond()) {
                    if (lastUser == null && !mi.getShift().hasShift() && defNoSp) {
                        mi.remove();
                        unDone = true;
                        continue;
                    }

                    if (mi.isIMov()) {
                        if (!CodeGen.immCanCode(((I.Mov) mi).getSrc().get_I_Imm())) {
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
                            if (binary.getROpd().isImm(I32)) {
                                int imm = binary.getROpd().getValue();
                                if (!mi.getNext().equals(mb.miList.tail)
                                        && lastUser != null
                                        && !lastUser.equals(mi.getNext())) {
                                    MachineInst nextInst = (MachineInst) mi.getNext();
                                    if (nextInst.isOf(Ldr)) {
                                        I.Ldr ldr = (I.Ldr) nextInst;
                                        if (ldr.getAddr().equals(binary.getDst())
                                                && ldr.getOffset().isImm(I32)) {
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
                                                && str.getOffset().isImm(I32)) {
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
                                                // && lastUser != null
                                                && !lastUser.equals(mi.getNext())) {
                                            I.Mov iMov = (I.Mov) nextInst;
                                            MachineInst secondNextMI = (MachineInst) mi.getNext();
                                            if (secondNextMI.isOf(Str)) {
                                                I.Str str = (I.Str) secondNextMI;
                                                if (iMov.getDst().equals(str.getData())
                                                        && binary.getDst().equals(str.getAddr())
                                                        && !str.getData().equals(binary.getLOpd())
                                                        && str.getOffset().isImm(I32)) {
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
                        }
                    }
                }

            }
        }
        return unDone;
    }
}