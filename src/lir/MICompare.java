package lir;

import javax.crypto.Mac;
import java.io.PrintStream;

public class MICompare extends MachineInst{
    // Machine.Operand lOpd;
    // Machine.Operand rOpd;
    public MICompare(Machine.Block insertAtEnd){
        super(Tag.Compare,insertAtEnd);
        genDefUse();
    }

    public MICompare(Machine.Operand lOpd, Machine.Operand rOpd, Machine.Block insertAtEnd){
        super(Tag.Compare,insertAtEnd);
        useOpds.add(lOpd);
        useOpds.add(rOpd);
    }

    // @Override
    // public void genDefUse() {
    //     useOpds.add(lOpd);
    //     useOpds.add(rOpd);
    // }

    public Machine.Operand getLOpd(){
        return useOpds.get(0);
    }

    public Machine.Operand getROpd(){
        return useOpds.get(1);
    }

    @Override
    public void output(PrintStream os, Machine.McFunction f){
        transfer_output(os);
        if(!isFloat) {
            os.println("cmp\t" + getLOpd().toString() + "," + getROpd().toString());
        }
        else{
            os.println("vcmpe.f32\t" + getLOpd().toString() + "," + getROpd().toString());
            os.println("vmrs\tAPSR_nzcv, FPSCR");
        }
    }

    @Override
    public String toString() {
        return tag.toString()+'\t' + getLOpd() + ",\t"+getROpd();
    }
}
