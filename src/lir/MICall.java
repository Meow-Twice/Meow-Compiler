package lir;

public class MICall extends MachineInst{
    Machine.Function function;
    public MICall(Machine.Block insertAtEnd){
        super(Tag.Call,insertAtEnd);
    }

    @Override
    public void genDefUse() {
        super.genDefUse();
    }
}
