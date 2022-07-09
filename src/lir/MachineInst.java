package lir;

import mir.Instr;
import util.ILinkNode;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.EnumMap;

import static mir.Instr.Alu.Op.*;
import static mir.Instr.Alu.Op.OR;

public class MachineInst extends ILinkNode{

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
        Branch,
        Jump,
        Return,  // Control flow
        Load,
        Store,  // Memory
        Compare,
        Call,
        Global,
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

    ;


    Machine.Block mb;
    Tag tag;
    boolean isFloat = false;
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
    init and insert at end of the bb
    */
    public MachineInst(Tag tag, Machine.Block mb, boolean isFloat) {
        this.mb = mb;
        this.tag = tag;
        this.isFloat = isFloat;
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


    /*
    init and inset before inst
    */
    public MachineInst(Tag tag, MachineInst inst, boolean isFloat) {
        this.mb = inst.mb;
        this.isFloat = isFloat;
        this.tag = tag;
        inst.insertBefore(this);
    }

    public MachineInst(Tag tag) {
        this.tag = tag;
        this.isFloat = false;
    }

    public MachineInst(Tag tag, boolean isFloat) {
        this.tag = tag;
        this.isFloat = isFloat;
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

    public void output(PrintStream os, Machine.McFunction f){
        return;
    }
}



