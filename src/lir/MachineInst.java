package lir;

import backend.CodeGen;
import mir.Instr;
import util.ILinkNode;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.EnumMap;

import static mir.Instr.Alu.Op.*;

public class MachineInst extends ILinkNode {
    protected Arm.Cond cond = Arm.Cond.Any;
    protected Arm.Shift shift = Arm.Shift.NONE_SHIFT;
    // private boolean needFix = false;

    public boolean isNeedFix() {
        return fixType != CodeGen.STACK_FIX.NO_NEED;
    }

    public Machine.McFunction getCallee() {
        return callee;
    }

    public void setCallee(Machine.McFunction callee) {
        this.callee = callee;
    }

    private Machine.McFunction callee = null;
    private CodeGen.STACK_FIX fixType = CodeGen.STACK_FIX.NO_NEED;

    /**
     * main函数刚进有一个sp自减, 不过这时候自减的偏移一定是0
     * dealParam时, 刚开始对一个函数进行CodeGen时进行超过四个之外的参数时的load
     * return之前要对sp进行加操作
     */
    public void setNeedFix(CodeGen.STACK_FIX stack_fix) {
        Machine.Program.PROGRAM.needFixList.add((I) this);
        fixType = stack_fix;
    }

    /**
     * 调用一个非库的函数之前需要sp偏移
     */
    public void setNeedFix(Machine.McFunction callee, CodeGen.STACK_FIX stack_fix) {
        setCallee(callee);
        setNeedFix(stack_fix);
    }

    public void setUse(int i, Machine.Operand set) {
        useOpds.set(i, set);
    }

    public Tag getTag() {
        return tag;
    }

    public Arm.Cond getCond() {
        return cond;
    }

    public Arm.Shift getShift() {
        return shift;
    }

    public boolean isComment() {
        return tag == Tag.Comment;
    }

    public void setDef(Machine.Operand operand) {
        defOpds.set(0, operand);
    }

    public CodeGen.STACK_FIX getFixType() {
        return fixType;
    }

    public void clearNeedFix() {
        fixType = CodeGen.STACK_FIX.NO_NEED;
    }

    public boolean isOf(Tag... tags) {
        for (Tag tag : tags) {
            if (this.tag == tag) return true;
        }
        return false;
    }

    public boolean isOf(Tag tag) {
        return (this.tag == tag);
    }

    public boolean isNoCond() {
        return cond == Arm.Cond.Any;
    }

    public enum Tag {
        // Binary
        Add("add"),
        FAdd("vadd.f32"),
        Sub("sub"),
        FSub("vsub.f32"),
        Rsb("rsb"),
        Mul("mul"),
        FMul("vmul.f32"),
        Div("sdiv"),
        FDiv("vdiv.f32"),
        Mod("!Mod"),
        FMod("!Fmod"),
        Lt("lt"),
        Le("le"),
        Ge("ge"),
        Gt("gt"),
        Eq("eq"),
        Ne("ne"),
        And("!And"),
        FAnd("!Fand"),
        Or("!Or"),
        FOr("!FOr"),
        LongMul("smmul"),
        FMA("!Fma"),
        IMov("mov"),
        VMov("!Vmov"),
        VCvt("!Vcvt"),
        VNeg("vneg.f32"),
        Branch("b"),
        Jump("b"),
        IRet("bx"),  // Control flow
        VRet("bx"),
        Ldr("ldr"),
        VLdr("vldr.f32"),
        Str("str"),  // Memory
        VStr("vstr.f32"),
        ICmp("cmp"),
        VCmp("!Vcmp"),
        Call("bl"),
        Global("@g?"),
        Push("push"),
        Pop("pop"),
        VPush("vpush"),
        VPop("vpop"),
        Comment("@"),   // for printing comments
        Empty("!?");

        private final String name;
        public static final EnumMap<Instr.Alu.Op, Tag> map = new EnumMap<>(Instr.Alu.Op.class);

        static {
            map.put(ADD, Tag.Add);
            map.put(FADD, Tag.FAdd);
            map.put(SUB, Tag.Sub);
            map.put(FSUB, Tag.FSub);
            map.put(MUL, Tag.Mul);
            map.put(FMUL, Tag.FMul);
            map.put(DIV, Tag.Div);
            map.put(FDIV, Tag.FDiv);
            map.put(REM, Tag.Mod);
            map.put(FREM, Tag.FMod);
            map.put(AND, Tag.FAnd);
            map.put(OR, Tag.FOr);
        }

        Tag(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public boolean isMove() {
        return tag == Tag.IMov;
    }

    public boolean isVMov() {
        return tag == Tag.VMov;
    }

    public boolean isCall() {
        return tag == Tag.Call;
    }

    public boolean isReturn() {
        return tag == Tag.IRet || tag == Tag.VRet;
    }

    // public boolean isActuallyBino() {
    //     return tag.ordinal() < Tag.FMA.ordinal();
    // }

    public boolean isBranch() {
        return tag == Tag.Branch;
    }

    Machine.Block mb;

    public Machine.Block getMb() {
        return mb;
    }

    Tag tag;
    public ArrayList<Machine.Operand> defOpds = new ArrayList<>();
    public ArrayList<Machine.Operand> useOpds = new ArrayList<>();

    /*
    init and insert at end of the bb
    */
    public MachineInst(Tag tag, Machine.Block mb) {
        this.mb = mb;
        this.tag = tag;
        mb.insertAtEnd(this);
    }

    /*
    init and inset before inst
    */
    public MachineInst(Tag tag, MachineInst inst) {
        this.mb = inst.mb;
        this.tag = tag;
        inst.insertBefore(this);
    }

    /**
     * insertAfter -> this
     * 目前给MIStore插入一个指令后面时专用
     *
     * @param tag
     */
    public MachineInst(MachineInst insertAfter, Tag tag) {
        this.mb = insertAfter.mb;
        this.tag = tag;
        insertAfter.insertAfter(this);
    }

    public MachineInst() {
        this.tag = Tag.Empty;
    }

    public void genDefUse() {
    }

    public void output(PrintStream os, Machine.McFunction f) {
    }

    public interface Compare {
    }


    public interface MachineMemInst {
        Machine.Operand getData();

        Machine.Operand getAddr();

        Machine.Operand getOffset();

        void remove();
    }

    public interface MachineMove {

        Machine.Operand getDst();

        Machine.Operand getSrc();

        void remove();

    }
}



