package backend;

import lir.*;
import lir.Machine.Operand;
import manage.Manager;
import mir.type.DataType;
import util.ILinkNode;

import java.io.FileNotFoundException;
import java.util.*;

import static lir.Arm.Regs.GPRs;
import static lir.Arm.Regs.GPRs.sp;
import static mir.type.DataType.I32;

public class TrivialRegAllocator {
    Tools tools = new Tools();

    private final CodeGen CODEGEN = CodeGen.CODEGEN;
    private final Arm.Reg rSP = Arm.Reg.getR(sp);

    private final boolean DEBUG_STDIN_OUT = false;

    private int rk = 12;
    private int sk = 32;

    private int MAGIC_LIVE_INTERVAL = rk * 2;

    private DataType dataType = I32;

    void livenessAnalysis(Machine.McFunction mcFunc) {
        for (Machine.Block mb : mcFunc.mbList) {
            mb.liveUseSet = new HashSet<>();
            mb.defSet = new HashSet<>();
            for (MachineInst mi : mb.miList) {
                if (mi instanceof MIComment) continue;
                ArrayList<Operand> defs = mi.defOpds;
                ArrayList<Operand> uses = mi.useOpds;
                // liveuse 计算
                uses.forEach(use -> {
                    if (!use.isImm() && !mb.defSet.contains(use)) {
                        mb.liveUseSet.add(use);
                    }
                });
                // def 计算
                defs.forEach(def -> {
                    if (!def.isImm() && !mb.liveUseSet.contains(def)) {
                        mb.defSet.add(def);
                    }
                });
            }
            logOut(mb.getDebugLabel() + "\tdefSet:\t" + mb.defSet.toString());
            logOut(mb.getDebugLabel() + "\tuseSet:\t" + mb.liveUseSet.toString());
            mb.liveInSet = new HashSet<>(mb.liveUseSet);
            mb.liveOutSet = new HashSet<>();
        }

        // 计算LiveIn和LiveOut
        boolean changed = true;
        while (changed) {
            changed = false;
            for (ILinkNode mb = mcFunc.mbList.getEnd(); !mb.equals(mcFunc.mbList.head); mb = mb.getPrev()) {
                final Machine.Block finalMb = (Machine.Block) mb;
                // 1 a = , b =  ,c =  2, 3
                // 2 g = a + d
                // 3 return e + f
                // 任意succ的liveInSet如果有更新, 则可能更新 (只可能增加, 增量为newLiveOut) 当前MB的liveIn,
                // 且当前MB如果需要更新liveIn, 只可能新增且新增的Opd一定出自newLiveOut
                /*ArrayList<Operand> newLiveOut = new ArrayList<>();
                for (Machine.Block succMB : finalMb.succMB) {
                    for (Operand liveIn : succMB.liveInSet) {
                        if (finalMb.liveOutSet.add(liveIn)) {
                            newLiveOut.add(liveIn);
                        }
                    }
                }
                // finalMb.succMB.forEach(succMB ->
                //         succMB.liveInSet.forEach(liveIn -> {
                //             if (finalMb.liveOutSet.add(liveIn)) {
                //                 // logOut(liveIn.toString());
                //                 newLiveOut.add(liveIn);
                //             }
                //         }));
                changed = newLiveOut.size() > 0;

                // newLiveOut.retainAll(mb.defSet);
                // 从 newLiveOut 删除了不存在于 mb.defSet 的元素

                for (Operand newOut : newLiveOut) {
                    if (!finalMb.defSet.contains(newOut)) {
                        finalMb.liveInSet.add(newOut);
                    }
                }
                // newLiveOut.forEach(newOut -> {
                //     if (!finalMb.defSet.contains(newOut)) {
                //         finalMb.liveInSet.add(newOut);
                //     }
                // });
                */
                HashSet<Operand> newLiveOut = new HashSet<>();
                for (Machine.Block succ : finalMb.succMB) {
                    newLiveOut.addAll(succ.liveInSet);
                }
                if (!newLiveOut.equals(finalMb.liveOutSet)) {
                    changed = true;
                    finalMb.liveOutSet = newLiveOut;
                    finalMb.liveInSet = new HashSet<>(finalMb.liveUseSet);
                    for (Operand o : finalMb.liveOutSet) {
                        if (!finalMb.defSet.contains(o)) {
                            finalMb.liveInSet.add(o);
                        }
                    }
                }
                logOut(((Machine.Block) mb).getDebugLabel() + " liveInSet:\t" + finalMb.liveInSet.toString());
                logOut(((Machine.Block) mb).getDebugLabel() + " liveOutSet:\t" + finalMb.liveOutSet.toString());
            }
        }
    }

    /**
     * 图中冲突边 (u, v) 的集合, 如果(u, v) in adjSet, 则(v, u) in adjSet
     * 用于判断两个Operand是否相邻
     */
    HashSet<AdjPair> adjSet = new HashSet<>();

    /***
     * 结点, 工作表, 集合和栈的数据结构.
     * 下面的表和集合总是互不相交的, 并且每个结点斗数与一个且只属于一个集合或表
     */

    /**
     * 欲从图中删除的结点集
     * 初始为低度数的传送(mv)无关的结点集, 实际上在select_spill的时候会把下一轮需要挪出去的点放到这里
     */
    HashSet<Operand> simplifyWorkSet = new HashSet<>();


    /**
     * 低度数的传送有关的结点
     */
    HashSet<Operand> freezeWorkSet = new HashSet<>();

    /**
     * 高度数的结点
     */
    HashSet<Operand> spillWorkSet = new HashSet<>();

    /**
     * 在本轮中要被溢出的结点集合, 初始为空
     */
    HashSet<Operand> spilledNodeSet = new HashSet<>();

    /**
     * 已合并的寄存器集合. 当合并 u <- v 时, 将 v 加入到这个集合中, u 则被放回到某个工作表中
     */
    HashSet<Operand> coalescedNodeSet = new HashSet<>();
    /**
     * 已成功着色的结点集合
     */
    ArrayList<Operand> coloredNodeList = new ArrayList<>();
    /**
     * 包含从图中删除的临时寄存器的栈
     */
    Stack<Operand> selectStack = new Stack<>();

    /***
     * 传送指令的数据结构, 下面给出了5个由传送指令组成的集合,
     * 每一条传送指令都只在其中的一个集合中(执行完Build之后直到Main结束)
     */

    /**
     * 已经合并的传送指令的集合
     */
    HashSet<MIMove> coalescedMoveSet = new HashSet<>();

    /**
     * src 和 dst相冲突的传送指令集合
     */
    HashSet<MIMove> constrainedMoveSet = new HashSet<>();

    /**
     * 不再考虑合并的传送指令集合
     */
    HashSet<MIMove> frozenMoveSet = new HashSet<>();

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
    HashSet<MIMove> workListMoveSet = new HashSet<>();

    /**
     * 还未做好合并准备的传送指令的集合
     */
    HashSet<MIMove> activeMoveSet = new HashSet<>();

    public static class AdjPair {
        public Operand u;
        public Operand v;

        public AdjPair(Operand u, Operand v) {
            this.u = u;
            this.v = v;
        }

        @Override
        public int hashCode() {
            return u.hashCode() + v.hashCode();
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

    public Machine.McFunction curMF;
    public int MAX_DEGREE = Integer.MAX_VALUE >> 2;

    public void AllocateRegister(Machine.Program program) {
        for (Machine.McFunction mcFunc : program.funcList) {
            curMF = mcFunc;
            while (true) {
                livenessAnalysis(mcFunc);
                adjSet = new HashSet<>();
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

                // rk = Manager.MANAGER.RK;
                // sk = Manager.MANAGER.SK;
                for (int i = 0; i < rk; i++) {
                    Arm.Reg.getR(i).degree = MAX_DEGREE;
                }
                // for (int i = 0; i < sk; i++) {
                //     Arm.Reg.getS(i).degree = Integer.MAX_VALUE;
                // }

                logOut("RegAlloc Build start");
                build();
                logOut("RegAlloc Build end");

                logOut("curMF.vrList:\t" + curMF.vrList.toString());
                // makeWorkList
                for (Operand vr : curMF.vrList) {
                    // initial
                    if (vr.degree >= rk) {
                        spillWorkSet.add(vr);
                    } else if (nodeMoves(vr).size() > 0) {
                        freezeWorkSet.add(vr);
                    } else {
                        simplifyWorkSet.add(vr);
                    }
                }
                logOut("spillWorkSet:\t" + spillWorkSet.toString());
                logOut("freezeWorkSet:\t" + freezeWorkSet.toString());
                logOut("simplifyWorkSet:\t" + simplifyWorkSet.toString());

                while (simplifyWorkSet.size() + workListMoveSet.size() + freezeWorkSet.size() + spillWorkSet.size() > 0) {
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
                // Manager.MANAGER.outputMI();
                assignColors();
                // Manager.MANAGER.outputMI();
                if (spilledNodeSet.size() == 0) {
                    break;
                }
                logOut("needSpill");
                spilledNodeSet.forEach(this::dealSpillNode);
                logOut("endSpill");
                try {
                    Manager.MANAGER.outputMI();
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
            curMF.setUsedCalleeSavedRegs();
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
        for (Machine.Block mb : curMF.mbList) {
            offImm = new Operand(I32, curMF.getStackExceptParamSize());
            // generate a MILoad before first use, and a MIStore after last def
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
                if(srcMI instanceof MILoad){
                    logOut(x+"-------try to match--------"+srcMI.toString());
                }
                if (defs.size() > 0) {
                    assert defs.size() == 1;
                    Operand def = defs.get(0);
                    if (def.equals(x)) {
                        logOut(x+"-------match def--------"+def);
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
                            firstUse = srcMI;
                        }
                    }
                }
                if (checkCount++ > MAGIC_LIVE_INTERVAL) {
                    checkpoint();
                }
            }
            checkpoint();
        }
        if (toStack) {
            curMF.addStack(4);
        }
    }

    private void checkpoint() {
        if (toStack) {
            if (firstUse != null) {
                Operand offset = offImm;
                if (offImm.getImm() >= (1 << 12)) {
                    Operand dst = curMF.newVR();
                    MIMove mi = new MIMove(dst, offImm, firstUse);
                    logOut(String.format("+++++++%d Checkpoint insert {\t%s\t} before use:\t{\t%s\t}", dealSpillTimes, mi, firstUse));
                    offset = dst;
                }
                MILoad mi = new MILoad(curMF.getVR(vrIdx), rSP, offset, firstUse);
                logOut(String.format("+++++++%d Checkpoint insert {\t%s\t} before use:\t{\t%s\t}", dealSpillTimes, mi, firstUse));
                firstUse = null;
            }
            if (lastDef != null) {
                MachineInst insertAfter = lastDef;
                Operand offset = offImm;
                if (offImm.getImm() >= (1 << 12)) {
                    Operand dst = curMF.newVR();
                    insertAfter = new MIMove(lastDef, dst, offImm);
                    logOut(String.format("+++++++%d Checkpoint insert {\t%s\t} after def:\t{\t%s\t}", dealSpillTimes, insertAfter, lastDef));
                    offset = dst;
                }
                MIStore st = new MIStore(insertAfter, curMF.getVR(vrIdx), rSP, offset);
                logOut(String.format("+++++++%d Checkpoint insert {\t%s\t} after def:\t{\t%s\t}", dealSpillTimes, st, insertAfter));
                lastDef = null;
            }
            vrIdx = -1;
        }
        // TODO 计算生命周期长度
    }

    public void addEdge(Operand u, Operand v) {
        AdjPair adjPair = new AdjPair(u, v);
        if (!(adjSet.contains(adjPair) || u.equals(v))) {
            adjSet.add(adjPair);
            adjSet.add(new AdjPair(v, u));
            logOut("\tAddEdge: " + u + "\t,\t" + v);
            if (!u.isPreColored()) {
                u.addAdj(v);
                u.degree++;
            }
            if (!v.isPreColored()) {
                v.addAdj(u);
                v.degree++;
            }
        }
    }

    public void logOut(String s) {
        if (DEBUG_STDIN_OUT)
            System.err.println(s);
    }

    public void build() {
        for (Arm.Reg reg : Arm.Reg.getGPRPool()) {
            reg.loopCounter = 0;
            reg.degree = MAX_DEGREE;
            reg.adjOpdSet = new HashSet<>();
            reg.moveSet = new HashSet<>();
            reg.setAlias(null);
        }
        for (Arm.Reg reg : Arm.Reg.getFPRPool()) {
            reg.loopCounter = 0;
            reg.degree = MAX_DEGREE;
            reg.adjOpdSet = new HashSet<>();
            reg.moveSet = new HashSet<>();
            reg.setAlias(null);
        }
        for (Operand o : curMF.vrList) {
            o.loopCounter = 0;
            o.degree = 0;
            o.adjOpdSet = new HashSet<>();
            o.moveSet = new HashSet<>();
            o.setAlias(null);
        }
        // logOut("in build");
        for (ILinkNode mbNode = curMF.mbList.getEnd(); !mbNode.equals(curMF.mbList.head); mbNode = mbNode.getPrev()) {
            Machine.Block mb = (Machine.Block) mbNode;
            // 获取块的 liveOut
            logOut("build mb: " + mb.getDebugLabel());
            HashSet<Operand> live = new HashSet<>(mb.liveOutSet);
            for (ILinkNode iNode = mb.getEndMI(); !iNode.equals(mb.miList.head); iNode = iNode.getPrev()) {
                MachineInst mi = (MachineInst) iNode;
                if (mi.isComment()) continue;
                // TODO : 此时考虑了Call
                ArrayList<Operand> defs = mi.defOpds;
                ArrayList<Operand> uses = mi.useOpds;
                logOut(mi + "\tlive begin:\t" + live);
                if (mi.isMove()) {
                    MIMove mv = (MIMove) mi;
                    if (mv.directColor()) {
                        // 没有cond, 没有shift, src和dst都是虚拟寄存器的mov指令
                        // move 的 dst 和 src 不应是直接冲突的关系, 而是潜在的可合并的关系
                        // move a, b --> move rx, rx 需要a 和 b 不是冲突关系
                        live.remove(mv.getSrc());
                        mv.getDst().moveSet.add(mv);
                        mv.getSrc().moveSet.add(mv);
                        workListMoveSet.add(mv);
                    }
                }

                if (defs.size() == 1) {
                    Operand def = defs.get(0);
                    // 构建冲突图
                    if (def.needColor()) {
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
                        if (def.needColor()) {
                            live.add(def);
                        }
                    }
                    // defs.stream().filter(Operand::needColor).forEach(live::add);

                    // 构建冲突图
                    for (Operand def : defs) {
                        if (def.needColor()) {
                            // 该mi的def与当前所有活跃寄存器以及该指令的其他def均冲突
                            for (Operand l : live) {
                                addEdge(l, def);
                            }
                        }
                    }
                    // defs.stream().filter(Operand::needColor).forEach(def -> live.forEach(l -> addEdge(l, def)));

                    for (Operand def : defs) {
                        if (def.needColor()) {
                            live.remove(def);
                            def.loopCounter += mb.bb.getLoopDep();
                        }
                    }
                }

                // 使用的虚拟或预着色寄存器为活跃寄存器
                for (Operand use : uses) {
                    if (use.needColor()) {
                        live.add(use);
                        use.loopCounter += mb.bb.getLoopDep();
                    }
                }
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
    private HashSet<Operand> adjacent(Operand x) {
        HashSet<Operand> validConflictOpdSet = new HashSet<>(x.adjOpdSet);
        validConflictOpdSet.removeIf(r -> selectStack.contains(r) || coalescedNodeSet.contains(r));
        return validConflictOpdSet;
    }

    /**
     * x.moveSet 去掉
     * 1. 已经合并的传送指令的集合 coalescedMoveSet
     * 2. src 和 dst 相冲突的传送指令集合 constrainedMoveSet
     * 3. 不再考虑合并的传送指令集合 frozenMoveSet
     *
     * @param x
     * @return x.moveSet ∩ (activeMoveSet ∪ workListMoveSet)
     */
    private HashSet<MIMove> nodeMoves(Operand x) {
        HashSet<MIMove> canCoalesceSet = new HashSet<>(x.moveSet);
        canCoalesceSet.removeIf(r -> !(activeMoveSet.contains(r) || workListMoveSet.contains(r)));
        return canCoalesceSet;
    }

    /**
     * EnableMoves({x} ∪ Adjacent(x))
     * 有可能合并的传送指令从 activeMoveSet 挪到 workListMoveSet
     * @param x
     */
    /*
    private void enableMoves(Operand x) {
        for (MIMove mv : nodeMoves(x)) {
            // 考虑 x 关联的可能合并的 move
            if (activeMoveSet.contains(mv)) {
                // 未做好合并准备的集合如果包含mv, 就挪到workListMoveSet中
                activeMoveSet.remove(mv);
                workListMoveSet.add(mv);
            }
        }
        for (Operand adj : adjacent(x)) {
            // 对于o的每个实际邻接冲突adj
            for (MIMove mv : nodeMoves(adj)) {
                // adj关联的move, 如果是有可能合并的move
                if (activeMoveSet.contains(mv)) {
                    // 未做好合并准备的集合如果包含mv, 就挪到workListMoveSet中
                    activeMoveSet.remove(mv);
                    workListMoveSet.add(mv);
                }
            }
        }
    }*/

    /**
     * 从图中去掉一个结点需要减少该结点的当前各个邻结点的度数.
     * 如果某个邻结点的 degree < K - 1, 则这个邻结点一定是传送有关的,
     * (因为低度数结点有关 move 的已经放到 freezeWorkSet 里了, 无关 move 的已经放到 simplifyWorkSet 里了)
     * 因此不将它加入到 simplifyWorkSet 中.
     * 当邻结点adj的度数从 K 变为 K - 1时，与它(adj)的邻结点相关的传送指令将有可能变成可合并的
     *
     * @param x
     */
    private void decrementDegree(Operand x) {
        x.degree--;
        if (x.degree == rk - 1) {
            for (MIMove mv : nodeMoves(x)) {
                // 考虑 x 关联的可能合并的 move
                if (activeMoveSet.contains(mv)) {
                    // 未做好合并准备的集合如果包含mv, 就挪到workListMoveSet中
                    activeMoveSet.remove(mv);
                    workListMoveSet.add(mv);
                }
            }
            for (Operand adj : adjacent(x)) {
                // 对于o的每个实际邻接冲突adj
                for (MIMove mv : nodeMoves(adj)) {
                    // adj关联的move, 如果是有可能合并的move
                    if (activeMoveSet.contains(mv)) {
                        // 未做好合并准备的集合如果包含mv, 就挪到workListMoveSet中
                        activeMoveSet.remove(mv);
                        workListMoveSet.add(mv);
                    }
                }
            }
            // enableMoves(x);
            // TODO: 虎书上这里写的是remove
            // TODO 在 combine 的时候, v 和 u 虽然合并了, 对 v 的冲突邻结点做 decrementDegree, 但 v 的实际冲突邻结点个数仍然是 rk 个, 那为什么还要有度这个概念呢
            spillWorkSet.add(x);
            if (nodeMoves(x).size() > 0) {
                freezeWorkSet.add(x);
            } else {
                simplifyWorkSet.add(x);
            }
        }
    }

    /**
     * 当 move y, x 已被合并, x.alias = y, x 被放入 coalescedNodeSet 中
     */
    private Operand getAlias(Operand x) {
        while (coalescedNodeSet.contains(x)) {
            x = x.getAlias();
        }
        return x;
    }

    /**
     * 当 x 需要被染色, x 并不与 move 相关, x 的度 <= k - 1
     * 低度数传送有关结点集 freezeWorkSet 删除 x , 且低度数传送无关结点集 simplifyWorkSet 添加 x
     *
     * @param x
     */
    public void addWorkList(Operand x) {
        if (!x.isPreColored() && (nodeMoves(x).size() == 0) && x.degree < rk) {
            freezeWorkSet.remove(x);
            simplifyWorkSet.add(x);
            logOut(String.format("%s\t from freezeWorkSet to simplifyWorkSet", x));
        }
    }

    /**
     * 合并move u <- v
     * 1. u 预着色, v 是虚拟寄存器, 且 v 的冲突邻接点均满足: 要么为低度数结点, 要么预着色, 要么已经与 u 邻接
     * 2. u, v 都不是预着色, 且两者的邻接冲突结点的高结点个数加起来也不超过 rk - 1 个
     */

    public void combine(Operand u, Operand v) {
        if (freezeWorkSet.contains(v)) {
            freezeWorkSet.remove(v);
        } else {
            spillWorkSet.remove(v);
        }
        // 合并 move u, v, 将v加入 coalescedNodeSet
        coalescedNodeSet.add(v);
        v.setAlias(u);
        u.moveSet.addAll(v.moveSet);
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
        if (u.degree >= rk && freezeWorkSet.contains(u)) {
            freezeWorkSet.remove(u);
            spillWorkSet.add(u);
        }
    }

    //-------------------------------------------------------------------------------------------------
    public void coalesce() {
        // When workListMoveSet.size() > 0;
        MIMove mv = workListMoveSet.iterator().next();
        // u <- v
        Operand u = getAlias(mv.getDst());
        Operand v = getAlias(mv.getSrc());
        if (v.isPreColored()) {
            // 冲突图是无向图, 这里避免把mv归到受限一类而尽可能让v不是预着色的
            Operand tmp = u;
            u = v;
            v = tmp;
        }
        logOut(String.format("workListMoveSet.remove(%s)", mv));
        workListMoveSet.remove(mv);
        if (u.equals(v)) {
            coalescedMoveSet.add(mv);
            logOut(String.format("coalescedMoveSet.add(%s)", mv));
            addWorkList(u);
        } else if (v.isPreColored() || adjSet.contains(new AdjPair(u, v))) {
            // 这里似乎必须用adjSet判断
            // 两边都是预着色则不可能合并, 因为上面已经在 move u, v 的情况下将 u, v 互换, 如果v仍然是预着色说明u, v均为预着色
            constrainedMoveSet.add(mv);
            logOut(String.format("constrainedMoveSet.add(%s)", mv));
            addWorkList(u);
            addWorkList(v);
        } else {
            // 此时 v 已经不是预着色了
            // if (u.isPreColored()) {
            //     /**
            //      * v 的冲突邻接点是否均满足:
            //      * 要么为低度数结点, 要么预着色, 要么已经与 u 邻接
            //      */
            //     boolean flag = true;
            //     for (Operand adj : adjacent(v)) {
            //         if (adj.degree >= rk && !adj.isPreColored() && !adjSet.contains(new AdjPair(adj, u))) {
            //             // adjSet.contains(new AdjPair(adj, v))这个感觉可以改成 v.adjOpdSet.contains(adj)
            //             flag = false;
            //         }
            //     }
            //     if (flag) {
            //         coalescedMoveSet.add(mv);
            //         combine(u, v);
            //         addWorkList(u);
            //     } else {
            //         activeMoveSet.add(mv);
            //     }
            // } else {
            //     // union实际统计 u 和 v 的有效冲突邻接结点
            //     HashSet<Operand> union = new HashSet<>(u.adjOpdSet);
            //     union.removeIf(r -> selectStack.contains(r) || coalescedNodeSet.contains(r));
            //     union.addAll(v.adjOpdSet);
            //     // union.removeIf(r -> selectStack.contains(r) || coalescedNodeSet.contains(r));
            //     int cnt = 0;
            //     for (Operand x : union) {
            //         if (!selectStack.contains(x) && !coalescedNodeSet.contains(x) && x.degree >= rk) {
            //             // 统计union中的高度数结点个数
            //             // if (x.degree >= rk) {
            //             cnt++;
            //         }
            //     }
            //     // 如果结点个数 < rk 个表示未改变冲突图的可着色性
            //     if (cnt < rk) {
            //         coalescedMoveSet.add(mv);
            //         combine(u, v);
            //         addWorkList(u);
            //     } else {
            //         activeMoveSet.add(mv);
            //     }
            // }
            if ((u.isPreColored() && adjOk(v, u))
                    || (!u.isPreColored() && conservative(adjacent(u), adjacent(v)))) {
                coalescedMoveSet.add(mv);
                combine(u, v);
                addWorkList(u);
            } else {
                activeMoveSet.add(mv);
            }
        }
    }

    boolean ok(Operand t, Operand r) {
        return t.degree < rk || t.isPreColored() || adjSet.contains(new AdjPair(t, r));
    }

    ;

    boolean adjOk(Operand v, Operand u) {
        for (var t : adjacent(v)) {
            if (!ok(t, u)) {
                return false;
            }
        }
        return true;
    }

    ;

    private boolean conservative(HashSet<Operand> adjacent, HashSet<Operand> adjacent1) {
        HashSet<Operand> tmp = new HashSet<>(adjacent);
        tmp.addAll(adjacent1);
        int cnt = 0;
        for (Operand x : tmp) {
            if (x.degree >= rk) {
                cnt++;
            }
        }
        return cnt < rk;
    }

    /**
     * 对于每一个 x 相关的 move, 将其从可能合并的传送指令的集合 (activeMoveSet ∪ workListMoveSet)
     * 挪到 frozenMoveSet 中, 同时 对于 x 相关 move 的另一端的操作数 v
     * 如果 v 不是传送相关的低度数结点,
     * 则将 v 从低度数传送有关结点集 freezeWorkSet 挪到低度数传送无关结点集 simplifyWorkSet
     *
     * @param u
     */
    public void freezeMoves(Operand u) {
        for (MIMove mv : nodeMoves(u)) {
            // nodeMoves(x) 取出来的只可能是 activeMoveSet 中的或者 workListMoveSet 中的
            // if (!activeMoveSet.remove(mv)) {
            //     workListMoveSet.remove(mv);
            // }
            if (activeMoveSet.contains(mv)) {
                activeMoveSet.remove(mv);
            } else {
                workListMoveSet.remove(mv);
            }
            logOut(mv + "\t: activeMoveSet, workListMoveSet -> frozenMoveSet");
            frozenMoveSet.add(mv);

            // 这个很怪, 跟书上不一样
            // 选择 move 中非 x 方结点 v
            var v = mv.getDst().equals(u) ? mv.getSrc() : mv.getDst();
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
            if (nodeMoves(v).size() == 0 && v.degree < rk) {
                // nodeMoves(v) = v.moveSet ∩ (activeMoveSet ∪ workListMoveSet)
                freezeWorkSet.remove(v);
                simplifyWorkSet.add(v);
                logOut(v + "\t freezeWorkSet-> simplifyWorkSet");
            }
        }
    }

    public void assignColors() {
        logOut("Start to assign colors");
        HashMap<Operand, Operand> colorMap = new HashMap<>();
        while (selectStack.size() > 0) {
            Operand toBeColored = selectStack.pop();
            assert !toBeColored.isPreColored();
            logOut("when try assign:\t" + toBeColored);
            final TreeSet<Arm.Regs> okColorSet = new TreeSet<>(Arrays.asList(GPRs.values()).subList(0, rk));
            // logOut("--- rk = \t"+rk);

            // 把待分配颜色的结点的邻接结点的颜色去除
            toBeColored.adjOpdSet.forEach(adj -> {
                Operand a = getAlias(adj);
                if (a.hasReg()) {
                    // 已着色或者预分配
                    okColorSet.remove(a.getReg());
                } else if (a.isVirtual()) {
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
                Arm.Regs color = okColorSet.iterator().next();
                // Arm.Regs color = okColorSet.pollLast();
                logOut("Choose " + color);
                colorMap.put(toBeColored, new Operand(color));
                // if (color instanceof GPRs) {
                //     colorMap.put(toBeColored, Arm.Reg.getR((GPRs) color));
                // } else if (color instanceof Arm.Regs.FPRs) {
                //     colorMap.put(toBeColored, Arm.Reg.getS((Arm.Regs.FPRs) color));
                // } else {
                //     throw new AssertionError("");
                // }
            }
        }

        if (spilledNodeSet.size() > 0) {
            return;
        }

        for (Operand v : coalescedNodeSet) {
            Operand a = getAlias(v);
            colorMap.put(v, a.isPreColored() ? a : colorMap.get(a));
        }

        for (Machine.Block mb : curMF.mbList) {
            for (MachineInst mi : mb.miList) {
                // TODO 这里不考虑Call
                if (mi.isCall() || mi.isComment()) continue;
                // logOut("Consider " + mi);
                ArrayList<Operand> defs = mi.defOpds;
                ArrayList<Operand> uses = mi.useOpds;
                if (defs.size() > 0) {
                    assert defs.size() == 1; // 只要有def, 除Call外均为1
                    Operand o = colorMap.get(defs.get(0));
                    if (o != null) {
                        logOut("- Def\t" + defs.get(0) + "\tassign: " + o);
                        defs.set(0, o);
                    }
                }

                for (int i = 0; i < uses.size(); i++) {
                    assert uses.get(i) != null;
                    Operand set = colorMap.get(uses.get(i));
                    if (set != null) {
                        logOut("- Use\t" + uses.get(i) + "\tassign: " + set);
                        mi.setUse(i, set);
                    }
                }
            }
        }
    }
}
