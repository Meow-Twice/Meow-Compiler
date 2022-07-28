package midend;

import frontend.semantic.Initial;
import mir.Function;
import mir.GlobalVal;
import mir.Instr;
import mir.type.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class ArraySSA {
    private ArrayList<Function> functions;
    private HashMap<GlobalVal.GlobalValue, Initial> globalValues;

    public ArraySSA(ArrayList<Function> functions, HashMap<GlobalVal.GlobalValue, Initial> globalValues) {
        this.functions = functions;
        this.globalValues = globalValues;
    }

    //TODO:插入Def PHI和merge PHI
    private void Run() {
        //只考虑局部数组
        insertPHIForLocalArray();
        //考虑全局数组
        for (GlobalVal.GlobalValue globalValue: globalValues.keySet()) {
            if (((Type.PointerType) globalValue.getType()).getInnerType().isArrType() && globalValue.canLocal()) {
                //认为入口块定义全局数组
            }
        }
    }

    private void insertPHIForLocalArray() {
        for (Function function: functions) {
            insertPHI(function);
        }
    }

    private void insertPHI(Function function) {
        HashSet<Instr.Alloc> allocs = new HashSet<>();
    }
}
