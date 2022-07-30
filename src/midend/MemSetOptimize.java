package midend;

import frontend.semantic.Initial;
import lir.V;
import manage.Manager;
import mir.*;
import mir.type.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import static manage.Manager.MANAGER;

public class MemSetOptimize {

    //只针对全局数组
    //局部数组使用ArraySSA
    private ArrayList<Function> functions;
    private HashSet<Instr> idcPhis;
    private HashMap<GlobalVal.GlobalValue, Initial> globalValues;
    private HashSet<Loop> globalArrayInitLoops = new HashSet<>();
    private HashSet<Loop> localArrayInitLoops = new HashSet<>();
    private HashSet<Instr> canGVN = new HashSet<>();
    HashMap<String, Instr> GvnMap = new HashMap<>();
    HashMap<String, Integer> GvnCnt = new HashMap<>();
    HashSet<Loop> allLoop = new HashSet<>();
    //HashSet<Loop> oneDimLoop = new HashSet<>();

    public MemSetOptimize(ArrayList<Function> functions, HashMap<GlobalVal.GlobalValue, Initial> globalValues) {
        this.functions = functions;
        this.idcPhis = new HashSet<>();
        this.globalValues = globalValues;
    }

    public void Run() {
        init();
        globalArrayFold();
        //initLoopToMemSet();
    }

    private void init() {
        for (Function function: functions) {
            HashSet<Loop> loops = new HashSet<>();
            getLoops(function.getBeginBB().getLoop(), loops);
            for (Loop loop: loops) {
                if (loop.getLoopDepth() == 1) {
                    //
                    idcPhis.clear();
                    if (checkArrayInit(loop)) {
                        if (loop.getInitArray() instanceof GlobalVal) {
                            globalArrayInitLoops.add(loop);
                        } else {
                            localArrayInitLoops.add(loop);
                        }
                    }
                }
            }
        }
    }

    private void globalArrayFold() {
        for (Loop loop: globalArrayInitLoops) {
            Value initValue = loop.getInitValue();
            Value initArray = loop.getInitArray();
            HashSet<Function> userFuncs = new HashSet<>();
            for (Use use = initArray.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
                userFuncs.add(use.getUser().parentBB().getFunction());
            }
            boolean useInOneFunc = userFuncs.size() == 1;
            Function function = null;
            for (Function function1: userFuncs) {
                function = function1;
            }
            //TODO:下面条件限制的非常死
            // 待优化
            if (useInOneFunc && loop.getEnterings().size() == 1 && loop.getEnterings().contains(function.getBeginBB())) {
                canGVN.clear();
                boolean ret = check(initArray);
                if (ret) {
                    //globalArrayGVN(initArray, function);
                    ArrayGVNGCM arrayGVNGCM = new ArrayGVNGCM(function, canGVN);
                    arrayGVNGCM.Run();
                }
            }
        }
    }

    private void initLoopToMemSet() {
        if (functions.size() > 1) {
            return;
        }
        Function function = functions.get(0);
        for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
            allLoop.add(bb.getLoop());
        }
        for (Loop loop: allLoop) {
            if (loop.getChildrenLoops().size() == 0) {
                idcPhis.clear();
                checkArrayInit(loop);
            }
        }
//        //TODO:当所有的循环均为简单循环,且idcEnd均一样,且array的store仅在循环内发生,
//        //      add:循环内数组下标为迭代变量,高维数组需要很多限制
//        //fixme:当前只考虑一维数组
//        Value end = null;
//        for (Loop loop: allLoop) {
//            if (!loop.isSimpleLoop()) {
//                return;
//            }
//        }

        HashSet<BasicBlock> removes = new HashSet<>();
        for (Loop loop: allLoop) {
            if (!loop.isSimpleLoop()) {
                continue;
            }
            if (!loop.isArrayInit()) {
                continue;
            }
            Value value = loop.getInitValue();
            Value array = loop.getInitArray();
            if (!(value instanceof Constant && ((int) ((Constant) value).getConstVal()) == 0)) {
                continue;
            }
            if (((Type.PointerType) array.getType()).getInnerType() instanceof Type.ArrayType &&
                    ((Type.ArrayType) ((Type.PointerType) array.getType()).getInnerType()).getDimSize() == 1 &&
                    loop.getEnterings().size() == 1) {
                HashSet<BasicBlock> enterings = loop.getEnterings();
                BasicBlock entering = null;
                for (BasicBlock bb: enterings) {
                    entering = bb;
                }
                if (!(loop.getIdcInit() instanceof Constant.ConstantInt)) {
                    continue;
                }
                if (!(((int) ((Constant) loop.getIdcInit()).getConstVal()) == 0)) {
                    continue;
                }
                if (!((Instr.Icmp) loop.getIdcCmp()).getOp().equals(Instr.Icmp.Op.SLT)) {
                    continue;
                }
                if (!(loop.getIdcStep() instanceof Constant.ConstantInt)) {
                    continue;
                }
                if (!(((int) ((Constant) loop.getIdcStep()).getConstVal()) == 1)) {
                    continue;
                }
                boolean tag = true;
                for (Instr instr: loop.getExtras()) {
                    for (Use use = instr.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
                        if (!use.getUser().parentBB().getLoop().equals(loop)) {
                            tag = false;
                            break;
                        }
                    }
                }
                if (!tag) {
                    continue;
                }
                Type type = ((Type.ArrayType) ((Type.PointerType) array.getType()).getInnerType()).getBaseType();
                ArrayList<Value> params = new ArrayList<>();
                ArrayList<Value> indexs = new ArrayList<>();
                indexs.add(new Constant.ConstantInt(0));
                indexs.add(new Constant.ConstantInt(0));
                //只考虑从下标0开始,只考虑整数下标
                //fixme:计算长度,而不采用数组总长度
                Instr enteringBr = entering.getEndInstr();
                Instr.GetElementPtr gep = new Instr.GetElementPtr(type, array, indexs, entering);
                Instr.Alu offset = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.MUL, loop.getIdcEnd(), new Constant.ConstantInt(4), entering);
                params.add(gep);
                params.add(new Constant.ConstantInt(0));
                params.add(offset);
                Instr.Call memset = new Instr.Call(Manager.ExternFunction.MEM_SET, params, entering);
                enteringBr.insertBefore(gep);
                enteringBr.insertBefore(offset);
                enteringBr.insertBefore(memset);
                //fixme:直接采用数组总长度
//                Instr enteringBr = entering.getEndInstr();
//                Instr.GetElementPtr gep = new Instr.GetElementPtr(type, array, indexs, entering);
//                //Instr.Alu offset = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.MUL, loop.getIdcEnd(), new Constant.ConstantInt(4), entering);
//                params.add(gep);
//                params.add(new Constant.ConstantInt(0));
//                params.add(new Constant.ConstantInt(((Type.ArrayType) ((Type.PointerType) array.getType()).getInnerType()).getSize() * 4));
//                Instr.Call memset = new Instr.Call(Manager.ExternFunction.MEM_SET, params, entering);
//                enteringBr.insertBefore(gep);
//                //enteringBr.insertBefore(offset);
//                enteringBr.insertBefore(memset);


                HashSet<BasicBlock> exits = loop.getExits();
                BasicBlock exit = null;
                for (BasicBlock bb: exits) {
                    exit = bb;
                }
                HashSet<BasicBlock> exitings = loop.getExitings();
                BasicBlock exiting = null;
                for (BasicBlock bb: exitings) {
                    exiting = bb;
                }
                entering.modifyBrAToB(loop.getHeader(), exit);
                entering.modifySuc(loop.getHeader(), exit);
                exit.modifyPre(exiting, entering);

                for (BasicBlock bb: loop.getNowLevelBB()) {
                    removes.add(bb);
                }
            }
        }
        for (BasicBlock bb: removes) {
            for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                instr.remove();
            }
            bb.remove();
        }
    }

//    private void initLoopToMemSet() {
//
//    }

    private boolean check(Value array) {
        for (Use use = array.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
            if (use.getUser() instanceof Instr.Store && !use.getUser().isArrayInit()) {
                return false;
            }
            if (use.getUser() instanceof Instr.GetElementPtr) {
                boolean ret = check(use.getUser());
                if (!ret) {
                    return false;
                }
            }
            if (use.getUser() instanceof Instr.Load) {
                canGVN.add(use.getUser());
            }
        }
        return true;
    }

//    private void globalArrayGVN(Value array, Function function) {
//        BasicBlock bb = function.getBeginBB();
//        RPOSearch(bb);
//    }
//
//    private void RPOSearch(BasicBlock bb) {
//        HashSet<Instr> loads  = new HashSet<>();
//        Instr load = bb.getBeginInstr();
//        while (load.getNext() != null) {
//            if (canGVN.contains(load)) {
//                if (!addLoadToGVN(load)) {
//                    loads.add(load);
//                }
//            }
//            load = (Instr) load.getNext();
//        }
//
//        for (BasicBlock next: bb.getIdoms()) {
//            RPOSearch(next);
//        }
//
//        for (Instr temp: loads) {
//            removeLoadFromGVN(temp);
//        }
//    }
//
//    private void add(String str, Instr instr) {
//        if (!GvnCnt.containsKey(str)) {
//            GvnCnt.put(str, 1);
//        } else {
//            GvnCnt.put(str, GvnCnt.get(str) + 1);
//        }
//        if (!GvnMap.containsKey(str)) {
//            GvnMap.put(str, instr);
//        }
//    }
//
//    private void remove(String str) {
//        GvnCnt.put(str, GvnCnt.get(str) - 1);
//        if (GvnCnt.get(str) == 0) {
//            GvnMap.remove(str);
//        }
//    }
//
//    private boolean addLoadToGVN(Instr load) {
//        //进行替换
//        assert load instanceof Instr.Load;
//        String hash = ((Instr.Load) load).getPointer().getName();
//        if (GvnMap.containsKey(hash)) {
//            load.modifyAllUseThisToUseA(GvnMap.get(hash));
//            load.remove();
//            return true;
//        }
//        add(hash, load);
//        return false;
//    }
//
//    private void removeLoadFromGVN(Instr load) {
//        assert load instanceof Instr.Load;
//        String hash = ((Instr.Load) load).getPointer().getName();
//        remove(hash);
//    }

    private void getLoops(Loop loop, HashSet<Loop> loops) {
        loops.add(loop);
        for (Loop next: loop.getChildrenLoops()) {
            getLoops(next, loops);
        }
    }

    private boolean checkArrayInit(Loop loop) {
        if (!loop.isSimpleLoop() || !loop.isIdcSet()) {
            return false;
        }
        if (loop.hasChildLoop() && loop.getChildrenLoops().size() != 1) {
            //只有一个内部循环
            return false;
        }
        HashSet<Instr> instrs = new HashSet<>();
        instrs.add(loop.getHeader().getEndInstr());
        instrs.add((Instr) loop.getIdcPHI());
        instrs.add((Instr) loop.getIdcAlu());
        instrs.add((Instr) loop.getIdcCmp());

        idcPhis.add((Instr) loop.getIdcPHI());

        if (!loop.hasChildLoop()) {
            //没有子循环
            HashSet<Instr> extras = new HashSet<>();
            for (BasicBlock bb: loop.getNowLevelBB()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (!(instr instanceof Instr.Jump) && !instrs.contains(instr)) {
                        extras.add(instr);
                    }
                }
            }
            int getint_cnt = 0, gep_cnt = 0, store_cnt = 0;
            Instr.Call getint = null;
            Instr.GetElementPtr gep = null;
            Instr.Store store = null;
            for (Instr instr: extras) {
                if (instr instanceof Instr.Call && ((Instr.Call) instr).getFunc().getName().equals("getint")) {
                    getint_cnt++;
                    getint = (Instr.Call) instr;
                } else if (instr instanceof Instr.GetElementPtr) {
                    gep_cnt++;
                    gep = (Instr.GetElementPtr) instr;
                } else if (instr instanceof Instr.Store) {
                    store_cnt++;
                    store = (Instr.Store) instr;
                } else {
                    return false;
                }
            }
            if (gep_cnt != 1) {
                return false;
            }
            ArrayList<Value> indexs = gep.getIdxList();
            //TODO:下面条件限制的非常死,对于函数调用中传参数组的初始化不友好
            // 待优化
            if (idcPhis.size() + 1 != indexs.size()) {
                return false;
            }
            if (indexs.get(0) instanceof Constant && ((int) ((Constant) indexs.get(0)).getConstVal()) == 0) {
                for (Value index: indexs.subList(1, indexs.size())) {
                    if (!idcPhis.contains(index)) {
                        return false;
                    }
                }
            } else {
                return false;
            }
            if (store_cnt == 1) {
                Value ptr = store.getPointer();
                if (!ptr.equals(gep)) {
                    return false;
                }
                Value initArray = gep.getPtr();
                Value initValue = store.getValue();
                if (getint_cnt == 1) {
                    if (!initValue.equals(getint)) {
                        return false;
                    }
                }
                gep.setArrayInit(true);
                store.setArrayInit(true);
                loop.setArrayInitInfo(1, initArray, initValue, extras);
                return true;
            } else {
                return false;
            }

        } else {
            //有子循环且子循环数量为1
            for (BasicBlock bb: loop.getNowLevelBB()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (!(instr instanceof Instr.Jump) && !instrs.contains(instr)) {
                        return false;
                    }
                }
            }
            Loop next = null;
            for (Loop temp: loop.getChildrenLoops()) {
                next = temp;
            }
            assert next != null;
            boolean ret = checkArrayInit(next);
            if (ret) {
                loop.setArrayInitInfo(next.getArrayInitDims() + 1, next.getInitArray(), next.getInitValue(), next.getExtras());
                return true;
            }
            return false;
        }
    }


}
