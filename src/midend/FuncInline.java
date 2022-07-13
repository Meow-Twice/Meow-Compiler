package midend;

import mir.BasicBlock;
import mir.Function;
import mir.Instr;

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

    }

}
