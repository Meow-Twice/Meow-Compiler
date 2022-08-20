package midend;

import mir.BasicBlock;
import mir.Function;
import mir.Instr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class MergeSimpleBB {

    private ArrayList<Function> functions;
    HashMap<BasicBlock, ArrayList<BasicBlock>> preMap = new HashMap<>();
    HashMap<BasicBlock, ArrayList<BasicBlock>> sucMap = new HashMap<>();

    public MergeSimpleBB(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                if (bb.getBeginInstr().equals(bb.getEndInstr()) && bb.getBeginInstr() instanceof Instr.Jump) {
                    BasicBlock suc = bb.getSuccBBs().get(0);
                    suc.getPrecBBs().remove(bb);
                    for (BasicBlock pre: bb.getPrecBBs()) {
                        pre.modifyBrAToB(bb, suc);
                        pre.modifySuc(bb, suc);
                        suc.addPre(pre);
                    }
                    bb.getBeginInstr().remove();
                    bb.remove();
                }
            }
        }
        RemoveDeadBB();
        MakeCFG();
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
            //System.err.println("remove:" + bb.getLabel());
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
//            if  (pos.getLabel().equals("b242")) {
//                System.err.println("err");
//            }
            pos.modifyPres(preMap.get(pos));
            pos.modifySucs(sucMap.get(pos));
            pos = (BasicBlock) pos.getNext();
        }
        function.setPreMap(preMap);
        function.setSucMap(sucMap);
        function.setBBs(BBs);
    }
}
