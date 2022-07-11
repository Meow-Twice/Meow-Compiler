package backend;

import lir.Arm;
import lir.MIMove;
import lir.Machine;
import lir.Machine.Operand;
import lir.MachineInst;
import manage.Manager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Stack;
import java.util.stream.Stream;

public class TrivialRegAllocator {

    private int rk = 0;
    private int sk = 0;

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
    Stack<Operand> selectStackList = new Stack<>();

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
            for (Machine.Block mb : mcFunc.mbList) {
                boolean unDone = true;
                while (unDone) {
                    livenessAnalysis(mcFunc);
                    adjSet = new HashSet<>();
                    simplifyWorkSet = new HashSet<>();
                    freezeWorkSet = new HashSet<>();
                    spillWorkSet = new HashSet<>();
                    spilledNodeSet = new HashSet<>();
                    coloredNodeList = new ArrayList<>();
                    selectStackList = new Stack<>();
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
                }

            }
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
        o.adjOpdSet.removeIf(r -> selectStackList.contains(r) || coalescedNodeSet.contains(r));
        return o.adjOpdSet;
    }

    /**
     * 对于o, 如果不是有可能合并的move, 就从moveSet中删除, 包括
     * 1. 已经合并的传送指令的集合 coalescedMoveSet
     * 2. src 和 dst 相冲突的传送指令集合 constrainedMoveSet
     * 3. 不再考虑合并的传送指令集合 frozenMoveSet
     */
    private HashSet<MIMove> nodeMoves(Operand o) {
        o.moveSet.removeIf(r -> !(activeMoveSet.contains(r) || workListMoveSet.contains(r)));
        return o.moveSet;
    }

    /**
     * 结点 o 仍然有关联的move指令
     */
    private boolean moveRelated(Operand o) {
        return nodeMoves(o).size() > 0;
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

    private void Simplify() {
        Iterator<Operand> iter = simplifyWorkSet.iterator();
        Operand x = iter.next();
        iter.remove();
        selectStackList.push(x);
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


}
