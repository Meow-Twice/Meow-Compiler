package midend;

import frontend.semantic.Initial;
import mir.BasicBlock;
import mir.Function;
import mir.GlobalVal;
import mir.Instr;
import mir.type.Type;

import java.util.ArrayList;
import java.util.HashMap;

public class ArrayLift {

    private ArrayList<Function> functions;
    private HashMap<GlobalVal.GlobalValue, Initial> globalValues;
    private static final int length_low_line = 10;

    public ArrayLift(ArrayList<Function> functions, HashMap<GlobalVal.GlobalValue, Initial> globalValues) {
        this.functions = functions;
        this.globalValues = globalValues;
    }

    public void Run() {
        for (Function function: functions) {
            if (function.getName().equals("main") || function.onlyOneUser()) {
                for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                    for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                        if (instr instanceof Instr.Alloc && instr.parentBB().getLoop().getLoopDepth() == 0) {
                            //TODO:
                            Type type = ((Type.PointerType) instr.getType()).getInnerType();
                            Initial.ZeroInit init = new Initial.ZeroInit(type);
                            GlobalVal.GlobalValue val = new GlobalVal.GlobalValue(type, "lift_" + instr.getNameWithOutPrefix(), init);
                            globalValues.put(val, init);
                            instr.modifyAllUseThisToUseA(val);
                            instr.remove();
                        }
                    }
                }
            }
        }
    }
}
