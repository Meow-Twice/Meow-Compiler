package lir;

import javax.crypto.Mac;
import java.io.PrintStream;

public class MICompare extends MachineInst{
    Machine.Operand lOpd;
    Machine.Operand rOpd;
    public MICompare(Machine.Block insertAtEnd){
        super(Tag.Compare,insertAtEnd);
        genDefUse();
    }

    public MICompare(Machine.Operand lOpd, Machine.Operand rOpd, Machine.Block insertAtEnd){
        super(Tag.Compare,insertAtEnd);
        this.lOpd = lOpd;
        this.rOpd = rOpd;
        genDefUse();
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
