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
                } else if (instr.canCombFloat()) {
                    ArrayList<Instr.Alu> tags = new ArrayList<>();
                    Use use = instr.getBeginUse();
                    while (use.getNext() != null) {
                        assert use.getUser() instanceof Instr.Alu;
                        tags.add((Instr.Alu) use.getUser());
                        use = (Use) use.getNext();
                    }
                    assert instr instanceof Instr.Alu;
                    combSrcToTagsFloat((Instr.Alu) instr, tags);
                    instr.remove();
                } else if (instr.canCombMul()) {
                    ArrayList<Instr.Alu> tags = new ArrayList<>();
                    Use use = instr.getBeginUse();
                    while (use.getNext() != null) {
                        assert use.getUser() instanceof Instr.Alu;
                        tags.add((Instr.Alu) use.getUser());
                        use = (Use) use.getNext();
                    }
                    assert instr instanceof Instr.Alu;
                    combSrcToTagsMul((Instr.Alu) instr, tags);
                    instr.remove();
                } else if (instr.canCombMulFloat()) {
                    ArrayList<Instr.Alu> tags = new ArrayList<>();
                    Use use = instr.getBeginUse();
                    while (use.getNext() != null) {
                        assert use.getUser() instanceof Instr.Alu;
                        tags.add((Instr.Alu) use.getUser());
                        use = (Use) use.getNext();
                    }
                    assert instr instanceof Instr.Alu;
                    combSrcToTagsMulFloat((Instr.Alu) instr, tags);
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

    private void combSrcToTagsFloat(Instr.Alu src, ArrayList<Instr.Alu> tags) {
        for (Instr.Alu alu: tags) {
            combAToBFloat(src, alu);
        }
    }

    private void combSrcToTagsMul(Instr.Alu src, ArrayList<Instr.Alu> tags) {
        for (Instr.Alu alu: tags) {
            combAToBMul(src, alu);
        }
    }


    private void combSrcToTagsMulFloat(Instr.Alu src, ArrayList<Instr.Alu> tags) {
        for (Instr.Alu alu: tags) {
            combAToBMulFloat(src, alu);
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

    private void combAToBFloat(Instr.Alu A, Instr.Alu B) {
        ArrayList<Value> AUseList = A.getUseValueList();
        ArrayList<Value> BUseList = B.getUseValueList();
        float ConstInA = 0, ConstInB = 0;
        boolean ConstInAIs0 = false, ConstInBIs0 = false;
        if (AUseList.get(0) instanceof Constant) {
            ConstInA = (float) ((Constant.ConstantFloat) AUseList.get(0)).getConstVal();
            ConstInAIs0 = true;
        } else {
            ConstInA = (float) ((Constant.ConstantFloat) AUseList.get(1)).getConstVal();
        }

        if (BUseList.get(0) instanceof Constant) {
            ConstInB = (float) ((Constant.ConstantFloat) BUseList.get(0)).getConstVal();
            ConstInBIs0 = true;
        } else {
            ConstInB = (float) ((Constant.ConstantFloat) BUseList.get(1)).getConstVal();
        }
        if (ConstInAIs0 && ConstInBIs0 && A.isFAdd() && B.isFAdd()) {
            Constant.ConstantFloat constantFloat = new Constant.ConstantFloat(ConstInA + ConstInB);
            B.modifyUse(constantFloat, 0);
            B.modifyUse(AUseList.get(1), 1);
            //A.remove();
        }
        else if (ConstInAIs0 && ConstInBIs0 && A.isFAdd() && !B.isFAdd()) {
            Constant.ConstantFloat constantFloat = new Constant.ConstantFloat(ConstInB - ConstInA);
            B.modifyUse(constantFloat, 0);
            B.modifyUse(AUseList.get(1), 1);
            //A.remove();
        }
        else if (ConstInAIs0 && ConstInBIs0 && !A.isFAdd() && B.isFAdd()) {
            Constant.ConstantFloat constantFloat = new Constant.ConstantFloat(ConstInB + ConstInA);
            B.modifyUse(constantFloat, 0);
            B.modifyUse(AUseList.get(1), 1);
            B.setOp(Instr.Alu.Op.FSUB);
            //A.remove();
        }
        else if (ConstInAIs0 && ConstInBIs0 && !A.isFAdd() && !B.isFAdd()) {
            Constant.ConstantFloat constantFloat = new Constant.ConstantFloat(ConstInB - ConstInA);
            B.modifyUse(constantFloat, 0);
            B.modifyUse(AUseList.get(1), 1);
            B.setOp(Instr.Alu.Op.FADD);
            //A.remove();
        }


        else if (ConstInAIs0 && !ConstInBIs0 && A.isFAdd() && B.isFAdd()) {
            Constant.ConstantFloat constantFloat = new Constant.ConstantFloat(ConstInA + ConstInB);
            B.modifyUse(constantFloat, 1);
            B.modifyUse(AUseList.get(1), 0);
            //A.remove();
        }
        else if (ConstInAIs0 && !ConstInBIs0 && A.isFAdd() && !B.isFAdd()) {
            Constant.ConstantFloat constantFloat = new Constant.ConstantFloat(ConstInA - ConstInB);
            B.modifyUse(constantFloat, 1);
            B.modifyUse(AUseList.get(1), 0);
            B.setOp(Instr.Alu.Op.FADD);
            //A.remove();
        }
        //b = x - a; c = b + y; c = (x+y) - a
        else if (ConstInAIs0 && !ConstInBIs0 && !A.isFAdd() && B.isFAdd()) {
            Constant.ConstantFloat constantFloat = new Constant.ConstantFloat(ConstInA + ConstInB);
            B.modifyUse(constantFloat, 0);
            B.modifyUse(AUseList.get(1), 1);
            B.setOp(Instr.Alu.Op.FSUB);
            //A.remove();
        }
        //b = x - a; c = b - y ==> c = (x - y) - a
        else if (ConstInAIs0 && !ConstInBIs0 && !A.isFAdd() && !B.isFAdd()) {
            Constant.ConstantFloat constantFloat = new Constant.ConstantFloat(ConstInA - ConstInB);
            B.modifyUse(constantFloat, 0);
            B.modifyUse(AUseList.get(1), 1);
            //A.remove();
        }



        else if (!ConstInAIs0 && ConstInBIs0 && A.isFAdd() && B.isFAdd()) {
            Constant.ConstantFloat constantFloat = new Constant.ConstantFloat(ConstInA + ConstInB);
            B.modifyUse(constantFloat, 0);
            B.modifyUse(AUseList.get(0), 1);
            //A.remove();
        }
        else if (!ConstInAIs0 && ConstInBIs0 && A.isFAdd() && !B.isFAdd()) {
            Constant.ConstantFloat constantFloat = new Constant.ConstantFloat(ConstInB - ConstInA);
            B.modifyUse(constantFloat, 0);
            B.modifyUse(AUseList.get(0), 1);
            //A.remove();
        }
        else if (!ConstInAIs0 && ConstInBIs0 && !A.isFAdd() && B.isFAdd()) {
            Constant.ConstantFloat constantFloat = new Constant.ConstantFloat(ConstInB - ConstInA);
            B.modifyUse(constantFloat, 0);
            B.modifyUse(AUseList.get(0), 1);
            //A.remove();
        }
        else if (!ConstInAIs0 && ConstInBIs0 && !A.isFAdd() && !B.isFAdd()) {
            Constant.ConstantFloat constantFloat = new Constant.ConstantFloat(ConstInB + ConstInA);
            B.modifyUse(constantFloat, 0);
            B.modifyUse(AUseList.get(0), 1);
            //A.remove();
        }

        else if (!ConstInAIs0 && !ConstInBIs0 && A.isFAdd() && B.isFAdd()) {
            Constant.ConstantFloat constantFloat = new Constant.ConstantFloat(ConstInA + ConstInB);
            B.modifyUse(constantFloat, 0);
            B.modifyUse(AUseList.get(0), 1);
            //A.remove();
        }
        else if (!ConstInAIs0 && !ConstInBIs0 && A.isFAdd() && !B.isFAdd()) {
            Constant.ConstantFloat constantFloat = new Constant.ConstantFloat(ConstInA - ConstInB);
            B.modifyUse(constantFloat, 0);
            B.modifyUse(AUseList.get(0), 1);
            B.setOp(Instr.Alu.Op.FADD);
            //A.remove();
        }
        else if (!ConstInAIs0 && !ConstInBIs0 && !A.isFAdd() && B.isFAdd()) {
            Constant.ConstantFloat constantFloat = new Constant.ConstantFloat(ConstInB - ConstInA);
            B.modifyUse(constantFloat, 0);
            B.modifyUse(AUseList.get(0), 1);
            //A.remove();
        }

        //b = a - x; c = b - y; c = a - (x + y)
        else if (!ConstInAIs0 && !ConstInBIs0 && !A.isFAdd() && !B.isFAdd()) {
            Constant.ConstantFloat constantFloat = new Constant.ConstantFloat(-ConstInB - ConstInA);
            B.modifyUse(constantFloat, 0);
            B.modifyUse(AUseList.get(0), 1);
            B.setOp(Instr.Alu.Op.FADD);
            //A.remove();
        }
    }


    private void combAToBMul(Instr.Alu A, Instr.Alu B) {
        ArrayList<Value> AUseList = A.getUseValueList();
        ArrayList<Value> BUseList = B.getUseValueList();
        int ConstInA = 0, ConstInB = 0;
        boolean ConstInAIs0 = false, ConstInBIs0 = false;
        if (AUseList.get(0) instanceof Constant) {
            ConstInA = (int) ((Constant.ConstantInt) AUseList.get(0)).getConstVal();
            ConstInAIs0 = true;
        } else {
            ConstInA = (int) ((Constant.ConstantInt) AUseList.get(1)).getConstVal();
        }

        if (BUseList.get(0) instanceof Constant) {
            ConstInB = (int) ((Constant.ConstantInt) BUseList.get(0)).getConstVal();
            ConstInBIs0 = true;
        } else {
            ConstInB = (int) ((Constant.ConstantInt) BUseList.get(1)).getConstVal();
        }

        Constant.ConstantInt constantInt = new Constant.ConstantInt(ConstInA * ConstInB);
        if (ConstInAIs0 && ConstInBIs0) {
            B.modifyUse(constantInt, 0);
            B.modifyUse(AUseList.get(1), 1);
            //A.remove();
        }
        else if (ConstInAIs0 && !ConstInBIs0) {
            B.modifyUse(constantInt, 1);
            B.modifyUse(AUseList.get(1), 0);
            //A.remove();
        }
        else if (!ConstInAIs0 && ConstInBIs0) {
            B.modifyUse(constantInt, 0);
            B.modifyUse(AUseList.get(1), 1);
            //A.remove();
        }
        else if (!ConstInAIs0 && !ConstInBIs0) {
            B.modifyUse(constantInt, 1);
            B.modifyUse(AUseList.get(1), 0);
            //A.remove();
        }
    }



    private void combAToBMulFloat(Instr.Alu A, Instr.Alu B) {
        ArrayList<Value> AUseList = A.getUseValueList();
        ArrayList<Value> BUseList = B.getUseValueList();
        float ConstInA = 0, ConstInB = 0;
        boolean ConstInAIs0 = false, ConstInBIs0 = false;
        if (AUseList.get(0) instanceof Constant) {
            ConstInA = (float) ((Constant.ConstantFloat) AUseList.get(0)).getConstVal();
            ConstInAIs0 = true;
        } else {
            ConstInA = (float) ((Constant.ConstantFloat) AUseList.get(1)).getConstVal();
        }

        if (BUseList.get(0) instanceof Constant) {
            ConstInB = (float) ((Constant.ConstantFloat) BUseList.get(0)).getConstVal();
            ConstInBIs0 = true;
        } else {
            ConstInB = (float) ((Constant.ConstantFloat) BUseList.get(1)).getConstVal();
        }

        Constant.ConstantFloat constantFloat = new Constant.ConstantFloat(ConstInA * ConstInB);
        if (ConstInAIs0 && ConstInBIs0) {
            B.modifyUse(constantFloat, 0);
            B.modifyUse(AUseList.get(1), 1);
            //A.remove();
        }
        else if (ConstInAIs0 && !ConstInBIs0) {
            B.modifyUse(constantFloat, 1);
            B.modifyUse(AUseList.get(1), 0);
            //A.remove();
        }
        else if (!ConstInAIs0 && ConstInBIs0) {
            B.modifyUse(constantFloat, 0);
            B.modifyUse(AUseList.get(1), 1);
            //A.remove();
        }
        else if (!ConstInAIs0 && !ConstInBIs0) {
            B.modifyUse(constantFloat, 1);
            B.modifyUse(AUseList.get(1), 0);
            //A.remove();
        }
    }
}
