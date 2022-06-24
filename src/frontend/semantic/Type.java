package frontend.semantic;

import java.util.Objects;

/**
 * LLVM IR 中的变量类型系统
 */
public interface Type {



    enum BasicType implements Type {
        INT("i32"), FLOAT("f32"), BOOL("i1");
        private final String descriptor;

        @Override
        public String toString() {
            return descriptor;
        }

        private BasicType(final String descriptor) {
            this.descriptor = descriptor;
        }

        public String getDescriptor() {
            return this.descriptor;
        }
        
    }

    class ArrayType implements Type {
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

    class PointerType implements Type {
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
