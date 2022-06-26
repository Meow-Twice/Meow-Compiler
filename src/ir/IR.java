package ir;

import frontend.semantic.Initial;
import frontend.semantic.symbol.Symbol;
import ir.type.Type;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * LLVM IR
 */
public class IR {
    private static final String MAIN_FUNCTION = "main";
    private final Map<Value, Initial> globals = new HashMap<>();
    private final Map<String, Function> functions = new HashMap<>(); // 函数定义

    public static class ExternFunction {
        public static final Function GET_INT = new Function("getint", List.of(), Type.BasicType.INT);
        public static final Function PUT_INT = new Function("putint", List.of(Val.newVar(Type.BasicType.INT)), null);
        public static final Function GET_CH = new Function("getch", List.of(), Type.BasicType.INT);
        public static final Function PUT_CH = new Function("putch", List.of(Val.newVar(Type.BasicType.INT)), null);
        public static final Function MEM_SET = new Function("memset", Stream.of(new Type.PointerType(Type.BasicType.INT), Type.BasicType.INT, Type.BasicType.INT).map(Val::newVar).collect(Collectors.toUnmodifiableList()), null);
        public static final Function GET_ARR = new Function("getarray", List.of(Val.newVar(new Type.PointerType(Type.BasicType.INT))), Type.BasicType.INT);
        public static final Function PUT_ARR = new Function("putarray", Stream.of(Type.BasicType.INT, new Type.PointerType(Type.BasicType.INT)).map(Val::newVar).collect(Collectors.toUnmodifiableList()), null);
    }

    public IR() {
        addFunction(ExternFunction.GET_INT);
        addFunction(ExternFunction.PUT_INT);
        addFunction(ExternFunction.GET_CH);
        addFunction(ExternFunction.PUT_CH);
        addFunction(ExternFunction.MEM_SET);
        addFunction(ExternFunction.GET_ARR);
        addFunction(ExternFunction.PUT_ARR);
    }

    public void addGlobal(Symbol symbol) {
        assert symbol.getInitial() != null;
        globals.putIfAbsent(symbol.getPointer(), symbol.getInitial());
    }

    public void addFunction(Function function) {
        functions.putIfAbsent(function.getName(), function);
    }

    public boolean hasMainFunction() {
        return functions.containsKey(MAIN_FUNCTION);
    }

    public void output() {
        // 全局变量
        for (Map.Entry<Value, Initial> entry : globals.entrySet()) {
            System.out.println(entry.getKey().getDescriptor() + " = dso_local global " + entry.getValue());
        }
        // 函数声明
        for (Function function : functions.values()) {
            if (!function.hasBody()) {
                System.out.println(function.getDeclare());
            }
        }
        // 函数定义
        for (Function function : functions.values()) {
            if (function.hasBody()) {
                System.out.println(function.getDefinition());
            }
        }
    }

    public Map<Value, Initial> getGlobals() {
        return this.globals;
    }

    
    public Map<String, Function> getFunctions() {
        return this.functions;
    }
    
}
