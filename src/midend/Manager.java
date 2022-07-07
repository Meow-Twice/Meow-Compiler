package midend;

import frontend.semantic.Initial;
import frontend.semantic.symbol.Symbol;
import mir.Function;
import mir.Value;
import mir.type.Type;
import util.FileDealer;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.*;

import static mir.Function.Param.wrapParam;
import static mir.type.Type.BasicType.F32_TYPE;
import static mir.type.Type.BasicType.I32_TYPE;

/**
 * LLVM IR
 */
public class Manager {
    public static final Manager MANAGER = new Manager();
    private static final String MAIN_FUNCTION = "main";
    public static boolean external = false;
    public final Map<Value, Initial> globals = new HashMap<>();
    private final Map<String, Function> functions = new HashMap<>(); // 函数定义

    public ArrayList<Function> getFunctionList() {
        ArrayList<Function> list = new ArrayList<>();
        for (Function function : functions.values()) {
            if (function.hasBody()) {
                list.add(function);
            }
        }
        return list;
    }

    public static class ExternFunction {
        public static final Function GET_INT = new Function("getint", new ArrayList<>(), I32_TYPE);
        public static final Function GET_CH = new Function("getch", new ArrayList<>(), I32_TYPE);
        public static final Function GET_FLOAT = new Function("getfloat", new ArrayList<>(), F32_TYPE);
        public static final Function GET_ARR = new Function("getarray", wrapParam(new Type.PointerType(I32_TYPE)), I32_TYPE);
        public static final Function GET_FARR = new Function("getfarray", wrapParam(new Type.PointerType(F32_TYPE)), I32_TYPE);
        public static final Function PUT_INT = new Function("putint", wrapParam(I32_TYPE), Type.VoidType.getVoidType());
        public static final Function PUT_CH = new Function("putch", wrapParam(I32_TYPE), Type.VoidType.getVoidType());
        public static final Function PUT_FLOAT = new Function("putfloat", wrapParam(F32_TYPE), Type.VoidType.getVoidType());
        public static final Function PUT_ARR = new Function("putarray", wrapParam(I32_TYPE, new Type.PointerType(I32_TYPE)), Type.VoidType.getVoidType());
        public static final Function PUT_FARR = new Function("putfarray", wrapParam(I32_TYPE, new Type.PointerType(F32_TYPE)), Type.VoidType.getVoidType());
        public static final Function MEM_SET = new Function("memset", wrapParam(new Type.PointerType(I32_TYPE), I32_TYPE, I32_TYPE), Type.VoidType.getVoidType());
        public static final Function START_TIME = new Function("starttime", wrapParam(I32_TYPE), Type.VoidType.getVoidType());
        public static final Function STOP_TIME = new Function("stoptime", wrapParam(I32_TYPE), Type.VoidType.getVoidType());
    }

    private Manager() {
        external = true;
        addFunction(ExternFunction.GET_INT);
        addFunction(ExternFunction.GET_CH);
        addFunction(ExternFunction.GET_FLOAT);
        addFunction(ExternFunction.GET_ARR);
        addFunction(ExternFunction.GET_FARR);
        addFunction(ExternFunction.PUT_INT);
        addFunction(ExternFunction.PUT_CH);
        addFunction(ExternFunction.PUT_FLOAT);
        addFunction(ExternFunction.PUT_ARR);
        addFunction(ExternFunction.PUT_FARR);
        addFunction(ExternFunction.MEM_SET);
        addFunction(ExternFunction.START_TIME);
        ExternFunction.START_TIME.isTimeFunc = true;
        addFunction(ExternFunction.STOP_TIME);
        ExternFunction.STOP_TIME.isTimeFunc = true;
        external = false;
    }

    public void addGlobal(Symbol symbol) {
        assert symbol.getInitial() != null;
        globals.putIfAbsent(symbol.getValue(), symbol.getInitial());
    }

    public void addFunction(Function function) {
        functions.putIfAbsent(function.getName(), function);
    }

    public boolean hasMainFunction() {
        return functions.containsKey(MAIN_FUNCTION);
    }

    static int outputLLVMCnt = 0;

    public void outputLLVM() throws FileNotFoundException {
        outputLLVM("llvmOf-" + outputLLVMCnt++);
    }

    public void outputLLVM(String llvmFilename) throws FileNotFoundException {
        output(new FileOutputStream(llvmFilename + ".ll"));
    }

    public void output(OutputStream out) {
        // 全局变量
        for (Map.Entry<Value, Initial> entry : globals.entrySet()) {
            FileDealer.addOutputString(entry.getKey().getName() + " = dso_local global " + entry.getValue());
        }
        // 函数声明
        for (Function function : functions.values()) {
            if (!function.hasBody()) {
                FileDealer.addOutputString(function.getDeclare());
            }
        }
        // 函数定义
        for (Function function : functions.values()) {
            if (function.hasBody()) {
                FileDealer.addOutputString(function.getDefinition());
            }
        }
        FileDealer.outputStringList(out);
    }

    public Map<Value, Initial> getGlobals() {
        return this.globals;
    }


    public Map<String, Function> getFunctions() {
        return this.functions;
    }

}
