package lir;

import lir.Arm.Reg;
import lir.Machine.McFunction;

import java.io.PrintStream;

import static backend.CodeGen.rParamCnt;
import static backend.CodeGen.sParamCnt;
import static lir.Arm.Regs.FPRs.s0;
import static lir.Arm.Regs.GPRs.*;

public class MICall extends MachineInst {
    public McFunction mf;

    public MICall(McFunction mf, Machine.Block insertAtEnd) {
        super(Tag.Call, insertAtEnd);
        this.mf = mf;
        genDefUse();
    }

    @Override
    public void genDefUse() {
        if (mf.mFunc.isExternal) {
            for (int i = r0.ordinal(); i < r0.ordinal() + rParamCnt; i++) {
                useOpds.add(Reg.getR(i));
            }
            for (int i = s0.ordinal(); i < s0.ordinal() + sParamCnt; i++) {
                useOpds.add(Reg.getS(i));
            }
        } else {
            // TODO for xry: 到底是new还是get单例
            // 调用者保存
            for (int i = r0.ordinal(); i < r0.ordinal() + Math.min(mf.intParamCount, rParamCnt); i++) {
                useOpds.add(Reg.getR(i));
            }
            for (int i = s0.ordinal(); i < s0.ordinal() + Math.min(mf.floatParamCount, sParamCnt); i++) {
                useOpds.add(Reg.getS(i));
            }
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
        if (mf.mFunc.hasRet()) {
            if (mf.mFunc.getRetType().isInt32Type()) {
                defOpds.add(Reg.getR(r0));
            } else if (mf.mFunc.getRetType().isFloatType()) {
                defOpds.add(Reg.getS(s0));
            } else {
                throw new AssertionError("Wrong call func type: has ret but is type of " + mf.mFunc.getRetType());
            }
        }
    }

    @Override
    public void output(PrintStream os, Machine.McFunction f) {
        if (mf.mFunc.isExternal) {
            os.println("\tpush\t{r1,r2,r3}\t");
            os.println("\tvpush\t{s2-s15}\t");
        }
        os.println("\tblx\t" + mf.mFunc.getName());
        if (mf.mFunc.isExternal) {
            os.println("\tvpop\t{s2-s15}\t");
            os.println("\tpop\t{r1,r2,r3}\t");
        }
    }

    @Override
    public String toString() {
        return tag + "\t" + mf.mFunc.getName();
    }
}
