package backend;

import lir.*;
import mir.type.DataType;

import java.util.ArrayList;
import java.util.Stack;

public class SingleFuncRegAllocator extends RegAllocator {
    public SingleFuncRegAllocator() {
        initPool();
    }

    void initPool() {
        rRegPool[0] = Arm.Reg.getR(4);
        rRegPool[1] = Arm.Reg.getR(5);
        rRegPool[2] = Arm.Reg.getR(8);
        rRegPool[3] = Arm.Reg.getR(9);
        rRegPool[4] = Arm.Reg.getR(10);
        rRegPool[5] = Arm.Reg.getR(11);
        for (int i = 24; i < 32; i++) {
            sRegPool[i - 24] = Arm.Reg.getS(i);
        }
    }

    Arm.Reg[] rRegPool = new Arm.Reg[6];
    Arm.Reg[] sRegPool = new Arm.Reg[8];
    int rStackTop = 6;
    int sStackTop = 8;

    Arm.Reg rRegPop() {
        return rRegPool[--rStackTop];
    }

    Arm.Reg sRegPop() {
        return sRegPool[--sStackTop];
    }

    void reset() {
        rStackTop = 6;
        sStackTop = 8;
    }

    public void AllocateRegister(Machine.Program program) {
        for (Machine.McFunction mf : program.funcList) {
            int iVrBase = mf.getVarStack();
            int sVrBase = mf.getVRSize() * 4 + iVrBase;
            for (Machine.Block mb : mf.mbList) {
                for (MachineInst mi : mb.miList) {
                    ArrayList<Machine.Operand> defs = mi.defOpds;
                    ArrayList<Machine.Operand> uses = mi.useOpds;
                    if (mi.isComment() || mi.isCall()) continue;
                    assert uses.size() < 4;
                    int useIdx = 0;
                    for (Machine.Operand use : uses) {
                        if (use.is_I_Virtual()) {
                            Arm.Reg offReg = rRegPop();
                            new MIMove(offReg, new Machine.Operand(DataType.I32, iVrBase + 4 * use.getValue()), mi);
                            Arm.Reg useReg = rRegPop();
                            new MILoad(useReg, Arm.Reg.getR(Arm.Regs.GPRs.sp), offReg, mi);
                            mi.setUse(useIdx, useReg);
                        } else if (use.is_F_Virtual()) {
                            Arm.Reg offReg = rRegPop();
                            new MIMove(offReg, new Machine.Operand(DataType.I32, sVrBase + 4 * use.getValue()), mi);
                            Arm.Reg defReg = sRegPop();
                            new V.Ldr(defReg, Arm.Reg.getR(Arm.Regs.GPRs.sp), offReg, mi);
                            mi.setUse(useIdx, defReg);
                        }
                        useIdx++;
                    }
                    reset();
                    if (defs.size() == 1) {
                        Machine.Operand def = defs.get(0);
                        if (def.is_I_Virtual()) {
                            Arm.Reg offReg = rRegPop();
                            MIMove mv = new MIMove(mi, offReg, new Machine.Operand(DataType.I32, iVrBase + 4 * def.getValue()));
                            Arm.Reg useReg = rRegPop();
                            new MIStore(mv, useReg, Arm.Reg.getR(Arm.Regs.GPRs.sp), offReg);
                            mi.setDef(useReg);
                        } else if (def.is_F_Virtual()) {
                            Arm.Reg offReg = rRegPop();
                            MIMove mv = new MIMove(mi, offReg, new Machine.Operand(DataType.I32, sVrBase + 4 * def.getValue()));
                            Arm.Reg defReg = sRegPop();
                            new V.Str(mv, defReg, Arm.Reg.getR(Arm.Regs.GPRs.sp), offReg);
                            mi.setDef(defReg);
                        }
                    }
                    reset();
                }
            }
        }
    }
}
