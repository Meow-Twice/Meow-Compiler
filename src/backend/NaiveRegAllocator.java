package backend;

import lir.*;
import mir.type.DataType;

import java.util.ArrayList;

import static backend.CodeGen.needFPU;
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

    public void AllocateRegister(Machine.Program program) {

        for (Machine.McFunction mf : program.funcList) {
            mf.setAllocStack();
            mf.addVarStack(4 * mf.getVRSize());
            // if (needFPU)
            mf.addVarStack(4 * mf.getSVRSize());
            for (Machine.Block mb : mf.mbList) {
                for (MachineInst mi : mb.miList) {
                    if (mi.isCall()) {
                        // TODO 可以放到CodeGen里
                        mf.setUseLr();
                    }
                }
            }
            mf.alignTotalStackSize();
        }


        for (MachineInst mi : program.needFixList) {
            // fixStack
            Machine.McFunction mf = mi.getMb().mcFunc;
            MIMove mv = (MIMove) mi;
            Machine.Operand off = mv.getSrc();
            assert off.is_I_Imm();
            // TODO 没对齐
            int newOff = switch (mv.getFixType()) {
                case TOTAL_STACK -> off.getValue() + mf.getTotalStackSize();
                case VAR_STACK -> mf.getVarStack();
                case ONLY_PARAM -> mv.getCallee().getParamStack();
                default -> throw new AssertionError("needFixType Wrong");
            };
            boolean flag = false;
            if (CodeGen.immCanCode(newOff)) {
                if (mv.hasNext()) {
                    if ((mv.getNext() instanceof MIBinary)) {
                        if (((MIBinary) mv.getNext()).getLOpd().equals(rSP) && ((MIBinary) mv.getNext()).getDst().equals(rSP)) {
                            flag = true;
                            ((MIBinary) mv.getNext()).setROpd(new Machine.Operand(I32, newOff));
                            mv.remove();
                        }
                    }
                }
            }
            if(!flag){
                mv.setSrc(new Machine.Operand(I32, newOff));
                mv.clearNeedFix();
            }
        }

        for (Machine.McFunction mf : program.funcList) {
            int iVrBase = mf.getAllocStack();
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
                            int offset = iVrBase + 4 * use.getValue();
                            if (Math.abs(offset) < 4096) {
                                Arm.Reg useReg = rRegPop();
                                MILoad load = new MILoad(useReg, Arm.Reg.getR(Arm.Regs.GPRs.sp), new Machine.Operand(DataType.I32, offset), mi);
                                mi.setUse(useIdx, useReg);
                            } else {
                                Arm.Reg offReg = rRegPop();
                                new MIMove(offReg, new Machine.Operand(DataType.I32, offset), mi);
                                Arm.Reg useReg = rRegPop();
                                new MILoad(useReg, Arm.Reg.getR(Arm.Regs.GPRs.sp), offReg, mi);
                                mi.setUse(useIdx, useReg);
                            }
                        } else if (use.is_F_Virtual()) {
                            int offset = sVrBase + 4 * use.getValue();
                            if (Math.abs(offset) <= 1020) {
                                Arm.Reg useReg = sRegPop();
                                new V.Ldr(useReg, Arm.Reg.getR(Arm.Regs.GPRs.sp), new Machine.Operand(DataType.I32, offset), mi);
                            } else {
                                Arm.Reg offReg = rRegPop();
                                new MIMove(offReg, new Machine.Operand(DataType.I32, offset), mi);
                                new MIBinary(MachineInst.Tag.Add, offReg, Arm.Reg.getR(Arm.Regs.GPRs.sp), offReg, mi);
                                Arm.Reg useReg = sRegPop();
                                new V.Ldr(useReg, offReg, mi);
                                mi.setUse(useIdx, useReg);
                            }
                        }
                        useIdx++;
                    }
                    // reset();
                    if (defs.size() == 1) {
                        Machine.Operand def = defs.get(0);
                        if (def.is_I_Virtual()) {
                            int offset = iVrBase + 4 * def.getValue();
                            if (Math.abs(offset) < 4096) {
                                Arm.Reg useReg = rRegPop();
                                new MIStore(mi, useReg, Arm.Reg.getR(Arm.Regs.GPRs.sp), new Machine.Operand(DataType.I32, offset));
                                mi.setDef(useReg);
                            } else {
                                Arm.Reg offReg = rRegPop();
                                MIMove mv = new MIMove(mi, offReg, new Machine.Operand(DataType.I32, offset));
                                Arm.Reg defReg = rRegPop();
                                new MIStore(mv, defReg, Arm.Reg.getR(Arm.Regs.GPRs.sp), offReg);
                                mi.setDef(defReg);
                            }
                        } else if (def.is_F_Virtual()) {
                            int offset = sVrBase + 4 * def.getValue();
                            if (Math.abs(offset) <= 1020) {
                                Arm.Reg useReg = sRegPop();
                                new V.Ldr(useReg, Arm.Reg.getR(Arm.Regs.GPRs.sp), new Machine.Operand(DataType.I32, offset), mi);
                            } else {
                                Arm.Reg offReg = rRegPop();
                                MIMove mv = new MIMove(mi, offReg, new Machine.Operand(DataType.I32, offset));
                                MIBinary bino = new MIBinary(mv, MachineInst.Tag.Add, offReg, Arm.Reg.getR(Arm.Regs.GPRs.sp), offReg);
                                Arm.Reg defReg = sRegPop();
                                new V.Str(bino, defReg, Arm.Reg.getR(Arm.Regs.GPRs.sp), offReg);
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
