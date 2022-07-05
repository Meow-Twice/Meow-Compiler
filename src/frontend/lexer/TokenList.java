package frontend.lexer;

import exception.SyntaxException;

import java.util.ArrayList;
import java.util.Arrays;

public class TokenList {
    private static final boolean ENABLE_DEBUG = false;

    public final ArrayList<Token> tokens = new ArrayList<>();
    private int index = 0;

    public void append(Token token) {
        // System.err.println(token);
        tokens.add(token);
    }

    public boolean hasNext() {
        return index < tokens.size();
    }

    public Token get() {
        return ahead(0);
    }

    public Token ahead(int count) {
        return tokens.get(index + count);
    }

    private void detectEof() throws SyntaxException {
        if (!hasNext()) {
            throw new SyntaxException("Unexpected EOF");
        }
    }

    public Token consume() throws SyntaxException {
        detectEof();
        if (ENABLE_DEBUG) {
            System.err.println("consume: " + tokens.get(index));
        }
        return tokens.get(index++);
    }

    // Usage: tokenList.consumeExpected(TokenType.INT, TokenType.VOID)
    public Token consumeExpected(TokenType... types) throws SyntaxException {
        detectEof();
        Token token = tokens.get(index);
        for (TokenType type : types) {
            if (token.getType().equals(type)) {
                if (ENABLE_DEBUG) {
                    System.err.println("consume: " + tokens.get(index));
                }
                index++;
                return token;
            }
        }
        for (int i = 0 ; i < index; i++){
            System.err.println(tokens.get(i));
        }
        throw new SyntaxException("Expected " + Arrays.toString(types) + " but got " + token);
    }
}

