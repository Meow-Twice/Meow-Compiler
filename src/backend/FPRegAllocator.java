package backend;

import lir.*;
import lir.MC.Operand;
import util.CenterControl;

import java.util.*;

import static mir.type.DataType.F32;
import static mir.type.DataType.I32;

public class FPRegAllocator extends RegAllocator {

    public FPRegAllocator() {
        dataType = F32;
        K = SK;
        if (!CenterControl._FAST_REG_ALLOCATE) {
            SPILL_MAX_LIVE_INTERVAL = SK;
        }
    }

    public void AllocateRegister(MC.Program program) {
        for (MC.McFunction mf : program.funcList) {
            curMF = mf;
            while (true) {
                turnInit(mf);

                for (int i = 0; i < K; i++) {
                    Arm.Reg.getS(i).degree = MAX_DEGREE;
                }
                logOut("f RegAlloc Build start");
                for (Arm.Reg reg : Arm.Reg.getFPRPool()) {
                    reg.loopCounter = 0;
                    reg.degree = MAX_DEGREE;
                    reg.adjOpdSet = new HashSet<>();
                    reg.movSet = new HashSet<>();
                    reg.setAlias(null);
                }
                for (Operand o : curMF.sVrList) {
                    o.loopCounter = 0;
                    o.degree = 0;
                    o.adjOpdSet = new HashSet<>();
                    o.movSet = new HashSet<>();
                    o.setAlias(null);
                }
                // logOut("in build");
                build();
                logOut("f RegAlloc Build end");

                logOut("curMF.sVrList:\t" + curMF.sVrList.toString());
                // makeWorkList
                for (Operand vr : curMF.sVrList) {
                    // initial
                    if (vr.degree >= K) {
                        spillWorkSet.add(vr);
                    } else if (nodeMoves(vr).size() > 0) {
                        freezeWorkSet.add(vr);
                    } else {
                        simplifyWorkSet.add(vr);
                    }
                }
                regAllocIteration();
                // Manager.MANAGER.outputMI();
                assignColors();
                // Manager.MANAGER.outputMI();
                if (spilledNodeSet.size() == 0) {
                    break;
                }
                logOut("needSpill");
                spilledNodeSet.forEach(this::dealSpillNode);
                logOut("endSpill");
            }
            logOut(curMF.mFunc.getName() + "done");
        }
    }

    int vrIdx = -1;
    MachineInst firstUse = null;
    MachineInst lastDef = null;
    Operand offImm;
    boolean toStack = true;

    int dealSpillTimes = 0;

    private void dealSpillNode(Operand x) {
        dealSpillTimes++;
        for (MC.Block mb : curMF.mbList) {
            offImm = new Operand(I32, curMF.getVarStack());
            // generate a MILoad before first use, and a MIStore after last def
            firstUse = null;
            lastDef = null;
            vrIdx = -1;
            toStack = true;

            int checkCount = 0;
            for (MachineInst srcMI : mb.miList) {
                // MICall指令def的都是预分配的寄存器
                if (srcMI.isCall() || srcMI.isComment() || !(srcMI instanceof V)) continue;
                ArrayList<Operand> defs = srcMI.defOpds;
                ArrayList<Operand> uses = srcMI.useOpds;
                if (defs.size() > 0) {
                    assert defs.size() == 1;
                    Operand def = defs.get(0);
                    if (def.equals(x)) {
                        logOut(x + "-------match def--------" + def);
                        // 如果一条指令def的是溢出结点
                        if (vrIdx == -1) {
                            // 新建一个结点, vrIdx 即为当前新建立的结点
                            // TODO toStack
                            vrIdx = curMF.getSVRSize();
                            srcMI.setDef(curMF.newSVR());
                        } else {
                            // 替换当前 def 为新建立的 def
                            srcMI.setDef(curMF.sVrList.get(vrIdx));
                        }
                        lastDef = srcMI;
                    }
                }
                for (int idx = 0; idx < uses.size(); idx++) {
                    Operand use = uses.get(idx);
                    if (use.equals(x)) {
                        // Load
                        if (vrIdx == -1) {
                            // TODO toStack
                            vrIdx = curMF.getSVRSize();
                            srcMI.setUse(idx, curMF.newSVR());
                        } else {
                            srcMI.setUse(idx, curMF.sVrList.get(vrIdx));
                        }
                        if (firstUse == null && lastDef == null) {
                            // 基本块内如果没有def过这个虚拟寄存器, 并且是第一次用的话就将firstUse设为这个
                            firstUse = srcMI;
                        }
                    }
                }
                if (checkCount++ > SPILL_MAX_LIVE_INTERVAL) {
                    checkpoint();
                }
            }
            checkpoint();
        }
        if (toStack) {
            curMF.addVarStack(4);
        }
    }

    private void checkpoint() {
        if (toStack) {
            if (firstUse != null) {
                // Operand offset = offImm;
                V.Ldr mi;
                if (CodeGen.vLdrStrImmEncode(offImm.get_I_Imm())) {
                    new V.Ldr(curMF.getSVR(vrIdx), rSP, offImm, firstUse);
                } else {
                    if (CodeGen.immCanCode(offImm.get_I_Imm())) {
                        Operand dstAddr = curMF.newVR();
                        new I.Binary(MachineInst.Tag.Add, dstAddr, rSP, offImm, firstUse);
                        new V.Ldr(curMF.getSVR(vrIdx), dstAddr, firstUse);
                    } else {
                        Operand dstAddr = curMF.newVR();
                        new I.Mov(dstAddr, offImm, firstUse);
                        Operand finalAddr = curMF.newVR();
                        new I.Binary(MachineInst.Tag.Add, finalAddr, rSP, dstAddr, firstUse);
                        new V.Ldr(curMF.getSVR(vrIdx), finalAddr, firstUse);
                    }
                }
                firstUse = null;
            }
            if (lastDef != null) {
                // MachineInst insertAfter = lastDef;
                // Operand offset = offImm;
                if (CodeGen.vLdrStrImmEncode(offImm.get_I_Imm())) {
                    new V.Str(lastDef, curMF.getSVR(vrIdx), rSP, offImm);
                } else {
                    if (CodeGen.immCanCode(offImm.get_I_Imm())) {
                        Operand dstAddr = curMF.newVR();
                        I.Binary bino = new I.Binary(lastDef, MachineInst.Tag.Add, dstAddr, rSP, offImm);
                        new V.Str(bino, curMF.getSVR(vrIdx), dstAddr);
                    } else {
                        Operand dstAddr = curMF.newVR();
                        I.Mov mv = new I.Mov(lastDef, dstAddr, offImm);
                        Operand finalAddr = curMF.newVR();
                        I.Binary bino = new I.Binary(mv, MachineInst.Tag.Add, finalAddr, rSP, dstAddr);
                        new V.Str(bino, curMF.getSVR(vrIdx), finalAddr);
                    }
                }
                lastDef = null;
            }
            vrIdx = -1;
        }
        // TODO 计算生命周期长度
    }

    @Override
    protected TreeSet<Arm.Regs> getOkColorSet() {
        return new TreeSet<>(Arrays.asList(Arm.Regs.FPRs.values()).subList(0, K));
    }

    public void assignColors() {
        preAssignColors();

        if (spilledNodeSet.size() > 0) {
            return;
        }

        for (Operand v : coalescedNodeSet) {
            Operand a = getAlias(v);
            assert !a.isAllocated();
            colorMap.put(v, a.isPreColored(dataType) ? Arm.Reg.getRSReg(a.reg) : colorMap.get(a));
        }

        for (MC.Block mb : curMF.mbList) {
            for (MachineInst mi : mb.miList) {
                if (mi.isComment()) continue;
                if (mi.isCall()) {
                    // TODO 这里考虑Call
                    curMF.setUseLr();
                    continue;
                }
                if (!(mi instanceof V)) {
                    continue;
                }
                logOut("Consider " + mi);
                ArrayList<Operand> defs = mi.defOpds;
                ArrayList<Operand> uses = mi.useOpds;
                if (defs.size() > 0) {
                    assert defs.size() == 1; // 只要有def, 除Call外均为1
                    Operand def = defs.get(0);
                    if (def.isPreColored(dataType)) {
                        defs.set(0, Arm.Reg.getRSReg(def.getReg()));
                    } else {
                        Operand set = colorMap.get(defs.get(0));
                        if (set != null) {
                            curMF.addUsedFRPs(set.reg);
                            logOut("- Def\t" + defs.get(0) + "\tassign: " + set);
                            defs.set(0, set);
                        }
                    }
                }

                for (int i = 0; i < uses.size(); i++) {
                    assert uses.get(i) != null;
                    Operand use = uses.get(i);
                    if (use.isPreColored(dataType)) {
                        uses.set(i, Arm.Reg.getRSReg(use.getReg()));
                    } else {
                        Operand set = colorMap.get(use);
                        if (set != null) {
                            curMF.addUsedFRPs(set.reg);
                            logOut("- Use\t" + uses.get(i) + "\tassign: " + set);
                            mi.setUse(i, set);
                        }
                    }
                }
            }
        }

    }
}
