package mir;

import mir.type.Type;
import util.CenterControl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

public class Constant extends Value {
    private static int const_num = 0;

    private int hash = const_num++;

    public Constant(Type type) {
        this.type = type;
    }

    public Object getConstVal() {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Constant constant = (Constant) o;
        return hash == constant.hash;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), hash);
    }

    public static class ConstantInt extends Constant {
        public int constIntVal;

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
            super(Type.BasicType.getI32Type());
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

        @Override
        public Object getConstVal() {
            return constIntVal;
        }

        @Override
        public String getName() {
            return String.valueOf(constIntVal);
        }

        @Override
        public String toString() {
            return String.valueOf(constIntVal);
        }
    }

    public static class ConstantFloat extends Constant {
        float constFloatVal;
        private static final HashMap<Float, ConstantFloat> constFloatMap = new HashMap<>();
        public static final ConstantFloat CONST_0F/* = new ConstantInt(0)*/;
        public static final ConstantFloat CONST_1F/* = new ConstantInt(1)*/;

        static {
            CONST_0F = new ConstantFloat(0);
            CONST_1F = new ConstantFloat(1);
            constFloatMap.put((float) 0.0, CONST_0F);
            constFloatMap.put((float) 1.0, CONST_1F);
        }

        public ConstantFloat(float val) {
            super(Type.BasicType.getF32Type());
            constFloatVal = val;
        }

        String asmName = null;
        static int floatConstCnt = 1;

        public String getAsmName() {
            if (asmName == null) {
                asmName = "_F_CONST_" + floatConstCnt++;
            }
            return asmName;
        }

        @Override
        public Object getConstVal() {
            return constFloatVal;
        }

        @Override
        public String getName() {
            return String.format("0x%x", Double.doubleToRawLongBits((constFloatVal)));
        }

        public boolean isZero() {
            return getName().equals("0x0");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            ConstantFloat that = (ConstantFloat) o;
            return this.getName().equals(that.getName());
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), constFloatVal);
        }

        @Override
        public String toString() {
            if (CenterControl.AlreadyBackend) {
                return String.valueOf(getIntBits());
            }
            return String.format("0x%x", Double.doubleToRawLongBits((constFloatVal)));
        }

        public int getIntBits() {
            return Float.floatToIntBits(constFloatVal);
        }
    }

    public static class ConstantArray extends Constant {
        private ArrayList<Constant> constArray;
        // private ArrayList<Integer> arrayInt1D = new ArrayList<>();
        // private ArrayList<Float> arrayFloat1D = new ArrayList<>();
        private Type baseEleType;

        public ConstantArray(Type type, Type baseEleType, ArrayList<Constant> arrayList) {
            super(type);
            assert type instanceof Type.ArrayType;
            this.baseEleType = baseEleType;
            constArray = arrayList;
        }

        public Constant getBaseConst(ArrayList<Integer> dims) {
            int idx = 0;
            Type.ArrayType type = (Type.ArrayType) this.type;
            for (int i = 0; i < dims.size() - 1; i++) {
                assert type.getBaseType() instanceof Type.ArrayType;
                type = (Type.ArrayType) type.getBaseType();
                idx += type.getSize();
            }
            assert idx < constArray.size() + dims.get(dims.size() - 1);
            return constArray.get(idx);
        }
    }

    public static class ConstantBool extends Constant {

        private int constBoolVal;

        public ConstantBool(int val) {
            super(Type.BasicType.getI1Type());
            assert val == 0 || val == 1;
            constBoolVal = val;
        }


        @Override
        public Object getConstVal() {
            return constBoolVal;
        }

        @Override
        public String getName() {
            return String.valueOf(constBoolVal);
        }

        @Override
        public String toString() {
            return String.valueOf(constBoolVal);
        }
    }
}
