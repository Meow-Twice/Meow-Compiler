package lir;

public class MILoad extends MIAccess{
    Machine.Operand dOpd;
    Machine.Operand addr;

    public MILoad(Machine.Block insertAtEnd){
        super(Tag.Load,insertAtEnd);
    }
    public MILoad(MachineInst inst){
        super(Tag.Load,inst);
    }


    @Override
    public void genDefUse() {
        defOpds.add(dOpd);
        useOpds.add(addr);
    }
}
