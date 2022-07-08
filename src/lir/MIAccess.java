package lir;

import javax.crypto.Mac;

public class MIAccess extends MachineInst{
    public enum  Type{
        OFFSET,
        PREFIX,
        POSTFIX
    };
    Type type;
    Machine.Operand addr;
    Machine.Operand offset;
    int shift;
    Arm.Cond cond;
    public MIAccess(Tag tag, Machine.Block insertAtEnd,boolean isFloat){
        super(tag,insertAtEnd,isFloat);
        cond = Arm.Cond.Any;
    }

    public MIAccess(Tag tag, MachineInst inst,boolean isFloat){
        super(tag,inst,isFloat);
        cond = Arm.Cond.Any;
    }

    public MIAccess(Tag tag,boolean isFloat){
        super(tag,isFloat);
        cond = Arm.Cond.Any;
    }
}
