package lir;

import backend.CodeGen;
import mir.Instr;
import util.ILinkNode;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.EnumMap;

import static mir.Instr.Alu.Op.*;
import static mir.type.DataType.I32;

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
        Machine.Program.PROGRAM.needFixList.add(this);
        fixType = stack_fix;
    }

    /**
     * 调用一个非库的函数之前需要sp偏移
     */
    public void setNeedFix(Machine.McFunction callee, CodeGen.STACK_FIX stack_fix) {
        setCallee(callee);
        setNeedFix(stack_fix);
    }

    public void setCond(Arm.Cond cond) {
        this.cond = cond;
    }

    public void setUse(int i, Machine.Operand set) {
        useOpds.set(i, set);
    }

    public Tag getType() {
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

    public ArrayList<Machine.Operand> getMIDefOpds() {
        ArrayList<Machine.Operand> defs = getDefOpds();
        Machine.Operand cond = new Machine.Operand(Arm.Regs.GPRs.cspr);
        if(this instanceof MICompare || this instanceof MICall || this instanceof V.Cmp){
            defs.add(cond);
        }
        return defs;

    }


    public ArrayList<Machine.Operand> getMIUseOpds() {
       ArrayList<Machine.Operand> uses = getUseOpds();;
        Machine.Operand cond = new Machine.Operand(Arm.Regs.GPRs.cspr);
        if(this.getCond()!= Arm.Cond.Any){
            uses.add(cond);
        }
        return uses;
    }

    public boolean isIAddOrISub() {
        return tag == Tag.Add || tag == Tag.Sub;
    }

    public CodeGen.STACK_FIX getFixType() {
        return fixType;
    }

    public void clearNeedFix() {
        fixType = CodeGen.STACK_FIX.NO_NEED;
    }

    public enum Tag {
        // Binary
        Add,
        FAdd,
        Sub,
        FSub,
        Rsb,
        Mul,
        FMul,
        Div,
        FDiv,
        Mod,
        FMod,
        Lt,
        Le,
        Ge,
        Gt,
        Eq,
        Ne,
        And,
        FAnd,
        Or,
        FOr,
        LongMul,
        FMA,
        Mv,
        VMov,
        VCvt,
        VNeg,
        Branch,
        Jump,
        Return,  // Control flow
        VRet,
        Load,
        VLdr,
        Store,  // Memory
        VStr,
        ICmp,
        VCmp,
        Call,
        Global,
        Push,
        Pop,
        VPush,
        VPop,
        Comment,   // for printing comments
        Empty;

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
    }

    public boolean isMove() {
        return tag == Tag.Mv;
    }

    public boolean isVMov() {
        return tag == Tag.VMov;
    }

    public boolean isCall() {
        return tag == Tag.Call;
    }

    public boolean isReturn() {
        return tag == Tag.Return;
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
    public boolean isFloat = false;
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

    public void transfer_output(PrintStream os) {
        if (mb != null && mb.con_tran == this) {
            os.println("@ control transfer");
        }
    }
    //这两个get方法专门给窥孔数据流分析的时候用，在Push和Pop指令处做了特殊处理(其他地方别用)
    public ArrayList<Machine.Operand> getDefOpds() {
        ArrayList<Machine.Operand> defs = new ArrayList<>();
        for(Machine.Operand op:defOpds){
            if(op.type == Machine.Operand.Type.Allocated || op.type == Machine.Operand.Type.PreColored) {
                op.type = Machine.Operand.Type.Allocated;
                defs.add(op);
            }
        }
        return defs;
    }

    public ArrayList<Machine.Operand> getUseOpds() {
        ArrayList<Machine.Operand> uses = new ArrayList<>();
        for(Machine.Operand op:useOpds){
            if(op.type == Machine.Operand.Type.Allocated || op.type == Machine.Operand.Type.PreColored) {
                op.type = Machine.Operand.Type.Allocated;
                uses.add(op);
            }
        }
        return uses;
    }

    public void output(PrintStream os, Machine.McFunction f) {
        return;
    }

    public void setTag(Tag tag) {
        this.tag = tag;
    }
    public interface Compare {
    }
}



