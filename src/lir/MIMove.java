package lir;

import java.io.PrintStream;

public class MIMove extends MachineInst {
    Arm.Cond cond = Arm.Cond.Any;
    Machine.Operand dOpd;
    Machine.Operand sOpd;
    Arm.Shift shift = Arm.Shift.NONE_SHIFT;

    public MIMove(Machine.Block insertAtEnd) {
        super(Tag.Mv, insertAtEnd);
        this.cond = Arm.Cond.Any;
        genDefUse();
    }

    public boolean encode_imm(int imm) {
        for (int ror = 0; ror < 32; ror += 2) {
            if ((imm & ~0xFF) == 0) {
                return true;
            }
            imm = (imm << 2) | (imm >> 30);
        }
        return false;
    }

    public MIMove(Machine.Operand dOpd, Machine.Operand sOpd, Machine.Block insertAtEnd) {
        super(Tag.Mv, insertAtEnd);
        this.dOpd = dOpd;
        this.sOpd = sOpd;
        this.cond = Arm.Cond.Any;
        insertAtEnd.insertAtEnd(this);
        genDefUse();
    }

    public MIMove(Machine.Operand dOpd, Machine.Operand sOpd, Arm.Shift shift,Machine.Block insertAtEnd) {
        super(Tag.Mv, insertAtEnd);
        this.dOpd = dOpd;
        this.sOpd = sOpd;
        this.cond = Arm.Cond.Any;
        this.shift = shift;
        insertAtEnd.insertAtEnd(this);
        genDefUse();
    }

    public MIMove(Machine.Operand dOpd, Machine.Operand sOpd, MachineInst inst) {
        super(Tag.Mv, inst);
        this.dOpd = dOpd;
        this.sOpd = sOpd;
        this.cond = Arm.Cond.Any;
        inst.insertBefore(this);
        genDefUse();
    }

    public MIMove(Arm.Cond cond, Machine.Operand dOpd, Machine.Operand sOpd, Machine.Block insertAtEnd) {
        super(Tag.Mv, insertAtEnd);
        this.cond = cond;
        this.dOpd = dOpd;
        this.sOpd = sOpd;
        insertAtEnd.insertAtEnd(this);
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

    public MIMove() {
        super(Tag.Mv);
        cond = Arm.Cond.Any;
    }

    @Override
    public void genDefUse() {
        defOpds.add(dOpd);
        useOpds.add(sOpd);
    }

    @Override
    public void output(PrintStream os, Machine.McFunction f) {
        transfer_output(os);
        if (sOpd.type == Machine.Operand.Type.Immediate && encode_imm(sOpd.value)) {
            int imm = sOpd.value;
            if (imm >> 16 == 0) {
                os.println("movw" + cond + "\t" + dOpd.toString() + ",#" + imm);
            } else {
                os.println("ldr" + cond + "\t" + dOpd.toString() + ",=" + imm);
            }
        } else {
            os.print("mov" + cond + "\t" + dOpd.toString() + "," + sOpd.toString());
            os.println("," + shift.toString());
        }
    }

    @Override
    public boolean isMove() {
        return true;
    }

    public Machine.Operand getDst() {
        return dOpd;
    }

    public boolean directColor() {
        return dOpd.needColor() && sOpd.needColor() && cond == Arm.Cond.Any && shift.shiftType == Arm.ShiftType.None;
    }

    public Machine.Operand getSrc() {
        return sOpd;
    }

    @Override
    public String toString() {
        return tag.toString() + cond.toString() + '\t' + dOpd.toString() + ",\t"+sOpd.toString();
    }
}
