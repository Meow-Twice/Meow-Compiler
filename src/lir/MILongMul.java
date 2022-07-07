package lir;

public class MILongMul extends MachineInst{

    Machine.Operand dOpd;
    Machine.Operand lOpd;
    Machine.Operand rOpd;
    public MILongMul(Machine.Block insertAtEnd){
        super(Tag.LongMul,insertAtEnd);
    }

    @Override
    public void genDefUse() {
        defOpds.add(dOpd);
        useOpds.add(lOpd);
        useOpds.add(rOpd);
    }
}
