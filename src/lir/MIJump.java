package lir;

import java.io.PrintStream;

public class MIJump extends MachineInst {
    Machine.Block target;

    public MIJump(Machine.Block target, Machine.Block insertAtEnd) {
        super(Tag.Jump, insertAtEnd);
        this.target = target;
    }

    @Override
    public void output(PrintStream os, Machine.McFunction f) {
        transfer_output(os);
        os.println("b\t" + mb.index);
    }

    @Override
    public String toString() {
        return tag + mb.bb.getLabel();
    }
}
