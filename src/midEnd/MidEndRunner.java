package midEnd;

import ir.Function;

import java.util.ArrayList;

public class MidEndRunner {

    //TODO:另一种实现方法 functions定为static 提供init方法
    public ArrayList<Function> functions;

    public MidEndRunner(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        MakeDFG makeDFG = new MakeDFG(functions);
        makeDFG.Run();

        InsertPhi insertPhi = new InsertPhi(functions);
        insertPhi.Run();
    }

}
