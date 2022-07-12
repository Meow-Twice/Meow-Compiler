package backend;

import lir.*;
import lir.Machine.Operand;
import manage.Manager;
import mir.type.DataType;

import java.util.*;
import java.util.stream.Stream;

import static lir.Arm.Regs.GPRs.*;
import static lir.Arm.Regs.GPRs.sp;
import static mir.type.DataType.I32;

public class TrivialRegAllocator {
    Tools tools = new Tools();

    private final CodeGen CODEGEN = CodeGen.CODEGEN;
    private final Arm.Reg rSP = Arm.Reg.getR(sp);

    private int rk = 0;
    private int sk = 0;

    private DataType dataType = I32;

    void livenessAnalysis(Machine.McFunction mcFunc) {
        for (Machine.Block mb : mcFunc.mbList) {
            mb.liveUseSet.clear();
            mb.defSet.clear();
            for (MachineInst mi : mb.miList) {
                ArrayList<Operand> defs = mi.defOpds;
                ArrayList<Operand> uses = mi.useOpds;
                // liveuse 计算
                uses.forEach(use -> {
                    if (!use.isImm() && mb.defSet.contains(use)) {
                        mb.liveUseSet.add(use);
                    }
                });
                // def 计算
                defs.forEach(def -> {
                    if (!def.isImm() && mb.liveUseSet.contains(def)) {
                        mb.defSet.add(def);
                    }
                });
            }
            mb.liveInSet = mb.liveUseSet;
            mb.liveOutSet.clear();

        }

        // 计算LiveIn和LiveOut
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Machine.Block mb = mcFunc.mbList.getEnd(); !mb.equals(mcFunc.mbList.head); mb = (Machine.Block) mb.getPrev()) {
                // 任意succ的liveInSet如果有更新, 则可能更新 (只可能增加, 增量为newLiveOut) 当前MB的liveIn,
                // 且当前MB如果需要更新liveIn, 只可能新增且新增的Opd一定出自newLiveOut
                HashSet<Operand> newLiveOut = new HashSet<>();
                for (Machine.Block succMB : mb.successor) {
                    for (Operand liveIn : succMB.liveInSet) {
                        if (mb.liveOutSet.add(liveIn)) {
                            newLiveOut.add(liveIn);
                        }
                    }
                }
                changed = newLiveOut.size() > 0;
                // newLiveOut.retainAll(mb.defSet);
                // 从 newLiveOut 删除了不存在于 mb.defSet 的元素

                for (Operand newOut : newLiveOut) {
                    if (!mb.defSet.contains(newOut)) {
                        mb.liveInSet.add(newOut);
                    }
                }
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
     * 低度数的传送(mv)无关的结点
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
            return u.equals(((AdjPair) obj).u) && v.equals(((AdjPair) obj).v);
        }
    }

    public Machine.McFunction curMF;

    public void AllocateRegister(Machine.Program program) {
        for (Machine.McFunction mcFunc : program.funcList) {
            curMF = mcFunc;
            boolean unDone = true;
            while (unDone) {
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

                rk = Manager.MANAGER.RK;
                sk = Manager.MANAGER.SK;
                for (int i = 0; i < rk; i++) {
                    Arm.Reg.getR(i).degree = Integer.MAX_VALUE;
                }
                for (int i = 0; i < sk; i++) {
                    Arm.Reg.getS(i).degree = Integer.MAX_VALUE;
                }

                build();
                makeWorkList();
                while (simplifyWorkSet.size() > 0 || workListMoveSet.size() > 0 || freezeWorkSet.size() > 0 || spillWorkSet.size() > 0) {
                    if (simplifyWorkSet.size() > 0) {
                        simplify();
                    }
                    if (workListMoveSet.size() > 0) {
                        coalesce();
                    }
                    if (freezeWorkSet.size() > 0) {
                        freeze();
                    }
                    if (spillWorkSet.size() > 0) {
                        selectSpill();
                    }
                }
                assignColors();
                unDone = spilledNodeSet.size() > 0;
                if (!unDone) {
                    continue;
                }
                spilledNodeSet.forEach(this::dealSpillNode);
            }

        }
    }

    int vrIdx = -1;
    MachineInst firstUse = null;
    MachineInst lastDef = null;
    Operand offImm;

    private void dealSpillNode(Operand x) {
        for (Machine.Block mb : curMF.mbList) {
            int curOffset = curMF.getStackSize();
            offImm = new Operand(I32, curOffset);
            // generate a MILoad before first use, and a MIStore after last def
            firstUse = null;
            lastDef = null;
            vrIdx = -1;
        }
    }

    private void checkpoint() {
        if (firstUse != null) {
            MILoad miLoad = new MILoad(curMF.newVR(), Arm.Reg.getR(sp), firstUse);
            miLoad.offset = genMemOffSet(offImm, miLoad);
        }
        if (lastDef != null) {
            MIStore miStore = new MIStore(lastDef, curMF.getVR(vrIdx), rSP);
            miStore.offset = genMemOffSet(offImm, miStore);
        }
        vrIdx = -1;
    }

    public Operand genMemOffSet(Operand offImm, MachineInst miLoadStore) {
        if (offImm.getImm() < (1 << 12)) {
            return offImm;
        } else {
            Operand dst = curMF.newVR();
            new MIMove(dst, offImm, miLoadStore);
            return dst;
        }
    }

    public boolean addEdge(Operand u, Operand v) {
        AdjPair adjPair = new AdjPair(u, v);
        if (!(adjSet.contains(adjPair) && u.equals(v))) {
            adjSet.add(adjPair);
            adjSet.add(new AdjPair(v, u));
            if (!u.isPreColored()) {
                u.addAdj(v);
                u.degree++;
            }
            if (!v.isPreColored()) {
                v.addAdj(u);
                v.degree++;
            }
            return true;
        }
        return false;
    }

    public ArrayList<Machine.Block> recurMbList = new ArrayList<>();

    public void build() {
        for (Machine.Block mb = curMF.mbList.getEnd(); !mb.equals(curMF.mbList.head); mb = (Machine.Block) mb.getPrev()) {
            // 获取块的 liveOut
            HashSet<Operand> live = mb.liveOutSet;
            Machine.Block finalMb = mb;
            for (MachineInst mi = mb.getEndMI(); !mi.equals(mb.miList.head); mi = (MachineInst) mi.getPrev()) {
                ArrayList<Operand> defs = mi.defOpds;
                ArrayList<Operand> uses = mi.useOpds;
                if (mi.isMove()) {
                    MIMove mv = (MIMove) mi;
                    if (mv.directColor()) {
                        // 没有cond, 没有shift, src和dst都是虚拟寄存器的mov指令
                        live.remove(mv.getSrc());
                        mv.getDst().moveSet.add(mv);
                        mv.getSrc().moveSet.add(mv);
                        workListMoveSet.add(mv);
                    }
                }
                // defs.forEach(def -> {
                //     if (def.needColor()) {
                //         live.add(def);
                //     }
                // });
                defs.stream().filter(Operand::needColor).forEach(live::add);
                // defs.forEach(def -> {
                //     if (def.needColor()) {
                //         live.forEach(l -> addEdge(l, def));
                //     }
                // });
                defs.stream().filter(Operand::needColor).forEach(def -> live.forEach(l -> addEdge(l, def)));

                defs.forEach(def -> {
                    live.removeIf(Operand::needColor);
                    def.loopCounter += finalMb.bb.getLoopDep();
                });

                uses.forEach(use -> {
                    if (use.needColor()) live.add(use);
                    use.loopCounter += finalMb.bb.getLoopDep();
                });
            }
        }
    }

    /**
     * 获取有效冲突
     * 对于o, 删除在selectStackList(冲突图中已删除的结点list), 和已合并的mov的src(dst在其他工作表中)
     */
    private HashSet<Operand> adjacent(Operand o) {
        HashSet<Operand> validConflictOpdSet = new HashSet<>(o.adjOpdSet);
        validConflictOpdSet.removeIf(r -> selectStack.contains(r) || coalescedNodeSet.contains(r));
        return validConflictOpdSet;
    }

    /**
     * 取 x 的 moveSet 交 (activeMoveSet 并 workListMoveSet)
     * 1. 已经合并的传送指令的集合 coalescedMoveSet
     * 2. src 和 dst 相冲突的传送指令集合 constrainedMoveSet
     * 3. 不再考虑合并的传送指令集合 frozenMoveSet
     */
    private HashSet<MIMove> nodeMoves(Operand x) {
        HashSet<MIMove> canCoalesceSet = new HashSet<>(x.moveSet);
        canCoalesceSet.removeIf(r -> !(activeMoveSet.contains(r) || workListMoveSet.contains(r)));
        return canCoalesceSet;
    }

    /**
     * 结点 o 仍然有关联的move指令
     */
    private boolean moveRelated(Operand x) {
        return nodeMoves(x).size() > 0;
    }

    private void makeWorkList() {
        for (int i = 0; i < curMF.getVRSize(); i++) {
            // initial
            Operand vr = new Operand(i);
            if (vr.degree >= rk) {
                spillWorkSet.add(vr);
            } else if (moveRelated(vr)) {
                freezeWorkSet.add(vr);
            } else {
                simplifyWorkSet.add(vr);
            }
        }
    }

    /**
     * 有可能合并的传送指令从 activeMoveSet 挪到 workListMoveSet
     */
    // EnableMoves({x} u Adjacent(x))
    private void enableMoves(Operand x) {
        nodeMoves(x).stream().filter(activeMoveSet::contains).forEach(mv -> {
            activeMoveSet.remove(mv);
            workListMoveSet.add(mv);
            // tools.changeWorkSet(mv, activeMoveSet, workListMoveSet);
        });
        adjacent(x).forEach(adj -> nodeMoves(adj).stream().filter(activeMoveSet::contains).forEach(mv -> {
            activeMoveSet.remove(mv);
            workListMoveSet.add(mv);
        }));
    }
    // private void enableMoves(Operand x) {
    //     for (MIMove mv : nodeMoves(x)) {
    //         // 考虑 x 关联的可能合并的 move
    //         if (activeMoveSet.contains(mv)) {
    //             // 未做好合并准备的集合如果包含mv, 就挪到workListMoveSet中
    //             activeMoveSet.remove(mv);
    //             workListMoveSet.add(mv);
    //         }
    //     }
    //     for (Operand adj : adjacent(x)) {
    //         // 对于o的每个实际邻接冲突adj
    //         for (MIMove mv : nodeMoves(adj)) {
    //             // adj关联的move, 如果是有可能合并的move
    //             if(activeMoveSet.contains(mv)){
    //                 // 未做好合并准备的集合如果包含mv, 就挪到workListMoveSet中
    //                 activeMoveSet.remove(mv);
    //                 workListMoveSet.add(mv);
    //             }
    //         }
    //     }
    // }

    /**
     * 从图中去掉一个结点需要减少该结点的当前各个邻结点的度数.
     * TODO 如果某个邻结点的 degree < K - 1, 则这个邻结点一定是传送有关的, ?
     * 因此不将它加入到 simplifyWorkSet 中.
     * 当邻结点adj的度数从 K 变为 K - 1时，与它(adj)的邻结点相关的传送指令将有可能变成可合并的
     *
     * @param x
     */
    private void decrementDegree(Operand x) {
        x.degree--;
        if (x.degree == rk - 1) {
            enableMoves(x);
            // TODO: 这里trivial写的是insert, 很怪
            spillWorkSet.remove(x);
            if (moveRelated(x)) {
                freezeWorkSet.add(x);
            } else {
                simplifyWorkSet.add(x);
            }
        }
    }

    private void simplify() {
        Iterator<Operand> iter = simplifyWorkSet.iterator();
        Operand x = iter.next();
        iter.remove();
        selectStack.push(x);
        adjacent(x).forEach(this::decrementDegree);
    }

    /**
     * 当 move y, x 已被合并, x.alias = y, x 被放入 coalescedNodeSet 中
     */
    private Operand getAlias(Operand x) {
        while (coalescedNodeSet.contains(x)) {
            x = x.alias;
        }
        return x;
    }

    public void addWorkList(Operand x) {
        if (!x.isPreColored() && !moveRelated(x) && x.degree < rk) {
            // 当 x 需要被染色, x 并不与 move 相关, x 的度 <= k - 1
            // 低度数传送有关结点删除 x , 且低度数传送无关结点添加 x
            freezeWorkSet.remove(x);
            simplifyWorkSet.add(x);
            // tools.changeWorkSet(x, freezeWorkSet, simplifyWorkSet);
            // changeWorkSet(x, freezeWorkSet, simplifyWorkSet);
        }
    }

    /**
     * u 为低度数结点, 或者 u 已经预着色, 或者 (u, v)冲突
     */
    public boolean ok(Operand adj, Operand v) {
        return adj.degree < rk || adj.isPreColored() || adjSet.contains(new AdjPair(adj, v));
    }

    /**
     * u 的冲突邻接点是否均满足:
     * 要么为低度数结点, 要么预着色, 要么与 v 邻接
     */
    public boolean adjOk(Operand u, Operand v) {
        for (Operand adj : adjacent(u)) {
            if (!(adj.degree < rk
                    || adj.isPreColored()
                    || adjSet.contains(new AdjPair(adj, v)))) {
                return false;
            }
        }
        return true;
    }

    // 合并move u <- v
    public void combine(Operand u, Operand v) {
        if (freezeWorkSet.contains(v)) {
            freezeWorkSet.remove(v);
        } else {
            spillWorkSet.remove(v);
        }
        // 合并 move u, v, 将v加入 coalescedNodeSet
        coalescedNodeSet.add(v);
        v.alias = u;
        u.moveSet.addAll(v.moveSet);
        // 对于 v 在冲突图上的每个邻结点 adj , 建立 adj, u 之间的冲突边, 且
        adjacent(v).forEach(adj -> {
            addEdge(adj, u);
            decrementDegree(adj);
        });
        // 当 u 从(合并前的)低度数结点成为(合并后的)高度数结点, 则将其从freezeWorkSet转移到 spillWorkSet
        if (u.degree >= rk && freezeWorkSet.contains(u)) {
            freezeWorkSet.remove(u);
            spillWorkSet.add(u);
            // changeWorkSet(u, freezeWorkSet, spillWorkSet);
        }
    }

    // private void changeWorkSet(MIMove x, HashSet<MIMove> from, HashSet<MIMove> to) {
    //     from.remove(x);
    //     to.add(x);
    // }
    // private void changeWorkSet(Operand x, HashSet<Operand> from, HashSet<Operand> to) {
    //     from.remove(x);
    //     to.add(x);
    // }

    /**
     * 保守的,
     * 将 v 的冲突结点全部加到 u 的冲突结点中去
     */
    public boolean conservative(HashSet<Operand> adjU, HashSet<Operand> adjV) {
        return Stream.concat(adjU.stream(), adjV.stream()).filter(x -> x.degree >= rk).count() < rk;
    }

    //-------------------------------------------------------------------------------------------------
    public void coalesce() {
        MIMove mv = workListMoveSet.iterator().next();
        Operand u = mv.getDst();
        Operand v = mv.getSrc();
        if (v.isPreColored()) {
            var tmp = u;
            u = v;
            v = tmp;
        }
        workListMoveSet.remove(mv);
        if (u.equals(v)) {
            coalescedMoveSet.add(mv);
            addWorkList(u);
        } else if (v.isPreColored() || adjSet.contains(new AdjPair(u, v))) {
            constrainedMoveSet.add(mv);
            addWorkList(u);
            addWorkList(v);
        } else if ((u.isPreColored() && adjOk(u, v))
                || (!u.isPreColored() && conservative(adjacent(u), adjacent(v)))) {
            coalescedMoveSet.add(mv);
            combine(u, v);
            addWorkList(u);
        } else {
            activeMoveSet.add(mv);
        }
    }

    /**
     * 对于每一个 x 相关的 move, 将其从可能合并的传送指令的集合 (activeMoveSet ∪ workListMoveSet)
     * 挪到 frozenMoveSet 中, 同时 对于 x 相关 move 的另一端的操作数 v
     * 如果 v 不是传送相关的低度数结点
     *
     * @param x
     */
    public void freezeMoves(Operand x) {
        for (var mv : nodeMoves(x)) {
            // nodeMoves(x) 取出来的只可能是 activeMoveSet 中的或者 workListMoveSet 中的
            if (!activeMoveSet.remove(mv)) {
                workListMoveSet.remove(mv);
            }
            frozenMoveSet.add(mv);

            // 这个很怪, 跟书上不一样
            // 选择 move 中非 x 方结点 v
            Operand v = mv.getDst();
            if (v.equals(x)) v = mv.getSrc();
            /* // 鲸书:
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
            }
        }
    }

    /**
     * 从低度数的传送有关的结点中随机选择一个进行冻结
     */
    public void freeze() {
        Operand u = freezeWorkSet.iterator().next();
        freezeWorkSet.remove(u);
        simplifyWorkSet.add(u);
        freezeMoves(u);
    }

    /**
     * 从低度数结点集(simplifyWorkSet)中启发式选取结点 x , 挪到高度数结点集(spillWorkSet)中
     * 冻结 x 及其相关 move
     */
    public void selectSpill() {
        // Operand x = spillWorkSet.stream().reduce((a, b) -> a.heuristicVal() < b.heuristicVal() ? a : b).orElseThrow();
        Operand x = spillWorkSet.stream().reduce(Operand::select).orElseThrow();
        simplifyWorkSet.add(x);
        spillWorkSet.remove(x);
        freezeMoves(x);
    }

    public void assignColors() {
        HashMap<Operand, Operand> colorMap = new HashMap<>();
        while (selectStack.size() > 0) {
            Operand n = selectStack.pop();
            final HashSet<Arm.Regs> okColorSet = new HashSet<>(Arrays.asList(values()).subList(0, rk - 1));
            okColorSet.add(lr);

            n.adjOpdSet.forEach(w -> {
                Operand a = w.alias;
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

            if (okColorSet.isEmpty()) {
                spilledNodeSet.add(n);
            } else {
                Arm.Regs color = okColorSet.iterator().next();
                colorMap.put(n, new Operand(color));
            }
        }

        if (spilledNodeSet.size() > 0) {
            return;
        }

        coalescedNodeSet.forEach(mvSrc -> {
            Operand mvDst = mvSrc.alias;
            colorMap.put(mvSrc, mvDst.isPreColored() ? mvDst : colorMap.get(mvDst));
        });

        for (Machine.Block mb : curMF.mbList) {
            for (MachineInst mi : mb.miList) {
                ArrayList<Operand> defs = mi.defOpds;
                ArrayList<Operand> uses = mi.useOpds;
                if (defs.size() > 0) {
                    assert defs.size() == 1;
                    defs.set(0, colorMap.get(defs.get(0)));
                }

                for (int i = 0; i < uses.size(); i++) {
                    assert uses.get(i) != null;
                    Operand set = colorMap.get(uses.get(i));
                    if (set != null) {
                        mi.setUse(i, set);
                    }
                }
            }
        }
    }
}
