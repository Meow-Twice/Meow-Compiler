package lir;

import java.io.PrintStream;

public class MIJump extends MachineInst{
    Machine.Block target;
    public MIJump(Machine.Block target,Machine.Block insertAAtEnd,boolean isFloat){
        super(Tag.Jump,insertAAtEnd,isFloat);
        this.target = target;
    }

    public void output(PrintStream os){
        transfer_output(os);
        os.println("b\t"+bb.index);
    }
}
