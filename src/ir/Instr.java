package ir;

import ir.type.Type;
import ir.type.Type.*;

import java.util.ArrayList;

/**
 * LLVM IR 的一条指令
 */
public class Instr extends Value {
    public BasicBlock bb;

    public BasicBlock parentBB(){
        return bb;
    }

    public interface Terminator {
    }

    protected ArrayList<Use> useList;
    protected ArrayList<Value> useValueList;

    //空指令用于在BB中做链表头/尾
    public Instr() {
        super();
    }


    public Instr(BasicBlock curBB) {
        super();
        bb = curBB;
    }


    // public Instr(Type type){
    //     super(type);
    //     useList = new ArrayList<>();
    //     useValueList = new ArrayList<>();
    // }

    public Instr(Type type, BasicBlock curBB) {
        super(type);
        bb = curBB;
        bb.insertAtEnd(this);
        useList = new ArrayList<>();
        useValueList = new ArrayList<>();
    }

    public Instr(Type type, BasicBlock curBB, boolean Lipsum) {
        super(type);
        bb = curBB;
        bb.insertAtHead(this);
        useList = new ArrayList<>();
        useValueList = new ArrayList<>();
    }

    public Instr(Type type, Instr instr) {
        super(type);
        bb = instr.bb;
        // 在instr前面插入this (->this->instr->)
        instr.insertBefore(this);
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
            ADD("add"),
            FADD("fadd"),
            SUB("sub"),
            FSUB("fsub"),
            MUL("mul"),
            FMUL("fmul"),
            DIV("sdiv"),
            FDIV("fdiv"),
            REM("srem"),
            FREM("frem"),
            AND("and"),
            OR("or");
            private final String name;

            private Op(final String name) {
                this.name = name;
            }

            public String getName() {
                return this.name;
            }

        }

        private Op op;

        public Alu(Type type, Op op, Value op1, Value op2, BasicBlock basicBlock) {
            super(type, basicBlock);
            assert type.equals(op1.type) && op1.type.equals(op2.type);
            this.op = op;
            setUse(op1, 0);
            setUse(op2, 1);
        }

        public Alu(Type type, Op op, Value op1, Value op2, Instr insertBefore) {
            super(type, insertBefore);
            assert type.equals(op1.type) && op1.type.equals(op2.type);
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

        public void setOp(Op op) {
            this.op = op;
        }

        public Value getRVal1() {
            return useValueList.get(0);
        }

        public Value getRVal2() {
            return useValueList.get(1);
        }

    }

    // 比较运算, 结果是 i1 型
    public static class Icmp extends Instr {

        public enum Op {
            EQ("eq"),
            NE("ne"),
            SGT("sgt"),
            SGE("sge"),
            SLT("slt"),
            SLE("sle");
            private final String name;

            private Op(final String name) {
                this.name = name;
            }

            public String getName() {
                return this.name;
            }

        }

        private final Op op;

        public Icmp(Op op, Value op1, Value op2, BasicBlock curBB) {
            super(BasicType.getI1Type(), curBB);
            assert op1.type.isInt32Type() && op2.type.isInt32Type();
            this.op = op;
            setUse(op1, 0);
            setUse(op2, 1);
        }

        public Icmp(Op op, Value op1, Value op2, Instr insertBefore) {
            super(BasicType.getI1Type(), insertBefore);
            assert op1.type.isInt32Type() && op2.type.isInt32Type();
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

    public static class Fneg extends Instr {

        public Fneg(Value src, BasicBlock parentBB) {
            super(BasicType.getF32Type(), parentBB);
            assert src.type.isFloatType();
            setUse(src, 0);
        }

        public Fneg(Value src, Instr insertBefore) {
            super(BasicType.getF32Type(), insertBefore);
            assert src.type.isFloatType();
            setUse(src, 0);
        }

        public String toString() {
            return this.getDescriptor() + " = fneg "+ getRVal1().getType() + " " + getRVal1().getDescriptor();
        }


        public Value getRVal1() {
            return useValueList.get(0);
        }
    }

    // 比较运算, 结果是 i1 型
    public static class Fcmp extends Instr {

        public enum Op {
            OEQ("oeq"),
            OGT("ogt"),
            ONE("one"),
            OGE("oge"),
            OLT("olt"),
            OLE("ole");

            private final String name;

            private Op(final String name) {
                this.name = name;
            }

            public String getName() {
                return this.name;
            }

        }

        private final Op op;

        public Fcmp(Op op, Value op1, Value op2, BasicBlock curBB) {
            super(BasicType.getI1Type(), curBB);
            assert op1.type.isInt32Type() && op2.type.isInt32Type();
            this.op = op;
            setUse(op1, 0);
            setUse(op2, 1);
        }

        public Fcmp(Op op, Value op1, Value op2, Instr insertBefore) {
            super(BasicType.getI1Type(), insertBefore);
            assert op1.type.isInt32Type() && op2.type.isInt32Type();
            this.op = op;
            setUse(op1, 0);
            setUse(op2, 1);
        }

        public String toString() {
            return this.getDescriptor() + " = fcmp " + op.getName() + " " + getRVal1().getDescriptor() + ", " + getRVal2().getDescriptor();
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
    public static class Zext extends Instr {

        public Zext(Value src, BasicBlock parentBB) {
            super(BasicType.getI32Type(), parentBB);
            assert src.type.isInt1Type();
            setUse(src, 0);
        }

        public Zext(Value src, Instr insertBefore) {
            super(BasicType.getI32Type(), insertBefore);
            assert src.type.isInt1Type();
            setUse(src, 0);
        }

        public String toString() {
            return this.getDescriptor() + " = zext " + getRVal1().getDescriptor() + " to " + this.type;
        }

        public Value getRVal1() {
            return useValueList.get(0);
        }
    }

    public static class FPtosi extends Instr {

        public FPtosi(Value src, BasicBlock parentBB) {
            super(BasicType.getI32Type(), parentBB);
            assert src.type.isFloatType();
            setUse(src, 0);
        }

        public FPtosi(Value src, Instr insertBefore) {
            super(BasicType.getI32Type(), insertBefore);
            assert src.type.isFloatType();
            setUse(src, 0);
        }

        public String toString() {
            return this.getDescriptor() + " = fptosi " + getRVal1().getDescriptor() + " to " + this.getType();
        }


        public Value getRVal1() {
            return useValueList.get(0);
        }
    }

    public static class SItofp extends Instr {

        public SItofp(Value src, BasicBlock parentBB) {
            super(BasicType.getF32Type(), parentBB);
            assert src.type.isInt32Type();
            setUse(src, 0);
        }

        public SItofp(Value src, Instr insertBefore) {
            super(BasicType.getF32Type(), insertBefore);
            assert src.type.isInt32Type();
            setUse(src, 0);
        }

        public String toString() {
            return this.getDescriptor() + " = fptosi " + getRVal1().getDescriptor() + " to " + this.getType();
        }


        public Value getRVal1() {
            return useValueList.get(0);
        }
    }

    // 分配内存, 结果是指针型
    public static class Alloc extends Instr {

        private Type contentType;

        // Alloc一定插在基本块的开始(Phi之后)
        public Alloc(Type contentType, BasicBlock parentBB) {
            super(new PointerType(contentType), parentBB, true);
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
        public Load(Value pointer, BasicBlock parentBB) {
            super(((PointerType) pointer.getType()).getInnerType(), parentBB);
            assert type.equals(pointer.type);
            setUse(pointer, 0);
        }

        public Load(Value pointer, Instr insertBefore) {
            super(((PointerType) pointer.getType()).getInnerType(), insertBefore);
            assert type.equals(pointer.type);
            setUse(pointer, 0);
        }

        @Override
        public String toString() {
            return this.getDescriptor() + " = load " + type.toString() + ", " + getPointer().getDescriptor();
        }

        public Value getPointer() {
            return this.useValueList.get(0);
        }

    }

    // 写入内存
    // 认为Store是VoidType
    public static class Store extends Instr {

        //TODO:修改toString()方法添加指令的Type
        public Store(Value value, Value address, BasicBlock parent) {
            super(VoidType.getVoidType(), parent);
            assert ((PointerType) address.getType()).getInnerType().equals(value.type);
            setUse(value, 0);
            setUse(address, 1);
        }

        //TODO:修改toString()方法添加指令的Type
        public Store(Value value, Value address, Instr insertBefore) {
            super(value.type, insertBefore);
            assert ((PointerType) address.getType()).getInnerType().equals(value.type);
            setUse(value, 0);
            setUse(address, 1);
        }

        @Override
        public String toString() {
            return "store " + this.getValue().getDescriptor() + ", " + this.getPointer().getDescriptor();
        }

        public Value getValue() {
            return this.useValueList.get(0);
        }

        public Value getPointer() {
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

            assert base.getType() instanceof PointerType;
            this.base = base;
            this.offset = offset;
            this.array = array;
            if (array) {
                assert ((PointerType) base.getType()).getInnerType() instanceof ArrayType;
                assert ret.getType().equals(new PointerType(((ArrayType) ((PointerType) base.getType()).getInnerType()).getBase()));
            } else {
                assert ret.getType().equals(base.getType());
            }
        }

        @Override
        public String getDescriptor() {
            assert base.getType() instanceof PointerType;
            if (array) {
                return getRet().getDescriptor() + " = getelementptr inbounds " + ((PointerType) base.getType()).getInnerType() + ", " + base + ", i32 0, " + offset;
            } else {
                return getRet().getDescriptor() + " = getelementptr inbounds " + ((PointerType) base.getType()).getInnerType() + ", " + base + ", " + offset;
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

        // private final Function func;
        // private final ArrayList<Value> paramList;

        public Call(Function func, ArrayList<Value> paramList, BasicBlock parent) {
            super(func.getRetType(), parent); // ret may be null
            // func一定在前面已经定义，故此处一定可以取出来retType
            // this.func = func;
            // this.paramList = paramList;
            int i = 0;
            setUse(func, i++);
            for (Value value : paramList) {
                setUse(value, i++);
            }
        }

        public Call(Function func, ArrayList<Value> paramList, Instr insertBefore) {
            super(func.getRetType(), insertBefore); // ret may be null
            // this.func = func;
            // this.paramList = paramList;
            int i = 0;
            setUse(func, i++);
            for (Value value : paramList) {
                setUse(value, i++);
            }
        }

        @Override
        public String toString() {
            String prefix = "";
            String retType = "void";
            if (!type.isVoidType()) {
                prefix = getDescriptor() + " = ";
                retType = type.toString();
            }
            String paramList = getParamList().stream().map(Value::toString).reduce((s, s2) -> s + ", " + s2).orElse("");
            return prefix + "call " + retType + " @" + getFunc().getName() + "(" + paramList + ")";
        }

        public Function getFunc() {
            return (Function) useValueList.get(0);
        }

        public ArrayList<Value> getParamList() {
            return (ArrayList<Value>) useValueList.subList(1, useValueList.size());
        }

    }

    // SSA Phi 指令
    public static class Phi extends Instr {

        //TODO:assign to 刘传, xry已改
        private final ArrayList<Instr> optionalValues;

        public Phi(Type type, ArrayList<Instr> optionalValues, BasicBlock parent) {
            // Phi一定插在基本块的开始, Alloc之前
            super(type, parent, true);
            for (Instr instr : optionalValues) {
                assert type.equals(instr.type);
            }
            this.optionalValues = optionalValues;
            int idx = 0;
            for (Instr inst : optionalValues) {
                setUse(inst, idx++);
            }
        }

        @Override
        public String toString() {
            String src = optionalValues.stream().map(entry -> "[ " + (entry.getDescriptor()) + ", " + entry.bb.getDescriptor() + " ]").reduce((s, s2) -> s + ", " + s2).orElse("");
            // TODO: 这里的type.toString不知道是不是能直接调用到BasicType的toString方法
            return getDescriptor() + " = phi " + type.toString() + " " + src;
        }

        public ArrayList<Instr> getOptionalValues() {
            return this.optionalValues;
        }
    }

    // Terminator的type都是Void
    // 分支
    public static class Branch extends Instr implements Terminator {

        public Branch(Value cond, BasicBlock thenTarget, BasicBlock elseTarget, BasicBlock parent) {
            super(VoidType.getVoidType(), parent);
            assert cond.getType().isInt1Type();
            setUse(cond, 0);
            setUse(thenTarget, 1);
            setUse(elseTarget, 2);
        }

        @Override
        public String getDescriptor() {
            return "br " + getCond() + ", label %" + getThenTarget().getLabel() + ", label %" + getElseTarget().getLabel();
        }

        public Value getCond() {
            return useValueList.get(0);
        }

        public BasicBlock getThenTarget() {
            return (BasicBlock) useValueList.get(1);
        }

        public BasicBlock getElseTarget() {
            return (BasicBlock) useValueList.get(2);
        }

    }

    // 直接跳转
    public static class Jump extends Instr implements Terminator {

        public Jump(BasicBlock target, BasicBlock parent) {
            super(VoidType.getVoidType(), parent);
            setUse(target, 0);
        }

        @Override
        public String getDescriptor() {
            return "br label %" + getTarget().getLabel();
        }

        public BasicBlock getTarget() {
            return (BasicBlock) useValueList.get(0);
        }

    }

    // 返回
    public static class Return extends Instr implements Terminator {
        public boolean inMain = false;

        // Return 的类型是Void, 与返回值无关
        public Return(BasicBlock parent) {
            super(VoidType.getVoidType(), parent);
        }

        public Return(Value retValue, BasicBlock parent) {
            super(VoidType.getVoidType(), parent);
            assert retValue.type.equals(parent.getFunction().getRetType());
            setUse(retValue, 0);
        }

        public boolean hasValue() {
            return useValueList.isEmpty();
        }

        @Override
        public String getDescriptor() {
            if (hasValue()) {
                return "ret " + getRetValue().getDescriptor();
            } else {
                return "ret void";
            }
        }

        public Value getRetValue() {
            return useValueList.get(0);
        }

    }

}
