package midend;

import manage.Manager;
import mir.*;
import mir.type.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class AggressiveMarkParallel {

    private ArrayList<Function> functions;
    private static int parallel_num = 4;
    HashSet<BasicBlock> know = new HashSet<>();
    private HashSet<Function> goodFunc = new HashSet<>();
    private HashMap<Function, HashSet<Value>> funcLoadArrays = new HashMap<>();


    public AggressiveMarkParallel(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        init();
        for (Function function: functions) {
            know.clear();
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                if (!know.contains(bb) && bb.isLoopHeader()) {
                    markLoop(bb.getLoop());
                }
            }
        }
    }

    private void init() {
        for (Function function: functions) {
            funcLoadArrays.put(function, new HashSet<>());
            if (check(function)) {
                goodFunc.add(function);
            }
        }
    }

    private boolean check(Function function) {
        for (Value param: function.getParams()) {
            if (param.getType().isPointerType()) {
                return false;
            }
        }
        for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
            for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                if (instr instanceof Instr.Call) {
                    return false;
                }
                if (instr instanceof Instr.Store) {
                    return false;
                } else if (instr instanceof Instr.Load) {
                    Value array = ((Instr.Load) instr).getPointer();
                    while (array instanceof Instr.GetElementPtr) {
                        array = ((Instr.GetElementPtr) array).getPtr();
                    }
                    funcLoadArrays.get(function).add(array);
                }
            }
        }
        return true;
    }

    private boolean isPureLoop(Loop loop) {
        if (!loop.hasChildLoop()) {
            return true;
        }
        if (loop.getChildrenLoops().size() > 1) {
            return false;
        }
        if (!loop.isSimpleLoop() || !loop.isIdcSet()) {
            return false;
        }
        return isPureLoop(loop.getChildrenLoops().iterator().next());
    }

    private void markLoop(Loop loop) {
        //当前只考虑main函数内的循环
//        if (!loop.getHeader().getFunction().getName().equals("main")) {
//            return;
//        }
        if (!isPureLoop(loop)) {
            return;
        }
        if (loop.hasChildLoop()) {
            return;
        }
        HashSet<BasicBlock> bbs = new HashSet<>();
        HashSet<Value> idcVars = new HashSet<>();
        HashSet<Loop> loops = new HashSet<>();
        bbs.addAll(loop.getNowLevelBB());
        idcVars.add(loop.getIdcPHI());
        loops.add(loop);

        for (Instr instr = loop.getHeader().getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
            if (instr instanceof Instr.Phi && !instr.equals(loop.getIdcPHI())) {
                return;
            }
        }

        //只有一次调用函数, 读一个数组, 写一个数组
        Value loadArray = null, storeArray = null;
        Function func = null;

        for (BasicBlock bb: bbs) {
            for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                if (instr instanceof Instr.Call) {
                    if (func != null) {
                        return;
                    }
                    func = ((Instr.Call) instr).getFunc();
                }
                if (useOutLoops(instr, loops)) {
                    return;
                }
                //trivel写法
                if (instr instanceof Instr.GetElementPtr) {
                    if (!(((Instr.GetElementPtr) instr).getPtr() instanceof GlobalVal.GlobalValue)) {
                        return;
                    }
                    for (Value idc: ((Instr.GetElementPtr) instr).getIdxList()) {
                        if (idc instanceof Constant && (int) ((Constant) idc).getConstVal() == 0) {
                            continue;
                        }
                        if (!idcVars.contains(idc)) {
                            return;
                        }
                    }
                }
                if (instr instanceof Instr.Load) {
                    if (loadArray != null) {
                        return;
                    }
                    Value array = ((Instr.Load) instr).getPointer();
                    while (array instanceof Instr.GetElementPtr) {
                        array = ((Instr.GetElementPtr) array).getPtr();
                    }
                    loadArray = array;
                }
                if (instr instanceof Instr.Store) {
                    if (storeArray != null) {
                        return;
                    }
                    Value array = ((Instr.Store) instr).getPointer();
                    while (array instanceof Instr.GetElementPtr) {
                        array = ((Instr.GetElementPtr) array).getPtr();
                    }
                    storeArray = array;
                }
            }
        }

        for (Value array: funcLoadArrays.get(func)) {
            if (!(array instanceof GlobalVal.GlobalValue)) {
                return;
            }
        }
        if (funcLoadArrays.get(func).contains(storeArray) || loadArray.equals(storeArray)) {
            return;
        }

        loop.setCanAggressiveParallel(true);
    }


    private boolean useOutLoops(Value value, HashSet<Loop> loops) {
        for (Use use = value.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
            Instr user = use.getUser();
            if (!loops.contains(user.parentBB().getLoop())) {
                return true;
            }
        }
        return false;
    }
}
