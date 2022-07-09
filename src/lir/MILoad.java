package lir;

import java.io.PrintStream;

public class MILoad extends MIAccess{
    Machine.Operand dOpd;
    Machine.Operand addr;

    public MILoad(Machine.Block insertAtEnd) {
        super(Tag.Load, insertAtEnd);
    }

    public MILoad(Machine.Operand dOpd, Machine.Operand addr, Machine.Operand offset, Machine.Block insertAtEnd) {
        super(Tag.Load, insertAtEnd);
        this.dOpd = dOpd;
        this.addr = addr;
        this.offset = offset;
        genDefUse();
    }

    public MILoad(MachineInst inst, boolean isFloat) {
        super(Tag.Load, inst, isFloat);
    }

    @Override
    public void genDefUse() {
        defOpds.add(dOpd);
        useOpds.add(addr);
        useOpds.add(offset);
    }
    public void output(PrintStream os){
        transfer_output(os);
        if(offset.getType() == Machine.Operand.Type.Immediate){
            int offset = this.offset.value<<shift;
            os.println("ldr"+cond+"\t"+dOpd.toString()+",["+addr.toString()+",#"+offset+"]");
        }
        else{
            os.println("ldr"+cond+"\t"+dOpd.toString()+",["+addr.toString()+","+offset.toString()+",LSL #"+shift+"]");
        }
    }
}
