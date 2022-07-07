package lir;

import javax.crypto.Mac;

public class MIFma extends MachineInst{
    Machine.Operand acc;
    Machine.Operand dst;
    boolean add;
    boolean sign;
    Arm.Cond cond;
    public MIFma(boolean add,boolean sign,Machine.Block insertAtEnd){
        super(Tag.FMA,insertAtEnd);
        this.add = add;
        this.sign = sign;

    }

}
