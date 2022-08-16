package backend;

import lir.*;
import mir.type.DataType;

import java.util.ArrayList;

import static mir.type.DataType.F32;
import static mir.type.DataType.I32;

public class NaiveRegAllocator extends RegAllocator {
    public NaiveRegAllocator() {
        initPool();
    }

    final int rRegNum = 8;
    final int sRegNum = 8;

    void initPool() {
        rRegPool[0] = Arm.Reg.getR(4);
        rRegPool[1] = Arm.Reg.getR(5);
        rRegPool[2] = Arm.Reg.getR(6);
        rRegPool[3] = Arm.Reg.getR(7);
        rRegPool[4] = Arm.Reg.getR(8);
        rRegPool[5] = Arm.Reg.getR(9);
        rRegPool[6] = Arm.Reg.getR(10);
        rRegPool[7] = Arm.Reg.getR(11);
        for (int i = 24; i < 32; i++) {
            sRegPool[i - 24] = Arm.Reg.getS(i);
        }
    }

    Arm.Reg[] rRegPool = new Arm.Reg[rRegNum];
    Arm.Reg[] sRegPool = new Arm.Reg[sRegNum];
    int rStackTop = rRegNum;
    int sStackTop = sRegNum;

    Arm.Reg rRegPop() {
        if (rStackTop <= 0) {
            throw new AssertionError("");
        }
        return rRegPool[--rStackTop];
    }

    Arm.Reg sRegPop() {
        return sRegPool[--sStackTop];
    }

    void reset() {
        rStackTop = rRegNum;
        sStackTop = sRegNum;
    }

    public void AllocateRegister(MC.Program program) {

        for (MC.McFunction mf : program.funcList) {
            mf.setAllocStack();
            mf.addVarStack(4 * mf.getVRSize());
            // if (needFPU)
            mf.addVarStack(4 * mf.getSVRSize());
            for (MC.Block mb : mf.mbList) {
                for (MachineInst mi : mb.miList) {
                    if (mi.isCall()) {
                        // TODO 可以放到CodeGen里
                        mf.setUseLr();
                    }
                }
            }
            mf.alignTotalStackSize();
        }

        fixStack(program.needFixList);
        /*
            Machine.McFunction mf = mi.getMb().mcFunc;
            I.Mov mv = (I.Mov) mi;
            Machine.Operand off = mv.getSrc();
            assert off.is_I_Imm();
            int newOff = switch (mv.getFixType()) {
                case INT_TOTAL_STACK, FLOAT_TOTAL_STACK -> off.getValue() + mf.getTotalStackSize();
                case VAR_STACK -> mf.getVarStack();
                case ONLY_PARAM -> mv.getCallee().getParamStack();
                default -> throw new AssertionError("needFixType Wrong");
            };
            boolean flag = false;
            if (mv.hasNext()) {
                if ((mv.getNext() instanceof I.Binary)) {
                    // TODO 可以启发式做一些优化
                    if (((I.Binary) mv.getNext()).getLOpd().equals(rSP) && ((I.Binary) mv.getNext()).getDst().equals(rSP)) {
                        flag = true;
                        if (CodeGen.immCanCode(newOff)) {
                            ((I.Binary) mv.getNext()).setROpd(new Machine.Operand(I32, newOff));
                            mv.remove();
                        } else {
                            Arm.Reg reg = rRegPop();
                            mv.setSrc(new Machine.Operand(I32, newOff));
                            mv.setDst(reg);
                            ((I.Binary) mv.getNext()).setROpd(reg);
                            mv.clearNeedFix();
                            reset();
                        }
                    }
                }
            }
            if (!flag) {
                mv.setSrc(new Machine.Operand(I32, newOff));
                mv.clearNeedFix();
            }*/

        for (MC.McFunction mf : program.funcList) {
            int iVrBase = mf.getAllocStack();
            int sVrBase = mf.getVRSize() * 4 + iVrBase;
            for (MC.Block mb : mf.mbList) {
                for (MachineInst mi : mb.miList) {
                    ArrayList<MC.Operand> defs = mi.defOpds;
                    ArrayList<MC.Operand> uses = mi.useOpds;
                    if (mi.isComment() || mi.isCall()) continue;
                    assert uses.size() < 4;
                    int useIdx = 0;
                    for (MC.Operand use : uses) {
                        if (use.isVirtual(I32)) {
                            int offset = iVrBase + 4 * use.getValue();
                            if (Math.abs(offset) < 4096) {
                                Arm.Reg useReg = rRegPop();
                                new I.Ldr(useReg, Arm.Reg.getR(Arm.Regs.GPRs.sp), new MC.Operand(DataType.I32, offset), mi);
                                mi.setUse(useIdx, useReg);
                            } else {
                                Arm.Reg offReg = rRegPop();
                                new I.Mov(offReg, new MC.Operand(DataType.I32, offset), mi);
                                Arm.Reg useReg = rRegPop();
                                new I.Ldr(useReg, Arm.Reg.getR(Arm.Regs.GPRs.sp), offReg, mi);
                                mi.setUse(useIdx, useReg);
                            }
                        } else if (use.isVirtual(F32)) {
                            int offset = sVrBase + 4 * use.getValue();
                            if (Math.abs(offset) <= 1020) {
                                Arm.Reg useReg = sRegPop();
                                new V.Ldr(useReg, Arm.Reg.getR(Arm.Regs.GPRs.sp), new MC.Operand(DataType.I32, offset), mi);
                                mi.setUse(useIdx, useReg);
                            } else {
                                Arm.Reg offReg = rRegPop();
                                new I.Mov(offReg, new MC.Operand(DataType.I32, offset), mi);
                                new I.Binary(MachineInst.Tag.Add, offReg, Arm.Reg.getR(Arm.Regs.GPRs.sp), offReg, mi);
                                Arm.Reg useReg = sRegPop();
                                new V.Ldr(useReg, offReg, mi);
                                mi.setUse(useIdx, useReg);
                            }
                        }
                        useIdx++;
                    }
                    // reset();
                    if (defs.size() > 0) {
                        assert defs.size() == 1;
                        MC.Operand def = defs.get(0);
                        if (def.isVirtual(I32)) {
                            int offset = iVrBase + 4 * def.getValue();
                            if (Math.abs(offset) < 4096) {
                                Arm.Reg useReg = rRegPop();
                                new I.Str(mi, useReg, Arm.Reg.getR(Arm.Regs.GPRs.sp), new MC.Operand(DataType.I32, offset));
                                mi.setDef(useReg);
                            } else {
                                Arm.Reg offReg = rRegPop();
                                I.Mov mv = new I.Mov(mi, offReg, new MC.Operand(DataType.I32, offset));
                                Arm.Reg defReg = rRegPop();
                                new I.Str(mv, defReg, Arm.Reg.getR(Arm.Regs.GPRs.sp), offReg);
                                mi.setDef(defReg);
                            }
                        } else if (def.isVirtual(F32)) {
                            int offset = sVrBase + 4 * def.getValue();
                            if (Math.abs(offset) <= 1020) {
                                Arm.Reg defReg = sRegPop();
                                new V.Str(mi, defReg, rSP, new MC.Operand(DataType.I32, offset));
                                mi.setDef(defReg);
                            } else {
                                Arm.Reg offReg = rRegPop();
                                I.Mov mv = new I.Mov(mi, offReg, new MC.Operand(DataType.I32, offset));
                                I.Binary bino = new I.Binary(mv, MachineInst.Tag.Add, offReg, Arm.Reg.getR(Arm.Regs.GPRs.sp), offReg);
                                Arm.Reg defReg = sRegPop();
                                new V.Str(bino, defReg, offReg);
                                mi.setDef(defReg);
                            }
                        }
                    }
                    reset();
                }
            }
        }

    }
}
