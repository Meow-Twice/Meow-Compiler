package lir;

import lir.Machine;
import lir.MachineInst;
import mir.type.DataType;

import javax.xml.crypto.Data;
import java.util.ArrayList;


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

    public static class Reg {
        DataType dataType;
        Regs.FPRs fpr;
        Regs.GPRs gpr;


        public Reg(DataType dataType, Regs.FPRs fpr) {
            this.dataType = dataType;
            this.fpr = fpr;
        }

        public Reg(DataType dataType, Regs.GPRs gpr) {
            this.dataType = dataType;
            this.gpr = gpr;
        }

        private static final ArrayList<Reg> gprPool = new ArrayList<>();
        private static final ArrayList<Reg> fprPool = new ArrayList<>();

        static {
            for (int i = 0; i <= 12; i++) {
                gprPool.add(new Reg(DataType.I32, Regs.GPRs.valueOf("r" + i)));
                fprPool.add(new Reg(DataType.F32, Regs.FPRs.valueOf("s" + i)));
            }
        }

        public static Reg getR(int i) {
            return gprPool.get(i);
        }

        public static Reg getR(Regs.GPRs r) {
            return gprPool.get(r.ordinal());
        }

        public static Reg getS(int i) {
            return fprPool.get(i);
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
        Any("!Any");

        Cond(String cond) {
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
