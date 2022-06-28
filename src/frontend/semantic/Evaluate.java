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

    public static int evalConstIntExp(Ast.Exp exp) throws SemanticException {
        return (int) evalConstExp(exp);
    }

    public static float evalConstFloatExp(Ast.Exp exp) throws SemanticException {
        return (float) evalConstExp(exp);
    }

    // 编译期求值
    public static Object evalConstExp(Ast.Exp exp) throws SemanticException {
        if (exp instanceof Ast.BinaryExp) {
            return evalBinaryConstExp((Ast.BinaryExp) exp);
        } else if (exp instanceof Ast.UnaryExp) {
            return evalUnaryConstExp((Ast.UnaryExp) exp);
        } else {
            throw new AssertionError("Bad Exp:" + exp);
        }
    }

    // 二元运算
    private static Object binaryCalcHelper(Token op, Object src1, Object src2) {
        if (src1 instanceof Float || src2 instanceof Float) {
            Float f1 = (float) src1;
            Float f2 = (float) src2;
            return switch (op.getType()) {
                case ADD -> f1 + f2;
                case SUB -> f1 - f2;
                case MUL -> f1 * f2;
                case DIV -> f1 / f2;
                case MOD -> f1 % f2;
                default -> throw new AssertionError("Bad Binary Operator");
            };
        } else {
            assert src1 instanceof Integer && src2 instanceof Integer;
            Integer i1 = (int) src1;
            Integer i2 = (int) src2;
            return switch (op.getType()) {
                case ADD -> i1 + i2;
                case SUB -> i1 - i2;
                case MUL -> i1 * i2;
                case DIV -> i1 / i2;
                case MOD -> i1 % i2;
                default -> throw new AssertionError("Bad Binary Operator");
            };
        }
    }

    // 一元运算
    private static Object unaryCalcHelper(Token op, Object src) {
        if (src instanceof Integer) {
            int intConst = (int) src;
            return switch (op.getType()) {
                case ADD -> intConst;
                case SUB -> -intConst;
                case NOT -> intConst == 0 ? 1 : 0;
                default -> throw new AssertionError("Bad Unary Operator");
            };
        } else if (src instanceof Float) {
            float floatConst = (float) src;
            return switch (op.getType()) {
                case ADD -> floatConst;
                case SUB -> -floatConst;
                case NOT -> floatConst == 0.0 ? 1.0 : 0.0;
                default -> throw new AssertionError("Bad Unary Operator");
            };
        } else {
            throw new AssertionError("Bad src: " + src);
        }
    }

    // 从左到右按顺序计算即可
    public static Object evalBinaryConstExp(Ast.BinaryExp exp) throws SemanticException {
        Ast.Exp first = exp.getFirst();
        Iterator<Token> iterOp = exp.getOperators().listIterator();
        Iterator<Ast.Exp> iterExp = exp.getFollows().listIterator();
        Object result = evalConstExp(first);
        while (iterExp.hasNext()) {
            assert iterOp.hasNext();
            Token op = iterOp.next();
            Ast.Exp follow = iterExp.next();
            result = binaryCalcHelper(op, result, evalConstExp(follow));
        }
        return result;
    }

    public static Object evalUnaryConstExp(Ast.UnaryExp exp) throws SemanticException {
        Object primary = evalPrimaryExp(exp.getPrimary());
        // 从右向左结合
        ArrayList<Token> unaryOps = new ArrayList<>(exp.getUnaryOps());
        for (int i = unaryOps.size() - 1; i >= 0; i--) {
            Token op = unaryOps.get(i);
            primary = unaryCalcHelper(op, primary);
        }
        return primary;
    }

    public static Object evalPrimaryExp(Ast.PrimaryExp exp) throws SemanticException {
        if (exp instanceof Ast.Number) {
            return evalNumber((Ast.Number) exp);
        } else if (exp instanceof Ast.Exp) {
            return evalConstExp((Ast.Exp) exp);
        } else if (exp instanceof Ast.LVal) {
            return evalLVal((Ast.LVal) exp);
        } else {
            // 编译期不能求函数调用的值
            throw new AssertionError("Bad Eval Primary Exp: " + exp.getClass().getSimpleName());
        }
    }

    public static int evalNumber(Ast.Number number) {
        Token num = number.getNumber();
        String content = num.getContent();
        return switch (num.getType()) {
            case HEX_INT -> Integer.parseInt(content.substring(2), 16);
            case OCT_INT -> Integer.parseInt(content.substring(1), 8);
            case DEC_INT -> Integer.parseInt(content);
            default -> throw new AssertionError("Bad Number!");
        };
    }

    public static Object evalLVal(Ast.LVal lVal) throws SemanticException {
        String ident = lVal.getIdent().getContent();
        // 查找符号表
        Symbol symbol = Visitor.currentSymTable.get(ident, true);
        // if (requireConst && !symbol.isConstant()) {
        //     throw new SemanticException("Expected Const but got not Const.");
        // }
        // 必须初始化过，才可以编译期求值
        if (symbol.getInitial() == null) {
            throw new SemanticException("Symbol not initialized");
        }
        // 如果是数组, 逐层找偏移量
        Initial init = symbol.getInitial();
        ArrayList<Integer> indexes = new ArrayList<>(); // eval indexes
        for (Ast.Exp index : lVal.getIndexes()) {
            indexes.add((int) evalConstExp(index));
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
