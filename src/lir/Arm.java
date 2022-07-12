package lir;

import static mir.type.DataType.I32;
import static mir.type.DataType.F32;
import static lir.Machine.Operand.Type.*;

import mir.type.DataType;


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
            s12("s12"), s13("s13"), s14("s14");
            //TODO for yyf:这个不知道能用多少

            FPRs(String fpName) {
            }
        }
    }

    /**
     * 只供预着色使用
     */
    public static class Reg extends Machine.Operand {
        DataType dataType;
        Regs.FPRs fpr;
        Regs.GPRs gpr;


        public Reg(DataType dataType, Regs.FPRs fpr) {
            super(PreColored, dataType);
            this.dataType = dataType;
            this.fpr = fpr;
            this.value = fpr.ordinal();
        }

        public Reg(DataType dataType, Regs.GPRs gpr) {
            super(PreColored, dataType);
            this.dataType = dataType;
            this.gpr = gpr;
            this.value = gpr.ordinal();
        }

        private static final Reg[] gprPool = new Reg[Regs.GPRs.values().length];
        private static final Reg[] fprPool = new Reg[Regs.FPRs.values().length];

        static {
            for(Regs.GPRs gpr : Regs.GPRs.values()){
                gprPool[gpr.ordinal()] = new Reg(I32, gpr);
            }
            for(Regs.FPRs fpr : Regs.FPRs.values()){
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


    }

    public enum Cond {
        // TODO: 保证Arm.Cond与Icmp.Op, Fcmp.Op的顺序相同!!!!!!!!
        Eq("eq"),
        Ne("ne"),
        Ge("ge"),
        Gt("gt"),
        Le("le"),
        Lt("lt"),
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
        None("!None"),
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
        ShiftType shiftType;
        int shift;

        Shift() {
            shift = 0;
            shiftType = ShiftType.None;
        }

        public Shift(ShiftType shiftType, int shift) {
            this.shiftType = shiftType;
            this.shift = shift;
        }

        public String toString() {
            String op = switch (shiftType) {
                case Asr -> "asr";
                case Lsl -> "lsl";
                case Lsr -> "lsr";
                case Ror -> "ror";
                case Rrx -> "rrx";
                default -> null;
            };
            return op + " #" + this.shift;
        }
    }
}
