package lir;

import lir.Machine.*;

import lir.Arm.Reg;

import java.io.PrintStream;

import static lir.Arm.Regs.GPRs.*;
import static lir.Machine.Operand.Type.PreColored;

public class MICall extends MachineInst {
    McFunction mcFunction;

    public MICall(McFunction mcFunction, Machine.Block insertAtEnd) {
        super(Tag.Call, insertAtEnd);
        this.mcFunction = mcFunction;
        genDefUse();
    }

    @Override
    public void genDefUse() {
        // TODO for xry: 到底是new还是get单例
        // 调用者保存
        for (int i = r0.ordinal(); i < r0.ordinal() + Math.min(mcFunction.params.size(), 4); i++) {
            useOpds.add(new Operand(Reg.getR(i)));
        }
        for (int i = 0; i < 4; i++) {
            defOpds.add(new Operand(Reg.getR(i)));
        }
        defOpds.add(new Operand(Reg.getR(lr.ordinal())));
        defOpds.add(new Operand(Reg.getR(r12.ordinal())));
        // TODO: 不确定浮点怎么存,不确定到底存哪些

    }

    @Override
    public void output(PrintStream os, Machine.McFunction f) {
        os.println("blx\t" + mcFunction.func_name);
    }

    @Override
    public String toString() {
        return tag + "\t" + mcFunction.mFunc.getName();
    }
}
