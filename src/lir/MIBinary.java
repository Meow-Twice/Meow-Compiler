package lir;

import java.io.PrintStream;

public class MIBinary extends MachineInst {
    // Add, Sub, Rsb, Mul, Div, Mod, Lt, Le, Ge, Gt, Eq, Ne, And, Or

    // public Machine.Operand dOpd;
    // public Machine.Operand lOpd;
    // public Machine.Operand rOpd;
    // public Arm.Shift shift;
    // Arm.Cond cond = Arm.Cond.Any;

    // @Override
    // public Arm.Cond getCond() {
    //     return cond;
    // }


    public MIBinary(Tag tag, Machine.Block insertAtEnd, boolean isFloat) {
        super(tag, insertAtEnd, isFloat);
    }

    public MIBinary(Tag tag, Machine.Operand dOpd, Machine.Operand lOpd, Machine.Operand rOpd, Machine.Block insertAtEnd) {
        super(tag, insertAtEnd);
        defOpds.add(dOpd);
        useOpds.add(lOpd);
        useOpds.add(rOpd);
        genDefUse();
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

    @Override
    public void output(PrintStream os, Machine.McFunction f) {
        transfer_output(os);
        String tag_str = switch (tag) {
            case Mul -> "mul";
            case Add -> "add";
            case Sub -> "sub";
            case Rsb -> "rsb";
            case Div -> "sdiv";
            case And -> "and";
            case Or -> "orr";
            default -> null;
        };
        os.print(tag_str + "\t" + getDst().toString() + "," + getLOpd().toString() + "," + getROpd().toString());
        if (shift.shiftType != Arm.ShiftType.None) {
            os.println("," + shift);
        }
    }

    @Override
    public String toString() {
        return tag.toString() + "\t" + getDst() + ",\t" + getLOpd() + ",\t" +
                (isNeedFix() ? getROpd().value + this.mb.mcFunc.getStackSize() : getROpd());
    }
}