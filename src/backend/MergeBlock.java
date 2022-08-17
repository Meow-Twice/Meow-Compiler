package backend;

import lir.*;
import manage.Manager;
import util.ILinkNode;

import static lir.BJ.*;

import java.util.ArrayList;

public class MergeBlock {
    public MC.Program p = MC.Program.PROGRAM;

    public void run(boolean fflag) {
        for (MC.McFunction mf : p.funcList) {
            for (ILinkNode mb = mf.mbList.getEnd(); !mb.equals(mf.mbList.head); mb = mb.getPrev()) {
                final MC.Block curMB = (MC.Block) mb;
                boolean hasCmp = false;
                boolean hasCond = false;
                boolean hasCall = false;
                int branchNum = 0;
                int jumpNum = 0;
                int miNum = 0;
                for (MachineInst mi : curMB.miList) {
                    if (mi.isComment()) continue;
                    miNum++;
                    if (mi.hasCond()) hasCond = true;
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
                                //或者如果本块是pred的false后继(ll中的true块, 一定紧跟着当前块)，且线性化后本块不是pred的下一个块 // 这种情况好像不会出现， predMb.falseSucc()为原ll中true块一定放在了predMb的后面一个
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
                                // assert false;

                                //如果pred有两个后继，且本块是pred的True后继
                                assert predMb.succMBs.size() == 2;
                                //如果线性化后pred正好在mb前面，且pred的true和false后继都是mb，那么需要特殊处理 // 好像不会有这种情况
                                if (predMb.trueSucc() == curMB && predMb.falseSucc() == curMB) {
                                    ILinkNode node = predMb.getEndMI();
                                    while (!(node instanceof GDBranch || node.equals(predMb.miList.head))) {
                                        node = node.getPrev();
                                    }
                                    if (!node.equals(predMb.miList.head)) {
                                        boolean subHasCompare = false;
                                        boolean subHasCond = false;
                                        boolean subHasCall = false;
                                        int subBranchNum = 0;
                                        int subJumpNum = 0;
                                        int subMINum = 0;//非comment数量
                                        assert node instanceof GDBranch;
                                        GDBranch branch = (GDBranch) node;
                                        ILinkNode entry = branch.getNext();
                                        while (entry != predMb.miList.tail) {
                                            MachineInst mi = (MachineInst) entry;
                                            entry = entry.getNext();
                                            if (!(mi.isComment())) {
                                                subMINum++;
                                                if (mi.hasCond()) {
                                                    subHasCond = true;
                                                }
                                                switch (mi.getTag()) {
                                                    case ICmp, VCmp -> subHasCompare = true;
                                                    case Call -> subHasCall = true;
                                                    case Jump -> subJumpNum++;
                                                    case Branch -> subBranchNum++;
                                                }
                                            }
                                        }
                                        if (subHasCompare || subHasCall || subHasCond || (subMINum) > 5 || subBranchNum > 0 || subJumpNum > 0) {
                                            continue;
                                        } else {
                                            entry = branch.getNext();
                                            while (entry != predMb.miList.tail) {
                                                MachineInst mi = (MachineInst) entry;
                                                entry = entry.getNext();
                                                mi.setCond(branch.getOppoCond());
                                            }
                                            branch.remove();
                                        }
                                    }
                                } else {
                                    if (hasCmp || hasCall || hasCond || (miNum - branchNum - jumpNum) > 5) {
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
                                Manager.MANAGER.outputMI();

                            }
                        }
                    }
                    case 2 -> {

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
}
