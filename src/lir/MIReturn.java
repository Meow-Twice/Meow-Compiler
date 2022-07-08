package lir;

import java.io.PrintStream;

public class MIReturn extends MachineInst{
    Machine.Operand ret;
    public MIReturn(Machine.Block insertAtEnd,boolean isFloat){
        super(Tag.Return,insertAtEnd,isFloat);
    }
    @Override
    public void genDefUse() {
        useOpds.add(ret);
    }

    public void output(PrintStream os){
        return;
    }
}
