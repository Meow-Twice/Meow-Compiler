package midend;

import mir.*;

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
                    if (instr instanceof Instr.GetElementPtr) {
                        Value arrayPtr = ((Instr.GetElementPtr) instr).getPtr();
                        assert arrayPtr instanceof Instr.Alloc || arrayPtr instanceof GlobalVal;
                    }
                    if (instr instanceof Instr.Alloc) {

                    }
                }
            }
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
        HashSet<Instr> adds = new HashSet<>();
        HashSet<Instr> removes = new HashSet<>();
        Instr instr = bb.getBeginInstr();
        while (instr.getNext() != null) {
            if (instr instanceof Instr.Load && !(((Instr.Load) instr).getPointer() instanceof GlobalVal)) {
                if (!addLoadToGVN(instr)) {
                    adds.add(instr);
                }
            } else if (instr instanceof Instr.Store && !(((Instr.Load) instr).getPointer() instanceof GlobalVal)) {

            } else if (instr instanceof Instr.Call) {
                //判断函数的传参有没有数组
                if (((Instr.Call) instr).getFunc().isExternal) {

                } else if (((Instr.Call) instr).getFunc().getName().equals("memset")) {

                }
            }
            instr = (Instr) instr.getNext();
        }

        for (BasicBlock next : bb.getIdoms()) {
            RPOSearch(next);
        }

        for (Instr temp : adds) {
            removeLoadFromGVN(temp);
        }
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
            load.remove();
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
