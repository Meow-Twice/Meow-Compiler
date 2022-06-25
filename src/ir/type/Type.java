package ir.type;

import java.util.Objects;

/**
 * LLVM IR 中的变量类型系统
 */
public class Type {

    public static VoidType getVoidType(){
        return VoidType.getVoidType();
    }

    // public static BasicType getInt1Type(){
    //     return BasicType.getInt1Type();
    // }
    //
    // public static BasicType getInt32Type(){
    //     return BasicType.getInt32Type();
    // }

    public boolean isVoidType() {
        return this == VoidType.VOID_TYPE;
    }

    // public boolean isInt1Type() {
    //     return this instanceof BasicType && ((BasicType) this).dataType == DataType.BOOL;
    // }
    //
    // public boolean isInt32Type() {
    //     return this instanceof BasicType && ((BasicType) this).dataType == DataType.INT;
    // }

    public boolean isInt1Type() {
        return this == BasicType.getI1Type();
    }

    public boolean isInt32Type() {
        return this == BasicType.getI32Type();
    }

    public boolean isFloatType() {
        return this == BasicType.getI32Type();
    }

    public static class BasicType extends Type {
        private DataType dataType;
        private static final BasicType I32_TYPE = new BasicType(DataType.INT);
        private static final BasicType I1_TYPE = new BasicType(DataType.BOOL);
        private static final BasicType F32_TYPE = new BasicType(DataType.FLOAT);

        public static BasicType getF32Type() {
            return F32_TYPE;
        }

        public static BasicType getI1Type() {
            return I1_TYPE;
        }

        public static BasicType getI32Type() {
            return I32_TYPE;
        }

        private BasicType(DataType dataType) {
            this.dataType = dataType;
        }

        // @Override
        // public boolean equals(Object obj) {
        //     if(obj == null)return false;
        //     return obj instanceof BasicType && ((BasicType) obj).dataType == dataType;
        // }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public String toString() {
            return dataType.toString();
        }
    }

    // Call可能是VoidType
    // 认为Store是VoidType
    // Terminator(Return, Jump, Branch)都是VoidType
    public static class VoidType extends Type {
        private final static VoidType VOID_TYPE = new VoidType();
        private VoidType(){}

        public static VoidType getVoidType(){
            return VOID_TYPE;
        }

        @Override
        public boolean equals(Object obj) {
            return obj == VOID_TYPE;
        }

        @Override
        public String toString(){
            return "void";
        }
    }


    public static class BBType extends Type {
        private final static BBType BB_TYPE = new BBType();

        private BBType() {
        }

        public static BBType getBBType() {
            return BB_TYPE;
        }

        @Override
        public boolean equals(Object obj) {
            return obj == BB_TYPE;
        }

        @Override
        public String toString() {
            return "b ";
        }
    }

    public static class ArrayType extends Type {
        private final int size;
        private final Type base;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ArrayType arrayType = (ArrayType) o;
            return size == arrayType.size && Objects.equals(base, arrayType.base);
        }

        @Override
        public int hashCode() {
            return Objects.hash(size, base);
        }

        @Override
        public String toString() {
            return String.format("[%d x %s]", size, base);
        }

        public ArrayType(final int size, final Type base) {
            this.size = size;
            this.base = base;
        }

        public int getSize() {
            return this.size;
        }

        public Type getBase() {
            return this.base;
        }

    }

    public static class PointerType extends Type {
        private final Type innerType;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PointerType that = (PointerType) o;
            return Objects.equals(innerType, that.innerType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(innerType);
        }

        @Override
        public String toString() {
            return innerType + "*";
        }

        public PointerType(Type innerType) {
            this.innerType = innerType;
        }

        public Type getInnerType() {
            return this.innerType;
        }

    }
}
