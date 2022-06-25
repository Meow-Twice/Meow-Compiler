package ir;

import ir.type.Type;

import java.util.ArrayList;
import java.util.HashMap;

public class Constant extends Value {
    public Constant(Type type) {
        this.type = type;
    }

    public static class ConstantInt extends Constant {
        int constIntVal;

        private static final HashMap<Integer, ConstantInt> constIntMap = new HashMap<>();
        public static final ConstantInt CONST_0/* = new ConstantInt(0)*/;
        public static final ConstantInt CONST_1/* = new ConstantInt(1)*/;

        static {
            CONST_0 = new ConstantInt(0);
            CONST_1 = new ConstantInt(1);
            constIntMap.put(0, CONST_0);
            constIntMap.put(1, CONST_1);
        }

        public ConstantInt(int val) {
            super(Type.BasicType.getF32Type());
            constIntVal = val;
        }

        public static ConstantInt getConstInt(int intVal) {
            ConstantInt ret = constIntMap.get(intVal);
            if (ret == null) {
                ret = new ConstantInt(intVal);
                constIntMap.put(intVal, ret);
            }
            return ret;
        }
    }

    public static class ConstantFloat extends Constant {
        float constFloatVal;

        public ConstantFloat(int val) {
            super(Type.BasicType.getF32Type());
            constFloatVal = val;
        }
    }

    public static class ConstantArray extends Constant {
        private ArrayList<Constant> constArray;
        private ArrayList<Integer> arrayInt1D = new ArrayList<>();
        private ArrayList<Float> arrayFloat1D = new ArrayList<>();
        private Type eleType;

        public ConstantArray(Type type, Type eleType, ArrayList<Constant> arrayList) {
            super(type);
            assert type instanceof Type.ArrayType;
            this.eleType = eleType;
        }

        public Constant getBaseConst(ArrayList<Integer> dims) {
            int idx = 0;
            Type.ArrayType type = (Type.ArrayType) this.type;
            for (int i = 0; i < dims.size() - 1; i++) {
                assert type.getBase() instanceof Type.ArrayType;
                type = (Type.ArrayType) type.getBase();
                idx += type.getSize();
            }
            assert idx < constArray.size() + dims.get(dims.size() - 1);
            return constArray.get(idx);
        }
    }
}
