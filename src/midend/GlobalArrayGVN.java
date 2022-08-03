package midend;

import frontend.semantic.Initial;
import manage.Manager;
import mir.*;
import mir.type.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class GlobalArrayGVN {

    private ArrayList<Function> functions;

    private HashMap<GlobalVal.GlobalValue, Initial> globalValues;

    private HashMap<GlobalVal.GlobalValue, HashSet<Instr>> defs = new HashMap<>();
    private HashMap<GlobalVal.GlobalValue, HashSet<Instr>> loads = new HashMap<>();

    HashMap<String, Instr> GvnMap = new HashMap<>();
    HashMap<String, Integer> GvnCnt = new HashMap<>();


    public GlobalArrayGVN(ArrayList<Function> functions, HashMap<GlobalVal.GlobalValue, Initial> globalValues) {
        this.functions = functions;
        this.globalValues = globalValues;
    }

    public void Run() {
        init();
        GVN();
    }

    private void init() {
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (instr instanceof Instr.Alloc) {
                        ((Instr.Alloc) instr).clearLoads();
                    } else if (instr instanceof Instr.Load) {
                        ((Instr.Load) instr).clear();
                    } else if (instr instanceof Instr.Store) {
                        ((Instr.Store) instr).clear();
                    }
                }
            }
            for (Value param: function.getParams()) {
                if (param.getType() instanceof Type.PointerType) {
                    ((Function.Param) param).clearLoads();
                }
            }
        }

        for (GlobalVal.GlobalValue val: globalValues.keySet()) {
            if (((Type.PointerType) val.getType()).getInnerType() instanceof Type.ArrayType) {
                for (Use use = val.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
                    DFS(val, use.getUser());
                }
            }
        }


    }

    private void DFS(GlobalVal.GlobalValue array, Instr instr) {
        if (instr instanceof Instr.GetElementPtr) {
            for (Use use = instr.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
                Instr user = use.getUser();
                DFS(array, user);
            }
        } else if (instr instanceof Instr.Load) {
            ((Instr.Load) instr).setAlloc(array);
            if (!loads.containsKey(array)) {
                loads.put(array, new HashSet<>());
            }
            loads.get(array).add(instr);
        } else if (instr instanceof Instr.Store) {
            ((Instr.Store) instr).setAlloc(array);
        } else {
            //assert false;
        }
    }

    private void GVN() {
        GvnMap.clear();
        GvnCnt.clear();
        for (Function function: functions) {
            globalArrayGVN(function);
        }
    }

    private void globalArrayGVN(Function function) {
        BasicBlock bb = function.getBeginBB();
        RPOSearch(bb);
    }

    private void RPOSearch(BasicBlock bb) {
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
                addLoadToGVN(instr);
            } else if (instr instanceof Instr.Store && ((Instr.Store) instr).getAlloc() != null) {
                Value array = ((Instr.Store) instr).getAlloc();
                assert array instanceof GlobalVal.GlobalValue;
                if (loads.containsKey(array)) {
                    for (Instr load : loads.get(array)) {
                        assert load instanceof Instr.Load;
                        removeLoadFromGVN(load);
                    }
                } else {
                    //这是正常的,因为单一函数中可能没有load全局数组,只是store了它
                    //System.err.println("no_use");
                }

            } else if (instr instanceof Instr.Call) {
                //TODO:待强化,根据函数传入的指针,判断修改了哪个Alloc/参数
                GvnMap.clear();
                GvnCnt.clear();
            }
            instr = (Instr) instr.getNext();
        }

        for (BasicBlock next : bb.getIdoms()) {
            RPOSearch(next);
        }

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
        if (!GvnCnt.containsKey(str) || GvnCnt.get(str) == 0) {
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
