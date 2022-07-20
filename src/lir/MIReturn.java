package lir;

import javax.crypto.Mac;
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

    @Override
    public void output(PrintStream os, Machine.McFunction f){
        os.print("pop\t{");
        for (Arm.Regs reg : f.usedCalleeSavedRegs) {
            os.print(reg + ",");
        }
        os.println("pc}");
    }

    @Override
    public String toString() {
        return tag.toString();
    }
}
