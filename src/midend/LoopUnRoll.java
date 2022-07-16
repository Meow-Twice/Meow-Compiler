package midend;

import frontend.syntax.Ast;
import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class LoopUnRoll {

    //TODO:分类:
    // 1.归纳变量只有归纳作用:只有一条ALU和PHI->归纳变量可以删除
    // (fix:情况1貌似不用考虑,直接展开然后让死代码消除来做这件事情)
    // 2.归纳变量有其他user

    //LCSSA: 循环内定义的变量 只在循环内使用
    //LCSSA的PHI是对于那些在循环中定义在循环外使用的变量插入的
    //在循环中定义,外部还能用,意味着在循环前一定有"声明"
    //循环中有定义,意味着,Head块中一定有一条它的PHI
    private static int loop_unroll_up_lines = 10000;

    private ArrayList<Function> functions;
    private HashMap<Function, HashSet<Loop>> loopsByFunc;
    private HashMap<Function, ArrayList<Loop>> loopOrdered;

    public LoopUnRoll(ArrayList<Function> functions) {
        this.functions = functions;
        this.loopsByFunc = new HashMap<>();
        this.loopOrdered = new HashMap<>();
        init();
    }

    private void init() {
        for (Function function: functions) {
            HashSet<Loop> loops = new HashSet<>();
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                loops.add(bb.getLoop());
            }
            loopsByFunc.put(function, loops);
            loopOrdered.put(function, new ArrayList<>());
        }
        for (Function function: functions) {
            Loop pos = function.getBeginBB().getLoop();
            DFSForLoopOrder(function, pos);
        }
    }

    private void DFSForLoopOrder(Function function, Loop loop) {
        for (Loop next: loop.getChildrenLoops()) {
            DFSForLoopOrder(function, next);
        }
        loopOrdered.get(function).add(loop);
    }

    public void Run() {
        constLoopUnRoll();

    }

    //idc init/step/end 均为常数
    private void constLoopUnRoll() {
        for (Function function: functions) {
            constLoopUnRollForFunc(function);
        }
    }

    private void constLoopUnRollForFunc(Function function) {
        for (Loop loop: loopOrdered.get(function)) {
            if (loop.isSimpleLoop() && loop.isIdcSet()) {
                constLoopUnRollForLoop(loop);
            }
        }

    }

    private void constLoopUnRollForLoop(Loop loop) {
        Value idcInit = loop.getIdcInit();
        Value idcStep = loop.getIdcStep();
        Value idcEnd = loop.getIdcEnd();
        Instr.Alu idcAlu = (Instr.Alu) loop.getIdcAlu();


        if (!(idcInit instanceof Constant) || !(idcStep instanceof Constant) || !(idcEnd instanceof Constant)) {
            return;
        }
        //for (i = init; i < end; i+=step)
        //先只考虑int的情况
        if (!idcAlu.getType().isInt32Type()) {
            return;
        }
        Instr.Icmp idcCmp = (Instr.Icmp) loop.getIdcCmp();
        int init = (int) ((Constant.ConstantInt) idcInit).getConstVal();
        int step = (int) ((Constant.ConstantInt) idcStep).getConstVal();
        int end = (int) ((Constant.ConstantInt) idcEnd).getConstVal();
        if (step == 0) {
            return;
        }
        int times = 0;
        int max_time = (int) 1e9;
        switch (idcAlu.getOp()) {
            case ADD -> {
                switch (idcCmp.getOp()) {
                    case EQ -> times = (init == end)? 1:0;
                    case NE -> times = ((end - init) % step == 0)? (end - init) / step : -1;
                    case SGE, SLE -> times = (end - init) / step + 1;
                    case SGT, SLT -> times = ((end - init) % step == 0)? (end - init) / step : (end - init) / step + 1;
                }
            } case SUB -> {
                switch (idcCmp.getOp()) {
                    case EQ -> times = (init == end)? 1:0;
                    case NE -> times = ((init - end) % step == 0)? (init - end) / step : -1;
                    case SGE, SLE -> times = (init - end) / step + 1;
                    case SGT, SLT -> times = ((init - end) % step == 0)? (init - end) / step : (init - end) / step + 1;
                }
            } case MUL, DIV -> {
                //TODO:把乘法除法也纳入考虑
                return;
            }
        }

        //循环次数为负-->循环无数次
        if (times < 0) {
            return;
        }
        loop.setIdcTimes(times);
        DoLoopUnRoll(loop);
    }

    private void DoLoopUnRoll(Loop loop) {
        Function function = loop.getHeader().getFunction();
        int times = loop.getIdcTimes();
        int cnt = cntDFS(loop);
        if (cnt * times > loop_unroll_up_lines) {
            return;
        }

        BasicBlock head = loop.getHeader();
        BasicBlock exit = null, entering = null, latch = null, headNext = null;
        for (BasicBlock bb: head.getPrecBBs()) {
            if (bb.isLoopLatch()) {
                latch = bb;
            } else {
                entering = bb;
            }
        }
        for (BasicBlock bb: loop.getExits()) {
            exit = bb;
        }
        for (BasicBlock bb: head.getSuccBBs()) {
            if (!bb.equals(exit)) {
                headNext = bb;
            }
        }

        entering.modifySucAToB(head, headNext);
        latch.modifySucAToB(head, exit);
        //headNext 只有head一个前驱
        assert headNext.getPrecBBs().size() == 1;


        //修正exit的LCSSA PHI
        assert exit.getPrecBBs().size() == 1;
        for (Instr instr = exit.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
            if (!(instr instanceof Instr.Phi)) {
                break;
            }
            //fixme:测试
            // 当前采用的写法基于一些特性,如定义一定是PHI?,这些特性需要测试
            Value value = instr.getUseValueList().get(0);
            assert value instanceof Instr;
            if (((Instr) value).parentBB().equals(head)) {
                assert value instanceof Instr.Phi;
                int index = head.getPrecBBs().indexOf(latch);
                instr.modifyUse(((Instr.Phi) value).getUseValueList().get(index), 0);
            }
        }

        //修正对head中phi的使用
        for (Instr instr = head.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
            if (!(instr instanceof Instr.Phi)) {
                break;
            }
            int index = head.getPrecBBs().indexOf(entering);
            instr.modifyAllUseThisToUseA(instr.getUseValueList().get(index));
        }

        head.remove();

        //维护bbInWhile这些块的Loop信息
        //维护块中指令的isInWhileCond信息
        HashSet<BasicBlock> bbInWhile = loop.getNowLevelBB();
        bbInWhile.remove(head);
        if (loop.getLoopDepth() == 1) {
            for (BasicBlock bb: bbInWhile) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (instr.isInWhileCond()) {
                        instr.setLoopCondCount(-1);
                    }
                }
            }
        }

        Loop parentLoop = loop.getParentLoop();
        for (BasicBlock bb: bbInWhile) {
            bb.modifyLoop(parentLoop);
        }

        //fixme:复制time / time - 1?份
        BasicBlock oldBegin = headNext;
        BasicBlock oldLatch = latch;
        //times = 0;
        for (int i = 0; i < times - 1; i++) {
            CloneInfoMap.clear();
            for (BasicBlock bb: bbInWhile) {
                bb.cloneToFunc_LUR(function);
            }
            for (BasicBlock bb: bbInWhile) {
                BasicBlock newBB = (BasicBlock) CloneInfoMap.getReflectedValue(bb);
                newBB.fixPreSuc(bb);
                newBB.fix();
            }
            BasicBlock newBegin = (BasicBlock) CloneInfoMap.getReflectedValue(oldBegin);
            BasicBlock newLatch = (BasicBlock) CloneInfoMap.getReflectedValue(oldLatch);

            oldLatch.modifySuc(exit, newBegin);
            ArrayList<BasicBlock> pres = new ArrayList<>();
            pres.add(oldLatch);
            newBegin.modifyPres(pres);
            oldLatch.modifyBrAToB(entering, newBegin);

            oldBegin = newBegin;
            oldLatch = newLatch;
            HashSet<BasicBlock> newBBInWhile = new HashSet<>();
            for (BasicBlock bb: bbInWhile) {
                newBBInWhile.add((BasicBlock) CloneInfoMap.getReflectedValue(bb));
            }
            bbInWhile = newBBInWhile;
        }
    }

    private int cntDFS(Loop loop) {
        int cnt = 0;
        for (BasicBlock bb: loop.getNowLevelBB()) {
            for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                cnt++;
            }
        }
        for (Loop next: loop.getChildrenLoops()) {
            cnt += cntDFS(next);
        }
        return cnt;
    }
}
