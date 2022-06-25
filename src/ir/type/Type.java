package ir.type;

import ir.BasicBlock;

import java.util.Objects;

/**
 * LLVM IR 中的变量类型系统
 */
public class Type {


    public static class BasicType extends Type {
        private DataType dataType;

        public BasicType(DataType dataType) {
            this.dataType = dataType;
        }

        @Override
        public String toString() {
            return dataType.toString();
        }
    }

    public static class VoidType extends Type {
        public VoidType(){}

        @Override
        public String toString(){
            return "void";
        }
    }


    public static class BBType extends Type {
        private static BBType type = new BBType();

        private BBType() {
        }

        public static BBType getType() {
            return type;
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
