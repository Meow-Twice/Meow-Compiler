package lir;

import java.io.PrintStream;

public class MIComment extends MachineInst{
    String content;
    public MIComment(String content,Machine.Block insertAtEnd,boolean isFloat){
        super(Tag.Comment,insertAtEnd,isFloat);
        this.content = content;
    }

    public MIComment(String content,MachineInst inst){
        super(Tag.Comment,inst, inst.isFloat);
        this.content = content;
    }

    public void output(PrintStream os){
        os.println("@ "+content);
    }
}
