package mir;

import frontend.semantic.Initial;
import frontend.syntax.Ast;
import mir.type.Type;

public class GlobalVal extends Value {
    public GlobalVal(Type type) {
        super(type);
    }

    public static class GlobalValue extends GlobalVal {
        private static int GLOBAL_COUNT = 0;
        public Ast.Def def;
        public Initial initial;

        public GlobalValue(Type pointeeType, Ast.Def def, Initial initial) {
            super(new Type.PointerType(pointeeType));
            prefix = GLOBAL_PREFIX;
            name = GLOBAL_NAME_PREFIX + GLOBAL_COUNT++;
            this.def = def;
            this.initial = initial;
        }
    }

    public static class UndefValue {
        public UndefValue() {

        }
    }
}
