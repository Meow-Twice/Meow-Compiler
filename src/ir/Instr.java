package ir;

import frontend.semantic.Function;
import frontend.semantic.Types;
import util.ILinkNode;

import java.util.List;
import java.util.Map;

/**
 * LLVM IR 的一条指令
 */
public abstract class Instr extends ILinkNode {
    private final Value.Var ret; // 指令的返回值(可以为空)

    public boolean hasRet() {
        return ret != null;
    }

    @Override
    public abstract String toString();

    public interface Terminator {
    }

    // 二元算术运算, 结果是 i32 型
    public static class Alu extends Instr {

        public enum Op {
            ADD("add"), SUB("sub"), MUL("mul"), DIV("sdiv"), REM("srem"), AND("and"), OR("or");
            private final String name;

            private Op(final String name) {
                this.name = name;
            }

            public String getName() {
                return this.name;
            }

        }

        private final Op op;

        private final Value op1; // <ty> <op1>

        private final Value op2; // <op2>

        public Alu(Value.Var ret, Op op, Value op1, Value op2) {
            super(ret);

            if (ret == null) {
                throw new NullPointerException("ret is marked non-null but is null");
            }

            this.op = op;
            this.op1 = op1;
            this.op2 = op2;
        }

        @Override
        public String toString() {
            return getRet().getDescriptor() + " = " + op.getName() + " " + op1 + ", " + op2.getDescriptor();
        }

        public Op getOp() {
            return this.op;
        }

        public Value getOp1() {
            return this.op1;
        }

        public Value getOp2() {
            return this.op2;
        }

    }

    // 比较运算, 结果是 i1 型
    public static class Cmp extends Instr {

        public enum Op {
            EQ("eq"), NE("ne"), SGT("sgt"), SGE("sge"), SLT("slt"), SLE("sle");
            private final String name;

            private Op(final String name) {
                this.name = name;
            }

            public String getName() {
                return this.name;
            }

        }

        private final Op op;

        private final Value op1;

        private final Value op2;

        public Cmp(Value.Var ret, Op op, Value op1, Value op2) {
            super(ret);

            if (ret == null) {
                throw new NullPointerException("ret is marked non-null but is null");
            }

            assert ret.getType().equals(Types.BasicType.BOOL);
            this.op = op;
            this.op1 = op1;
            this.op2 = op2;
        }

        @Override
        public String toString() {
            return getRet().getDescriptor() + " = icmp " + op.getName() + " " + op1 + ", " + op2.getDescriptor();
        }

        public Op getOp() {
            return this.op;
        }

        public Value getOp1() {
            return this.op1;
        }

        public Value getOp2() {
            return this.op2;
        }

    }

    // 零扩展运算, 结果是 i32 型
    public static class Ext extends Instr {

        private final Value src;

        public Ext(Value.Var ret, Value src) {
            super(ret);

            if (ret == null) {
                throw new NullPointerException("ret is marked non-null but is null");
            }

            this.src = src;
        }

        @Override
        public String toString() {
            return getRet().getDescriptor() + " = zext " + src + " to " + getRet().getType();
        }

        public Value getSrc() {
            return this.src;
        }

    }

    // 分配内存, 结果是指针型
    public static class Alloc extends Instr {

        private final Types type;

        public Alloc(Value.Var ret, Types type) {
            super(ret);

            if (ret == null) {
                throw new NullPointerException("ret is marked non-null but is null");
            }

            assert ret.getType() instanceof Types.PointerType;
            this.type = type;
        }

        @Override
        public String toString() {
            return getRet().getDescriptor() + " = alloca " + type;
        }

        public Types getType() {
            return this.type;
        }

    }

    // 读取内存
    public static class Load extends Instr {

        private final Value address;

        public Load(Value.Var ret, Value address) {
            super(ret);

            if (ret == null) {
                throw new NullPointerException("ret is marked non-null but is null");
            }

            assert address.getType() instanceof Types.PointerType && ((Types.PointerType) address.getType()).getBase().equals(ret.getType());
            this.address = address;
        }

        @Override
        public String toString() {
            return getRet().getDescriptor() + " = load " + getRet().getType() + ", " + address;
        }

        public Value getAddress() {
            return this.address;
        }

    }

    // 写入内存
    public static class Store extends Instr {

        private final Value value;

        private final Value address;

        public Store(Value value, Value address) {
            super(null);

            if (value == null) {
                throw new NullPointerException("value is marked non-null but is null");
            }

            this.value = value;
            this.address = address;
        }

        @Override
        public String toString() {
            return "store " + value + ", " + address;
        }

        public Value getValue() {
            return this.value;
        }

        public Value getAddress() {
            return this.address;
        }

    }

    // 数组元素寻址, 每个该指令对指针/数组解引用一层, 返回值含义等价于 `base[offset]`
    public static class GetElementPtr extends Instr {

        private final Value base; // 基地址
        private final boolean array; // 是否需要第一层 offset 0 来解数组

        private final Value offset; // (解引用后的)偏移量

        // e.g. a is [10 x i32]*: a[3] -> %v1 = getelementptr inbounds [10 x i32], [10 x i32]* %a, i32 0, i32 3 ; 第一个 i32 0 表示解引用前不偏移(固定)，第二个才是解引用后的偏移
        // e.g. a is i32*: a[3] -> %v1 = getelementptr inbounds i32, i32* %a, i32 3
        public GetElementPtr(Value.Var ret, Value base, Value offset, boolean array) {
            super(ret);

            if (ret == null) {
                throw new NullPointerException("ret is marked non-null but is null");
            }

            assert base.getType() instanceof Types.PointerType;
            this.base = base;
            this.offset = offset;
            this.array = array;
            if (array) {
                assert ((Types.PointerType) base.getType()).getBase() instanceof Types.ArrayType;
                assert ret.getType().equals(new Types.PointerType(((Types.ArrayType) ((Types.PointerType) base.getType()).getBase()).getBase()));
            } else {
                assert ret.getType().equals(base.getType());
            }
        }

        @Override
        public String toString() {
            assert base.getType() instanceof Types.PointerType;
            if (array) {
                return getRet().getDescriptor() + " = getelementptr inbounds " + ((Types.PointerType) base.getType()).getBase() + ", " + base + ", i32 0, " + offset;
            } else {
                return getRet().getDescriptor() + " = getelementptr inbounds " + ((Types.PointerType) base.getType()).getBase() + ", " + base + ", " + offset;
            }
        }

        public Value getBase() {
            return this.base;
        }

        public boolean isArray() {
            return this.array;
        }

        public Value getOffset() {
            return this.offset;
        }

    }

    // 函数调用
    public static class Call extends Instr {

        private final Function function;

        private final List<Value> params;

        public Call(Value.Var ret, Function function, List<Value> params) {
            super(ret); // ret may be null
            this.function = function;
            this.params = params;
            assert (function.hasRet() && ret.getType().equals(function.getRetType())) || (!function.hasRet() && ret == null);
        }

        @Override
        public String toString() {
            String prefix = "";
            String retType = "void";
            if (getRet() != null) {
                prefix = getRet().getDescriptor() + " = ";
                retType = getRet().getType().toString();
            }
            String paramList = getParams().stream().map(Value::toString).reduce((s, s2) -> s + ", " + s2).orElse("");
            return prefix + "call " + retType + " @" + function.getName() + "(" + paramList + ")";
        }

        public Function getFunction() {
            return this.function;
        }

        public List<Value> getParams() {
            return this.params;
        }

    }

    // SSA Phi 指令
    public static class Phi extends Instr {

        private final Map<Value, BasicBlock> sources;

        public Phi(Value.Var ret, Map<Value, BasicBlock> sources) {
            super(ret);
            this.sources = sources;
        }

        @Override
        public String toString() {
            String src = sources.entrySet().stream().map(entry -> "[ " + (entry.getKey().getDescriptor()) + ", %" + entry.getValue().getLabel() + " ]").reduce((s, s2) -> s + ", " + s2).orElse("");
            return getRet().getDescriptor() + " = phi " + getRet().getType() + " " + src;
        }

        public Map<Value, BasicBlock> getSources() {
            return this.sources;
        }
    }

    // 分支
    public static class Branch extends Instr implements Terminator {

        private final Value cond;

        private final BasicBlock thenTarget;

        private final BasicBlock elseTarget;

        public Branch(Value cond, BasicBlock thenTarget, BasicBlock elseTarget) {
            super(null);
            assert cond.getType().equals(Types.BasicType.BOOL);
            this.cond = cond;
            this.thenTarget = thenTarget;
            this.elseTarget = elseTarget;
        }

        @Override
        public String toString() {
            return "br " + cond + ", label %" + thenTarget.getLabel() + ", label %" + elseTarget.getLabel();
        }

        public Value getCond() {
            return this.cond;
        }

        public BasicBlock getThenTarget() {
            return this.thenTarget;
        }

        public BasicBlock getElseTarget() {
            return this.elseTarget;
        }

    }

    // 直接跳转
    public static class Jump extends Instr implements Terminator {
        private final BasicBlock target;

        public Jump(BasicBlock target) {
            super(null);
            this.target = target;
        }

        @Override
        public String toString() {
            return "br label %" + target.getLabel();
        }

        public BasicBlock getTarget() {
            return this.target;
        }

    }

    // 返回
    public static class Return extends Instr implements Terminator {
        private final Value value;

        public Return(Value value) {
            super(null);
            this.value = value;
        }

        public boolean hasValue() {
            return value != null;
        }

        @Override
        public String toString() {
            if (hasValue()) {
                return "ret " + value;
            } else {
                return "ret void";
            }
        }

        public Value getValue() {
            return this.value;
        }

    }

    public Instr(final Value.Var ret) {
        this.ret = ret;
    }

    public Value.Var getRet() {
        return this.ret;
    }

}
