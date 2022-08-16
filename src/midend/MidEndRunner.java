package midend;

import backend.PeepHole;
import frontend.semantic.Initial;
import manage.Manager;
import mir.*;
import util.CenterControl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

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
            reMakeCFGAndLoopInfo();
            LoopInfo loopInfo = new LoopInfo(functions);
            loopInfo.Run();

            LoopIdcVarInfo loopIdcVarInfo = new LoopIdcVarInfo(functions);
            loopIdcVarInfo.Run();

            FuncInfo funcInfo = new FuncInfo(functions);
            funcInfo.Run();
            Pass();
            Pass();
//            BrOptimize();
//            BrOptimize();
            MathOpt();
            return;
        }

        System.err.println("O2 Begin");

        GlobalValueLocalize globalValueLocalize = new GlobalValueLocalize(functions, globalValues);
        globalValueLocalize.Run();

        //TODO:内联,重算数据流,控制流信息并再进行一次局部化
        //FuncInline();

        Mem2Reg();

        //outputLLVM();
        if (CenterControl._OPEN_CONST_TRANS_FOLD) {
            ConstTranFold();
        }

        MathOpt();

        //outputLLVM();
        Pass();

        MathOpt();

        GepFuse();

        //暂定函数内联的位置
        Pass();
        //
        FuncGVN();
        FuncGCM();
        //outputLLVM();
        FuncInline();
        Mem2Reg();
        MathOpt();
        GepFuse();

        //outputLLVM();

        Pass();

        //outputLLVM();

        ArrayGVN();

        MathOpt();
        BrOptimize();

        //outputLLVM();
        loopOptimize();

        ArrayGVN();

        //outputLLVM();
        GlobalArrayGVN();

        MemSetOptimize memSetOptimize = new MemSetOptimize(functions, globalValues);
        memSetOptimize.Run();

        reMakeCFGAndLoopInfo();


        //TODO:删除冗余phi,分支优化(删除无用的br/jump等),等等
        BrOptimize();
        BrOptimize();
        BrOptimize();
        BrOptimize();

        //outputLLVM();
        LoopFold();
        LoopFold();


        MathOpt();
        Pass();
        //outputLLVM();
        LoopStrengthReduction();
        //outputLLVM();




        MathOpt();
        //outputLLVM();
        MathOpt();


//        loopOptimize();
//        BrOptimize();

        Pass();

        //outputLLVM();
        //ArrayGCM();
        LoopInVarCodeLift();
        //outputLLVM();

        //outputLLVM();
        LoopFuse();


        ArrayLift();
        removePhiUseSame();
        Rem2DivMulSub();
        //outputLLVM();
        MarkParallel();
        GepSplit();

        //outputLLVM();

        System.err.println("O2 End");
        //check();
        //
        // RemovePhi removePhi = new RemovePhi(functions);
        // removePhi.Run();
    }

    private void MarkParallel() {
        if (!CenterControl._OPEN_PARALLEL) {
            return;
        }
        MarkParallel markParallel = new MarkParallel(functions);
        markParallel.Run();

        reMakeCFGAndLoopInfo();
    }

    private void ArrayLift() {
        ArrayLift arrayLift = new ArrayLift(functions, globalValues);
        arrayLift.Run();
    }

    private void LoopInVarCodeLift() {
        LoopInVarCodeLift loopInVarCodeLift = new LoopInVarCodeLift(functions, globalValues);
        loopInVarCodeLift.Run();

        Pass();
    }

    private void LoopFuse() {
        LoopFuse loopFuse = new LoopFuse(functions);
        loopFuse.Run();

        GlobalArrayGVN();

        ArrayGVN();

        RemoveUselessStore removeUselessStore = new RemoveUselessStore(functions);
        removeUselessStore.Run();

        MidPeepHole midPeepHole = new MidPeepHole(functions);
        midPeepHole.Run();

        Pass();
    }

    private void Rem2DivMulSub() {
        Rem2DivMulSub rem2DivMulSub = new Rem2DivMulSub(functions);
        rem2DivMulSub.Run();

        outputLLVM();
        Pass();
    }

    private void Mem2Reg() {
        Mem2Reg mem2Reg = new Mem2Reg(functions);
        mem2Reg.Run();
    }

    private void MathOpt() {
        MathOptimize mathOptimize = new MathOptimize(functions);
        mathOptimize.Run();
    }

    private void FuncInline() {
        FuncInline funcInline = new FuncInline(functions);
        funcInline.Run();

        reMakeCFGAndLoopInfo();

        GlobalValueLocalize globalValueLocalize = new GlobalValueLocalize(functions, globalValues);
        globalValueLocalize.Run();
    }

    private void FuncGVN() {
        //outputLLVM();
        AggressiveFuncGVN aggressiveFuncGVN = new AggressiveFuncGVN(functions, globalValues);
        aggressiveFuncGVN.Run();


        Pass();
        //outputLLVM();
    }

    private void FuncGCM() {
        //outputLLVM();
        AggressiveFuncGCM aggressiveFuncGCM = new AggressiveFuncGCM(functions);
        aggressiveFuncGCM.Run();

        Pass();
        //outputLLVM();
    }

    private void ArrayGVN() {
        Pass();

        LocalArrayGVN localArrayGVN = new LocalArrayGVN(functions, "GVN");
        localArrayGVN.Run();

        Pass();
    }

    private void GlobalArrayGVN() {
        Pass();

        GlobalArrayGVN globalArrayGVN = new GlobalArrayGVN(functions, globalValues);
        globalArrayGVN.Run();

        Pass();
    }

    //暂时关闭
    private void ArrayGCM() {
        Pass();

        LocalArrayGVN localArrayGVN = new LocalArrayGVN(functions, "GCM");
        localArrayGVN.Run();

        Pass();
    }

    private void GepFuse() {
        //check_instr();

        GepFuse gepFuse = new GepFuse(functions);
        gepFuse.Run();

        //check_instr();

        DeadCodeDelete deadCodeDelete = new DeadCodeDelete(functions, globalValues);
        deadCodeDelete.Run();
    }

    private void check_instr() {
        for (Function function : functions) {
            HashSet<Instr> instrs = new HashSet<>();
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    instrs.add(instr);
                }
            }
            System.err.println(function.getName());
        }
    }

    private void GepSplit() {
        GepSplit gepSplit = new GepSplit(functions);
        gepSplit.Run();

        Pass();
    }

    private void LoopFold() {
        LoopFold loopFold = new LoopFold(functions);
        loopFold.Run();

        reMakeCFGAndLoopInfo();

        BrOptimize();
    }

    private void LoopStrengthReduction() {
        if (CenterControl._ONLY_FRONTEND) {
            return;
        }
        //outputLLVM();

        LoopStrengthReduction loopStrengthReduction = new LoopStrengthReduction(functions);
        loopStrengthReduction.Run();

        //outputLLVM();

        reMakeCFGAndLoopInfo();

        //outputLLVM();
        Pass();
        //outputLLVM();
    }

    //死代码删除 指令融合 GVN/GCM
    private void Pass() {
        if (CenterControl._OPEN_CONST_TRANS_FOLD) {
            ConstTranFold();
        }

        DeadCodeDelete deadCodeDelete_1 = new DeadCodeDelete(functions, globalValues);
        deadCodeDelete_1.Run();

        InstrComb instrComb_1 = new InstrComb(functions);
        instrComb_1.Run();

        ConstFold constFold_1 = new ConstFold(functions, globalValues);
        constFold_1.Run();

        //outputLLVM();

        DeadCodeDelete deadCodeDelete_2 = new DeadCodeDelete(functions, globalValues);
        deadCodeDelete_2.Run();

        //outputLLVM();

        GVNAndGCM gvnAndGCM = new GVNAndGCM(functions);
        gvnAndGCM.Run();
    }

    private void ConstTranFold() {
        ConstTransFold constTransFold = new ConstTransFold(functions);
        constTransFold.Run();
    }

    //重建数据流, 简化PHI, 重建循环关系
    private void reMakeCFGAndLoopInfo() {
        MakeDFG makeDFG = new MakeDFG(functions);
        makeDFG.Run();

        LoopInfo loopInfo = new LoopInfo(functions);
        loopInfo.Run();

        LoopIdcVarInfo loopIdcVarInfo = new LoopIdcVarInfo(functions);
        loopIdcVarInfo.Run();

        FuncInfo funcInfo = new FuncInfo(functions);
        funcInfo.Run();

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

        //outputLLVM();

        Pass();


        // TODO:获取迭代变量idcVar的相关信息
        LoopIdcVarInfo loopIdcVarInfo = new LoopIdcVarInfo(functions);
        loopIdcVarInfo.Run();
//
        // TODO:循环展开
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

    private void removePhiUseSame() {
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (instr instanceof Instr.Phi) {
                        boolean canRemove = true;
                        Value base = instr.getUseValueList().get(0);
                        for (Value value: instr.getUseValueList()) {
                            if (!base.equals(value)) {
                                canRemove = false;
                                break;
                            }
                        }
                        if (canRemove) {
                            instr.modifyAllUseThisToUseA(base);
                            instr.remove();
                        }
                    }
                }
            }
        }

    }

    private void check() {
        for (Function function : functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    for (Use use = instr.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
                        Instr user = use.getUser();
                        assert user.getUseValueList().contains(instr);
                    }

                    for (Value value : instr.getUseValueList()) {
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
