package midend;

import mir.*;
import mir.type.Type;

import java.util.ArrayList;

public class GepFuse {

    //考虑不能融合的情况?
    //TODO:是否存在不能融合的情况?
    private ArrayList<Function> functions;
    private ArrayList<Instr.GetElementPtr> Geps = new ArrayList<>();

    public GepFuse(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        //gepFuse();
    }

    private void gepFuse() {
        for (Function function: functions) {
            gepFuseForFunc(function);
        }
    }

    private void gepFuseForFunc(Function function) {
        //删除冗余GEP
        for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
            for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                if (instr instanceof Instr.GetElementPtr && isZeroOffsetGep((Instr.GetElementPtr) instr)) {
                    instr.modifyAllUseThisToUseA(((Instr.GetElementPtr) instr).getPtr());
                }
            }
        }

        //GEP融合
//        for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
//            for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
//                if (instr instanceof Instr.GetElementPtr && !(((Instr.GetElementPtr) instr).getPtr() instanceof Instr.GetElementPtr)) {
//                    fuseDFS(instr);
//                }
//            }
//        }

    }

    private boolean isZeroOffsetGep(Instr.GetElementPtr gep) {
        if (!(gep.getPtr() instanceof Instr.GetElementPtr)) {
            return false;
        }
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

    private void  fuseDFS(Instr instr) {
        Geps.add((Instr.GetElementPtr) instr);
        for (Use use = instr.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
            if (use.getUser() instanceof Instr.GetElementPtr) {
                fuseDFS(use.getUser());
            } else {
                fuse();
            }
        }
        Geps.remove(Geps.size() - 1);
    }

    private void fuse() {
        if (Geps.size() == 1) {
            return;
        }
        Instr.GetElementPtr ret = Geps.get(0);
        int dim = ((Type.ArrayType) ((Type.PointerType) Geps.get(0).getPtr().getType()).getInnerType()).getDimSize();
        int sum = 0;
        for (Instr.GetElementPtr gep: Geps) {
            //TODO:在过程中发现偏移相等了
            if (sum == dim) {
                return;
            }
            sum += gep.getIdxList().size() - 1;
        }
        assert sum <= dim;
        if (sum < dim) {
            return;
        }
        for (int i = 1; i < Geps.size(); i++) {
            Instr.GetElementPtr gep = Geps.get(i);
            for (int j = 1; j < gep.getIdxList().size(); j++) {
                ret.addIdx(gep.getIdxList().get(j));
            }
        }
        Geps.get(Geps.size() - 1).modifyAllUseThisToUseA(ret);
    }
}
