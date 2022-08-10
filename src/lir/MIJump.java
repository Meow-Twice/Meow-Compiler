package lir;

import java.io.PrintStream;

public class MIJump extends MachineInst {
    public MC.Block getTarget() {
        return target;
    }

    MC.Block target;

    public MIJump(MC.Block target, MC.Block insertAtEnd) {
        super(Tag.Jump, insertAtEnd);
        this.target = target;
    }

    public MIJump(Arm.Cond cond, MC.Block target, MachineInst before) {
        super(Tag.Jump, before);
        this.target = target;
        this.cond = cond;
    }

    public MIJump(Arm.Cond cond, MC.Block target, MC.Block insertAtEnd) {
        super(Tag.Jump, insertAtEnd);
        this.target = target;
        this.cond = cond;
    }

    @Override
    public void output(PrintStream os, MC.McFunction f) {
        os.println("\t" + this);
    }

    @Override
    public String toString() {
        return tag.toString() + cond + "\t" + target.getLabel();
    }
}
