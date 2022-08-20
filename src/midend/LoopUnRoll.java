package midend;

import lir.I;
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
    private static int loop_unroll_up_lines = 3000;

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
//        if (loop.getHash() == 4) {
//            System.err.println("hash_4_unroll");
//        }
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
            } case MUL -> {
                //TODO:把乘法除法也纳入考虑
                double val = Math.log(end / init) / Math.log(step);
                boolean tag = init * Math.pow(step, val) == end;
                switch (idcCmp.getOp()) {
                    case EQ -> times = (init == end)? 1:0;
                    case NE -> times = tag ? (int) val : -1;
                    case SGE, SLE -> times = (int) val + 1;
                    case SGT, SLT -> times = tag ? (int) val : (int) val + 1;
                }
            } case DIV -> {
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

    private void checkDFS(Loop loop, HashSet<BasicBlock> allBB, HashSet<BasicBlock> exits) {
        allBB.addAll(loop.getNowLevelBB());
        exits.addAll(loop.getExits());
        for (Loop loop1: loop.getChildrenLoops()) {
            checkDFS(loop1, allBB, exits);
        }
    }

    private void DoLoopUnRoll(Loop loop) {
//        if (loop.getHash() == 11) {
//            System.err.println("loop_11");
//            //return;
//        }
        //check子循环不能跳出此循环
        HashSet<BasicBlock> allBB = new HashSet<>();
        HashSet<BasicBlock> exits = new HashSet<>();
        allBB.addAll(loop.getNowLevelBB());
        for (Loop loop1: loop.getChildrenLoops()) {
            checkDFS(loop1, allBB, exits);
        }

        for (BasicBlock bb: exits) {
            if (!allBB.contains(bb)) {
                return;
            }
        }

        CloneInfoMap.clear();
        Function function = loop.getHeader().getFunction();
        int times = loop.getIdcTimes();
        int cnt = cntDFS(loop);
//        if (!loop.getChildrenLoops().isEmpty()) {
//            return;
//        }
        if ((long) cnt * times > loop_unroll_up_lines) {
            //DoLoopUnRollForSeveralTimes(loop, cnt);
            //System.err.println("loop_cnt " + String.valueOf(cnt * times));
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

        //TODO:不要直接删head
        //entering.modifySucAToB(head, headNext);
        //latch.modifySucAToB(head, exit);


        if (headNext.getPrecBBs().size() != 1) {
            return;
        }

        //headNext 只有head一个前驱
        assert headNext.getPrecBBs().size() == 1;


        HashMap<Value, Value> reachDefBeforeHead = new HashMap();
        HashMap<Value, Value> reachDefAfterLatch = new HashMap();
        HashMap<Value, Value> beginToEnd = new HashMap<>();
        HashMap<Value, Value> endToBegin = new HashMap<>();
        for (Instr instr = head.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
            if (!(instr instanceof Instr.Phi)) {
                break;
            }
            int index = head.getPrecBBs().indexOf(entering);
            reachDefBeforeHead.put(instr, instr.getUseValueList().get(index));
            //reachDefAfterLatch.put(instr.getUseValueList().get(index), instr.getUseValueList().get(1 - index));
//            beginToEnd.put(instr.getUseValueList().get(index), instr.getUseValueList().get(1 - index));
//            endToBegin.put(instr.getUseValueList().get(1 - index), instr.getUseValueList().get(index));

//            reachDefAfterLatch.put(instr, instr.getUseValueList().get(1 - index));
//            beginToEnd.put(instr, instr.getUseValueList().get(1 - index));
//            endToBegin.put(instr.getUseValueList().get(1 - index), instr);
        }


        //修正对head中phi的使用 old_version to line222
//        for (Instr instr = head.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
//            if (!(instr instanceof Instr.Phi)) {
//                break;
//            }
//            int index = head.getPrecBBs().indexOf(entering);
//            //instr.modifyAllUseThisToUseA(instr.getUseValueList().get(index));
//            Use use = (Use) instr.getBeginUse();
//            while (use.getNext() != null) {
//                Instr user = use.getUser();
//                if (!user.parentBB().equals(exit)) {
//                    user.modifyUse(instr.getUseValueList().get(index), use.getIdx());
//                }
//                use = (Use) use.getNext();
//            }
//        }
//
//        head.remove();
        BasicBlock latchNext = new BasicBlock(function, loop);
        int latch_index = head.getPrecBBs().indexOf(latch);
        for (Instr instr = head.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
            if (!(instr instanceof Instr.Phi)) {
                break;
            }
            Use use = instr.getUseList().get(latch_index);
            ArrayList<Value> values = new ArrayList<>();
            values.add(instr.getUseValueList().get(latch_index));
            Instr phiInLatchNext = new Instr.Phi(instr.getType(), values, latchNext);
            use.remove();
            instr.getUseList().remove(latch_index);
            instr.getUseValueList().remove(latch_index);

            reachDefAfterLatch.put(instr, phiInLatchNext);
            beginToEnd.put(instr, phiInLatchNext);
            endToBegin.put(phiInLatchNext, instr);
        }
        ArrayList<BasicBlock> headNewPres = new ArrayList<>();
        headNewPres.add(entering);
        head.modifyPres(headNewPres);
        ArrayList<BasicBlock> headNewSucs = new ArrayList<>();
        headNewSucs.add(headNext);
        head.modifySucs(headNewSucs);
        loop.removeBB(head);
//        head.setLoop(loop.getParentLoop());
//        head.isLoopHeader = false;
        head.modifyLoop(loop.getParentLoop());
        head.modifyBrToJump(headNext);
        if (loop.getLoopDepth() == 1) {
            for (Instr instr = head.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                if (instr.isInLoopCond()) {
                    instr.setNotInLoopCond();
                }
            }
        }

        Instr jumpInLatchNext = new Instr.Jump(exit, latchNext);
        latch.modifySuc(head, latchNext);
        latch.modifyBrAToB(head, latchNext);
        latchNext.addPre(latch);
        latchNext.addSuc(exit);

        //fixme:只有一个Exit不代表exit只有一个入口 07-20-00:31
        exit.modifyPre(head, latchNext);



        //维护bbInWhile这些块的Loop信息
        //维护块中指令的isInWhileCond信息
        HashSet<BasicBlock> bbInWhile = new HashSet<>();
        getAllBBInLoop(loop, bbInWhile);
        if (loop.getLoopDepth() == 1) {
            for (BasicBlock bb: loop.getNowLevelBB()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (instr.isInLoopCond()) {
                        instr.setNotInLoopCond();
                    }
                }
            }
        }

        Loop parentLoop = loop.getParentLoop();
        parentLoop.getChildrenLoops().remove(loop);
        for (BasicBlock bb: loop.getNowLevelBB()) {
            bb.modifyLoop(parentLoop);
        }

        for (Loop child: loop.getChildrenLoops()) {
            child.setParentLoop(parentLoop);
        }

        //loop.getNowLevelBB().clear();

        bbInWhile.remove(head);
        //bbInWhile.add(head);

        //fixme:复制time / time - 1?份
        BasicBlock oldBegin = headNext;
        BasicBlock oldLatchNext = latchNext;
        //times = 0;
        for (int i = 0; i < times - 1; i++) {
            CloneInfoMap.clear();
            for (BasicBlock bb: bbInWhile) {
                bb.cloneToFunc_LUR(function, parentLoop);
            }
            for (BasicBlock bb: bbInWhile) {
                BasicBlock newBB = (BasicBlock) CloneInfoMap.getReflectedValue(bb);
                newBB.fixPreSuc(bb);
                newBB.fix();
            }
            BasicBlock newBegin = (BasicBlock) CloneInfoMap.getReflectedValue(oldBegin);
            BasicBlock newLatchNext = (BasicBlock) CloneInfoMap.getReflectedValue(oldLatchNext);

            oldLatchNext.modifySuc(exit, newBegin);
            exit.modifyPre(oldLatchNext, newLatchNext);
            ArrayList<BasicBlock> pres = new ArrayList<>();
            pres.add(oldLatchNext);
            newBegin.modifyPres(pres);
            oldLatchNext.modifyBrAToB(exit, newBegin);


            HashSet<Value> keys = new HashSet<>();
            for (Value value: reachDefAfterLatch.keySet()) {
                keys.add(value);
            }

            for (Value A: keys) {
                reachDefAfterLatch.put(CloneInfoMap.getReflectedValue(A), reachDefAfterLatch.get(A));
                if (CloneInfoMap.valueMap.containsKey(A)) {
                    reachDefAfterLatch.remove(A);
                }
            }

            //修改引用,更新reach_def_after_latch
            //fixme:考虑const的情况
            for (BasicBlock temp: bbInWhile) {
                BasicBlock bb = (BasicBlock) CloneInfoMap.getReflectedValue(temp);
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    ArrayList<Value> values = instr.getUseValueList();
                    for (int j = 0; j < values.size(); j++) {
                        Value B = values.get(j);
                        if (reachDefAfterLatch.containsKey(B)) {
                            Value C = reachDefAfterLatch.get(B);
                            Value D = CloneInfoMap.getReflectedValue(reachDefAfterLatch.get(B));

                            //TODO:不能立刻更改
                            instr.modifyUse(C, j);
//                            reachDefAfterLatch.put(C, D);
//                            reachDefAfterLatch.remove(B);
//                            Value A = endToBegin.get(C);
//                            beginToEnd.put(A, D);
//                            endToBegin.put(D, A);
//                            endToBegin.remove(C);
                        }
                    }
                }
            }

//            HashSet<Value> values = new HashSet<>();
//            for (Value value: reachDefAfterLatch.keySet()) {
//                values.add(value);
//            }

//            for (Value B: values) {
//                Value C = reachDefAfterLatch.get(B);
//                Value D = CloneInfoMap.getReflectedValue(reachDefAfterLatch.get(B));
//                reachDefAfterLatch.put(C, D);
//                reachDefAfterLatch.remove(B);
//                Value A = endToBegin.get(C);
//                beginToEnd.put(A, D);
//                endToBegin.put(D, A);
//                endToBegin.remove(C);
//            }

            keys = new HashSet<>();
            for (Value value: reachDefAfterLatch.keySet()) {
                keys.add(value);
            }

            for (Value AA: keys) {
                Value D = reachDefAfterLatch.get(AA);
                Value DD = CloneInfoMap.getReflectedValue(reachDefAfterLatch.get(AA));
                reachDefAfterLatch.put(D, DD);
                reachDefAfterLatch.remove(AA);
            }

            keys = new HashSet<>();
            for (Value value: beginToEnd.keySet()) {
                keys.add(value);
            }

            for (Value A: keys) {
                Value DD = CloneInfoMap.getReflectedValue(beginToEnd.get(A));
                beginToEnd.put(A, DD);
            }


            oldBegin = newBegin;
            oldLatchNext = newLatchNext;
            HashSet<BasicBlock> newBBInWhile = new HashSet<>();
            for (BasicBlock bb: bbInWhile) {
                newBBInWhile.add((BasicBlock) CloneInfoMap.getReflectedValue(bb));
            }
            bbInWhile = newBBInWhile;
        }

        //修正exit的LCSSA PHI
        for (Instr instr = exit.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
            if (!(instr instanceof Instr.Phi)) {
                break;
            }
            //fixme:测试
            // 当前采用的写法基于一些特性,如定义一定是PHI?,这些特性需要测试
//            Value value = instr.getUseValueList().get(0);
//            assert value instanceof Instr;
//            if (((Instr) value).parentBB().equals(head)) {
//                assert value instanceof Instr.Phi;
//                //int index = head.getPrecBBs().indexOf(entering);
//                //Value B = ((Instr.Phi) value).getUseValueList().get(index);
//                instr.modifyUse(beginToEnd.get(value), 0);
//            }
            for (Value value: instr.getUseValueList()) {
                assert value instanceof Instr;
                if (((Instr) value).parentBB().equals(head)) {
                    instr.modifyUse(beginToEnd.get(value), instr.getUseValueList().indexOf(value));
                }
            }
        }
        //fixme:07-18-01:13 是否需要这样修正exit的 LCSSA 下叙方法有错,head中应该使用beginToEnd的key对应的value
//        for (Value value: beginToEnd.keySet()) {
//            value.modifyAllUseThisToUseA(beginToEnd.get(value));
//        }

        //删除head
//        for (Instr instr = head.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
//            instr.remove();
//        }
    }

    private void getAllBBInLoop(Loop loop, HashSet<BasicBlock> bbs) {
        for (BasicBlock bb: loop.getNowLevelBB()) {
            bbs.add(bb);
        }
        for (Loop next: loop.getChildrenLoops()) {
            getAllBBInLoop(next, bbs);
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

    private void DoLoopUnRollForSeveralTimes(Loop loop, int codeCnt) {
        Function function = loop.getHeader().getFunction();
        int times = getUnrollTime(loop.getIdcTimes(), codeCnt);
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
        assert headNext.getPrecBBs().size() == 1;


        HashMap<Value, Value> reachDefBeforeHead = new HashMap();
        HashMap<Value, Value> reachDefAfterLatch = new HashMap();
        HashMap<Value, Value> beginToEnd = new HashMap<>();
        HashMap<Value, Value> endToBegin = new HashMap<>();
        for (Instr instr = head.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
            if (!(instr instanceof Instr.Phi)) {
                break;
            }
            int index = head.getPrecBBs().indexOf(entering);
            reachDefBeforeHead.put(instr, instr.getUseValueList().get(index));
        }


        BasicBlock latchNext = new BasicBlock(function, loop);
        int latch_index = head.getPrecBBs().indexOf(latch);
        for (Instr instr = head.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
            if (!(instr instanceof Instr.Phi)) {
                break;
            }
            //Use use = instr.getUseList().get(latch_index);
            ArrayList<Value> values = new ArrayList<>();
            values.add(instr.getUseValueList().get(latch_index));
            Instr phiInLatchNext = new Instr.Phi(instr.getType(), values, latchNext);
            //use.remove();
            //instr.getUseList().remove(latch_index);
            //instr.getUseValueList().remove(latch_index);

            reachDefAfterLatch.put(instr, phiInLatchNext);
            beginToEnd.put(instr.getUseValueList().get(latch_index), phiInLatchNext);
            endToBegin.put(phiInLatchNext, instr.getUseValueList().get(latch_index));
        }

        Instr jumpInLatchNext = new Instr.Jump(head, latchNext);
        latch.modifySuc(head, latchNext);
        latch.modifyBrAToB(head, latchNext);
        latch.setNotLatch();
        latchNext.addPre(latch);
        latchNext.addSuc(head);
        head.modifyPre(latch, latchNext);

        HashSet<BasicBlock> bbInWhile = new HashSet<>();
        getAllBBInLoop(loop, bbInWhile);

        bbInWhile.remove(head);

        BasicBlock oldBegin = headNext;
        BasicBlock oldLatchNext = latchNext;
        for (int i = 0; i < times - 1; i++) {
            CloneInfoMap.clear();
            for (BasicBlock bb: bbInWhile) {
                bb.cloneToFunc_LUR(function, loop);
            }
            for (BasicBlock bb: bbInWhile) {
                BasicBlock newBB = (BasicBlock) CloneInfoMap.getReflectedValue(bb);
                newBB.fixPreSuc(bb);
                newBB.fix();
            }
            BasicBlock newBegin = (BasicBlock) CloneInfoMap.getReflectedValue(oldBegin);
            BasicBlock newLatchNext = (BasicBlock) CloneInfoMap.getReflectedValue(oldLatchNext);

            oldLatchNext.modifySuc(head, newBegin);
            head.modifyPre(oldLatchNext, newLatchNext);
            ArrayList<BasicBlock> pres = new ArrayList<>();
            pres.add(oldLatchNext);
            newBegin.modifyPres(pres);
            oldLatchNext.modifyBrAToB(head, newBegin);


            HashSet<Value> keys = new HashSet<>();
            for (Value value: reachDefAfterLatch.keySet()) {
                keys.add(value);
            }

            for (Value A: keys) {
                reachDefAfterLatch.put(CloneInfoMap.getReflectedValue(A), reachDefAfterLatch.get(A));
                if (CloneInfoMap.valueMap.containsKey(A)) {
                    reachDefAfterLatch.remove(A);
                }
            }

            //修改引用,更新reach_def_after_latch
            //fixme:考虑const的情况
            for (BasicBlock temp: bbInWhile) {
                BasicBlock bb = (BasicBlock) CloneInfoMap.getReflectedValue(temp);
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    ArrayList<Value> values = instr.getUseValueList();
                    for (int j = 0; j < values.size(); j++) {
                        Value B = values.get(j);
                        if (reachDefAfterLatch.containsKey(B)) {
                            Value C = reachDefAfterLatch.get(B);
                            instr.modifyUse(C, j);
                        }
                    }
                }
            }


            keys = new HashSet<>();
            for (Value value: reachDefAfterLatch.keySet()) {
                keys.add(value);
            }

            for (Value AA: keys) {
                Value D = reachDefAfterLatch.get(AA);
                Value DD = CloneInfoMap.getReflectedValue(reachDefAfterLatch.get(AA));
                reachDefAfterLatch.put(D, DD);
                reachDefAfterLatch.remove(AA);
            }

            keys = new HashSet<>();
            for (Value value: beginToEnd.keySet()) {
                keys.add(value);
            }

            for (Value A: keys) {
                Value DD = CloneInfoMap.getReflectedValue(beginToEnd.get(A));
                beginToEnd.put(A, DD);
            }


            oldBegin = newBegin;
            oldLatchNext = newLatchNext;
            HashSet<BasicBlock> newBBInWhile = new HashSet<>();
            for (BasicBlock bb: bbInWhile) {
                newBBInWhile.add((BasicBlock) CloneInfoMap.getReflectedValue(bb));
            }
            bbInWhile = newBBInWhile;
        }

        //exit 中 LCSSA的phi并不需要修正
        for (Instr instr = head.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
            if (!(instr instanceof Instr.Phi)) {
                break;
            }
            instr.modifyUse(beginToEnd.get(instr.getUseValueList().get(latch_index)), latch_index);
//            for (Value value: instr.getUseValueList()) {
//                //assert value instanceof Instr;
//                if (!(value instanceof Instr)) {
//                    continue;
//                }
//            instr.modifyUse(beginToEnd.get(value), latch_index);
//            }
        }

    }

    private int getUnrollTime(int times, int codeCnt) {
        int ret = 1;
        for (int i = 2; i < ((int) Math.sqrt(times)); i++) {
            if (i * codeCnt > loop_unroll_up_lines) {
                break;
            }
            if (times % i == 0) {
                ret = i;
            }
        }
        return ret;
    }
}
