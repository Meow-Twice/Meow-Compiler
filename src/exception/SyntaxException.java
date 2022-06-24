package exception;

/**
 * 编译器前端发生的异常(词法分析和语法分析阶段)
 */
public class SyntaxException extends Exception {
    public SyntaxException() { super(); }

    public SyntaxException(String message) { super(message); }
}
