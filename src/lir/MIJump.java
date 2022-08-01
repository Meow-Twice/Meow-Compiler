package lir;

import java.io.PrintStream;

public class MIJump extends MachineInst {
    public Machine.Block getTarget() {
        return target;
    }

    Machine.Block target;

    public MIJump(Machine.Block target, Machine.Block insertAtEnd) {
        super(Tag.Jump, insertAtEnd);
        this.target = target;
    }

    public MIJump(Arm.Cond cond, Machine.Block target, MachineInst before) {
        super(Tag.Jump, before);
        this.target = target;
        this.cond = cond;
    }
    @Override
    public void output(PrintStream os, Machine.McFunction f) {
        os.println("\t" + this);
    }

    @Override
    public String toString() {
        return tag.toString() + cond + "\t" + target.getDebugLabel();
    }
}
