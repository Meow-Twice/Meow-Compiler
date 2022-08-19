package backend;

import lir.*;
import lir.MC.Operand;
import manage.Manager;
import util.CenterControl;

import java.util.*;

import static lir.Arm.Regs.GPRs;
import static lir.Arm.Regs.GPRs.sp;
import static mir.type.DataType.I32;

public class GPRegAllocator extends RegAllocator {
    public GPRegAllocator() {
        dataType = I32;
        K = RK;
        SPILL_MAX_LIVE_INTERVAL = RK * 2;
    }

    public void AllocateRegister(MC.Program program) {
        for (MC.McFunction mf : program.funcList) {
            newVRLiveLength = new HashMap<>();
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
                    reg.adjOpdSet = newOperandSet();
                    reg.movSet = newMoveSet();
                    reg.setAlias(null);
                    reg.old = null;
                }
                for (Operand o : curMF.vrList) {
                    o.loopCounter = 0;
                    o.degree = 0;
                    o.adjOpdSet = newOperandSet();
                    o.movSet = newMoveSet();
                    o.setAlias(null);
                    o.old = null;
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

    Operand vr = null;
    MachineInst firstUse = null;
    MachineInst lastDef = null;
    Operand offImm;
    boolean toStack = true;
    MC.Block curMB;

    int dealSpillTimes = 0;

    private void dealSpillNode(Operand x) {
        // assert false;
        assertDataType(x);
        dealSpillTimes++;
        toStack = judge(x);
        int a = 0;
        if(x.getValue() == 80){
            a = 1;
            // assert false;
        }
        for (MC.Block mb : curMF.mbList) {
            curMB = mb;
            offImm = new Operand(I32, curMF.getVarStack());
            // generate a MILoad before first use, and a I.Str after last def
            firstUse = null;
            lastDef = null;
            vr = null;
            toStack = CenterControl._MustToStack || x.cost > 4 || x.getDefMI() == null;
            if(a == 1 && mb.getLabel().equals("._MB_75_b10")){
                a = 2;
                Manager.MANAGER.outputMI();
                assert false;
            }
            if (x.getValue() == 18) {
                a = 0;
            }

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
                        if ("v18-------match def--------v18".equals(x + "-------match def--------" + def)) {
                            a = 0;
                        }
                        logOut(x + "-------match def--------" + def);
                        // 如果一条指令def的是溢出结点
                        if (vr == null) {
                            // 新建一个结点, vrIdx 即为当前新建立的结点
                            // TODO toStack
                            vr = vrCopy(x);
                            srcMI.setDef(vr);
                        } else {
                            // 替换当前 def 为新建立的 def
                            srcMI.setDef(vr);
                        }
                        lastDef = srcMI;
                    }
                }
                for (int idx = 0; idx < uses.size(); idx++) {
                    Operand use = uses.get(idx);
                    if (use.equals(x)) {
                        // Load
                        if (vr == null) {
                            // TODO toStack
                            vr = vrCopy(x);
                            srcMI.setUse(idx, vr);
                        } else {
                            srcMI.setUse(idx, vr);
                        }
                        if (firstUse == null && (CenterControl._cutLiveNessShortest || lastDef == null)) {
                            // 基本块内如果没有def过这个虚拟寄存器, 并且是第一次用的话就将firstUse设为这个
                            firstUse = srcMI;
                        }
                    }
                }
                if (CenterControl._cutLiveNessShortest || checkCount++ > SPILL_MAX_LIVE_INTERVAL) {
                    checkpoint(x);
                }
            }
            checkpoint(x);
        }
        if (toStack) {
            curMF.addVarStack(4);
        }
    }

    private boolean judge(Operand x) {
        return x.getDefMI() == null || x.getCost() > 4;
    }

    LinkedHashSet<MachineInst> toInsertMIList = new LinkedHashSet<>();
    HashMap<Operand, Operand> regMap = new HashMap<>();

    private void checkpoint(Operand x) {
        boolean flag = false;
        if (toStack) {
            if (firstUse != null) {
                Operand offset = offImm;
                MachineInst mi;
                if (offImm.getValue() >= 4096) {
                    assert (offImm.getValue() % 4 == 0);
                    Operand dst = curMF.newVR();
                    mi = new I.Mov(dst, new Operand(I32, offImm.getValue() / 4), firstUse);
                    logOut(String.format("+++++++%d Checkpoint insert {\t%s\t} before use:\t{\t%s\t}", dealSpillTimes, mi, firstUse));
                    mi = new I.Ldr(vr, rSP, dst, new Arm.Shift(Arm.ShiftType.Lsl, 2), firstUse);
                } else {
                    mi = new I.Ldr(vr, rSP, offset, firstUse);
                }
                logOut(String.format("+++++++%d Checkpoint insert {\t%s\t} before use:\t{\t%s\t}", dealSpillTimes, mi, firstUse));
                // firstUse = null;
            }
            if (lastDef != null) {
                MachineInst insertAfter = lastDef;
                Operand offset = offImm;
                MachineInst mi;
                if (offImm.getValue() >= 4096) {
                    Operand dst = curMF.newVR();
                    assert (offImm.getValue() % 4 == 0);
                    insertAfter = new I.Mov(lastDef, dst, new Operand(I32, offImm.getValue() / 4));
                    logOut(String.format("+++++++%d Checkpoint insert {\t%s\t} after def:\t{\t%s\t}", dealSpillTimes, insertAfter, lastDef));
                    mi = new I.Str(insertAfter, vr, rSP, dst, new Arm.Shift(Arm.ShiftType.Lsl, 2));
                } else {
                    mi = new I.Str(insertAfter, vr, rSP, offset);
                }
                logOut(String.format("+++++++%d Checkpoint insert {\t%s\t} after def:\t{\t%s\t}", dealSpillTimes, mi, insertAfter));
                // lastDef = null;
            }
            // vrIdx = -1;
        } else {
            // assert false;
            if (firstUse != null) {
                Manager.MANAGER.outputMI();
                // assert false;
                flag = true;
                MachineInst defMI = x.getDefMI();
                toInsertMIList = new LinkedHashSet<>();
                regMap = new HashMap<>();
                MachineInst prevMI = firstUse;
                toInsertMIList.add(defMI);

                if (defMI.defOpds.size() != 1) {
                    System.exit(102);
                }
                Operand def = defMI.defOpds.get(0);
                if (def.old != null) {
                    def = def.old;
                }
                // 原始def到firstUse里面的use的映射
                regMap.put(def, vr);

                while (toInsertMIList.size() > 0) {
                    MachineInst mi = toInsertMIList.iterator().next();
                    toInsertMIList.remove(mi);
                    if (mi == null) continue;
                    if (mi instanceof I.Binary) {
                        I.Binary binary = (I.Binary) mi;
                        Operand l = binary.getLOpd();
                        Operand r = binary.getROpd();
                        addDefiners(l, r);
                    } else {
                        switch (mi.getTag()) {
                            case IMov -> addDefiners(((I.Mov) mi).getSrc());
                            case Ldr -> addDefiners(((I.Ldr) mi).getAddr(), ((I.Ldr) mi).getOffset());
                        }
                    }

                    if (mi instanceof I.Binary) {
                        I.Binary binary = (I.Binary) mi;
                        Operand dst = binary.getDst();
                        Operand l = binary.getLOpd();
                        Operand r = binary.getROpd();
                        Arm.Shift shift = binary.getShift();
                        if (shift.shiftOpd.isVirtual(dataType)) {
                            shift = new Arm.Shift(shift.shiftType, genOpd(shift.shiftOpd));
                        }
                        prevMI = new I.Binary(binary.getTag(), genOpd(dst), genOpd(l), genOpd(r), shift, prevMI);
                        prevMI.setCond(mi.getCond());
                    } else {
                        switch (mi.getTag()) {
                            case IMov -> {
                                I.Mov iMov = (I.Mov) mi;
                                Arm.Shift shift = iMov.getShift();
                                if (shift.shiftOpd.isVirtual(dataType)) {
                                    shift = new Arm.Shift(shift.shiftType, genOpd(shift.shiftOpd));
                                }
                                prevMI = new I.Mov(genOpd(iMov.getDst()), genOpd(iMov.getSrc()), shift, prevMI);
                                prevMI.setCond(iMov.getCond());
                            }
                            case Ldr -> {
                                I.Ldr ldr = (I.Ldr) mi;
                                Operand data = ldr.getData();
                                Operand addr = ldr.getAddr();
                                Operand offset = ldr.getOffset();
                                Arm.Shift shift = ldr.getShift();
                                if (shift.shiftOpd.isVirtual(dataType)) {
                                    shift = new Arm.Shift(shift.shiftType, genOpd(shift.shiftOpd));
                                }
                                prevMI = new I.Ldr(genOpd(data), genOpd(addr), genOpd(offset), shift, prevMI);
                                prevMI.setCond(mi.getCond());
                            }
                        }
                    }
                    if (mi.isNeedFix()) {
                        if (mi.getCallee() != null) {
                            prevMI.setNeedFix(mi.getCallee(), mi.getFixType());
                        } else {
                            prevMI.setNeedFix(mi.getFixType());
                        }
                    }
                }
            }
        }

        if (vr != null) {
            if(flag) {
                Manager.MANAGER.outputMI();
                // assert false;
            }
            int firstPos = -1;
            int lastPos = -1;
            int pos = 0;
            for (MachineInst mi : curMB.miList) {
                ++pos;
                if (mi.defOpds.contains(vr) || mi.useOpds.contains(vr)) {
                    if (firstPos == -1) {
                        firstPos = pos;
                    }
                    lastPos = pos;
                }
            }

            if (firstPos >= 0) {
                newVRLiveLength.put(vr, lastPos - firstPos + 1);
            }
        }
        firstUse = null;
        lastDef = null;
        vr = null;
        // TODO 计算生命周期长度
    }

    private void addDefiners(Operand o) {
        if (o.isVirtual(dataType) && !o.isGlobAddr()) {
            MachineInst definer = o.getDefMI();
            toInsertMIList.add(definer);
        }
    }

    private void addDefiners(Operand o1, Operand o2) {
        if (o1.isVirtual(dataType) && !o1.isGlobAddr()) {
            MachineInst definer = o1.getDefMI();
            toInsertMIList.add(definer);
        }
        if (o2.isVirtual(dataType) && !o2.isGlobAddr()) {
            MachineInst definer = o2.getDefMI();
            toInsertMIList.add(definer);
        }
    }

    private Operand genOpd(Operand dst) {
        if (dst.isVirtual(dataType) && !dst.isGlobAddr()) {
            if (dst.old != null) dst = dst.old;
            Operand put = regMap.get(dst);
            if (put == null) {
                put = vrCopy(dst);
                regMap.put(dst, put);
            }
            return put;
        } else {
            return dst;
        }
    }

    public Operand vrCopy(Operand oldVR) {
        Operand copy = curMF.newVR();
        if (oldVR != null && oldVR.isVirtual(dataType) && !oldVR.isGlobAddr()) {
            while (oldVR.old != null) {
                oldVR = oldVR.old;
            }
            copy.old = oldVR;
            copy.setDefCost(oldVR);
        }
        return copy;
    }

    @Override
    protected TreeSet<Arm.Regs> getOkColorSet() {
        TreeSet<Arm.Regs> res = new TreeSet<>(Arrays.asList(GPRs.values()).subList(0, K == 14 ? K + 1 : K));
        res.remove(sp);
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
                    for (Operand def : mi.defOpds) {
                        defs.set(idx++, Arm.Reg.getRSReg(def.getReg()));
                    }
                    idx = 0;
                    ArrayList<Operand> uses = mi.useOpds;
                    for (Operand use : mi.useOpds) {
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
