package lir;

public class MILoad extends MIAccess{
    Machine.Operand dOpd;

    public MILoad(Machine.Block insertAtEnd){
        super(Tag.Load,insertAtEnd);
    }
    public MILoad(MachineInst inst){
        super(Tag.Load,inst);
    }
}
