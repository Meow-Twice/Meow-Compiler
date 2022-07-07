package backend;

import frontend.semantic.Initial;
import lir.Machine;
import manage.Manager;
import mir.BasicBlock;
import mir.Function;
import mir.GlobalVal;
import mir.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashMap;

public class CodeGen {

    private static Machine.Function curMachineFunc;
    private static Function curFunc;
    private HashMap<String, Function> midFuncMap;
    public HashMap<Value, Machine.Operand> value2opd;
    public HashMap<Value, Machine.Operand> opd2value;
    public ArrayList<Machine.Function> mcFuncList;
    public HashMap<Function, Machine.Function> func2mcFunc;
    public HashMap<BasicBlock, Machine.Block> bb2mcBB;
    private HashMap<GlobalVal.GlobalValue, Initial> globalMap;

    public CodeGen() {
        curFunc = null;
        curMachineFunc = null;
        midFuncMap = Manager.MANAGER.getFunctions();
        globalMap = Manager.MANAGER.globals;
        value2opd = new HashMap<>();
        opd2value = new HashMap<>();
        mcFuncList = new ArrayList<>();
        func2mcFunc = new HashMap<>();
        bb2mcBB = new HashMap<>();
    }

    public void gen() {
        genGlobal();
        // TODO
    }

    public void genGlobal(){
        // for(GlobalVal.GlobalValue glob: )
    }

    boolean immCanCode(int imm) {
        int encoding = imm;
        for (int i = 0; i < 32; i += 2) {
            if ((encoding & ~((-1) >>> 24)) != 0) {
                return true;
            }
            encoding = (encoding << 2) | (encoding >> 30);
        }
        return false;
    }
}
