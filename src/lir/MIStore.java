package lir;

import java.io.PrintStream;

public class MIStore extends MachineInst {
    // public Machine.Operand data;
    // public Machine.Operand addr;
    public int shift = 0;
    public Arm.Cond cond = Arm.Cond.Any;


    public MIStore(Machine.Operand data, Machine.Operand addr, Machine.Operand offset, Machine.Block insertAtEnd) {
        super(Tag.Load, insertAtEnd);
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
        if (getOffset().getType() == Machine.Operand.Type.Immediate) {
            // TODO 这里没有检查立即数是否能被编码
            int offset = this.getOffset().value << shift;
            os.println("str" + cond + "\t" + getData().toString() + ",[" + getAddr().toString() + ",#" + offset + "]");
        } else {
            os.println("str" + cond + "\t" + getData().toString() + ",[" + getAddr().toString() + "," + getOffset().toString() + ",LSL #" + shift + "]");
        }
    }

    @Override
    public String toString() {
        return tag.toString() + cond + '\t' + getData() + ",[" + getAddr() + "," + getOffset() + "," + shift + "]";
    }
}
