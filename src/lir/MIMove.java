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

    public MIMove(Machine.Operand dOpd, Machine.Operand sOpd, Arm.Shift shift, Machine.Block insertAtEnd) {
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
        Machine.Operand src = getSrc();
        if (src.type == Machine.Operand.Type.Immediate) {
            if (src.isGlobPtr()) {
                os.println("\tmovw" + cond + "\t" + getDst() + ",\t:lower16:" + src.getGlob());
                os.println("\tmovt" + cond + "\t" + getDst() + ",\t:upper16:" + src.getGlob());
                //os.println("\tldr" + cond + "\t" + getDst().toString() + ",=" + src.getGlob());
            } else {
                int imm = getSrc().value;
                if (encode_imm(imm)) {
                    os.println("\tmov" + cond + "\t" + getDst().toString() + ",\t#" + imm);
                } else {
                    int lowImm = (imm << 16) >>> 16;
                    os.println("\tmovw" + cond + "\t" + getDst().toString() + ",\t#" + lowImm);
                    int highImm = imm >>> 16;
                    if (highImm != 0) {
                        os.println("\tmovt" + cond + "\t" + getDst().toString() + ",\t#" + highImm);
                    }
                }
            }
        } else {
            os.print("\tmov" + cond + "\t" + getDst().toString() + ",\t" + getSrc().toString());
            if (shift != Arm.Shift.NONE_SHIFT) {
                os.println(",\t" + shift.toString());
            } else {
                os.print("\n");
            }
        }
    }

    public Machine.Operand getDst() {
        return defOpds.get(0);
    }

    public boolean directColor() {
        return getDst().need_I_Color() && getSrc().need_I_Color() && cond == Arm.Cond.Any && shift.shiftType == Arm.ShiftType.None;
    }

    public Machine.Operand getSrc() {
        return useOpds.get(0);
    }

    String oldToString = "";

    @Override
    public String toString() {
        if (getDst() == null) {
            assert false;
        }
        StringBuilder os = new StringBuilder();
        Machine.Operand src = getSrc();
        if (src.type == Machine.Operand.Type.Immediate) {
            if (src.isGlobPtr()) {
                os.append("\tmovw" + cond + "\t" + getDst() + ",\t:lower16:" + src.getGlob());
                os.append("\tmovt" + cond + "\t" + getDst() + ",\t:upper16:" + src.getGlob());
                //os.append("\tldr" + cond + "\t" + getDst().toString() + ",=" + src.getGlob());
            } else {
                int imm = getSrc().value;
                if (encode_imm(imm)) {
                    os.append("\tmov" + cond + "\t" + getDst().toString() + ",\t#" + imm);
                } else {
                    int lowImm = (imm << 16) >>> 16;
                    os.append("\tmovw" + cond + "\t" + getDst().toString() + ",\t#" + lowImm);
                    int highImm = imm >>> 16;
                    if (highImm != 0) {
                        os.append("\tmovt" + cond + "\t" + getDst().toString() + ",\t#" + highImm);
                    }
                }
            }
        } else {
            os.append("\tmov" + cond + "\t" + getDst().toString() + ",\t" + getSrc().toString());
            if (shift != Arm.Shift.NONE_SHIFT) {
                os.append(",\t" + shift.toString());
            }
        }
        if (oldToString.equals("")) {
            oldToString = os.toString();
        }
        return os + "{\t--\t" + oldToString + "\t--\t}";
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