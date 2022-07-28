package lir;

import java.io.PrintStream;

public class MICompare extends MachineInst implements MachineInst.Compare {

    public MICompare(Machine.Operand lOpd, Machine.Operand rOpd, Machine.Block insertAtEnd) {
        super(Tag.ICmp, insertAtEnd);
        useOpds.add(lOpd);
        useOpds.add(rOpd);
    }

    public Machine.Operand getLOpd() {
        return useOpds.get(0);
    }

    public Machine.Operand getROpd() {
        return useOpds.get(1);
    }

    @Override
    public void output(PrintStream os, Machine.McFunction f) {
        transfer_output(os);
        os.println("\tcmp\t" + getLOpd() + "," + getROpd());
    }

    @Override
    public String toString() {
        return tag.toString() + '\t' + getLOpd() + ",\t" + getROpd();
    }
}
