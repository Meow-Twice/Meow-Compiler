package lir;
import lir.Machine;
import lir.MachineInst;
import lir.Tag;

public class MachineBinary extends MachineInst {
    // Add, Sub, Rsb, Mul, Div, Mod, Lt, Le, Ge, Gt, Eq, Ne, And, Or
    Machine.Operand lOpd;
    Machine.Operand rOpd;
    Machine.Operand dOpd;
    Arm.Shift shift;

    public MachineBinary(Tag tag, Machine.Block insertAtEnd){
        super(tag,insertAtEnd);
    }

}