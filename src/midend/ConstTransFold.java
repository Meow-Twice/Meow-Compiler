package midend;

import mir.BasicBlock;
import mir.Constant;
import mir.Function;
import mir.Instr;

import java.util.ArrayList;

public class ConstTransFold {

    private ArrayList<Function> functions;

    public ConstTransFold(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext()!= null; instr = (Instr) instr.getNext()) {
                    if (instr instanceof Instr.FPtosi && ((Instr.FPtosi) instr).getRVal1() instanceof Constant.ConstantFloat) {
                        float val = (float) ((Constant.ConstantFloat) ((Instr.FPtosi) instr).getRVal1()).getConstVal();
                        int ret = (int) val;

                        instr.modifyAllUseThisToUseA(new Constant.ConstantInt(ret));
                        instr.remove();
                    } else if (instr instanceof Instr.SItofp && ((Instr.SItofp) instr).getRVal1() instanceof Constant.ConstantInt) {
                        int val = (int) ((Constant.ConstantInt) ((Instr.SItofp) instr).getRVal1()).getConstVal();
                        float ret = (float) val;

                        instr.modifyAllUseThisToUseA(new Constant.ConstantFloat(ret));
                        instr.remove();
                    }
                }
            }
        }
    }
}
