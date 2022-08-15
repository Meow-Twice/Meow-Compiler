package backend;

import lir.MC;
import lir.MIJump;
import lir.MachineInst;
import util.ILinkNode;

import java.util.ArrayList;

public class MergeBlock {
    public MC.Program p = MC.Program.PROGRAM;

    public void run() {
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
                            if ((predMb.succMBs.size() == 1 || predMb.falseSucc().equals(curMB)) && !predMb.equals(curMB.getPrev())) {
                                //如果pred只有一个后继，且线性化后本块不是pred的下一个块
                                //或者如果本块是pred的false后继，且线性化后本块不是pred的下一个块
                                MIJump j = (MIJump) predMb.getEndMI();
                                ArrayList<MachineInst> list = new ArrayList<>();
                                for (MachineInst mi : curMB.miList) {
                                    if (!mi.isOf(MachineInst.Tag.Comment, MachineInst.Tag.Jump)) list.add(mi);
                                }
                                for (MachineInst mi : list) {
                                    mi.setNext(j);
                                }
                                if (predMb.getNext().equals(onlySuccMB)) {
                                    j.remove();
                                } else {
                                    j.setTarget(onlySuccMB);
                                }
                                removeList.add(predMb);
                                if (predMb.succMBs.size() == 1) {
                                    predMb.setTrue(onlySuccMB);
                                } else {
                                    predMb.setFalse(onlySuccMB);
                                }
                                onlySuccMB.predMBs.add(predMb);
                            } else if (predMb.trueSucc().equals(curMB) && predMb.succMBs.size() > 1) {
                                //如果pred有两个后继，且本块是pred的True后继

                            }
                        }
                    }
                    case 2 -> {

                    }
                }
                for (MC.Block r : removeList) {
                    boolean flag = true;
                    for (MC.Block succ : r.succMBs) {
                        if (curMB.equals(succ)) {
                            flag = false;
                            break;
                        }
                    }
                    if (flag) r.remove();
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
