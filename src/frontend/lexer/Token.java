package frontend.lexer;

import java.util.HashSet;
import java.util.List;

public class Token {
    private final TokenType type;
    public final String content;

    public int lineNum = 0;

    private static final boolean[] IS_ALU_TYPE = new boolean[100];
    private static final boolean[] IS_CMP_TYPE = new boolean[100];

    static {
        for(int i = TokenType.ADD.ordinal(); i <= TokenType.MOD.ordinal(); i++){
            IS_ALU_TYPE[i] = true;
        }
        for(int i = TokenType.LE.ordinal(); i <= TokenType.GT.ordinal(); i++){
            IS_CMP_TYPE[i] = true;
        }
    }

    public Token(final TokenType type, final String content) {
        this.type = type;
        this.content = content;
    }

    public TokenType getType() {
        return this.type;
    }

    public String getContent() {
        return this.content;
    }

    public boolean isOf(TokenType... types) {
        return new HashSet<>(List.of(types)).contains(type);
    }

    public boolean isNumber() {
        return type.ordinal() <= TokenType.DEC_INT.ordinal()
                && type.ordinal() >= TokenType.HEX_FLOAT.ordinal();
    }

    public boolean isIntConst() {
        return type.ordinal() <= TokenType.DEC_INT.ordinal()
                && type.ordinal() >= TokenType.HEX_INT.ordinal();
    }

    public boolean isFloatConst() {
        return type.ordinal() <= TokenType.DEC_FLOAT.ordinal()
                && type.ordinal() >= TokenType.HEX_FLOAT.ordinal();
    }

    @Override
    public String toString() {
        return "<" + type + " " + content + ">";
    }

    public static boolean isAlu(TokenType tokenType) {
        return IS_ALU_TYPE[tokenType.ordinal()];
    }

    public static boolean isCmp(TokenType tokenType){
        return IS_CMP_TYPE[tokenType.ordinal()];
    }
}
