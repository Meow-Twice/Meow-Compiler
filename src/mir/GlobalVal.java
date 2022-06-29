package mir;

import frontend.semantic.Initial;
import frontend.syntax.Ast;
import mir.type.Type;

public class GlobalVal extends Value {
    public GlobalVal(Type type) {
        super(type);
    }

    public static class GlobalValue extends GlobalVal {
        public Ast.Def def;
        public Initial initial;

        public GlobalValue(Type pointeeType, Ast.Def def, Initial initial) {
            super(new Type.PointerType(pointeeType));
            this.def = def;
            this.initial = initial;
        }
    }

    public static class UndefValue {
        public UndefValue() {

        }
    }
}
