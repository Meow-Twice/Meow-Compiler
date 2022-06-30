package lir;

public class MachineInst {
    public enum InstTag {
        // Binary
        LongMul,
        FMA,
        Mv,
        Branch,
        Jump,
        Return,  // Control flow
        Load,
        Store,  // Memory
        Compare,
        Call,
        Global,
        Comment,  // for printing comments
    }

    public static class Binary extends MachineInst {
        Machine.Operand lOpd;
        Machine.Operand rOpd;
        Machine.Operand dOpd;
        Arm.Shift shift;

        public enum Op {
            Add, Sub, Rsb, Mul, Div, Mod, Lt, Le, Ge, Gt, Eq, Ne, And, Or,
        }

    }
}
