package lir;

import lir.Arm.Reg;
import lir.Machine.McFunction;

import java.io.PrintStream;
import java.util.ArrayList;

import static backend.CodeGen.*;
import static backend.CodeGen.needFPU;
import static lir.Arm.Regs.FPRs.s0;
import static lir.Arm.Regs.GPRs.*;

public class MICall extends MachineInst {
    public McFunction callee;

    public MICall(McFunction callee, Machine.Block insertAtEnd) {
        super(Tag.Call, insertAtEnd);
        this.callee = callee;
        genDefUse();
    }

    @Override
    public void genDefUse() {
        if (callee.mFunc.isExternal) {
            for (int i = 0; i < 2; i++) {
                if(needFPU) {
                    useOpds.add(Reg.getS(i));
                }
                useOpds.add(Reg.getR(i));
            }
            for (int i = r0.ordinal(); i < r0.ordinal() + rParamCnt; i++) {
                defOpds.add(Reg.getR(i));
            }
            if(needFPU) {
                for (int i = s0.ordinal(); i < s0.ordinal() + sParamCnt; i++) {
                    defOpds.add(Reg.getS(i));
                }
            }
        } else {
            for (int i = r0.ordinal(); i < r0.ordinal() + Math.min(callee.intParamCount, rParamCnt); i++) {
                useOpds.add(Reg.getR(i));
                defOpds.add(Reg.getR(i));
            }
            if(needFPU) {
                for (int i = s0.ordinal(); i < s0.ordinal() + Math.min(callee.floatParamCount, sParamCnt); i++) {
                    useOpds.add(Reg.getS(i));
                    defOpds.add(Reg.getS(i));
                }
            }
            // TODO for xry: 到底是new还是get单例
            // 调用者保存
            // if (mf.mFunc.hasRet()) {
            //     if (mf.mFunc.getRetType().isInt32Type()) {
            //         defOpds.add(Reg.getR(r0));
            //     } else if (mf.mFunc.getRetType().isFloatType()) {
            //         defOpds.add(Reg.getS(s0));
            //     } else {
            //         throw new AssertionError("Wrong call func type: has ret but is type of " + mf.mFunc.getRetType());
            //     }
            // }
        }
        // TODO for bug test!!!
        if (callee.mFunc.hasRet()) {
            if (callee.mFunc.getRetType().isInt32Type()) {
                defOpds.add(Reg.getR(r0));
                useOpds.add(Reg.getR(r0));
            } else if (callee.mFunc.getRetType().isFloatType()) {
                defOpds.add(Reg.getS(s0));
                useOpds.add(Reg.getS(s0));
            } else {
                throw new AssertionError("Wrong call func type: has ret but is type of " + callee.mFunc.getRetType());
            }
        }
    }




    @Override
    public void output(PrintStream os, McFunction f) {
        os.println("\tblx\t" + callee.mFunc.getName());
    }

    @Override
    public String toString() {
        return tag + "\t" + callee.mFunc.getName();
    }
}
