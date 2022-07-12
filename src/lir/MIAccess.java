package lir;

import javax.crypto.Mac;

public class MIAccess extends MachineInst {
    // public enum Type {
    //     OFFSET,
    //     PREFIX,
    //     POSTFIX
    // }
    //
    // ;
    // public Type type;
    public Machine.Operand offset;
    public int shift;
    public Arm.Cond cond;

    public MIAccess(Tag tag, Machine.Block insertAtEnd, boolean isFloat) {
        super(tag, insertAtEnd, isFloat);
        cond = Arm.Cond.Any;
    }

    public MIAccess(Tag tag, Machine.Block insertAtEnd) {
        super(tag, insertAtEnd);
        isFloat = false;
        cond = Arm.Cond.Any;
    }

    public MIAccess(Tag tag, MachineInst inst, boolean isFloat) {
        super(tag, inst, isFloat);
        cond = Arm.Cond.Any;
    }

    public MIAccess(Tag tag, MachineInst inst) {
        super(tag, inst);
        cond = Arm.Cond.Any;
    }
    public MIAccess(MachineInst inst, Tag tag) {
        super(inst, tag);
        cond = Arm.Cond.Any;
    }

    public MIAccess(Tag tag, boolean isFloat) {
        super(tag, isFloat);
        cond = Arm.Cond.Any;
    }
}
