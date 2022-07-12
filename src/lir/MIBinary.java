package lir;

import java.io.PrintStream;

public class MIBinary extends MachineInst {
    // Add, Sub, Rsb, Mul, Div, Mod, Lt, Le, Ge, Gt, Eq, Ne, And, Or

    public Machine.Operand dOpd;
    public Machine.Operand lOpd;
    public Machine.Operand rOpd;
    public Arm.Shift shift;

    public MIBinary(Tag tag, Machine.Block insertAtEnd, boolean isFloat) {
        super(tag, insertAtEnd, isFloat);
    }

    public MIBinary(Tag tag, Machine.Operand dOpd, Machine.Operand lOpd, Machine.Operand rOpd, Machine.Block insertAtEnd) {
        super(tag, insertAtEnd);
        this.dOpd = dOpd;
        this.rOpd = rOpd;
        this.lOpd = lOpd;
        genDefUse();
    }

    @Override
    public void genDefUse() {
        defOpds.add(dOpd);
        useOpds.add(lOpd);
        useOpds.add(rOpd);
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
        os.print(tag_str + "\t" + dOpd.toString() + "," + lOpd.toString() + "," + rOpd.toString());
        if (shift.shiftType != Arm.ShiftType.None) {
            os.println("," + shift.toString());
        }
    }

    @Override
    public String toString() {
        return tag.toString() + " , " + dOpd + " , " + lOpd + " , " + rOpd;
    }
}