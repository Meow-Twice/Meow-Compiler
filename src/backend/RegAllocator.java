package backend;

import lir.*;
import lir.Machine.Operand;
import mir.type.DataType;
import util.ILinkNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Stack;

import static lir.Arm.Regs.GPRs.r11;
import static lir.Arm.Regs.GPRs.sp;
import static mir.type.DataType.I32;

public class RegAllocator {
    protected Machine.McFunction curMF;
    public static final int SP_ALIGN = 2 * 4;

    protected static final boolean DEBUG_STDIN_OUT = false;

    protected int K;
    protected final int RK = 12;
    // TODO 尝试将sp直接设为Allocated或者不考虑sp add或sub指令
    protected final int SK = 32;
    protected DataType dataType;

    protected int SPILL_MAX_LIVE_INTERVAL = 1;
    protected final Arm.Reg rSP = Arm.Reg.getR(sp);
    protected int MAX_DEGREE = Integer.MAX_VALUE >> 2;


    /***
     * 结点, 工作表, 集合和栈的数据结构.
     * 下面的表和集合总是互不相交的, 并且每个结点斗数与一个且只属于一个集合或表
     */

    /**
     * 欲从图中删除的结点集
     * 初始为低度数的传送(mv)无关的结点集, 实际上在select_spill的时候会把下一轮需要挪出去的点放到这里
     */
    protected HashSet<Operand> simplifyWorkSet = new HashSet<>();


    /**
     * 低度数的传送有关的结点
     */
    protected HashSet<Operand> freezeWorkSet = new HashSet<>();

    /**
     * 高度数的结点
     */
    protected HashSet<Operand> spillWorkSet = new HashSet<>();

    /**
     * 在本轮中要被溢出的结点集合, 初始为空
     */
    protected HashSet<Operand> spilledNodeSet = new HashSet<>();

    /**
     * 已合并的寄存器集合. 当合并 u <- v 时, 将 v 加入到这个集合中, u 则被放回到某个工作表中
     */
    protected HashSet<Operand> coalescedNodeSet = new HashSet<>();
    /**
     * 已成功着色的结点集合
     */
    protected ArrayList<Operand> coloredNodeList = new ArrayList<>();
    /**
     * 包含从图中删除的临时寄存器的栈
     */
    protected Stack<Operand> selectStack = new Stack<>();


    /**
     * 图中冲突边 (u, v) 的集合, 如果(u, v) in adjSet, 则(v, u) in adjSet
     * 用于判断两个Operand是否相邻
     */
    HashSet<AdjPair> adjSet = new HashSet<>();

    protected static class AdjPair {
        public Operand u;
        public Operand v;

        public AdjPair(Operand u, Operand v) {
            this.u = u;
            this.v = v;
        }

        @Override
        public int hashCode() {
            return Objects.hash(u, v);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (!(obj instanceof AdjPair)) return false;
            return (u.equals(((AdjPair) obj).u) && v.equals(((AdjPair) obj).v))
                    /*|| (u.equals(((AdjPair) obj).v) && v.equals(((AdjPair) obj).u))*/;
        }

        @Override
        public String toString() {
            return "(" + u + " ,\t" + v + ")";
        }
    }


    protected void assertDataType(Operand x) {
        assert x.isDataType(dataType);
    }

    protected void addEdge(Operand u, Operand v) {
        assertDataType(u);
        assertDataType(v);
        AdjPair adjPair = new AdjPair(u, v);
        if (!(adjSet.contains(adjPair) || u.equals(v))) {
            adjSet.add(adjPair);
            adjSet.add(new AdjPair(v, u));
            logOut("\tAddEdge: " + u + "\t,\t" + v);
            if (!u.isPreColored(dataType)) {
                u.addAdj(v);
                u.degree++;
            }
            if (!v.isPreColored(dataType)) {
                v.addAdj(u);
                v.degree++;
            }
        }
    }

    public static void logOut(String s) {
        if (DEBUG_STDIN_OUT)
            System.err.println(s);
    }

    public void AllocateRegister(Machine.Program program) {
    }

    protected void fixStack(ArrayList<I> needFixList) {
        for (MachineInst mi : needFixList) {
            // fixStack
            Machine.McFunction mf = mi.getMb().mcFunc;
            if (mi instanceof I.Binary) {
                I.Binary binary = (I.Binary) mi;
                Machine.Operand off;
                int newOff = switch (binary.getFixType()) {
                    case VAR_STACK -> mf.getVarStack();
                    case ONLY_PARAM -> binary.getCallee().getParamStack();
                    default -> {
                        System.exit(110);
                        throw new AssertionError(binary + " of " + binary.getFixType());
                    }
                };
                if (newOff == 0) {
                    // TODO
                    binary.remove();
                } else {
                    if (CodeGen.immCanCode(newOff)) {
                        off = new Machine.Operand(I32, newOff);
                    } else {
                        off = Arm.Reg.getR(r11);
                        new I.Mov(off, new Machine.Operand(I32, newOff), binary);
                    }
                    binary.setROpd(off);
                }
                binary.clearNeedFix();
            } else if (mi.isIMov()) {
                I.Mov mv = (I.Mov) mi;
                Machine.Operand off = mv.getSrc();
                assert off.is_I_Imm();
                int newOff = mf.getTotalStackSize() + off.get_I_Imm();
                // mov a, #off
                // add add, sp, a
                // vldr.32 data, [add]
                if (mv.getFixType() == CodeGen.STACK_FIX.FLOAT_TOTAL_STACK) {
                    if (CodeGen.vLdrStrImmEncode(newOff)) {
                        V.Ldr vldr = (V.Ldr) mv.getNext().getNext();
                        vldr.setOffSet(new Machine.Operand(I32, newOff));
                        assert mv.getNext() instanceof I.Binary;
                        mv.getNext().remove();
                        mv.clearNeedFix();
                        mv.remove();
                    } else if (CodeGen.immCanCode(newOff)) {
                        assert mv.getNext() instanceof I.Binary;
                        I.Binary binary = (I.Binary) mv.getNext();
                        mv.clearNeedFix();
                        mv.remove();
                        binary.setROpd(new Machine.Operand(I32, newOff));
                    } else {
                        mv.setSrc(new Machine.Operand(I32, newOff));
                        mv.clearNeedFix();
                    }
                } else if (mv.getFixType() == CodeGen.STACK_FIX.INT_TOTAL_STACK) {
                    // mov dst, offImm
                    // ldr opd, [sp, dst]
                    if (CodeGen.LdrStrImmEncode(newOff)) {
                        I.Ldr ldr = (I.Ldr) mv.getNext();
                        ldr.setOffSet(new Machine.Operand(I32, newOff));
                        mv.clearNeedFix();
                        mv.remove();
                    } else {
                        mv.setSrc(new Machine.Operand(I32, newOff));
                        mv.clearNeedFix();
                    }
                } else {
                    System.exit(120);
                    throw new AssertionError(mv + " of " + mv.getFixType());
                }
            } else {
                System.exit(120);
                throw new AssertionError(mi + " of " + mi.getFixType());
            }
        }
    }

    void livenessAnalysis(Machine.McFunction mf) {
    }

    public static void liveInOutAnalysis(Machine.McFunction mf) {
        // 计算LiveIn和LiveOut
        boolean changed = true;
        while (changed) {
            changed = false;
            for (ILinkNode mb = mf.mbList.getEnd(); !mb.equals(mf.mbList.head); mb = mb.getPrev()) {
                final Machine.Block finalMb = (Machine.Block) mb;
                logOut(finalMb + "\t:\t" + finalMb.succMBs);
                // 任意succ的liveInSet如果有更新, 则可能更新 (只可能增加, 增量为newLiveOut) 当前MB的liveIn,
                // 且当前MB如果需要更新liveIn, 只可能新增且新增的Opd一定出自newLiveOut


                ArrayList<Operand> newLiveOut = new ArrayList<>();
                for (Machine.Block succMB : finalMb.succMBs) {
                    for (Operand liveIn : succMB.liveInSet) {
                        if (finalMb.liveOutSet.add(liveIn)) {
                            newLiveOut.add(liveIn);
                        }
                    }
                }
                if (newLiveOut.size() > 0) {
                    changed = true;
                }
                if (changed) {
                    for (Operand o : finalMb.liveOutSet) {
                        if (!finalMb.liveDefSet.contains(o)) {
                            finalMb.liveInSet.add(o);
                        }
                    }
                }

                logOut(((Machine.Block) mb).getDebugLabel() + " liveInSet:\t" + finalMb.liveInSet.toString());
                logOut(((Machine.Block) mb).getDebugLabel() + " liveOutSet:\t" + finalMb.liveOutSet.toString());
            }
        }
    }

    protected void dealDefUse(HashSet<Operand> live, MachineInst mi, Machine.Block mb) {
        ArrayList<Operand> defs = mi.defOpds;
        ArrayList<Operand> uses = mi.useOpds;
        if (defs.size() == 1) {
            Operand def = defs.get(0);
            // 构建冲突图
            if (def.needColor(dataType)) {
                live.add(def);
                // 该mi的def与当前所有活跃寄存器以及该指令的其他def均冲突
                for (Operand l : live) {
                    addEdge(l, def);
                }
                def.loopCounter += mb.bb.getLoopDep();
            }
            live.remove(def);
        } else if (defs.size() > 1) {
            // 一个指令的不同def也会相互冲突
            for (Operand def : defs) {
                if (def.needColor(dataType)) {
                    live.add(def);
                }
            }
            // defs.stream().filter(Operand::needColor).forEach(live::add);

            // 构建冲突图
            for (Operand def : defs) {
                if (def.needColor(dataType)) {
                    // 该mi的def与当前所有活跃寄存器以及该指令的其他def均冲突
                    for (Operand l : live) {
                        addEdge(l, def);
                    }
                }
            }
            // defs.stream().filter(Operand::needColor).forEach(def -> live.forEach(l -> addEdge(l, def)));

            for (Operand def : defs) {
                if (def.needColor(dataType)) {
                    live.remove(def);
                    def.loopCounter += mb.bb.getLoopDep();
                }
            }
        }

        // 使用的虚拟或预着色寄存器为活跃寄存器
        for (Operand use : uses) {
            if (use.needColor(dataType)) {
                live.add(use);
                use.loopCounter += mb.bb.getLoopDep();
            }
        }
    }


    /**
     * 获取有效冲突
     * x.adjOpdSet \ (selectStack u coalescedNodeSet)
     * 对于o, 除在selectStackList(冲突图中已删除的结点list), 和已合并的mov的src(dst在其他工作表中)
     *
     * @param x
     * @return x.adjOpdSet \ (selectStack u coalescedNodeSet)
     */
    protected HashSet<Operand> adjacent(Operand x) {
        HashSet<Operand> validConflictOpdSet = new HashSet<>(x.adjOpdSet);
        validConflictOpdSet.removeIf(r -> selectStack.contains(r) || coalescedNodeSet.contains(r));
        return validConflictOpdSet;
    }


    /**
     * 当 move y, x 已被合并, x.alias = y, x 被放入 coalescedNodeSet 中
     */
    protected Operand getAlias(Operand x) {
        assertDataType(x);
        while (coalescedNodeSet.contains(x)) {
            x = x.getAlias();
        }
        return x;
    }

    protected boolean ok(Operand t, Operand r) {
        return t.degree < K || t.isPreColored(dataType) || adjSet.contains(new AdjPair(t, r));
    }

    protected boolean adjOk(Operand v, Operand u) {
        for (var t : adjacent(v)) {
            if (!ok(t, u)) {
                return false;
            }
        }
        return true;
    }

    protected boolean conservative(HashSet<Operand> adjacent, HashSet<Operand> adjacent1) {
        HashSet<Operand> tmp = new HashSet<>(adjacent);
        tmp.addAll(adjacent1);
        int cnt = 0;
        for (Operand x : tmp) {
            if (x.degree >= K) {
                cnt++;
            }
        }
        return cnt < K;
    }

    protected void turnInit(Machine.McFunction mf){
        livenessAnalysis(mf);
        adjSet = new HashSet<>();
        // AdjPair.cnt = 0;
        simplifyWorkSet = new HashSet<>();
        freezeWorkSet = new HashSet<>();
        spillWorkSet = new HashSet<>();
        spilledNodeSet = new HashSet<>();
        coloredNodeList = new ArrayList<>();
        selectStack = new Stack<>();
        coalescedNodeSet = new HashSet<>();
    }
}
