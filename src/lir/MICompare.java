package lir;

import javax.crypto.Mac;

public class MICompare extends MachineInst{
    Machine.Operand lOpd;
    Machine.Operand rOpd;
    public MICompare(Machine.Block insertAtEnd){
        super(Tag.Compare,insertAtEnd);
    }

    @Override
    public void genDefUse() {
        useOpds.add(lOpd);
        useOpds.add(rOpd);
    }
}
