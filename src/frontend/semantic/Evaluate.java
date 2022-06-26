package frontend.semantic;

import exception.SemanticException;
import frontend.lexer.Token;
import frontend.semantic.symbol.SymTable;
import frontend.semantic.symbol.Symbol;
import frontend.syntax.Ast;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * 编译期求值工具类
 */
public class Evaluate {
    private final SymTable symTable; // 当前的符号表
    private final boolean requireConst; // 是否是 ConstExp

    public Evaluate(SymTable symTable, boolean requireConst) {
        this.symTable = symTable;
        this.requireConst = requireConst;
    }

    // 编译期求值
    public int evalIntExp(Ast.Exp exp) throws SemanticException {
        if (exp instanceof Ast.BinaryExp) { return evalBinaryExp((Ast.BinaryExp) exp); }
        else if (exp instanceof Ast.UnaryExp) { return evalUnaryExp((Ast.UnaryExp) exp); }
        else { throw new AssertionError("Bad Exp"); }
    }

    public float evalFloatExp(Ast.Exp exp) throws SemanticException {
        if (exp instanceof Ast.BinaryExp) { return evalBinaryExp((Ast.BinaryExp) exp); }
        else if (exp instanceof Ast.UnaryExp) { return evalUnaryExp((Ast.UnaryExp) exp); }
        else { throw new AssertionError("Bad Exp"); }
    }

    // 二元运算
    private static int binaryCalcHelper(Token op, int src1, int src2) {
        return switch (op.getType()) {
            case ADD -> src1 + src2;
            case SUB -> src1 - src2;
            case MUL -> src1 * src2;
            case DIV -> src1 / src2;
            case MOD -> src1 % src2;
            case LT -> src1 < src2 ? 1 : 0;
            case GT -> src1 > src2 ? 1 : 0;
            case LE -> src1 <= src2 ? 1 : 0;
            case GE -> src1 >= src2 ? 1 : 0;
            case EQ -> src1 == src2 ? 1 : 0;
            case NE -> src1 != src2 ? 1 : 0;
            case LAND -> ((src1 != 0) && (src2 != 0)) ? 1 : 0;
            case LOR -> ((src1 != 0) || (src2 != 0)) ? 1 : 0;
            default -> throw new AssertionError("Bad Binary Operator");
        };
    }

    private static float binaryCalcHelper(Token op, float src1, float src2) {
        // union
        return switch (op.getType()) {
            case ADD -> src1 + src2;
            case SUB -> src1 - src2;
            case MUL -> src1 * src2;
            case DIV -> src1 / src2;
            case MOD -> src1 % src2;
            case LT -> src1 < src2 ? 1 : 0;
            case GT -> src1 > src2 ? 1 : 0;
            case LE -> src1 <= src2 ? 1 : 0;
            case GE -> src1 >= src2 ? 1 : 0;
            case EQ -> src1 == src2 ? 1 : 0;
            case NE -> src1 != src2 ? 1 : 0;
            case LAND -> ((src1 != 0) && (src2 != 0)) ? 1 : 0;
            case LOR -> ((src1 != 0) || (src2 != 0)) ? 1 : 0;
            default -> throw new AssertionError("Bad Binary Operator");
        };
    }

    // 一元运算
    private static int unaryCalcHelper(Token op, int src) {
        return switch (op.getType()) {
            case ADD -> src;
            case SUB -> -src;
            case NOT -> src == 0 ? 1 : 0;
            default -> throw new AssertionError("Bad Unary Operator");
        };
    }

    // 从左到右按顺序计算即可
    public int evalBinaryExp(Ast.BinaryExp exp) throws SemanticException {
        Ast.Exp first = exp.getFirst();
        Iterator<Token> iterOp = exp.getOperators().listIterator();
        Iterator<Ast.Exp> iterExp = exp.getFollows().listIterator();
        int result = evalIntExp(first);
        while (iterExp.hasNext()) {
            assert iterOp.hasNext();
            Token op = iterOp.next();
            Ast.Exp follow = iterExp.next();
            result = binaryCalcHelper(op, result, evalIntExp(follow));
        }
        return result;
    }

    public int evalUnaryExp(Ast.UnaryExp exp) throws SemanticException {
        int primary = evalPrimaryExp(exp.getPrimary());
        // 从右向左结合
        ArrayList<Token> unaryOps = new ArrayList<>(exp.getUnaryOps());
        for (int i = unaryOps.size() - 1; i >= 0; i--) {
            Token op = unaryOps.get(i);
            primary = unaryCalcHelper(op, primary);
        }
        return primary;
    }

    public int evalPrimaryExp(Ast.PrimaryExp exp) throws SemanticException {
        if (exp instanceof Ast.Number) {
            return evalNumber((Ast.Number) exp);
        } else if (exp instanceof Ast.Exp) {
            return evalIntExp((Ast.Exp) exp);
        } else if (exp instanceof Ast.LVal) {
            return evalLVal((Ast.LVal) exp);
        } else {
            // 编译期不能求函数调用的值
            throw new AssertionError("Bad Eval Primary Exp: " + exp.getClass().getSimpleName());
        }
    }

    public int evalNumber(Ast.Number number) {
        Token num = number.getNumber();
        String content = num.getContent();
        // return 1;
        return switch (num.getType()) {
            case HEX_INT -> Integer.parseInt(content.substring(2), 16);
            case OCT_INT -> Integer.parseInt(content.substring(1), 8);
            case DEC_INT -> Integer.parseInt(content);
            default -> throw new AssertionError("Bad Number!");
        };
    }

    public int evalLVal(Ast.LVal lVal) throws SemanticException {
        String ident = lVal.getIdent().getContent();
        // 查找符号表
        Symbol symbol = symTable.get(ident, true);
        if (requireConst && !symbol.isConstant()) {
            throw new SemanticException("Expected Const but got not Const.");
        }
        // 必须初始化过，才可以编译期求值
        if (symbol.getInitial() == null) {
            throw new SemanticException("Symbol not initialized");
        }
        // 如果是数组, 逐层找偏移量
        Initial init = symbol.getInitial();
        ArrayList<Integer> indexes = new ArrayList<>(); // eval indexes
        for (Ast.Exp index : lVal.getIndexes()) {
            indexes.add(evalIntExp(index));
        }
        for (Integer index : indexes) {
            if (init instanceof Initial.ValueInit) {
                throw new SemanticException("Should be Array/Zero Init!");
            }
            if (init instanceof Initial.ZeroInit) {
                return 0;
            }
            assert init instanceof Initial.ArrayInit;
            init = ((Initial.ArrayInit) init).get(index);
        }
        if (!(init instanceof Initial.ValueInit)) {
            throw new SemanticException("Should be Value Init");
        }
        return ((Initial.ValueInit) init).getValue(); // 取出初始值
    }
}
