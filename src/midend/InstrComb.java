package midend;

import mir.*;

import java.util.ArrayList;

public class InstrComb {

    //TODO:把乘法 除法 取模也纳入考虑
    private ArrayList<Function> functions;

    public InstrComb(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        for (Function function: functions) {
            InstrCombForFunc(function);
        }
    }

    private void InstrCombForFunc(Function function) {
        BasicBlock bb = function.getBeginBB();
        while (bb.getNext() != null) {
            Instr instr = bb.getBeginInstr();
            while (instr.getNext() != null) {
                if (instr.canComb())  {
                    ArrayList<Instr.Alu> tags = new ArrayList<>();
                    Use use = instr.getBeginUse();
                    while (use.getNext() != null) {
                        assert use.getUser() instanceof Instr.Alu;
                        tags.add((Instr.Alu) use.getUser());
                        use = (Use) use.getNext();
                    }
                    assert instr instanceof Instr.Alu;
                    combSrcToTags((Instr.Alu) instr, tags);
                    instr.remove();
                }
                instr = (Instr) instr.getNext();
            }
            bb = (BasicBlock) bb.getNext();
        }
    }

    private void combSrcToTags(Instr.Alu src, ArrayList<Instr.Alu> tags) {
        for (Instr.Alu alu: tags) {
            combAToB(src, alu);
        }
    }

    private void combAToB(Instr.Alu A, Instr.Alu B) {
        ArrayList<Value> AUseList = A.getUseValueList();
        ArrayList<Value> BUseList = B.getUseValueList();
        int ConstInA = 0, ConstInB = 0;
        boolean ConstInAIs0 = false, ConstInBIs0 = false;
        if (AUseList.get(0) instanceof Constant) {
            ConstInA = (int) ((Constant) AUseList.get(0)).getConstVal();
            ConstInAIs0 = true;
        } else {
            ConstInA = (int) ((Constant) AUseList.get(1)).getConstVal();
        }

        if (BUseList.get(0) instanceof Constant) {
            ConstInB = (int) ((Constant) BUseList.get(0)).getConstVal();
            ConstInBIs0 = true;
        } else {
            ConstInB = (int) ((Constant) BUseList.get(1)).getConstVal();
        }
        if (ConstInAIs0 && ConstInBIs0 && A.isAdd() && B.isAdd()) {
            Constant.ConstantInt constantInt = new Constant.ConstantInt(ConstInA + ConstInB);
            B.modifyUse(constantInt, 0);
            B.modifyUse(AUseList.get(1), 1);
            //A.remove();
        }
        else if (ConstInAIs0 && ConstInBIs0 && A.isAdd() && !B.isAdd()) {
            Constant.ConstantInt constantInt = new Constant.ConstantInt(ConstInB - ConstInA);
            B.modifyUse(constantInt, 0);
            B.modifyUse(AUseList.get(1), 1);
            //A.remove();
        }
        else if (ConstInAIs0 && ConstInBIs0 && !A.isAdd() && B.isAdd()) {
            Constant.ConstantInt constantInt = new Constant.ConstantInt(ConstInB + ConstInA);
            B.modifyUse(constantInt, 0);
            B.modifyUse(AUseList.get(1), 1);
            B.setOp(Instr.Alu.Op.SUB);
            //A.remove();
        }
        else if (ConstInAIs0 && ConstInBIs0 && !A.isAdd() && !B.isAdd()) {
            Constant.ConstantInt constantInt = new Constant.ConstantInt(ConstInB - ConstInA);
            B.modifyUse(constantInt, 0);
            B.modifyUse(AUseList.get(1), 1);
            B.setOp(Instr.Alu.Op.ADD);
            //A.remove();
        }


        else if (ConstInAIs0 && !ConstInBIs0 && A.isAdd() && B.isAdd()) {
            Constant.ConstantInt constantInt = new Constant.ConstantInt(ConstInA + ConstInB);
            B.modifyUse(constantInt, 1);
            B.modifyUse(AUseList.get(1), 0);
            //A.remove();
        }
        else if (ConstInAIs0 && !ConstInBIs0 && A.isAdd() && !B.isAdd()) {
            Constant.ConstantInt constantInt = new Constant.ConstantInt(ConstInA - ConstInB);
            B.modifyUse(constantInt, 1);
            B.modifyUse(AUseList.get(1), 0);
            B.setOp(Instr.Alu.Op.ADD);
            //A.remove();
        }
        //b = x - a; c = b + y; c = (x+y) - a
        else if (ConstInAIs0 && !ConstInBIs0 && !A.isAdd() && B.isAdd()) {
            Constant.ConstantInt constantInt = new Constant.ConstantInt(ConstInA + ConstInB);
            B.modifyUse(constantInt, 0);
            B.modifyUse(AUseList.get(1), 1);
            B.setOp(Instr.Alu.Op.SUB);
            //A.remove();
        }
        //b = x - a; c = b - y ==> c = (x - y) - a
        else if (ConstInAIs0 && !ConstInBIs0 && !A.isAdd() && !B.isAdd()) {
            Constant.ConstantInt constantInt = new Constant.ConstantInt(ConstInA - ConstInB);
            B.modifyUse(constantInt, 0);
            B.modifyUse(AUseList.get(1), 1);
            //A.remove();
        }



        else if (!ConstInAIs0 && ConstInBIs0 && A.isAdd() && B.isAdd()) {
            Constant.ConstantInt constantInt = new Constant.ConstantInt(ConstInA + ConstInB);
            B.modifyUse(constantInt, 0);
            B.modifyUse(AUseList.get(0), 1);
            //A.remove();
        }
        else if (!ConstInAIs0 && ConstInBIs0 && A.isAdd() && !B.isAdd()) {
            Constant.ConstantInt constantInt = new Constant.ConstantInt(ConstInB - ConstInA);
            B.modifyUse(constantInt, 0);
            B.modifyUse(AUseList.get(0), 1);
            //A.remove();
        }
        else if (!ConstInAIs0 && ConstInBIs0 && !A.isAdd() && B.isAdd()) {
            Constant.ConstantInt constantInt = new Constant.ConstantInt(ConstInB - ConstInA);
            B.modifyUse(constantInt, 0);
            B.modifyUse(AUseList.get(0), 1);
            //A.remove();
        }
        else if (!ConstInAIs0 && ConstInBIs0 && !A.isAdd() && !B.isAdd()) {
            Constant.ConstantInt constantInt = new Constant.ConstantInt(ConstInB + ConstInA);
            B.modifyUse(constantInt, 0);
            B.modifyUse(AUseList.get(0), 1);
            //A.remove();
        }

        else if (!ConstInAIs0 && !ConstInBIs0 && A.isAdd() && B.isAdd()) {
            Constant.ConstantInt constantInt = new Constant.ConstantInt(ConstInA + ConstInB);
            B.modifyUse(constantInt, 0);
            B.modifyUse(AUseList.get(0), 1);
            //A.remove();
        }
        else if (!ConstInAIs0 && !ConstInBIs0 && A.isAdd() && !B.isAdd()) {
            Constant.ConstantInt constantInt = new Constant.ConstantInt(ConstInA - ConstInB);
            B.modifyUse(constantInt, 0);
            B.modifyUse(AUseList.get(0), 1);
            B.setOp(Instr.Alu.Op.ADD);
            //A.remove();
        }
        else if (!ConstInAIs0 && !ConstInBIs0 && !A.isAdd() && B.isAdd()) {
            Constant.ConstantInt constantInt = new Constant.ConstantInt(ConstInB - ConstInA);
            B.modifyUse(constantInt, 0);
            B.modifyUse(AUseList.get(0), 1);
            //A.remove();
        }

        //b = a - x; c = b - y; c = a - (x + y)
        else if (!ConstInAIs0 && !ConstInBIs0 && !A.isAdd() && !B.isAdd()) {
            Constant.ConstantInt constantInt = new Constant.ConstantInt(-ConstInB - ConstInA);
            B.modifyUse(constantInt, 0);
            B.modifyUse(AUseList.get(0), 1);
            B.setOp(Instr.Alu.Op.ADD);
            //A.remove();
        }
    }
}
