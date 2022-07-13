package midend;

import mir.BasicBlock;
import mir.Function;
import mir.Instr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

//计算所需数据流信息
public class MakeDFG {

    private ArrayList<Function> functions;
    HashMap<BasicBlock, ArrayList<BasicBlock>> preMap = new HashMap<>();
    HashMap<BasicBlock, ArrayList<BasicBlock>> sucMap = new HashMap<>();

    public MakeDFG(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        RemoveDeadBB();
        MakeCFG();
        MakeDom();
        MakeIDom();
        MakeDF();
        MakeDomTreeDeep(); //定义基本块在支配树中的深度,并定义每个基本块在支配树中的父节点
    }


    private void RemoveDeadBB() {
        //TODO:优化删除基本块的算法
        for (Function function: functions) {
            removeFuncDeadBB(function);
        }
    }

    private void MakeCFG() {
        for (Function function: functions) {
            makeSingleFuncCFG(function);
        }
    }

    private void MakeDom() {
        for (Function function: functions) {
            makeSingleFuncDom(function);
        }
    }

    private void MakeIDom() {
        for (Function function: functions) {
            makeSingleFuncIDom(function);
        }
    }

    private void MakeDF() {
        for (Function function: functions) {
            makeSingleFuncDF(function);
        }
    }

    private void MakeDomTreeDeep() {
        for (Function function: functions) {
            makeDomTreeDeepForFunc(function);
        }
    }



    //删除冗余块  一次删不完, 删除一些没有前驱的块会多出来一些新的没有前驱的块
    //可以DFS删,一次就能删完
    private void removeFuncDeadBB(Function function) {
        BasicBlock beginBB = function.getBeginBB();
        BasicBlock end = function.getEnd();

        preMap = new HashMap<>();
        sucMap = new HashMap<>();
        HashSet<BasicBlock> BBs = new HashSet<>();

        //初始化前驱后继图
        BasicBlock pos = beginBB;
        while (!pos.equals(end)) {
            preMap.put(pos, new ArrayList<>());
            sucMap.put(pos, new ArrayList<>());
            BBs.add(pos);
            pos = (BasicBlock) pos.getNext();

            //remove useless br
            Instr instr = pos.getEndInstr();
            while (instr.getPrev() instanceof Instr.Branch || instr.getPrev() instanceof Instr.Jump) {
                Instr temp = instr;
                temp.remove();
                instr = (Instr) instr.getPrev();
            }
        }

        //添加前驱和后继
        pos = beginBB;
        while (!pos.equals(end)) {
            Instr lastInstr = pos.getEndInstr();
            if (lastInstr instanceof Instr.Branch) {
                BasicBlock elseTarget = ((Instr.Branch) lastInstr).getElseTarget();
                BasicBlock thenTarget = ((Instr.Branch) lastInstr).getThenTarget();
                sucMap.get(pos).add(thenTarget);
                sucMap.get(pos).add(elseTarget);
                preMap.get(thenTarget).add(pos);
                preMap.get(elseTarget).add(pos);
            } else if (lastInstr instanceof Instr.Jump) {
                BasicBlock target = ((Instr.Jump) lastInstr).getTarget();
                sucMap.get(pos).add(target);
                preMap.get(target).add(pos);
            }
            pos = (BasicBlock) pos.getNext();
        }

        //回写基本块和函数
        HashSet<BasicBlock> needRemove = new HashSet<>();
        HashSet<BasicBlock> know = new HashSet<>();
        DFS(function.getBeginBB(), know);

        pos = beginBB;
        while (!pos.equals(end)) {
            if (!know.contains(pos)) {
                needRemove.add(pos);
            }
            pos = (BasicBlock) pos.getNext();
        }


        for (BasicBlock bb: needRemove) {
            bb.remove();
            Instr instr = bb.getBeginInstr();
            while (instr.getNext() != null) {
                instr.remove();
                instr = (Instr) instr.getNext();
            }
        }
    }

    private void DFS(BasicBlock bb, HashSet<BasicBlock> know) {
        if (know.contains(bb)) {
            return;
        }
        know.add(bb);
        for (BasicBlock next: sucMap.get(bb)) {
            DFS(next, know);
        }
    }

    //计算单个函数的控制流图
    private void makeSingleFuncCFG(Function function) {
        BasicBlock beginBB = function.getBeginBB();
        BasicBlock end = function.getEnd();

        preMap = new HashMap<>();
        sucMap = new HashMap<>();
        HashSet<BasicBlock> BBs = new HashSet<>();

        //初始化前驱后继图
        BasicBlock pos = beginBB;
        while (!pos.equals(end)) {
            preMap.put(pos, new ArrayList<>());
            sucMap.put(pos, new ArrayList<>());
            BBs.add(pos);
            pos = (BasicBlock) pos.getNext();
        }

        //添加前驱和后继
        pos = beginBB;
        while (!pos.equals(end)) {
            Instr lastInstr = pos.getEndInstr();
            if (lastInstr instanceof Instr.Branch) {
                BasicBlock elseTarget = ((Instr.Branch) lastInstr).getElseTarget();
                BasicBlock thenTarget = ((Instr.Branch) lastInstr).getThenTarget();
                sucMap.get(pos).add(thenTarget);
                sucMap.get(pos).add(elseTarget);
                preMap.get(thenTarget).add(pos);
                preMap.get(elseTarget).add(pos);
            } else if (lastInstr instanceof Instr.Jump) {
                BasicBlock target = ((Instr.Jump) lastInstr).getTarget();
                sucMap.get(pos).add(target);
                preMap.get(target).add(pos);
            }
            pos = (BasicBlock) pos.getNext();
        }

        //回写基本块和函数
        pos = beginBB;
        while (!pos.equals(end)) {
            pos.setPrecBBs(preMap.get(pos));
            pos.setSuccBBs(sucMap.get(pos));
            pos = (BasicBlock) pos.getNext();
        }
        function.setPreMap(preMap);
        function.setSucMap(sucMap);
        function.setBBs(BBs);
    }

    private void makeSingleFuncDom(Function function) {
        BasicBlock enter = function.getBeginBB();
        HashSet<BasicBlock> BBs = function.getBBs();
        for (BasicBlock bb: BBs) {
            HashSet<BasicBlock> doms = new HashSet<>();
            HashSet<BasicBlock> know = new HashSet<>();
            dfs(enter, bb, know);

            for (BasicBlock temp: BBs) {
                if (!know.contains(temp)) {
                    doms.add(temp);
                }
            }

            bb.setDoms(doms);
        }
    }

    private void dfs(BasicBlock bb, BasicBlock not,HashSet<BasicBlock> know) {
        if (bb.equals(not)) {
            return;
        }
        if (know.contains(bb)) {
            return;
        }
        know.add(bb);
        for (BasicBlock next: bb.getSuccBBs()) {
            if (!know.contains(next) && !next.equals(not)) {
                dfs(next, not, know);
            }
        }
    }

    private void makeSingleFuncIDom(Function function) {
        HashSet<BasicBlock> BBs = function.getBBs();
        for (BasicBlock A: BBs) {
            HashSet<BasicBlock> idoms = new HashSet<>();
            for (BasicBlock B: A.getDoms()) {
                if (AIDomB(A, B)) {
                    idoms.add(B);
                }
            }

            A.setIdoms(idoms);
        }
    }

    private boolean AIDomB(BasicBlock A, BasicBlock B) {
        HashSet<BasicBlock> ADoms = A.getDoms();
        if (!ADoms.contains(B)) {
            return false;
        }
        if (A.equals(B)) {
            return false;
        }
        for (BasicBlock temp: ADoms) {
            if (!temp.equals(A) && !temp.equals(B)) {
                if (temp.getDoms().contains(B)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void makeSingleFuncDF(Function function) {
        for (BasicBlock X: function.getBBs()) {
            HashSet<BasicBlock> DF = new HashSet<>();
            for (BasicBlock Y: function.getBBs()) {
                if (DFXHasY(X, Y)) {
                    DF.add(Y);
                }
            }

            X.setDF(DF);
        }
    }

    private boolean DFXHasY(BasicBlock X, BasicBlock Y) {
        for (BasicBlock P: Y.getPrecBBs()) {
            if (X.getDoms().contains(P) && (X.equals(Y) || !X.getDoms().contains(Y))) {
                return true;
            }
        }
        return false;
    }

    private void makeDomTreeDeepForFunc(Function function) {
        BasicBlock entry = function.getBeginBB();
        int deep = 1;
        DFSForDomTreeDeep(entry, deep);
    }

    private void DFSForDomTreeDeep(BasicBlock bb, int deep) {
        bb.setDomTreeDeep(deep);
        for (BasicBlock next: bb.getIdoms()) {
            next.setIDominator(bb);
            DFSForDomTreeDeep(next, deep+1);
        }
    }

}
