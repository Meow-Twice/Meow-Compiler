package lir;

import backend.CodeGen;
import mir.Instr;
import mir.type.DataType;
import util.ILinkNode;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.EnumMap;

import static mir.Instr.Alu.Op.*;
import static mir.type.DataType.F32;
import static mir.type.DataType.I32;

public class MachineInst extends ILinkNode implements Cloneable {
    public final static MachineInst emptyInst = new MachineInst();
    public MachineInst theLastUserOfDef = null;
    protected Arm.Cond cond = Arm.Cond.Any;
    protected Arm.Shift shift = Arm.Shift.NONE_SHIFT;
    // private boolean needFix = false;

    public boolean isNeedFix() {
        return fixType != CodeGen.STACK_FIX.NO_NEED;
    }

    public MC.McFunction getCallee() {
        return callee;
    }

    public void setCallee(MC.McFunction callee) {
        this.callee = callee;
    }

    private MC.McFunction callee = null;
    private CodeGen.STACK_FIX fixType = CodeGen.STACK_FIX.NO_NEED;

    /**
     * main函数刚进有一个sp自减, 不过这时候自减的偏移一定是0
     * dealParam时, 刚开始对一个函数进行CodeGen时进行超过四个之外的参数时的load
     * return之前要对sp进行加操作
     */
    public void setNeedFix(CodeGen.STACK_FIX stack_fix) {
        MC.Program.PROGRAM.needFixList.add((I) this);
        fixType = stack_fix;
    }

    /**
     * 调用一个非库的函数之前需要sp偏移
     */
    public void setNeedFix(MC.McFunction callee, CodeGen.STACK_FIX stack_fix) {
        setCallee(callee);
        setNeedFix(stack_fix);
    }

    public void setUse(int i, MC.Operand set) {
        useOpds.set(i, set);
    }

    public Tag getTag() {
        return tag;
    }

    public Arm.Cond getCond() {
        return cond;
    }

    public void setCond(Arm.Cond cond) {
        this.cond = cond;
    }

    public void calCost() {
    }

    public Arm.Shift getShift() {
        return shift;
    }

    public boolean isComment() {
        return tag == Tag.Comment;
    }

    public void setDef(MC.Operand operand) {
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

    public boolean isJump() {
        return tag == Tag.Jump;
    }

    private Boolean isSideEff = null;

    public boolean sideEff() {
        if (isSideEff == null) {
            isSideEff = (tag == Tag.Branch || tag == Tag.Jump
                    || tag == Tag.Str || tag == Tag.VStr
                    || tag == Tag.Ldr || tag == Tag.VLdr
                    || tag == Tag.VCvt
                    || tag == Tag.Call
                    || tag == Tag.IRet || tag == Tag.VRet
                    || tag == Tag.Comment);
        }
        return isSideEff;
    }

    public boolean noShift() {
        return shift.noShift();
    }

    public boolean lastUserIsNext() {
        if (theLastUserOfDef == null) return false;
        // if (this.getNext().equals(this.mb.miList.tail)) return false;
        return theLastUserOfDef.equals(this.getNext());
    }

    public String getSTB() {
        return this.toString();
    }

    public boolean isNotLastInst() {
        return !getNext().equals(mb.miList.tail);
    }

    public void addShift(Arm.Shift shift) {
        // 不可能出现shift.getShiftOpd()和this.shift.getShiftOpd不一致的情况
        if (this.shift.shiftOpd.type != MC.Operand.Type.FConst && this.shift.shiftOpd.type != MC.Operand.Type.Immediate) {
            this.useOpds.add(shift.getShiftOpd());
        }
        this.shift = new Arm.Shift(shift.shiftType, shift.getShiftOpd());
    }

    public boolean noShiftAndCond() {
        return shift.noShift() && cond == Arm.Cond.Any;
    }

    public boolean hasCond() {
        return cond != Arm.Cond.Any;
    }

    public void setNext(MachineInst mi) {
        this.mb = mi.mb;
        mi.mb.miList.insertBefore(this, mi);
    }

    @Override
    public MachineInst clone() {
        try {
            return (MachineInst) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
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
        And("and"),
        FAnd("!Fand"),
        Or("or"),
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
        Swi("swi"),
        Wait("wait"),
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

    public boolean isIMov() {
        return tag == Tag.IMov;
    }

    public boolean isVMov() {
        return tag == Tag.VMov;
    }

    public boolean isMovOfDataType(DataType dataType) {
        return (dataType == I32 && tag == Tag.IMov) || (dataType == F32 && tag == Tag.VMov);
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

    MC.Block mb;

    public MC.Block getMb() {
        return mb;
    }

    Tag tag;
    public ArrayList<MC.Operand> defOpds = new ArrayList<>();
    public ArrayList<MC.Operand> useOpds = new ArrayList<>();

    int hash = 0;
    static int cnt = 0;

    /*
    init and insert at end of the bb
    */
    public MachineInst(Tag tag, MC.Block mb) {
        this.mb = mb;
        this.tag = tag;
        this.hash = cnt++;
        mb.insertAtEnd(this);
    }

    /*
    init and inset before inst
    */
    public MachineInst(Tag tag, MachineInst inst) {
        this.mb = inst.mb;
        this.tag = tag;
        this.hash = cnt++;
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
        this.hash = cnt++;
        insertAfter.insertAfter(this);
    }

    public MachineInst() {
        this.tag = Tag.Empty;
    }

    public void genDefUse() {
    }

    public void output(PrintStream os, MC.McFunction f) {
    }

    public interface Compare {
        void setCond(Arm.Cond cond);
    }


    public interface MachineMemInst {
        MC.Operand getData();

        MC.Operand getAddr();

        MC.Operand getOffset();

        void remove();

        boolean isNoCond();

        Arm.Shift getShift();

        void setAddr(MC.Operand lOpd);

        void setOffSet(MC.Operand rOpd);

        void addShift(Arm.Shift shift);

        Arm.Cond getCond();
    }

    public interface MachineMove {

        MC.Operand getDst();

        MC.Operand getSrc();

        void remove();

        boolean isNoCond();

        boolean directColor();
    }

    public interface ActualDefMI {
        MC.Operand getDef();

        void setDef(MC.Operand def);
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(hash);
    }
}



