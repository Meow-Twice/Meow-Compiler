package lir;

public class MILongMul extends MachineInst{
    Machine.Operand lOpd;
    Machine.Operand rOpd;

    public MILongMul(Machine.Block insertAtEnd){
        super(Tag.LongMul,insertAtEnd);
    }

}
