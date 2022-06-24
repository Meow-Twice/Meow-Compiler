package ir;

import frontend.semantic.Function;
import frontend.semantic.Types;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LLVM IR 的一条指令
 */
public class Instr extends Value {
    private Val.Var ret; // 指令的返回值(可以为空)

    public boolean hasRet() {
        return ret != null;
    }

    public interface Terminator {
    }
    protected ArrayList<Use> useList;
    protected ArrayList<Value> useValueList;

    public Instr(){
        useList = new ArrayList<>();
        useValueList = new ArrayList<>();
    }

    protected void setUse(Value value, int idx) {
        Use use = new Use(this, value, idx);
        value.insertAtEnd(use);
        useList.add(use);
        useValueList.add(value);
    }

    public void modifyUse(Value old, Value now, int index) {
        // remove old
        old.remove();
        // add now
        Use use = new Use(this, now, index);
        this.useList.set(index, use);
        this.useValueList.set(index, now);
        now.insertAtEnd(use);
    }

    // @Override
    public String getDescriptor() {
        return prefix + name;
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
        public Alu(Val.Var ret, Op op, Value op1, Value op2) {
            super(ret);

            if (ret == null) {
                throw new NullPointerException("ret is marked non-null but is null");
            }

            this.op = op;
            setUse(op1, 0);
            setUse(op2, 1);
        }

        @Override
        public String toString() {
            return getRet().getDescriptor() + " = " + op.getName() + " " + op1 + ", " + op2.getDescriptor();
        }

        public Op getOp() {
            return this.op;
        }

        public Value getRVal1() {
            return useValueList.get(0);
        }

        public Value getRVal2() {
            return useValueList.get(1);
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

        private final Val op1;

        private final Val op2;

        public Cmp(Val.Var ret, Op op, Val op1, Val op2) {
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

        public Val getOp1() {
            return this.op1;
        }

        public Val getOp2() {
            return this.op2;
        }

    }

    // 零扩展运算, 结果是 i32 型
    public static class Ext extends Instr {

        private final Val src;

        public Ext(Val.Var ret, Val src) {
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

        public Val getSrc() {
            return this.src;
        }

    }

    // 分配内存, 结果是指针型
    public static class Alloc extends Instr {

        private final Types type;

        public Alloc(Val.Var ret, Types type) {
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

        private final Val address;

        public Load(Val.Var ret, Val address) {
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

        public Val getAddress() {
            return this.address;
        }

    }

    // 写入内存
    public static class Store extends Instr {

        private final Val val;

        private final Val address;

        public Store(Val val, Val address) {
            super(null);

            if (val == null) {
                throw new NullPointerException("value is marked non-null but is null");
            }

            this.val = val;
            this.address = address;
        }

        @Override
        public String toString() {
            return "store " + val + ", " + address;
        }

        public Val getValue() {
            return this.val;
        }

        public Val getAddress() {
            return this.address;
        }

    }

    // 数组元素寻址, 每个该指令对指针/数组解引用一层, 返回值含义等价于 `base[offset]`
    public static class GetElementPtr extends Instr {

        private final Val base; // 基地址
        private final boolean array; // 是否需要第一层 offset 0 来解数组

        private final Val offset; // (解引用后的)偏移量

        // e.g. a is [10 x i32]*: a[3] -> %v1 = getelementptr inbounds [10 x i32], [10 x i32]* %a, i32 0, i32 3 ; 第一个 i32 0 表示解引用前不偏移(固定)，第二个才是解引用后的偏移
        // e.g. a is i32*: a[3] -> %v1 = getelementptr inbounds i32, i32* %a, i32 3
        public GetElementPtr(Val.Var ret, Val base, Val offset, boolean array) {
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

        public Val getBase() {
            return this.base;
        }

        public boolean isArray() {
            return this.array;
        }

        public Val getOffset() {
            return this.offset;
        }

    }

    // 函数调用
    public static class Call extends Instr {

        private final Function function;

        private final List<Val> params;

        public Call(Val.Var ret, Function function, List<Val> params) {
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
            String paramList = getParams().stream().map(Val::toString).reduce((s, s2) -> s + ", " + s2).orElse("");
            return prefix + "call " + retType + " @" + function.getName() + "(" + paramList + ")";
        }

        public Function getFunction() {
            return this.function;
        }

        public List<Val> getParams() {
            return this.params;
        }

    }

    // SSA Phi 指令
    public static class Phi extends Instr {

        private final Map<Val, BasicBlock> sources;

        public Phi(Val.Var ret, Map<Val, BasicBlock> sources) {
            super(ret);
            this.sources = sources;
        }

        @Override
        public String toString() {
            String src = sources.entrySet().stream().map(entry -> "[ " + (entry.getKey().getDescriptor()) + ", %" + entry.getValue().getLabel() + " ]").reduce((s, s2) -> s + ", " + s2).orElse("");
            return getRet().getDescriptor() + " = phi " + getRet().getType() + " " + src;
        }

        public Map<Val, BasicBlock> getSources() {
            return this.sources;
        }
    }

    // 分支
    public static class Branch extends Instr implements Terminator {

        private final Val cond;

        private final BasicBlock thenTarget;

        private final BasicBlock elseTarget;

        public Branch(Val cond, BasicBlock thenTarget, BasicBlock elseTarget) {
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

        public Val getCond() {
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
        private final Val val;

        public Return(Val val) {
            super(null);
            this.val = val;
        }

        public boolean hasValue() {
            return val != null;
        }

        @Override
        public String toString() {
            if (hasValue()) {
                return "ret " + val;
            } else {
                return "ret void";
            }
        }

        public Val getValue() {
            return this.val;
        }

    }

    public Instr(final Val.Var ret) {
        this.ret = ret;
    }

    public Val.Var getRet() {
        return this.ret;
    }

}
