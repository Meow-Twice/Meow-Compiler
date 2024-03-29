package mir;

import frontend.semantic.Initial;
import frontend.syntax.Ast;
import mir.type.Type;

public class GlobalVal extends Value {
    public GlobalVal(Type type) {
        super(type);
    }

    public GlobalVal() {

    }

    public static class GlobalValue extends GlobalVal {
        // private static int GLOBAL_COUNT = 0;
        public Ast.Def def;
        public Initial initial;
        public boolean local = false;

        public GlobalValue(Type pointeeType, Ast.Def def, Initial initial) {
            super(new Type.PointerType(pointeeType));
            prefix = GLOBAL_PREFIX;
            name = "g_" + def.ident.getContent();
            // name = GLOBAL_NAME_PREFIX + GLOBAL_COUNT++;
            this.def = def;
            this.initial = initial;
        }

        public GlobalValue(Type pointeeType, String name, Initial initial) {
            super(new Type.PointerType(pointeeType));
            prefix = GLOBAL_PREFIX;
            this.name = name;
            this.initial = initial;
        }

        public void setCanLocal() {
            this.local = true;
        }

        public boolean canLocal() {
            return this.local;
        }
    }

    public static class UndefValue extends GlobalVal {
        private static int undefValueCnt = 0;

        private String label;
        private String name;

        public UndefValue() {
            super();
        }

        public UndefValue(Type type) {
            super(type);
        }


        @Override
        public String getName() {
            return "undef";
        }

        public String getLabel() {
            return label;
        }
    }

    public static class VirtualValue extends GlobalVal{
        private static int virtual_value_cnt = 0;

        public VirtualValue(Type type) {
            super(type);
            prefix = LOCAL_PREFIX;
            name = GLOBAL_NAME_PREFIX + virtual_value_cnt++;
        }
    }
}
