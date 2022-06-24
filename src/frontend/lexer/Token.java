package frontend.lexer;

import java.util.HashSet;
import java.util.List;

public class Token {
    private final TokenType type;

    private final String content;

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

    @Override
    public String toString() {
        return "<" + type + " " +  content + ">";
    }
}
