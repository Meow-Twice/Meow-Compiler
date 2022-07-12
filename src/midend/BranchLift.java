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
        for (BasicBlock head: function.getLoopHeads()) {
            Loop loop = head.getLoop();
            HashMap<Integer, HashSet<Instr>> conds = loop.getConds();
            for (int key: conds.keySet()) {
                if (conds.get(key).size() == 1) {
                    Instr br = null;
                    for (Instr temp: conds.get(key)) {
                        br = temp;
                    }
                    assert br instanceof Instr.Branch;
                    liftBrOutLoop((Instr.Branch) br, loop);
                }
            }
        }
    }

    private void liftBrOutLoop(Instr.Branch thenBr, Loop thenLoop) {
        Function function = thenLoop.getHeader().getFunction();
        BasicBlock thenHead = thenLoop.getHeader();
        thenLoop.cloneToFunc(function);
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
        }

        //Clone br
        //transBB --> thenHead & elseHead
        Instr brInTransBB = thenBr.cloneToBB(transBB);
        brInTransBB.modifyUse(thenHead, 1);
        brInTransBB.modifyUse(elseHead, 2);

        //修改thenLoop和elseLoop中的br为无条件转跳
        //thenBr.modifyUse(new Constant.ConstantInt(1), 0);
        Instr.Jump thenJump = new Instr.Jump((BasicBlock) thenBr.getUseValueList().get(1), thenBr.parentBB());
        thenJump.insertBefore(thenBr);

        Instr.Branch elseBr = (Instr.Branch) CloneInfoMap.getReflectedValue(thenBr);
        //elseBr.modifyUse(new Constant.ConstantInt(0), 0);
        Instr.Jump elseJump = new Instr.Jump((BasicBlock) elseBr.getUseValueList().get(2), elseBr.parentBB());
        elseJump.insertBefore(elseBr);

        thenBr.remove();
        elseBr.remove();
    }
}
