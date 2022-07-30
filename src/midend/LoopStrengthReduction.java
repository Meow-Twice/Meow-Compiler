package midend;

import mir.Function;

import java.util.ArrayList;

public class LoopStrengthReduction {

    //TODO:识别循环中,使用了迭代变量idc的数学运算
    //      可以考虑任何一个常数*idc相关的ADD/SUB指令再进行ADD会/SUB
    //      即(i + A) * const + B
    //      A和B是循环不变量(def不在当前循环)
    //      const为常数
    //      循环展开前做
    private ArrayList<Function> functions;

    public LoopStrengthReduction(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {

    }

    private void loopConstLift() {

    }


}
