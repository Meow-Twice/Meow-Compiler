package frontend.semantic;

import exception.SemanticException;
import frontend.lexer.Token;
import frontend.lexer.TokenType;
import ir.BasicBlock;
import ir.IR;
import ir.Instr;
import ir.Val;
import frontend.semantic.symbol.SymTable;
import frontend.semantic.symbol.Symbol;
import frontend.syntax.Ast;

import java.util.*;

/**
 * 遍历语法树, 生成 IR 代码
 */
public class Visitor {
    private final IR ir = new IR(); // 最终生成的 IR
    private SymTable currentSymTable = new SymTable(); // 当前符号表, 初始时是全局符号表
    private Function currentFunc = null; // 当前正在分析的函数
    private BasicBlock currentBlock = null; // 当前所在的基本块

    private boolean isGlobal() {
        return currentFunc == null && currentBlock == null && !currentSymTable.hasParent();
    }

    private final Stack<BasicBlock> loopHeads = new Stack<>(); // for continue;
    private final Stack<BasicBlock> loopFollows = new Stack<>(); // for break;

    public Visitor() {
    }

    private Val trimTo(Val val, Types.BasicType type) throws SemanticException {
        if (val == null) {
            throw new SemanticException("Null val found!");
        }
        if (!(val.getType() instanceof Types.BasicType)) {
            throw new SemanticException("Cannot trim non-basic type");
        }
        if (val.getType().equals(type)) {
            return val;
        }
        Val.Var tmp = Val.newVar(type);
        if (type.equals(Types.BasicType.BOOL)) {
            currentBlock.append(new Instr.Cmp(tmp, Instr.Cmp.Op.NE, val, new Val.Num(0, val.getType())));
        } else {
            currentBlock.append(new Instr.Ext(tmp, val));
        }
        return tmp;
    }

    private boolean isAlu(Token token) {
        return token.isOf(TokenType.ADD, TokenType.SUB, TokenType.MUL, TokenType.DIV, TokenType.MOD);
    }

    private boolean isCmp(Token token) {
        return token.isOf(TokenType.GE, TokenType.GT, TokenType.LE, TokenType.LT, TokenType.EQ, TokenType.NE);
    }

    private boolean isLogical(Token token) {
        return token.isOf(TokenType.LAND, TokenType.LOR);
    }

    private Instr.Alu.Op aluOpHelper(Token token) {
        return switch (token.getType()) {
            case ADD -> Instr.Alu.Op.ADD;
            case SUB -> Instr.Alu.Op.SUB;
            case MUL -> Instr.Alu.Op.MUL;
            case DIV -> Instr.Alu.Op.DIV;
            case MOD -> Instr.Alu.Op.REM;
            default -> throw new AssertionError("Bad Alu Op");
        };
    }

    private Instr.Cmp.Op cmpOpHelper(Token token) {
        return switch (token.getType()) {
            case GE -> Instr.Cmp.Op.SGE;
            case GT -> Instr.Cmp.Op.SGT;
            case LE -> Instr.Cmp.Op.SLE;
            case LT -> Instr.Cmp.Op.SLT;
            case EQ -> Instr.Cmp.Op.EQ;
            case NE -> Instr.Cmp.Op.NE;
            default -> throw new AssertionError("Bad Cmp Op");
        };
    }

    public void visitAst(Ast ast) throws SemanticException {
        for (Ast.CompUnit unit : ast.getUnits()) {
            visitCompUnit(unit);
        }
    }

    private Val visitBinaryExp(Ast.BinaryExp exp) throws SemanticException {
        // 注意短路求值!
        Val first = visitExp(exp.getFirst());
        Iterator<Token> iterOp = exp.getOperators().listIterator();
        for (Ast.Exp nextExp : exp.getFollows()) {
            assert iterOp.hasNext();
            Token op = iterOp.next();
            if (isAlu(op)) {
                first = trimTo(first, Types.BasicType.INT);
                Instr.Alu.Op aluOp = aluOpHelper(op);
                Val second = visitExp(nextExp);
                second = trimTo(second, Types.BasicType.INT);
                Val.Var tmp = Val.newVar(Types.BasicType.INT);
                currentBlock.append(new Instr.Alu(tmp, aluOp, first, second));
                first = tmp;
            } else if (isCmp(op)) {
                first = trimTo(first, Types.BasicType.INT);
                Instr.Cmp.Op cmpOp = cmpOpHelper(op);
                Val second = visitExp(nextExp);
                second = trimTo(second, Types.BasicType.INT);
                Val.Var tmp = Val.newVar(Types.BasicType.BOOL);
                currentBlock.append(new Instr.Cmp(tmp, cmpOp, first, second));
                first = tmp;
            } else if (isLogical(op)) {
                // 短路求值
                Val.Var tmp1 = Val.newVar(Types.BasicType.BOOL);
                first = trimTo(first, Types.BasicType.BOOL);
                BasicBlock from1 = currentBlock;
                BasicBlock next = new BasicBlock();
                BasicBlock follow = new BasicBlock();
                currentBlock.append(new Instr.Alu(tmp1, Instr.Alu.Op.AND, first, new Val.Num(1, Types.BasicType.BOOL))); // mov first -> tmp
                if (op.isOf(TokenType.LAND)) {
                    // first && second
                    // if first is true, jump to follow
                    // else jump to next
                    currentBlock.append(new Instr.Branch(first, next, follow));
                } else if (op.isOf(TokenType.LOR)) {
                    // if first is false, jump to follow
                    // else jump to next
                    currentBlock.append(new Instr.Branch(first, follow, next));
                } else {
                    throw new AssertionError("Bad Logical Op");
                }
                assert currentBlock.isTerminated();
                currentBlock = next;
                Val second = visitExp(nextExp);
                second = trimTo(second, Types.BasicType.BOOL);
                BasicBlock from2 = currentBlock;
                Val.Var tmp2 = Val.newVar(Types.BasicType.BOOL);
                currentBlock.append(new Instr.Alu(tmp2, Instr.Alu.Op.AND, second, new Val.Num(1, Types.BasicType.BOOL))); // mov second -> tmp
                currentBlock.append(new Instr.Jump(follow));
                assert currentBlock.isTerminated();
                currentBlock = follow;
                // need a Phi: from, next -> follow
                Val.Var phi = Val.newVar(Types.BasicType.BOOL);
                Map<Val, BasicBlock> phiSrc = new HashMap<>();
                phiSrc.put(tmp1, from1);
                phiSrc.put(tmp2, from2);
                currentBlock.append(new Instr.Phi(phi, phiSrc));
                first = phi;
            } else {
                throw new AssertionError("Bad Binary Op");
            }
        }
        return first;
    }

    private Val visitUnaryExp(Ast.UnaryExp exp) throws SemanticException {
        Val primary = visitPrimaryExp(exp.getPrimary());
        // 从右向左结合
        ArrayList<Token> unaryOps = new ArrayList<>(exp.getUnaryOps());
        for (int i = unaryOps.size() - 1; i >= 0; i--) {
            Token op = unaryOps.get(i);
            Val.Var tmp;
            if (op.getType().equals(TokenType.NOT)) {
                primary = trimTo(primary, Types.BasicType.BOOL);
                tmp = Val.newVar(Types.BasicType.BOOL);
                currentBlock.append(new Instr.Cmp(tmp, Instr.Cmp.Op.EQ, primary, new Val.Num(0, primary.getType())));
            } else {
                assert primary.getType() instanceof Types.BasicType;
                primary = trimTo(primary, Types.BasicType.INT);
                tmp = Val.newVar(Types.BasicType.INT);
                Instr.Alu.Op aluOp = switch (op.getType()) {
                    case ADD -> Instr.Alu.Op.ADD;
                    case SUB -> Instr.Alu.Op.SUB;
                    default -> throw new AssertionError("Bad UnaryOp");
                };
                currentBlock.append(new Instr.Alu(tmp, aluOp, new Val.Num(0, Types.BasicType.INT), primary));
            }
            primary = tmp;
        }
        return primary;
    }

    private Val visitExp(Ast.Exp exp) throws SemanticException {
        if (exp instanceof Ast.BinaryExp) {
            return visitBinaryExp((Ast.BinaryExp) exp);
        } else if (exp instanceof Ast.UnaryExp) {
            return visitUnaryExp((Ast.UnaryExp) exp);
        } else {
            throw new AssertionError("Bad Exp");
        }
    }

    private Val visitPrimaryExp(Ast.PrimaryExp exp) throws SemanticException {
        if (exp instanceof Ast.Exp) {
            return visitExp((Ast.Exp) exp);
        } else if (exp instanceof Ast.LVal) {
            return visitLVal((Ast.LVal) exp, false);
        } else if (exp instanceof Ast.Number) {
            return visitNumber((Ast.Number) exp);
        } else if (exp instanceof Ast.Call) {
            return visitCall((Ast.Call) exp);
        } else {
            throw new AssertionError("Bad PrimaryExp");
        }
    }

    // if left: return address, else return value (generate load instruction)
    private Val visitLVal(Ast.LVal lVal, boolean left) throws SemanticException {
        // 去符号表拿出指向这个左值的指针
        String ident = lVal.getIdent().getContent();
        Symbol symbol = currentSymTable.get(ident, true);
        if (left && symbol.isConstant()) {
            throw new SemanticException("Modify const");
        }
        Val address = symbol.getPointer();
        assert address.getType() instanceof Types.PointerType;
        // 处理数组的偏移寻址
        // 遍历下标，逐层 getelementptr, 每层均进行一个解引用和一个偏移
        for (Ast.Exp exp : lVal.getIndexes()) {
            if (!(address.getType() instanceof Types.PointerType)) {
                throw new SemanticException("Non-Array has indexes");
            }
            Val offset = visitExp(exp);
            if (!(offset.getType() instanceof Types.BasicType)) {
                throw new SemanticException("Index not number");
            }
            offset = trimTo(offset, Types.BasicType.INT);
            Types nextType = ((Types.PointerType) address.getType()).getBase(); // 实体的类型
            assert !(nextType instanceof Types.BasicType);
            Val.Var elem;
            // 实体是数组, 地址是数组指针, getelementptr 要有两层
            // 实体是指针, 地址是二级指针, getelementptr 应有一层(仅在含有数组形参的函数中有), 同时还有个 load
            if (nextType instanceof Types.PointerType) {
                Val.Var basePtr = Val.newVar(nextType);
                currentBlock.append(new Instr.Load(basePtr, address));
                elem = Val.newVar(nextType);
                currentBlock.append(new Instr.GetElementPtr(elem, basePtr, offset, false));
            } else {
                assert nextType instanceof Types.ArrayType;
                nextType = ((Types.ArrayType) nextType).getBase();
                elem = Val.newVar(new Types.PointerType(nextType));
                currentBlock.append(new Instr.GetElementPtr(elem, address, offset, true));
            }
            address = elem;
        }
        assert address.getType() instanceof Types.PointerType;
        if (left) {
            // 如果作为左值, 一定不能是部分数组
            // 是数组元素的条件: address
            if (!(((Types.PointerType) address.getType()).getBase() instanceof Types.BasicType)) {
                throw new SemanticException("Part-array cannot be left value");
            }
            assert ((Types.PointerType) address.getType()).getBase() instanceof Types.BasicType;
            return address; // 返回一个可以直接 store i32 值的指针
        } else {
            // 如果是数组元素或者普通的值, 就 load; 如果是部分数组(仅用于函数传参), 应该得到一个降维的数组指针;
            // 如果是将数组指针作为参数继续传递, 也需要 load 来解引用
            Types baseType = ((Types.PointerType) address.getType()).getBase();
            if (baseType instanceof Types.BasicType || baseType instanceof Types.PointerType) {
                Val.Var val = Val.newVar(baseType);
                currentBlock.append(new Instr.Load(val, address));
                return val;
            } else if (baseType instanceof Types.ArrayType) {
                // [2 x [3 x [4 x i32]]] a: a[2] -> int p[][4]
                // &a: [2 x [3 x [4 x i32]]]*, &(a[2]): [3 x [4 x i32]]*, p: [4 x i32]*
                // [3 x [4 x i32]] b: b[3] -> int p[][4]
                // &b: [3 x [4 x i32]]*, &(b[3]): [4 x i32]*, p: i32*
                // 数组解引用
                Val.Var ptr = Val.newVar(new Types.PointerType(((Types.ArrayType) baseType).getBase()));
                currentBlock.append(new Instr.GetElementPtr(ptr, address, new Val.Num(0, Types.BasicType.INT), true)); // ???
                return ptr;
            } else {
                throw new AssertionError("Bad baseType");
            }
        }
    }

    private Val visitNumber(Ast.Number number) {
        return new Val.Num(new Evaluate(currentSymTable, false).evalNumber(number), Types.BasicType.INT);
    }

    // returns the return value if function call, null if function is void
    private Val visitCall(Ast.Call call) throws SemanticException {
        String ident = call.getIdent().getContent();
        Function function = ir.getFunctions().get(ident);
        if (function == null) {
            throw new SemanticException("Function " + ident + " not declared.");
        }
        ArrayList<Val> params = new ArrayList<>();
        for (Ast.Exp exp : call.getParams()) {
            params.add(visitExp(exp));
        }
        if (function.hasRet()) {
            Val.Var ret = Val.newVar(function.getRetType());
            currentBlock.append(new Instr.Call(ret, function, params));
            return ret;
        } else {
            currentBlock.append(new Instr.Call(null, function, params));
            return null;
        }
    }

    private void visitAssign(Ast.Assign assign) throws SemanticException {
        Val left = visitLVal(assign.getLeft(), true);
        Val right = trimTo(visitExp(assign.getRight()), Types.BasicType.INT);
        assert left.getType() instanceof Types.PointerType; // 分析出来的左值一定是指针类型
        assert right.getType().equals(((Types.PointerType) left.getType()).getBase()); // 分析出来的右值一定是左值指针解引用的类型
        currentBlock.append(new Instr.Store(right, left));
    }

    private void visitExpStmt(Ast.ExpStmt expStmt) throws SemanticException {
        Ast.Exp exp = expStmt.getExp();
        if (exp != null) {
            visitExp(expStmt.getExp());
        }
    }

    private void visitIfStmt(Ast.IfStmt ifStmt) throws SemanticException {
        Val cond = visitExp(ifStmt.getCond());
        cond = trimTo(cond, Types.BasicType.BOOL);
        Ast.Stmt thenTarget = ifStmt.getThenTarget();
        Ast.Stmt elseTarget = ifStmt.getElseTarget();
        BasicBlock thenBlock = new BasicBlock();
        BasicBlock follow = new BasicBlock();
        if (elseTarget == null) {
            currentBlock.append(new Instr.Branch(cond, thenBlock, follow));
            currentBlock = thenBlock;
            visitStmt(thenTarget); // currentBlock may be modified
        } else {
            BasicBlock elseBlock = new BasicBlock();
            currentBlock.append(new Instr.Branch(cond, thenBlock, elseBlock));
            currentBlock = thenBlock;
            visitStmt(thenTarget);
            currentBlock.append(new Instr.Jump(follow));
            currentBlock = elseBlock;
            visitStmt(elseTarget);
        }
        currentBlock.append(new Instr.Jump(follow));
        currentBlock = follow;
    }

    private void visitWhileStmt(Ast.WhileStmt whileStmt) throws SemanticException {
        BasicBlock head = new BasicBlock();
        currentBlock.append(new Instr.Jump(head));
        currentBlock = head;
        Val cond = visitExp(whileStmt.getCond());
        cond = trimTo(cond, Types.BasicType.BOOL);
        BasicBlock body = new BasicBlock();
        BasicBlock follow = new BasicBlock();
        currentBlock.append(new Instr.Branch(cond, body, follow));
        currentBlock = body;
        loopHeads.push(head);
        loopFollows.push(follow);
        visitStmt(whileStmt.getBody());
        loopHeads.pop();
        loopFollows.pop();
        currentBlock.append(new Instr.Jump(head));
        currentBlock = follow;
    }

    private void visitBreak() throws SemanticException {
        if (loopFollows.empty()) {
            throw new SemanticException("Break not in loop");
        }
        currentBlock.append(new Instr.Jump(loopFollows.peek()));
    }

    private void visitContinue() throws SemanticException {
        if (loopHeads.empty()) {
            throw new SemanticException("Continue not in loop");
        }
        currentBlock.append(new Instr.Jump(loopHeads.peek()));
    }

    private void visitReturn(Ast.Return ret) throws SemanticException {
        Ast.Exp retExp = ret.getValue();
        if (retExp == null) {
            currentBlock.append(new Instr.Return(null));
        } else {
            currentBlock.append(new Instr.Return(visitExp(retExp)));
        }
    }

    private void visitStmt(Ast.Stmt stmt) throws SemanticException {
        if (stmt instanceof Ast.Assign) {
            visitAssign((Ast.Assign) stmt);
        } else if (stmt instanceof Ast.ExpStmt) {
            visitExpStmt((Ast.ExpStmt) stmt);
        } else if (stmt instanceof Ast.IfStmt) {
            visitIfStmt((Ast.IfStmt) stmt);
        } else if (stmt instanceof Ast.WhileStmt) {
            visitWhileStmt((Ast.WhileStmt) stmt);
        } else if (stmt instanceof Ast.Break) {
            visitBreak();
        } else if (stmt instanceof Ast.Continue) {
            visitContinue();
        } else if (stmt instanceof Ast.Return) {
            visitReturn((Ast.Return) stmt);
        } else if (stmt instanceof Ast.Block) {
            visitBlock((Ast.Block) stmt, true);
        } else {
            throw new AssertionError("Bad Stmt");
        }
    }

    private void visitBlockItem(Ast.BlockItem item) throws SemanticException {
        if (item instanceof Ast.Stmt) {
            visitStmt((Ast.Stmt) item);
        } else if (item instanceof Ast.Decl) {
            visitDecl((Ast.Decl) item);
        } else {
            throw new AssertionError("Bad BlockItem");
        }
    }

    // sym: 是否需要新开一层符号表
    private void visitBlock(Ast.Block block, boolean sym) throws SemanticException {
        assert currentBlock != null;
        BasicBlock inner = new BasicBlock();
        BasicBlock follow = new BasicBlock();
        currentBlock.append(new Instr.Jump(inner));
        currentBlock = inner;
        if (sym) {
            currentSymTable = new SymTable(currentSymTable);
        } // 新开一层符号表
        for (Ast.BlockItem item : block.getItems()) {
            visitBlockItem(item);
        }
        if (sym) {
            currentSymTable = currentSymTable.getParent();
        } // 退出一层符号表
        currentBlock.append(new Instr.Jump(follow));
        currentBlock = follow;
    }

    private void visitDecl(Ast.Decl decl) throws SemanticException {
        Token type = decl.getBType();
        if (!type.isOf(TokenType.INT)) {
            throw new SemanticException("BType not int");
        }
        for (Ast.Def def : decl.getDefs()) {
            boolean eval = (decl.isConstant()) || (currentFunc == null); // 局部变量,可以运行时初始化
            visitDef(def, decl.isConstant(), eval);
        }
    }

    private void initZeroHelper(Val.Var pointer) {
        // 将一整个局部数组利用 memset 全部初始化为零
        // 一层一层拆类型并得到总大小
        assert pointer.getType() instanceof Types.PointerType;
        Types baseType = ((Types.PointerType) pointer.getType()).getBase();
        Val.Var ptr = pointer;
        int size = 1;
        while (baseType instanceof Types.ArrayType) {
            size *= ((Types.ArrayType) baseType).getSize();
            Types innerType = ((Types.ArrayType) baseType).getBase();
            Val.Var innerPtr = Val.newVar(new Types.PointerType(innerType));
            currentBlock.append(new Instr.GetElementPtr(innerPtr, ptr, new Val.Num(0), true));
            ptr = innerPtr;
            baseType = innerType;
        }
        size *= 4; // sizeof int
        assert ptr.getType() instanceof Types.PointerType && ((Types.PointerType) ptr.getType()).getBase().equals(Types.BasicType.INT);
        currentBlock.append(new Instr.Call(null, IR.ExternFunction.MEM_SET, List.of(ptr, new Val.Num(0), new Val.Num(size))));
    }

    private void initHelper(Val.Var pointer, Initial init) {
        assert currentBlock != null && currentFunc != null;
        Types type = pointer.getType();
        assert type instanceof Types.PointerType;
        Types baseType = ((Types.PointerType) type).getBase();
        if (init instanceof Initial.ExpInit) {
            currentBlock.append(new Instr.Store(((Initial.ExpInit) init).getResult(), pointer));
        } else if (init instanceof Initial.ValueInit) {
            currentBlock.append(new Instr.Store(new Val.Num(((Initial.ValueInit) init).getValue()), pointer));
        } else if (init instanceof Initial.ArrayInit) {
            Initial.ArrayInit arrayInit = (Initial.ArrayInit) init;
            assert baseType instanceof Types.ArrayType;
            int len = arrayInit.length();
            for (int i = 0; i < len; i++) {
                Val.Var ptr = Val.newVar(new Types.PointerType(((Types.ArrayType) baseType).getBase()));
                currentBlock.append(new Instr.GetElementPtr(ptr, pointer, new Val.Num(i), true));
                initHelper(ptr, arrayInit.get(i));
            }
        }
    }

    private void visitDef(Ast.Def def, boolean constant, boolean eval) throws SemanticException {
        String ident = def.getIdent().getContent();
        if (currentSymTable.contains(ident, false)) {
            throw new SemanticException("Duplicated variable definition");
        }
        Types type = Types.BasicType.INT;
        // 编译期计算数组每一维的长度，然后从右向左"组装"成数组类型
        ArrayList<Integer> lengths = new ArrayList<>();
        for (Ast.Exp len : def.getIndexes()) {
            lengths.add(new Evaluate(currentSymTable, true).evalIntExp(len));
        }
        for (int i = lengths.size() - 1; i >= 0; i--) {
            int len = lengths.get(i);
            type = new Types.ArrayType(len, type);
        }
        // 构造该类型的指针
        Val.Var pointer;
        if (!isGlobal()) {
            pointer = Val.newVar(new Types.PointerType(type)); // 局部变量
        } else {
            pointer = new Val.Var(ident, new Types.PointerType(type), true, constant); // 全局变量
        }
        // 解析其初始化内容
        Ast.Init astInit = def.getInit();
        Initial init = null;
        if (astInit != null) {
            if (type instanceof Types.BasicType) {
                if (!(astInit instanceof Ast.Exp)) {
                    throw new SemanticException("Value variable not init by value");
                }
                if (eval) {
                    init = visitInitVal((Ast.Exp) astInit, constant || isGlobal());
                } else {
                    init = visitInitExp((Ast.Exp) astInit);
                }
            } else {
                // ArrayType
                if (!(astInit instanceof Ast.InitArray)) {
                    throw new SemanticException("Array variable not init by a list");
                }
                init = visitInitArray((Ast.InitArray) astInit, (Types.ArrayType) type, constant, eval);
            }
        }
        // 如果是全局变量且没有初始化，则初始化为零
        if (isGlobal() && init == null) {
            if (type instanceof Types.BasicType) {
                init = new Initial.ValueInit(0, type);
            } else {
                init = new Initial.ZeroInit(type);
            }
        }
        // 构建符号表项并插入符号表
        Symbol symbol = new Symbol(ident, type, init, constant, pointer);
        currentSymTable.add(symbol);
        // 全局: 直接给出初始化结果, 局部: 分配空间, store + memset 初始化的值
        if (currentFunc == null) {
            // 全局
            ir.addGlobal(symbol);
        } else {
            // 局部
            // 分配的空间指向 pointer
            assert currentBlock != null;
            currentBlock.append(new Instr.Alloc(pointer, type));
            if (type instanceof Types.ArrayType) {
                initZeroHelper(pointer);
            }
            initHelper(pointer, init); // 生成初始化
        }
    }

    private Initial.ArrayInit visitInitArray(Ast.InitArray initial, Types.ArrayType type, boolean constant, boolean eval) throws SemanticException {
        Initial.ArrayInit arrayInit = new Initial.ArrayInit(type);
        int count = 0;
        for (Ast.Init init : initial.getInit()) {
            count++; // 统计已经初始化了多少个
            if (init instanceof Ast.Exp) {
                // 是单个数
                if (!(type.getBase() instanceof Types.BasicType)) {
                    throw new SemanticException("Array initializer to a value type");
                }
                if (eval) {
                    // 必须编译期计算
                    arrayInit.add(visitInitVal((Ast.Exp) init, constant));
                } else {
                    assert !constant;
                    arrayInit.add(visitInitExp((Ast.Exp) init));
                }
            } else {
                // 子数组的初始化
                // 类型拆一层
                assert type.getBase() instanceof Types.ArrayType;
                Initial innerInit = visitInitArray((Ast.InitArray) init, (Types.ArrayType) type.getBase(), constant, eval);
                arrayInit.add(innerInit);
            }
        }
        while (count < type.getSize()) {
            // 初始化个数小于当前维度的长度，补零
            count++;
            if (type.getBase() instanceof Types.BasicType) {
                arrayInit.add(new Initial.ValueInit(0, Types.BasicType.INT));
            } else {
                assert type.getBase() instanceof Types.ArrayType;
                arrayInit.add(new Initial.ZeroInit(type.getBase()));
            }
        }
        return arrayInit;
    }

    private Initial.ValueInit visitInitVal(Ast.Exp exp, boolean constant) throws SemanticException {
        int eval = new Evaluate(currentSymTable, constant).evalIntExp(exp);
        return new Initial.ValueInit(eval, Types.BasicType.INT);
    }

    private Initial.ExpInit visitInitExp(Ast.Exp exp) throws SemanticException {
        Val eval = visitExp(exp); // 运行期才计算
        return new Initial.ExpInit(eval, Types.BasicType.INT);
    }

    // 由于编译器采用 Load-Store 形式，变量符号全部对应指针，所以在函数入口一开始先把形参全存到栈上 hhh
    private void visitFuncDef(Ast.FuncDef def) throws SemanticException {
        TokenType funcTypeTk = def.getType().getType();
        Types retType = funcTypeTk.equals(TokenType.VOID) ? null : Types.BasicType.INT;
        String ident = def.getIdent().getContent();
        if (ir.getFunctions().containsKey(ident)) {
            throw new SemanticException("Duplicated defined function");
        }
        // 入口基本块
        BasicBlock entry = new BasicBlock();
        currentBlock = entry;
        // 构造形参层符号表
        currentSymTable = new SymTable(currentSymTable);
        // 形参表
        ArrayList<Val.Var> params = new ArrayList<>();
        for (Ast.FuncFParam fParam : def.getFParams()) {
            Symbol paramSymbol = visitFuncFParam(fParam);
            currentSymTable.add(paramSymbol);
            Val.Var paramPtr = paramSymbol.getPointer(); // not assigned yet
            assert paramPtr.getType() instanceof Types.PointerType;
            Types paramType = ((Types.PointerType) paramPtr.getType()).getBase();
            Val.Var param = Val.Var.newVar(paramType);
            params.add(param); // 形参变量
            currentBlock.append(new Instr.Alloc(paramPtr, paramType));
            currentBlock.append(new Instr.Store(param, paramPtr));
        }
        Function function = new Function(ident, params, retType);
        ir.addFunction(function);
        function.setBody(entry);
        currentFunc = function;
        visitBlock(def.getBody(), false); // 分析函数体
        if (!currentBlock.isTerminated()) {
            // 如果没有 return 补上一条
            if (retType == null) {
                currentBlock.append(new Instr.Return(null));
            } else {
                currentBlock.append(new Instr.Return(new Val.Num(0)));
            }
        }
        currentSymTable = currentSymTable.getParent();
        currentBlock = null;
        currentFunc = null;
    }

    private Symbol visitFuncFParam(Ast.FuncFParam param) throws SemanticException {
        // 常数, 常数指针或者数组指针(注意降维)
        String ident = param.getIdent().getContent();
        if (!param.isArray()) {
            Val.Var ptr = Val.newVar(new Types.PointerType(Types.BasicType.INT));
            return new Symbol(ident, Types.BasicType.INT, null, false, ptr);
        } else {
            ArrayList<Integer> lengths = new ArrayList<>();
            for (Ast.Exp index : param.getSizes()) {
                int len = new Evaluate(currentSymTable, true).evalIntExp(index);
                lengths.add(len);
            }
            Types paramType = Types.BasicType.INT;
            for (int i = lengths.size() - 1; i >= 0; i--) {
                int len = lengths.get(i);
                paramType = new Types.ArrayType(len, paramType);
            }
            paramType = new Types.PointerType(paramType); // 降维数组, 在整数/数组上套一层指针
            Val.Var ptr = Val.newVar(new Types.PointerType(paramType)); // 再套一层指针用来 getelementptr
            return new Symbol(ident, paramType, null, false, ptr);
        }
    }

    private void visitCompUnit(Ast.CompUnit unit) throws SemanticException {
        assert isGlobal();
        if (unit instanceof Ast.Decl) {
            visitDecl((Ast.Decl) unit); // 全局变量
        } else if (unit instanceof Ast.FuncDef) {
            visitFuncDef((Ast.FuncDef) unit);
        } else {
            throw new AssertionError("Bad Compile Unit");
        }
    }

    
    
    public IR getIr() {
        return this.ir;
    }
    
}
