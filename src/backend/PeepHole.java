package backend;

import lir.*;
import lir.Machine.Operand;
import util.ILinkNode;

import static lir.MachineInst.Tag.*;
import static mir.type.DataType.I32;

public class PeepHole {
    final Machine.Program p;

    public PeepHole(Machine.Program p) {
        this.p = p;
    }

    public void run() {
        oneStage();
    }

    private static final Operand I_ZERO = new Operand(I32, 0);

    Machine.Block curMB = null;

    public void oneStage() {
        boolean unDone = true;
        while (unDone) {
            unDone = false;
            for (Machine.McFunction mf : p.funcList) {
                for (Machine.Block mb : mf.mbList) {
                    curMB = mb;
                    for (ILinkNode i = mb.miList.getBegin(); !i.equals(mb.miList.tail); i =i.getNext()) {
                        // if(mi.getPrev())
                        MachineInst mi = (MachineInst) i;
                        MachineInst prevInst = mi.getPrev() == mb.miList.head ? MachineInst.emptyInst : (MachineInst) mi.getPrev();
                        MachineInst nextInst = mi.getNext() == mb.miList.tail ? MachineInst.emptyInst : (MachineInst) mi.getNext();
                        switch (mi.getTag()) {
                            case Add, Sub -> {
                                I.Binary bino = (I.Binary) mi;
                                if (bino.getROpd().equals(I_ZERO)) {
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
                                // trivialMemPeep(Ldr, Str);
                                I.Ldr ldr = (I.Ldr) mi;
                                if (prevInst.isOf(Str)) {
                                    // TODO ldr 和 str 不可能有条件
                                    I.Str str = (I.Str) prevInst;
                                    assert ldr.isNoCond() && str.isNoCond();
                                    if (ldr.getAddr().equals(str.getAddr())
                                            && ldr.getOffset().equals(str.getOffset())
                                            && ldr.getShift().equals(str.getShift())) {
                                        unDone = true;
                                        new I.Mov(ldr.getData(), str.getData(), ldr);
                                        ldr.remove();
                                    }
                                }
                            }
                            case VLdr -> {
                                V.Ldr vldr = (V.Ldr) mi;
                                if (prevInst.isOf(VStr)) {
                                    // TODO ldr 和 str 不可能有条件
                                    V.Str vStr = (V.Str) prevInst;
                                    assert vldr.isNoCond() && vStr.isNoCond();
                                    if (vldr.getAddr().equals(vStr.getAddr())
                                            && vldr.getOffset().equals(vStr.getOffset())
                                            && vldr.getShift().equals(vStr.getShift())) {
                                        unDone = true;
                                        new V.Mov(vldr.getData(), vStr.getData(), vldr);
                                        vldr.remove();
                                    }
                                }
                            }
                            case IMov -> {
                                if (!mi.getShift().hasShift()) {
                                    I.Mov curMov = (I.Mov) mi;
                                    if (curMov.getDst().equals(curMov.getSrc())) {
                                        unDone = true;
                                        curMov.remove();
                                    } else if (curMov.isNoCond() && nextInst.isOf(IMov)) {
                                        I.Mov nextMov = (I.Mov) nextInst;
                                        if (nextMov.getDst().equals(nextMov.getSrc())) {
                                            unDone = true;
                                            nextMov.remove();
                                        } else if (nextMov.getDst().equals(curMov.getDst())) {
                                            unDone = true;
                                            curMov.remove();
                                        }
                                    }
                                }
                            }
                            case VMov -> {
                                if (!mi.getShift().hasShift()) {
                                    V.Mov curVMov = (V.Mov) mi;
                                    if (curVMov.getDst().equals(curVMov.getSrc())) {
                                        unDone = true;
                                        curVMov.remove();
                                    } else if (curVMov.isNoCond() && nextInst.isOf(VMov)) {
                                        V.Mov nextVMov = (V.Mov) nextInst;
                                        if (nextVMov.getDst().equals(nextVMov.getSrc())) {
                                            unDone = true;
                                            nextVMov.remove();
                                        } else if (nextVMov.getDst().equals(curVMov.getDst())) {
                                            unDone = true;
                                            curVMov.remove();
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
