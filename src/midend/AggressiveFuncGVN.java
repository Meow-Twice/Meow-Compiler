package midend;

import frontend.semantic.Initial;
import mir.*;
import mir.type.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class AggressiveFuncGVN {

    //激进的GVN认为没有自定义函数调用的函数都可以GVN,
    // 即使它的传参有数组
    private ArrayList<Function> functions;
    private HashSet<Function> canGVN = new HashSet<>();
    private HashMap<Function, HashSet<Integer>> use = new HashMap<>();
    private HashMap<Function, HashSet<Integer>> def = new HashMap<>();
    HashMap<String, Instr> GvnMap = new HashMap<>();
    HashMap<String, Integer> GvnCnt = new HashMap<>();
    private HashMap<GlobalVal.GlobalValue, HashSet<Instr>> defs = new HashMap<>();
    private HashMap<GlobalVal.GlobalValue, HashSet<Instr>> loads = new HashMap<>();
    private HashMap<GlobalVal.GlobalValue, Initial> globalValues;

    private HashMap<Value, HashSet<Instr>> callMap = new HashMap<>();
    private HashSet<Instr> removes = new HashSet<>();




    public AggressiveFuncGVN(ArrayList<Function> functions, HashMap<GlobalVal.GlobalValue, Initial> globalValues) {
        this.functions = functions;
        this.globalValues = globalValues;
    }

    public void Run() {
        for (Function function: functions) {
            use.put(function, new HashSet<>());
            def.put(function, new HashSet<>());
        }
        for (Value value: globalValues.keySet()) {
            callMap.put(value, new HashSet<>());
        }
        for (Function function: functions) {
            for (Function.Param param: function.getParams()) {
                callMap.put(param, new HashSet<>());
            }
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (instr instanceof Instr.Alloc) {
                        callMap.put(instr, new HashSet<>());
                    }
                }
            }
        }
        init();
        initCallMap();
        GVN();
        for (Instr instr: removes) {
            instr.remove();
        }
    }

    private void init() {
        //clear
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

        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
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

        for (GlobalVal.GlobalValue val: globalValues.keySet()) {
            if (((Type.PointerType) val.getType()).getInnerType() instanceof Type.ArrayType) {
                for (Use use = val.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
                    globalArrayDFS(val, use.getUser());
                }
            }
        }



        for (Function function: functions) {
           if (check(function)) {
               canGVN.add(function);
           }
//           for (Function.Param param: function.getParams()) {
//               if (param.getType().isPointerType()) {
//                   int index = function.getParams().indexOf(param);
//
//               }
//           }
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (instr instanceof Instr.Store) {
                        Value val = ((Instr.Store) instr).getPointer();
                        while (val instanceof Instr.GetElementPtr) {
                            val = ((Instr.GetElementPtr) val).getPtr();
                        }
                        if (val instanceof Function.Param) {
                            def.get(function).add(function.getParams().indexOf(val));
                        }
                    } else if (instr instanceof Instr.Load) {
                        Value val = ((Instr.Load) instr).getPointer();
                        while (val instanceof Instr.GetElementPtr) {
                            val = ((Instr.GetElementPtr) val).getPtr();
                        }
                        if (val instanceof Function.Param) {
                            use.get(function).add(function.getParams().indexOf(val));
                        }
                    }
                }
            }
        }

        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (instr instanceof Instr.Call) {
                        Function callFunc = ((Instr.Call) instr).getFunc();
                        for (Value val: ((Instr.Call) instr).getParamList()) {
                            if (val instanceof Function.Param) {
                                int thisFuncIndex = function.getParams().indexOf(val);
                                int callFuncIndex = ((Instr.Call) instr).getParamList().indexOf(val);

                                if (use.get(callFunc).contains(callFuncIndex)) {
                                    use.get(function).add(thisFuncIndex);
                                }
                                if (def.get(callFunc).contains(callFuncIndex)) {
                                    def.get(function).add(thisFuncIndex);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void initCallMap() {
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (instr instanceof Instr.Call) {
                        for (Value val: ((Instr.Call) instr).getParamList()) {
                            if (val.getType().isPointerType()) {
                                while (val instanceof Instr.GetElementPtr) {
                                    val = ((Instr.GetElementPtr) val).getPtr();
                                }
                                if (!callMap.containsKey(val)) {
                                    callMap.put(val, new HashSet<>());
                                }
                                callMap.get(val).add(instr);
                            }
                        }
                    }
                }
            }
        }
    }

    private void globalArrayDFS(GlobalVal.GlobalValue array, Instr instr) {
        if (instr instanceof Instr.GetElementPtr) {
            for (Use use = instr.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
                Instr user = use.getUser();
                globalArrayDFS(array, user);
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


    private boolean check(Function function) {
//        for (Function.Param param: function.getParams()) {
//            if (param.getType().isPointerType()) {
//                return false;
//            }
//        }
        for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
            for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                if (instr instanceof Instr.Call) {
                    if (!((Instr.Call) instr).getFunc().equals(function)) {
                        return false;
                    }
                    //return false;
                }
                for (Value value: instr.getUseValueList()) {
                    if (value instanceof GlobalVal.GlobalValue) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void GVN() {
        for (Function function: functions) {
            BasicBlock bb = function.getBeginBB();
            RPOSearch(bb);
        }
    }

    private void RPOSearch(BasicBlock bb) {
//        if (bb.getLabel().equals("b30")) {
//            System.err.println("30");
//        }
        HashMap<String, Integer> tempGvnCnt = new HashMap<>();
        HashMap<String, Instr> tempGvnMap = new HashMap<>();
//        for (String key: GvnCnt.keySet()) {
//            tempGvnCnt.put(key, GvnCnt.get(key));
//        }
//        for (String key: GvnMap.keySet()) {
//            tempGvnMap.put(key, GvnMap.get(key));
//        }
        tempGvnCnt.putAll(GvnCnt);
        tempGvnMap.putAll(GvnMap);

        for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
            if (instr instanceof Instr.Call && ((Instr.Call) instr).getFunc().isExternal) {
                if (((Instr.Call) instr).getFunc().getName().equals("memset")) {
                    Value array = ((Instr.Call) instr).getParamList().get(0);
                    while (array instanceof Instr.GetElementPtr) {
                        array = ((Instr.GetElementPtr) array).getPtr();
                    }
                    assert callMap.containsKey(array);
                    for (Instr instr1: callMap.get(array)) {
                        removeCallFromGVN(instr1);
                    }
                } else {
                    return;
                }
            }

            if (instr instanceof Instr.Call && !((Instr.Call) instr).getFunc().isExternal) {
                if (!canGVN.contains(((Instr.Call) instr).getFunc())) {
                    continue;
                }
                addCallToGVN(instr);
                Function func = ((Instr.Call) instr).getFunc();
                for (Integer index: def.get(func)) {
                    Value array = ((Instr.Call) instr).getParamList().get(index);
                    for (Instr instr1: callMap.get(array)) {
                        removeCallFromGVN(instr1);
                    }
                }
            } else if (instr instanceof Instr.Store) {
                if (!((Type.PointerType) ((Instr.Store) instr).getPointer().getType()).getInnerType().isBasicType()) {
                    Value array = ((Instr.Store) instr).getAlloc();
                    for (Instr instr1 : callMap.get(array)) {
                        removeCallFromGVN(instr1);
                    }
                }
            }
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

    private boolean addCallToGVN(Instr call) {
        //进行替换
        assert call instanceof Instr.Call;
        String hash = ((Instr.Call) call).getFunc().getName() + "(";
        for (Value value: ((Instr.Call) call).getParamList()) {
            hash += value.getName() + ", ";
        }
        hash += ")";
        if (GvnMap.containsKey(hash)) {
            call.modifyAllUseThisToUseA(GvnMap.get(hash));
            //call.remove();
            removes.add(call);
            return true;
        }
        add(hash, call);
        return false;
    }

    private void removeCallFromGVN(Instr call) {
        assert call instanceof Instr.Call;
        String hash = ((Instr.Call) call).getFunc().getName() + "(";
        for (Value value: ((Instr.Call) call).getParamList()) {
            hash += value.getName() + ", ";
        }
        hash += ")";
        remove(hash);
    }
}
