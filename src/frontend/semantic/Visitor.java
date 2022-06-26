package frontend.semantic;

import frontend.syntax.Ast.*;
import exception.SemanticException;
import frontend.lexer.Token;
import frontend.lexer.TokenType;
import frontend.semantic.symbol.SymTable;
import frontend.semantic.symbol.Symbol;
import frontend.syntax.Ast;
import ir.*;
import ir.Instr.*;
import ir.type.Type;

import java.util.*;

import static ir.Constant.ConstantFloat.CONST_0F;
import static ir.Constant.ConstantInt.CONST_0;
import static ir.type.Type.BasicType.*;

/**
 * 遍历语法树, 生成 IR 代码
 */
public class Visitor {
    private final IR ir = new IR(); // 最终生成的 IR
    private SymTable currentSymTable = new SymTable(); // 当前符号表, 初始时是全局符号表
    private Function curFunc = null; // 当前正在分析的函数
    private BasicBlock curBB = null; // 当前所在的基本块

    private boolean isGlobal() {
        return curFunc == null && curBB == null && !currentSymTable.hasParent();
    }

    private final Stack<BasicBlock> loopHeads = new Stack<>(); // for continue;
    private final Stack<BasicBlock> loopFollows = new Stack<>(); // for break;

    public Visitor() {
    }

    private Value trimTo(Value value, BasicType targetType) /*throws SemanticException*/ {
        assert value != null;
        assert value.getType() instanceof BasicType;
        // Value res = value;
        if (value.getType().equals(targetType)) {
            System.err.printf("Try to trim %s to %s in Func(%s), %s", value, targetType,
                    ((Instr) value).parentBB().getFunction().getName(), ((Instr) value).parentBB().getLabel());
            // return value;
        }
        return switch (((BasicType) value.getType()).dataType) {
            case I1 -> switch (targetType.dataType) {
                case I32 -> new Zext(value, curBB);
                case F32 -> new SItofp(new Zext(value, curBB), curBB);
                case I1 -> value;
            };
            case I32 -> switch (targetType.dataType) {
                // TODO: 关于ConstVal是新建一个实例还是复用同一个常数，有待考察，此处使用唯一常量
                case I1 -> new Icmp(Icmp.Op.NE, value, CONST_0, curBB);
                case F32 -> new SItofp(value, curBB);
                case I32 -> value;
            };
            case F32 -> switch (targetType.dataType) {
                case I1 -> new Fcmp(Fcmp.Op.ONE, value, CONST_0F, curBB);
                case I32 -> new FPtosi(value, curBB);
                case F32 -> value;
            };
        };
    }

    private boolean isAlu(Token token) {
        return Token.isAlu(token.getType());
    }

    private boolean isCmp(Token token) {
        return Token.isCmp(token.getType());
    }

    private boolean isLogical(Token token) {
        return token.getType() == TokenType.LAND || token.getType() == TokenType.LOR;
    }

    private Alu.Op aluOpHelper(Token token) {
        return switch (token.getType()) {
            case ADD -> Alu.Op.ADD;
            case SUB -> Alu.Op.SUB;
            case MUL -> Alu.Op.MUL;
            case DIV -> Alu.Op.DIV;
            case MOD -> Alu.Op.REM;
            default -> throw new AssertionError("Bad Alu Op");
        };
    }

    private Alu.Op aluOpHelper(Token token, boolean needFloat) {
        return switch (token.getType()) {
            case ADD -> Alu.Op.FADD;
            case SUB -> Alu.Op.FSUB;
            case MUL -> Alu.Op.FMUL;
            case DIV -> Alu.Op.FDIV;
            case MOD -> Alu.Op.FREM;
            default -> throw new AssertionError(String.format("Bad Alu Op %s", token));
        };
    }

    private Icmp.Op icmpOpHelper(Token token) {
        return switch (token.getType()) {
            case GE -> Icmp.Op.SGE;
            case GT -> Icmp.Op.SGT;
            case LE -> Icmp.Op.SLE;
            case LT -> Icmp.Op.SLT;
            case EQ -> Icmp.Op.EQ;
            case NE -> Icmp.Op.NE;
            default -> throw new AssertionError(String.format("Bad icmp Op %s", token));
        };
    }

    private Fcmp.Op fcmpOpHelper(Token token) {
        return switch (token.getType()) {
            case GE -> Fcmp.Op.OGE;
            case GT -> Fcmp.Op.OGT;
            case LE -> Fcmp.Op.OLE;
            case LT -> Fcmp.Op.OLT;
            case EQ -> Fcmp.Op.OEQ;
            case NE -> Fcmp.Op.ONE;
            default -> throw new AssertionError(String.format("Bad fcmp Op %s", token));
        };
    }

    public void visitAst(Ast ast) throws SemanticException {
        for (CompUnit unit : ast.getUnits()) {
            visitCompUnit(unit);
        }
    }

    private boolean hasFloatType(Value v1, Value v2) {
        return v1.getType().isFloatType() || v2.getType().isFloatType();
    }

    private boolean hasIntType(Value v1, Value v2) {
        return v1.getType().isInt32Type() || v2.getType().isInt32Type();
    }

    private Value visitBinaryExp(BinaryExp exp) throws SemanticException {
        // 注意短路求值!
        // boolean flagAllNotLogical = true;
        // boolean flagAllLogical = true;
        // for (Token token : exp.getOperators()) {
        //     flagAllLogical = flagAllLogical && isLogical(token);
        //     flagAllNotLogical = flagAllNotLogical && !isLogical(token);
        // }
        // assert flagAllLogical || flagAllNotLogical;
        // BasicBlock trueBlock;
        // BasicBlock falseBlock;
        // BasicBlock nextBlock;
        // if(flagAllLogical){
        //     trueBlock = new BasicBlock(curFunc);
        //     falseBlock = new BasicBlock(curFunc);
        //     nextBlock = new BasicBlock(curFunc);
        //     new Jump(nextBlock, falseBlock);
        //     new Jump(nextBlock, trueBlock);
        // }
        Value first = visitExp(exp.getFirst());
        Iterator<Token> iterOp = exp.getOperators().listIterator();
        for (Exp nextExp : exp.getFollows()) {
            assert iterOp.hasNext();
            Token op = iterOp.next();
            if (isAlu(op)) {
                Value second = visitExp(nextExp);
                if (hasFloatType(first, second)) {
                    first = trimTo(first, F32_TYPE);
                    second = trimTo(second, F32_TYPE);
                    Alu.Op aluOp = aluOpHelper(op, true);
                    first = new Alu(F32_TYPE, aluOp, first, second, curBB);
                } else {
                    first = trimTo(first, I32_TYPE);
                    second = trimTo(second, I32_TYPE);
                    Alu.Op aluOp = aluOpHelper(op, false);
                    first = new Alu(I32_TYPE, aluOp, first, second, curBB);
                }
            } else if (isCmp(op)) {
                Value second = visitExp(nextExp);
                if (hasFloatType(first, second)) {
                    first = trimTo(first, F32_TYPE);
                    second = trimTo(second, F32_TYPE);
                    Fcmp.Op fcmpOp = fcmpOpHelper(op);
                    first = new Fcmp(fcmpOp, first, second, curBB);
                } else {
                    first = trimTo(first, I32_TYPE);
                    second = trimTo(second, I32_TYPE);
                    Icmp.Op icmpOp = icmpOpHelper(op);
                    first = new Icmp(icmpOp, first, second, curBB);
                }
            } else if (isLogical(op)) {
                first = trimTo(first, I1_TYPE);
                BasicBlock followBB = new BasicBlock(curFunc); // 实为trueBlock的前驱
                BasicBlock falseBB = new BasicBlock(curFunc); // 实为falseBlock
                if (op.getType() == TokenType.LAND) {
                    // first && second
                    // if first is true, jump to follow
                    // else jump to next
                    new Branch(first, falseBB, followBB, curBB);
                } else if (op.getType() == TokenType.LOR) {
                    // if first is false, jump to follow
                    // else jump to next
                    new Branch(first, followBB, falseBB, curBB);
                } else {
                    throw new AssertionError("Bad Logical Op");
                }
                assert curBB.isTerminated();
                curBB = followBB;
                first = visitExp(nextExp);
            } else {
                throw new AssertionError("Bad Binary Op");
            }
        }
        return first;
    }

    private Value visitUnaryExp(UnaryExp exp) throws SemanticException {
        Value primary = visitPrimaryExp(exp.getPrimary());
        // 从右向左结合
        ArrayList<Token> unaryOps = new ArrayList<>(exp.getUnaryOps());
        for (int i = unaryOps.size() - 1; i >= 0; i--) {
            Token op = unaryOps.get(i);
            if (op.getType().equals(TokenType.NOT)) {
                // TODO: 不确定这个是不是对, 与LLVM IR一致
                if (primary.getType().isFloatType())
                    primary = new Fcmp(Fcmp.Op.OEQ, primary, CONST_0F, curBB);
                else if (primary.getType().isInt32Type())
                    primary = new Icmp(Icmp.Op.EQ, primary, CONST_0, curBB);
                else
                    throw new AssertionError(String.format("Bad primary ! %s", primary));
            } else {
                if (op.getType() == TokenType.SUB) {
                    if (primary.getType().isFloatType())
                        primary = new Alu(getF32Type(), Alu.Op.FSUB, CONST_0F, primary, curBB);
                    else if (primary.getType().isInt32Type())
                        primary = new Alu(getI32Type(), Alu.Op.SUB, CONST_0, primary, curBB);
                    else
                        throw new AssertionError(String.format("Bad primary - %s", primary));
                }
            }
        }
        return primary;
    }

    private Value visitExp(Exp exp) throws SemanticException {
        if (exp instanceof BinaryExp) {
            return visitBinaryExp((BinaryExp) exp);
        } else if (exp instanceof UnaryExp) {
            return visitUnaryExp((UnaryExp) exp);
        } else {
            throw new AssertionError("Bad Exp");
        }
    }

    private Value visitNumber(Ast.Number number) {
        if (number.isFloatConst()) {
            return new Constant.ConstantFloat(number.getFloatConstVal());
        } else if (number.isIntConst()) {
            return new Constant.ConstantInt(number.getIntConstVal());
        } else {
            throw new AssertionError(String.format("Bad Number: %s", number));
        }
        // return new Value(new Evaluate(currentSymTable, false).evalNumber(number), BasicType.INT);
    }

    private Value visitPrimaryExp(PrimaryExp exp) throws SemanticException {
        if (exp instanceof Exp) {
            return visitExp((Exp) exp);
        } else if (exp instanceof LVal) {
            return visitLVal((LVal) exp, false);
        } else if (exp instanceof Ast.Number) {
            return visitNumber((Ast.Number) exp);
        } else if (exp instanceof Ast.Call) {
            return visitCall((Ast.Call) exp);
        } else {
            throw new AssertionError("Bad PrimaryExp");
        }
    }

    // if left: return address, else return value (generate load instruction)
    private Value visitLVal(LVal lVal, boolean left) throws SemanticException {
        // 去符号表拿出指向这个左值的指针
        String ident = lVal.getIdent().getContent();
        Symbol symbol = currentSymTable.get(ident, true);
        if (left && symbol.isConstant()) {
            throw new SemanticException("Modify const");
        }
        Value address = symbol.getPointer();
        assert address.getType() instanceof PointerType;
        // 处理数组的偏移寻址
        // 遍历下标，逐层 getelementptr, 每层均进行一个解引用和一个偏移
        for (Exp exp : lVal.getIndexes()) {
            if (!(address.getType() instanceof PointerType)) {
                throw new SemanticException("Non-Array has indexes");
            }
            Value offset = visitExp(exp);
            if (!(offset.getType() instanceof BasicType)) {
                throw new SemanticException("Index not number");
            }
            offset = trimTo(offset, I32_TYPE);
            Type nextType = ((PointerType) address.getType()).getInnerType(); // 实体的类型
            assert !(nextType instanceof BasicType);
            Value elem;
            // 实体是数组, 地址是数组指针, getelementptr 要有两层
            // 实体是指针, 地址是二级指针, getelementptr 应有一层(仅在含有数组形参的函数中有), 同时还有个 load
            if (nextType instanceof PointerType) {
                Value basePtr = Value.newVar(nextType);
                curBB.insertAtEnd(new Load(basePtr, address));
                elem = Value.newVar(nextType);
                curBB.insertAtEnd(new GetElementPtr(elem, basePtr, offset, false));
            } else {
                assert nextType instanceof ArrayType;
                nextType = ((ArrayType) nextType).getBase();
                elem = Value.newVar(new PointerType(nextType));
                curBB.insertAtEnd(new GetElementPtr(elem, address, offset, true));
            }
            address = elem;
        }
        assert address.getType() instanceof PointerType;
        if (left) {
            // 如果作为左值, 一定不能是部分数组
            // 是数组元素的条件: address
            if (!(((PointerType) address.getType()).getInnerType() instanceof BasicType)) {
                throw new SemanticException("Part-array cannot be left value");
            }
            assert ((PointerType) address.getType()).getInnerType() instanceof BasicType;
            return address; // 返回一个可以直接 store i32 值的指针
        } else {
            // 如果是数组元素或者普通的值, 就 load; 如果是部分数组(仅用于函数传参), 应该得到一个降维的数组指针;
            // 如果是将数组指针作为参数继续传递, 也需要 load 来解引用
            Type baseType = ((PointerType) address.getType()).getInnerType();
            if (baseType instanceof BasicType || baseType instanceof PointerType) {
                Value val = Value.newVar(baseType);
                curBB.insertAtEnd(new Load(val, address));
                return val;
            } else if (baseType instanceof ArrayType) {
                // [2 x [3 x [4 x i32]]] a: a[2] -> int p[][4]
                // &a: [2 x [3 x [4 x i32]]]*, &(a[2]): [3 x [4 x i32]]*, p: [4 x i32]*
                // [3 x [4 x i32]] b: b[3] -> int p[][4]
                // &b: [3 x [4 x i32]]*, &(b[3]): [4 x i32]*, p: i32*
                // 数组解引用
                Value ptr = Value.newVar(new PointerType(((ArrayType) baseType).getBase()));
                curBB.insertAtEnd(new GetElementPtr(ptr, address, new Value.Num(0, BasicType.INT), true)); // ???
                return ptr;
            } else {
                throw new AssertionError("Bad baseType");
            }
        }
    }

    // returns the return value if function call, null if function is void
    private Value visitCall(Ast.Call call) throws SemanticException {
        String ident = call.getIdent().getContent();
        Function function = ir.getFunctions().get(ident);
        if (function == null) {
            throw new SemanticException("Function " + ident + " not declared.");
        }
        ArrayList<Value> params = new ArrayList<>();
        for (Exp exp : call.getParams()) {
            params.add(visitExp(exp));
        }
        return new Instr.Call(function, params, curBB);
    }

    private void visitAssign(Assign assign) throws SemanticException {
        Value left = visitLVal(assign.left, true);
        Value right = visitExp(assign.right);
        assert left.getType() instanceof PointerType; // 分析出来的左值一定是指针类型
        Type innerType = ((PointerType) left.getType()).getInnerType();
        assert innerType instanceof BasicType;
        right = trimTo(right, (BasicType) innerType);
        // assert right.getType().equals(innerType); // 分析出来的右值一定是左值指针解引用的类型
        new Store(right, left, curBB);
    }

    private void visitExpStmt(ExpStmt expStmt) throws SemanticException {
        Exp exp = expStmt.getExp();
        if (exp != null) {
            visitExp(expStmt.getExp());
        }
    }

    private void visitIfStmt(IfStmt ifStmt) throws SemanticException {
        Value cond = visitExp(ifStmt.getCond());
        cond = trimTo(cond, I32_TYPE);
        Stmt thenTarget = ifStmt.getThenTarget();
        Stmt elseTarget = ifStmt.getElseTarget();
        BasicBlock thenBlock = new BasicBlock(curFunc);
        BasicBlock follow = new BasicBlock(curFunc);
        if (elseTarget == null) {
            new Branch(cond, thenBlock, follow, curBB);
            curBB = thenBlock;
            visitStmt(thenTarget); // currentBlock may be modified
        } else {
            BasicBlock elseBlock = new BasicBlock(curFunc);
            new Branch(cond, thenBlock, elseBlock, curBB);
            curBB = thenBlock;
            visitStmt(thenTarget);
            new Jump(follow, curBB);
            curBB = elseBlock;
            visitStmt(elseTarget);
        }
        new Jump(follow, curBB);
        curBB = follow;
    }

    private void visitWhileStmt(WhileStmt whileStmt) throws SemanticException {
        BasicBlock head = new BasicBlock();
        new Jump(head, curBB);
        curBB = head;
        Value cond = visitExp(whileStmt.getCond());
        cond = trimTo(cond, I1_TYPE);
        BasicBlock body = new BasicBlock();
        BasicBlock follow = new BasicBlock();
        new Branch(cond, body, follow, curBB);
        curBB = body;
        loopHeads.push(head);
        loopFollows.push(follow);
        visitStmt(whileStmt.getBody());
        loopHeads.pop();
        loopFollows.pop();
        new Jump(head, curBB);
        curBB = follow;
    }

    private void visitBreak() throws SemanticException {
        if (loopFollows.empty()) {
            throw new SemanticException("Break not in loop");
        }
        new Jump(loopFollows.peek(), curBB);
    }

    private void visitContinue() throws SemanticException {
        if (loopHeads.empty()) {
            throw new SemanticException("Continue not in loop");
        }
        new Jump(loopHeads.peek(), curBB);
    }

    private void visitReturn(Ast.Return ret) throws SemanticException {
        Exp retExp = ret.getRetExp();
        if (retExp == null) {
            new Instr.Return(curBB);
        } else {
            new Instr.Return(visitExp(retExp), curBB);
        }
    }

    private void visitStmt(Stmt stmt) throws SemanticException {
        if (stmt instanceof Assign) {
            visitAssign((Assign) stmt);
        } else if (stmt instanceof ExpStmt) {
            visitExpStmt((ExpStmt) stmt);
        } else if (stmt instanceof IfStmt) {
            visitIfStmt((IfStmt) stmt);
        } else if (stmt instanceof WhileStmt) {
            visitWhileStmt((WhileStmt) stmt);
        } else if (stmt instanceof Break) {
            visitBreak();
        } else if (stmt instanceof Continue) {
            visitContinue();
        } else if (stmt instanceof Ast.Return) {
            visitReturn((Ast.Return) stmt);
        } else if (stmt instanceof Block) {
            visitBlock((Block) stmt, true);
        } else {
            throw new AssertionError("Bad Stmt");
        }
    }

    private void visitBlockItem(BlockItem item) throws SemanticException {
        if (item instanceof Stmt) {
            visitStmt((Stmt) item);
        } else if (item instanceof Decl) {
            visitDecl((Decl) item);
        } else {
            throw new AssertionError("Bad BlockItem");
        }
    }

    // sym: 是否需要新开一层符号表
    private void visitBlock(Block block, boolean sym) throws SemanticException {
        assert curBB != null;
        BasicBlock inner = new BasicBlock();
        BasicBlock follow = new BasicBlock();
        new Jump(inner, curBB);
        curBB = inner;
        if (sym) {
            currentSymTable = new SymTable(currentSymTable);
        } // 新开一层符号表
        for (BlockItem item : block.getItems()) {
            visitBlockItem(item);
        }
        if (sym) {
            currentSymTable = currentSymTable.getParent();
        } // 退出一层符号表
        new Jump(follow, curBB);
        curBB = follow;
    }

    private void visitDecl(Decl decl) throws SemanticException {
        for (Def def : decl.getDefs()) {
            boolean eval = (decl.isConstant()) || (curFunc == null); // 局部变量,可以运行时初始化
            visitDef(def, decl.isConstant(), eval);
        }
    }

    private void initZeroHelper(Value pointer) {
        // 将一整个局部数组利用 memset 全部初始化为零
        // 一层一层拆类型并得到总大小
        assert pointer.getType() instanceof PointerType;
        Type baseType = ((PointerType) pointer.getType()).getInnerType();
        Value ptr = pointer;
        int size = 1;
        while (baseType instanceof ArrayType) {
            size *= ((ArrayType) baseType).getSize();
            Type innerType = ((ArrayType) baseType).getBase();
            Value innerPtr = Value.newVar(new PointerType(innerType));
            curBB.insertAtEnd(new GetElementPtr(innerPtr, ptr, new Value.Num(0), true));
            ptr = innerPtr;
            baseType = innerType;
        }
        size *= 4; // sizeof int
        assert ptr.getType() instanceof PointerType && ((PointerType) ptr.getType()).getInnerType().equals(BasicType.INT);
        new Instr.Call(IR.ExternFunction.MEM_SET, List.of(ptr, new Value.Num(0), new Value.Num(size)), curBB);
    }

    private void initHelper(Value pointer, Initial init) {
        assert curBB != null && curFunc != null;
        Type type = pointer.getType();
        assert type instanceof PointerType;
        Type baseType = ((PointerType) type).getInnerType();
        if (init instanceof Initial.ExpInit) {
            curBB.insertAtEnd(new Store(((Initial.ExpInit) init).getResult(), pointer));
        } else if (init instanceof Initial.ValueInit) {
            curBB.insertAtEnd(new Store(new Value.Num(((Initial.ValueInit) init).getValue()), pointer));
        } else if (init instanceof Initial.ArrayInit) {
            Initial.ArrayInit arrayInit = (Initial.ArrayInit) init;
            assert baseType instanceof ArrayType;
            int len = arrayInit.length();
            for (int i = 0; i < len; i++) {
                Value ptr = Value.newVar(new PointerType(((ArrayType) baseType).getBase()));
                curBB.insertAtEnd(new GetElementPtr(ptr, pointer, new Value.Num(i), true));
                initHelper(ptr, arrayInit.get(i));
            }
        }
    }

    private void visitDef(Def def, boolean constant, boolean eval) throws SemanticException {
        String ident = def.getIdent().getContent();
        if (currentSymTable.contains(ident, false)) {
            throw new SemanticException("Duplicated variable definition");
        }
        Type type = I32_TYPE;
        // 编译期计算数组每一维的长度，然后从右向左"组装"成数组类型
        ArrayList<Integer> lengths = new ArrayList<>();
        for (Exp len : def.getIndexes()) {
            lengths.add(new Evaluate(currentSymTable, true).evalIntExp(len));
        }
        for (int i = lengths.size() - 1; i >= 0; i--) {
            int len = lengths.get(i);
            type = new ArrayType(len, type);
        }
        // 构造该类型的指针
        Value pointer;
        if (!isGlobal()) {
            pointer = Value.newVar(new PointerType(type)); // 局部变量
        } else {
            pointer = new Value(ident, new PointerType(type), true, constant); // 全局变量
        }
        // 解析其初始化内容
        Init astInit = def.getInit();
        Initial init = null;
        if (astInit != null) {
            if (type instanceof BasicType) {
                if (!(astInit instanceof Exp)) {
                    throw new SemanticException("Value variable not init by value");
                }
                if (eval) {
                    init = visitInitVal((Exp) astInit, constant || isGlobal());
                } else {
                    init = visitInitExp((Exp) astInit);
                }
            } else {
                // ArrayType
                if (!(astInit instanceof InitArray)) {
                    throw new SemanticException("Array variable not init by a list");
                }
                init = visitInitArray((InitArray) astInit, (ArrayType) type, constant, eval);
            }
        }
        // 如果是全局变量且没有初始化，则初始化为零
        if (isGlobal() && init == null) {
            if (type instanceof BasicType) {
                init = new Initial.ValueInit(0, type);
            } else {
                init = new Initial.ZeroInit(type);
            }
        }
        // 构建符号表项并插入符号表
        Symbol symbol = new Symbol(ident, type, init, constant, pointer);
        currentSymTable.add(symbol);
        // 全局: 直接给出初始化结果, 局部: 分配空间, store + memset 初始化的值
        if (curFunc == null) {
            // 全局
            ir.addGlobal(symbol);
        } else {
            // 局部
            // 分配的空间指向 pointer
            assert curBB != null;
            curBB.insertAtEnd(new Alloc(pointer, type));
            if (type instanceof ArrayType) {
                initZeroHelper(pointer);
            }
            initHelper(pointer, init); // 生成初始化
        }
    }

    private Initial.ArrayInit visitInitArray(InitArray initial, ArrayType type, boolean constant, boolean eval) throws SemanticException {
        Initial.ArrayInit arrayInit = new Initial.ArrayInit(type);
        int count = 0;
        for (Init init : initial.getInit()) {
            count++; // 统计已经初始化了多少个
            if (init instanceof Exp) {
                // 是单个数
                if (!(type.getBase() instanceof BasicType)) {
                    throw new SemanticException("Array initializer to a value type");
                }
                if (eval) {
                    // 必须编译期计算
                    arrayInit.add(visitInitVal((Exp) init, constant));
                } else {
                    assert !constant;
                    arrayInit.add(visitInitExp((Exp) init));
                }
            } else {
                // 子数组的初始化
                // 类型拆一层
                assert type.getBase() instanceof ArrayType;
                Initial innerInit = visitInitArray((InitArray) init, (ArrayType) type.getBase(), constant, eval);
                arrayInit.add(innerInit);
            }
        }
        while (count < type.getSize()) {
            // 初始化个数小于当前维度的长度，补零
            count++;
            if (type.getBase() instanceof BasicType) {
                arrayInit.add(new Initial.ValueInit(0, BasicType.INT));
            } else {
                assert type.getBase() instanceof ArrayType;
                arrayInit.add(new Initial.ZeroInit(type.getBase()));
            }
        }
        return arrayInit;
    }

    private Initial.ValueInit visitInitVal(Exp exp, boolean constant) throws SemanticException {
        int eval = new Evaluate(currentSymTable, constant).evalIntExp(exp);
        return new Initial.ValueInit(eval, BasicType.INT);
    }

    private Initial.ExpInit visitInitExp(Exp exp) throws SemanticException {
        Value eval = visitExp(exp); // 运行期才计算
        return new Initial.ExpInit(eval, BasicType.INT);
    }

    // 由于编译器采用 Load-Store 形式，变量符号全部对应指针，所以在函数入口一开始先把形参全存到栈上 hhh
    private void visitFuncDef(FuncDef def) throws SemanticException {
        TokenType funcTypeTk = def.getType().getType();
        Type retType = switch (funcTypeTk){
            case VOID -> VoidType.getVoidType();
            case INT -> I32_TYPE;
            case FLOAT -> F32_TYPE;
            default -> throw new SemanticException("Wrong func ret type: " + funcTypeTk);
        };
        String ident = def.getIdent().getContent();
        if (ir.getFunctions().containsKey(ident)) {
            throw new SemanticException("Duplicated function defined");
        }
        // 入口基本块
        BasicBlock entry = new BasicBlock();
        curBB = entry;
        // 构造形参层符号表
        currentSymTable = new SymTable(currentSymTable);
        // 形参表
        ArrayList<Function.Param> params = new ArrayList<>();
        int idx = 0;
        for (FuncFParam fParam : def.getFParams()) {
            Type paramType = visitFuncFParam(fParam);
            Function.Param param = new Function.Param(paramType, idx++);
            params.add(param); // 形参变量
            Value paramPointer = new Alloc(paramType, curBB);
            new Store(param, paramPointer, curBB);
            currentSymTable.add(new Symbol(fParam.ident.getContent(), paramType, null, false, paramPointer));
        }
        Function function = new Function(ident, params, retType);
        ir.addFunction(function);
        function.setBody(entry);
        curFunc = function;
        entry.setFunction(curFunc);
        visitBlock(def.getBody(), false); // 分析函数体
        if (!curBB.isTerminated()) {
            // 如果没有 return 补上一条
            switch (funcTypeTk){
                case VOID -> new Instr.Return(curBB);
                case INT -> new Instr.Return(CONST_0, curBB);
                case FLOAT -> new Instr.Return(CONST_0F, curBB);
                default -> throw new SemanticException("Wrong func ret type: " + funcTypeTk);
            }
        }
        currentSymTable = currentSymTable.getParent();
        curBB = null;
        curFunc = null;
    }

    private Type visitFuncFParam(FuncFParam funcFParam) throws SemanticException {
        // 常数, 常数指针或者数组指针(注意降维)
        if (!funcFParam.isArray()) {
            return getBasicType(funcFParam);
        } else {
            ArrayList<Integer> lengths = new ArrayList<>();
            for (Exp index : funcFParam.getSizes()) {
                int len = new Evaluate(currentSymTable, true).evalIntExp(index);
                lengths.add(len);
            }
            Type paramType = getBasicType(funcFParam);
            for (int i = lengths.size() - 1; i >= 0; i--) {
                int len = lengths.get(i);
                paramType = new ArrayType(len, paramType);
            }
            return new PointerType(paramType);
        }
    }

    private Type getBasicType(FuncFParam funcFParam) throws SemanticException {
        return switch (funcFParam.bType.getType()) {
            case INT -> I32_TYPE;
            case FLOAT -> F32_TYPE;
            default -> throw new SemanticException("Wrong param type: " + funcFParam.ident.getContent());
        };
    }

    private void visitCompUnit(CompUnit unit) throws SemanticException {
        assert isGlobal();
        if (unit instanceof Decl) {
            visitDecl((Decl) unit); // 全局变量
        } else if (unit instanceof FuncDef) {
            visitFuncDef((FuncDef) unit);
        } else {
            throw new AssertionError("Bad Compile Unit");
        }
    }

    public IR getIr() {
        return this.ir;
    }

}
