package lir;

import mir.Constant;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;

import static backend.CodeGen.needFPU;
import static backend.CodeGen.sParamCnt;
import static lir.MachineInst.Tag.*;
import static lir.MC.Operand;
import static mir.type.DataType.F32;
import static mir.type.DataType.I32;

public class V extends MachineInst {

    protected String getMvSuffixTypeSimply(Operand dst) {
        return switch (dst.dataType) {
            case F32 -> ".f32";
            case I32 -> "";
            case I1 -> throw new AssertionError("WRONG dst of vmov: i1\t" + dst);
        };
    }

    public static final ArrayList<Constant.ConstantFloat> CONST_FLOAT_POLL = new ArrayList<>();

    public V(Tag tag, MC.Block insertAtEnd) {
        super(tag, insertAtEnd);
    }

    public V(Tag tag, MachineInst insertBefore) {
        super(tag, insertBefore);
    }

    public V(MachineInst insertAfter, Tag tag) {
        super(insertAfter, tag);
    }

    public static class Ldr extends V implements MachineMemInst {

        /**
         * addr可能是常数立即数的地址
         * 参数的load, 正常的Load
         */
        public Ldr(MC.Operand data, MC.Operand addr, MC.Block insertAtEnd) {
            super(VLdr, insertAtEnd);
            defOpds.add(data);
            useOpds.add(addr);
        }

        /**
         * 浮点寄存器分配插入的load
         *
         * @param data
         * @param addr
         * @param offset
         * @param insertBefore
         */
        public Ldr(MC.Operand data, MC.Operand addr, MC.Operand offset, MachineInst insertBefore) {
            super(VLdr, insertBefore);
            defOpds.add(data);
            useOpds.add(addr);
            useOpds.add(offset);
        }

        /**
         * 浮点寄存器分配插入的load
         *
         * @param svr
         * @param dstAddr
         * @param firstUse
         */
        public Ldr(Operand svr, Operand dstAddr, MachineInst firstUse) {
            super(VLdr, firstUse);
            defOpds.add(svr);
            useOpds.add(dstAddr);
        }

        public MC.Operand getData() {
            return defOpds.get(0);
        }

        public MC.Operand getAddr() {
            return useOpds.get(0);
        }

        public MC.Operand getOffset() {
            if (useOpds.size() < 2) return new Operand(I32, 0);
            return useOpds.get(1);
        }

        @Override
        public void setAddr(Operand lOpd) {
            useOpds.set(0, lOpd);
        }

        public void setOffSet(Operand offSet) {
            if (useOpds.size() < 2) useOpds.add(offSet);
            useOpds.set(1, offSet);
        }

        @Override
        public void output(PrintStream os, MC.McFunction f) {
            os.println("\t" + this);
        }

        @Override
        public String toString() {
            return tag.toString() + cond.toString() + '\t' + getData() + ",\t[" + getAddr() +
                    (getOffset().equals(Operand.I_ZERO) ? "" : ",\t" + getOffset()) +
                    (shift.shiftType == Arm.ShiftType.None ? "" : ("\t," + shift)) + "\t]";
        }
    }

    public static class Str extends V implements MachineMemInst {

        public Str(MC.Operand data, MC.Operand addr, MC.Operand offset, MC.Block insertAtEnd) {
            super(VStr, insertAtEnd);
            useOpds.add(data);
            useOpds.add(addr);
            useOpds.add(offset);
        }

        public Str(MC.Operand data, MC.Operand addr, MC.Block insertAtEnd) {
            super(VStr, insertAtEnd);
            useOpds.add(data);
            useOpds.add(addr);
        }

        /**
         * 寄存器分配时专用
         *
         * @param insertAfter
         * @param data
         * @param addr
         */
        public Str(MachineInst insertAfter, MC.Operand data, MC.Operand addr, MC.Operand offset) {
            super(insertAfter, VStr);
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
        public Str(MachineInst insertAfter, Operand data, Operand addr) {
            super(insertAfter, VStr);
            useOpds.add(data);
            useOpds.add(addr);
        }

        public MC.Operand getData() {
            return useOpds.get(0);
        }

        public MC.Operand getAddr() {
            return useOpds.get(1);
        }

        public MC.Operand getOffset() {
            if (useOpds.size() < 3) return null;
            return useOpds.get(2);
        }

        @Override
        public void setAddr(Operand lOpd) {

        }

        @Override
        public void setOffSet(Operand rOpd) {
            if (useOpds.size() < 3) useOpds.add(rOpd);
            else useOpds.set(2, rOpd);
        }

        @Override
        public void output(PrintStream os, MC.McFunction f) {
            if (getOffset() == null) {
                os.println("\tvstr" + cond + ".32\t" + getData() + ",\t[" + getAddr() + "]");
            } else if (getOffset().type == MC.Operand.Type.Immediate) {
                int shift = (this.shift.shiftType == Arm.ShiftType.None) ? 0 : this.shift.shiftOpd.getValue();
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
                    os.println("\tvstr" + cond + ".32\t" + getData() + ",\t[" + getAddr() + ",\t" + getOffset() + ",\tLSL #" + this.shift.shiftOpd + "]");
                }
            }
        }

        @Override
        public String toString() {
            StringBuilder stb = new StringBuilder();
            if (getOffset() == null) {
                stb.append("vstr").append(cond).append(".32\t").append(getData()).append(",\t[").append(getAddr()).append("]");
            } else if (getOffset().type == MC.Operand.Type.Immediate) {
                int shift = (this.shift.shiftType == Arm.ShiftType.None) ? 0 : this.shift.shiftOpd.getValue();
                int offset = this.getOffset().value << shift;
                if (offset != 0) {
                    stb.append("vstr").append(cond).append(".32\t").append(getData()).append(",\t[").append(getAddr()).append(",\t#").append(offset).append("]");
                } else {
                    stb.append("vstr").append(cond).append(".32\t").append(getData()).append(",\t[").append(getAddr()).append("]");
                }
            } else {
                if (this.shift.shiftType == Arm.ShiftType.None) {
                    stb.append("vstr").append(cond).append(".32\t").append(getData()).append(",\t[").append(getAddr()).append(",\t").append(getOffset()).append("]");
                } else {
                    stb.append("vstr").append(cond).append(".32\t").append(getData()).append(",\t[").append(getAddr()).append(",\t").append(getOffset()).append(",\tLSL #").append(this.shift.shiftOpd).append("]");
                }
            }
            return stb.toString();
        }
    }

    public static class Mov extends V implements MachineMove {

        public Mov(Operand dst, Operand src, MC.Block insertAtEnd) {
            super(VMov, insertAtEnd);
            defOpds.add(dst);
            useOpds.add(src);
        }

        public Mov(Operand dst, Operand src, MachineInst insertBefore) {
            super(VMov, insertBefore);
            defOpds.add(dst);
            useOpds.add(src);
        }

        public MC.Operand getDst() {
            return defOpds.get(0);
        }

        public MC.Operand getSrc() {
            return useOpds.get(0);
        }

        public void setSrc(MC.Operand offset_opd) {
            assert offset_opd != null;
            useOpds.set(0, offset_opd);
        }

        public void setDst(MC.Operand dst) {
            assert dst != null;
            defOpds.set(0, dst);
        }

        @Override
        public void output(PrintStream os, MC.McFunction f) {
            assert cond == Arm.Cond.Any;
            os.println("\tvmov" + getMvSuffixTypeSimply(getDst()) + "\t" + getDst() + ",\t" + getSrc());
        }

        @Override
        public String toString() {
            assert cond == Arm.Cond.Any;
            return "vmov" + getMvSuffixTypeSimply(getDst()) + "\t" + getDst() + ",\t" + getSrc();
        }

        public boolean directColor() {
            return getDst().needColor(F32) && getSrc().needColor(F32) && cond == Arm.Cond.Any && shift.shiftType == Arm.ShiftType.None;
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

        public Cvt(CvtType cvtType, Operand dst, Operand src, MC.Block insertAtEnd) {
            super(Tag.VCvt, insertAtEnd);
            this.cvtType = cvtType;
            defOpds.add(dst);
            useOpds.add(src);
        }

        public MC.Operand getDst() {
            return defOpds.get(0);
        }

        public MC.Operand getSrc() {
            return useOpds.get(0);
        }

        String old = null;

        @Override
        public String toString() {
            StringBuilder stb = new StringBuilder();
            switch (cvtType) {
                case f2i -> stb.append("vcvt.s32.f32\t").append(getDst()).append(",\t").append(getSrc());
                case i2f -> stb.append("vcvt.f32.s32\t").append(getDst()).append(",\t").append(getSrc());
                // TODO: for debug
                default -> {
                    throw new AssertionError("Wrong cvtType");
                }
            }
            return stb.toString();
        }

        @Override
        public void output(PrintStream os, MC.McFunction f) {
            switch (cvtType) {
                case f2i -> os.println("\tvcvt.s32.f32\t" + getDst() + ",\t" + getSrc());
                case i2f -> os.println("\tvcvt.f32.s32\t" + getDst() + ",\t" + getSrc());
                // TODO: for debug
                default -> {
                    throw new AssertionError("Wrong cvtType");
                }
            }
        }
    }

    public static class Binary extends V {
        public Binary(Tag tag, MC.Operand dOpd, MC.Operand lOpd, MC.Operand rOpd, MC.Block insertAtEnd) {
            super(tag, insertAtEnd);
            defOpds.add(dOpd);
            useOpds.add(lOpd);
            useOpds.add(rOpd);
        }

        public MC.Operand getDst() {
            return defOpds.get(0);
        }

        public MC.Operand getLOpd() {
            return useOpds.get(0);
        }

        public MC.Operand getROpd() {
            return useOpds.get(1);
        }

        @Override
        public void output(PrintStream os, MC.McFunction f) {
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
            StringBuilder stb = new StringBuilder();

            stb.append(switch (tag) {
                case FAdd -> "vadd.f32";
                case FSub -> "vsub.f32";
                case FDiv -> "vdiv.f32";
                case FMul -> "vmul.f32";
                default -> throw new AssertionError("Wrong FBino of " + tag);
            });
            stb.append("\t").append(getDst()).append(",\t").append(getLOpd()).append(",\t").append(getROpd());
            if (shift.shiftType != Arm.ShiftType.None) {
                stb.append(",\t").append(shift);
            }
            return stb.toString();
        }
    }

    public static class Neg extends V {

        public Neg(MC.Operand dst, MC.Operand src, MC.Block insertAtEnd) {
            super(Tag.VNeg, insertAtEnd);
            defOpds.add(dst);
            useOpds.add(src);
        }

        public MC.Operand getDst() {
            return defOpds.get(0);
        }

        public MC.Operand getSrc() {
            return useOpds.get(0);
        }

        @Override
        public void output(PrintStream os, MC.McFunction f) {
            os.println("\tvneg.f32" + getDst() + ",\t" + getSrc());
        }

        @Override
        public String toString() {
            return "vneg.f32" + getDst() + ",\t" + getSrc();
        }
    }

    public static class Cmp extends V implements MachineInst.Compare {
        public Cmp(Arm.Cond cond, MC.Operand lOpd, MC.Operand rOpd, MC.Block insertAtEnd) {
            super(VCmp, insertAtEnd);
            this.cond = cond;
            useOpds.add(lOpd);
            useOpds.add(rOpd);
        }


        public MC.Operand getLOpd() {
            return useOpds.get(0);
        }

        public MC.Operand getROpd() {
            return useOpds.get(1);
        }

        @Override
        public void output(PrintStream os, MC.McFunction f) {
            os.println("\tvcmpe.f32\t" + getLOpd() + ",\t" + getROpd());
            os.println("\tvmrs\tAPSR_nzcv,\tFPSCR");
        }

        @Override
        public String toString() {
            return "vcmpe.f32\t" + getLOpd() + ",\t" + getROpd() + "\n\tvmrs\tAPSR_nzcv,\tFPSCR";
        }

        @Override
        public void setCond(Arm.Cond cond) {
            this.cond = cond;
        }
    }

    public static class Ret extends V {
        MC.McFunction savedRegsMf;
        public Ret(MC.McFunction mf, Arm.Reg s0, MC.Block curMB) {
            super(VRet, curMB);
            savedRegsMf = mf;
            useOpds.add(s0);
        }


        @Override
        public void output(PrintStream os, MC.McFunction mf) {
            os.println("\tbx\tlr");
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (savedRegsMf.mFunc.isExternal) {
                // throw new AssertionError("pop in external func");
                // return "\tpop\t{r0,r1,r2,r3}";
                sb.append("pop\t{r2,r3}");
            } else {
                // StringBuilder sb = new StringBuilder();
                if (savedRegsMf.usedCalleeSavedGPRs.size() > 0) {
                    sb.append("pop\t{");
                    Iterator<Arm.Regs.GPRs> gprIter = savedRegsMf.usedCalleeSavedGPRs.iterator();
                    sb.append(gprIter.next());
                    while (gprIter.hasNext()) {
                        Arm.Regs.GPRs gpr = gprIter.next();
                        sb.append(",").append(gpr);
                    }
                    sb.append("}\n");
                }
            }
            if (needFPU) {
                if (savedRegsMf.mFunc.isExternal) {
                    sb.append(String.format("\tvpop\t{s2-s%d}", sParamCnt - 1));
                } else {
                    if (savedRegsMf.usedCalleeSavedFPRs.size() > 0) {
                        int fprNum = Arm.Regs.FPRs.values().length;
                        boolean[] fprBit = new boolean[fprNum];
                        for (Arm.Regs.FPRs fpr : savedRegsMf.usedCalleeSavedFPRs) {
                            fprBit[fpr.ordinal()] = true;
                        }
                        int end = fprNum - 1;
                        while (end > -1) {
                            while (end > -1 && !fprBit[end])
                                end--;
                            if (end == -1)
                                break;
                            int start = end;
                            while (start > -1 && fprBit[start])
                                start--;
                            start++;
                            if (start == end) {
                                throw new AssertionError("illegal vpop");
                                // sb.append(String.format("\tvpop\t{s%d}%n", end));
                            } else if (start < end) {
                                if (end - start > 15) {
                                    start = end - 15;
                                }
                                sb.append(String.format("\tvpop\t{s%d-s%d}%n", start, end));
                            }
                            end = start - 1;
                        }
                    }
                }
            }
            sb.append("\n\tbx\tlr");
            return sb.toString();
        }
    }
}
