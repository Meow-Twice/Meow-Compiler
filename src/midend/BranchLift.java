package midend;

import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class BranchLift {

    private ArrayList<Function> functions;

    public BranchLift(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        branchLift();
    }

    private void branchLift() {
        for (Function function: functions) {
            branchLiftForFunc(function);
        }
    }

    private void branchLiftForFunc(Function function) {
        //TODO:一次只能提升一个循环的一个Branch
        for (BasicBlock head: function.getLoopHeads()) {
            Loop loop = head.getLoop();
            HashMap<Integer, HashSet<Instr>> conds = loop.getConds();
            for (int key: conds.keySet()) {
                if (conds.get(key).size() == 1) {
                    Instr br = null;
                    for (Instr temp: conds.get(key)) {
                        br = temp;
                    }
                    if (!(br instanceof Instr.Branch)) {
                        continue;
                    }
//                    if (loop.getChildrenLoops().size() != 0) {
//                        continue;
//                    }
                    liftBrOutLoop((Instr.Branch) br, loop);
                    //TODO:只提升一个branch就直接return
                    return;
                }
            }
        }
    }

    private void liftBrOutLoop(Instr.Branch thenBr, Loop thenLoop) {
        CloneInfoMap.clear();
        Function function = thenLoop.getHeader().getFunction();
        BasicBlock thenHead = thenLoop.getHeader();
        thenLoop.cloneToFunc(function);
//        for (BasicBlock bb: thenLoop.getNowLevelBB()) {
//            System.err.println(bb.getLabel() + " to " + ((BasicBlock) CloneInfoMap.getReflectedValue(bb)).getLabel());
//        }

        thenLoop.fix();
        Loop elseLoop = CloneInfoMap.getReflectedLoop(thenLoop);
        BasicBlock elseHead = elseLoop.getHeader();

        BasicBlock transBB = new BasicBlock(function, thenLoop.getParentLoop());
        HashSet<BasicBlock> enterings = thenLoop.getEnterings();

        //entering --> transBB
        for (BasicBlock entering: enterings) {
            Instr instr = entering.getEndInstr();
            if (instr instanceof Instr.Jump) {
                instr.modifyUse(transBB, 0);
            } else if (instr instanceof Instr.Branch){
                ArrayList<Value> values = instr.getUseValueList();
                if (values.size() == 1) {
                    instr.modifyUse(transBB, 0);
                } else {
                    int index = values.indexOf(thenHead);
                    instr.modifyUse(transBB, index);
                }
            } else {
                System.err.println("error");
            }
            entering.modifySuc(thenHead, transBB);
            transBB.addPre(entering);
            thenHead.modifyPre(entering, transBB);
            elseHead.modifyPre(entering, transBB);

            //transBB.addPre(entering);
        }

        //Clone br
        // entering --> transBB
        // transBB --> thenHead & elseHead
        transBB.addSuc(thenHead);
        transBB.addSuc(elseHead);


        Instr.Branch elseBr = (Instr.Branch) CloneInfoMap.getReflectedValue(thenBr);
        Instr brInTransBB = thenBr.cloneToBB(transBB);
        if (transBB.getLoopDep() > 0) {
            brInTransBB.setInLoopCond();
            brInTransBB.setCondCount(thenBr.getCondCount());
        }

        brInTransBB.modifyUse(thenHead, 1);
        brInTransBB.modifyUse(elseHead, 2);

        //修改thenLoop和elseLoop中的br为无条件转跳
        //thenBr.modifyUse(new Constant.ConstantInt(1), 0);
        Instr.Jump thenJump = new Instr.Jump((BasicBlock) thenBr.getUseValueList().get(1), thenBr.parentBB());
        thenBr.insertBefore(thenJump);


        //elseBr.modifyUse(new Constant.ConstantInt(0), 0);
        Instr.Jump elseJump = new Instr.Jump((BasicBlock) elseBr.getUseValueList().get(2), elseBr.parentBB());
        elseBr.insertBefore(elseJump);

        thenBr.remove();
        elseBr.remove();


        //修改exiting的数据流
        //
        //修改exits的(冗余)phi
        HashSet<BasicBlock> exits = thenLoop.getExits();
        for (BasicBlock bb: exits) {
            ArrayList<BasicBlock> addBBs = new ArrayList<>();
            for (BasicBlock pre: bb.getPrecBBs()) {
                if (CloneInfoMap.valueMap.containsKey(pre)) {
                    addBBs.add((BasicBlock) CloneInfoMap.getReflectedValue(pre));
                }
            }
            for (BasicBlock add: addBBs) {
                bb.addPre(add);
            }

            Instr instr = bb.getBeginInstr();
            while (instr.getNext() != null) {
                if (instr instanceof Instr.Phi) {
                    ArrayList<Value> adds = new ArrayList<>();
                    for (Value used: instr.getUseValueList()) {
                        int index = instr.getUseValueList().indexOf(used);
                        if (CloneInfoMap.valueMap.containsKey(bb.getPrecBBs().get(index))) {
                            adds.add(CloneInfoMap.getReflectedValue(used));
                        }
//                        if (CloneInfoMap.valueMap.containsKey(used)) {
//                            adds.add(CloneInfoMap.getReflectedValue(used));
//                        }

                        //adds.add(CloneInfoMap.getReflectedValue(used));
                    }
                    for (Value add: adds) {
                        ((Instr.Phi) instr).addOptionalValue(add);
                    }
                    assert instr.parentBB().getPrecBBs().size() == instr.getUseValueList().size();
                }
                instr = (Instr) instr.getNext();
            }
        }
    }
}
