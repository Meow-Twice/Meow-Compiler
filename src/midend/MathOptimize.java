package midend;

import mir.*;

import java.util.ArrayList;

public class MathOptimize {

    private static int adds_to_mul_boundary = 10;

    private ArrayList<Function> functions;

    public MathOptimize(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        continueAddToMul();
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
        return user.getUseValueList().contains(value);
    }

    //只对 只有一个user的指令保证正确
    private Instr getOneUser(Instr instr) {
        return instr.getBeginUse().getUser();
    }


}
