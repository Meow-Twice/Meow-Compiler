package lir;

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

    public Machine.Operand getData(){
        return useOpds.get(0);
    }

    public Machine.Operand getAddr(){
        return useOpds.get(1);
    }

    public Machine.Operand getOffset(){
        return useOpds.get(2);
    }
    @Override
    public void output(PrintStream os, Machine.McFunction f) {
        transfer_output(os);
        if(!isFloat) {
            if (getOffset().getType() == Machine.Operand.Type.Immediate) {
                int offset = this.getOffset().value << getShift().shift;
                os.println("str" + cond + "\t" + getData().toString() + ",[" + getAddr().toString() + ",#" + offset + "]");
            } else {
                os.println("str" + cond + "\t" + getData().toString() + ",[" + getAddr().toString() + "," + getOffset().toString() + ",LSL #" + shift + "]");
            }
        }
        else{
            if (getOffset().getType() == Machine.Operand.Type.Immediate) {
                int offset = this.getOffset().value << getShift().shift;
                os.println("vstr" + cond +".32"+ "\t" + getData().toString() + ",[" + getAddr().toString() + ",#" + offset + "]");
            } else {
                os.println("vstr" + cond +".32"+ "\t" + getData().toString() + ",[" + getAddr().toString() + "," + getOffset().toString() + ",LSL #" + shift + "]");
            }
        }
    }

    @Override
    public String toString() {
        return tag.toString() + cond + '\t' + getData() + ",\t[" + getAddr() + ",\t" + getOffset() +
                (shift.shiftType == Arm.ShiftType.None ? "" : ("\t," + shift)) + "]";
    }
}
