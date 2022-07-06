package lir;

public class Arm {
    public enum GPRs {
        // args and return value (caller saved)
        r0("r0"), r1("r1"), r2("r2"), r3("r3"),
        // local variables (callee saved)
        r4("r4"), r5("r5"), r6("r6"), r7("r7"),
        r8("r8"), r9("r9"), r10("r10"), r11("r11"), r12("r12"),
        // some aliases
        // fp("r11"),  // frame pointer (omitted), allocatable
        // ip("r12"),  // ipc scratch register, used in some instructions (caller saved)
        sp("r13"),  // stack pointer
        lr("r14"),  // link register (caller saved) BL指令调用时存放返回地址
        pc("r15"),  // program counter
        cspr("r16");

        GPRs(String rName) {

        }
    }

    public enum FPRs{
        // args and return value (caller saved)
        r0("r0"), r1("r1"), r2("r2"), r3("r3"),
        // local variables (callee saved)
        r4("r4"), r5("r5"), r6("r6"), r7("r7"),
        r8("r8"), r9("r9"), r10("r10"), r11("r11"), r12("r12"),
        // some aliases
        // fp("r11"),  // frame pointer (omitted), allocatable
        // ip("r12"),  // ipc scratch register, used in some instructions (caller saved)
        sp("r13"),  // stack pointer
        lr("r14"),  // link register (caller saved) BL指令调用时存放返回地址
        pc("r15"),  // program counter
        cspr("r16");

        FPRs(String fpName) {
        }
    }

    public enum Cond {
        Any("!Any"),
        Eq("eq"),
        Ne("ne"),
        Ge("ge"),
        Gt("gt"),
        Le("le"),
        Lt("lt");

        Cond(String cond) {
        }
    }

    public enum Shift {
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

        Shift(String shift) {
        }
    }
}
