package midend;

import mir.BasicBlock;
import mir.Function;
import mir.Instr;
import mir.Use;

import java.util.*;

public class FuncInline {

    //TODO: 不能递归
    //TODO: 寻找函数调用链
    //TODO: 递归的内联

    private ArrayList<Function> functions;
    private ArrayList<Function> funcCanInline;

    private HashMap<Function, HashSet<Function>> reserveMap;
    private HashMap<Function, Integer> inNum;
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
            inlineFunc(function);
            functions.remove(function);
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    instr.remove();
                }
            }
            function.setDeleted();
        }
    }

    private void GetFuncCanInline() {
//        for (Function function: functions) {
//            if (canInline(function)) {
//                funcCanInline.add(function);
//            }
//        }
        makeReserveMap();
        topologySort();
    }

    //f1调用f2 添加一条f2到f1的边
    private void makeReserveMap() {
        for (Function function: functions) {
            reserveMap.put(function, new HashSet<>());
            if (!inNum.containsKey(function)) {
                inNum.put(function, 0);
            }
            Use use = function.getBeginUse();
            while (use.getNext() != null) {
                Function userFunc = use.getUser().parentBB().getFunction();
                boolean ret = reserveMap.get(function).add(userFunc);
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


        Instr jumpToCallBB = new Instr.Jump((BasicBlock) CloneInfoMap.getReflectedValue(function.getBeginBB()), beforeCallBB);
        Instr jumpToAfterCallBB = new Instr.Jump(afterCallBB, retBB);

    }

}
