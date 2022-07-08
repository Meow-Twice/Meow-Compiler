package lir;

import java.io.PrintStream;

public class MIBranch extends MachineInst {
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

    public void output(PrintStream os) {
        transfer_output(os);
        os.println("b\t" + cond + "\t" + trueTargetBlock.index);
    }
}
