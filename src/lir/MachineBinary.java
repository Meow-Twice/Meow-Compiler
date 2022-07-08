package lir;

import lir.Machine;
import lir.MachineInst;
import lir.Tag;

public class MachineBinary extends MachineInst {
    // Add, Sub, Rsb, Mul, Div, Mod, Lt, Le, Ge, Gt, Eq, Ne, And, Or

    Machine.Operand dOpd;
    Machine.Operand lOpd;
    Machine.Operand rOpd;
    Arm.Shift shift;

    public MachineBinary(Tag tag, Machine.Operand dOpd, Machine.Operand lOpd, Machine.Operand rOpd, Machine.Block insertAtEnd) {
        super(tag, insertAtEnd);
    }

    @Override
    public void genDefUse() {
        defOpds.add(dOpd);
        useOpds.add(lOpd);
        useOpds.add(rOpd);
    }
}