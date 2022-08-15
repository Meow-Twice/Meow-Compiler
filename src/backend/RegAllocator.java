package backend;

import lir.*;
import lir.MC.Operand;
import lir.MachineInst.MachineMove;
import mir.type.DataType;
import util.CenterControl;
import util.ILinkNode;

import java.util.*;

import static lir.Arm.Regs.GPRs.r11;
import static lir.Arm.Regs.GPRs.sp;
import static mir.type.DataType.F32;
import static mir.type.DataType.I32;

public class RegAllocator {
    protected MC.McFunction curMF;
    public static final int SP_ALIGN = 2 * 4;
    protected static final boolean DEBUG_STDIN_OUT = false;

    protected int K;
    protected final int RK = 14;
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

    public void AllocateRegister(MC.Program program) {
    }

    protected void fixStack(ArrayList<I> needFixList) {
        for (MachineInst mi : needFixList) {
            // fixStack
            MC.McFunction mf = mi.getMb().mf;
            if (mi instanceof I.Binary) {
                I.Binary binary = (I.Binary) mi;
                MC.Operand off;
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
                        off = new MC.Operand(I32, newOff);
                    } else {
                        off = Arm.Reg.getR(r11);
                        new I.Mov(off, new MC.Operand(I32, newOff), binary);
                    }
                    binary.setROpd(off);
                }
                binary.clearNeedFix();
            } else if (mi.isIMov()) {
                I.Mov mv = (I.Mov) mi;
                MC.Operand off = mv.getSrc();
                assert off.is_I_Imm();
                int newOff = mf.getTotalStackSize() + off.get_I_Imm();
                // mov a, #off
                // add add, sp, a
                // vldr.32 data, [add]
                if (mv.getFixType() == CodeGen.STACK_FIX.FLOAT_TOTAL_STACK) {
                    if (CenterControl._FixStackWithPeepHole && CodeGen.vLdrStrImmEncode(newOff) && mv.getNext() instanceof I.Ldr) {
                        V.Ldr vldr = (V.Ldr) mv.getNext().getNext();
                        vldr.setUse(0, Arm.Reg.getRSReg(sp));
                        vldr.setOffSet(new MC.Operand(I32, newOff));
                        assert mv.getNext() instanceof I.Binary;
                        mv.getNext().remove();
                        mv.clearNeedFix();
                        mv.remove();
                    } else if (CenterControl._FixStackWithPeepHole && CodeGen.immCanCode(newOff) && mv.getNext() instanceof I.Binary) {
                        assert mv.getNext() instanceof I.Binary;
                        I.Binary binary = (I.Binary) mv.getNext();
                        mv.clearNeedFix();
                        mv.remove();
                        binary.setROpd(new MC.Operand(I32, newOff));
                    } else {
                        mv.setSrc(new MC.Operand(I32, newOff));
                        mv.clearNeedFix();
                    }
                } else if (mv.getFixType() == CodeGen.STACK_FIX.INT_TOTAL_STACK) {
                    // mov dst, offImm
                    // ldr opd, [sp, dst]
                    if (CenterControl._FixStackWithPeepHole && CodeGen.LdrStrImmEncode(newOff) && mv.getNext() instanceof I.Ldr) {
                        I.Ldr ldr = (I.Ldr) mv.getNext();
                        ldr.setOffSet(new MC.Operand(I32, newOff));
                        mv.clearNeedFix();
                        mv.remove();
                    } else {
                        mv.setSrc(new MC.Operand(I32, newOff));
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

    public static void liveInOutAnalysis(MC.McFunction mf) {
        // 计算LiveIn和LiveOut
        boolean changed = true;
        while (changed) {
            changed = false;
            for (ILinkNode mb = mf.mbList.getEnd(); !mb.equals(mf.mbList.head); mb = mb.getPrev()) {
                final MC.Block finalMb = (MC.Block) mb;
                logOut(finalMb + "\t:\t" + finalMb.succMBs);
                // 任意succ的liveInSet如果有更新, 则可能更新 (只可能增加, 增量为newLiveOut) 当前MB的liveIn,
                // 且当前MB如果需要更新liveIn, 只可能新增且新增的Opd一定出自newLiveOut


                ArrayList<Operand> newLiveOut = new ArrayList<>();
                for (MC.Block succMB : finalMb.succMBs) {
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
                logOut(((MC.Block) mb).getLabel() + " liveInSet:\t" + finalMb.liveInSet.toString());
                logOut(((MC.Block) mb).getLabel() + " liveOutSet:\t" + finalMb.liveOutSet.toString());
            }
        }
    }

    protected void dealDefUse(HashSet<Operand> live, MachineInst mi, MC.Block mb) {
        ArrayList<Operand> defs = mi.defOpds;
        ArrayList<Operand> uses = mi.useOpds;
        int loopDepth = (mb.bb.getLoopDep());
        if (defs.size() == 1) {
            Operand def = defs.get(0);
            // 构建冲突图
            if (def.needColor(dataType)) {
                live.add(def);
                // 该mi的def与当前所有活跃寄存器以及该指令的其他def均冲突
                for (Operand l : live) {
                    addEdge(l, def);
                }
                def.loopCounter += loopDepth;
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
                    def.loopCounter += loopDepth;
                }
            }
        }

        // 使用的虚拟或预着色寄存器为活跃寄存器
        for (Operand use : uses) {
            if (use.needColor(dataType)) {
                live.add(use);
                use.loopCounter += loopDepth;
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

    /*protected boolean ok(Operand t, Operand r) {
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
    }*/

    protected void turnInit(MC.McFunction mf) {
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
        coalescedMoveSet = new HashSet<>();
        constrainedMoveSet = new HashSet<>();
        frozenMoveSet = new HashSet<>();
        workListMoveSet = new HashSet<>();
        activeMoveSet = new HashSet<>();
    }

    protected void livenessAnalysis(MC.McFunction mf) {
        for (MC.Block mb : mf.mbList) {
            mb.liveUseSet = new HashSet<>();
            mb.liveDefSet = new HashSet<>();
            for (MachineInst mi : mb.miList) {
                // TODO
                if (mi instanceof MIComment || (dataType == F32 && !(mi instanceof V))) continue;

                // liveuse 计算
                for (Operand use : mi.useOpds) {
                    if (use.needRegOf(dataType) && !mb.liveDefSet.contains(use)) mb.liveUseSet.add(use);
                }
                // def 计算
                for (Operand def : mi.defOpds) {
                    if (def.needRegOf(dataType) && !mb.liveUseSet.contains(def)) mb.liveDefSet.add(def);
                }
            }
            logOut(mb.getLabel() + "\tdefSet:\t" + mb.liveDefSet.toString());
            logOut(mb.getLabel() + "\tuseSet:\t" + mb.liveUseSet.toString());
            mb.liveInSet = new HashSet<>(mb.liveUseSet);
            mb.liveOutSet = new HashSet<>();
        }

        liveInOutAnalysis(mf);
    }

    protected void build() {
        for (ILinkNode mbNode = curMF.mbList.getEnd(); !mbNode.equals(curMF.mbList.head); mbNode = mbNode.getPrev()) {
            MC.Block mb = (MC.Block) mbNode;
            // 获取块的 liveOut
            logOut("build mb: " + mb.getLabel());
            HashSet<Operand> live = new HashSet<>(mb.liveOutSet);
            for (ILinkNode iNode = mb.getEndMI(); !iNode.equals(mb.miList.head); iNode = iNode.getPrev()) {
                MachineInst mi = (MachineInst) iNode;
                if (mi.isComment()) continue;
                // TODO : 此时考虑了Call
                logOut(mi + "\tlive begin:\t" + live);
                if (mi.isMovOfDataType(dataType)) {
                    MachineInst.MachineMove mv = (MachineInst.MachineMove) mi;
                    if (mv.directColor()) {
                        // 没有cond, 没有shift, src和dst都是虚拟寄存器的mov指令
                        // move 的 dst 和 src 不应是直接冲突的关系, 而是潜在的可合并的关系
                        // move a, b --> move rx, rx 需要a 和 b 不是冲突关系
                        live.remove(mv.getSrc());
                        mv.getDst().movSet.add(mv);
                        mv.getSrc().movSet.add(mv);
                        workListMoveSet.add(mv);
                    }
                }

                dealDefUse(live, mi, mb);
            }
        }
    }

    protected void regAllocIteration() {
        logOut("spillWorkSet:\t" + spillWorkSet.toString());
        logOut("freezeWorkSet:\t" + freezeWorkSet.toString());
        logOut("simplifyWorkSet:\t" + simplifyWorkSet.toString());

        while (simplifyWorkSet.size() + workListMoveSet.size() + freezeWorkSet.size() + spillWorkSet.size() > 0) {
            // TODO 尝试验证if - else if结构的可靠性和性能

            if (simplifyWorkSet.size() > 0) {
                logOut("-- simplify");
                logOut(simplifyWorkSet.toString());
                // 从度数低的结点集中随机选择一个从图中删除放到 selectStack 里
                Operand x = simplifyWorkSet.iterator().next();
                simplifyWorkSet.remove(x);
                selectStack.push(x);
                logOut(String.format("selectStack.push(%s)", x));
                for (Operand adj : x.adjOpdSet) {
                    // 对于 x 的邻接冲突结点adj, 如果不是已经被删除的或者已合并的
                    if (!(selectStack.contains(adj) || coalescedNodeSet.contains(adj))) {
                        logOut(String.format("decrementDegree(%s)", adj));
                        decrementDegree(adj);
                    }
                }
                // adjacent(x).forEach(this::decrementDegree);
            }
            if (workListMoveSet.size() > 0) {
                logOut("-- coalesce");
                logOut("workListMoveSet:\t" + workListMoveSet);
                coalesce();
            }
            if (freezeWorkSet.size() > 0) {
                logOut("freeze");
                /**
                 * 从低度数的传送有关的结点中随机选择一个进行冻结
                 */
                Operand x = freezeWorkSet.iterator().next();
                freezeWorkSet.remove(x);
                simplifyWorkSet.add(x);
                logOut(x + "\t" + "freezeWorkSet -> simplifyWorkSet");
                freezeMoves(x);
            }
            if (spillWorkSet.size() > 0) {
                logOut("selectSpill");
                /**
                 * 从高度数结点集(spillWorkSet)中启发式选取结点 x , 挪到低度数结点集(simplifyWorkSet)中
                 * 冻结 x 及其相关 move
                 */
                Iterator<Operand> it = spillWorkSet.iterator();
                Operand x = it.next();
                assert x != null;
                double max = x.heuristicVal();
                while (it.hasNext()) {
                    Operand o = it.next();
                    double h = o.heuristicVal();
                    if (h > max) {
                        x = o;
                        max = h;
                    }
                }
                logOut("select: " + x + "\t" + "add to simplifyWorkSet");
                // Operand x = spillWorkSet.stream().reduce((a, b) -> a.heuristicVal() < b.heuristicVal() ? a : b).orElseThrow();
                // Operand x = spillWorkSet.stream().reduce(Operand::select).orElseThrow();
                // TODO 为什么这里可以先挪到simplifyWorkSet里面啊
                // simplifyWorkSet真正含义是希望将结点移出冲突图
                simplifyWorkSet.add(x);
                freezeMoves(x);
                spillWorkSet.remove(x);
                logOut("select: " + x + "\t" + "remove from spillWorkSet");
            }
        }
    }


    /***
     * 传送指令的数据结构, 下面给出了5个由传送指令组成的集合,
     * 每一条传送指令都只在其中的一个集合中(执行完Build之后直到Main结束)
     */

    /**
     * 已经合并的传送指令的集合
     */
    HashSet<MachineMove> coalescedMoveSet = new HashSet<>();

    /**
     * src 和 dst相冲突的传送指令集合
     */
    HashSet<MachineMove> constrainedMoveSet = new HashSet<>();

    /**
     * 不再考虑合并的传送指令集合
     */
    HashSet<MachineMove> frozenMoveSet = new HashSet<>();

    /**
     * 有可能合并的传送指令, 当结点x从高度数结点变为低度数结点时,
     * 与 x 的邻接点关联的传送指令必须添加到传送指令工作表 workListMoveSet .
     * 此时原来因为合并后会有太多高度数邻结点而不能合并的传送指令现在则可能变成可合并的.
     * 只在下面少数几种情况下传送指令才会加入到工作表 workListMoveSet :
     * 1. 在简化期间, 删除一个结点可能导致其邻结点 x 的度数发生变化.
     * 因此要把与 x 的邻结点相关联的传送指令加入到 workListMoveSet 中.
     * 2. 当合并 u 和 v 时,可能存在一个与 u 和 v 都有冲突的结点 x .
     * 因为 x 现在只于 u 和 v 合并后的这个结点相冲突, 故 x 的度将减少,
     * 因此也要把与 x 的邻结点关联的传送指令加入到 workListMoveSet 中.
     * 如果 x 是传送有关的, 则与 x 本身关联的传送指令也要加入到此表中,
     * 因为 u 和 v 有可能都是高度数的结点
     */
    HashSet<MachineMove> workListMoveSet = new HashSet<>();

    /**
     * 还未做好合并准备的传送指令的集合
     */
    HashSet<MachineMove> activeMoveSet = new HashSet<>();

    /**
     * x.vMovSet 去掉
     * 1. 已经合并的传送指令的集合 coalescedMoveSet
     * 2. src 和 dst 相冲突的传送指令集合 constrainedMoveSet
     * 3. 不再考虑合并的传送指令集合 frozenMoveSet
     *
     * @param x
     * @return x.vMovSet ∩ (activeVMovSet ∪ workListVMovSet)
     */
    protected HashSet<MachineMove> nodeMoves(Operand x) {
        HashSet<MachineMove> canCoalesceSet = new HashSet<>(x.movSet);
        canCoalesceSet.removeIf(r -> !(activeMoveSet.contains(r) || workListMoveSet.contains(r)));
        return canCoalesceSet;
    }


    /**
     * 从图中去掉一个结点需要减少该结点的当前各个邻结点的度数.
     * 如果某个邻结点的 degree < K - 1, 则这个邻结点一定是传送有关的,
     * (因为低度数结点有关 move 的已经放到 freezeWorkSet 里了, 无关 move 的已经放到 simplifyWorkSet 里了)
     * 因此不将它加入到 simplifyWorkSet 中.
     * 当邻结点adj的度数从 K 变为 K - 1时，与它(adj)的邻结点相关的传送指令将有可能变成可合并的
     *
     * @param x
     */
    protected void decrementDegree(Operand x) {
        x.degree--;
        if (x.degree == K - 1) {
            for (MachineMove mv : nodeMoves(x)) {
                // 考虑 x 关联的可能合并的 move
                if (activeMoveSet.contains(mv)) {
                    // 未做好合并准备的集合如果包含mv, 就挪到workListVMovSet中
                    activeMoveSet.remove(mv);
                    workListMoveSet.add(mv);
                }
            }
            for (Operand adj : adjacent(x)) {
                // 对于o的每个实际邻接冲突adj
                for (MachineMove mv : nodeMoves(adj)) {
                    // adj关联的move, 如果是有可能合并的move
                    if (activeMoveSet.remove(mv)) {
                        // 未做好合并准备的集合如果包含mv, 就挪到workListVMovSet中
                        workListMoveSet.add(mv);
                    }
                }
            }
            // enableMoves(x);
            // TODO: 虎书上这里写的是remove
            // TODO 在 combine 的时候, v 和 u 虽然合并了, 对 v 的冲突邻结点做 decrementDegree, 但 v 的实际冲突邻结点个数仍然是 K 个, 那为什么还要有度这个概念呢
            spillWorkSet.add(x);
            if (nodeMoves(x).size() > 0) {
                freezeWorkSet.add(x);
            } else {
                simplifyWorkSet.add(x);
            }
        }
    }

    /**
     * 当 x 需要被染色, x 并不与 move 相关, x 的度 <= k - 1
     * 低度数传送有关结点集 freezeWorkSet 删除 x , 且低度数传送无关结点集 simplifyWorkSet 添加 x
     *
     * @param x
     */
    protected void addWorkList(Operand x) {
        if (!x.isPreColored(dataType) && (nodeMoves(x).size() == 0) && x.degree < K) {
            freezeWorkSet.remove(x);
            simplifyWorkSet.add(x);
            logOut(String.format("%s\t from freezeWorkSet to simplifyWorkSet", x));
        }
    }


    /**
     * 合并move u <- v
     * 1. u 预着色, v 是虚拟寄存器, 且 v 的冲突邻接点均满足: 要么为低度数结点, 要么预着色, 要么已经与 u 邻接
     * 2. u, v 都不是预着色, 且两者的邻接冲突结点的高结点个数加起来也不超过 K - 1 个
     */

    protected void combine(Operand u, Operand v) {
        if (freezeWorkSet.contains(v)) {
            freezeWorkSet.remove(v);
        } else {
            spillWorkSet.remove(v);
        }
        // 合并 move u, v, 将v加入 coalescedNodeSet
        coalescedNodeSet.add(v);
        v.setAlias(u);
        u.movSet.addAll(v.movSet);
        // 对于 v 在冲突图上的每个邻结点 adj , 建立 adj, u 之间的冲突边, 且为t

        for (Operand adj : v.adjOpdSet) {
            if (!(selectStack.contains(adj) || coalescedNodeSet.contains(adj))) {
                addEdge(adj, u);
                decrementDegree(adj);
            }
        }
        // adjacent(v).forEach(adj -> {
        //     addEdge(adj, u);
        //     decrementDegree(adj);
        // });
        // 当 u 从(合并前的)低度数结点成为(合并后的)高度数结点, 则将其从freezeWorkSet转移到 spillWorkSet
        if (u.degree >= K && freezeWorkSet.contains(u)) {
            freezeWorkSet.remove(u);
            spillWorkSet.add(u);
        }
    }

    protected void coalesce() {
        // When workListVMovSet.size() > 0;
        MachineMove mv = workListMoveSet.iterator().next();
        // u <- v
        Operand u = getAlias(mv.getDst());
        Operand v = getAlias(mv.getSrc());
        if (v.isPreColored(dataType)) {
            // 冲突图是无向图, 这里避免把mv归到受限一类而尽可能让v不是预着色的
            Operand tmp = u;
            u = v;
            v = tmp;
        }
        logOut(String.format("workListVMovSet.remove(%s)", mv));
        workListMoveSet.remove(mv);
        if (u.equals(v)) {
            coalescedMoveSet.add(mv);
            logOut(String.format("coalescedMoveSet.add(%s)", mv));
            addWorkList(u);
        } else if (v.isPreColored(dataType) || adjSet.contains(new AdjPair(u, v))) {
            // 这里似乎必须用adjSet判断
            // 两边都是预着色则不可能合并, 因为上面已经在 move u, v 的情况下将 u, v 互换, 如果v仍然是预着色说明u, v均为预着色
            constrainedMoveSet.add(mv);
            logOut(String.format("constrainedMoveSet.add(%s)", mv));
            addWorkList(u);
            addWorkList(v);
        } else {
            // TODO 尝试重新验证写法
            // 此时 v 已经不是预着色了
            if (u.isPreColored(dataType)) {
                /**
                 * v 的冲突邻接点是否均满足:
                 * 要么为低度数结点, 要么预着色, 要么已经与 u 邻接
                 */
                boolean flag = true;
                for (Operand adj : adjacent(v)) {
                    if (adj.degree >= K && !adj.isPreColored(dataType) && !adjSet.contains(new AdjPair(adj, u))) {
                        // adjSet.contains(new AdjPair(adj, v))这个感觉可以改成 v.adjOpdSet.contains(adj)
                        flag = false;
                    }
                }
                if (flag) {
                    coalescedMoveSet.add(mv);
                    combine(u, v);
                    addWorkList(u);
                } else {
                    activeMoveSet.add(mv);
                }
            } else {
                // union实际统计 u 和 v 的有效冲突邻接结点
                HashSet<Operand> union = new HashSet<>(u.adjOpdSet);
                union.removeIf(r -> selectStack.contains(r) || coalescedNodeSet.contains(r));
                union.addAll(v.adjOpdSet);
                int cnt = 0;
                for (Operand x : union) {
                    if (!selectStack.contains(x) && !coalescedNodeSet.contains(x) && x.degree >= K) {
                        // 统计union中的高度数结点个数
                        // if (x.degree >= K) {
                        cnt++;
                    }
                }
                // 如果结点个数 < K 个表示未改变冲突图的可着色性
                if (cnt < K) {
                    coalescedMoveSet.add(mv);
                    combine(u, v);
                    addWorkList(u);
                } else {
                    activeMoveSet.add(mv);
                }
            }
            /*if ((u.isPreColored(dataType) && adjOk(v, u))
                    || (!u.isPreColored(dataType) && conservative(adjacent(u), adjacent(v)))) {
                coalescedMoveSet.add(mv);
                combine(u, v);
                addWorkList(u);
            } else {
                activeMoveSet.add(mv);
            }*/
        }
    }

    protected void freezeMoves(Operand u) {
        for (MachineMove mv : nodeMoves(u)) {
            // nodeMoves(x) 取出来的只可能是 activeVMovSet 中的或者 workListVMovSet 中的
            // if (!activeVMovSet.remove(mv)) {
            //     workListVMovSet.remove(mv);
            // }
            if (activeMoveSet.contains(mv)) {
                activeMoveSet.remove(mv);
            } else {
                workListMoveSet.remove(mv);
            }
            logOut(mv + "\t: activeVMovSet, workListVMovSet -> frozenMoveSet");
            frozenMoveSet.add(mv);

            // 这个很怪, 跟书上不一样
            // 选择 move 中非 x 方结点 v
            var v = mv.getDst().equals(u) ? mv.getSrc() : mv.getDst();
            // TODO 尝试验证另一写法
            // Operand v = mv.getDst();
            // if (v.equals(u)) v = mv.getSrc();
            /*// 虎书:
            Operand v;
            if(src.alias.equals(x.alias)){
                v = dst.alias;
            }else{
                v = src.alias;
            }
            */

            // 如果 v 不是传送相关的低度数结点, 则将 v 从低度数传送有关结点集 freezeWorkSet 挪到低度数传送无关结点集 simplifyWorkSet
            if (nodeMoves(v).size() == 0 && v.degree < K) {
                // nodeMoves(v) = v.vMovSet ∩ (activeVMovSet ∪ workListVMovSet)
                freezeWorkSet.remove(v);
                simplifyWorkSet.add(v);
                logOut(v + "\t freezeWorkSet-> simplifyWorkSet");
            }
        }
    }

    HashMap<Operand, Operand> colorMap = new HashMap<>();

    protected TreeSet<Arm.Regs> getOkColorSet() {
        System.exit(153);
        throw new AssertionError("fuck getOkColor");
    }

    protected void preAssignColors() {
        logOut("Start to assign colors");
        colorMap = new HashMap<>();
        while (selectStack.size() > 0) {
            Operand toBeColored = selectStack.pop();
            assert !toBeColored.isPreColored(dataType) && !toBeColored.isAllocated();
            logOut("when try assign:\t" + toBeColored);
            final TreeSet<Arm.Regs> okColorSet = getOkColorSet();
            // logOut("--- K = \t"+K);

            // 把待分配颜色的结点的邻接结点的颜色去除
            toBeColored.adjOpdSet.forEach(adj -> {
                Operand a = getAlias(adj);
                if (a.hasReg() && a.isDataType(dataType)) {
                    // 已着色或者预分配
                    okColorSet.remove(a.getReg());
                } else if (a.isVirtual(dataType)) {
                    Operand r = colorMap.get(a);
                    if (r != null) {
                        okColorSet.remove(r.reg);
                    }
                }
            });
            logOut(okColorSet.toString());

            if (okColorSet.isEmpty()) {
                // 如果没有可分配的颜色则溢出
                spilledNodeSet.add(toBeColored);
            } else {
                // 如果有可分配的颜色则从可以分配的颜色中选取一个
                // TODO 尝试重新验证写法
                Arm.Regs color = okColorSet.pollFirst();
                logOut("Choose " + color);
                colorMap.put(toBeColored, Arm.Reg.getRSReg(color));
            }
        }
    }

}
