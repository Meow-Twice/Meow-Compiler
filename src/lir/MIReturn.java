package lir;

import java.io.PrintStream;

import static lir.Machine.Program.pop_output;

public class MIReturn extends MachineInst{
    public MIReturn(Machine.Block insertAtEnd){
        super(Tag.Return,insertAtEnd);
        useOpds.add(Arm.Reg.getR(Arm.Regs.GPRs.r0));
        // genDefUse();
    }
    // @Override
    // public void genDefUse() {
    //     useOpds.add(ret);
    // }

    @Override
    public void output(PrintStream os, Machine.McFunction mf){
        pop_output(os, mf);
    }

    @Override
    public String toString() {
        return tag.toString();
    }
}
