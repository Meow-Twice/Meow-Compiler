package ir;

import ir.type.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LLVM IR 的一条指令
 */
public class Instr extends Value {

    public interface Terminator {
    }
    protected ArrayList<Use> useList;
    protected ArrayList<Value> useValueList;

    //空指令用于在BB中做链表头/尾
    public Instr() {
        super();
    }

    public Instr(Type type){
        super(type);
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
        public Alu(Type type, Op op, Value op1, Value op2) {
            super(type);
            this.op = op;
            setUse(op1, 0);
            setUse(op2, 1);
        }

        @Override
        public String toString() {
            return this.getDescriptor() + " = " + op.getName() + " " + getRVal1().getDescriptor() + ", " + getRVal2().getDescriptor();
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

        private Op op;

        public Cmp(Type type, Op op, Value op1, Value op2) {
            super(type);
            this.op = op;
            setUse(op1, 0);
            setUse(op2, 1);
        }

        public String toString() {
            return this.getDescriptor() + " = icmp " + op.getName() + " " + getRVal1().getDescriptor() + ", " + getRVal2().getDescriptor();
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

    // 零扩展运算, 结果是 i32 型
    public static class Ext extends Instr {


        public Ext(Type type, Value src) {
            super(type);
            setUse(src, 0);
        }

        public String toString() {
            return this.getDescriptor() + " = zext " + getRVal1().getDescriptor() + " to " + this.getType();
        }


        public Value getRVal1() {
            return useValueList.get(0);
        }

    }

    // 分配内存, 结果是指针型
    public static class Alloc extends Instr {

        private Type contentType;

        public Alloc(Type type, Type contentType) {
            super(type);
            this.contentType = contentType;
        }

        @Override
        public String toString() {
            return this.getDescriptor() + " = alloca " + contentType;
        }

        public Type getContentType() {
            return this.contentType;
        }

    }

    // 读取内存
    public static class Load extends Instr {
        //TODO:修改toString()方法添加指令的Type
        public Load(Type type, Value RVal) {
            super(type);
            setUse(RVal, 0);
        }

        @Override
        public String toString() {
            return this.getDescriptor() + " = load " + getRet().getType() + ", " + this.getRVal1().getDescriptor();
        }

        public Value getRVal1() {
            return this.useValueList.get(0);
        }

    }

    // 写入内存
    public static class Store extends Instr {

        //TODO:修改toString()方法添加指令的Type
        public Store(Value value, Value address) {
            super(null);
            setUse(value, 0);
            setUse(address, 1);
        }

        @Override
        public String toString() {
            return "store " + this.getRVal1().getDescriptor() + ", " + this.getRVal2().getDescriptor();
        }

        public Value getRVal1() {
            return this.useValueList.get(0);
        }

        public Value getRVal2() {
            return this.useValueList.get(1);
        }
    }

    // 数组元素寻址, 每个该指令对指针/数组解引用一层, 返回值含义等价于 `base[offset]`
    public static class GetElementPtr extends Instr {

        //TODO:学习getelementptr
        private final Val base; // 基地址
        private final boolean array; // 是否需要第一层 offset 0 来解数组

        private final Val offset; // (解引用后的)偏移量

        // e.g. a is [10 x i32]*: a[3] -> %v1 = getelementptr inbounds [10 x i32], [10 x i32]* %a, i32 0, i32 3 ; 第一个 i32 0 表示解引用前不偏移(固定)，第二个才是解引用后的偏移
        // e.g. a is i32*: a[3] -> %v1 = getelementptr inbounds i32, i32* %a, i32 3
        public GetElementPtr(Type type, Val base, Val offset, boolean array) {
            super(type);

            if (ret == null) {
                throw new NullPointerException("ret is marked non-null but is null");
            }

            assert base.getType() instanceof Type.PointerType;
            this.base = base;
            this.offset = offset;
            this.array = array;
            if (array) {
                assert ((Type.PointerType) base.getType()).getBase() instanceof Type.ArrayType;
                assert ret.getType().equals(new Type.PointerType(((Type.ArrayType) ((Type.PointerType) base.getType()).getBase()).getBase()));
            } else {
                assert ret.getType().equals(base.getType());
            }
        }

        @Override
        public String getDescriptor() {
            assert base.getType() instanceof Type.PointerType;
            if (array) {
                return getRet().getDescriptor() + " = getelementptr inbounds " + ((Type.PointerType) base.getType()).getBase() + ", " + base + ", i32 0, " + offset;
            } else {
                return getRet().getDescriptor() + " = getelementptr inbounds " + ((Type.PointerType) base.getType()).getBase() + ", " + base + ", " + offset;
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

        //TODO:考虑函数调用的user used

        private final Function function;

        private final List<Val> params;

        public Call(Type type, Function function, List<Val> params) {
            super(type); // ret may be null
            this.function = function;
            this.params = params;
            assert (function.hasRet() && ret.getType().equals(function.getRetType())) || (!function.hasRet() && ret == null);
        }

        @Override
        public String getDescriptor() {
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

        //TODO:assign to 刘传
        private final Map<Val, BasicBlock> sources;

        public Phi(Type type, Map<Val, BasicBlock> sources) {
            super(type);
            this.sources = sources;
        }

        @Override
        public String getDescriptor() {
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
            assert cond.getType().equals(Type.BasicType.BOOL);
            this.cond = cond;
            this.thenTarget = thenTarget;
            this.elseTarget = elseTarget;
        }

        @Override
        public String getDescriptor() {
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
        public String getDescriptor() {
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
        public String getDescriptor() {
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


    public Val.Var getRet() {
        return this.ret;
    }

}
