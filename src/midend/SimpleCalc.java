package midend;

import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class SimpleCalc {

    private ArrayList<Function> functions;

    public SimpleCalc(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                HashMap<Value, HashSet<Value>> eq = new HashMap<>();
                HashMap<Value, HashSet<Value>> inverse = new HashMap<>();
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    Instr A = instr;
                    if (A.getNext().getNext() != null) {
                        Instr B = (Instr) instr.getNext();
                        if (A instanceof Instr.Alu && ((Instr.Alu) A).getOp().equals(Instr.Alu.Op.ADD) &&
                                B instanceof Instr.Alu && ((Instr.Alu) B).getOp().equals(Instr.Alu.Op.SUB) &&
                                ((Instr.Alu) A).getRVal1().equals(((Instr.Alu) B).getRVal1()) &&
                                ((Instr.Alu) B).getRVal2().equals(A)) {

                            inverse.putIfAbsent(B, new HashSet<>());
                            inverse.get(B).add(((Instr.Alu) A).getRVal2());
                        }
                    }
                    if (A instanceof Instr.Alu && ((Instr.Alu) A).getOp().equals(Instr.Alu.Op.ADD)) {
                        Value val1 = ((Instr.Alu) A).getRVal1(), val2 = ((Instr.Alu) A).getRVal2();
                        if (inverse.getOrDefault(val1, new HashSet<>()).contains(val2) ||
                                inverse.getOrDefault(val2, new HashSet<>()).contains(val1)) {
                            A.modifyAllUseThisToUseA(new Constant.ConstantInt(0));
                        } else if (val1 instanceof Constant.ConstantInt && (int) ((Constant.ConstantInt) val1).getConstVal() == 0) {
                            A.modifyAllUseThisToUseA(val2);
                            eq.putIfAbsent(A, new HashSet<>());
                            eq.get(A).add(val2);
                        } else if (val2 instanceof Constant.ConstantInt && (int) ((Constant.ConstantInt) val2).getConstVal() == 0) {
                            A.modifyAllUseThisToUseA(val1);
                            eq.putIfAbsent(A, new HashSet<>());
                            eq.get(A).add(val1);
                        }
                    } else if (A instanceof Instr.Alu && ((Instr.Alu) A).getOp().equals(Instr.Alu.Op.SUB)) {
                        Value val1 = ((Instr.Alu) A).getRVal1(), val2 = ((Instr.Alu) A).getRVal2();
                        if (val1.equals(val2)) {
                            A.modifyAllUseThisToUseA(new Constant.ConstantInt(0));
                        } else if (eq.getOrDefault(val1, new HashSet<>()).contains(val2) ||
                                eq.getOrDefault(val2, new HashSet<>()).contains(val1)) {
                            A.modifyAllUseThisToUseA(new Constant.ConstantInt(0));
                        } else if (val1 instanceof Constant.ConstantInt && (int) ((Constant.ConstantInt) val1).getConstVal() == 0) {
                            inverse.putIfAbsent(A, new HashSet<>());
                            inverse.get(A).add(val2);
                        } else if (val2 instanceof Constant.ConstantInt && (int) ((Constant.ConstantInt) val2).getConstVal() == 0) {
                            A.modifyAllUseThisToUseA(val1);
                            eq.putIfAbsent(A, new HashSet<>());
                            eq.get(A).add(val1);
                        }
                    }
                }
            }
        }
    }

}
