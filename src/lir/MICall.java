package lir;

import lir.Machine.*;

import lir.Arm.Reg;

import static lir.Arm.Regs.GPRs.lr;

import java.io.PrintStream;

import static lir.Machine.Operand.Type.PreColored;

public class MICall extends MachineInst {
    McFunction mcFunction;

    public MICall(Machine.Block insertAtEnd) {
        super(Tag.Call, insertAtEnd);
        genDefUse();
    }

    @Override
    public void genDefUse() {
        // TODO for xry: 到底是new还是get单例
        // 调用者保存
        for (int i = 0; i < mcFunction.params.size(); i++) {
            useOpds.add(new Operand(Reg.getR(i)));
        }
        for (int i = 0; i < 4; i++) {
            defOpds.add(new Operand(Reg.getR(i)));
        }
        defOpds.add(new Operand(Reg.getR(lr.ordinal())));
        // TODO: 不确定浮点怎么存,不确定到底存哪些

    }

    public void output(PrintStream os) {
        os.println("blx\t" + mcFunction.func_name);
    }
}
