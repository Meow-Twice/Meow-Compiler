package lir;

import javax.crypto.Mac;
import java.io.PrintStream;

public class MICompare extends MachineInst{
    Machine.Operand lOpd;
    Machine.Operand rOpd;
    public MICompare(Machine.Block insertAtEnd,boolean isFloat){
        super(Tag.Compare,insertAtEnd,isFloat);
    }

    @Override
    public void genDefUse() {
        useOpds.add(lOpd);
        useOpds.add(rOpd);
    }

    public void output(PrintStream os){
        transfer_output(os);
        os.println("cmp\t"+lOpd.toString()+","+rOpd.toString());
    }
}
