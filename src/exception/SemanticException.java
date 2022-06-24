package exception;

/**
 * 中端异常
 */
public class SemanticException extends Exception {
    public SemanticException() { super(); }

    public SemanticException(String message) { super(message); }
}
