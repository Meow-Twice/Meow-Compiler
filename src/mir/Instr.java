package mir;

import frontend.Visitor;
import midend.CloneInfoMap;
import mir.type.Type;
import mir.type.Type.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;

import static lir.MachineInst.Tag.Mod;

/**
 * LLVM IR 的一条指令
 */
public class Instr extends Value {

    //TODO:删除reflect
    public static int LOCAL_COUNT = 0;

    public static int empty_instr_cnt = 0;

    public BasicBlock bb;

    //TODO:添加一个hash标记,是否比比较arraylist的equal方法快且保险(正确性)
    public String hash;

    private BasicBlock earliestBB;
    private BasicBlock latestBB;//在本算法中,last is best

    private boolean arrayInit = false;

    public void setEarliestBB(BasicBlock earliestBB) {
        this.earliestBB = earliestBB;
    }

    public void setLatestBB(BasicBlock latestBB) {
        this.latestBB = latestBB;
    }

    public BasicBlock getEarliestBB() {
        return earliestBB;
    }

    public BasicBlock getLatestBB() {
        return latestBB;
    }

    public BasicBlock parentBB() {
        return bb;
    }


    public void setBb(BasicBlock bb) {
        this.bb = bb;
    }

    private void removeUserUse() {
        for (Use use : useList) {
            use.remove();
        }
        useValueList.clear();
        useList.clear();
    }

    @Override
    public void remove() {
        super.remove();
        this.removeUserUse();
    }

    public void delFromNowBB() {
        super.remove();
    }

    public interface Terminator {
    }

    protected ArrayList<Use> useList;
    protected ArrayList<Value> useValueList;

    private int condCount;
    private boolean inLoopCond =false;

    public String getHash() {
        return hash;
    }

    public void setInLoopCond(){
        inLoopCond = true;
    }

    public void setNotInLoopCond() {
        inLoopCond = false;
    }

    public boolean isInLoopCond(){
        return inLoopCond;
    }

    public boolean isCond() {
        return condCount > 0;
    }

    public int getCondCount(){
        return condCount;
    }

    public void setCondCount(int condCount) {
        this.condCount = condCount;
    }

    //空指令用于在BB中做链表头/尾
    public Instr() {
        super();
        this.hash = "EMPTY_INSTR_" + (empty_instr_cnt++);
    }

    private void init() {
        inLoopCond = Visitor.VISITOR.isInLoopCond();
        condCount = Visitor.VISITOR.getCondCount();
        hash = "Instr " + LOCAL_COUNT;
        prefix = LOCAL_PREFIX;
        name = LOCAL_NAME_PREFIX + LOCAL_COUNT++;
    }

    //新建指令,但是不插入基本块,仅在消除PCopy时使用,
    //好像不需要?
    //TODO:del after review
    public Instr(Type type, BasicBlock bb, int bit) {
        super(type);
        init();
        this.bb = bb;
        useList = new ArrayList<>();
        useValueList = new ArrayList<>();
    }

    public Instr(Type type, BasicBlock curBB) {
        super(type);
        init();
        bb = curBB;
        if (!bb.isTerminated()) {
            bb.insertAtEnd(this);
        }
        useList = new ArrayList<>();
        useValueList = new ArrayList<>();
    }

    public Instr(Type type, BasicBlock curBB, boolean Lipsum) {
        super(type);
        init();
        bb = curBB;
        bb.insertAtHead(this);
        useList = new ArrayList<>();
        useValueList = new ArrayList<>();
    }

    public Instr(Type type, Instr instr) {
        super(type);
        init();
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
        // remove old used by this
        //old.remove();
        useList.get(index).remove();

        // add now
        Use use = new Use(this, now, index);
        this.useList.set(index, use);
        this.useValueList.set(index, now);
        now.insertAtEnd(use);
    }

    public void modifyUse(Value now, int index) {
        Value old = useValueList.get(index);
        //old.remove();
        useList.get(index).remove();

        Use use = new Use(this, now, index);
        this.useList.set(index, use);
        this.useValueList.set(index, now);
        now.insertAtEnd(use);
    }

    public boolean isDefInstr() {
        return !(this instanceof Alloc || this instanceof Store || this instanceof Call
                || this instanceof Branch || this instanceof Return);
    }

    public boolean isEnd() {
        return this.getNext() == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Instr instr = (Instr) o;
        return hash.equals(instr.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash);
    }

    @Override
    public String toString() {
        return this.hash;
    }

    public ArrayList<Use> getUseList() {
        return useList;
    }

    public ArrayList<Value> getUseValueList() {
        return useValueList;
    }

    public boolean check() {
        if (!(this.isAlu())) {
            return false;
        }
        //TODO:当前不是i32类型默认不可融合,后需要修改
        if (!this.getType().isInt32Type()) {
            return false;
        }
        if (!((Alu) this).isSub() && !((Alu) this).isAdd()) {
            return false;
        }
        if (!((Alu) this).hasOneConst()) {
            return false;
        }
        return true;
    }

    public void setArrayInit(boolean arrayInit) {
        this.arrayInit = arrayInit;
    }

    public boolean isArrayInit() {
        return arrayInit;
    }

    public boolean canComb() {
        if (!check()) {
            return false;
        }
        Use use = getBeginUse();
        while (use.getNext() != null) {
            Instr user = use.getUser();
            if (!user.check()) {
                return false;
            }
            use = (Use) use.getNext();
        }
        return true;
    }

    //把指令复制到指定bb
    public Instr cloneToBB(BasicBlock bb) {
        return null;
    }

    public void fix() {
        int len = useValueList.size();
        for (int i = 0; i < len; i++) {
            modifyUse(CloneInfoMap.getReflectedValue(useValueList.get(i)), i);
        }
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
            tag = AmaTag.bino;
            this.op = op;
            setUse(op1, 0);
            setUse(op2, 1);
        }

        public Alu(Type type, Op op, Value op1, Value op2, Instr insertBefore) {
            super(type, insertBefore);
            assert type.equals(op1.type) && op1.type.equals(op2.type);
            tag = AmaTag.bino;
            this.op = op;
            setUse(op1, 0);
            setUse(op2, 1);
        }

        @Override
        public String toString() {
            // assert getRVal1().getType().equals(getRVal2().getType());
            return this.getName() + " = " + op.getName() + " " + getRVal1().getDescriptor() + ", " + getRVal2().getName();
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

        public boolean isAdd() {
            return op.equals(Op.ADD);
        }

        public boolean isSub() {
            return op.equals(Op.SUB);
        }

        public boolean hasOneConst() {
            if (useValueList.get(0) instanceof Constant && useValueList.get(1) instanceof Constant) {
                return false;
            }
            return useValueList.get(0) instanceof Constant || useValueList.get(1) instanceof Constant;
        }

        public boolean hasTwoConst() {
            return useValueList.get(0) instanceof Constant && useValueList.get(1) instanceof Constant;
        }

        @Override
        public Instr cloneToBB(BasicBlock bb) {
            //Instr ret = new Alu(getType(), getOp(), CloneInfoMap.getReflectedValue(getRVal1()),
            //        CloneInfoMap.getReflectedValue(getRVal2()), bb);
            Instr ret = new Alu(getType(), getOp(), getRVal1(), getRVal2(), bb);
            CloneInfoMap.addValueReflect(this, ret);
            return ret;
        }
    }

    // 比较运算, 结果是 i1 型
    public static class Icmp extends Instr {

        public enum Op {
            // TODO: 保证Arm.Cond与Icmp.Op, Fcmp.Op的顺序相同!!!!!!!!
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

        private Op op;

        public Icmp(Op op, Value op1, Value op2, BasicBlock curBB) {
            super(BasicType.getI1Type(), curBB);
            assert op1.type.isInt32Type() && op2.type.isInt32Type();
            this.op = op;
            tag = AmaTag.icmp;
            setUse(op1, 0);
            setUse(op2, 1);
        }

        public Icmp(Op op, Value op1, Value op2, Instr insertBefore) {
            super(BasicType.getI1Type(), insertBefore);
            assert op1.type.isInt32Type() && op2.type.isInt32Type();
            this.op = op;
            tag = AmaTag.icmp;
            setUse(op1, 0);
            setUse(op2, 1);
        }

        public String toString() {
            // assert getRVal1().getType().equals(getRVal2().getType());
            return this.getName() + " = icmp " + op.getName() + " " + getRVal1().getDescriptor() + ", " + getRVal2().getName();
        }

        public Op getOp() {
            return this.op;
        }

        public Op setOp(Op op) {
            return this.op = op;
        }

        public Value getRVal1() {
            return useValueList.get(0);
        }

        public Value getRVal2() {
            return useValueList.get(1);
        }

        @Override
        public Instr cloneToBB(BasicBlock bb) {
//            Instr ret =  new Icmp(getOp(), CloneInfoMap.getReflectedValue(getRVal1()),
//                    CloneInfoMap.getReflectedValue(getRVal2()), bb);
            Instr ret = new Icmp(getOp(), getRVal1(), getRVal2(), bb);
            CloneInfoMap.addValueReflect(this, ret);
            return ret;
        }
    }

    public static class Fneg extends Instr {

        public Fneg(Value src, BasicBlock parentBB) {
            super(BasicType.getF32Type(), parentBB);
            assert src.type.isFloatType();
            tag = AmaTag.fneg;
            setUse(src, 0);
        }

        public Fneg(Value src, Instr insertBefore) {
            super(BasicType.getF32Type(), insertBefore);
            assert src.type.isFloatType();
            tag = AmaTag.fneg;
            setUse(src, 0);
        }

        public String toString() {
            return this.getName() + " = fneg " + getRVal1().getDescriptor();
        }


        public Value getRVal1() {
            return useValueList.get(0);
        }

        //TODO:当前构造方法没有使用,当需要使用的时候,需要重写cloneToBB方法
    }

    // 比较运算, 结果是 i1 型
    public static class Fcmp extends Instr {

        public enum Op {
            // TODO: 保证Arm.Cond与Icmp.Op, Fcmp.Op的顺序相同!!!!!!!!
            OEQ("oeq"),
            ONE("one"),
            OGT("ogt"),
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
            assert op1.type.isFloatType() && op2.type.isFloatType();
            this.op = op;
            tag = AmaTag.fcmp;
            setUse(op1, 0);
            setUse(op2, 1);
        }

        public Fcmp(Op op, Value op1, Value op2, Instr insertBefore) {
            super(BasicType.getI1Type(), insertBefore);
            assert op1.type.isFloatType() && op2.type.isFloatType();
            this.op = op;
            tag = AmaTag.fcmp;
            setUse(op1, 0);
            setUse(op2, 1);
        }

        public String toString() {
            // assert getRVal1().getType().equals(getRVal2().getType());
            return this.getName() + " = fcmp " + op.getName() + " " + getRVal1().getDescriptor() + ", " + getRVal2().getName();
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

        @Override
        public Instr cloneToBB(BasicBlock bb) {
//            Instr ret =  new Fcmp(getOp(), CloneInfoMap.getReflectedValue(getRVal1()),
//                    CloneInfoMap.getReflectedValue(getRVal2()), bb);
            Instr ret = new Fcmp(getOp(), getRVal1(), getRVal2(), bb);
            CloneInfoMap.addValueReflect(this, ret);
            return ret;
        }
    }

    // 零扩展运算, 结果是 i32 型
    public static class Zext extends Instr {

        public Zext(Value src, BasicBlock parentBB) {
            super(BasicType.getI32Type(), parentBB);
            assert src.type.isInt1Type();
            tag = AmaTag.zext;
            setUse(src, 0);
        }

        public Zext(Value src, Instr insertBefore) {
            super(BasicType.getI32Type(), insertBefore);
            assert src.type.isInt1Type();
            tag = AmaTag.zext;
            setUse(src, 0);
        }

        public String toString() {
            return this.getName() + " = zext " + getRVal1().getDescriptor() + " to " + this.type;
        }

        public Value getRVal1() {
            return useValueList.get(0);
        }

        @Override
        public Instr cloneToBB(BasicBlock bb) {
            //Instr ret =  new Zext(CloneInfoMap.getReflectedValue(getRVal1()), bb);
            Instr ret = new Zext(getRVal1(), bb);
            CloneInfoMap.addValueReflect(this, ret);
            return ret;
        }
    }

    public static class FPtosi extends Instr {

        public FPtosi(Value src, BasicBlock parentBB) {
            super(BasicType.getI32Type(), parentBB);
            tag = AmaTag.fptosi;
            assert src.type.isFloatType();
            setUse(src, 0);
        }

        public FPtosi(Value src, Instr insertBefore) {
            super(BasicType.getI32Type(), insertBefore);
            tag = AmaTag.fptosi;
            assert src.type.isFloatType();
            setUse(src, 0);
        }

        public String toString() {
            return this.getName() + " = fptosi " + getRVal1().getDescriptor() + " to " + this.getType();
        }


        public Value getRVal1() {
            return useValueList.get(0);
        }

        @Override
        public Instr cloneToBB(BasicBlock bb) {
            //Instr ret = new FPtosi(CloneInfoMap.getReflectedValue(getRVal1()), bb);
            Instr ret = new FPtosi(getRVal1(), bb);
            CloneInfoMap.addValueReflect(this, ret);
            return ret;
        }
    }

    public static class SItofp extends Instr {

        public SItofp(Value src, BasicBlock parentBB) {
            super(BasicType.getF32Type(), parentBB);
            assert src.type.isInt32Type();
            tag = AmaTag.sitofp;
            setUse(src, 0);
        }

        public SItofp(Value src, Instr insertBefore) {
            super(BasicType.getF32Type(), insertBefore);
            assert src.type.isInt32Type();
            tag = AmaTag.sitofp;
            setUse(src, 0);
        }

        public String toString() {
            return this.getName() + " = sitofp " + getRVal1().getDescriptor() + " to " + this.getType();
        }


        public Value getRVal1() {
            return useValueList.get(0);
        }

        @Override
        public Instr cloneToBB(BasicBlock bb) {
            //Instr ret = new SItofp(CloneInfoMap.getReflectedValue(getRVal1()), bb);
            Instr ret = new SItofp(getRVal1(), bb);
            CloneInfoMap.addValueReflect(this, ret);
            return ret;
        }
    }

    // 分配内存, 结果是指针型
    public static class Alloc extends Instr {

        private Type contentType;
        private HashSet<Instr> loads = new HashSet<>();

        // Alloc一定插在基本块的开始(Phi之后)
        public Alloc(Type contentType, BasicBlock parentBB) {
            super(new PointerType(contentType), parentBB, true);
            tag = AmaTag.alloc;
            this.contentType = contentType;
        }

        //不自动插入到基本块的ALLOC
        public Alloc(Type contentType, BasicBlock parentBB, boolean Lipsum) {
            super(new PointerType(contentType), parentBB);
            tag = AmaTag.alloc;
            this.contentType = contentType;
        }

        public void setLoads(HashSet<Instr> loads) {
            this.loads = loads;
        }

        public void clearLoads() {
            this.loads.clear();
        }

        public HashSet<Instr> getLoads() {
            return loads;
        }

        public void addLoad(Instr instr) {
            loads.add(instr);
        }

        @Override
        public String toString() {
            return this.getName() + " = alloca " + contentType;
        }

        public Type getContentType() {
            return this.contentType;
        }

        public boolean isArrayAlloc() {
            return contentType instanceof ArrayType;
        }

        @Override
        public Instr cloneToBB(BasicBlock bb) {
            Instr ret = new Alloc(contentType, bb);
            CloneInfoMap.addValueReflect(this, ret);
            return ret;
        }
    }

    // 读取内存
    public static class Load extends Instr {

        private Value alloc = null;
        private Instr useStore = null;

        public void setUseStore(Instr useStore) {
            this.useStore = useStore;
        }

        public Instr getUseStore() {
            return useStore;
        }

        public void clear() {
            alloc = null;
            useStore = null;
        }

        public void setAlloc(Value alloc) {
            this.alloc = alloc;
        }

        public Value getAlloc() {
            return alloc;
        }

        //TODO:修改toString()方法添加指令的Type
        public Load(Value pointer, BasicBlock parentBB) {
            super(((PointerType) pointer.getType()).getInnerType(), parentBB);
            tag = AmaTag.load;
            setUse(pointer, 0);
        }

        public Load(Value pointer, Instr insertBefore) {
            super(((PointerType) pointer.getType()).getInnerType(), insertBefore);
            tag = AmaTag.load;
            setUse(pointer, 0);
        }

        @Override
        public String toString() {
            return this.getName() + " = load " + type.toString() + ", " + getPointer().getDescriptor();
        }

        public Value getPointer() {
            return this.useValueList.get(0);
        }

        @Override
        public Instr cloneToBB(BasicBlock bb) {
            //Instr ret = new Load(CloneInfoMap.getReflectedValue(getPointer()), bb);
            Instr ret = new Load(getPointer(), bb);
            CloneInfoMap.addValueReflect(this, ret);
            return ret;
        }
    }

    // 写入内存
    // 认为Store是VoidType
    public static class Store extends Instr {

        private Value alloc = null;
        private HashSet<Instr> users = new HashSet<>();

        public HashSet<Instr> getUsers() {
            return users;
        }

        public void addUser(Instr user) {
            users.add(user);
        }

        public void setAlloc(Value alloc) {
            this.alloc = alloc;
        }

        public Value getAlloc() {
            return alloc;
        }

        public void clear() {
            alloc = null;
            users.clear();
        }

        //TODO:修改toString()方法添加指令的Type
        public Store(Value value, Value address, BasicBlock parent) {
            super(VoidType.getVoidType(), parent);
            assert ((PointerType) address.getType()).getInnerType().equals(value.type);
            tag = AmaTag.store;
            setUse(value, 0);
            setUse(address, 1);
        }

        //TODO:修改toString()方法添加指令的Type
        public Store(Value value, Value address, Instr insertBefore) {
            super(value.type, insertBefore);
            assert ((PointerType) address.getType()).getInnerType().equals(value.type);
            tag = AmaTag.store;
            setUse(value, 0);
            setUse(address, 1);
        }

        @Override
        public String toString() {
            return "store " + getValue().getDescriptor() + ", " + getPointer().getDescriptor();
        }

        public Value getValue() {
            return this.useValueList.get(0);
        }

        public Value getPointer() {
            return this.useValueList.get(1);
        }

        @Override
        public Instr cloneToBB(BasicBlock bb) {
//            Instr ret = new Store(CloneInfoMap.getReflectedValue(getValue()),
//                    CloneInfoMap.getReflectedValue(getPointer()), bb);
            Instr ret = new Store(getValue(), getPointer(), bb);
            CloneInfoMap.addValueReflect(this, ret);
            return ret;
        }
    }

    // 数组元素寻址, 每个该指令对指针/数组解引用一层, 返回值含义等价于 `base[offset]`
    public static class GetElementPtr extends Instr {

        // e.g. a is [10 x i32]*: a[3] -> %v1 = getelementptr inbounds [10 x i32], [10 x i32]* %a, i32 0, i32 3 ; 第一个 i32 0 表示解引用前不偏移(固定)，第二个才是解引用后的偏移
        // e.g. a is i32*: a[3] -> %v1 = getelementptr inbounds i32, i32* %a, i32 3
        public GetElementPtr(Type pointeeType, Value ptr, ArrayList<Value> idxList, BasicBlock basicBlock) {
            super(new PointerType(pointeeType), basicBlock);
            tag = AmaTag.gep;
            setUse(ptr, 0);
            int i = 1;
            for (Value idxValue : idxList) {
                setUse(idxValue, i++);
            }
        }

        @Override
        public String toString() {
            StringBuilder strBD = new StringBuilder();
            strBD.append(getName()).append(" = getelementptr inbounds ").
                    append(((PointerType) getPtr().getType()).getInnerType()).append(", ").append(getPtr().getType()).append(" ").append(getPtr().getName());
            for (Value idxValue : getIdxList()) {
                strBD.append(", ").append(idxValue.getType()).append(" ").append(idxValue.getName());
            }
            return strBD.toString();
        }

        public Type getPointeeType() {
            return ((PointerType) type).getInnerType();
        }

        public Value getPtr() {
            return useValueList.get(0);
        }

        public ArrayList<Value> getIdxList() {
            return new ArrayList<>(useValueList.subList(1, useValueList.size()));
        }

        public void addIdx(Value value) {
            int len = useValueList.size();
            setUse(value, len);
        }

        public int getOffsetCount() {
            return useValueList.size() - 1;
        }

        public Value getIdxValueOf(int i) {
            if (i + 1 >= useValueList.size()) {
                throw new AssertionError("Out of gep idx : " + i + 1 + "\t" + this);
            }
            return useValueList.get(i + 1);
        }

        public void modifyType(Type type) {
            this.type = type;
        }

        public void modifyPtr(Value ptr) {
            modifyUse(ptr, 0);
        }

        public void modifyIndexs(ArrayList<Value> indexs) {
            for (int i = 0; i < getIdxList().size(); i++) {
                modifyUse(indexs.get(i), i + 1);
            }
            for (int i = getIdxList().size(); i < indexs.size(); i++) {
                setUse(indexs.get(i), i + 1);
            }
        }

        @Override
        public Instr cloneToBB(BasicBlock bb) {
            Instr ret = new GetElementPtr(getPointeeType(), getPtr(), getIdxList(), bb);
            CloneInfoMap.addValueReflect(this, ret);
            return ret;
        }
    }

    public static class Bitcast extends Instr {

        public Bitcast(Value srcValue, Type dstType, BasicBlock parent) {
            super(dstType, parent);
            tag = AmaTag.bitcast;
            setUse(srcValue, 0);
        }

        public Value getSrcValue() {
            return useValueList.get(0);
        }

        @Override
        public String toString() {
            return getName() + " = bitcast " + getSrcValue().getDescriptor() + " to " + getType();
        }

        public Type getDstType() {
            return getType();
        }

        @Override
        public Instr cloneToBB(BasicBlock bb) {
            //Instr ret = new Bitcast(CloneInfoMap.getReflectedValue(getSrcValue()), getDstType(), bb);
            Instr ret = new Bitcast(getSrcValue(), getDstType(), bb);
            CloneInfoMap.addValueReflect(this, ret);
            return ret;
        }
    }

    // 函数调用
    public static class Call extends Instr {

        //TODO:考虑函数调用的user used

        // private final Function func;
        // private final ArrayList<Value> paramList;

        public Call(Function func, ArrayList<Value> paramList, BasicBlock parent) {
            super(func.getRetType(), parent); // ret may be null
            tag = AmaTag.call;
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
            tag = AmaTag.call;
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
                prefix = getName() + " = ";
                retType = type.toString();
            }
            String paramList = getParamList().stream().map(Value::getDescriptor).reduce((s, s2) -> s + ", " + s2).orElse("");
            return prefix + "call " + retType + " @" + getFunc().getName() + "(" + paramList + ")";
        }

        public Function getFunc() {
            return (Function) useValueList.get(0);
        }

        public ArrayList<Value> getParamList() {
            return new ArrayList<>(useValueList.subList(1, useValueList.size()));
        }

        @Override
        public Instr cloneToBB(BasicBlock bb) {
            ArrayList<Value> params = new ArrayList<>();
            params.addAll(getParamList());
            Instr ret = new Call(getFunc(), params, bb);
            CloneInfoMap.addValueReflect(this, ret);
            return ret;
        }
    }

    // SSA Phi 指令
    public static class Phi extends Instr {

        //TODO:assign to 刘传, xry已改
        //private final ArrayList<Value> optionalValues;
        private boolean isLCSSA;

        public Phi(Type type, ArrayList<Value> optionalValues, BasicBlock parent) {
            // Phi一定插在基本块的开始, Alloc之前
            super(type, parent, true);
            tag = AmaTag.phi;
//            for (Value instr : optionalValues) {
//                assert type.equals(instr.type);
//            }
            //this.optionalValues = optionalValues;
            int idx = 0;
            for (Value inst : optionalValues) {
                setUse(inst, idx++);
            }
        }

        //LCSSA_PHI
        public Phi(Type type, ArrayList<Value> optionalValues, BasicBlock parent, boolean isLCSSA) {
            super(type, parent, true);
            tag = AmaTag.phi;
//            for (Value instr : optionalValues) {
//                assert type.equals(instr.type);
//            }
            //this.optionalValues = optionalValues;
            int idx = 0;
            for (Value inst : optionalValues) {
                setUse(inst, idx++);
            }
//            Instr instr = parent.getBeginInstr();
//            while (instr instanceof Phi) {
//                instr = (Instr) instr.getNext();
//            }
//            instr.insertBefore(this);
            this.isLCSSA = isLCSSA;
        }

        public boolean isLCSSA() {
            return isLCSSA;
        }

        //        @Override
//        public String toString() {
//            String src = optionalValues.stream().map(entry -> "[ " + (entry.getName()) + ", " + entry.bb.getName() + " ]").reduce((s, s2) -> s + ", " + s2).orElse("");
//            // TODO: 这里的type.toString不知道是不是能直接调用到BasicType的toString方法
//            return getName() + " = phi " + type.toString() + " " + src;
//        }


        @Override
        public String toString() {
            StringBuilder ret = new StringBuilder(getName() + " = phi " + type.toString() + " ");
            int len = useValueList.size();
            for (int i = 0; i < len; i++) {
                Value value = useValueList.get(i);
//                if (value instanceof Constant) {
                if (parentBB().getPrecBBs().size() <= i) {
                    //System.err.println("err_966");
                    return "err";
                }
                ret.append("[ ").append(value.getName()).append(", %").append(parentBB().getPrecBBs().get(i).getLabel()).append(" ]");
//                } else if (value instanceof Instr) {
//                    ret += "[ " + value.getName() + ", %" + ((Instr) value).parentBB() + " ]";
//                } else {
//                    System.err.println("panic when phi to string");
//                }
                if (i < len - 1) {
                    ret.append(", ");
                }
            }
            return ret.toString();
        }

        public ArrayList<Value> getOptionalValues() {
            return useValueList;
        }

        public int getValueIndexInUseValueList(Value value) {
            assert useValueList.contains(value);
            return useValueList.indexOf(value);
        }

        public void addOptionalValue(Value value) {
            int index = useValueList.size();
            setUse(value, index);
        }

        @Override
        public Instr cloneToBB(BasicBlock bb) {
            ArrayList<Value> optionalValues = new ArrayList<>();
            optionalValues.addAll(getOptionalValues());
            Instr ret = new Phi(getType(), optionalValues, bb);
            CloneInfoMap.addValueReflect(this, ret);
            return ret;
        }

        public void simple(ArrayList<BasicBlock> oldPres, ArrayList<BasicBlock> newPres) {
            ArrayList<Value> values = new ArrayList<>();
//            for (int i = 0; i < oldPres.size(); i++) {
//                if (!newPres.contains(oldPres.get(i))) {
//                    continue;
//                }
//                values.add(useValueList.get(i));
//            }
            for (int i = 0; i < newPres.size(); i++) {
                int index = oldPres.indexOf(newPres.get(i));
                assert index != -1;
                values.add(useValueList.get(index));
            }
            for (Use use : useList) {
                use.remove();
            }
            useList.clear();
            useValueList.clear();
            int index = 0;
            for (Value value : values) {
                setUse(value, index++);
            }
        }
    }

    public static class PCopy extends Instr {
        private ArrayList<Value> LHS;
        private ArrayList<Value> RHS;

        public PCopy(ArrayList<Value> LHS, ArrayList<Value> RHS, BasicBlock parent) {
            super(Type.getVoidType(), parent);
            tag = AmaTag.pcopy;
            this.LHS = LHS;
            this.RHS = RHS;
        }

        @Override
        public String toString() {
            StringBuilder ret = new StringBuilder("PCopy ");
            int len = RHS.size();
            for (int i = 0; i < len; i++) {
                ret.append(LHS.get(i).getName()).append(" <-- ").append(RHS.get(i).getName());
                if (i < len - 1) {
                    ret.append(", ");
                }
            }
            return ret.toString();
        }

        public void addToPC(Value tag, Value src) {
            this.LHS.add(tag);
            this.RHS.add(src);
        }

        public ArrayList<Value> getLHS() {
            return LHS;
        }

        public ArrayList<Value> getRHS() {
            return RHS;
        }
    }

    public static class Move extends Instr {
        private Value src;
        private Value dst;

        public Move(Type type, Value dst, Value src, BasicBlock parent) {
            super(type, parent);
            tag = AmaTag.move;
            this.dst = dst;
            this.src = src;
        }

        @Override
        public String toString() {
            String ret = "move ";
            ret += type.toString() + " " + src.getName() + " --> " + dst.getName();
            return ret;
        }

        public Value getSrc() {
            return src;
        }

        public Value getDst() {
            return dst;
        }
    }

    // Terminator的type都是Void
    // 分支
    public static class Branch extends Instr implements Terminator {

        private BasicBlock thenTarget;
        private BasicBlock elseTarget;

        public Branch(Value cond, BasicBlock thenTarget, BasicBlock elseTarget, BasicBlock parent) {
            super(VoidType.getVoidType(), parent);
            assert cond.getType().isInt1Type();
            tag = AmaTag.branch;
            setUse(cond, 0);
            setUse(thenTarget, 1);
            setUse(elseTarget, 2);

            this.thenTarget = thenTarget;
            this.elseTarget = elseTarget;
        }

        @Override
        public String toString() {
            return "br " + getCond().getDescriptor() + ", label %" + getThenTarget().getLabel() + ", label %" + getElseTarget().getLabel();
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

        public void setThenTarget(BasicBlock thenTarget) {
            this.thenTarget = thenTarget;
            modifyUse(thenTarget, 1);
        }

        public void setElseTarget(BasicBlock elseTarget) {
            this.elseTarget = elseTarget;
            modifyUse(elseTarget, 2);
        }

        @Override
        public Instr cloneToBB(BasicBlock bb) {
            Instr ret = new Branch(getCond(), getThenTarget(), getElseTarget(), bb);
            CloneInfoMap.addValueReflect(this, ret);
            return ret;
        }
    }

    // 直接跳转
    public static class Jump extends Instr implements Terminator {

        public Jump(BasicBlock target, BasicBlock parent) {
            super(VoidType.getVoidType(), parent);
            tag = AmaTag.jump;
            setUse(target, 0);
        }

        // @Override
        // public String getDescriptor() {
        //     return "br label %" + (useValueList == null ? "null" :getTarget().getLabel());
        //     // return "br label %" + getTarget().getLabel();
        // }

        @Override
        public String toString() {
            return "br label %" + getTarget().getLabel();
        }

        public BasicBlock getTarget() {
            return (BasicBlock) useValueList.get(0);
        }

        @Override
        public Instr cloneToBB(BasicBlock bb) {
            Instr ret = new Jump(getTarget(), bb);
            CloneInfoMap.addValueReflect(this, ret);
            return ret;
        }
    }

    // 返回
    public static class Return extends Instr implements Terminator {
        public boolean inMain = false;

        // Return 的类型是Void, 与返回值无关
        public Return(BasicBlock parent) {
            super(VoidType.getVoidType(), parent);
            tag = AmaTag.ret;
        }

        public Return(Value retValue, BasicBlock parent) {
            super(retValue.getType(), parent);
            tag = AmaTag.ret;
            //fixme:当函数内联的时候i,需要复制ret指令到另一个函数,当这两个函数的返回值不一样的时候,会出现问题,
            // 故注释掉
            //assert retValue.type.equals(parent.getFunction().getRetType());
            setUse(retValue, 0);
        }

        public boolean hasValue() {
            return !useValueList.isEmpty();
        }

        @Override
        public String toString() {
            if (hasValue()) {
                return "ret " + getRetValue().getDescriptor();
            } else {
                return "ret void";
            }
        }

        public Value getRetValue() {
            return useValueList.get(0);
        }

        @Override
        public Instr cloneToBB(BasicBlock bb) {
            Instr ret = null;
            if (hasValue()) {
                ret = new Return(getRetValue(), bb);
            } else {
                ret = new Return(bb);
            }
            CloneInfoMap.addValueReflect(this, ret);
            return ret;
        }
    }

}
