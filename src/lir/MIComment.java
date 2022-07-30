package lir;

import java.io.PrintStream;

public class MIComment extends MachineInst {
    String content;

    public MIComment(String content, Machine.Block insertAtEnd) {
        super(Tag.Comment, insertAtEnd);
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
