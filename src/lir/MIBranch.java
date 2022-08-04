package lir;

import java.io.PrintStream;

public class MIBranch extends MachineInst {
    public Arm.Cond getCond() {
        return cond;
    }

    public MC.Block getTrueTargetBlock() {
        return trueTargetBlock;
    }

    public MC.Block getFalseTargetBlock() {
        return falseTargetBlock;
    }

    Arm.Cond cond;
    MC.Block trueTargetBlock;
    // 条件不满足则跳这个块
    MC.Block falseTargetBlock;

    public MIBranch(Arm.Cond cond, MC.Block trueBlock, MC.Block falseBlock, MC.Block insertAtEnd) {
        super(Tag.Branch, insertAtEnd);
        this.cond = cond;
        trueTargetBlock = trueBlock;
        falseTargetBlock = falseBlock;
    }

    @Override
    public void output(PrintStream os, MC.McFunction f) {
        os.println(tag.toString() + cond + "\t" + trueTargetBlock.getLabel());
        os.println(tag.toString() + "\t" + falseTargetBlock.getLabel());
    }

    @Override
    public String toString() {
        return tag.toString() + cond + "\t" + trueTargetBlock.getLabel() + "\n" + tag.toString() + "\t" + falseTargetBlock.toString();
    }
}
