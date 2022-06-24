package frontend.syntax;

import frontend.lexer.Token;

import java.util.List;

/**
 * 所有的语法树节点
 * 为简化编译器实现难度, 对文法进行了改写(不影响语义)
 */
public class Ast {
    
    private final List<CompUnit> units;

    // CompUnit -> Decl | FuncDef
    public interface CompUnit {
    }

    // Decl -> ['const'] 'int' Def {',' Def} ';'
    public static class Decl implements CompUnit, BlockItem {
        private final boolean constant;
        
        private final Token bType;
        
        private final List<Def> defs;

        public Decl(final boolean constant,  final Token bType,  final List<Def> defs) {
            if (bType == null) {
                throw new NullPointerException("bType is marked non-null but is null");
            }
            if (defs == null) {
                throw new NullPointerException("defs is marked non-null but is null");
            }
            this.constant = constant;
            this.bType = bType;
            this.defs = defs;
        }

        public boolean isConstant() {
            return this.constant;
        }

        public Token getBType() {
            return this.bType;
        }

        public List<Def> getDefs() {
            return this.defs;
        }
    }

    // Def -> Ident {'[' Exp ']'} ['=' Init]
    public static class Def {
        
        private final Token ident;
        
        private final List<Exp> indexes;
        private final Init init;

        public Def( final Token ident,  final List<Exp> indexes, final Init init) {
            if (ident == null) {
                throw new NullPointerException("ident is marked non-null but is null");
            }
            if (indexes == null) {
                throw new NullPointerException("indexes is marked non-null but is null");
            }
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
        private final List<Init> init;

        public InitArray(final List<Init> init) {
            this.init = init;
        }

        public List<Init> getInit() {
            return this.init;
        }
    }

    // FuncDef -> FuncType Ident '(' [FuncFParams] ')' Block
    // FuncFParams -> FuncFParam {',' FuncFParam}
    public static class FuncDef implements CompUnit {
        
        private final Token type; // FuncType
        private final Token ident; // name
        private final List<FuncFParam> fParams;
        private final Block body;

        public FuncDef( final Token type,  final Token ident,  final List<FuncFParam> fParams,  final Block body) {
            if (type == null) {
                throw new NullPointerException("type is marked non-null but is null");
            }
            if (ident == null) {
                throw new NullPointerException("ident is marked non-null but is null");
            }
            if (fParams == null) {
                throw new NullPointerException("fParams is marked non-null but is null");
            }
            if (body == null) {
                throw new NullPointerException("body is marked non-null but is null");
            }
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
        
        private final Token bType;
        private final Token ident;
        private final boolean array; // whether it is an array
        private final List<Exp> sizes; // array sizes of each dim

        public FuncFParam( final Token bType,  final Token ident, final boolean array,  final List<Exp> sizes) {
            if (bType == null) {
                throw new NullPointerException("bType is marked non-null but is null");
            }
            if (ident == null) {
                throw new NullPointerException("ident is marked non-null but is null");
            }
            if (sizes == null) {
                throw new NullPointerException("sizes is marked non-null but is null");
            }
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
        
        private final List<BlockItem> items;

        public Block( final List<BlockItem> items) {
            if (items == null) {
                throw new NullPointerException("items is marked non-null but is null");
            }
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
        
        private final LVal left;
        
        private final Exp right;

        public Assign( final LVal left,  final Exp right) {
            if (left == null) {
                throw new NullPointerException("left is marked non-null but is null");
            }
            if (right == null) {
                throw new NullPointerException("right is marked non-null but is null");
            }
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
        private final Exp exp; // nullable, empty stmt if null

        public ExpStmt(final Exp exp) {
            this.exp = exp;
        }

        public Exp getExp() {
            return this.exp;
        }
    }

    // IfStmt
    public static class IfStmt implements Stmt {
        
        private final Exp cond;
        
        private final Stmt thenTarget;
        private final Stmt elseTarget;

        public IfStmt( final Exp cond,  final Stmt thenTarget, final Stmt elseTarget) {
            if (cond == null) {
                throw new NullPointerException("cond is marked non-null but is null");
            }
            if (thenTarget == null) {
                throw new NullPointerException("thenTarget is marked non-null but is null");
            }
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
        
        private final Exp cond;
        
        private final Stmt body;

        public WhileStmt( final Exp cond,  final Stmt body) {
            if (cond == null) {
                throw new NullPointerException("cond is marked non-null but is null");
            }
            if (body == null) {
                throw new NullPointerException("body is marked non-null but is null");
            }
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
        private final Exp value;

        
        public Return(final Exp value) {
            this.value = value;
        }

        
        public Exp getValue() {
            return this.value;
        }
    }

    // Exp -> BinaryExp | UnaryExp
    public interface Exp extends Init, PrimaryExp {
    }

    // BinaryExp: Arithmetic, Relation, Logical
    // BinaryExp -> Exp { Op Exp }, calc from left to right
    public static class BinaryExp implements Exp {
        
        private final Exp first;
        
        private final List<Token> operators;
        
        private final List<Exp> follows;

        
        public BinaryExp( final Exp first,  final List<Token> operators,  final List<Exp> follows) {
            if (first == null) {
                throw new NullPointerException("first is marked non-null but is null");
            }
            if (operators == null) {
                throw new NullPointerException("operators is marked non-null but is null");
            }
            if (follows == null) {
                throw new NullPointerException("follows is marked non-null but is null");
            }
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
        
        private final List<Token> unaryOps;
        
        private final PrimaryExp primary;

        public UnaryExp( final List<Token> unaryOps,  final PrimaryExp primary) {
            if (unaryOps == null) {
                throw new NullPointerException("unaryOps is marked non-null but is null");
            }
            if (primary == null) {
                throw new NullPointerException("primary is marked non-null but is null");
            }
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
        
        private final Token ident;
        
        private final List<Exp> indexes;

        
        public LVal( final Token ident,  final List<Exp> indexes) {
            if (ident == null) {
                throw new NullPointerException("ident is marked non-null but is null");
            }
            if (indexes == null) {
                throw new NullPointerException("indexes is marked non-null but is null");
            }
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
        
        private final Token number;

        public Number( final Token number) {
            if (number == null) {
                throw new NullPointerException("number is marked non-null but is null");
            }
            this.number = number;
        }

        public Token getNumber() {
            return this.number;
        }
    }

    // Call -> Ident '(' [ Exp {',' Exp} ] ')'
    // FuncRParams -> Exp {',' Exp}, already inlined in Call
    public static class Call implements PrimaryExp {
        
        private final Token ident;
        
        private final List<Exp> params;

        public Call( final Token ident,  final List<Exp> params) {
            if (ident == null) {
                throw new NullPointerException("ident is marked non-null but is null");
            }
            if (params == null) {
                throw new NullPointerException("params is marked non-null but is null");
            }
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

    public Ast( final List<CompUnit> units) {
        if (units == null) {
            throw new NullPointerException("units is marked non-null but is null");
        }
        this.units = units;
    }
    
    public List<CompUnit> getUnits() {
        return this.units;
    }
    
}
