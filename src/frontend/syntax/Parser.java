package frontend.syntax;

import exception.SyntaxException;
import frontend.lexer.Token;
import frontend.lexer.TokenList;
import frontend.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;

/**
 * 递归下降语法分析器
 */
public class Parser {
    private final TokenList tokenList;

    public Parser(TokenList tokenList) {
        this.tokenList = tokenList;
    }

    public Ast parseAst() throws SyntaxException {
        ArrayList<Ast.CompUnit> units = new ArrayList<>();
        while (tokenList.hasNext()) {
            if (tokenList.ahead(2).isOf(TokenType.LPARENT)) {
                units.add(parseFuncDef());
            }
            else { units.add(parseDecl()); }
        }
        return new Ast(units);
    }

    private Ast.Decl parseDecl() throws SyntaxException {
        ArrayList<Ast.Def> defs = new ArrayList<>();
        boolean constant;
        if (tokenList.get().isOf(TokenType.CONST)) {
            tokenList.consume();
            constant = true;
        }
        else { constant = false; }
        Token bType = tokenList.consumeExpected(TokenType.INT);
        defs.add(parseDef(constant));
        while (tokenList.get().isOf(TokenType.COMMA)) {
            tokenList.consume();
            defs.add(parseDef(constant));
        }
        tokenList.consumeExpected(TokenType.SEMI);
        return new Ast.Decl(constant, bType, defs);
    }

    private Ast.Def parseDef(boolean constant) throws SyntaxException {
        Token ident = tokenList.consumeExpected(TokenType.IDENT);
        ArrayList<Ast.Exp> indexes = new ArrayList<>();
        Ast.Init init = null;
        while (tokenList.get().isOf(TokenType.LBRACK)) {
            tokenList.consume();
            indexes.add(parseAddExp());
            tokenList.consumeExpected(TokenType.RBRACK);
        }
        if (constant) {
            tokenList.consumeExpected(TokenType.ASSIGN);
            init = parseInitVal();
        }
        else {
            if (tokenList.hasNext() && tokenList.get().isOf(TokenType.ASSIGN)) {
                tokenList.consume();
                init = parseInitVal();
            }
        }
        return new Ast.Def(ident, indexes, init);
    }

    private Ast.Init parseInitVal() throws SyntaxException {
        if (tokenList.get().isOf(TokenType.LBRACE)) {
            return parseInitArray();
        }
        else {
            return parseAddExp();
        }
    }

    private Ast.InitArray parseInitArray() throws SyntaxException {
        ArrayList<Ast.Init> init = new ArrayList<>();
        tokenList.consumeExpected(TokenType.LBRACE);
        if (!tokenList.get().isOf(TokenType.RBRACE)) {
            init.add(parseInitVal());
            while (tokenList.get().isOf(TokenType.COMMA)) {
                tokenList.consume();
                init.add(parseInitVal());
            }
        }
        tokenList.consumeExpected(TokenType.RBRACE);
        return new Ast.InitArray(init);
    }

    private Ast.FuncDef parseFuncDef() throws SyntaxException {
        Token type = tokenList.consumeExpected(TokenType.VOID, TokenType.INT);
        Token ident = tokenList.consumeExpected(TokenType.IDENT);
        ArrayList<Ast.FuncFParam> fParams = new ArrayList<>();
        tokenList.consumeExpected(TokenType.LPARENT);
        if (!tokenList.get().getContent().equals(")")) {
            fParams = parseFuncFParams();
        }
        tokenList.consumeExpected(TokenType.RPARENT);
        Ast.Block body = parseBlock();
        return new Ast.FuncDef(type, ident, fParams, body);
    }

    private ArrayList<Ast.FuncFParam> parseFuncFParams() throws SyntaxException {
        ArrayList<Ast.FuncFParam> fParams = new ArrayList<>();
        fParams.add(parseFuncFParam());
        while (tokenList.hasNext() && tokenList.get().isOf(TokenType.COMMA)) {
            tokenList.consumeExpected(TokenType.COMMA);
            fParams.add(parseFuncFParam());
        }
        return fParams;
    }

    private Ast.FuncFParam parseFuncFParam() throws SyntaxException {
        Token bType = tokenList.consumeExpected(TokenType.INT);
        Token ident = tokenList.consumeExpected(TokenType.IDENT);
        boolean array = false;
        ArrayList<Ast.Exp> sizes = new ArrayList<>();
        if (tokenList.hasNext() && tokenList.get().isOf(TokenType.LBRACK)) {
            array = true;
            tokenList.consumeExpected(TokenType.LBRACK);
            tokenList.consumeExpected(TokenType.RBRACK);
            while (tokenList.hasNext() && tokenList.get().isOf(TokenType.LBRACK)) {
                tokenList.consumeExpected(TokenType.LBRACK);
                sizes.add(parseAddExp());
                tokenList.consumeExpected(TokenType.RBRACK);
            }
        }
        return new Ast.FuncFParam(bType, ident, array, sizes);
    }

    private Ast.Block parseBlock() throws SyntaxException {
        ArrayList<Ast.BlockItem> items = new ArrayList<>();
        tokenList.consumeExpected(TokenType.LBRACE);
        while (!tokenList.get().getContent().equals("}")) {
            items.add(parseBlockItem());
        }
        tokenList.consumeExpected(TokenType.RBRACE);
        return new Ast.Block(items);
    }

    private Ast.BlockItem parseBlockItem() throws SyntaxException {
        if (tokenList.get().getContent().equals("const") ||
                tokenList.get().getContent().equals("int")) {
            return parseDecl();
        } else {
            return parseStmt();
        }
    }

    private Ast.Stmt parseStmt() throws SyntaxException {
        TokenType temp = tokenList.get().getType();
        Ast.Exp cond;
        switch (temp) {
            case LBRACE:
                return parseBlock();
            case IF:
                tokenList.consume();
                tokenList.consumeExpected(TokenType.LPARENT);
                cond = parseCond();
                tokenList.consumeExpected(TokenType.RPARENT);
                Ast.Stmt thenTarget = parseStmt();
                Ast.Stmt elseTarget = null;
                if (tokenList.hasNext() && tokenList.get().isOf(TokenType.ELSE)) {
                    tokenList.consumeExpected(TokenType.ELSE);
                    elseTarget = parseStmt();
                }
                return new Ast.IfStmt(cond, thenTarget, elseTarget);
            case WHILE:
                tokenList.consume();
                tokenList.consumeExpected(TokenType.LPARENT);
                cond = parseCond();
                tokenList.consumeExpected(TokenType.RPARENT);
                Ast.Stmt body = parseStmt();
                return new Ast.WhileStmt(cond, body);
            case BREAK:
                tokenList.consumeExpected(TokenType.BREAK);
                tokenList.consumeExpected(TokenType.SEMI);
                return new Ast.Break();
            case CONTINUE:
                tokenList.consumeExpected(TokenType.CONTINUE);
                tokenList.consumeExpected(TokenType.SEMI);
                return new Ast.Continue();
            case RETURN:
                tokenList.consumeExpected(TokenType.RETURN);
                Ast.Exp value = null;
                if (!tokenList.get().getContent().equals(";")) {
                    value = parseAddExp();
                }
                tokenList.consumeExpected(TokenType.SEMI);
                return new Ast.Return(value);
            case IDENT:
                // 先读出一整个 Exp 再判断是否只有一个 LVal (因为 LVal 可能是数组)
                Ast.Exp temp2 = parseAddExp();
                Ast.LVal left = extractLValFromExp(temp2);
                if (left == null) {
                    tokenList.consumeExpected(TokenType.SEMI);
                    return new Ast.ExpStmt(temp2);
                }
                else {
                    tokenList.consumeExpected(TokenType.ASSIGN);
                    Ast.Exp right = parseAddExp();
                    tokenList.consumeExpected(TokenType.SEMI);
                    return new Ast.Assign(left, right);
                }
            case SEMI:
                tokenList.consume();
                return new Ast.ExpStmt(null);
            default: throw new SyntaxException("");
        }
    }

    private Ast.LVal parseLVal() throws SyntaxException {
        Token ident = tokenList.consumeExpected(TokenType.IDENT);
        ArrayList<Ast.Exp> indexes = new ArrayList<>();
        while (tokenList.hasNext() && tokenList.get().isOf(TokenType.LBRACK)) {
            tokenList.consumeExpected(TokenType.LBRACK);
            indexes.add(parseAddExp());
            tokenList.consumeExpected(TokenType.RBRACK);
        }
        return new Ast.LVal(ident, indexes);
    }

    private Ast.PrimaryExp parsePrimary() throws SyntaxException {
        Token temp = tokenList.get();
        if (temp.isOf(TokenType.LPARENT)) {
            tokenList.consume();
            Ast.Exp exp = parseAddExp();
            tokenList.consumeExpected(TokenType.RPARENT);
            return exp;
        } else if (temp.isOf(TokenType.HEX_INT, TokenType.OCT_INT, TokenType.NUM_INT)) {
            Token number = tokenList.consume();
            return new Ast.Number(number);
        } else if (temp.isOf(TokenType.IDENT)
                && tokenList.ahead(1).isOf(TokenType.LPARENT)) {
            return parseCall();
        } else {
            return parseLVal();
        }
    }

    private Ast.Call parseCall() throws SyntaxException {
        Token ident = tokenList.consumeExpected(TokenType.IDENT);
        ArrayList<Ast.Exp> params = new ArrayList<>();
        tokenList.consumeExpected(TokenType.LPARENT);
        if (!tokenList.get().getContent().equals(")")) {
            params.add(parseAddExp());
            while (tokenList.get().isOf(TokenType.COMMA)) {
                tokenList.consume();
                params.add(parseAddExp());
            }
        }
        tokenList.consumeExpected(TokenType.RPARENT);
        return new Ast.Call(ident, params);
    }

    // 二元表达式的种类
    private enum BinaryExpType {
        LOR(TokenType.LOR),
        LAND(TokenType.LAND),
        EQ(TokenType.EQ, TokenType.NE),
        REL(TokenType.GT, TokenType.LT, TokenType.GE, TokenType.LE),
        ADD(TokenType.ADD, TokenType.SUB),
        MUL(TokenType.MUL, TokenType.DIV, TokenType.MOD),
        ;

        private final List<TokenType> types;

        BinaryExpType(TokenType... types) {
            this.types = List.of(types);
        }

        public boolean contains(TokenType type) {
            return types.contains(type);
        }
    }

    // 解析二元表达式的下一层表达式
    private Ast.Exp parseSubBinaryExp(BinaryExpType expType) throws SyntaxException {
        return switch (expType) {
            case LOR -> parseBinaryExp(BinaryExpType.LAND);
            case LAND -> parseBinaryExp(BinaryExpType.EQ);
            case EQ -> parseBinaryExp(BinaryExpType.REL);
            case REL -> parseBinaryExp(BinaryExpType.ADD);
            case ADD -> parseBinaryExp(BinaryExpType.MUL);
            case MUL -> parseUnaryExp();
            default -> throw new AssertionError("Bad BinaryExpType");
        };
    }

    // 解析二元表达式
    private Ast.BinaryExp parseBinaryExp(BinaryExpType expType) throws SyntaxException {
        Ast.Exp first = parseSubBinaryExp(expType);
        ArrayList<Token> operators = new ArrayList<>();
        ArrayList<Ast.Exp> follows = new ArrayList<>();
        while (tokenList.hasNext() && expType.contains(tokenList.get().getType())) {
            Token op = tokenList.consume(); // 取得当前层次的运算符
            operators.add(op);
            follows.add(parseSubBinaryExp(expType));
        }
        return new Ast.BinaryExp(first, operators, follows);
    }

    private Ast.UnaryExp parseUnaryExp() throws SyntaxException {
        ArrayList<Token> unaryOps = new ArrayList<>();
        while (tokenList.get().isOf(TokenType.ADD, TokenType.SUB, TokenType.NOT)) {
            unaryOps.add(tokenList.consume());
        }
        Ast.PrimaryExp primary = parsePrimary();
        return new Ast.UnaryExp(unaryOps, primary);
    }

    private Ast.BinaryExp parseAddExp() throws SyntaxException {
        return parseBinaryExp(BinaryExpType.ADD);
    }

    private Ast.BinaryExp parseCond() throws SyntaxException {
        return parseBinaryExp(BinaryExpType.LOR);
    }

    // 从 Exp 中提取一个 LVal (如果不是仅有一个 LVal) 则返回 null
    private Ast.LVal extractLValFromExp(Ast.Exp exp) {
        Ast.Exp cur = exp;
        while (cur instanceof Ast.BinaryExp) {
            // 如果是二元表达式，只能有 first 否则一定不是一个 LVal
            if (!(((Ast.BinaryExp) cur).getFollows().isEmpty())) {
                return null;
            }
            cur = ((Ast.BinaryExp) cur).getFirst();
        }
        assert cur instanceof Ast.UnaryExp;
        if (!(((Ast.UnaryExp) cur).getUnaryOps().isEmpty())) {
            return null; // 不能有一元运算符
        }
        Ast.PrimaryExp primary = ((Ast.UnaryExp) cur).getPrimary();
        if (primary instanceof Ast.LVal) {
            return (Ast.LVal) primary;
        } else {
            return null; // 不是 LVal
        }
    }
}