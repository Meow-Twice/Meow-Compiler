package midend;

import mir.BasicBlock;
import mir.Function;
import mir.Instr;
import mir.Use;

import java.util.ArrayList;
import java.util.HashSet;

public class FuncInline {

    //TODO: 不能递归
    //TODO: 寻找函数调用链
    //TODO: 递归的内联

    private ArrayList<Function> functions;
    private HashSet<Function> funcCanInline;

    public FuncInline(ArrayList<Function> functions) {
        this.functions = functions;
        this.funcCanInline = new HashSet<>();
    }

    public void Run() {
        GetFuncCanInline();
        for (Function function: funcCanInline) {
            inlineFunc(function);
        }
    }

    private void GetFuncCanInline() {
        funcCanInline.clear();
        for (Function function: functions) {
            if (canInline(function)) {
                funcCanInline.add(function);
            }
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
            Instr retPhi = new Instr.Phi(call.getType(), new ArrayList<>(), retBB);
            instr.modifyAllUseThisToUseA(retPhi);
        }

        function.inlineToFunc(inFunction, retBB, call);

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
