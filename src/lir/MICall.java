package lir;

import lir.Arm.Reg;
import lir.Machine.McFunction;

import java.io.PrintStream;

import static lir.Arm.Regs.GPRs.*;

public class MICall extends MachineInst {
    public McFunction mcFunction;

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
            useOpds.add(Reg.getR(i));
        }
        // for (int i = 0; i < 4; i++) {
        //     defOpds.add(Reg.getR(i));
        // }
        // defOpds.add(Reg.getR(lr));
        // defOpds.add(Reg.getR(r12));
        if(mcFunction.mFunc.hasRet()){
            defOpds.add(Reg.getR(r0));
        }
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
