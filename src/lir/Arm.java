package lir;

import frontend.semantic.Initial;
import mir.GlobalVal;
import mir.type.DataType;
import mir.type.Type.*;
import mir.type.Type;

import static lir.Machine.Operand.Type.*;
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
    public static class Reg extends Machine.Operand {
        // DataType dataType;
        Regs.FPRs fpr;
        Regs.GPRs gpr;


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

        private static final Reg[] gprPool = new Reg[Regs.GPRs.values().length];
        private static final Reg[] fprPool = new Reg[Regs.FPRs.values().length];

        static {
            for (Regs.GPRs gpr : Regs.GPRs.values()) {
                gprPool[gpr.ordinal()] = new Reg(I32, gpr);
                if (gpr == Regs.GPRs.sp) {
                    gprPool[gpr.ordinal()].type = Allocated;
                }
            }
            for (Regs.FPRs fpr : Regs.FPRs.values()) {
                fprPool[fpr.ordinal()] = new Reg(F32, fpr);
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

        @Override
        public Regs getReg() {
            return reg;
        }
    }

    public static class Glob extends Machine.Operand {
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
        }
    }

    public static class Shift {
        public static final Shift NONE_SHIFT = new Shift();
        public ShiftType shiftType;
        public int shift;

        Shift() {
            shift = 0;
            shiftType = ShiftType.None;
        }

        public Shift(ShiftType shiftType, int shift) {
            this.shiftType = shiftType;
            this.shift = shift;
        }

        public int getShift() {
            return shift;
        }

        public String toString() {
            return switch (shiftType) {
                case Asr -> "asr #" + this.shift;
                case Lsl -> "lsl #" + this.shift;
                case Lsr -> "lsr #" + this.shift;
                case Ror -> "ror #" + this.shift;
                case Rrx -> "rrx #" + this.shift;
                default -> "";
            };
        }

        // @Override
        public boolean equals(Shift oth) {
            return shift == oth.shift && shiftType == oth.shiftType;
        }

        public boolean hasShift() {
            return shiftType != ShiftType.None;
        }
    }
}
