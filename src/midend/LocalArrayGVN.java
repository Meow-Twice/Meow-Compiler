package midend;

import frontend.semantic.Initial;
import lir.V;
import mir.*;
import mir.type.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class LocalArrayGVN {

    //TODO:GCM更新phi,删除无用phi,添加数组相关分析,
    // 把load,store,get_element_ptr也纳入GCM考虑之中

    private static HashSet<Instr> know;
    private BasicBlock root;


    HashMap<String, Instr> GvnMap = new HashMap<>();
    HashMap<String, Integer> GvnCnt = new HashMap<>();
    private ArrayList<Function> functions;
    //private HashMap<GlobalVal.GlobalValue, Initial> globalValues;


    public LocalArrayGVN(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        Init();
        GVN();
    }

    private void Init() {
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
//                    if (instr instanceof Instr.GetElementPtr) {
//                        Value arrayPtr = ((Instr.GetElementPtr) instr).getPtr();
//                        assert arrayPtr instanceof Instr.Alloc || arrayPtr instanceof GlobalVal;
//                    }
                    if (instr instanceof Instr.Alloc) {
                        initAlloc(instr);
                    }
                }
            }
            for (Value param: function.getParams()) {
                if (param.getType() instanceof Type.PointerType) {
                    initAlloc(param);
                }
            }
        }

//        for (GlobalVal globalVal: globalValues.keySet()) {
//            if (((Type.PointerType) globalVal.getType()).getInnerType() instanceof Type.ArrayType) {
//
//            }
//        }

    }

    private void initAlloc(Value alloc) {
        for (Use use = alloc.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
            Instr user = use.getUser();
            allocDFS(alloc, user);
        }
    }

    private void allocDFS(Value alloc, Instr instr) {
        if (instr instanceof Instr.GetElementPtr) {
            for (Use use = instr.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
                Instr user = use.getUser();
                allocDFS(alloc, user);
            }
        } else if (instr instanceof Instr.Load) {
            ((Instr.Load) instr).setAlloc(alloc);
            if (alloc instanceof Instr.Alloc) {
                ((Instr.Alloc) alloc).addLoad(instr);
            } else if (alloc instanceof Function.Param) {
                ((Function.Param) alloc).addLoad(instr);
            }
        } else if (instr instanceof Instr.Store) {
            ((Instr.Store) instr).setAlloc(alloc);
        } else {
            //assert false;
        }
    }

    private void GVN() {
        GvnMap.clear();
        GvnCnt.clear();
        for (Function function: functions) {
            localArrayGVN(function);
        }
    }

    private void localArrayGVN(Function function) {
        BasicBlock bb = function.getBeginBB();
        RPOSearch(bb);
    }

    private void RPOSearch(BasicBlock bb) {
//        if (bb.getLabel().equals("b16")) {
//            System.err.println("b16");
//        }
        //HashSet<Instr> adds = new HashSet<>();
        //HashSet<Instr> removes = new HashSet<>();
        HashMap<String, Integer> tempGvnCnt = new HashMap<>();
        HashMap<String, Instr> tempGvnMap = new HashMap<>();
        for (String key: GvnCnt.keySet()) {
            tempGvnCnt.put(key, GvnCnt.get(key));
        }
        for (String key: GvnMap.keySet()) {
            tempGvnMap.put(key, GvnMap.get(key));
        }


        Instr instr = bb.getBeginInstr();
        while (instr.getNext() != null) {
            if (instr instanceof Instr.Load && ((Instr.Load) instr).getAlloc() != null) {
//                if (!addLoadToGVN(instr)) {
//                    adds.add(instr);
//                }
                addLoadToGVN(instr);
            } else if (instr instanceof Instr.Store && ((Instr.Store) instr).getAlloc() != null) {
                Value alloc = ((Instr.Store) instr).getAlloc();
                if (alloc instanceof Instr.Alloc) {
                    for (Instr load: ((Instr.Alloc) alloc).getLoads()) {
                        assert load instanceof Instr.Load;
                        removeLoadFromGVN(load);
                    }
                } else if (alloc instanceof Function.Param) {
                    for (Instr load: ((Function.Param) alloc).getLoads()) {
                        assert load instanceof Instr.Load;
                        removeLoadFromGVN(load);
                    }
                } else {
                    assert false;
                }

            } else if (instr instanceof Instr.Call) {
                //判断函数的传参有没有数组
//                if (((Instr.Call) instr).getFunc().isExternal) {
//
//                } else if (((Instr.Call) instr).getFunc().getName().equals("memset")) {
//
//                }
                //TODO:待强化,根据函数传入的指针,判断修改了哪个Alloc/参数
                GvnMap.clear();
                GvnCnt.clear();
            }
            instr = (Instr) instr.getNext();
        }

        for (BasicBlock next : bb.getIdoms()) {
            RPOSearch(next);
        }

//        for (Instr temp : adds) {
//            removeLoadFromGVN(temp);
//        }
        GvnMap.clear();
        GvnCnt.clear();
        GvnMap.putAll(tempGvnMap);
        GvnCnt.putAll(tempGvnCnt);
    }

    private void add(String str, Instr instr) {
        if (!GvnCnt.containsKey(str)) {
            GvnCnt.put(str, 1);
        } else {
            GvnCnt.put(str, GvnCnt.get(str) + 1);
        }
        if (!GvnMap.containsKey(str)) {
            GvnMap.put(str, instr);
        }
    }

    private void remove(String str) {
        if (!GvnCnt.containsKey(str)) {
            return;
        }
        GvnCnt.put(str, GvnCnt.get(str) - 1);
        if (GvnCnt.get(str) == 0) {
            GvnMap.remove(str);
        }
    }

    private boolean addLoadToGVN(Instr load) {
        //进行替换
        assert load instanceof Instr.Load;
        String hash = ((Instr.Load) load).getPointer().getName();
        if (GvnMap.containsKey(hash)) {
            load.modifyAllUseThisToUseA(GvnMap.get(hash));
            //load.remove();
            return true;
        }
        add(hash, load);
        return false;
    }

    private void removeLoadFromGVN(Instr load) {
        assert load instanceof Instr.Load;
        String hash = ((Instr.Load) load).getPointer().getName();
        remove(hash);
    }
}
