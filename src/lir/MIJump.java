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
    public MIJump(Arm.Cond cond,Machine.Block target, Machine.Block insertAtEnd){
        super(Tag.Jump,insertAtEnd);
        this.setCond(cond);
        this.target = target;
    }

    @Override
    public void output(PrintStream os, Machine.McFunction f) {
        transfer_output(os);
        os.println("\tb" + cond + "\t" + target.getDebugLabel());
    }

    @Override
    public String toString() {
        return tag + "\t" + target.getDebugLabel();
    }
}
