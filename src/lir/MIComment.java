package lir;

import java.io.PrintStream;

public class MIComment extends MachineInst{
    String content;

    public MIComment(String content, Machine.Block insertAtEnd) {
        super(Tag.Comment, insertAtEnd);
        this.content = content;
    }

    public MIComment(String content, MachineInst inst) {
        super(Tag.Comment, inst, false);
        this.content = content;
    }

    public void output(PrintStream os){
        os.println("@ "+content);
    }
}
