package midend;

import frontend.semantic.Initial;
import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class ConstFold {

    private ArrayList<Function> functions;
    private HashMap<GlobalVal.GlobalValue, Initial> globalValues;
    private HashSet<Value> constGlobalPtrs;

    public ConstFold(ArrayList<Function> functions, HashMap<GlobalVal.GlobalValue, Initial> globalValues) {
        this.functions = functions;
        this.globalValues = globalValues;
        this.constGlobalPtrs = new HashSet<>();
    }

    public void Run() {
        condConstFold();
        arrayConstFold();
    }

    private void condConstFold() {
        for (Function function: functions) {
            condConstFoldForFunc(function);
        }
    }

    private void condConstFoldForFunc(Function function) {
        for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
            for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                if (instr instanceof Instr.Icmp) {
                    Value lValue = instr.getUseValueList().get(0);
                    Value rValue = instr.getUseValueList().get(1);
                    if (lValue instanceof Constant && rValue instanceof Constant) {
                        boolean tag = true;
                        int lInt = (int) ((Constant) lValue).getConstVal();
                        int rInt = (int) ((Constant) rValue).getConstVal();
                        switch (((Instr.Icmp) instr).getOp()) {
                            case SLT -> tag = lInt < rInt;
                            case SLE -> tag = lInt <= rInt;
                            case SGT -> tag = lInt > rInt;
                            case SGE -> tag = lInt >= rInt;
                            case NE -> tag = lInt != rInt;
                            case EQ -> tag = lInt == rInt;
                        }
                        int val = tag? 1:0;
                        Constant.ConstantBool bool = new Constant.ConstantBool(val);
                        instr.modifyAllUseThisToUseA(bool);
                    }
                } else if (instr instanceof Instr.Fcmp) {
                    Value lValue = instr.getUseValueList().get(0);
                    Value rValue = instr.getUseValueList().get(1);
                    if (lValue instanceof Constant && rValue instanceof Constant) {
                        boolean tag = true;
                        float lFloat = (float) ((Constant) lValue).getConstVal();
                        float rFloat = (float) ((Constant) rValue).getConstVal();
                        switch (((Instr.Fcmp) instr).getOp()) {
                            case OLT -> tag = lFloat < rFloat;
                            case OLE -> tag = lFloat <= rFloat;
                            case OGT -> tag = lFloat > rFloat;
                            case OGE -> tag = lFloat >= rFloat;
                            case ONE -> tag = lFloat != rFloat;
                            case OEQ -> tag = lFloat == rFloat;
                        }
                        int val = tag? 1:0;
                        Constant.ConstantBool bool = new Constant.ConstantBool(val);
                        instr.modifyAllUseThisToUseA(bool);
                    }
                } else if (instr instanceof Instr.Zext) {
                    Value value = ((Instr.Zext) instr).getRVal1();
                    if (value instanceof Constant.ConstantBool) {
                        int val = (int) ((Constant.ConstantBool) value).getConstVal();
                        if (instr.getType().isInt32Type()) {
                            Value afterExt = new Constant.ConstantInt(val);
                            instr.modifyAllUseThisToUseA(afterExt);
                        }
                    }
                }
            }
        }
    }


    private void globalConstPtrInit() {
//        for (Function function: functions) {
//            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
//                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
//                    if (instr instanceof Instr.Alloc) {
//                        if (allocIsConst((Instr.Alloc) instr)) {
//                            constPtrs.add(instr);
//                        }
//                    }
//                }
//            }
//        }
        for (Value value: globalValues.keySet()) {
            if (value.getType().isPointerType() && globalArrayIsConst(value)) {
                constGlobalPtrs.add(value);
            }
        }
    }

    //找到没有store过的数组
    private boolean globalArrayIsConst(Value value) {
        for (Use use = value.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
            boolean ret = check(use.getUser());
            if (!ret) {
                return false;
            }
        }
        return true;
    }

    private boolean check(Instr instr) {
        for (Use use = instr.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
            Instr user = use.getUser();
            if (user instanceof Instr.GetElementPtr) {
                boolean ret = check(user);
                if (!ret) {
                    return false;
                }
            } else if (user instanceof Instr.Store) {
                return false;
            } else if (user instanceof Instr.Load) {

            } else {
                assert true;
            }
        }
        return true;
    }

    //TODO:check 语句只执行一次<==>语句在Main中且不在循环内
    //          数组==>只store一次 写入的是constValue / value

    //fixme:目前只处理全局数组,因为局部数组没有init,考虑上述问题,可以优化
    private void arrayConstFold() {
        globalConstPtrInit();
        for (Function function: functions) {
            arrayConstFoldForFunc(function);
        }
    }

    private void arrayConstFoldForFunc(Function function) {
        for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
            for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                if (instr instanceof Instr.GetElementPtr) {
                    Value ptr = ((Instr.GetElementPtr) instr).getPtr();
                    if (constGlobalPtrs.contains(ptr)) {
                        ArrayList<Value> indexs = ((Instr.GetElementPtr) instr).getIdxList();
                        for (Value value: indexs) {
                            if (!(value instanceof Constant)) {
                                continue;
                            }
                        }
                        //gep指令的数组没有被store过,且下标为常数
                        Value arrayElementVal = getConstArrayValue(ptr, (ArrayList<Value>) indexs.subList(1, indexs.size()));
                    }
                }
            }
        }
    }

    private Value getConstArrayValue(Value ptr, ArrayList<Value> indexs) {
        Initial init = globalValues.get(ptr);
        if (init instanceof Initial.ZeroInit) {
            if (ptr.getType().isInt32Type()) {
                return new Constant.ConstantInt(0);
            } else {
                return new Constant.ConstantFloat(0);
            }
        }
        for (Value tmp: indexs) {
            assert tmp instanceof Constant.ConstantInt;
            int index = (int) ((Constant) tmp).getConstVal();

        }

        return null;
    }
}
