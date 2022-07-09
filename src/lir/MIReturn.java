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
        if(f.stackSize>0){
            Machine.Program.stack_output(os,false,f.stackSize,"\t");
            os.print("\t");
        }
        boolean bx = true;
        if(!f.usedCalleeSavedRegs.isEmpty()||f.useLr){
            os.print("pop\t{");
            f.output_reg_list(os);
            if(f.useLr){
                if(!f.usedCalleeSavedRegs.isEmpty()){
                    os.print(",");
                }
                os.print("pc");
                bx = false;
            }
            os.println("}");
            if(bx){
                if(!f.usedCalleeSavedRegs.isEmpty()){
                    os.print("\t");
                }
                os.println("bx\tlr");
            }
        }

        return;
    }
}
