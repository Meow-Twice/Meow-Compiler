package lir;

import java.io.PrintStream;

public class MIMove extends MachineInst {
    // @Override
    // public Arm.Cond getCond() {
    //     return cond;
    // }

    // Arm.Cond cond = Arm.Cond.Any;
    // // Machine.Operand dOpd;
    // // Machine.Operand sOpd;
    // Arm.Shift shift = Arm.Shift.NONE_SHIFT;

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
        defOpds.add(dOpd);
        useOpds.add(sOpd);
        // this.dOpd = dOpd;
        // this.sOpd = sOpd;
        // this.cond = Arm.Cond.Any;
    }


    public MIMove(Machine.Operand dOpd, Machine.Operand sOpd, Arm.Shift shift,Machine.Block insertAtEnd) {
        super(Tag.Mv, insertAtEnd);
        defOpds.add(dOpd);
        useOpds.add(sOpd);
        // this.dOpd = dOpd;
        // this.sOpd = sOpd;
        // this.cond = Arm.Cond.Any;
        this.shift = shift;
    }

    public MIMove(MachineInst inst, Machine.Operand dOpd, Machine.Operand sOpd) {
        super(inst, Tag.Mv);
        defOpds.add(dOpd);
        useOpds.add(sOpd);
        // this.dOpd = dOpd;
        // this.sOpd = sOpd;
        // this.cond = Arm.Cond.Any;
    }

    public MIMove(Machine.Operand dOpd, Machine.Operand sOpd, MachineInst inst) {
        super(Tag.Mv, inst);
        defOpds.add(dOpd);
        useOpds.add(sOpd);
        // this.dOpd = dOpd;
        // this.sOpd = sOpd;
        // this.cond = Arm.Cond.Any;
    }

    public MIMove(Arm.Cond cond, Machine.Operand dOpd, Machine.Operand sOpd, Machine.Block insertAtEnd) {
        super(Tag.Mv, insertAtEnd);
        this.cond = cond;
        defOpds.add(dOpd);
        useOpds.add(sOpd);
        // this.dOpd = dOpd;
        // this.sOpd = sOpd;
    }

    public boolean operator(MIMove move) {
        if (this.cond != move.cond)
            return this.cond.compareTo(move.cond) < 0;
        if (!this.getDst().equals(this.getDst()))
            return this.getDst().compareTo(this.getDst());
        if (this.getSrc() != this.getSrc())
            return this.getSrc().compareTo(this.getSrc());
        return false;
    }

    public MIMove() {
        super(Tag.Mv);
        // cond = Arm.Cond.Any;
    }

    // @Override
    // public void genDefUse() {
    //     defOpds.add(dOpd);
    //     useOpds.add(sOpd);
    // }

    @Override
    public void output(PrintStream os, Machine.McFunction f) {
        transfer_output(os);
        if (getSrc().type == Machine.Operand.Type.Immediate && encode_imm(getSrc().value)) {
            int imm = getSrc().value;
            if (imm >> 16 == 0) {
                os.println("movw" + cond + "\t" + getDst().toString() + ",#" + imm);
            } else {
                os.println("ldr" + cond + "\t" + getDst().toString() + ",=" + imm);
            }
        } else {
            os.print("mov" + cond + "\t" + getDst().toString() + "," + getSrc().toString());
            os.println("," + shift.toString());
        }
    }

    @Override
    public boolean isMove() {
        return true;
    }

    public Machine.Operand getDst() {
        return defOpds.get(0);
    }

    public boolean directColor() {
        return getDst().needColor() && getSrc().needColor() && cond == Arm.Cond.Any && shift.shiftType == Arm.ShiftType.None;
    }

    public Machine.Operand getSrc() {
        return useOpds.get(0);
    }

    @Override
    public String toString() {
        if (getDst() == null) {
            assert false;
        }
        return tag.toString() + cond.toString() + '\t' + getDst().toString() + ",\t" + getSrc().toString();
    }

    public void setSrc(Machine.Operand offset_opd) {
        assert offset_opd != null;
        useOpds.set(0, offset_opd);
    }

    public void setDst(Machine.Operand dst) {
        assert dst != null;
        defOpds.set(0, dst);
    }
}