package midend;

import mir.BasicBlock;
import mir.Function;
import mir.Instr;
import mir.Use;
import util.CenterControl;

import java.util.*;

public class FuncInline {

    //TODO: 不能递归
    //TODO: 寻找函数调用链
    //TODO: 递归的内联


    //fixme:考虑库函数如何维持正确性
    //      因为建立反向图的时候,只考虑了自定义的函数

    //TODO:如果存在调用:
    // A -> A
    // B -> A
    // C -> B
    // 在当前判断中 A B C 入度均为1,无法内联
    // 但是,其实可以把B内联到C里,
    // 所以可以内联的条件可以加强为:对于一个函数,如果入度为0/入度不为0,但是所有的入边对应的函数,均只存在自调用

    private ArrayList<Function> functions;
    private ArrayList<Function> funcCanInline;
    private HashSet<Function> funcCallSelf = new HashSet<>();

    //A调用B则存在B->A
    private HashMap<Function, HashSet<Function>> reserveMap;
    //记录反图的入度
    private HashMap<Function, Integer> inNum;
    //A调用B则存在A->B
    private HashMap<Function, HashSet<Function>> Map = new HashMap<>();
    private Queue<Function> queue;


    public FuncInline(ArrayList<Function> functions) {
        this.functions = functions;
        this.funcCanInline = new ArrayList<>();
        this.reserveMap = new HashMap<>();
        this.inNum = new HashMap<>();
        this.queue = new LinkedList<>();
    }

    public void Run() {
        GetFuncCanInline();
        for (Function function: funcCanInline) {
            if (funcCallSelf.contains(function)) {
                continue;
            }
            inlineFunc(function);
            functions.remove(function);
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    instr.remove();
                }
            }
            function.setDeleted();
        }
        //System.err.println("fun_inline_end");
    }

    private void GetFuncCanInline() {
//        for (Function function: functions) {
//            if (canInline(function)) {
//                funcCanInline.add(function);
//            }
//        }
        makeReserveMap();
        if (CenterControl._STRONG_FUNC_INLINE) {
            topologySortStrong();
        } else {
            topologySort();
        }
    }

    //f1调用f2 添加一条f2到f1的边
    private void makeReserveMap() {
        for (Function function: functions) {
            Map.put(function, new HashSet<>());
        }
        for (Function function: functions) {
            reserveMap.put(function, new HashSet<>());
            if (!inNum.containsKey(function)) {
                inNum.put(function, 0);
            }
            Use use = function.getBeginUse();
            while (use.getNext() != null) {
                Function userFunc = use.getUser().parentBB().getFunction();
                boolean ret = reserveMap.get(function).add(userFunc);
                Map.get(userFunc).add(function);
                if (!inNum.containsKey(userFunc)) {
                    inNum.put(userFunc, 0);
                }
                if (ret) {
                    inNum.put(userFunc, inNum.get(userFunc) + 1);
                }
                use = (Use) use.getNext();
            }
        }
    }

    private void topologySortStrong() {
        for (Function function: inNum.keySet()) {
            if (inNum.get(function) == 0 && !function.getName().equals("main")) {
                queue.add(function);
            } else if (inNum.get(function) == 1 && Map.get(function).iterator().next().equals(function)) {
                queue.add(function);
                funcCallSelf.add(function);
            }
        }
        while (!queue.isEmpty()) {
            Function pos = queue.peek();
            funcCanInline.add(pos);
            for (Function next: reserveMap.get(pos)) {
                if (funcCanInline.contains(next)) {
                    continue;
                }
                inNum.put(next, inNum.get(next) - 1);
                assert Map.get(next).contains(pos);
                Map.get(next).remove(pos);
                if (inNum.get(next) == 0 && !next.getName().equals("main")) {
                    queue.add(next);
                } else if (inNum.get(next) == 1 && Map.get(next).iterator().next().equals(next)) {
                    queue.add(next);
                    funcCallSelf.add(next);
                }
            }
            queue.poll();
        }
    }

    private void topologySort() {
        for (Function function: inNum.keySet()) {
            if (inNum.get(function) == 0 && !function.getName().equals("main")) {
                queue.add(function);
            }
        }
        while (!queue.isEmpty()) {
            Function pos = queue.peek();
            funcCanInline.add(pos);
            for (Function next: reserveMap.get(pos)) {
                inNum.put(next, inNum.get(next) - 1);
                if (inNum.get(next) == 0 && !next.getName().equals("main")) {
                    queue.add(next);
                }
            }
            queue.poll();
        }
    }

    private boolean canInline(Function function) {
        BasicBlock bb = function.getBeginBB();
        while (bb.getNext() != null) {
            Instr instr = bb.getBeginInstr();
            while (instr.getNext() != null) {
                if (instr instanceof Instr.Call) {
                    return false;
                }
                instr = (Instr) instr.getNext();
            }
            bb = (BasicBlock) bb.getNext();
        }
        return true;
    }

    private void inlineFunc(Function function) {
        Use use = function.getBeginUse();
        while (use.getNext() != null) {
            assert use.getUser() instanceof Instr.Call;
            transCallToFunc(function, (Instr.Call) use.getUser());
            use = (Use) use.getNext();
        }
    }

    private void transCallToFunc(Function function, Instr.Call call) {
        if (function.getBeginBB().getBeginInstr() instanceof Instr.Phi) {
            System.err.println("phi_in_func_begin_BB");
        }
        CloneInfoMap.clear();
        Function inFunction = call.parentBB().getFunction();
        BasicBlock beforeCallBB = call.parentBB();

        ArrayList<BasicBlock> sucs_afterCall = beforeCallBB.getSuccBBs();

        Instr instr = beforeCallBB.getBeginInstr();
        //原BB中需要保留的instr
        while (instr.getNext() != null) {
            if (instr.equals(call)) {
                break;
            }
            instr = (Instr) instr.getNext();
        }


        BasicBlock retBB = new BasicBlock(inFunction, beforeCallBB.getLoop());
        if (!function.getRetType().isVoidType()) {
            //Instr retPhi = new Instr.Phi(call.getType(), new ArrayList<>(), retBB);
            Instr retPhi = new Instr.Phi(function.getRetType(), new ArrayList<>(), retBB);
            instr.modifyAllUseThisToUseA(retPhi);
        }

        function.inlineToFunc(inFunction, retBB, call, beforeCallBB.getLoop());

        //instr.cloneToBB(callBB);
        instr.remove();
        instr = (Instr) instr.getNext();
        instr.getPrev().setNext(beforeCallBB.getEnd());
        beforeCallBB.getEnd().setPrev(instr.getPrev());




        BasicBlock afterCallBB = new BasicBlock(inFunction, beforeCallBB.getLoop());
        ArrayList<Instr> instrs = new ArrayList<>();
        while (instr.getNext() != null) {
            instrs.add(instr);
            instr = (Instr) instr.getNext();
        }
        for (Instr instr1: instrs) {
            instr1.setBb(afterCallBB);
            afterCallBB.insertAtEnd(instr1);
        }


        BasicBlock funcBegin = (BasicBlock) CloneInfoMap.getReflectedValue(function.getBeginBB());


        ArrayList<BasicBlock> sucs_beforeCall = new ArrayList<>();
        sucs_beforeCall.add(funcBegin);
        beforeCallBB.modifySucs(sucs_beforeCall);

        ArrayList<BasicBlock> pres_funcBegin = new ArrayList<>();
        pres_funcBegin.add(beforeCallBB);
        funcBegin.modifyPres(pres_funcBegin);

        Instr jumpToCallBB = new Instr.Jump((BasicBlock) CloneInfoMap.getReflectedValue(function.getBeginBB()), beforeCallBB);
        Instr jumpToAfterCallBB = new Instr.Jump(afterCallBB, retBB);

        ArrayList<BasicBlock> succ_retBB = new ArrayList<>();
        succ_retBB.add(afterCallBB);
        retBB.modifySucs(succ_retBB);

        ArrayList<BasicBlock> pres_afterCall = new ArrayList<>();
        pres_afterCall.add(retBB);
        afterCallBB.modifySucs(sucs_afterCall);
        afterCallBB.modifyPres(pres_afterCall);

        for (BasicBlock bb: sucs_afterCall) {
            bb.modifyPre(beforeCallBB, afterCallBB);
        }
    }

}
