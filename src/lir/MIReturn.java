package lir;

public class MIReturn extends MachineInst{
    Machine.Operand ret;
    public MIReturn(Machine.Block insertAtEnd){
        super(Tag.Return,insertAtEnd);
    }
    @Override
    public void genDefUse() {
        useOpds.add(ret);
    }
}
