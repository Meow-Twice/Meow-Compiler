package frontend.syntax;

import frontend.lexer.Token;

import java.util.List;

/**
 * 所有的语法树节点
 * 为简化编译器实现难度, 对文法进行了改写(不影响语义)
 */
public class Ast {

    public List<CompUnit> units;

    // CompUnit -> Decl | FuncDef
    public interface CompUnit {
    }

    // Decl -> ['const'] 'int' Def {',' Def} ';'
    public static class Decl implements CompUnit, BlockItem {

        public boolean constant;
        public Token bType;
        public List<Def> defs;

        public Decl(boolean constant, Token bType, List<Def> defs) {
            assert bType != null;
            assert defs != null;
            this.constant = constant;
            this.bType = bType;
            this.defs = defs;
        }

        public boolean isConstant() {
            return this.constant;
        }

        public Token getBType() {
            return bType;
        }

        public List<Def> getDefs() {
            return defs;
        }
    }

    // Def -> Ident {'[' Exp ']'} ['=' Init]
    public static class Def {

        public Token ident;
        public List<Exp> indexes;
        public Init init;

        public Def(Token ident, List<Exp> indexes, Init init) {
            assert ident != null;
            assert indexes != null;
            assert init != null;
            this.ident = ident;
            this.indexes = indexes;
            this.init = init;
        }

        public Token getIdent() {
            return this.ident;
        }

        public List<Exp> getIndexes() {
            return this.indexes;
        }

        public Init getInit() {
            return this.init;
        }
    }

    // Init -> Exp | InitArray
    public interface Init {
    }

    // InitArray -> '{' [ Init { ',' Init } ] '}'
    public static class InitArray implements Init {
        public List<Init> init;

        public InitArray(List<Init> init) {
            assert init != null;
            this.init = init;
        }

        public List<Init> getInit() {
            return this.init;
        }
    }

    // FuncDef -> FuncType Ident '(' [FuncFParams] ')' Block
    // FuncFParams -> FuncFParam {',' FuncFParam}
    public static class FuncDef implements CompUnit {

        public Token type; // FuncType
        public Token ident; // name
        public List<FuncFParam> fParams;
        public Block body;

        public FuncDef(Token type, Token ident, List<FuncFParam> fParams, Block body) {
            assert type != null;
            assert ident != null;
            assert fParams != null;
            assert body != null;
            this.type = type;
            this.ident = ident;
            this.fParams = fParams;
            this.body = body;
        }

        public Token getType() {
            return this.type;
        }

        public Token getIdent() {
            return this.ident;
        }

        public List<FuncFParam> getFParams() {
            return this.fParams;
        }

        public Block getBody() {
            return this.body;
        }
    }

    // FuncFParam -> BType Ident ['[' ']' { '[' Exp ']' }]
    public static class FuncFParam {

        public Token bType;
        public Token ident;
        public boolean array; // whether it is an array
        public List<Exp> sizes; // array sizes of each dim

        public FuncFParam(Token bType, Token ident, boolean array, List<Exp> sizes) {
            assert bType != null;
            assert ident != null;
            assert sizes != null;
            this.bType = bType;
            this.ident = ident;
            this.array = array;
            this.sizes = sizes;
        }

        public Token getBType() {
            return this.bType;
        }

        public Token getIdent() {
            return this.ident;
        }

        public boolean isArray() {
            return this.array;
        }

        public List<Exp> getSizes() {
            return this.sizes;
        }
    }

    // Block
    public static class Block implements Stmt {

        public List<BlockItem> items;

        public Block(List<BlockItem> items) {
            assert items != null;
            this.items = items;
        }

        public List<BlockItem> getItems() {
            return this.items;
        }
    }

    // BlockItem -> Decl | Stmt
    public interface BlockItem {
    }

    // Stmt -> Assign | ExpStmt | Block | IfStmt | WhileStmt | Break | Continue | Return
    public interface Stmt extends BlockItem {
    }

    // Assign
    public static class Assign implements Stmt {

        public LVal left;
        public Exp right;

        public Assign(LVal left, Exp right) {
            assert left != null;
            assert right != null;
            this.left = left;
            this.right = right;
        }

        public LVal getLeft() {
            return this.left;
        }

        public Exp getRight() {
            return this.right;
        }
    }

    // ExpStmt
    public static class ExpStmt implements Stmt {
        public Exp exp; // nullable, empty stmt if null

        public ExpStmt(Exp exp) {
            // assert exp != null;
            this.exp = exp;
        }

        public Exp getExp() {
            return this.exp;
        }
    }

    // IfStmt
    public static class IfStmt implements Stmt {

        public Exp cond;
        public Stmt thenTarget;
        public Stmt elseTarget;

        public IfStmt(Exp cond, Stmt thenTarget, Stmt elseTarget) {
            assert cond != null;
            assert thenTarget != null;
            assert elseTarget != null;
            this.cond = cond;
            this.thenTarget = thenTarget;
            this.elseTarget = elseTarget;
        }

        public Exp getCond() {
            return this.cond;
        }

        public Stmt getThenTarget() {
            return this.thenTarget;
        }

        public Stmt getElseTarget() {
            return this.elseTarget;
        }
    }

    // WhileStmt
    public static class WhileStmt implements Stmt {

        public Exp cond;
        public Stmt body;

        public WhileStmt(Exp cond, Stmt body) {
            assert cond != null;
            assert body != null;
            this.cond = cond;
            this.body = body;
        }

        public Exp getCond() {
            return this.cond;
        }

        public Stmt getBody() {
            return this.body;
        }
    }

    // Break
    public static class Break implements Stmt {
        public Break() {
        }
    }

    // Continue
    public static class Continue implements Stmt {
        public Continue() {
        }
    }

    // Return
    public static class Return implements Stmt {
        public Exp value;

        public Return(Exp value) {
            assert value != null;
            this.value = value;
        }

        public Exp getRetExp() {
            return this.value;
        }
    }

    // PrimaryExp -> Call | '(' Exp ')' | LVal | Number
    // Init -> Exp | InitArray
    // Exp -> BinaryExp | UnaryExp
    public interface Exp extends Init, PrimaryExp {
    }

    // BinaryExp: Arithmetic, Relation, Logical
    // BinaryExp -> Exp { Op Exp }, calc from left to right
    public static class BinaryExp implements Exp {

        public Exp first;
        public List<Token> operators;
        public List<Exp> follows;

        public BinaryExp(Exp first, List<Token> operators, List<Exp> follows) {
            assert first != null;
            assert operators != null;
            assert follows != null;
            this.first = first;
            this.operators = operators;
            this.follows = follows;
        }

        public Exp getFirst() {
            return this.first;
        }

        public List<Token> getOperators() {
            return this.operators;
        }

        public List<Exp> getFollows() {
            return this.follows;
        }
    }

    // UnaryExp -> {UnaryOp} PrimaryExp
    public static class UnaryExp implements Exp {

        public List<Token> unaryOps;
        public PrimaryExp primary;

        public UnaryExp(List<Token> unaryOps, PrimaryExp primary) {
            assert unaryOps != null;
            assert primary != null;
            this.unaryOps = unaryOps;
            this.primary = primary;
        }

        public List<Token> getUnaryOps() {
            return this.unaryOps;
        }

        public PrimaryExp getPrimary() {
            return this.primary;
        }
    }

    // PrimaryExp -> Call | '(' Exp ')' | LVal | Number
    public interface PrimaryExp {
    }

    // LVal -> Ident {'[' Exp ']'}
    public static class LVal implements PrimaryExp {

        public Token ident;
        public List<Exp> indexes;

        public LVal(Token ident, List<Exp> indexes) {
            assert ident != null;
            assert indexes != null;
            this.ident = ident;
            this.indexes = indexes;
        }

        public Token getIdent() {
            return this.ident;
        }

        public List<Exp> getIndexes() {
            return this.indexes;
        }
    }

    // Number
    public static class Number implements PrimaryExp {

        public Token number;
        public boolean isIntConst = false;
        public boolean isFloatConst = false;
        public int intConstVal = 0;
        public float floatConstVal = (float) 0.0;

        public Number(Token number) {
            assert number != null;
            this.number = number;

            if (number.isIntConst()) {
                isIntConst = true;
                intConstVal = switch (number.getType()) {
                    case HEX_INT -> Integer.parseInt(number.getContent().substring(2), 16);
                    case OCT_INT -> Integer.parseInt(number.getContent().substring(1), 8);
                    case DEC_INT -> Integer.parseInt(number.getContent());
                    default -> throw new AssertionError("Bad Number!");
                };
                floatConstVal = (float) intConstVal;
            } else if (number.isFloatConst()) {
                isFloatConst = true;
                floatConstVal = Float.parseFloat(number.getContent());
                intConstVal = (int) floatConstVal;
            } else {
                assert isIntConst || isFloatConst;
            }
        }

        public Token getNumber() {
            return this.number;
        }

        public boolean isFloatConst() {
            return isFloatConst;
        }

        public boolean isIntConst() {
            return isIntConst;
        }

        public float getFloatConstVal() {
            return floatConstVal;
        }

        public int getIntConstVal() {
            return intConstVal;
        }

        @Override
        public String toString() {
            return isIntConst ? "int " + intConstVal : isFloatConst ? "float" + floatConstVal : "???" + number;
        }
    }

    // Call -> Ident '(' [ Exp {',' Exp} ] ')'
    // FuncRParams -> Exp {',' Exp}, already inlined in Call
    public static class Call implements PrimaryExp {

        public Token ident;
        public List<Exp> params;

        public Call(Token ident, List<Exp> params) {
            assert ident != null;
            assert params != null;
            this.ident = ident;
            this.params = params;
        }

        public Token getIdent() {
            return this.ident;
        }

        public List<Exp> getParams() {
            return this.params;
        }
    }

    public Ast(List<CompUnit> units) {
        assert units != null;
        this.units = units;
    }

    public List<CompUnit> getUnits() {
        return this.units;
    }

}
