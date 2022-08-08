package lir;

import frontend.semantic.Initial;
import mir.GlobalVal;
import mir.type.DataType;

import static lir.MC.Operand.Type.*;
import static mir.type.DataType.F32;
import static mir.type.DataType.I32;


public class Arm {

    public interface Regs {

        public enum GPRs implements Regs {
            // args and return value (caller saved)
            r0("r0"), r1("r1"), r2("r2"), r3("r3"),
            // local variables (callee saved)
            r4("r4"), r5("r5"), r6("r6"), r7("r7"),
            r8("r8"), r9("r9"), r10("r10"), r11("r11"), r12("r12"),
            // some aliases
            // fp("r11"),  // frame pointer (omitted), allocatable
            // ip("r12"),  // ipc scratch register, used in some instructions (caller saved)
            sp("sp"),  // stack pointer
            lr("lr"),  // link register (caller saved) BL指令调用时存放返回地址
            pc("pc"),  // program counter
            cspr("r16");

            GPRs(String rName) {
            }
        }

        public enum FPRs implements Regs {
            // args and return value (caller saved)
            s0("s0"), s1("s1"), s2("s2"), s3("s3"),
            // local vasiables (callee saved)
            s4("s4"), s5("s5"), s6("s6"), s7("s7"),
            s8("s8"), s9("s9"), s10("s10"), s11("s11"),
            s12("s12"), s13("s13"), s14("s14"), s15("s15"),
            s16("s16"), s17("s17"), s18("s18"), s19("s19"),
            s20("s20"), s21("s21"), s22("s22"), s23("s23"),
            s24("s24"), s25("s25"), s26("s26"), s27("s27"),
            s28("s28"), s29("s29"), s30("s30"), s31("s31");

            FPRs(String fpName) {
            }
        }
    }

    /**
     * 只供预着色使用
     */
    public static class Reg extends MC.Operand {
        // DataType dataType;
        public Regs.FPRs fpr;
        public Regs.GPRs gpr;


        public Reg(DataType dataType, Regs.FPRs fpr) {
            super(PreColored, dataType);
            this.dataType = dataType;
            this.fpr = fpr;
            this.value = fpr.ordinal();
            reg = fpr;
        }

        public Reg(DataType dataType, Regs.GPRs gpr) {
            super(PreColored, dataType);
            this.dataType = dataType;
            this.gpr = gpr;
            this.value = gpr.ordinal();
            reg = gpr;
        }

        public Reg(Regs.GPRs gpr) {
            super(gpr);
            this.dataType = I32;
            this.gpr = gpr;
            this.value = gpr.ordinal();
            reg = gpr;
        }

        public Reg(Regs.FPRs fpr) {
            super(fpr);
            this.dataType = F32;
            this.fpr = fpr;
            this.value = fpr.ordinal();
            reg = fpr;
        }

        private static final Reg[] gprPool = new Reg[Regs.GPRs.values().length];
        private static final Reg[] fprPool = new Reg[Regs.FPRs.values().length];

        private static final Reg[] allocGprPool = new Reg[Regs.GPRs.values().length];
        private static final Reg[] allocFprPool = new Reg[Regs.FPRs.values().length];

        static {
            for (Regs.GPRs gpr : Regs.GPRs.values()) {
                gprPool[gpr.ordinal()] = new Reg(I32, gpr);
                allocGprPool[gpr.ordinal()] = new Reg(gpr);
                if (gpr == Regs.GPRs.sp) {
                    // TODO
                    gprPool[gpr.ordinal()] = allocGprPool[gpr.ordinal()];
                }
            }
            for (Regs.FPRs fpr : Regs.FPRs.values()) {
                fprPool[fpr.ordinal()] = new Reg(F32, fpr);
                allocFprPool[fpr.ordinal()] = new Reg(fpr);
            }
        }

        public static Reg getR(int i) {
            return gprPool[i];
        }

        public static Reg getR(Regs.GPRs r) {
            return gprPool[r.ordinal()];
        }

        public static Reg getS(int i) {
            return fprPool[i];
        }

        public static Reg getS(Regs.FPRs s) {
            return fprPool[s.ordinal()];
        }

        public static Reg[] getGPRPool() {
            return gprPool;
        }

        public static Reg[] getFPRPool() {
            return fprPool;
        }

        public static MC.Operand getRSReg(Regs color) {
            if (color instanceof Regs.GPRs) {
                return allocGprPool[((Regs.GPRs) color).ordinal()];
            } else if (color instanceof Regs.FPRs) {
                return allocFprPool[((Regs.FPRs) color).ordinal()];
            } else {
                System.exit(101);
                throw new AssertionError("try to get reg of " + color);
            }
        }

        @Override
        public Regs getReg() {
            return reg;
        }
        // for debug
        // @Override
        // public String toString() {
        //
        //     return "(" + type.name() + ")" + switch (dataType) {
        //         case I32 -> "r" + value;
        //         case F32 -> "s" + value;
        //         default -> {
        //             throw new AssertionError("");
        //         }
        //     };
        // }
    }

    public static class Glob extends MC.Operand {
        public String name;
        public GlobalVal.GlobalValue globalValue;
        public Initial init;

        public Glob(GlobalVal.GlobalValue glob) {
            super(Immediate);
            name = glob.name;
            this.init = glob.initial;
            globalValue = glob;
        }

        public Glob(String name) {
            super(Immediate);
            this.name = name;
            // this.init = glob.initial;
            // globalValue = glob;
        }

        // /**
        //  * 浮点的全局变量
        //  * @param glob
        //  * @param dataType
        //  */
        // public Glob(GlobalVal.GlobalValue glob, DataType dataType) {
        //     super(FConst);
        //     name = glob.name;
        //     this.init = glob.initial;
        //     globalValue = glob;
        // }

        public String getGlob() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }

        public GlobalVal.GlobalValue getGlobalValue() {
            return globalValue;
        }

        public Initial getInit() {
            return init;
        }
    }

    // https://developer.arm.com/documentation/dui0489/i/arm-and-thumb-instructions/condition-codes?lang=en
    public enum Cond {
        // TODO: 保证Arm.Cond与Icmp.Op, Fcmp.Op的顺序相同!!!!!!!!
        Eq("eq"),
        Ne("ne"),
        Gt("gt"),
        Ge("ge"),
        Lt("lt"),
        Le("le"),
        Hi("hi"), // >
        Pl("pl"), // >=
        Any("");

        Cond(String cond) {
            name = cond;
        }

        String name;

        @Override
        public String toString() {
            return name;
        }
    }

    public enum ShiftType {
        // no shifting
        None(""),
        // arithmetic right
        Asr("asr"),
        // logic left
        Lsl("lsl"),
        // logic right
        Lsr("lsr"),
        // rotate right
        Ror("ror"),
        // rotate right one bit with extend
        Rrx("rrx");

        ShiftType(String shift) {
            name = shift;
        }

        String name;


        @Override
        public String toString() {
            return name;
        }
    }

    public enum OppositeCond {
        // TODO: 保证Arm.Cond与Icmp.Op, Fcmp.Op的顺序相同!!!!!!!!
        Ne("ne"),
        Eq("eq"),
        Lt("lt"),
        Le("le"),
        Gt("gt"),
        Ge("ge"),
        Any("!Any");

        OppositeCond(String cond) {
        }
    }

    public static class Shift {
        Shift() {
            shiftOpd = new MC.Operand(I32, 0);
            shiftType = ShiftType.None;
        }

        public ShiftType shiftType;
        public static final Shift NONE_SHIFT = new Shift();

        public MC.Operand shiftOpd;
        // public MC.Operand shiftReg = null;
        public Shift(ShiftType shiftType, MC.Operand shiftOpd) {
            this.shiftType = shiftType;
            // this.shiftReg = shiftOpd;
            this.shiftOpd = shiftOpd;
        }

        public Shift(ShiftType shiftType, int shiftOpd) {
            this.shiftType = shiftType;
            this.shiftOpd = new MC.Operand(I32, shiftOpd);
        }

        public MC.Operand getShiftOpd() {
            return shiftOpd;
        }

        public String toString() {            // if (shiftReg != null)
            //     return switch (shiftType) {
            //         case Asr -> "asr #" + this.shiftReg;
            //         case Lsl -> "lsl #" + this.shiftReg;
            //         case Lsr -> "lsr #" + this.shiftReg;
            //         case Ror -> "ror #" + this.shiftReg;
            //         case Rrx -> "rrx #" + this.shiftReg;
            //         default -> "";
            //     };
            // else
            return switch (shiftType) {
                case Asr -> "asr " + this.shiftOpd;
                case Lsl -> "lsl " + this.shiftOpd;
                case Lsr -> "lsr " + this.shiftOpd;
                case Ror -> "ror " + this.shiftOpd;
                case Rrx -> "rrx " + this.shiftOpd;
                default -> "";
            };
        }

        // @Override
        public boolean equals(Shift oth) {
            return shiftType == oth.shiftType && shiftOpd.equals(oth.shiftOpd);
        }

        public boolean hasShift() {
            return shiftType != ShiftType.None;
        }

        public boolean noShift() {
            return shiftType == ShiftType.None;
        }
    }
}
