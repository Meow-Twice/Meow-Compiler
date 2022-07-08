package lir;

public class MIJump extends MachineInst{
    Machine.Block target;
    public MIJump(Machine.Block target,Machine.Block insertAtEnd){
        super(Tag.Jump,insertAtEnd);
        this.target = target;
    }
}
