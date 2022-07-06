package midend;

import mir.BasicBlock;
import mir.Function;
import mir.Instr;

import java.util.ArrayList;

public class DeadCodeDelete {
    ArrayList<Function> functions;
    public DeadCodeDelete(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run(){
        deadCodeDelete(functions);
    }
    public void deadCodeDelete(ArrayList<Function> functions) {
        boolean changed = true;
        while(changed)
        {
            changed = false;
            for (Function function : functions) {
                BasicBlock beginBB = function.getBeginBB();
                BasicBlock end = function.getEnd();

                BasicBlock pos = beginBB;
                while (!pos.equals(end)) {

                    Instr instr = pos.getBeginInstr();
                    Instr endInst = pos.getEndInstr();
                    // 一个基本块至少有一条跳转指令
                    while (!instr.equals(endInst)) {
                        if (!(instr instanceof Instr.Terminator) && !(instr instanceof Instr.Call) && !(instr.getType().isVoidType()) &&
                        instr.isNoUse()) {
                            instr.removeUserUse();
                            instr.remove();
                            changed = true;
                        }
                        instr = (Instr) instr.getNext();
                    }

                    pos = (BasicBlock) pos.getNext();
                }
            }
        }
    }
}
