package lir;

public class MIReturn extends MachineInst{
    public MIReturn(Machine.Block insertAtEnd){
        super(Tag.Return,insertAtEnd);
    }
}
