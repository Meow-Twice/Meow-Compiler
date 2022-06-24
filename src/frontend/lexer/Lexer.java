package frontend.lexer;

import exception.SyntaxException;

import java.util.regex.Matcher;

// 词法分析器(工具类)
public class Lexer {

    private static final String LINE_COMMENT = "//";
    private static final String BLOCK_COMMENT_START = "/*";
    private static final String BLOCK_COMMENT_END = "*/";

    public static TokenList lex(String source) throws SyntaxException {
        TokenList ts = new TokenList();

        int pos = 0;
        while (pos < source.length()) {
            // 跳过空白符号
            while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) { pos++; }
            // 跳过行注释
            if (source.startsWith(LINE_COMMENT, pos)) {
                pos += LINE_COMMENT.length();
                while (pos < source.length() && source.charAt(pos) != '\n') { pos++; }
                continue;
            }
            // 跳过块注释
            if (source.startsWith(BLOCK_COMMENT_START, pos)) {
                pos += BLOCK_COMMENT_START.length();
                while (pos < source.length() && !source.startsWith(BLOCK_COMMENT_END, pos)) { pos++; }
                if (pos >= source.length()) {
                    throw new SyntaxException("Block comment not end");
                }
                pos += BLOCK_COMMENT_END.length();
                continue;
            }
            // 进入词法分析
            boolean success = false;
            for (TokenType type : TokenType.values()) {
                Matcher matcher = type.getPattern().matcher(source.substring(pos));
                if (matcher.find()) {
                    String token = matcher.group(0);
                    ts.append(new Token(type, token));
                    success = true;
                    pos += token.length();
                    break;
                }
            }
            if (pos < source.length() && !success) {
                throw new SyntaxException("Undefined Token"); }
        }

        return ts;
    }
}
