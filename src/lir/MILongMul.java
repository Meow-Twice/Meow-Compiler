package lir;

import java.io.PrintStream;

/**
 * Syntax
 * SMMUL{R}{cond} {Rd}, Rn, Rm
 *
 * where:
 *
 * R
 * is an optional parameter. If R is present, the result is rounded, otherwise it is truncated.
 *
 * cond
 * is an optional condition code.
 *
 * Rd
 * is the destination register.
 *
 * Rn, Rm
 * are the registers holding the operands.
 *
 * Ra
 * is a register holding the value to be added or subtracted from.
 *
 * Operation
 * SMMUL multiplies the 32-bit values from Rn and Rm, and stores the most significant 32 bits of the 64-bit result to Rd.
 */
public class MILongMul extends MachineInst {

    // Machine.Operand dOpd;
    // Machine.Operand lOpd;
    // Machine.Operand rOpd;

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
    public String toString(){
        return "LongMul\t"+getDst().toString()+",\t"+getLOpd().toString()+",\t"+getROpd().toString();
    }
    @Override
    public void output(PrintStream os, Machine.McFunction f) {
        transfer_output(os);
        os.println("\tsmmul\t" + getDst().toString() + ",\t" + getLOpd().toString() + ",\t" + getROpd().toString());
    }
}
