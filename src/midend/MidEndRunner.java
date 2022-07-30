package midend;

import frontend.semantic.Initial;
import manage.Manager;
import mir.*;

import java.util.ArrayList;
import java.util.HashMap;

public class MidEndRunner {

    //TODO:另一种实现方法 functions定为static 提供init方法
    public ArrayList<Function> functions;
    private HashMap<GlobalVal.GlobalValue, Initial> globalValues = Manager.MANAGER.getGlobals();
    public static boolean O2 = false;

    public MidEndRunner(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        MakeDFG makeDFG = new MakeDFG(functions);
        makeDFG.Run();
        if (!O2) {
            Mem2Reg mem2Reg = new Mem2Reg(functions);
            mem2Reg.Run();
            Pass();
            Pass();
            BrOptimize();
            BrOptimize();
            return;
        }

        System.err.println("O2 Begin");

        GlobalValueLocalize globalValueLocalize = new GlobalValueLocalize(functions, globalValues);
        globalValueLocalize.Run();

        FuncInline funcInline = new FuncInline(functions);
        funcInline.Run();

        reMakeCFGAndLoopInfo();

        GlobalValueLocalize globalValueLocalize_1 = new GlobalValueLocalize(functions, globalValues);
        globalValueLocalize_1.Run();

        Mem2Reg mem2Reg = new Mem2Reg(functions);
        mem2Reg.Run();

        GepFuse();

        Pass();

        loopOptimize();

        //outputLLVM();

        //outputLLVM();

        MemSetOptimize memSetOptimize = new MemSetOptimize(functions, globalValues);
        memSetOptimize.Run();

        reMakeCFGAndLoopInfo();


        //TODO:删除冗余phi,分支优化(删除无用的br/jump等),等等
        BrOptimize();
        BrOptimize();
        BrOptimize();

        outputLLVM();
        //loopFold


        MathOptimize mathOptimize = new MathOptimize(functions);
        mathOptimize.Run();

//        loopOptimize();
//        BrOptimize();

        Pass();

        GepSplit();

        System.err.println("O2 End");
        //check();
        //
        // RemovePhi removePhi = new RemovePhi(functions);
        // removePhi.Run();
    }

    private void GepFuse() {
        GepFuse gepFuse = new GepFuse(functions);
        gepFuse.Run();

        DeadCodeDelete deadCodeDelete = new DeadCodeDelete(functions, globalValues);
        deadCodeDelete.Run();
    }

    private void GepSplit() {
        GepSplit gepSplit = new GepSplit(functions);
        gepSplit.Run();

        Pass();
    }

    //死代码删除 指令融合 GVN/GCM
    private void Pass() {
        DeadCodeDelete deadCodeDelete_1 = new DeadCodeDelete(functions, globalValues);
        deadCodeDelete_1.Run();

        InstrComb instrComb_1 = new InstrComb(functions);
        instrComb_1.Run();

        ConstFold constFold_1 = new ConstFold(functions, globalValues);
        constFold_1.Run();

        DeadCodeDelete deadCodeDelete_2 = new DeadCodeDelete(functions, globalValues);
        deadCodeDelete_2.Run();

//        BranchOptimize branchOptimize = new BranchOptimize(functions);
//        branchOptimize.Run();
//
//        reMakeCFGAndLoopInfo();

//        DeadCodeDelete deadCodeDelete_3 = new DeadCodeDelete(functions, globalValues);
//        deadCodeDelete_3.Run();
//
//        InstrComb instrComb_2 = new InstrComb(functions);
//        instrComb_2.Run();
//
//        ConstFold constFold_2 = new ConstFold(functions, globalValues);
//        constFold_2.Run();

        GVNAndGCM gvnAndGCM = new GVNAndGCM(functions);
        gvnAndGCM.Run();
    }

    //重建数据流, 简化PHI, 重建循环关系
    private void reMakeCFGAndLoopInfo() {
        MakeDFG makeDFG = new MakeDFG(functions);
        makeDFG.Run();

        LoopInfo loopInfo = new LoopInfo(functions);
        loopInfo.Run();

        LoopIdcVarInfo loopIdcVarInfo = new LoopIdcVarInfo(functions);
        loopIdcVarInfo.Run();

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
        //outputLLVM();

        reMakeCFGAndLoopInfo();

        Pass();

//        LoopInfo loopInfo1 = new LoopInfo(functions);
//        loopInfo1.Run();

        // TODO:获取迭代变量idcVar的相关信息
        LoopIdcVarInfo loopIdcVarInfo = new LoopIdcVarInfo(functions);
        loopIdcVarInfo.Run();
//
////        // TODO:循环展开
        //outputLLVM();

        LoopUnRoll loopUnRoll = new LoopUnRoll(functions);
        loopUnRoll.Run();

        reMakeCFGAndLoopInfo();

        //outputLLVM();

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
//        MakeDFG makeDFG = new MakeDFG(functions);
//        makeDFG.Run();
        reMakeCFGAndLoopInfo();

        Pass();
    }

    public static void outputLLVM() {
        try {
            Manager.MANAGER.outputLLVM();
        } catch (Exception e) {

        }
    }

    private void check() {
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    for (Use use = instr.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
                        Instr user = use.getUser();
                        assert user.getUseValueList().contains(instr);
                    }

                    for (Value value: instr.getUseValueList()) {
                        boolean tag = false;
                        for (Use use1 = value.getBeginUse(); use1.getNext() != null; use1 = (Use) use1.getNext()) {
                            if (use1.getUser().equals(instr)) {
                                tag = true;
                            }
                        }
                        assert tag;
                    }
                }
            }
        }
    }

}
