package lir;

import java.io.PrintStream;

public class MILongMul extends MachineInst{

    Machine.Operand dOpd;
    Machine.Operand lOpd;
    Machine.Operand rOpd;
    public MILongMul(Machine.Block insertAtEnd,boolean isFloat){
        super(Tag.LongMul,insertAtEnd,isFloat);
    }

    @Override
    public void genDefUse() {
        defOpds.add(dOpd);
        useOpds.add(lOpd);
        useOpds.add(rOpd);
    }
    public void output(PrintStream os){
        transfer_output(os);
        os.println("smmul\t"+dOpd.toString()+","+lOpd.toString()+","+rOpd.toString());
    }
}
