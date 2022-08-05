package lir;

import javax.crypto.Mac;
import java.io.PrintStream;

public class MIStore extends MachineInst {
    // public int getShift() {
    //     return shift;
    // }

    // public Machine.Operand data;
    // public Machine.Operand addr;
    // private int shift = 0;

    @Override
    public Arm.Cond getCond() {
        return cond;
    }

    // private Arm.Cond cond = Arm.Cond.Any;


    public MIStore(Machine.Operand data, Machine.Operand addr, Machine.Operand offset, Machine.Block insertAtEnd) {
        super(Tag.Store, insertAtEnd);
        useOpds.add(data);
        useOpds.add(addr);
        useOpds.add(offset);
    }

    /**
     * 寄存器分配时专用
     *
     * @param insertAfter
     * @param data
     * @param addr
     */
    public MIStore(MachineInst insertAfter, Machine.Operand data, Machine.Operand addr, Machine.Operand offset) {
        super(insertAfter, Tag.Store);
        useOpds.add(data);
        useOpds.add(addr);
        useOpds.add(offset);
    }

    public Machine.Operand getData() {
        return useOpds.get(0);
    }

    public Machine.Operand getAddr() {
        return useOpds.get(1);
    }

    public Machine.Operand getOffset() {
        return useOpds.get(2);
    }

    public Machine.Operand setData(Machine.Operand data){
        return useOpds.set(0,data);
    }

    public Machine.Operand setAddr(Machine.Operand addr){
        return useOpds.set(1,addr);
    }

    public Machine.Operand setOffset(Machine.Operand offset){
        return useOpds.set(2,offset);
    }
    @Override
    public void output(PrintStream os, Machine.McFunction f) {
        transfer_output(os);
        if (!isFloat) {
            if (this.shift.shiftType == Arm.ShiftType.None) {
                os.println("\tstr" + cond + "\t" + getData().toString() + ",\t[" + getAddr().toString() + ",\t" + getOffset().toString() + "]");
            } else {
                os.println("\tstr" + cond + "\t" + getData().toString() + ",\t[" + getAddr().toString() + ",\t" + getOffset().toString() + ",LSL #" + this.shift.shift + "]");
            }
        } else {
            if (getOffset().getType() == Machine.Operand.Type.Immediate) {
                int shift = (this.shift.shiftType == Arm.ShiftType.None) ? 0 : this.shift.shift;
                int offset = this.getOffset().value << shift;
                if (offset != 0) {
                    os.println("\tvstr" + cond + ".32" + "\t" + getData().toString() + ",\t[" + getAddr().toString() + ",\t#" + offset + "]");
                } else {
                    os.println("\tvstr" + cond + ".32" + "\t" + getData().toString() + ",\t[" + getAddr().toString() + "]");
                }
            } else {
                if (this.shift.shiftType == Arm.ShiftType.None) {
                    os.println("\tvstr" + cond + ".32" + "\t" + getData().toString() + ",\t[" + getAddr().toString() + ",\t" + getOffset().toString() + "]");
                } else {
                    os.println("\tvstr" + cond + ".32" + "\t" + getData().toString() + ",\t[" + getAddr().toString() + ",\t" + getOffset().toString() + ",LSL #" + this.shift.shift + "]");
                }
            }
        }
    }


    @Override
    public String toString() {
        return tag.toString() + cond + '\t' + getData() + ",\t[" + getAddr() + ",\t" + getOffset() +
                (shift.shiftType == Arm.ShiftType.None ? "" : ("\t," + shift)) + "]";
    }
}
