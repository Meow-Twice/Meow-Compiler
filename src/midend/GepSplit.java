package midend;

import mir.*;
import mir.type.Type;

import java.util.ArrayList;

public class GepSplit {
    private ArrayList<Function> functions;
    public GepSplit(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        GepSplit();
        RemoveUselessGep();
    }

    private void GepSplit() {
        for (Function function: functions) {
            gepSplitForFunc(function);
        }
    }

    //TODO:GEP最终的格式?
    //  %v30 = getelementptr inbounds [3 x [4 x [5 x i32]]], [3 x [4 x [5 x i32]]]* %f1, i32 1, i32 2
    // ----------------
    // 是否需要保证index[0] == 0 ==> choose
    // %v30 = getelementptr inbounds [3 x [4 x [5 x i32]]], [3 x [4 x [5 x i32]]]* %f1, i32 1
    // %v31 = getelementptr inbounds [3 x [4 x [5 x i32]]], [3 x [4 x [5 x i32]]]* %30, i32 0, i32 2
    // 不允许强制类型转换
    // %v30 = getelementptr inbounds [3 x [4 x [5 x i32]]], [3 x [4 x [5 x i32]]]* %f1, i32 1
    // %v31 = getelementptr inbounds [4 x [5 x i32]], [4 x [5 x i32]]* %30, i32 2
    private void gepSplitForFunc(Function function) {
        for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
            for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                if (instr instanceof Instr.GetElementPtr && ((Instr.GetElementPtr) instr).getIdxList().size() >= 2) {
                    split((Instr.GetElementPtr) instr);
                }
            }
        }
    }

    private void split(Instr.GetElementPtr gep) {
        BasicBlock bb = gep.parentBB();
        ArrayList<Value> indexs = new ArrayList<>(gep.getIdxList().subList(1, gep.getIdxList().size()));
        Value ptr = gep.getPtr();
        Instr pos = gep;
        Value pre = gep.getIdxList().get(0);
        ArrayList<Value> preIndexs = new ArrayList<>();
        preIndexs.add(pre);
        if (gep.getIdxList().size() == 2) {
            if (pre instanceof Constant && ((int) ((Constant) pre).getConstVal()) == 0) {
                return;
            }
        }
        Instr preOffset = new Instr.GetElementPtr(((Type.PointerType) ptr.getType()).getInnerType(), ptr, preIndexs, bb);
        pos.insertAfter(preOffset);
        pos = preOffset;
        ptr = preOffset;
        for (Value index: indexs) {
            ArrayList<Value> tempIndexs = new ArrayList<>();
            tempIndexs.add(new Constant.ConstantInt(0));
            tempIndexs.add(index);
            Instr.GetElementPtr temp = new Instr.GetElementPtr(((Type.ArrayType) ((Type.PointerType) ptr.getType()).getInnerType()).getBaseType(), ptr, tempIndexs, bb);
            pos.insertAfter(temp);
            pos = temp;
            ptr = temp;
        }
        gep.modifyAllUseThisToUseA(pos);
    }

//    private void splitA(Instr.GetElementPtr gep) {
//        BasicBlock bb = gep.parentBB();
//        ArrayList<Value> indexs = new ArrayList<>(gep.getIdxList().subList(1, gep.getIdxList().size()));
//        Value ptr = gep.getPtr();
//        Instr pos = gep;
//        for (Value index: indexs) {
//            ArrayList<Value> tempIndexs = new ArrayList<>();
//            tempIndexs.add(new Constant.ConstantInt(0));
//            tempIndexs.add(index);
//            Instr.GetElementPtr temp = new Instr.GetElementPtr(((Type.ArrayType) ((Type.PointerType) ptr.getType()).getInnerType()).getBaseType(), ptr, tempIndexs, bb);
//            pos.insertAfter(temp);
//            pos = temp;
//            ptr = temp;
//        }
//        gep.modifyAllUseThisToUseA(pos);
//    }

    private void RemoveUselessGep() {
        for (Function function: functions) {
            //删除冗余GEP
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (instr instanceof Instr.GetElementPtr && isZeroOffsetGep((Instr.GetElementPtr) instr)) {
                        instr.modifyAllUseThisToUseA(((Instr.GetElementPtr) instr).getPtr());
                    }
                }
            }
        }
    }

    private boolean isZeroOffsetGep(Instr.GetElementPtr gep) {
        ArrayList<Value> values = gep.getIdxList();
        if (values.size() > 1) {
            return false;
        }
        for (Value value: values) {
            if (!(value instanceof Constant)) {
                return false;
            }
            int val = (int) ((Constant) value).getConstVal();
            if (val != 0) {
                return false;
            }
        }
        return true;
    }
}
