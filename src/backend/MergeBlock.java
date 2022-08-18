package backend;

import lir.*;
import manage.Manager;
import util.ILinkNode;

import static lir.BJ.*;

import java.util.ArrayList;

public class MergeBlock {
    public MC.Program p = MC.Program.PROGRAM;

    boolean yesCondMov = true;

    public void run(boolean yesCondMov) {
        this.yesCondMov = yesCondMov;
        for (MC.McFunction mf : p.funcList) {
            for (ILinkNode mb = mf.mbList.getEnd(); !mb.equals(mf.mbList.head); mb = mb.getPrev()) {
                final MC.Block curMB = (MC.Block) mb;
                boolean hasCmp = false;
                boolean hasCond = false;
                boolean hasCall = false;
                boolean hasV = false;
                int branchNum = 0;
                int jumpNum = 0;
                int miNum = 0;
                for (MachineInst mi : curMB.miList) {
                    if (mi.isComment()) continue;
                    miNum++;
                    if (mi.hasCond()) hasCond = true;
                    if (mi instanceof V) hasV = true;
                    switch (mi.getTag()) {
                        case ICmp, VCmp -> hasCmp = true;
                        case Call -> hasCall = true;
                        case Branch -> branchNum++;
                        case Jump -> jumpNum++;
                    }
                }
                ArrayList<MC.Block> removeList = new ArrayList<>();
                switch (curMB.succMBs.size()) {
                    case 1 -> {
                        MC.Block onlySuccMB = curMB.succMBs.get(0);
                        for (MC.Block predMb : curMB.predMBs) {
                            if ((predMb.succMBs.size() == 1 || curMB.equals(predMb.falseSucc())) && !predMb.equals(curMB.getPrev())) {
                                // System.exit(201);
                                //如果pred只有一个后继，且线性化后本块不是pred的下一个块
                                //或者如果本块是pred的false后继(ll中的true块, 不一定紧跟着当前块)，且线性化后本块不是pred的下一个块 // 这种情况好像不会出现， predMb.falseSucc()为原ll中true块一定放在了predMb的后面一个
                                // if (!(predMb.getEndMI() instanceof GDJump)) {
                                //     int a = 0;
                                // }
                                GDJump bj = (GDJump) predMb.getEndMI();
                                ArrayList<MachineInst> list = new ArrayList<>();
                                for (MachineInst mi : curMB.miList) {
                                    if (!mi.isOf(MachineInst.Tag.Comment, MachineInst.Tag.Jump, MachineInst.Tag.Branch))
                                        list.add(mi);
                                }
                                for (MachineInst mi : list) {
                                    predMb.miList.insertBefore(mi.clone(), bj);
                                }
                                if (predMb.getNext().equals(onlySuccMB)) {
                                    bj.remove();
                                } else {
                                    //assert false;
                                    //这个的前提是这里的块的最后一条是无条件跳转
                                    bj.setTarget(onlySuccMB);
                                }
                                removeList.add(predMb);
                                if (predMb.succMBs.size() == 1) {
                                    predMb.setTrueSucc(onlySuccMB);
                                } else {
                                    // assert curMB.equals(predMb.falseSucc());
                                    // System.exit(32);
                                    predMb.setFalseSucc(onlySuccMB);
                                }
                                onlySuccMB.predMBs.add(predMb);
                            } else if (predMb.succMBs.size() > 1 && predMb.trueSucc().equals(curMB)) {
                                //如果pred有两个后继，且本块是pred的True后继
                                assert predMb.succMBs.size() == 2;
                                //如果线性化后pred正好在mb前面，且pred的true和false后继都是mb，那么需要特殊处理
                                if (predMb == curMB.getPrev() && predMb.falseSucc() == curMB) {
                                    // System.exit(122);
                                    dealPred(predMb);
                                } else if (yesCondMov) {
                                    if (hasV || hasCmp || hasCall || hasCond || (miNum - branchNum - jumpNum) > 5) {
                                        continue;
                                    }
                                    removeList.add(predMb);
                                    ILinkNode node = predMb.getEndMI();
                                    while (!(node instanceof GDBranch)) {
                                        node = node.getPrev();
                                    }
                                    GDBranch bj = (GDBranch) node;
                                    ArrayList<MachineInst> list = new ArrayList<>();
                                    for (MachineInst mi : curMB.miList) {
                                        if (!mi.isOf(MachineInst.Tag.Comment, MachineInst.Tag.Jump, MachineInst.Tag.Branch))
                                            list.add(mi);
                                    }
                                    for (MachineInst mi : list) {
                                        MachineInst newMI = mi.clone();
                                        newMI.setCond(bj.getCond());
                                        predMb.miList.insertBefore(newMI, bj);
                                    }
                                    bj.setTarget(onlySuccMB);
                                    predMb.setTrueSucc(onlySuccMB);
                                    if (predMb.trueSucc() == predMb.falseSucc()) {
                                        bj.remove();
                                    }
                                    // assert predMb.trueSucc() != predMb.falseSucc();
                                    onlySuccMB.predMBs.add(predMb);
                                }
                                // Manager.MANAGER.outputMI();

                            }
                        }
                    }
                    case 2 -> {
                        for (MC.Block predMb : curMB.predMBs) {
                            //如果线性化后pred就是本块的上一个块
                            if (predMb.equals(curMB.getPrev())) {
                                //如果线性化后pred正好在mb前面，且pred的true和false后继都是mb，那么需要特殊处理
                                if (curMB == predMb.trueSucc() && predMb.falseSucc() == predMb.trueSucc()) {
                                    // fixme 有没有可能不等于curMB
                                    // System.exit(122);
                                    dealPred(predMb);
                                }
                                // Manager.MANAGER.outputMI();
                            } else if (!curMB.getEndMI().isJump()) {
                                continue;
                            } else if (predMb.succMBs.size() == 1) {
                                // Manager.MANAGER.outputMI();
                                //如果pred只有一个后继，且线性化后本块不是pred的下一个块
                                assert predMb.miList.getEnd().isJump();
                                GDJump j = (GDJump) predMb.getEndMI();
                                MC.Block newMB = new MC.Block(curMB, predMb);
                                // predMb.insertAfter(newMB);
                                for (MachineInst mi : curMB.miList) {
                                    newMB.miList.insertAtEnd(mi.clone());
                                }
                                j.remove();
                                removeList.add(predMb);
                                predMb.succMBs.set(0, newMB);
                                newMB.succMBs.addAll(curMB.succMBs);
                                newMB.predMBs.add(predMb);
                            } else if (predMb.succMBs.size() == 2 && predMb.falseSucc() == curMB) {
                                //或者如果本块是pred的false后继，且线性化后本块不是pred的下一个块(第一个if排除)
                                // Manager.MANAGER.outputMI();
                                assert predMb.miList.getEnd().isJump();
                                GDJump j = (GDJump) predMb.getEndMI();
                                MC.Block newMB = new MC.Block(curMB, predMb);
                                // predMb.insertAfter(newMB);
                                for (MachineInst mi : curMB.miList) {
                                    newMB.miList.insertAtEnd(mi.clone());
                                }
                                j.remove();
                                removeList.add(predMb);
                                predMb.succMBs.set(1, newMB);
                                newMB.succMBs.addAll(curMB.succMBs);
                                newMB.predMBs.add(predMb);
                            }
                        }

                    }
                }
                for (MC.Block pred : removeList) {
                    boolean flag = true;
                    for (MC.Block succ : pred.succMBs) {
                        if (curMB.equals(succ)) {
                            flag = false;
                            break;
                        }
                    }
                    if (flag) curMB.predMBs.remove(pred);
                }
                if (curMB.predMBs.isEmpty() && !mb.equals(mf.getBeginMB())) {
                    curMB.remove();
                    for (MC.Block succ : curMB.succMBs) {
                        succ.predMBs.remove(curMB);
                    }
                }
            }
        }
    }

    static int cnt = 0;

    private void dealPred(MC.Block predMb) {
        if (!yesCondMov) return;
        ILinkNode node = predMb.getEndMI();
        while (!(node instanceof GDBranch || node.equals(predMb.miList.head))) {
            node = node.getPrev();
        }
        if (!node.equals(predMb.miList.head)) {
            boolean hasCmp = false;
            boolean hasCond = false;
            boolean hasCall = false;
            boolean hasV = false;
            int brNum = 0;
            int jNum = 0;
            int miNum = 0;//非comment数量
            assert node instanceof GDBranch;
            GDBranch branch = (GDBranch) node;

            for (ILinkNode entry = branch.getNext(); entry != predMb.miList.tail; entry = entry.getNext()) {
                MachineInst mi = (MachineInst) entry;
                if (!(mi.isComment())) {
                    miNum++;
                    if (mi.hasCond()) {
                        hasCond = true;
                    }
                    switch (mi.getTag()) {
                        case ICmp, VCmp -> hasCmp = true;
                        case Call -> hasCall = true;
                        case Jump -> jNum++;
                        case Branch -> brNum++;
                    }
                    if (mi instanceof V) {
                        hasV = true;
                    }
                }
            }
            if (!hasV && !hasCmp && !hasCall && !hasCond && (miNum) <= 5 && brNum <= 0 && jNum <= 0) {
                for (ILinkNode entry = branch.getNext(); entry != predMb.miList.tail; entry = entry.getNext()) {
                    MachineInst mi = (MachineInst) entry;
                    mi.setCond(branch.getOppoCond());
                }
                branch.remove();
            }
        }
    }
}
