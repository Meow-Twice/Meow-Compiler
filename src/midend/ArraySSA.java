package midend;

import frontend.semantic.Initial;
import mir.BasicBlock;
import mir.Function;
import mir.GlobalVal;
import mir.Instr;
import mir.type.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class ArraySSA {
    //只考虑局部数组
    //全局可以考虑 遍历支配树, 遇到一个Array的store就删除之前的GVNhash,达到GVN的目的
    private ArrayList<Function> functions;
    private HashMap<GlobalVal.GlobalValue, Initial> globalValues;
    private HashSet<Instr.Alloc> allocs = new HashSet<>();

    public ArraySSA(ArrayList<Function> functions, HashMap<GlobalVal.GlobalValue, Initial> globalValues) {
        this.functions = functions;
        this.globalValues = globalValues;
    }

    //TODO:插入Def PHI和merge PHI
    private void Run() {
        GetAllocs();
        //局部数组
        for (Instr.Alloc alloc: allocs) {

        }

        //全局数组
        for (GlobalVal.GlobalValue globalValue: globalValues.keySet()) {
            if (((Type.PointerType) globalValue.getType()).getInnerType().isArrType() && globalValue.canLocal()) {
                //认为入口块定义全局数组
            }
        }
    }

    private void GetAllocs() {
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (instr instanceof Instr.Alloc) {
                        allocs.add((Instr.Alloc) instr);
                    }
                }
            }
        }
    }

    private void insertPHIForLocalArray(Instr instr) {

    }

    private void insertPHIForGlobalArray(GlobalVal.GlobalValue globalValue) {

    }
}
