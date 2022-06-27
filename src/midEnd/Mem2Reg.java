package midEnd;

import ir.BasicBlock;
import ir.Function;
import ir.Instr;
import ir.Use;

import java.util.ArrayList;
import java.util.HashSet;

//插phi并重命名
public class Mem2Reg {

    private ArrayList<Function> functions;

    public Mem2Reg(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        removeAlloc();

    }

    public void removeAlloc() {
        for (Function function: functions) {
            removeFuncAlloc(function);
        }
    }

    public void removeFuncAlloc(Function function) {
        for (BasicBlock bb: function.getBBs()) {
            removeBBAlloc(bb);
        }
    }

    public void removeBBAlloc(BasicBlock basicBlock) {
        Instr temp = basicBlock.getBeginInstr();
        while (!temp.isEnd()) {
            if (temp instanceof Instr.Alloc && !((Instr.Alloc) temp).isArrayAlloc()) {
                remove(temp);
            }
        }
    }

    public void remove(Instr instr) {
        HashSet<BasicBlock> userBB = new HashSet<>();
        Use pos = instr.getBeginUse();
        while (pos != null) {
            userBB.add(pos.getUser().parentBB());
            pos = (Use) pos.getNext();
        }

        if (userBB.size() == 0) {
            instr.remove();
        } else if (userBB.size() == 1) {
            //遍历使用的唯一BB,记录最近的store,所有的load的使用替换为store的值的使用,删除alloc,store和load
        }
    }

}
