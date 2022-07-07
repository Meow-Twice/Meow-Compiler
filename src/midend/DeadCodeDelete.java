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

    //TODO:对于指令闭包
    // 指令闭包的算法: 必须被保留的指令  函数的return,函数调用,对全局变量的store,数组  从这些指令dfs use
    //  其余全部删除
    //  wait memorySSA for 数组级别数据流分析
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
                    //Instr endInst = pos.getEndInstr();
                    // 一个基本块至少有一条跳转指令
                    try {
                        while (instr.getNext() != null) {
                            if (!(instr instanceof Instr.Terminator) && !(instr instanceof Instr.Call) && !(instr.getType().isVoidType()) &&
                                    instr.isNoUse()) {
                                instr.remove();
                                changed = true;
                            }
                            instr = (Instr) instr.getNext();
                        }
                    } catch (Exception e) {
                        System.out.println(instr.toString());
                    }

                    pos = (BasicBlock) pos.getNext();
                }
            }
        }
    }
}
