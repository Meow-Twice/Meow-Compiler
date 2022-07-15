package mir.type;

import java.util.ArrayList;
import java.util.Objects;

/**
 * LLVM IR 中的变量类型系统
 */
public class Type {

    public static VoidType getVoidType() {
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
        return this == BasicType.getF32Type();
    }

    public boolean isPointerType() {
        return this instanceof PointerType;
    }

    public boolean isBasicType() {
        return this instanceof BasicType;
    }

    public static class BasicType extends Type {
        public DataType dataType;
        public static final BasicType I32_TYPE = new BasicType(DataType.I32);
        public static final BasicType I1_TYPE = new BasicType(DataType.I1);
        public static final BasicType F32_TYPE = new BasicType(DataType.F32);

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

        private VoidType() {
        }

        public static VoidType getVoidType() {
            return VOID_TYPE;
        }

        @Override
        public boolean equals(Object obj) {
            return obj == VOID_TYPE;
        }

        @Override
        public String toString() {
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
        private final Type baseType;
        private BasicType baseEleType = null;
        private ArrayList<Integer> dims = new ArrayList<>();

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ArrayType arrayType = (ArrayType) o;
            return size == arrayType.size && Objects.equals(baseType, arrayType.baseType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(size, baseType);
        }

        @Override
        public String toString() {
            return String.format("[%d x %s]", size, baseType);
        }

        public ArrayType(final int size, final Type baseType) {
            this.size = size;
            this.baseType = baseType;
            dims.add(size);
            if (baseType.isArrType()) {
                dims.addAll(((ArrayType) baseType).dims);
            }
        }

        public int getSize() {
            return this.size;
        }

        public int getFlattenSize() {
            // TODO: 没有做高速缓存, 保证更改后不会出问题
            if (baseType instanceof BasicType) {
                return size;
            }
            assert baseType.isArrType();
            return ((ArrayType) baseType).getFlattenSize() * size;
        }

        public Type getBaseType() {
            return this.baseType;
        }

        public BasicType getBaseEleType() {
            if (baseEleType != null) {
                return baseEleType;
            }
            if (baseType instanceof BasicType) {
                return (BasicType) baseType;
            }
            assert baseType instanceof ArrayType;
            return ((ArrayType) baseType).getBaseEleType();
        }

        public ArrayList<Integer> getDims() {
            // TODO: 没有用高速缓存, 每次都新建
            dims.clear();
            dims.add(size);
            if(baseType.isArrType()){
                dims.addAll(((ArrayType) baseType).dims);
            }
            return dims;
        }

        public int getDimSize(){
            if(baseType.isArrType()){
                return ((ArrayType) baseType).getDimSize() + 1;
            }
            return 0;
        }

        public int getBaseFlattenSize(){
            if(baseType.isArrType()){
                return ((ArrayType) baseType).getFlattenSize();
            }
            return 4;
        }
    }

    public boolean isArrType() {
        return this instanceof ArrayType;
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
