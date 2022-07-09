package lir;

import java.io.PrintStream;

public class MIStore extends MIAccess{
    Machine.Operand data;
    Machine.Operand addr;
    public MIStore(Machine.Block insertAtEnd,boolean isFloat){
        super(Tag.Store,insertAtEnd,isFloat);
    }

    public MIStore(Machine.Operand data, Machine.Operand addr, Machine.Operand offset, Machine.Block insertAtEnd) {
        super(Tag.Load, insertAtEnd);
        this.data = data;
        this.addr = addr;
        this.offset = offset;
        genDefUse();
    }

    @Override
    public void genDefUse() {
        useOpds.add(data);
        useOpds.add(addr);
        useOpds.add(offset);

    }
    @Override
    public void output(PrintStream os, Machine.McFunction f){
        transfer_output(os);
        if(offset.getType() == Machine.Operand.Type.Immediate){
            int offset = this.offset.value <<shift;
            os.println("str"+cond+"\t"+data.toString()+",["+addr.toString()+",#"+offset+"]");
        }
        else{
            os.println("str"+cond+"\t"+data.toString()+",["+addr.toString()+","+offset.toString()+",LSL #"+shift+"]");
        }
    }
}
