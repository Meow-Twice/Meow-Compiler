package lir;

import lir.Machine;
import lir.MachineInst;
import lir.Tag;

import java.io.PrintStream;

public class MachineBinary extends MachineInst {
    // Add, Sub, Rsb, Mul, Div, Mod, Lt, Le, Ge, Gt, Eq, Ne, And, Or

    Machine.Operand dOpd;
    Machine.Operand lOpd;
    Machine.Operand rOpd;
    Arm.Shift shift;

    public MachineBinary(Tag tag, Machine.Block insertAtEnd,boolean isFloat) {
        super(tag, insertAtEnd,isFloat);
    }

    @Override
    public void genDefUse() {
        defOpds.add(dOpd);
        useOpds.add(lOpd);
        useOpds.add(rOpd);
    }

    public void output(PrintStream os){
        transfer_output(os);
        String tag_str  = null;
        switch (tag){
            case Mul:
                tag_str = "mul";
                break;
            case Add:
                tag_str = "add";
                break;
            case Sub:
                tag_str = "sub";
                break;
            case Rsb:
                tag_str = "rsb";
                break;
            case Div:
                tag_str = "sdiv";
                break;
            case And:
                tag_str = "and";
                break;
            case Or:
                tag_str = "orr";
                break;
            default:
                tag_str = null;

        }
        os.print(tag_str+"\t"+dOpd.toString()+","+lOpd.toString()+","+rOpd.toString());
        if(shift.shiftType != Arm.ShiftType.None){
            os.println(","+shift.toString());
        }
    }
}