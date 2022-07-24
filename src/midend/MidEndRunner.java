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
    private static boolean O2 = true;

    public MidEndRunner(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        MakeDFG makeDFG = new MakeDFG(functions);
        makeDFG.Run();
        if (!O2) {

            Mem2Reg mem2Reg = new Mem2Reg(functions);
            mem2Reg.Run();

            return;
        }

        GlobalValueLocalize globalValueLocalize = new GlobalValueLocalize(functions, globalValues);
        globalValueLocalize.Run();

        FuncInline funcInline = new FuncInline(functions);
        funcInline.Run();

        reMakeCFGAndLoopInfo();

        GlobalValueLocalize globalValueLocalize_1 = new GlobalValueLocalize(functions, globalValues);
        globalValueLocalize_1.Run();

        Mem2Reg mem2Reg = new Mem2Reg(functions);
        mem2Reg.Run();

        Pass();


        //BrOptimize();
        loopOptimize();


        //TODO:删除冗余phi,分支优化(删除无用的br/jump等),等等
        BranchOptimize branchOptimize = new BranchOptimize(functions);
        branchOptimize.Run();

        //前驱后继关系已经维护
        //拆分MakeCFG
        reMakeCFGAndLoopInfo();

        Pass();

        MathOptimize mathOptimize = new MathOptimize(functions);
        mathOptimize.Run();

        // RemovePhi removePhi = new RemovePhi(functions);
        // removePhi.Run();
    }

    //死代码删除 指令融合 GVN/GCM
    private void Pass() {
        DeadCodeDelete deadCodeDelete = new DeadCodeDelete(functions);
        deadCodeDelete.Run();

        InstrComb instrComb = new InstrComb(functions);
        instrComb.Run();

        GVNAndGCM gvnAndGCM = new GVNAndGCM(functions);
        gvnAndGCM.Run();
    }

    //重建数据流, 简化PHI, 重建循环关系
    private void reMakeCFGAndLoopInfo() {
        MakeDFG makeDFG = new MakeDFG(functions);
        makeDFG.Run();

        LoopInfo loopInfo = new LoopInfo(functions);
        loopInfo.Run();

    }

    //循环优化
    private void loopOptimize() {
        //outputLLVM();

        LoopInfo loopInfo = new LoopInfo(functions);
        loopInfo.Run();

        LCSSA lcssa = new LCSSA(functions);
        lcssa.Run();

        //outputLLVM();

        BranchLift branchLift = new BranchLift(functions);
        branchLift.Run();
//
//        outputLLVM();

        reMakeCFGAndLoopInfo();

        Pass();

//        LoopInfo loopInfo1 = new LoopInfo(functions);
//        loopInfo1.Run();

        // TODO:获取迭代变量idcVar的相关信息
        LoopIdcVarInfo loopIdcVarInfo = new LoopIdcVarInfo(functions);
        loopIdcVarInfo.Run();
//
////        // TODO:循环展开
        LoopUnRoll loopUnRoll = new LoopUnRoll(functions);
        loopUnRoll.Run();

        reMakeCFGAndLoopInfo();

        Pass();

        // TODO:循环融合

        // TODO:强度削弱
        // a = b * i + c; i = i + d
        // a = b * init + c; a = a + bd; i = i + d
        //
    }

    private void BrOptimize() {
        BranchOptimize branchOptimize = new BranchOptimize(functions);
        branchOptimize.Run();

        //前驱后继关系已经维护
        //拆分MakeCFG
        reMakeCFGAndLoopInfo();

        Pass();
    }

    private void outputLLVM() {
        try {
            Manager.MANAGER.outputLLVM();
        } catch (Exception e) {

        }
    }

}
