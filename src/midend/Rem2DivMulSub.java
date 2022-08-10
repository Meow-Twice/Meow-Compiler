package midend;

import mir.*;
import mir.type.Type;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IntSummaryStatistics;

public class Rem2DivMulSub {

    private ArrayList<Function> functions;
    private HashSet<Instr.Alu> rems = new HashSet<>();
    private HashSet<Instr.Alu> frems = new HashSet<>();


    public Rem2DivMulSub(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        init();
        transRemToDivMulSub();
    }

    private void init() {
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (instr instanceof Instr.Alu) {
                        if (((Instr.Alu) instr).getOp().equals(Instr.Alu.Op.REM)) {
                            rems.add((Instr.Alu) instr);
                        } else if (((Instr.Alu) instr).getOp().equals(Instr.Alu.Op.FREM)) {
                            frems.add((Instr.Alu) instr);
                        }
                    }
                }
            }
        }
    }

    private void transRemToDivMulSub() {
        for (Instr.Alu rem: rems) {
            Value A = rem.getRVal1();
            Value B = rem.getRVal2();
            if (B instanceof Constant.ConstantInt) {
                int val = (int) ((Constant.ConstantInt) B).getConstVal();
                if (val > 0 && ((int) Math.pow(2, ((int) (Math.log(val) / Math.log(2))))) == val) {
                    continue;
                }
            }
            Instr div = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.DIV, A, B, rem.parentBB());
            rem.insertBefore(div);
            Instr mul = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.MUL, div, B, rem.parentBB());
            rem.insertBefore(mul);
            Instr mod = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.SUB, A, mul, rem.parentBB());
            rem.insertBefore(mod);

            rem.modifyAllUseThisToUseA(mod);
        }

        for (Instr.Alu frem: frems) {
            Value A = frem.getRVal1();
            Value B = frem.getRVal2();
            Instr div = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.FDIV, A, B, frem.parentBB());
            frem.insertBefore(div);
            Instr mul = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.FMUL, div, B, frem.parentBB());
            frem.insertBefore(mul);
            Instr mod = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.FSUB, A, mul, frem.parentBB());
            frem.insertBefore(mod);

            frem.modifyAllUseThisToUseA(mod);
        }
    }
}
