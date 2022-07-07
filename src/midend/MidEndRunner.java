package midend;

import frontend.semantic.Initial;
import frontend.syntax.Ast;
import mir.Function;
import mir.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MidEndRunner {

    //TODO:另一种实现方法 functions定为static 提供init方法
    public ArrayList<Function> functions;
    private HashMap<Value, Initial> globalValues = (HashMap<Value, Initial>) Manager.MANAGER.getGlobals();

    public MidEndRunner(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        MakeDFG makeDFG = new MakeDFG(functions);
        makeDFG.Run();

        Mem2Reg mem2Reg = new Mem2Reg(functions);
        mem2Reg.Run();

        GlobalValueLocalize globalValueLocalize = new GlobalValueLocalize(functions, globalValues);
        globalValueLocalize.Run();

        DeadCodeDelete deadCodeDelete = new DeadCodeDelete(functions);
        deadCodeDelete.Run();

        InstrComb instrComb = new InstrComb(functions);
        instrComb.Run();

        GVNAndGCM gvnAndGCM = new GVNAndGCM(functions);
        gvnAndGCM.Run();

//        RemovePhi removePhi = new RemovePhi(functions);
//        removePhi.Run();
    }

}
