package midend;

import manage.Manager;
import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class BranchOptimize {

    private ArrayList<Function> functions;

    public BranchOptimize(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        RemoveUselessPHI();
        RemoveUselessJump();
        remakeCFG();
        ModifyConstBranch();
        remakeCFG();
        //fixme:check貌似是错的
        //RemoveBBOnlyJump();
    }

    private void remakeCFG() {
        MakeDFG makeDFG = new MakeDFG(functions);
        makeDFG.Run();
    }

    //删除只有一个use的PHI(冗余PHI)
    private void RemoveUselessPHI() {
        for (Function function: functions) {
            removeUselessPHIForFunc(function);
        }
    }

    private void RemoveUselessJump() {
        for (Function function: functions) {
            removeUselessJumpForFunc(function);
        }
    }

    //branch条件为恒定值的时候,变为JUMP
    private void ModifyConstBranch() {
        for (Function function: functions) {
            modifyConstBranchForFunc(function);
        }
    }

    private void RemoveBBOnlyJump() {
        for (Function function: functions) {
            removeBBOnlyJumpForFunc(function);
        }
    }

    private void removeUselessPHIForFunc(Function function) {
        BasicBlock bb = function.getBeginBB();
        while (bb.getNext() != null) {
            Instr instr = bb.getBeginInstr();
            while (instr.getNext() != null) {
                if (!(instr instanceof Instr.Phi)) {
                    break;
                }
                if (instr.getUseValueList().size() == 1) {
                    Value value = instr.getUseValueList().get(0);
                    instr.modifyAllUseThisToUseA(value);
                    instr.remove();
                }

                instr = (Instr) instr.getNext();
            }

            bb = (BasicBlock) bb.getNext();
        }
    }

    private void removeUselessJumpForFunc(Function function) {
        HashSet<BasicBlock> bbCanRemove = new HashSet<>();
        BasicBlock bb = function.getBeginBB();
        while (bb.getNext() != null) {
            if (bb.getPrecBBs().size() == 1) {
                bbCanRemove.add(bb);
            }
            bb = (BasicBlock) bb.getNext();
        }
        for (BasicBlock mid: bbCanRemove) {
            // bbs -> pre -> mid -> bbs
            // bbs -> pre -> bbs
            // move all instr in mid to pre
            BasicBlock pre = mid.getPrecBBs().get(0);
            if (pre.getSuccBBs().size() != 1) {
                continue;
            }
            pre.getEndInstr().remove();
            ArrayList<Instr> instrInMid = new ArrayList<>();
            for (Instr instr = mid.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                instrInMid.add(instr);
            }
            for (Instr instr: instrInMid) {
                instr.setBb(pre);
                pre.insertAtEnd(instr);
            }
            pre.setSuccBBs(mid.getSuccBBs());
            for (BasicBlock temp: mid.getSuccBBs()) {
                temp.modifyPre(mid, pre);
            }
            mid.remove();
        }
    }

    private void modifyConstBranchForFunc(Function function) {
        HashMap<Instr.Branch, Boolean> modifyBrMap = new HashMap<>();
        for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
            for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                if (instr instanceof Instr.Branch) {
                    Value cond = ((Instr.Branch) instr).getCond();
                    if (!(cond instanceof Instr)) {
                        continue;
                    }
                    Value lValue = ((Instr) cond).getUseValueList().get(0);
                    Value rValue = ((Instr) cond).getUseValueList().get(1);
                    if (lValue instanceof Constant && rValue instanceof Constant) {
                        boolean tag = true;
                        if (cond instanceof Instr.Icmp) {
                            int lInt = (int) ((Constant) lValue).getConstVal();
                            int rInt = (int) ((Constant) rValue).getConstVal();
                            switch (((Instr.Icmp) cond).getOp()) {
                                case SLT -> tag = lInt < rInt;
                                case SLE -> tag = lInt <= rInt;
                                case SGT -> tag = lInt > rInt;
                                case SGE -> tag = lInt >= rInt;
                                case NE -> tag = lInt != rInt;
                                case EQ -> tag = lInt == rInt;
                            }
                        } else if (cond instanceof Instr.Fcmp) {
                            float lFloat = (float) ((Constant) lValue).getConstVal();
                            float rFloat = (float) ((Constant) rValue).getConstVal();
                            switch (((Instr.Fcmp) cond).getOp()) {
                                case OLT -> tag = lFloat < rFloat;
                                case OLE -> tag = lFloat <= rFloat;
                                case OGT -> tag = lFloat > rFloat;
                                case OGE -> tag = lFloat >= rFloat;
                                case ONE -> tag = lFloat != rFloat;
                                case OEQ -> tag = lFloat == rFloat;
                            }
                        } else {
                            assert false;
                        }
                        modifyBrMap.put((Instr.Branch) instr, tag);
                    }

                }
            }
        }

        for (Instr.Branch br: modifyBrMap.keySet()) {
            BasicBlock tagBB = null;
            BasicBlock parentBB = br.parentBB();
            if (modifyBrMap.get(br)) {
                tagBB = br.getThenTarget();
            } else {
                tagBB = br.getElseTarget();
            }
            //br.remove();
            Instr.Jump jump = new Instr.Jump(tagBB, parentBB);
            br.insertBefore(jump);
            br.getCond().remove();
            br.remove();

        }
    }

    private void removeBBOnlyJumpForFunc(Function function) {
        HashSet<BasicBlock> removes = new HashSet<>();
        for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
            if (function.entry.equals(bb)) {
                continue;
            }
            if (bb.getBeginInstr().equals(bb.getEndInstr())) {
                Instr instr = bb.getBeginInstr();
                if (instr instanceof Instr.Jump) {
                    //TODO:
                    Value next = ((Instr.Jump) instr).getTarget();
                    BasicBlock target = ((Instr.Jump) instr).getTarget();
                    bb.modifyAllUseThisToUseA(next);
                    for (BasicBlock pre: bb.getPrecBBs()) {
                        pre.modifySuc(bb, target);
                        target.modifyPre(bb, pre);
                    }
                    removes.add(bb);
                }
            }
        }
        for (BasicBlock bb: removes) {
            bb.remove();
            for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                instr.remove();
            }
        }
    }
}
