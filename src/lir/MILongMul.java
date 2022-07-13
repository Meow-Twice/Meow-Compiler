package lir;

import java.io.PrintStream;

public class MILongMul extends MachineInst {

    // Machine.Operand dOpd;
    // Machine.Operand lOpd;
    // Machine.Operand rOpd;

    public MILongMul(Machine.Block insertAtEnd, boolean isFloat) {
        super(Tag.LongMul, insertAtEnd, isFloat);
    }

    public MILongMul(Machine.Operand dOpd, Machine.Operand lOpd, Machine.Operand rOpd, Machine.Block insertAtEnd) {
        super(Tag.LongMul, insertAtEnd);
        defOpds.add(dOpd);
        useOpds.add(lOpd);
        useOpds.add(rOpd);
    }

    // @Override
    // public void genDefUse() {
    //     defOpds.add(dOpd);
    //     useOpds.add(lOpd);
    //     useOpds.add(rOpd);
    // }

    public Machine.Operand getDst(){
        return defOpds.get(0);
    }

    public Machine.Operand getLOpd(){
        return useOpds.get(0);
    }

    public Machine.Operand getROpd(){
        return useOpds.get(1);
    }

    @Override
    public void output(PrintStream os, Machine.McFunction f) {
        transfer_output(os);
        os.println("smmul\t" + getDst().toString() + ",\t" + getLOpd().toString() + ",\t" + getROpd().toString());
    }
}
