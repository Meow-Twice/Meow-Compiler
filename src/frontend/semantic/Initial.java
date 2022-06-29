package frontend.semantic;

import mir.Value;
import mir.type.Type;

import java.util.ArrayList;

/**
 * 储存变量初始化的初值
 *
 * 人为约束: 初始化器的种类以及数组维度/长度，必须保证和类型中的数组信息一致
 */
public abstract class Initial {
    private final Type type; // LLVM IR 的初始值是含有类型信息的

    @Override
    public abstract String toString();

    public static class ArrayInit extends Initial {
    
        public ArrayInit(Type type) {
            super(type);
        }

        private final ArrayList<Initial> inits = new ArrayList<>();

        public int length() {
            return inits.size();
        }

        public void add(Initial init) {
            inits.add(init);
        }

        public Initial get(int index) {
            return inits.get(index);
        }

        @Override
        public String toString() {
            String init = inits.stream().map(Initial::toString).reduce((s, s2) -> s + ", " + s2).orElse("");
            return getType() + " [" + init + "]";
        }
    }

    public static class ValueInit extends Initial {
        private final Value value;

        public ValueInit(Value value, Type type) {
            super(type);
            this.value = value;
        }

        @Override
        public String toString() {
            return getType() + " " + value;
        }

        public Value getValue() {
            return this.value;
        }
        
    }

    // 初值为0的初始化
    public static class ZeroInit extends Initial {
    
        public ZeroInit(Type type) {
            super(type);
        }

        @Override
        public String toString() {
            return getType() + " zeroinitializer";
        }
    }

    // 用于编译期不可求值的初始化
    public static class ExpInit extends Initial {
        private final Value result;

        public ExpInit(Value result, Type type) {
            super(type);
            this.result = result;
        }

        @Override
        public String toString() {
            throw new AssertionError("non-evaluable initializer should never be output");
        }

        public Value getResult() {
            return this.result;
        }
        
    }

    public Initial(final Type type) {
        this.type = type;
    }
    
    public Type getType() {
        return this.type;
    }
    
}
