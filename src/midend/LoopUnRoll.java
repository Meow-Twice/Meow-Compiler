package midend;

import frontend.syntax.Ast;
import mir.*;

import java.util.ArrayList;
import java.util.HashSet;

public class LoopUnRoll {

    //TODO:分类:
    // 1.归纳变量只有归纳作用:只有一条ALU和PHI->归纳变量可以删除
    // (fix:情况1貌似不用考虑,直接展开然后让死代码消除来做这件事情)
    // 2.归纳变量有其他user
    private ArrayList<Function> functions;

    public LoopUnRoll(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {

    }
}
