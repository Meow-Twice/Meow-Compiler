package midend;

import frontend.semantic.Initial;
import mir.*;
import mir.type.Type;

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
        singleBBMemoryFold();
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
            } else if (user instanceof Instr.Store || user instanceof Instr.Call) {
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
                        Boolean indexAllConstTag = true;
                        for (Value value: indexs) {
                            if (!(value instanceof Constant)) {
                                indexAllConstTag = false;
                            }
                        }
                        if (!indexAllConstTag) {
                            continue;
                        }
                        //gep指令的数组没有被store过,且下标为常数
                        ArrayList<Value> ret = new ArrayList<>();
                        for (int i = 1; i < indexs.size(); i++) {
                            ret.add(indexs.get(i));
                        }
                        Value arrayElementVal = getGlobalConstArrayValue(ptr, ret);
                        if (arrayElementVal == null) {
                            continue;
                        }
                        //此时保证GEP是可以直接load的指针
                        for (Use use = instr.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
                            Instr user = use.getUser();
                            if (!(user instanceof Instr.Load)) {
                                continue;
                            }
                            user.modifyAllUseThisToUseA(arrayElementVal);
                        }
                    }
                }
            }
        }
    }

    private Value getGlobalConstArrayValue(Value ptr, ArrayList<Value> indexs) {
        ArrayList<Integer> lens = new ArrayList<>();
        getArraySize(((Type.PointerType) ptr.getType()).getInnerType(), lens);
        Type type = ((Type.ArrayType) ((Type.PointerType) ptr.getType()).getInnerType()).getBaseEleType();
        ArrayList<Integer> offsetArray = new ArrayList<>();
        offsetArray.addAll(lens);
        for (int i = offsetArray.size() - 2; i >= 0; i--) {
            offsetArray.set(i, offsetArray.get(i + 1) * offsetArray.get(i));
        }

        Initial init = globalValues.get(ptr);
        ArrayList<Value> initArray = init.getFlattenInit();
        if (indexs.size() != offsetArray.size()) {
            return null;
        }
        //assert indexs.size() == offsetArray.size();
        //TODO:修改取init值的方式
        //      适配a[2][3] --> load a[0][5]


        if (init instanceof Initial.ZeroInit) {
            if (type.isInt32Type()) {
                return new Constant.ConstantInt(0);
            } else {
                return new Constant.ConstantFloat(0);
            }
        }
        for (Value tmp: indexs) {
            assert tmp instanceof Constant.ConstantInt;
            assert init instanceof Initial.ArrayInit;
            int index = (int) ((Constant) tmp).getConstVal();
            init = ((Initial.ArrayInit) init).get(index);
            if (init instanceof Initial.ZeroInit) {
                if (type.isInt32Type()) {
                    return new Constant.ConstantInt(0);
                } else {
                    return new Constant.ConstantFloat(0);
                }
            }
        }
        assert init instanceof Initial.ValueInit;

        return ((Initial.ValueInit) init).getValue();
    }

    private void getArraySize(Type type, ArrayList<Integer> ret) {
        if (type instanceof Type.ArrayType) {
            ret.add(((Type.ArrayType) type).getSize());
            getArraySize(((Type.ArrayType) type).getBaseType(), ret);
        }
    }

    private void singleBBMemoryFold() {
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                singleBBMemoryFoldForBB(bb);
            }
        }
    }

    private void singleBBMemoryFoldForBB(BasicBlock bb) {
        for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
            if (instr instanceof Instr.Store) {
                Value ptr = ((Instr.Store) instr).getPointer();
                Value value = ((Instr.Store) instr).getValue();
                instr = (Instr) instr.getNext();
                while (instr.getNext() != null &&
                        !(instr instanceof Instr.Call) &&
                        !(instr instanceof Instr.Store)) {
                    if (instr instanceof Instr.Load && ((Instr.Load) instr).getPointer().equals(ptr)) {
                        instr.modifyAllUseThisToUseA(value);
                    }
                    instr = (Instr) instr.getNext();
                }
                if (instr instanceof Instr.Store) {
                    instr = (Instr) instr.getPrev();
                }
            }
            if (instr.getNext() == null) {
                break;
            }
        }
    }
}
