package lir;

import mir.Instr;
import util.ILinkNode;

import java.util.ArrayList;
import java.util.EnumMap;

import static mir.Instr.Alu.Op.*;
import static mir.Instr.Alu.Op.OR;

public class MachineInst {

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
        Comment;  // for printing comments

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
    };



    Machine.Block bb;
    Tag tag;
    boolean isFloat;
    public ArrayList<Machine.Operand> defOpds = new ArrayList<>();
    public ArrayList<Machine.Operand> useOpds = new ArrayList<>();

    /*
    init and insert at end of the bb
    */
    public MachineInst(Tag tag,Machine.Block insertAtEnd) {
        this.bb = insertAtEnd;
        this.tag = tag;
        if (insertAtEnd != null) {
            insertAtEnd.insts.add(insertAtEnd.insts.size - 1, this);
        }
    }

    /*
    init and inset before inst
    */
    public MachineInst(Tag tag,MachineInst inst,boolean isFloat) {
            this.bb = inst.bb;
            this.isFloat = isFloat;
            this.tag = tag;
            if (inst.bb != null) {
                int index = inst.bb.insts.getIndex(inst);
                inst.bb.insts.add(index,this);
            }
    }

    public MachineInst(Tag tag,boolean isFloat) {
        this.tag = tag;
        this.isFloat = isFloat;
    }

    public void genDefUse(){
    }
}



