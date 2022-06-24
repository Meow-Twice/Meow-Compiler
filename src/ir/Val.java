package ir;

import frontend.semantic.Type;

/**
 * IR 中的操作数 Value
 *
 * 分为变量和常数
 */
public abstract class Val {
    public static final String GLOBAL_PREFIX = "@";
    public static final String LOCAL_PREFIX = "%";
    public static final String GLOBAL_NAME_PREFIX = "g";
    public static final String VAR_NAME_PREFIX = "v";
    private final Type type;

    public abstract String getDescriptor(); // Value 的描述符号 (变量名称或者常数值)

    @Override
    public String toString() {
        return type + " " + getDescriptor();
    }

    public static class Num extends Val {
        private final int value;

        public Num(int value, Type type) {
            super(type);
            this.value = value;
        }

        public Num(int value) {
            super(Type.BasicType.INT);
            this.value = value;
        }

        @Override
        public String getDescriptor() {
            return Integer.toString(value);
        }

        public int getValue() {
            return this.value;
        }
        
    }

    public static class Var extends Val {
        private final String name;
        private final boolean global;
        private final boolean constant; // 全局变量初始化时用

        public Var(String name, Type type, boolean global, boolean constant) {
            super(type);
            this.name = name;
            this.global = global;
            this.constant = constant;
        }

        private String prefix() {
            return global ? GLOBAL_PREFIX : LOCAL_PREFIX;
        }

        @Override
        public String getDescriptor() {
            return prefix() + name;
        }

        public String getName() {
            return this.name;
        }

        public boolean isGlobal() {
            return this.global;
        }

        public boolean isConstant() {
            return this.constant;
        }
        
    }

    private static int count = 0; // 生成变量的计数器

    public static Var newVar(Type type) {
        count++;
        return new Var(VAR_NAME_PREFIX + count, type, false, false);
    }

    public static Var newGlobal(Type type) {
        count++;
        return new Var(VAR_NAME_PREFIX + count, type, true, false);
    }

    public Val(final Type type) {
        this.type = type;
    }

    public Type getType() {
        return this.type;
    }
    
}
