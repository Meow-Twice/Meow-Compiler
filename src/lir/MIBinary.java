package lir;

import javax.crypto.Mac;
import java.io.PrintStream;

public class MIBinary extends MachineInst {
    // Add, Sub, Rsb, Mul, Div, Mod, Lt, Le, Ge, Gt, Eq, Ne, And, Or

    // @Override
    // public Arm.Cond getCond() {
    //     return cond;
    // }

    public MIBinary(MachineInst insertAfter, Tag tag, Machine.Operand dOpd, Machine.Operand lOpd, Machine.Operand rOpd) {
        super(insertAfter, tag);
        defOpds.add(dOpd);
        useOpds.add(lOpd);
        useOpds.add(rOpd);
    }

    public MIBinary(Tag tag, Machine.Operand dOpd, Machine.Operand lOpd, Machine.Operand rOpd, Machine.Block insertAtEnd) {
        super(tag, insertAtEnd);
        defOpds.add(dOpd);
        useOpds.add(lOpd);
        useOpds.add(rOpd);
    }

    public MIBinary(Tag tag, Machine.Operand dstAddr, Arm.Reg rSP, Machine.Operand offset, MachineInst firstUse) {
        super(tag, firstUse);
        defOpds.add(dstAddr);
        useOpds.add(rSP);
        useOpds.add(offset);
    }

    public Machine.Operand getDst() {
        return defOpds.get(0);
    }

    public Machine.Operand getLOpd() {
        return useOpds.get(0);
    }

    public Machine.Operand getROpd() {
        return useOpds.get(1);
    }

    public Machine.Operand setROpd(Machine.Operand o) {
        return useOpds.set(1, o);
    }

    public Machine.Operand setLOpd(Machine.Operand o){
        return useOpds.set(0,o);
    }
    @Override
    public void output(PrintStream os, Machine.McFunction f) {
        transfer_output(os);
        String tag_str = "\t" + switch (tag) {
            case Mul -> "mul";
            case Add -> "add";
            case Sub -> "sub";
            case Rsb -> "rsb";
            case Div -> "sdiv";
            case And -> "and";
            case Or -> "orr";
            default -> throw new AssertionError("Wrong Int Binary");
        };

        os.print(tag_str + "\t" + getDst() + ",\t" + getLOpd() + ",\t" + getROpd());
        if (shift.shiftType != Arm.ShiftType.None) {
            os.print(",\t" + shift);
        }
        os.print("\n");


    }

    @Override
    public String toString() {
        return tag.toString() + "\t" + getDst() + ",\t" + getLOpd() + ",\t" + getROpd();
        // (isNeedFix() ? getROpd().value + (this.getCallee() == null ? this.mb.mcFunc.getTotalStackSize() : this.getCallee().getTotalStackSize()) : getROpd());
    }
}