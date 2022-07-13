package manage;

import frontend.semantic.Initial;
import frontend.semantic.symbol.Symbol;
import lir.MIComment;
import lir.Machine;
import lir.MachineInst;
import mir.Function;
import mir.GlobalVal;
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
    public final HashMap<GlobalVal.GlobalValue, Initial> globals = new HashMap<>();
    private final HashMap<String, Function> functions = new HashMap<>(); // 函数定义

    public int RK = 2;
    public int SK = 5;

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
        public static final Function GET_INT = new Function(true, "getint", new ArrayList<>(), I32_TYPE);
        public static final Function GET_CH = new Function(true, "getch", new ArrayList<>(), I32_TYPE);
        public static final Function GET_FLOAT = new Function(true, "getfloat", new ArrayList<>(), F32_TYPE);
        public static final Function GET_ARR = new Function(true, "getarray", wrapParam(new Type.PointerType(I32_TYPE)), I32_TYPE);
        public static final Function GET_FARR = new Function(true, "getfarray", wrapParam(new Type.PointerType(F32_TYPE)), I32_TYPE);
        public static final Function PUT_INT = new Function(true, "putint", wrapParam(I32_TYPE), Type.VoidType.getVoidType());
        public static final Function PUT_CH = new Function(true, "putch", wrapParam(I32_TYPE), Type.VoidType.getVoidType());
        public static final Function PUT_FLOAT = new Function(true, "putfloat", wrapParam(F32_TYPE), Type.VoidType.getVoidType());
        public static final Function PUT_ARR = new Function(true, "putarray", wrapParam(I32_TYPE, new Type.PointerType(I32_TYPE)), Type.VoidType.getVoidType());
        public static final Function PUT_FARR = new Function(true, "putfarray", wrapParam(I32_TYPE, new Type.PointerType(F32_TYPE)), Type.VoidType.getVoidType());
        public static final Function MEM_SET = new Function(true, "memset", wrapParam(new Type.PointerType(I32_TYPE), I32_TYPE, I32_TYPE), Type.VoidType.getVoidType());
        public static final Function START_TIME = new Function(true, "starttime", wrapParam(I32_TYPE), Type.VoidType.getVoidType());
        public static final Function STOP_TIME = new Function(true, "stoptime", wrapParam(I32_TYPE), Type.VoidType.getVoidType());
    }

    public final ArrayList<Function> externalFuncList = new ArrayList<>();

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
        assert symbol.getValue() instanceof GlobalVal.GlobalValue;
        globals.putIfAbsent((GlobalVal.GlobalValue) symbol.getValue(), symbol.getInitial());
    }

    public void addFunction(Function function) {
        functions.putIfAbsent(function.getName(), function);
        externalFuncList.add(function);
    }

    public boolean hasMainFunction() {
        return functions.containsKey(MAIN_FUNCTION);
    }

    static int outputLLVMCnt = 0;

    public void outputLLVM() throws FileNotFoundException {
        outputLLVM("llvmOf-" + outputLLVMCnt++);
    }

    public void outputLLVM(String llvmFilename) throws FileNotFoundException {
        outputLLVM(new FileOutputStream(llvmFilename + ".ll"));
    }

    public void outputLLVM(OutputStream out) {
        FileDealer.outputClear();
        // 全局变量
        for (HashMap.Entry<GlobalVal.GlobalValue, Initial> entry : globals.entrySet()) {
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
                FileDealer.addOutputString(function.output());
            }
        }
        FileDealer.outputStringList(out);
    }

    static int outputMIcnt = 0;

    public void outputMI() throws FileNotFoundException {
        outputMI("mcinstOf-" + outputMIcnt++);
    }

    public void outputMI(String miFilename) throws FileNotFoundException {
        outputMI(new FileOutputStream(miFilename + ".txt"));
    }

    public void outputMI(OutputStream out) {
        FileDealer.outputClear();
        Machine.Program p = Machine.Program.PROGRAM;
        for (Machine.McFunction mcFunc : p.funcList) {
            FileDealer.addOutputString("\n");
            FileDealer.addOutputString(mcFunc.mFunc.getName());
            for (Machine.Block mb : mcFunc.mbList) {
                FileDealer.addOutputString("\n");
                FileDealer.addOutputString(mb.getDebugLabel());
                for (MachineInst mi : mb.miList) {
                    String str = mi instanceof MIComment ? "" : "\t";
                    FileDealer.addOutputString(str + mi);
                }
            }
        }

        FileDealer.outputStringList(out);
    }

    public HashMap<GlobalVal.GlobalValue, Initial> getGlobals() {
        return this.globals;
    }


    public HashMap<String, Function> getFunctions() {
        return this.functions;
    }

}
