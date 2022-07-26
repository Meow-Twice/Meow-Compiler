package lir;

import backend.CodeGen;
import mir.Constant;
import mir.type.DataType;

import java.io.PrintStream;
import java.util.ArrayList;

import static lir.MachineInst.Tag.*;
import static lir.Machine.Operand;
import static mir.type.DataType.F32;
import static mir.type.DataType.I32;

public class V extends MachineInst {

    // protected String getCvtSuffixType(Operand dst, Operand src) {
    //     DataType lDataType = dst.dataType;
    //     DataType rDataType = src.dataType;
    //     CvtType cvtCvtType = switch (lDataType) {
    //         case I32 -> switch (rDataType) {
    //             case I32 -> throw new AssertionError("LIRI\t" + dst + "\t" + src);
    //             case F32 -> CvtType.LIRF;
    //             case I1 -> throw new AssertionError("LIRB\t" + dst + "\t" + src);
    //         };
    //         case F32 -> switch (rDataType) {
    //             case I32 -> CvtType.LFRI;
    //             case F32 -> CvtType.LFRF;
    //             case I1 -> throw new AssertionError("LIRB\t" + dst + "\t" + src);
    //         };
    //         case I1 -> throw new AssertionError("LB\t" + dst + "\t" + src);
    //     };
    //
    //     return switch (cvtCvtType) {
    //         case LFRI -> ".f32.s32";
    //         case LIRF -> ".s32.f32";
    //         case LFRF -> throw new AssertionError("WRONG LFRF in vmov of LFRF");
    //         case LIRI -> throw new AssertionError("WRONG LIRI in vmov of LIRI");
    //         case None -> throw new AssertionError("WRONG None in vmov");
    //     };
    // }

    protected String getMvSuffixTypeSimply(Operand dst) {
        return switch (dst.dataType) {
            case F32 -> ".f32";
            case I32 -> "";
            case I1 -> throw new AssertionError("WRONG dst of vmov: i1\t" + dst);
        };
    }

    public static final ArrayList<Constant.ConstantFloat> CONST_FLOAT_POLL = new ArrayList<>();

    public V(Tag tag, Machine.Block insertAtEnd) {
        super(tag, insertAtEnd);
    }

    public V(Tag tag, MachineInst insertBefore) {
        super(tag, insertBefore);
    }

    public V(MachineInst insertAfter, Tag tag) {
        super(insertAfter, tag);
    }

    public static class Ldr extends V {
        /**
         * 函数开始取出参数
         * loadInst
         * @param data
         * @param addr
         * @param offset
         * @param insertAtEnd
         */
        public Ldr(Machine.Operand data, Machine.Operand addr, Machine.Operand offset, Machine.Block insertAtEnd) {
            super(VLdr, insertAtEnd);
            defOpds.add(data);
            useOpds.add(addr);
            useOpds.add(offset);
        }

        /**
         *
         * @param sVr
         * @param constF
         * @param insertAtEnd
         */
        public Ldr(Machine.Operand sVr, Machine.Operand constF, Machine.Block insertAtEnd) {
            super(VLdr, insertAtEnd);
            defOpds.add(sVr);
            useOpds.add(constF);
        }

        public Ldr(Machine.Operand data, Machine.Operand addr, Machine.Operand offset, MachineInst insertBefore) {
            super(VLdr, insertBefore);
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
            if (useOpds.size() < 2) return null;
            return useOpds.get(1);
        }

        @Override
        public void output(PrintStream os, Machine.McFunction f) {
            transfer_output(os);
            Operand off = getOffset();
            if (off == null) {
                // TODO 待优化
                os.println("\tvldr" + cond + ".32\t" + getData() + ",\t[" + getAddr() + "]");
            } else if (this.shift.shiftType == Arm.ShiftType.None) {
                os.println("\tvldr" + cond + ".32\t" + getData() + ",\t[" + getAddr() + ",\t" + off + "]");
            } else {
                os.println("\tvldr" + cond + ".32\t" + getData() + ",\t[" + getAddr() + ",\t" + off + ",\tLSL #" + this.shift.shift + "]");
            }
        }

        @Override
        public String toString() {
            return tag.toString() + cond.toString() + '\t' + getData() + ",\t[" + getAddr() + ",\t" + getOffset()/*(this.isNeedFix() ? getOffset().value + this.mb.mcFunc.getTotalStackSize() : getOffset())*/ +
                    (shift.shiftType == Arm.ShiftType.None ? "" : ("\t," + shift)) + "\t]";
        }
    }

    public static class Str extends V {

        public Str(Machine.Operand data, Machine.Operand addr, Machine.Operand offset, Machine.Block insertAtEnd) {
            super(VStr, insertAtEnd);
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
            super(insertAfter, VStr);
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
            return useOpds.get(2);
        }

        @Override
        public void output(PrintStream os, Machine.McFunction f) {
            transfer_output(os);
            if (getOffset().getType() == Machine.Operand.Type.Immediate) {
                int shift = (this.shift.shiftType == Arm.ShiftType.None) ? 0 : this.shift.shift;
                int offset = this.getOffset().value << shift;
                if (offset != 0) {
                    os.println("\tvstr" + cond + ".32\t" + getData() + ",\t[" + getAddr() + ",\t#" + offset + "]");
                } else {
                    os.println("\tvstr" + cond + ".32\t" + getData() + ",\t[" + getAddr() + "]");
                }
            } else {
                if (this.shift.shiftType == Arm.ShiftType.None) {
                    os.println("\tvstr" + cond + ".32\t" + getData() + ",\t[" + getAddr() + ",\t" + getOffset() + "]");
                } else {
                    os.println("\tvstr" + cond + ".32\t" + getData() + ",\t[" + getAddr() + ",\t" + getOffset() + ",\tLSL #" + this.shift.shift + "]");
                }
            }
        }

        @Override
        public String toString() {
            return tag.toString() + cond + '\t' + getData() + ",\t[" + getAddr() + ",\t" + getOffset() +
                    (shift.shiftType == Arm.ShiftType.None ? "" : ("\t," + shift)) + "]";
        }
    }

    public static class Mov extends V {

        public Mov(Operand dst, Operand src, Machine.Block insertAtEnd) {
            super(VMov, insertAtEnd);
            defOpds.add(dst);
            useOpds.add(src);
        }

        public Mov(Operand dst, Operand src, MachineInst insertBefore) {
            super(VMov, insertBefore);
            defOpds.add(dst);
            useOpds.add(src);
        }

        public Machine.Operand getDst() {
            return defOpds.get(0);
        }

        public Machine.Operand getSrc() {
            return useOpds.get(0);
        }

        public void setSrc(Machine.Operand offset_opd) {
            assert offset_opd != null;
            useOpds.set(0, offset_opd);
        }

        public void setDst(Machine.Operand dst) {
            assert dst != null;
            defOpds.set(0, dst);
        }

        @Override
        public void output(PrintStream os, Machine.McFunction f) {
            transfer_output(os);
            assert cond == Arm.Cond.Any;
            os.println("\tvmov" + getMvSuffixTypeSimply(getDst()) + "\t" + getDst() + ",\t" + getSrc());
        }

        @Override
        public String toString() {
            assert cond == Arm.Cond.Any;
            return "vmov\t" + getMvSuffixTypeSimply(getDst()) + "\t" + getDst() + ",\t" + getSrc();
        }

        public boolean directColor() {
            return getDst().need_F_Color() && getSrc().need_F_Color() && cond == Arm.Cond.Any && shift.shiftType == Arm.ShiftType.None;
        }
    }

    public enum CvtType {
        // LIRI,
        f2i,
        i2f,
        // LFRF,
        None
    }

    /**
     * 里面天然是两条指令vcvt, vmov
     */
    public static class Cvt extends V {
        CvtType cvtType = CvtType.None;

        public Cvt(CvtType cvtType, Operand dst, Operand src, Machine.Block insertAtEnd) {
            super(Tag.VCvt, insertAtEnd);
            this.cvtType = cvtType;
            defOpds.add(dst);
            useOpds.add(src);
        }

        public Machine.Operand getDst() {
            return defOpds.get(0);
        }

        public Machine.Operand getSrc() {
            return useOpds.get(0);
        }

        String old = null;

        @Override
        public String toString() {
            StringBuilder stb = new StringBuilder();
            switch (cvtType){
                case f2i -> stb.append("\tvcvt.s32.f32\t" + getDst() + ",\t" + getSrc());
                case i2f -> stb.append("\tvcvt.f32.s32\t" + getDst() + ",\t" + getSrc());
                // TODO: for debug
                default -> {throw new AssertionError("Wrong cvtType");}
            }
            return stb + "\n";
        }

        @Override
        public void output(PrintStream os, Machine.McFunction f) {
            transfer_output(os);
            switch (cvtType){
                case f2i -> os.println("\tvcvt.s32.f32\t" + getDst() + ",\t" + getSrc());
                case i2f -> os.println("\tvcvt.f32.s32\t" + getDst() + ",\t" + getSrc());
                // TODO: for debug
                default -> {throw new AssertionError("Wrong cvtType");}
            }
        }
    }

    public static class Binary extends V {
        public Binary(Tag tag, Machine.Operand dOpd, Machine.Operand lOpd, Machine.Operand rOpd, Machine.Block insertAtEnd) {
            super(tag, insertAtEnd);
            defOpds.add(dOpd);
            useOpds.add(lOpd);
            useOpds.add(rOpd);
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

        @Override
        public void output(PrintStream os, Machine.McFunction f) {
            transfer_output(os);
            String tag_str = "\t" + switch (tag) {
                case FAdd -> "vadd.f32";
                case FSub -> "vsub.f32";
                case FDiv -> "vdiv.f32";
                case FMul -> "vmul.f32";
                default -> throw new AssertionError("Wrong FBino of " + tag);
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
            // (isNeedFix() ? getROpd().value + (this.getCallee() == null ? this.mb.mcFunc.getTotalStackSize() : this.getCallee().getTotalStackSize()) : getROpd());
        }
    }

    public static class Neg extends V {

        public Neg(Machine.Operand dst, Machine.Operand src, Machine.Block insertAtEnd) {
            super(Tag.VNeg, insertAtEnd);
            defOpds.add(dst);
            useOpds.add(src);
        }

        public Machine.Operand getDst() {
            return defOpds.get(0);
        }

        public Machine.Operand getSrc() {
            return useOpds.get(0);
        }

        @Override
        public void output(PrintStream os, Machine.McFunction f) {
            os.println("\tvneg.f32" + getDst() + ",\t" + getSrc());
        }

        @Override
        public String toString() {
            return "vneg.f32" + getDst() + ",\t" + getSrc();
        }
    }

    public static class Cmp extends V implements MachineInst.Compare {
        public Cmp(Machine.Operand lOpd, Machine.Operand rOpd, Machine.Block insertAtEnd) {
            super(VCmp, insertAtEnd);
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
            transfer_output(os);
            os.println("\tvcmpe.f32\t" + getLOpd() + ",\t" + getROpd());
            os.println("\tvmrs\tAPSR_nzcv,\tFPSCR");
        }

        @Override
        public String toString() {
            return tag.toString() + '\t' + getLOpd() + ",\t" + getROpd();
        }
    }

    public static class Ret extends V {
        public Ret(Arm.Reg s0, Machine.Block curMB) {
            super(VRet, curMB);
            useOpds.add(s0);
        }


        @Override
        public void output(PrintStream os, Machine.McFunction mf) {
            os.println("\tbx\tlr");
            // pop_output(os, mf);
        }

        @Override
        public String toString() {
            return tag.toString() + (useOpds.size() > 0 ? useOpds.get(0) : "");
        }
    }
}
