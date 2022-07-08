package lir;

import mir.Instr;

import java.util.EnumMap;
import java.util.Map;

import static mir.Instr.Alu.Op.*;

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
