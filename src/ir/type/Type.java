package ir.type;

import java.util.Objects;

/**
 * LLVM IR 中的变量类型系统
 */
public class Type {


    public boolean isVoidType() {
        return this == VoidType.VOID_TYPE;
    }

    public boolean isInt1Type() {
        return this instanceof BasicType && ((BasicType) this).dataType == DataType.BOOL;
    }

    public boolean isInt32Type() {
        return this instanceof BasicType && ((BasicType) this).dataType == DataType.INT;
    }

    public static class BasicType extends Type {
        private DataType dataType;

        public BasicType(DataType dataType) {
            this.dataType = dataType;
        }

        @Override
        public boolean equals(Object obj) {
            if(obj == null)return false;
            return obj instanceof BasicType && ((BasicType) obj).dataType == dataType;
        }

        @Override
        public String toString() {
            return dataType.toString();
        }
    }

    // 函数调用(Call)时需要此Type
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
        private final Type base;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PointerType that = (PointerType) o;
            return Objects.equals(base, that.base);
        }

        @Override
        public int hashCode() {
            return Objects.hash(base);
        }

        @Override
        public String toString() {
            return base + "*";
        }

        public PointerType(final Type base) {
            this.base = base;
        }

        public Type getBase() {
            return this.base;
        }

    }
}
