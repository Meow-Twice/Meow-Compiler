package lir;

import java.io.PrintStream;

public class MIStore extends MIAccess{
    Machine.Operand data;
    Machine.Operand addr;
    public MIStore(Machine.Block insertAtEnd,boolean isFloat){
        super(Tag.Store,insertAtEnd,isFloat);
    }

    @Override
    public void genDefUse() {
        useOpds.add(data);
        useOpds.add(addr);

    }
    public void output(PrintStream os){
        transfer_output(os);
        if(offset.getType() == Machine.Operand.Type.Immediate){
            int offset = this.offset.id<<shift;
            os.println("str"+cond+"\t"+data.toString()+",["+addr.toString()+",#"+offset+"]");
        }
        else{
            os.println("str"+cond+"\t"+data.toString()+",["+addr.toString()+","+offset.toString()+",LSL #"+shift+"]");
        }
    }
}
