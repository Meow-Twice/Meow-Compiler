package lir;

public class MIComment extends MachineInst{
    String content;
    public MIComment(String content,Machine.Block insertAtEnd){
        super(Tag.Comment,insertAtEnd);
        this.content = content;
    }

    public MIComment(String content,MachineInst inst){
        super(Tag.Comment,inst);
        this.content = content;
    }

}
