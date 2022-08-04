package backend;

import lir.*;
import lir.Machine.Operand;
import util.CenterControl;
import util.ILinkNode;

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

    void livenessAnalysis(Machine.McFunction mf) {
        for (Machine.Block mb : mf.mbList) {
            mb.liveUseSet = new HashSet<>();
            mb.liveDefSet = new HashSet<>();
            for (MachineInst mi : mb.miList) {
                // TODO 
                if (!(mi instanceof V || mi instanceof MICall)) continue;
                ArrayList<Operand> defs = mi.defOpds;
                ArrayList<Operand> uses = mi.useOpds;
                // liveuse 计算
                uses.forEach(use -> {
                    if (use.fNotConst() && !mb.liveDefSet.contains(use)) {
                        mb.liveUseSet.add(use);
                    }
                });
                // def 计算
                defs.forEach(def -> {
                    if (def.fNotConst() && !mb.liveUseSet.contains(def)) {
                        mb.liveDefSet.add(def);
                    }
                });
            }
            logOut(mb.getDebugLabel() + "\tdefSet:\t" + mb.liveDefSet.toString());
            logOut(mb.getDebugLabel() + "\tuseSet:\t" + mb.liveUseSet.toString());
            mb.liveInSet = new HashSet<>(mb.liveUseSet);
            mb.liveOutSet = new HashSet<>();
        }

        liveInOutAnalysis(mf);
    }

    /***
     * 传送指令的数据结构, 下面给出了5个由传送指令组成的集合,
     * 每一条传送指令都只在其中的一个集合中(执行完Build之后直到Main结束)
     */

    /**
     * 已经合并的传送指令的集合
     */
    HashSet<V.Mov> coalescedVMovSet = new HashSet<>();

    /**
     * src 和 dst相冲突的传送指令集合
     */
    HashSet<V.Mov> constrainedVMovSet = new HashSet<>();

    /**
     * 不再考虑合并的传送指令集合
     */
    HashSet<V.Mov> frozenVMovSet = new HashSet<>();

    /**
     * 有可能合并的传送指令, 当结点x从高度数结点变为低度数结点时,
     * 与 x 的邻接点关联的传送指令必须添加到传送指令工作表 workListVMovSet .
     * 此时原来因为合并后会有太多高度数邻结点而不能合并的传送指令现在则可能变成可合并的.
     * 只在下面少数几种情况下传送指令才会加入到工作表 workListVMovSet :
     * 1. 在简化期间, 删除一个结点可能导致其邻结点 x 的度数发生变化.
     * 因此要把与 x 的邻结点相关联的传送指令加入到 workListVMovSet 中.
     * 2. 当合并 u 和 v 时,可能存在一个与 u 和 v 都有冲突的结点 x .
     * 因为 x 现在只于 u 和 v 合并后的这个结点相冲突, 故 x 的度将减少,
     * 因此也要把与 x 的邻结点关联的传送指令加入到 workListVMovSet 中.
     * 如果 x 是传送有关的, 则与 x 本身关联的传送指令也要加入到此表中,
     * 因为 u 和 v 有可能都是高度数的结点
     */
    HashSet<V.Mov> workListVMovSet = new HashSet<>();

    /**
     * 还未做好合并准备的传送指令的集合
     */
    HashSet<V.Mov> activeVMovSet = new HashSet<>();

    public void AllocateRegister(Machine.Program program) {
        for (Machine.McFunction mf : program.funcList) {
            curMF = mf;
            while (true) {
                turnInit(mf);
                coalescedVMovSet = new HashSet<>();
                constrainedVMovSet = new HashSet<>();
                frozenVMovSet = new HashSet<>();
                workListVMovSet = new HashSet<>();
                activeVMovSet = new HashSet<>();

                for (int i = 0; i < K; i++) {
                    Arm.Reg.getS(i).degree = MAX_DEGREE;
                }
                // for (int i = 0; i < sk; i++) {
                //     Arm.Reg.getS(i).degree = Integer.MAX_VALUE;
                // }

                logOut("f RegAlloc Build start");
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
                logOut("spillWorkSet:\t" + spillWorkSet.toString());
                logOut("freezeWorkSet:\t" + freezeWorkSet.toString());
                logOut("simplifyWorkSet:\t" + simplifyWorkSet.toString());

                while (simplifyWorkSet.size() + workListVMovSet.size() + freezeWorkSet.size() + spillWorkSet.size() > 0) {
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
                    if (workListVMovSet.size() > 0) {
                        logOut("-- coalesce");
                        logOut("workListVMovSet:\t" + workListVMovSet);
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
                    fixStack();
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

    private void fixStack() {

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

    public void build() {
        for (Arm.Reg reg : Arm.Reg.getFPRPool()) {
            reg.loopCounter = 0;
            reg.degree = MAX_DEGREE;
            reg.adjOpdSet = new HashSet<>();
            reg.vMovSet = new HashSet<>();
            reg.setAlias(null);
        }
        for (Operand o : curMF.sVrList) {
            o.loopCounter = 0;
            o.degree = 0;
            o.adjOpdSet = new HashSet<>();
            o.vMovSet = new HashSet<>();
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
                logOut(mi + "\tlive begin:\t" + live);
                if (mi.isVMov()) {
                    V.Mov vmov = (V.Mov) mi;
                    if (vmov.directColor()) {
                        // 没有cond, 没有shift, src和dst都是虚拟寄存器的mov指令
                        // move 的 dst 和 src 不应是直接冲突的关系, 而是潜在的可合并的关系
                        // move a, b --> move rx, rx 需要a 和 b 不是冲突关系
                        live.remove(vmov.getSrc());
                        vmov.getDst().vMovSet.add(vmov);
                        vmov.getSrc().vMovSet.add(vmov);
                        workListVMovSet.add(vmov);
                    }
                }

                dealDefUse(live, mi, mb);
            }
        }
    }

    /**
     * x.vMovSet 去掉
     * 1. 已经合并的传送指令的集合 coalescedMoveSet
     * 2. src 和 dst 相冲突的传送指令集合 constrainedMoveSet
     * 3. 不再考虑合并的传送指令集合 frozenMoveSet
     *
     * @param x
     * @return x.vMovSet ∩ (activeVMovSet ∪ workListVMovSet)
     */
    private HashSet<V.Mov> nodeMoves(Operand x) {
        HashSet<V.Mov> canCoalesceSet = new HashSet<>(x.vMovSet);
        canCoalesceSet.removeIf(r -> !(activeVMovSet.contains(r) || workListVMovSet.contains(r)));
        return canCoalesceSet;
    }

    /**
     * EnableMoves({x} ∪ Adjacent(x))
     * 有可能合并的传送指令从 activeVMovSet 挪到 workListVMovSet
     * @param x
     */
    /*
    private void enableMoves(Operand x) {
        for (V.Mov mv : nodeMoves(x)) {
            // 考虑 x 关联的可能合并的 move
            if (activeVMovSet.contains(mv)) {
                // 未做好合并准备的集合如果包含mv, 就挪到workListVMovSet中
                activeVMovSet.remove(mv);
                workListVMovSet.add(mv);
            }
        }
        for (Operand adj : adjacent(x)) {
            // 对于o的每个实际邻接冲突adj
            for (V.Mov mv : nodeMoves(adj)) {
                // adj关联的move, 如果是有可能合并的move
                if (activeVMovSet.contains(mv)) {
                    // 未做好合并准备的集合如果包含mv, 就挪到workListVMovSet中
                    activeVMovSet.remove(mv);
                    workListVMovSet.add(mv);
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
        if (x.degree == K - 1) {
            for (V.Mov mv : nodeMoves(x)) {
                // 考虑 x 关联的可能合并的 move
                if (activeVMovSet.contains(mv)) {
                    // 未做好合并准备的集合如果包含mv, 就挪到workListVMovSet中
                    activeVMovSet.remove(mv);
                    workListVMovSet.add(mv);
                }
            }
            for (Operand adj : adjacent(x)) {
                // 对于o的每个实际邻接冲突adj
                for (V.Mov mv : nodeMoves(adj)) {
                    // adj关联的move, 如果是有可能合并的move
                    if (activeVMovSet.contains(mv)) {
                        // 未做好合并准备的集合如果包含mv, 就挪到workListVMovSet中
                        activeVMovSet.remove(mv);
                        workListVMovSet.add(mv);
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
    public void addWorkList(Operand x) {
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

    public void combine(Operand u, Operand v) {
        if (freezeWorkSet.contains(v)) {
            freezeWorkSet.remove(v);
        } else {
            spillWorkSet.remove(v);
        }
        // 合并 move u, v, 将v加入 coalescedNodeSet
        coalescedNodeSet.add(v);
        v.setAlias(u);
        u.vMovSet.addAll(v.vMovSet);
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

    //-------------------------------------------------------------------------------------------------
    public void coalesce() {
        // When workListVMovSet.size() > 0;
        V.Mov mv = workListVMovSet.iterator().next();
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
        workListVMovSet.remove(mv);
        if (u.equals(v)) {
            coalescedVMovSet.add(mv);
            logOut(String.format("coalescedMoveSet.add(%s)", mv));
            addWorkList(u);
        } else if (v.isPreColored(dataType) || adjSet.contains(new AdjPair(u, v))) {
            // 这里似乎必须用adjSet判断
            // 两边都是预着色则不可能合并, 因为上面已经在 move u, v 的情况下将 u, v 互换, 如果v仍然是预着色说明u, v均为预着色
            constrainedVMovSet.add(mv);
            logOut(String.format("constrainedMoveSet.add(%s)", mv));
            addWorkList(u);
            addWorkList(v);
        } else {
            // TODO 尝试重新验证写法
            // 此时 v 已经不是预着色了
            // if (u.is_F_PreColored()) {
            //     /**
            //      * v 的冲突邻接点是否均满足:
            //      * 要么为低度数结点, 要么预着色, 要么已经与 u 邻接
            //      */
            //     boolean flag = true;
            //     for (Operand adj : adjacent(v)) {
            //         if (adj.degree >= K && !adj.is_F_PreColored() && !adjSet.contains(new AdjPair(adj, u))) {
            //             // adjSet.contains(new AdjPair(adj, v))这个感觉可以改成 v.adjOpdSet.contains(adj)
            //             flag = false;
            //         }
            //     }
            //     if (flag) {
            //         coalescedMoveSet.add(mv);
            //         combine(u, v);
            //         addWorkList(u);
            //     } else {
            //         activeVMovSet.add(mv);
            //     }
            // } else {
            //     // union实际统计 u 和 v 的有效冲突邻接结点
            //     HashSet<Operand> union = new HashSet<>(u.adjOpdSet);
            //     union.removeIf(r -> selectStack.contains(r) || coalescedNodeSet.contains(r));
            //     union.addAll(v.adjOpdSet);
            //     // union.removeIf(r -> selectStack.contains(r) || coalescedNodeSet.contains(r));
            //     int cnt = 0;
            //     for (Operand x : union) {
            //         if (!selectStack.contains(x) && !coalescedNodeSet.contains(x) && x.degree >= K) {
            //             // 统计union中的高度数结点个数
            //             // if (x.degree >= K) {
            //             cnt++;
            //         }
            //     }
            //     // 如果结点个数 < K 个表示未改变冲突图的可着色性
            //     if (cnt < K) {
            //         coalescedMoveSet.add(mv);
            //         combine(u, v);
            //         addWorkList(u);
            //     } else {
            //         activeVMovSet.add(mv);
            //     }
            // }
            if ((u.isPreColored(dataType) && adjOk(v, u))
                    || (!u.isPreColored(dataType) && conservative(adjacent(u), adjacent(v)))) {
                coalescedVMovSet.add(mv);
                combine(u, v);
                addWorkList(u);
            } else {
                activeVMovSet.add(mv);
            }
        }
    }

    /**
     * 对于每一个 x 相关的 move, 将其从可能合并的传送指令的集合 (activeVMovSet ∪ workListVMovSet)
     * 挪到 frozenMoveSet 中, 同时 对于 x 相关 move 的另一端的操作数 v
     * 如果 v 不是传送相关的低度数结点,
     * 则将 v 从低度数传送有关结点集 freezeWorkSet 挪到低度数传送无关结点集 simplifyWorkSet
     *
     * @param u
     */
    public void freezeMoves(Operand u) {
        for (V.Mov mv : nodeMoves(u)) {
            // nodeMoves(x) 取出来的只可能是 activeVMovSet 中的或者 workListVMovSet 中的
            // if (!activeVMovSet.remove(mv)) {
            //     workListVMovSet.remove(mv);
            // }
            if (activeVMovSet.contains(mv)) {
                activeVMovSet.remove(mv);
            } else {
                workListVMovSet.remove(mv);
            }
            logOut(mv + "\t: activeVMovSet, workListVMovSet -> frozenMoveSet");
            frozenVMovSet.add(mv);

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

    public void assignColors() {
        logOut("Start to assign colors");
        HashMap<Operand, Operand> colorMap = new HashMap<>();
        while (selectStack.size() > 0) {
            Operand toBeColored = selectStack.pop();
            assert !toBeColored.isPreColored(dataType) && !toBeColored.isAllocated();
            logOut("when try assign:\t" + toBeColored);
            final TreeSet<Arm.Regs> okColorSet = new TreeSet<>(Arrays.asList(Arm.Regs.FPRs.values()).subList(0, K));
            // logOut("--- K = \t"+K);

            // 把待分配颜色的结点的邻接结点的颜色去除
            toBeColored.adjOpdSet.forEach(adj -> {
                Operand a = getAlias(adj);
                if (a.hasReg() && a.isF32()) {
                    // 已着色或者预分配
                    okColorSet.remove(a.getReg());
                } else if (a.is_F_Virtual()) {
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
                // Arm.Regs color = okColorSet.iterator().next();
                // TODO 尝试重新验证写法
                Arm.Regs color = okColorSet.pollLast();
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
            colorMap.put(v, a.isPreColored(dataType) ? a : colorMap.get(a));
        }

        for (Machine.Block mb : curMF.mbList) {
            for (MachineInst mi : mb.miList) {
                // TODO 这里不考虑Call
                if (mi.isCall()) {
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
                    Operand set = colorMap.get(defs.get(0));
                    if (set != null) {
                        curMF.addUsedFRPs(set.reg);
                        logOut("- Def\t" + defs.get(0) + "\tassign: " + set);
                        defs.set(0, set);
                    }
                }

                for (int i = 0; i < uses.size(); i++) {
                    assert uses.get(i) != null;
                    Operand set = colorMap.get(uses.get(i));
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
