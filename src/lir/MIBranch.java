package lir;

import java.io.PrintStream;

public class MIBranch extends MachineInst{
    Arm.Cond cond ;
    Machine.Block target;
    public MIBranch(Machine.Block insertAtEnd,boolean isFloat){
        super(Tag.Branch,insertAtEnd,isFloat);
    }
    public void output(PrintStream os){
        transfer_output(os);
        os.println("b\t"+cond+"\t"+bb.index);
    }
}
