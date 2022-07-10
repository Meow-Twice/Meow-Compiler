package lir;

import java.io.PrintStream;

public class MILoad extends MIAccess{
    public Machine.Operand data;
    public Machine.Operand addr;

    public MILoad(Machine.Block insertAtEnd) {
        super(Tag.Load, insertAtEnd);
    }

    public MILoad(Machine.Operand data, Machine.Operand addr, Machine.Operand offset, Machine.Block insertAtEnd) {
        super(Tag.Load, insertAtEnd);
        this.data = data;
        this.addr = addr;
        this.offset = offset;
        genDefUse();
    }

    public MILoad(MachineInst inst, boolean isFloat) {
        super(Tag.Load, inst, isFloat);
    }

    @Override
    public void genDefUse() {
        defOpds.add(data);
        useOpds.add(addr);
        useOpds.add(offset);
    }
    @Override
    public void output(PrintStream os, Machine.McFunction f){
        transfer_output(os);
        if(offset.getType() == Machine.Operand.Type.Immediate){
            int offset = this.offset.value<<shift;
            os.println("ldr"+cond+"\t"+ data.toString()+",["+addr.toString()+",#"+offset+"]");
        }
        else{
            os.println("ldr"+cond+"\t"+ data.toString()+",["+addr.toString()+","+offset.toString()+",LSL #"+shift+"]");
        }
    }
}
