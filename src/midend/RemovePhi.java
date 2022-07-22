package midend;

import mir.*;

import java.util.ArrayList;
import java.util.HashSet;

public class RemovePhi {

    private ArrayList<Function> functions;

    public RemovePhi(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        RemovePhiAddPCopy();
        ReplacePCopy();
    }

    private void RemovePhiAddPCopy() {
        for (Function function: functions) {
            removeFuncPhi(function);
        }
    }

    private void ReplacePCopy() {
        for (Function function: functions) {
            replacePCopyForFunc(function);
        }
    }


    private void removeFuncPhi(Function function) {
        BasicBlock bb = function.getBeginBB();
        while (bb.getNext() != null) {
            ArrayList<BasicBlock> pres = new ArrayList<>();
            for (BasicBlock b:bb.getPrecBBs()) {
                pres.add(b);
            }
            ArrayList<Instr.PCopy> PCopys = new ArrayList<>();
            //TODO:使用迭代器遍历会导致报错:遍历中修改元素 ?
            for (int i = 0; i < pres.size(); i++) {
                BasicBlock incomeBB = pres.get(i);

                if (incomeBB.getSuccBBs().size() > 1) {
                    // TODO: 这里不知道优化的时候， incomeBB的loop是不是null
                    BasicBlock mid = new BasicBlock(function, incomeBB.loop);
                    Instr.PCopy pCopy = new Instr.PCopy(new ArrayList<>(), new ArrayList<>(), mid);
                    PCopys.add(pCopy);
                    addMidBB(incomeBB, mid, bb);
                } else {
                    Instr endInstr = incomeBB.getEndInstr();
                    Instr.PCopy pCopy = new Instr.PCopy(new ArrayList<>(), new ArrayList<>(), incomeBB);
                    endInstr.insertBefore(pCopy);
                    PCopys.add(pCopy);
                }

            }

            Instr instr = bb.getBeginInstr();
            while (instr instanceof Instr.Phi) {
                ArrayList<Value> phiRHS = instr.getUseValueList();
                for (int i = 0; i < phiRHS.size(); i++) {
                    PCopys.get(i).addToPC(instr, phiRHS.get(i));
                }
                instr = (Instr) instr.getNext();
            }

            instr = bb.getBeginInstr();
            while (instr instanceof Instr.Phi) {
                Instr temp = instr;
                instr = (Instr) instr.getNext();
                //temp.remove();
                temp.delFromNowBB();
            }

            bb = (BasicBlock) bb.getNext();
        }
    }

    private void addMidBB(BasicBlock src, BasicBlock mid, BasicBlock tag) {
        src.getSuccBBs().remove(tag);
        src.getSuccBBs().add(mid);
        mid.getSuccBBs().add(tag);
        tag.getPrecBBs().remove(src);
        tag.getPrecBBs().add(mid);

        Instr instr = src.getEndInstr();
        assert instr instanceof Instr.Branch;
        BasicBlock thenBB = ((Instr.Branch) instr).getThenTarget();
        BasicBlock elseBB = ((Instr.Branch) instr).getElseTarget();

        if (tag.equals(thenBB)) {
            ((Instr.Branch) instr).setThenTarget(mid);
            Instr.Jump jump = new Instr.Jump(tag, mid);
        } else if (tag.equals(elseBB)) {
            ((Instr.Branch) instr).setElseTarget(mid);
            Instr.Jump jump = new Instr.Jump(tag, mid);
        } else {
            System.err.println("Panic At Remove PHI addMidBB");
        }

    }

    private void replacePCopyForFunc(Function function) {
        BasicBlock bb = function.getBeginBB();
        while (bb.getNext() != null) {
            Instr instr = bb.getBeginInstr();
            ArrayList<Instr> moves = new ArrayList<>();
            ArrayList<Instr> PCopys = new ArrayList<>();
            while (instr.getNext() != null) {
                if (!(instr instanceof Instr.PCopy)) {
                    instr = (Instr) instr.getNext();
                    continue;
                }
                PCopys.add(instr);
                ArrayList<Value> tags = ((Instr.PCopy) instr).getLHS();
                ArrayList<Value> srcs = ((Instr.PCopy) instr).getRHS();

                HashSet<String> tagNameSet = new HashSet<>();
                HashSet<String> srcNameSet = new HashSet<>();

                removeUndefCopy(tags, srcs, tagNameSet, srcNameSet);

                while (!checkPCopy(tags, srcs)) {
                    boolean temp = false;
                    for (int i = 0; i < tags.size(); i++) {
                        String tagName = tags.get(i).getName();
                        if (!srcNameSet.contains(tagName)) {
                            Instr move = new Instr.Move(tags.get(i).getType(), tags.get(i), srcs.get(i), bb);
                            moves.add(move);

                            tagNameSet.remove(tags.get(i).getName());
                            srcNameSet.remove(srcs.get(i).getName());

                            tags.remove(i);
                            srcs.remove(i);

                            temp = true;
                            break;
                        }
                    }
                    //temp = true 表示存在a,b 满足b <- a  且b没有被使用过,且已经处理过
                    //否则需要执行操作
                    if (!temp) {
                        for (int i = 0; i < tags.size(); i++) {
                            String srcName = srcs.get(i).getName();
                            Value src = srcs.get(i);
                            Value tag = tags.get(i);
                            if (!srcs.get(i).getName().equals(tags.get(i).getName())) {
                                GlobalVal.VirtualValue newSrc = new GlobalVal.VirtualValue(tag.getType());
                                Instr move = new Instr.Move(tag.getType(), newSrc, src, bb);
                                moves.add(move);
                                srcs.set(i, newSrc);

                                srcNameSet.remove(srcName);
                                srcNameSet.add(move.getName());
                            }
                        }
                    }

                }
                instr = (Instr) instr.getNext();
            }
            for (Instr instr1: PCopys) {
                instr1.remove();
            }
            for (Instr instr1: moves) {
                bb.getEndInstr().insertBefore(instr1);
            }

            bb = (BasicBlock) bb.getNext();
        }
    }

    private boolean checkPCopy(ArrayList<Value> tag, ArrayList<Value> src) {
        for (int i = 0; i < tag.size(); i++) {
            if (!tag.get(i).getName().equals(src.get(i).getName())) {
                return false;
            }
        }
        return true;
    }

    private void removeUndefCopy(ArrayList<Value> tag, ArrayList<Value> src,
                                 HashSet<String> tagNames, HashSet<String> srcNames) {
        ArrayList<Value> tempTag = new ArrayList<>();
        ArrayList<Value> tempSrc = new ArrayList<>();
        for (int i = 0; i < tag.size(); i++) {
            if (src.get(i) instanceof GlobalVal.UndefValue) {
                continue;
            }
            tempTag.add(tag.get(i));
            tempSrc.add(src.get(i));
        }
        tag.clear();
        src.clear();
        tag.addAll(tempTag);
        src.addAll(tempSrc);
        for (Value value: tag) {
            tagNames.add(value.getName());
        }
        for (Value value: src) {
            srcNames.add(value.getName());
        }

    }
}
