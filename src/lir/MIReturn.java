package lir;

import mir.type.DataType;

import javax.crypto.Mac;
import java.io.PrintStream;

import static lir.Machine.Program.pop_output;

public class MIReturn extends MachineInst {
    public MIReturn(Arm.Reg r0, Machine.Block insertAtEnd) {
        super(Tag.Return, insertAtEnd);
        useOpds.add(r0);
        // if(dataType == DataType.I32){
        //     useOpds.add(Arm.Reg.getR(Arm.Regs.GPRs.r0));
        // }else if(dataType == DataType.F32){
        //     useOpds.add(Arm.Reg.getS(Arm.Regs.FPRs.s0));
        // }
        // genDefUse();
    }

    public MIReturn(Machine.Block insertAtEnd) {
        super(Tag.Return, insertAtEnd);
    }
    // @Override
    // public void genDefUse() {
    //     useOpds.add(ret);
    // }

    @Override
    public void output(PrintStream os, Machine.McFunction mf) {
        pop_output(os, mf);
    }

    @Override
    public String toString() {
        return tag.toString() + (useOpds.size() > 0 ? useOpds.get(0) : "");
    }
}
