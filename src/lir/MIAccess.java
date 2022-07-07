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
    public MIAccess(Tag tag, Machine.Block insertAtEnd){
        super(tag,insertAtEnd);
        cond = Arm.Cond.Any;
    }

    public MIAccess(Tag tag, MachineInst inst){
        super(tag,inst);
        cond = Arm.Cond.Any;
    }

    public MIAccess(Tag tag){
        super(tag);
        cond = Arm.Cond.Any;
    }
}
