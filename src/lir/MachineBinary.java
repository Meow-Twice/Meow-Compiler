package lir;

import lir.Machine;
import lir.MachineInst;

public class MachineBinary extends MachineInst {
    // Add, Sub, Rsb, Mul, Div, Mod, Lt, Le, Ge, Gt, Eq, Ne, And, Or

    Machine.Operand dOpd;
    Machine.Operand lOpd;
    Machine.Operand rOpd;
    Arm.Shift shift;

    public MachineBinary(Tag tag, Machine.Block insertAtEnd) {
        super(tag, insertAtEnd);
    }

    public MachineBinary(Tag tag, Machine.Operand dOpd, Machine.Operand lOpd, Machine.Operand rOpd, Machine.Block insertAtEnd) {
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
}