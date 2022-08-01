package lir;

import java.io.PrintStream;
import java.util.stream.Stream;

public class MIBranch extends MachineInst {
    public Arm.Cond getCond() {
        return cond;
    }

    public Machine.Block getTrueTargetBlock() {
        return trueTargetBlock;
    }

    public Machine.Block getFalseTargetBlock() {
        return falseTargetBlock;
    }

    Arm.Cond cond;
    Machine.Block trueTargetBlock;
    // 条件不满足则跳这个块
    Machine.Block falseTargetBlock;

    public MIBranch(Arm.Cond cond, Machine.Block trueBlock, Machine.Block falseBlock, Machine.Block insertAtEnd) {
        super(Tag.Branch, insertAtEnd);
        this.cond = cond;
        trueTargetBlock = trueBlock;
        falseTargetBlock = falseBlock;
    }

    @Override
    public void output(PrintStream os, Machine.McFunction f) {
        os.println(tag.toString() + cond + "\t" + trueTargetBlock.toString());
        os.println(tag.toString() + "\t" + falseTargetBlock.toString());
    }

    @Override
    public String toString() {
        return tag.toString() + cond + "\t" + trueTargetBlock.getDebugLabel() + "\n" + tag.toString() + "\t" + falseTargetBlock.toString();
    }
}
