package lir;

import java.io.PrintStream;

public class MIComment extends MachineInst {
    String content;

    public MIComment(String content, Machine.Block insertAtEnd) {
        super(Tag.Comment, insertAtEnd);
        this.content = content;
    }

    public MIComment(String content, MachineInst inst) {
        super(Tag.Comment, inst, false);
        this.content = content;
    }

    @Override
    public void output(PrintStream os, Machine.McFunction f) {
        os.println("@ " + content);
    }

    @Override
    public String toString() {
        return "@" + content;
    }
}
