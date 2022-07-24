package midend;

import frontend.semantic.Initial;
import mir.*;
import mir.type.Type;

import java.util.*;

public class GlobalValueLocalize {
    private HashMap<GlobalVal.GlobalValue, Initial> globalValues;
    private ArrayList<Function> functions;

    private HashSet<Value> removedGlobal;

    public GlobalValueLocalize(ArrayList<Function> functions, HashMap<GlobalVal.GlobalValue, Initial> globalValues) {
        this.functions = functions;
        this.globalValues = globalValues;
        this.removedGlobal = new HashSet<>();
    }

    public void Run() {
        for (Value value: globalValues.keySet()) {
            if (!globalValues.get(value).getType().isInt32Type() && !globalValues.get(value).getType().isFloatType()) {
                continue;
            }
            localizeSingleValue(value);
        }
        for (Value value: removedGlobal) {
            globalValues.remove(value);
            value.remove();
        }
    }

    private void localizeSingleValue(Value value) {
        boolean hasStore = false;
        HashSet<Function> useFunctions = new HashSet<>();
        Use use = value.getBeginUse();
        HashSet<Instr> useInstrs = new HashSet<>();
        while (use.getNext() != null) {
            if (use.getUser() instanceof Instr.Store) {
                hasStore = true;
            }
            useInstrs.add(use.getUser());
            useFunctions.add(use.getUser().parentBB().getFunction());
            use = (Use) use.getNext();
        }


        // 没有被store的全局变量,
        if (!hasStore) {
            for (Instr instr : useInstrs) {
                Constant constant = null;
                Initial.ValueInit initValue = (Initial.ValueInit) globalValues.get(value);
                assert initValue.getValue() instanceof Constant;
                if (initValue.getType().isFloatType()) {
                    constant = new Constant.ConstantFloat((float) ((Constant) initValue.getValue()).getConstVal());
                } else if (globalValues.get(value).getType().isInt32Type()) {
                    constant = new Constant.ConstantInt((int) ((Constant) initValue.getValue()).getConstVal());
                } else {
                    System.err.println("error");
                }
                instr.modifyAllUseThisToUseA(constant);
                instr.remove();
            }
            removedGlobal.add(value);
            return;
        }

        //只在main中调用的
        if (useFunctions.size() == 1) {
            Function function = null;
            for (Function tmp: useFunctions) {
                function = tmp;
            }
            if (!function.getName().equals("main")) {
                return;
            }
            BasicBlock entry = function.getBeginBB();

            Initial.ValueInit initValue = (Initial.ValueInit) globalValues.get(value);
            assert initValue.getValue() instanceof Constant;

            if (initValue.getType().isFloatType()) {
                Instr.Alloc alloc = new Instr.Alloc(Type.BasicType.getF32Type(), entry, true);
                Instr.Store store = new Instr.Store(initValue.getValue(), alloc, entry);
                value.modifyAllUseThisToUseA(alloc);
                entry.insertAtHead(store);
                entry.insertAtHead(alloc);
            } else if (globalValues.get(value).getType().isInt32Type()) {
                Instr.Alloc alloc = new Instr.Alloc(Type.BasicType.getI32Type(), entry, true);
                Instr.Store store = new Instr.Store(initValue.getValue(), alloc, entry);
                value.modifyAllUseThisToUseA(alloc);
                entry.insertAtHead(store);
                entry.insertAtHead(alloc);
            } else {
                System.err.println("error");
            }
            removedGlobal.add(value);
        }

        // 只在一个函数中被load,store
        // 只要有store,就不能直接替换为初始值,
        // 即使一个函数内没有store,但是因为另外的函数存在store,不明函数的调用逻辑,仍不能在没有store 的函数内替换为初始值
        // 即使只在一个函数内被load,store,仍然不能局部化,因为局部化后,每次调用函数对变量的修改是无法保留的
        // 如:
        // int a = 0;
        // int func() {
        //     a = a + 1;
        // }
        //TODO:获取函数被调用的次数,次数为1则可局部化:只在非递归函数的循环深度为一的位置被调用一次?
        //fixme:上述条件是否正确
        //TODO:main不可能被递归调用,纳入考虑
//        if (useFunctions.size() == 1) {
//            Function function = null;
//            for (Function tmp: useFunctions) {
//                function = tmp;
//            }
//            BasicBlock entry = function.getBeginBB();
//
//            Initial.ValueInit initValue = (Initial.ValueInit) globalValues.get(value);
//            assert initValue.getValue() instanceof Constant;
//
//            if (initValue.getType().isFloatType()) {
//                Instr.Alloc alloc = new Instr.Alloc(Type.BasicType.getF32Type(), entry, true);
//                Instr.Store store = new Instr.Store(initValue.getValue(), alloc, entry);
//                value.modifyAllUseThisToUseA(alloc);
//                entry.insertAtHead(store);
//                entry.insertAtHead(alloc);
//            } else if (globalValues.get(value).getType().isInt32Type()) {
//                Instr.Alloc alloc = new Instr.Alloc(Type.BasicType.getI32Type(), entry, true);
//                Instr.Store store = new Instr.Store(initValue.getValue(), alloc, entry);
//                value.modifyAllUseThisToUseA(alloc);
//                entry.insertAtHead(store);
//                entry.insertAtHead(alloc);
//            } else {
//                System.err.println("error");
//            }
//            removedGlobal.add(value);
//        }
    }



}
