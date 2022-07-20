package lir;

import java.io.PrintStream;

public class MILoad extends MachineInst {
    // public int getShift() {
    //     return shift;
    // }

    // public Machine.Operand data;
    // public Machine.Operand addr;
    // public Machine.Operand offset;
    // private int shift;
    // private Arm.Cond cond;

    // @Override
    // public Arm.Cond getCond() {
    //     return cond;
    // }

    public MILoad(Machine.Operand data, Machine.Operand addr, Machine.Operand offset, Machine.Block insertAtEnd) {
        super(MachineInst.Tag.Load, insertAtEnd);
        defOpds.add(data);
        useOpds.add(addr);
        useOpds.add(offset);
    }

    public MILoad(Machine.Operand data, Machine.Operand addr, Machine.Operand offset, MachineInst insertBefore) {
        super(MachineInst.Tag.Load, insertBefore);
        defOpds.add(data);
        useOpds.add(addr);
        useOpds.add(offset);
    }
    // public MILoad(Machine.Operand data, Machine.Operand addr, MachineInst inst) {
    //     super(Tag.Load, inst);
    //     // this.data = data;
    //     // this.addr = addr;
    // }

    public Machine.Operand getData() {
        return defOpds.get(0);
    }

    public Machine.Operand getAddr() {
        return useOpds.get(0);
    }

    public Machine.Operand getOffset() {
        return useOpds.get(1);
    }

    @Override
    public void output(PrintStream os, Machine.McFunction f) {
        transfer_output(os);
        if(!isFloat) {
            if (getOffset().getType() == Machine.Operand.Type.Immediate) {
                int offset = this.getOffset().value << this.shift.shift;
                os.println("\tldr" + cond + "\t" + getData().toString() + ",[" + getAddr().toString() + ",#" + offset + "]");
            } else {
                os.println("\tldr" + cond + "\t" + getData().toString() + ",[" + getAddr().toString() + "," + getOffset().toString() + ",LSL #" + shift + "]");
            }
        }
        else{
            if (getOffset().getType() == Machine.Operand.Type.Immediate) {
                int offset = this.getOffset().value << this.shift.shift;
                os.println("\tvldr" + cond +".32"+ "\t" + getData().toString() + ",[" + getAddr().toString() + ",#" + offset + "]");
            } else {
                os.println("\tvldr" + cond +".32"+ "\t" + getData().toString() + ",[" + getAddr().toString() + "," + getOffset().toString() + ",LSL #" + shift + "]");
            }
        }
    }

    @Override
    public String toString() {
        return tag.toString() + cond.toString() + '\t' + getData() + ",\t[" + getAddr() + ",\t" + (this.isNeedFix() ? getOffset().value + this.mb.mcFunc.getTotalStackSize() : getOffset()) +
                (shift.shiftType == Arm.ShiftType.None ? "" : ("\t," + shift)) + "\t]";
    }
}
