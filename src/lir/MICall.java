package lir;

import lir.Machine.*;

import lir.Arm.Reg;

import java.io.PrintStream;

import static lir.Arm.Reg.GPRs.lr;
import static lir.Machine.Operand.Type.PreColored;

public class MICall extends MachineInst {
    Machine.Function function;

    public MICall(Machine.Block insertAtEnd,boolean isFloat) {
        super(Tag.Call, insertAtEnd,isFloat);
    }

    @Override
    public void genDefUse() {
        // TODO for xry: 到底是new还是get单例
        // 调用者保存
        for (int i = 0; i < function.params.size(); i++) {
            useOpds.add(new Operand(PreColored, Reg.getR(i)));
        }
        for (int i = 0; i < 4; i++) {
            defOpds.add(new Operand(PreColored, Reg.getR(i)));
        }
        defOpds.add(new Operand(PreColored, Reg.getR(lr.ordinal())));
        // TODO: 不确定浮点怎么存,不确定到底存哪些

    }

    public void output(PrintStream os){
        os.println("blx\t"+function.func_name);
    }
}
