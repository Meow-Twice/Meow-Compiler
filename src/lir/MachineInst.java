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
        public enum Op {
            Add("add"),
            Sub("sub"),
            Rsb("rsb"),
            Mul("mul"),
            Div("sdiv"),
            Mod("mod"),
            Lt("lt"),
            Le("le"),
            Ge("ge"),
            Gt("gt"),
            Eq("eq"),
            Ne("ne"),
            And("and"),
            Or("or"),
            ;

            Op(String op) {
            }
        }

        Machine.Operand lOpd;
        Machine.Operand rOpd;
        Machine.Operand dOpd;
        Arm.Shift shift;

        public Binary(Op op, Machine.Block insertAtEnd){

        }

    }
}
