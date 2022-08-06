package midend;

import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class MathOptimize {

    private static int adds_to_mul_boundary = 10;

    private ArrayList<Function> functions;
    //private HashSet<MulPair> mulSet;
    //%1 = mul %2, 100
    // key ==> %1
    // value ==> 100
    private HashMap<Instr, Integer> mulMap = new HashMap<>();

    public MathOptimize(ArrayList<Function> functions) {
        this.functions = functions;
        }

    public void Run() {
        continueAddToMul();
        addZeroFold();
        mulDivFold();
    }

    private void continueAddToMul() {
        for (Function function: functions) {
            BasicBlock bb = function.getBeginBB();
            while (bb.getNext() != null) {
                Instr instr = bb.getBeginInstr();

                while (instr.getNext() != null) {
                    continueAddToMulForInstr(instr);
                    instr = (Instr) instr.getNext();
                }

                bb = (BasicBlock) bb.getNext();
            }
        }
    }

    private void continueAddToMulForInstr(Instr instr) {
        int cnt = 0;
        Instr beginInstr = instr;
        if (!(instr instanceof Instr.Alu)) {
            return;
        }
        //连加的启动,能否启动
        Value rhs1 = instr.getUseValueList().get(0);
        Value rhs2 = instr.getUseValueList().get(1);
        Value base = null;
        Value addValue = null;
        if (addCanTransToMul(instr, rhs1)) {
            base = rhs1;
            addValue = rhs2;
        } else if (addCanTransToMul(instr, rhs2)) {
            base = rhs2;
            addValue = rhs1;
        } else {
            return;
        }

        ArrayList<Instr> adds = new ArrayList<>();
        adds.add(instr);
        cnt = 1;
        while (addCanTransToMul(instr, base)) {
            adds.add(getOneUser(instr));
            instr = (Instr) instr.getNext();
            cnt++;
        }

        BasicBlock bb = instr.parentBB();
        if (cnt > adds_to_mul_boundary) {
            //TODO:do trans adds -> mul
            if (addValue.equals(base)) {
                cnt++;
//                Instr.Alu mul = new Instr.Alu(instr.getType(), Instr.Alu.Op.MUL, base, new Constant.ConstantInt(cnt), bb);
//                instr.insertBefore(mul);
//                instr.modifyUse(mul, 0);
//                instr.modifyUse(addValue, 1);

                ((Instr.Alu) instr).setOp(Instr.Alu.Op.MUL);
                instr.modifyUse(base, 0);
                instr.modifyUse(new Constant.ConstantInt(cnt), 1);
                for (int i = 0; i < adds.size() - 1; i++) {
                    adds.get(i).remove();
                }

                beginInstr.setNext(instr.getNext());
                return;
            } else {
                Instr.Alu mul = new Instr.Alu(instr.getType(), Instr.Alu.Op.MUL, base, new Constant.ConstantInt(cnt), bb);
                instr.insertBefore(mul);
                instr.modifyUse(mul, 0);
                instr.modifyUse(addValue, 1);

                for (int i = 0; i < adds.size() - 1; i++) {
                    adds.get(i).remove();
                }

                beginInstr.setNext(instr.getNext());
                return;
            }
        }
    }

    private boolean addCanTransToMul(Instr instr, Value value) {
        if (!(instr instanceof Instr.Alu && ((Instr.Alu) instr).getOp().equals(Instr.Alu.Op.ADD))) {
            return false;
        }
        //TODO:似乎不是必要,可以考虑一下如何找到可以归越成多个乘法的多个连加
        if (!instr.onlyOneUser()) {
            return false;
        }
        Use use = instr.getBeginUse();
        Instr user = use.getUser();
        if (!(user instanceof Instr.Alu && ((Instr.Alu) user).getOp().equals(Instr.Alu.Op.ADD))) {
            return false;
        }
        if (value instanceof Constant.ConstantInt) {
            int index = 1 - user.getUseValueList().indexOf(instr);
            int val1 = (int) ((Constant.ConstantInt) value).getConstVal();
            Value value2 = user.getUseValueList().get(index);
            if (value2 instanceof Constant.ConstantInt) {
                int val2 = (int) ((Constant.ConstantInt) value2).getConstVal();
                return val1 == val2;
            } else {
                return false;
            }
        }
        return user.getUseValueList().contains(value);
    }

    //只对 只有一个user的指令保证正确
    private Instr getOneUser(Instr instr) {
        return instr.getBeginUse().getUser();
    }

    private void addZeroFold() {
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (instr instanceof Instr.Alu && ((Instr.Alu) instr).getOp().equals(Instr.Alu.Op.ADD)) {
                        if (((Instr.Alu) instr).getRVal1() instanceof Constant.ConstantInt) {
                            int val = (int) ((Constant.ConstantInt) ((Instr.Alu) instr).getRVal1()).getConstVal();
                            if (val == 0) {
                                instr.modifyAllUseThisToUseA(((Instr.Alu) instr).getRVal2());
                                instr.remove();
                            }
                        } else if (((Instr.Alu) instr).getRVal2() instanceof Constant.ConstantInt) {
                            int val = (int) ((Constant.ConstantInt) ((Instr.Alu) instr).getRVal2()).getConstVal();
                            if (val == 0) {
                                instr.modifyAllUseThisToUseA(((Instr.Alu) instr).getRVal1());
                                instr.remove();
                            }
                        }
                    }
                }
            }
        }
    }

    private void mulDivFold() {
        mulMap.clear();
        for (Function function: functions) {
            BasicBlock bb = function.getBeginBB();
            RPOSearch(bb);
        }
    }

    private void RPOSearch(BasicBlock bb) {
        HashSet<Instr> adds = new HashSet<>();

        for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
            if (instr instanceof Instr.Alu) {
                Instr.Alu.Op op = ((Instr.Alu) instr).getOp();
                if (op.equals(Instr.Alu.Op.MUL)) {
                    Value val1 = ((Instr.Alu) instr).getRVal1();
                    Value val2 = ((Instr.Alu) instr).getRVal2();
                    if (val1 instanceof Constant.ConstantInt) {
                        int time = (int) ((Constant.ConstantInt) val1).getConstVal();
                        adds.add(instr);
                        mulMap.put(instr, time);
                    } else if (val2 instanceof Constant.ConstantInt) {
                        int time = (int) ((Constant.ConstantInt) val2).getConstVal();
                        adds.add(instr);
                        mulMap.put(instr, time);
                    }
                } else if (op.equals(Instr.Alu.Op.DIV)) {
                    if (((Instr.Alu) instr).getRVal2() instanceof Constant.ConstantInt) {
                        int divTime = (int) ((Constant.ConstantInt) ((Instr.Alu) instr).getRVal2()).getConstVal();
                        if (((Instr.Alu) instr).getRVal1() instanceof Instr) {
                            Instr val = (Instr) ((Instr.Alu) instr).getRVal1();
                            if (mulMap.containsKey(val) && mulMap.get(val) == divTime) {
                                assert val instanceof Instr.Alu;
                                if (((Instr.Alu) val).getRVal1() instanceof Constant.ConstantInt) {
                                    instr.modifyAllUseThisToUseA(((Instr.Alu) val).getRVal2());
                                    instr.remove();
                                } else if (((Instr.Alu) val).getRVal2() instanceof Constant.ConstantInt) {
                                    instr.modifyAllUseThisToUseA(((Instr.Alu) val).getRVal1());
                                    instr.remove();
                                } else {
                                    assert false;
                                }
                            }
                        }
                    }
                }
            }
        }

        for (BasicBlock next: bb.getIdoms()) {
            RPOSearch(next);
        }

        for (Instr instr: adds) {
            mulMap.remove(instr);
        }


    }


}
