package lir;

import java.io.PrintStream;

public class MILoad extends MIAccess{
    Machine.Operand dOpd;
    Machine.Operand addr;

    public MILoad(Machine.Block insertAtEnd,boolean isFloat){
        super(Tag.Load,insertAtEnd,isFloat);
    }
    public MILoad(MachineInst inst,boolean isFloat){
        super(Tag.Load,inst,isFloat);
    }

    @Override
    public void genDefUse() {
        defOpds.add(dOpd);
        useOpds.add(addr);
    }
    public void output(PrintStream os){
        transfer_output(os);
        if(offset.getType() == Machine.Operand.Type.Immediate){
            int offset = this.offset.id<<shift;
            os.println("ldr"+cond+"\t"+dOpd.toString()+",["+addr.toString()+",#"+offset+"]");
        }
        else{
            os.println("ldr"+cond+"\t"+dOpd.toString()+",["+addr.toString()+","+offset.toString()+",LSL #"+shift+"]");
        }
    }
}
