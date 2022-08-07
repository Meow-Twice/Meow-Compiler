package backend;

import lir.*;
import lir.MC.Operand;

import java.util.*;

import static lir.Arm.Regs.GPRs;
import static lir.Arm.Regs.GPRs.r12;
import static mir.type.DataType.I32;

public class GPRegAllocator extends RegAllocator {
    public GPRegAllocator() {
        dataType = I32;
        K = RK;
        SPILL_MAX_LIVE_INTERVAL = RK * 2;
    }

    public void AllocateRegister(MC.Program program) {
        for (MC.McFunction mf : program.funcList) {
            curMF = mf;
            while (true) {
                turnInit(mf);

                for (int i = 0; i < K; i++) {
                    Arm.Reg.getR(i).degree = MAX_DEGREE;
                }
                logOut("RegAlloc Build start");
                for (Arm.Reg reg : Arm.Reg.getGPRPool()) {
                    reg.loopCounter = 0;
                    reg.degree = MAX_DEGREE;
                    reg.adjOpdSet = new HashSet<>();
                    reg.movSet = new HashSet<>();
                    reg.setAlias(null);
                }
                for (Operand o : curMF.vrList) {
                    o.loopCounter = 0;
                    o.degree = 0;
                    o.adjOpdSet = new HashSet<>();
                    o.movSet = new HashSet<>();
                    o.setAlias(null);
                }
                // logOut("in build");
                build();
                logOut("RegAlloc Build end");

                logOut("curMF.vrList:\t" + curMF.vrList.toString());
                // makeWorkList
                for (Operand vr : curMF.vrList) {
                    assert vr.isI32();
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
                    // fixStack();
                    break;
                }
                logOut("needSpill");
                spilledNodeSet.forEach(this::dealSpillNode);
                logOut("endSpill");
                // try {
                //     Manager.MANAGER.outputMI();
                // } catch (FileNotFoundException e) {
                //     throw new RuntimeException(e);
                // }
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
        assertDataType(x);
        dealSpillTimes++;
        for (MC.Block mb : curMF.mbList) {
            offImm = new Operand(I32, curMF.getVarStack());
            // generate a MILoad before first use, and a I.Str after last def
            firstUse = null;
            lastDef = null;
            vrIdx = -1;
            toStack = true;

            int checkCount = 0;
            for (MachineInst srcMI : mb.miList) {
                // MICall指令def的都是预分配的寄存器
                if (srcMI.isCall() || srcMI.isComment()) continue;
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
                            vrIdx = curMF.getVRSize();
                            srcMI.setDef(curMF.newVR());
                        } else {
                            // 替换当前 def 为新建立的 def
                            srcMI.setDef(curMF.vrList.get(vrIdx));
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
                            vrIdx = curMF.getVRSize();
                            srcMI.setUse(idx, curMF.newVR());
                        } else {
                            srcMI.setUse(idx, curMF.vrList.get(vrIdx));
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
                Operand offset = offImm;
                if (offImm.get_I_Imm() >= (1 << 12)) {
                    Operand dst = curMF.newVR();
                    I.Mov mi = new I.Mov(dst, offImm, firstUse);
                    logOut(String.format("+++++++%d Checkpoint insert {\t%s\t} before use:\t{\t%s\t}", dealSpillTimes, mi, firstUse));
                    offset = dst;
                }
                I.Ldr mi = new I.Ldr(curMF.getVR(vrIdx), rSP, offset, firstUse);
                logOut(String.format("+++++++%d Checkpoint insert {\t%s\t} before use:\t{\t%s\t}", dealSpillTimes, mi, firstUse));
                firstUse = null;
            }
            if (lastDef != null) {
                MachineInst insertAfter = lastDef;
                Operand offset = offImm;
                if (offImm.get_I_Imm() >= (1 << 12)) {
                    Operand dst = curMF.newVR();
                    insertAfter = new I.Mov(lastDef, dst, offImm);
                    logOut(String.format("+++++++%d Checkpoint insert {\t%s\t} after def:\t{\t%s\t}", dealSpillTimes, insertAfter, lastDef));
                    offset = dst;
                }
                I.Str st = new I.Str(insertAfter, curMF.getVR(vrIdx), rSP, offset);
                logOut(String.format("+++++++%d Checkpoint insert {\t%s\t} after def:\t{\t%s\t}", dealSpillTimes, st, insertAfter));
                lastDef = null;
            }
            vrIdx = -1;
        }
        // TODO 计算生命周期长度
    }

    @Override
    protected TreeSet<Arm.Regs> getOkColorSet() {
        TreeSet<Arm.Regs> res =  new TreeSet<>(Arrays.asList(GPRs.values()).subList(0, K));
        res.remove(r12);
        return res;
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

        ArrayList<I> needFixList = new ArrayList<>();
        for (MC.Block mb : curMF.mbList) {
            for (MachineInst mi : mb.miList) {
                if (mi.isNeedFix()) {
                    needFixList.add((I) mi);
                }
                if (mi.isComment()) continue;
                if (mi.isCall()) {
                    // TODO 这里不考虑Call
                    curMF.setUseLr();
                    int idx = 0;
                    ArrayList<Operand> defs = mi.defOpds;
                    for(Operand def: mi.defOpds){
                        defs.set(idx++, Arm.Reg.getRSReg(def.getReg()));
                    }
                    idx = 0;
                    ArrayList<Operand> uses = mi.useOpds;
                    for(Operand use: mi.useOpds){
                        uses.set(idx++, Arm.Reg.getRSReg(use.getReg()));
                    }
                    continue;
                }
                // logOut("Consider " + mi);
                ArrayList<Operand> defs = mi.defOpds;
                ArrayList<Operand> uses = mi.useOpds;
                if (defs.size() > 0) {
                    assert defs.size() == 1; // 只要有def, 除Call外均为1
                    Operand def = defs.get(0);
                    if (def.isPreColored(dataType)) {
                        defs.set(0, Arm.Reg.getRSReg(def.getReg()));
                    } else {
                        Operand set = colorMap.get(def);
                        if (set != null) {
                            curMF.addUsedGPRs(set.reg);
                            logOut("- Def\t" + def + "\tassign: " + set);
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
                            curMF.addUsedGPRs(set.reg);
                            logOut("- Use\t" + uses.get(i) + "\tassign: " + set);
                            mi.setUse(i, set);
                        }
                    }
                }
            }
        }

        curMF.alignTotalStackSize();
        // fixStack
        fixStack(needFixList);
    }
}
