package midend;

import frontend.semantic.Initial;
import manage.Manager;
import mir.Function;
import mir.GlobalVal;
import mir.Value;

import java.util.ArrayList;
import java.util.HashMap;

public class MidEndRunner {

    //TODO:另一种实现方法 functions定为static 提供init方法
    public ArrayList<Function> functions;
    private HashMap<GlobalVal.GlobalValue, Initial> globalValues = Manager.MANAGER.getGlobals();

    public MidEndRunner(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        MakeDFG makeDFG = new MakeDFG(functions);
        makeDFG.Run();

        GlobalValueLocalize globalValueLocalize = new GlobalValueLocalize(functions, globalValues);
        globalValueLocalize.Run();



        Mem2Reg mem2Reg = new Mem2Reg(functions);
        mem2Reg.Run();


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
