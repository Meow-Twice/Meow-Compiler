package lir;

public class MIMove extends MachineInst {
    Arm.Cond cond;
    Machine.Operand dOpd;
    Machine.Operand sOpd;
    Arm.Shift shift;

    public MIMove(Machine.Block insertAtEnd) {
        super(Tag.Mv, insertAtEnd);
        this.cond = Arm.Cond.Any;
        genDefUse();
    }

    public MIMove(Machine.Operand dOpd, Machine.Operand sOpd, Machine.Block insertAtEnd) {
        super(Tag.Mv, insertAtEnd);
        this.dOpd = dOpd;
        this.sOpd = sOpd;
        this.cond = Arm.Cond.Any;
        genDefUse();
    }

    public MIMove(Arm.Cond cond, Machine.Operand dOpd, Machine.Operand sOpd, Machine.Block insertAtEnd) {
        super(Tag.Mv, insertAtEnd);
        this.cond = cond;
        this.dOpd = dOpd;
        this.sOpd = sOpd;
        genDefUse();
    }

    public boolean operator(MIMove move) {
        if (this.cond != move.cond)
            return this.cond.compareTo(move.cond) < 0;
        if (this.dOpd != this.dOpd)
            return this.dOpd.compareTo(this.dOpd);
        if (this.sOpd != this.sOpd)
            return this.sOpd.compareTo(this.sOpd);
        return false;
    }

    @Override
    public void genDefUse() {
        defOpds.add(dOpd);
        useOpds.add(sOpd);
    }
}
