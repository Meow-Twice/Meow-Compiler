package frontend.lexer;

import java.io.*;
import java.util.regex.Matcher;

import static frontend.lexer.TokenType.*;

public class Lexer {
    private static final boolean[] constChar = new boolean[128];
    private static final boolean[] floatChar = new boolean[128];

    private static final Lexer lexer = new Lexer();
    public static boolean detectFloat = false;

    private TokenList tokenList;

    static {
        for (int c = '0'; c <= '9'; c++) {
            constChar[c] = true;
        }
        constChar['x'] = true;
        constChar['X'] = true;
        constChar['p'] = true;
        constChar['P'] = true;
        constChar['e'] = true;
        constChar['E'] = true;
        constChar['.'] = true;
        for (int c = 'a'; c <= 'f'; c++) {
            constChar[c] = true;
        }
        for (int c = 'A'; c <= 'F'; c++) {
            constChar[c] = true;
        }


        floatChar['x'] = true;
        floatChar['X'] = true;
        floatChar['p'] = true;
        floatChar['P'] = true;
        floatChar['e'] = true;
        floatChar['E'] = true;
    }

    private Lexer() {
    }

    public static Lexer getInstance() {
        return lexer;
    }

    public int myGetc(BufferedInputStream bis) {
        int c = 0;
        try {
            c = bis.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return c;
    }

    private boolean isWhiteSpace(char c) {
        return Character.isWhitespace(c);
    }

    private boolean isNewline(char c) {
        return c == '\n';
    }

    private boolean isDigital(char c) {
        return (c <= '9' && c >= '0') || c == '.';
    }

    private boolean isLetter(char c) {
        return Character.isLowerCase(c) || Character.isUpperCase(c) || c == '_';
    }

    private void keywordTokenDeal(String str) {
        switch (str) {
            // case "main": {
            //     Token tk = new Token(TokenType., str);
            //     tokenList.append(tk);
            //     break;
            // }
            case "const" -> {
                Token tk = new Token(CONST, str);
                tokenList.append(tk);
            }
            case "int" -> {
                Token tk = new Token(INT, str);
                tokenList.append(tk);
            }
            case "float" -> {
                detectFloat = true;
                Token tk = new Token(FLOAT, str);
                tokenList.append(tk);
            }
            case "break" -> {
                Token tk = new Token(BREAK, str);
                tokenList.append(tk);
            }
            case "continue" -> {
                Token tk = new Token(CONTINUE, str);
                tokenList.append(tk);
            }
            case "if" -> {
                Token tk = new Token(IF, str);
                tokenList.append(tk);
            }
            case "else" -> {
                Token tk = new Token(ELSE, str);
                tokenList.append(tk);
            }
            case "while" -> {
                Token tk = new Token(WHILE, str);
                tokenList.append(tk);
            }

            // case "getint": {
            //     Token tk = new Token(, str);
            //     tokenList.append(tk);
            //     break;
            // }
            // case "printf": {
            //     Token tk = new Token(TokenEnum.tk_printf, str);
            //     tokenList.append(tk);
            //     break;
            // }
            case "return" -> {
                Token tk = new Token(RETURN, str);
                tokenList.append(tk);
            }
            case "void" -> {
                Token tk = new Token(VOID, str);
                tokenList.append(tk);
            }
            default -> {
                Token tk = new Token(IDENT, str);
                tokenList.append(tk);
            }
        }
    }

    public void lex(BufferedInputStream bis, TokenList tokenList) {
        this.tokenList = tokenList;
        int c = myGetc(bis);
        // System.out.print((char)c);
        while (c != -1) {
            StringBuilder stringBuilder = new StringBuilder();
            Character lastChar = (char) c;
            if (lastChar == '/') {
                c = myGetc(bis);
                if (c == -1) {
                    break;
                }
                if (c == (int) '/') {
                    c = myGetc(bis);
                    while (c != -1) {
                        if (isNewline((char) c)) {
                            c = myGetc(bis);
                            break;
                        }
                        c = myGetc(bis);
                    }
                } else if (c == (int) '*') {
                    c = myGetc(bis);
                    while (c != -1) {
                        if (c == (int) '*') {
                            c = myGetc(bis);
                            if (c == (int) '/') {
                                c = myGetc(bis);
                                break;
                            } else {
                                continue;
                            }
                        }
                        c = myGetc(bis);
                    }
                } else {
                    Token tk = new Token(DIV, "/");
                    tokenList.append(tk);
                }
                if (c == -1) {
                    break;
                }
                lastChar = (char) c;
            }
            while (isWhiteSpace(lastChar) || isNewline(lastChar)) {
                c = myGetc(bis);
                if (c == -1) {
                    break;
                }
                lastChar = (char) c;
            }
            if (c == -1) {
                break;
            }
            if (isLetter(lastChar)) {
                // String str = "";
                stringBuilder.append(lastChar);
                c = myGetc(bis);
                if (c == -1) {
                    keywordTokenDeal(stringBuilder.toString());
                    break;
                }
                lastChar = (char) c;
                while (isLetter(lastChar) || isDigital(lastChar)) {
                    stringBuilder.append(lastChar);
                    c = myGetc(bis);
                    if (c == -1) {
                        break;
                    }
                    lastChar = (char) c;
                }
                keywordTokenDeal(stringBuilder.toString());
                if (c == -1) {
                    break;
                }
            } else if (isDigital(lastChar)) {
                StringBuilder numStr = new StringBuilder();
                boolean flag = false;
                do {
                    numStr.append(lastChar);
                    c = myGetc(bis);
                    if (c == -1) {
                        break;
                    }
                    lastChar = (char) c;
                    if (flag) {
                        if (lastChar == '+' || lastChar == '-') {
                            c = myGetc(bis);
                            if (c == -1) {
                                break;
                            }
                            numStr.append(lastChar);
                            lastChar = (char) c;
                        }
                    }
                    flag = floatChar[c];
                } while (constChar[(lastChar)]);
                boolean success = false;
                for (TokenType type : NUM_CON_LIST) {
                    Matcher matcher = type.getPattern().matcher(numStr);
                    if (matcher.matches()) {
                        if(type == HEX_FLOAT || type == DEC_FLOAT){
                            detectFloat = true;
                        }
                        // String token = matcher.group(0);
                        Token tk = new Token(type, numStr.toString());
                        tokenList.append(tk);
                        success = true;
                        break;
                    }
                }
                if (!success) {
                    throw new AssertionError("fuck token: " + numStr);
                }
                if (c == -1) {
                    break;
                }
            } else if (lastChar == '\"') {
                stringBuilder.append('\"');
                c = myGetc(bis);
                if (c == -1) {
                    break;
                }
                lastChar = (char) c;
                if (lastChar == '\"') {
                    // lexerErrorAdd(lexer'a');
                    Token tk = new Token(STR_CON, "");
                    tokenList.append(tk);
                    c = myGetc(bis);
                    if (c == -1) {
                        break;
                    }
                    continue;
                }
                while (true) {
                    if (lastChar == '\\') {
                        stringBuilder.append("\\");
                    } else {
                        stringBuilder.append(lastChar);
                    }
                    c = myGetc(bis);
                    if (c == -1) {
                        break;
                    }
                    lastChar = (char) c;

                    if (lastChar == '\"') {
                        break;
                    }
                }
                if (c == -1) {
                    break;
                }
                stringBuilder.append('\"');
                Token tk = new Token(STR_CON, stringBuilder.toString());
                tokenList.append(tk);
                c = myGetc(bis);
                if (c == -1) {
                    break;
                }
            } else if (lastChar == '=') {
                c = myGetc(bis);
                if (c == -1) {
                    Token tk = new Token(ASSIGN, "=");
                    tokenList.append(tk);
                    break;
                }
                lastChar = (char) c;
                if (lastChar == '=') {
                    Token tk = new Token(EQ, "==");
                    tokenList.append(tk);
                    c = myGetc(bis);
                    if (c == -1) {
                        break;
                    }
                } else {
                    Token tk = new Token(ASSIGN, "=");
                    tokenList.append(tk);
                }
            } else if (lastChar == '!') {
                c = myGetc(bis);
                if (c == -1) {
                    Token tk = new Token(NOT, "!");
                    tokenList.append(tk);
                    break;
                }
                lastChar = (char) c;
                if (lastChar == '=') {
                    Token tk = new Token(NE, "!=");
                    tokenList.append(tk);
                    c = myGetc(bis);
                    if (c == -1) {
                        break;
                    }
                } else {
                    Token tk = new Token(NOT, "!");
                    tokenList.append(tk);
                }
            } else if (lastChar == '>') {
                c = myGetc(bis);
                if (c == -1) {
                    Token tk = new Token(GT, ">");
                    tokenList.append(tk);
                    break;
                }
                lastChar = (char) c;
                if (lastChar == '=') {
                    Token tk = new Token(GE, ">=");
                    tokenList.append(tk);
                    c = myGetc(bis);
                    if (c == -1) {
                        break;
                    }
                } else {
                    Token tk = new Token(GT, ">");
                    tokenList.append(tk);
                }
            } else if (lastChar == '<') {
                c = myGetc(bis);
                if (c == -1) {
                    Token tk = new Token(LT, "<");
                    tokenList.append(tk);
                    break;
                }
                lastChar = (char) c;
                if (lastChar == '=') {
                    Token tk = new Token(LE, "<=");
                    tokenList.append(tk);
                    c = myGetc(bis);
                    if (c == -1) {
                        break;
                    }
                } else {
                    Token tk = new Token(LT, "<");
                    tokenList.append(tk);
                }
            } else if (lastChar == '&') {
                c = myGetc(bis);
                if (c == -1) {
                    break;
                }
                lastChar = (char) c;
                if (lastChar == '&') {
                    Token tk = new Token(LAND, "&&");
                    tokenList.append(tk);
                    c = myGetc(bis);
                    if (c == -1) {
                        break;
                    }
                }
            } else if (lastChar == '|') {
                c = myGetc(bis);
                if (c == -1) {
                    break;
                }
                lastChar = (char) c;
                if (lastChar == '|') {
                    Token tk = new Token(LOR, "||");
                    tokenList.append(tk);
                    c = myGetc(bis);
                    if (c == -1) {
                        break;
                    }
                }
            } else {
                switch (lastChar) {
                    case '+' -> {
                        Token tk = new Token(ADD, "+");
                        tokenList.append(tk);
                        c = myGetc(bis);
                    }
                    case '-' -> {
                        Token tk = new Token(SUB, "-");
                        tokenList.append(tk);
                        c = myGetc(bis);
                    }
                    case '*' -> {
                        Token tk = new Token(MUL, "*");
                        tokenList.append(tk);
                        c = myGetc(bis);
                    }
                    case '%' -> {
                        Token tk = new Token(MOD, "%");
                        tokenList.append(tk);
                        c = myGetc(bis);
                    }
                    case ';' -> {
                        Token tk = new Token(SEMI, ";");
                        tokenList.append(tk);
                        c = myGetc(bis);
                    }
                    case ',' -> {
                        Token tk = new Token(COMMA, ",");
                        tokenList.append(tk);
                        c = myGetc(bis);
                    }
                    case '(' -> {
                        Token tk = new Token(LPARENT, "(");
                        tokenList.append(tk);
                        c = myGetc(bis);
                    }
                    case ')' -> {
                        Token tk = new Token(RPARENT, ")");
                        tokenList.append(tk);
                        c = myGetc(bis);
                    }
                    case '[' -> {
                        Token tk = new Token(LBRACK, "[");
                        tokenList.append(tk);
                        c = myGetc(bis);
                    }
                    case ']' -> {
                        Token tk = new Token(RBRACK, "]");
                        tokenList.append(tk);
                        c = myGetc(bis);
                    }
                    case '{' -> {
                        Token tk = new Token(LBRACE, "{");
                        tokenList.append(tk);
                        c = myGetc(bis);
                    }
                    case '}' -> {
                        Token tk = new Token(RBRACE, "}");
                        tokenList.append(tk);
                        c = myGetc(bis);
                    }
                    default -> {
                    }
                }
                if (c == -1) {
                    break;
                }

            }
        }
        try {
            bis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
