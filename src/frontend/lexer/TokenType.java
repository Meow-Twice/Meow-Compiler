package frontend.lexer;

import java.util.regex.Pattern;

public enum TokenType {
    // keyword (must have lookahead assertion)
    CONST("const", true),
    INT("int", true),
    FLOAT("float", true),
    BREAK("break", true),
    CONTINUE("continue", true),
    IF("if", true),
    ELSE("else", true),
    VOID("void", true),
    WHILE("while", true),
    RETURN("return", true),
    // ident
    IDENT("[A-Za-z_][A-Za-z0-9_]*"),
    // float const
    HEX_FLOAT("0(x|X)[0-9A-Fa-f]*[\\.]?[0-9A-Fa-f]*((p|P|e|E)(\\+|\\-)?[0-9A-Fa-f]*)?"),
    // OCT_FLOAT("0[0-7]+[\\.]?[0-7]*((p|P|e|E)(\\+|\\-)?[0-9A-Fa-f]+)?"),
    DEC_FLOAT("(0|([1-9][0-9]*))[\\.]?[0-9]*((p|P|e|E)(\\+|\\-)?[0-9]+)?"), // decimal
    // int const
    HEX_INT("0(x|X)[0-9A-Fa-f]+"),
    OCT_INT("0[0-7]+"),
    DEC_INT("0|([1-9][0-9]*)"), // decimal
    // operator (double char)
    LAND("&&"),
    LOR("\\|\\|"),
    LE("<="),
    GE(">="),
    EQ("=="),
    NE("!="),
    // operator (single char)
    LT("<"),
    GT(">"),
    ADD("\\+"), // regex
    SUB("-"),
    MUL("\\*"),
    DIV("/"),
    MOD("%"),
    NOT("!"),
    ASSIGN("="),
    SEMI(";"),
    COMMA(","),
    LPARENT("\\("),
    RPARENT("\\)"),
    LBRACK("\\["),
    RBRACK("]"),
    LBRACE("\\{"),
    RBRACE("}"),
    ;

    private final String pattern;   // regex pattern

    private final boolean keyword;  // keyword

    private TokenType(final String pattern, final boolean keyword) {
        this.pattern = pattern;
        this.keyword = keyword;
    }

    TokenType(String pattern) {
        this(pattern, false);
    }

    public Pattern getPattern() {
        return Pattern.compile("^(" + pattern + ")" + (keyword ? "(?![A-Za-z0-9_])" : ""));
    }

}
