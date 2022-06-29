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

    public MakeDFG(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        MakeCFG();
        MakeDom();
        MakeIDom();
        MakeDF();
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

    //计算单个函数的控制流图
    private void makeSingleFuncCFG(Function function) {
        BasicBlock beginBB = function.getBeginBB();
        BasicBlock end = function.getEnd();

        HashMap<BasicBlock, ArrayList<BasicBlock>> preMap = new HashMap<>();
        HashMap<BasicBlock, ArrayList<BasicBlock>> sucMap = new HashMap<>();
        HashSet<BasicBlock> BBs = new HashSet<>();

        //初始化前驱后继图
        BasicBlock pos = beginBB;
        while (!pos.equals(end)) {
            preMap.put(pos, new ArrayList<>());
            sucMap.put(end, new ArrayList<>());
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
}
