package lir;

public class MILongMul extends MachineInst{

    Machine.Operand dOpd;
    Machine.Operand lOpd;
    Machine.Operand rOpd;
    public MILongMul(Machine.Block insertAtEnd){
        super(Tag.LongMul,insertAtEnd);
    }

    public MILongMul(Machine.Operand dOpd, Machine.Operand lOpd, Machine.Operand rOpd, Machine.Block insertAtEnd) {
        super(Tag.LongMul, insertAtEnd);
        this.dOpd = dOpd;
        this.rOpd = rOpd;
        this.lOpd = lOpd;
        genDefUse();
    }

    @Override
    public void genDefUse() {
        defOpds.add(dOpd);
        useOpds.add(lOpd);
        useOpds.add(rOpd);
    }
}
