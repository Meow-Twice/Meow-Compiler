package lir;

public class MIJump extends MachineInst{
    Machine.Block target;
    public MIJump(Machine.Block target,Machine.Block insertAAtEnd){
        super(Tag.Jump,insertAAtEnd);
        this.target = target;
    }
}
