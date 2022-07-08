package lir;

import mir.Instr;

import javax.crypto.Mac;
import java.io.PrintStream;

public class MIFma extends MachineInst{
    Machine.Operand acc;
    Machine.Operand dst;
    Machine.Operand lOpd;
    Machine.Operand rOpd;
    boolean add;
    boolean sign;
    Arm.Cond cond;
    public MIFma(boolean add,boolean sign,Machine.Block insertAtEnd,boolean isFloat){
        super(Tag.FMA,insertAtEnd,isFloat);
        this.add = add;
        this.sign = sign;

    }
    public void output(PrintStream os){
        transfer_output(os);
        if(sign){
            os.print("sm");
        }
        String op = null;
        if(add){
            op = "mla";
        }
        else{
            op = "mls";
        }
        os.println(op+cond+"\t"+dst.toString()+","+lOpd.toString()+","+rOpd.toString()+","+acc.toString());

    }
}
