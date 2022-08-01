package lir;

import java.io.PrintStream;

import static lir.Machine.Program.encode_imm;
import static mir.type.DataType.I32;

public class I extends MachineInst {
    public I(Tag tag, Machine.Block insertAtEnd) {
        super(tag, insertAtEnd);
    }

    public I(Tag tag, MachineInst insertBefore) {
        super(tag, insertBefore);
    }

    public I(MachineInst insertAfter, Tag tag) {
        super(insertAfter, tag);
    }

    public static class Ldr extends I implements MachineMemInst {
        public Ldr(Machine.Operand data, Machine.Operand addr, Machine.Block insertAtEnd) {
            super(Tag.Ldr, insertAtEnd);
            defOpds.add(data);
            useOpds.add(addr);
        }

        public Ldr(Machine.Operand data, Machine.Operand addr, Machine.Operand offset, Machine.Block insertAtEnd) {
            super(Tag.Ldr, insertAtEnd);
            defOpds.add(data);
            useOpds.add(addr);
            useOpds.add(offset);
        }

        public Ldr(Machine.Operand data, Machine.Operand addr, Machine.Operand offset, MachineInst insertBefore) {
            super(Tag.Ldr, insertBefore);
            defOpds.add(data);
            useOpds.add(addr);
            useOpds.add(offset);
        }

        public Machine.Operand getData() {
            return defOpds.get(0);
        }

        public Machine.Operand getAddr() {
            return useOpds.get(0);
        }

        public Machine.Operand getOffset() {
            if (useOpds.size() < 2) return new Machine.Operand(I32, 0);
            return useOpds.get(1);
        }

        @Override
        public boolean isNoCond() {
            return super.isNoCond();
        }

        @Override
        public void output(PrintStream os, Machine.McFunction f) {
            os.println("\t" + this);
        }

        @Override
        public String toString() {
            return tag.toString() + cond.toString() + '\t' + getData() +
                    ",\t[" + getAddr() + (getOffset().equals(Machine.Operand.ZERO) ? "" : ",\t" + getOffset()) +
                    (shift.shiftType == Arm.ShiftType.None ? "" : ("\t," + shift)) + "]";
        }

        public void setOffSet(Machine.Operand offSet) {
            if (useOpds.size() > 1) {
                useOpds.set(1, offSet);
            } else {
                assert useOpds.size() > 0;
                useOpds.add(offSet);
            }
        }
    }


    public static class Str extends I implements MachineMemInst {
        @Override
        public Arm.Cond getCond() {
            return cond;
        }

        public Str(Machine.Operand data, Machine.Operand addr, Machine.Block insertAtEnd) {
            super(Tag.Str, insertAtEnd);
            useOpds.add(data);
            useOpds.add(addr);
            // useOpds.add(new Machine.Operand(I32, 0));
        }

        public Str(Machine.Operand data, Machine.Operand addr, Machine.Operand offset, Machine.Block insertAtEnd) {
            super(Tag.Str, insertAtEnd);
            useOpds.add(data);
            useOpds.add(addr);
            useOpds.add(offset);
        }

        /**
         * 寄存器分配时专用
         *
         * @param insertAfter
         * @param data
         * @param addr
         */
        public Str(MachineInst insertAfter, Machine.Operand data, Machine.Operand addr, Machine.Operand offset) {
            super(insertAfter, Tag.Str);
            useOpds.add(data);
            useOpds.add(addr);
            useOpds.add(offset);
        }

        public Machine.Operand getData() {
            return useOpds.get(0);
        }

        public Machine.Operand getAddr() {
            return useOpds.get(1);
        }

        public Machine.Operand getOffset() {
            if (useOpds.size() < 3) return new Machine.Operand(I32, 0);
            return useOpds.get(2);
        }

        public void setOffSet(Machine.Operand offSet) {
            if (useOpds.size() > 2) useOpds.set(2, offSet);
            else useOpds.add(offSet);
        }

        @Override
        public void output(PrintStream os, Machine.McFunction f) {
            os.println("\t" + this);
        }

        @Override
        public String toString() {
            return tag.toString() + cond + '\t' + getData() + ",\t[" + getAddr() +
                    (getOffset().equals(Machine.Operand.ZERO) ? "" : ",\t" + getOffset()) +
                    (shift.shiftType == Arm.ShiftType.None ? "" : ("\t," + shift)) + "]";
        }
    }

    public static class Ret extends I {
        public Ret(Arm.Reg retReg, Machine.Block insertAtEnd) {
            super(Tag.IRet, insertAtEnd);
            useOpds.add(retReg);
        }

        public Ret(Machine.Block insertAtEnd) {
            super(Tag.IRet, insertAtEnd);
        }

        @Override
        public void output(PrintStream os, Machine.McFunction mf) {
            // TODO vpush我觉得可能八字节对齐比较好, 所以vpop必须在后面, 这样不能先pop lr再vpop
            os.println("\tbx\tlr");
        }

        @Override
        public String toString() {
            return tag.toString() + (useOpds.size() > 0 ? useOpds.get(0) : "");
        }
    }

    public static class Mov extends I implements MachineMove {
        public Mov(Machine.Operand dOpd, Machine.Operand sOpd, Machine.Block insertAtEnd) {
            super(Tag.IMov, insertAtEnd);
            defOpds.add(dOpd);
            useOpds.add(sOpd);
        }

        public Mov(Machine.Operand dOpd, Machine.Operand sOpd, Arm.Shift shift, Machine.Block insertAtEnd) {
            super(Tag.IMov, insertAtEnd);
            defOpds.add(dOpd);
            useOpds.add(sOpd);
            this.shift = shift;
        }

        public Mov(MachineInst inst, Machine.Operand dOpd, Machine.Operand sOpd) {
            super(inst, Tag.IMov);
            defOpds.add(dOpd);
            useOpds.add(sOpd);
        }

        public Mov(Machine.Operand dOpd, Machine.Operand sOpd, MachineInst inst) {
            super(Tag.IMov, inst);
            defOpds.add(dOpd);
            useOpds.add(sOpd);
        }

        public Mov(Arm.Cond cond, Machine.Operand dOpd, Machine.Operand sOpd, Machine.Block insertAtEnd) {
            super(Tag.IMov, insertAtEnd);
            this.cond = cond;
            defOpds.add(dOpd);
            useOpds.add(sOpd);
        }

        @Override
        public void output(PrintStream os, Machine.McFunction f) {
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
                    os.append("movw" + cond + "\t" + getDst() + ",\t:lower16:" + src.getGlob());
                    os.append("movt" + cond + "\t" + getDst() + ",\t:upper16:" + src.getGlob());
                    //os.append("\tldr" + cond + "\t" + getDst().toString() + ",=" + src.getGlob());
                } else {
                    int imm = getSrc().value;
                    if (encode_imm(imm)) {
                        os.append("mov" + cond + "\t" + getDst().toString() + ",\t#" + imm);
                    } else {
                        int lowImm = (imm << 16) >>> 16;
                        os.append("movw" + cond + "\t" + getDst().toString() + ",\t#" + lowImm);
                        int highImm = imm >>> 16;
                        if (highImm != 0) {
                            os.append("\tmovt" + cond + "\t" + getDst().toString() + ",\t#" + highImm);
                        }
                    }
                }
            } else {
                os.append("mov" + cond + "\t" + getDst().toString() + ",\t" + getSrc().toString());
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

    public static class Binary extends I {
        // Add, Sub, Rsb, Mul, Div, Mod, Lt, Le, Ge, Gt, Eq, Ne, And, Or

        public Binary(MachineInst insertAfter, Tag tag, Machine.Operand dOpd, Machine.Operand lOpd, Machine.Operand rOpd) {
            super(insertAfter, tag);
            defOpds.add(dOpd);
            useOpds.add(lOpd);
            useOpds.add(rOpd);
        }

        public Binary(Tag tag, Machine.Operand dOpd, Machine.Operand lOpd, Machine.Operand rOpd, Machine.Block insertAtEnd) {
            super(tag, insertAtEnd);
            defOpds.add(dOpd);
            useOpds.add(lOpd);
            useOpds.add(rOpd);
        }

        public Binary(Tag tag, Machine.Operand dstAddr, Arm.Reg rSP, Machine.Operand offset, MachineInst firstUse) {
            super(tag, firstUse);
            defOpds.add(dstAddr);
            useOpds.add(rSP);
            useOpds.add(offset);
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

        public Machine.Operand setROpd(Machine.Operand o) {
            return useOpds.set(1, o);
        }

        @Override
        public void output(PrintStream os, Machine.McFunction f) {
            String tag_str = "\t" + switch (tag) {
                case Mul -> "mul";
                case Add -> "add";
                case Sub -> "sub";
                case Rsb -> "rsb";
                case Div -> "sdiv";
                case And -> "and";
                case Or -> "orr";
                default -> throw new AssertionError("Wrong Int Binary");
            };

            os.print(tag_str + "\t" + getDst() + ",\t" + getLOpd() + ",\t" + getROpd());
            if (shift.shiftType != Arm.ShiftType.None) {
                os.print(",\t" + shift);
            }
            os.print("\n");


        }

        @Override
        public String toString() {
            return tag.toString() + "\t" + getDst() + ",\t" + getLOpd() + ",\t" + getROpd();
        }
    }


    public static class Cmp extends I implements MachineInst.Compare {

        public Cmp(Machine.Operand lOpd, Machine.Operand rOpd, Machine.Block insertAtEnd) {
            super(Tag.ICmp, insertAtEnd);
            useOpds.add(lOpd);
            useOpds.add(rOpd);
        }

        public Machine.Operand getLOpd() {
            return useOpds.get(0);
        }

        public Machine.Operand getROpd() {
            return useOpds.get(1);
        }

        @Override
        public void output(PrintStream os, Machine.McFunction f) {
            os.println("\tcmp\t" + getLOpd() + "," + getROpd());
        }

        @Override
        public String toString() {
            return tag.toString() + '\t' + getLOpd() + ",\t" + getROpd();
        }
    }


    /**
     * Fma
     * smmla:Rn + (Rm * Rs)[63:32] or smmls:Rd := Rn – (Rm * Rs)[63:32]
     * mla:Rn + (Rm * Rs)[31:0] or mls:Rd := Rn – (Rm * Rs)[31:0]
     * dst = acc +(-) lhs * rhs
     */
    public static class Fma extends I {

        public Machine.Operand getDst() {
            return defOpds.get(0);
        }

        public Machine.Operand getlOpd() {
            return useOpds.get(0);
        }

        public Machine.Operand getrOpd() {
            return useOpds.get(1);
        }

        public Machine.Operand getAcc() {
            return useOpds.get(2);
        }

        public boolean add() {
            return add;
        }

        public boolean isSign() {
            return sign;
        }

        public Arm.Cond getCond() {
            return cond;
        }

        boolean add;
        boolean sign;
        // Arm.Cond cond;

        public Fma(boolean add, boolean sign,
                   Machine.Operand dst, Machine.Operand lOpd, Machine.Operand rOpd, Machine.Operand acc,
                   Machine.Block insertAtEnd) {
            //dst = acc +(-) lhs * rhs
            super(Tag.FMA, insertAtEnd);
            this.add = add;
            this.sign = sign;
            defOpds.add(dst);
            useOpds.add(lOpd);
            useOpds.add(rOpd);
            useOpds.add(acc);
        }

        @Override
        public void output(PrintStream os, Machine.McFunction f) {
            if (sign) {
                os.print("\tsm");
            }
            String op = "";
            if (add) {
                op = "mla";
            } else {
                op = "mls";
            }
            os.println(op + cond + "\t" + getDst() + ",\t" + getlOpd() + ",\t" + getrOpd() + ",\t" + getAcc());

        }

        @Override
        public String toString() {
            String res = "";
            if (sign) {
                res += "sm";
            }
            String op;
            if (add) {
                op = "mla";
            } else {
                op = "mls";
            }
            res += op + cond + "\t" + getDst().toString() + ",\t" + getlOpd().toString() + ",\t" + getrOpd().toString() + ",\t" + getAcc().toString();
            return res;
        }
    }

}
