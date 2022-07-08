package lir;

import java.io.PrintStream;

public class MIReturn extends MachineInst{
    public MIReturn(Machine.Block insertAtEnd){
        super(Tag.Return,insertAtEnd);
        // genDefUse();
    }
    // @Override
    // public void genDefUse() {
    //     useOpds.add(ret);
    // }

    public void output(PrintStream os){
        return;
    }
}
