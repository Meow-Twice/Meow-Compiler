package lir;

import java.io.PrintStream;

public class MIReturn extends MachineInst{
    Machine.Operand ret;
    public MIReturn(Machine.Operand ret, Machine.Block insertAtEnd){
        super(Tag.Return,insertAtEnd);
        this.ret = ret;
        genDefUse();
    }
    @Override
    public void genDefUse() {
        useOpds.add(ret);
    }

    public void output(PrintStream os){
        return;
    }
}
