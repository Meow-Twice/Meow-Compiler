package lir;

import java.io.PrintStream;

public class MILoad extends MIAccess{
    Machine.Operand dOpd;
    Machine.Operand addr;

    public MILoad(Machine.Block insertAtEnd) {
        super(Tag.Load, insertAtEnd);
    }

    public MILoad(Machine.Operand dOpd, Machine.Operand addr, Machine.Block insertAtEnd) {
        super(Tag.Load, insertAtEnd);
        this.dOpd = dOpd;
        this.addr = addr;
        genDefUse();
    }

    public MILoad(MachineInst inst, boolean isFloat) {
        super(Tag.Load, inst, isFloat);
    }

    @Override
    public void genDefUse() {
        defOpds.add(dOpd);
        useOpds.add(addr);
    }
    @Override
    public void output(PrintStream os, Machine.McFunction f){
        transfer_output(os);
        if(offset.getType() == Machine.Operand.Type.Immediate){
            // 这一行是啥情况, vrId并不是栈偏移地址
            // int offset = this.offset.id<<shift;
            os.println("ldr"+cond+"\t"+dOpd.toString()+",["+addr.toString()+",#"+offset+"]");
        }
        else{
            os.println("ldr"+cond+"\t"+dOpd.toString()+",["+addr.toString()+","+offset.toString()+",LSL #"+shift+"]");
        }
    }
}
