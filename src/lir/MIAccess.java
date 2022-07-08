package lir;

import javax.crypto.Mac;

public class MIAccess extends MachineInst {
    public enum Type {
        OFFSET,
        PREFIX,
        POSTFIX
    }

    ;
    Type type;
    Machine.Operand addr;
    Machine.Operand offset;
    int shift;
    Arm.Cond cond;

    public MIAccess(Tag tag, Machine.Block insertAtEnd) {
        super(tag, insertAtEnd);
        cond = Arm.Cond.Any;
    }

    public MIAccess(Tag tag, MachineInst inst, boolean flag) {
        super(tag, inst, flag);
        cond = Arm.Cond.Any;
    }

    public MIAccess(Tag tag, boolean flag) {
        super(tag, flag);
        cond = Arm.Cond.Any;
    }
}
