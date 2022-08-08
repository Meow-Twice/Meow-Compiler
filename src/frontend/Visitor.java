package frontend;

import frontend.semantic.Evaluate;
import frontend.semantic.Initial;
import frontend.syntax.Ast.*;
import exception.SemanticException;
import frontend.lexer.Token;
import frontend.lexer.TokenType;
import frontend.semantic.symbol.SymTable;
import frontend.semantic.symbol.Symbol;
import frontend.syntax.Ast;
import lir.MC;
import manage.Manager;
import mir.*;
import mir.Instr.*;
import mir.type.Type;

import java.util.*;

import static mir.Constant.ConstantFloat.CONST_0F;
import static mir.Constant.ConstantInt.CONST_0;
import static mir.type.Type.BasicType.*;

/**
 * 遍历语法树, 生成 IR 代码
 */
public class Visitor {
    public static final Visitor VISITOR = new Visitor();
    private final boolean __ONLY_PARSE_OUTSIDE_DIM = false;
    private final Manager manager = Manager.MANAGER; // 最终生成的 IR
    public static SymTable currentSymTable = new SymTable(); // 当前符号表, 初始时是全局符号表
    // 用于记录loop的单cond中的累计第几个, 不可直接public获取, 很危险
    // 在循环相关的所有visitLOrExp和visitLAndExp之前++可保证递增且唯一
    // 上面这个想法是错的, visitLOrExp内进第一个visitLAndExp之前加加会导致第一个andCond有两批编号
    // 应该在每一次切换基本块时++
    public int curLoopCondCount = 0;

    // 实时更新表示Instr是否在循环中
    public boolean inLoop = false;

    // 实时更新表示Instr是否在Cond中
    private boolean inCond = false;

    public int getCondCount() {
        return inCond ? curLoopCondCount : -1;
    }

    public boolean isInLoopCond() {
        return inLoop && inCond;
    }

    private Function curFunc = null; // 当前正在分析的函数
    private BasicBlock curBB = null; // 当前所在的基本块
    private boolean isGlobal = true;
    private static Loop curLoop = Loop.emptyLoop;

    private static int loopDepth = 0;

    private boolean isGlobal() {
        return curFunc == null && curBB == null && !currentSymTable.hasParent();
    }

    private final Stack<BasicBlock> loopHeads = new Stack<>(); // for continue;
    private final Stack<BasicBlock> loopFollows = new Stack<>(); // for break;

    private Visitor() {
    }

    private Value trimTo(Value value, BasicType targetType) /*throws SemanticException*/ {
        assert value != null;
        if (!(value.getType() instanceof BasicType)) {
            System.exit(value.tag.ordinal());
            throw new AssertionError("Wrong trimTo " + targetType + " from " + value.getType());
        }
        // Value res = value;
        // if (value.getType().equals(targetType)) {
        //     System.err.printf("Try to trim %s to %s\n", value, targetType);
        //     // return value;
        // }
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
//                    if (op.getType() == TokenType.MOD) {
//                        // a % b = a - a / b;
//                        Value tmp = new Alu(F32_TYPE, Alu.Op.FDIV, first, second, curBB);
//                        tmp = new Alu(F32_TYPE, Alu.Op.FMUL, tmp, second, curBB);
//                        first = new Alu(F32_TYPE, Alu.Op.FSUB, first, tmp, curBB);
//                    } else {
//                        Alu.Op aluOp = aluOpHelper(op, true);
//                        first = new Alu(F32_TYPE, aluOp, first, second, curBB);
//                    }
                    Alu.Op aluOp = aluOpHelper(op, true);
                    first = new Alu(F32_TYPE, aluOp, first, second, curBB);
                } else {
                    first = trimTo(first, I32_TYPE);
                    second = trimTo(second, I32_TYPE);
//                    if (op.getType() == TokenType.MOD) {
//                        // a % b = a - a / b * b;
//                        Value tmp = new Alu(I32_TYPE, Alu.Op.DIV, first, second, curBB);
//                        tmp = new Alu(I32_TYPE, Alu.Op.MUL, tmp, second, curBB);
//                        first = new Alu(I32_TYPE, Alu.Op.SUB, first, tmp, curBB);
//                    } else {
//                        Alu.Op aluOp = aluOpHelper(op);
//                        first = new Alu(I32_TYPE, aluOp, first, second, curBB);
//                    }
                    Alu.Op aluOp = aluOpHelper(op);
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
                throw new AssertionError("Wrong code branch");
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
                else {
                    assert primary.getType().isInt1Type();
                    primary = trimTo(primary, I32_TYPE);
                    primary = new Icmp(Icmp.Op.EQ, primary, CONST_0, curBB);
                    // TODO: 大胆而激进的做法
                    // 有非仅下面一条指令调用的风险
                    // if(primary instanceof Icmp){
                    //     Icmp icmp = (Icmp) primary;
                    //     switch (icmp.getOp()){
                    //         case EQ -> icmp.setOp(Icmp.Op.NE);
                    //         case NE -> icmp.setOp(Icmp.Op.EQ);
                    //         case SGE -> icmp.setOp()
                    //     }
                    // }
                    // throw new AssertionError(String.format("Bad primary ! %s", primary));
                }
            } else {
                if (op.getType() == TokenType.SUB) {
                    if (primary.getType().isFloatType())
                        primary = new Alu(getF32Type(), Alu.Op.FSUB, CONST_0F, primary, curBB);
                    else if (primary.getType().isInt32Type())
                        primary = new Alu(getI32Type(), Alu.Op.SUB, CONST_0, primary, curBB);
                    else if (primary.getType().isInt1Type()) {
                        primary = new Alu(getI32Type(), Alu.Op.SUB, CONST_0, trimTo(primary, I32_TYPE), curBB);
                    } else {
                        throw new AssertionError(String.format("Bad primary - %s", primary));
                    }
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
    private Value visitLVal(LVal lVal, boolean needPointer) throws SemanticException {
        // 去符号表拿出指向这个左值的指针
        String ident = lVal.getIdent().getContent();
        Symbol symbol = currentSymTable.get(ident, true);
        if (symbol.isConstant() && lVal.getIndexes().isEmpty()) {
            // return symbol.getInitial();
            // assert !needPointer;
            assert symbol.getInitial() instanceof Initial.ValueInit;
            return ((Initial.ValueInit) symbol.getInitial()).getValue();
        }
        Value pointer = symbol.getValue();
        // assert pointer instanceof Alloc;
        assert pointer.getType() instanceof PointerType;
        ArrayList<Value> idxList = new ArrayList<>();
        idxList.add(CONST_0);
        boolean flag = false;
        Type innerType = ((PointerType) pointer.getType()).getInnerType(); // 实体的类型
        for (Exp exp : lVal.getIndexes()) {
            Value offset = trimTo(visitExp(exp), I32_TYPE);
            assert !(innerType instanceof BasicType);
            if (innerType instanceof PointerType) {
                // 参数, 如int a[][...][...]...
                Instr loadInst = new Load(pointer, curBB);
                innerType = ((PointerType) innerType).getInnerType();
                // assert innerType instanceof ArrayType;
                // innerType = ((ArrayType) innerType).getBase();
                if (__ONLY_PARSE_OUTSIDE_DIM) {
                    pointer = new GetElementPtr(innerType, loadInst, wrapImmutable(offset), curBB);
                } else {
                    pointer = loadInst;
                    flag = true;
                    idxList.clear();
                    idxList.add(offset);
                }
            } else if (innerType instanceof ArrayType) {
                // 数组
                innerType = ((ArrayType) innerType).getBaseType();
                if (__ONLY_PARSE_OUTSIDE_DIM) {
                    pointer = new GetElementPtr(innerType, pointer, wrapImmutable(CONST_0, offset), curBB);
                } else {
                    flag = true;
                    idxList.add(offset);
                }
            } else {
                throw new AssertionError(String.format("lVal:(%s) visit fail", lVal));
            }
        }
        if (!__ONLY_PARSE_OUTSIDE_DIM && flag) {
            pointer = new GetElementPtr(innerType, pointer, idxList, curBB);
        }
        assert pointer.getType() instanceof PointerType;
        if (needPointer) {
            assert ((PointerType) pointer.getType()).getInnerType() instanceof BasicType;
            return pointer; // 返回一个可以直接 store的指针
        }
        // 如果是数组元素或者普通的值, 就 load; 如果是部分数组(仅用于函数传参), 应该得到一个降维的数组指针;
        // 如果是将数组指针作为参数继续传递, 也需要 load 来解引用
        if (innerType instanceof BasicType || innerType instanceof PointerType) {
            return new Load(pointer, curBB);
        } else if (innerType instanceof ArrayType) {
            return new GetElementPtr(((ArrayType) innerType).getBaseType(), pointer, wrapImmutable(CONST_0, CONST_0), curBB);
        } else {
            throw new AssertionError("Bad baseType");
        }
    }

    // returns the return value if function call, null if function is void
    private Value visitCall(Ast.Call call) throws SemanticException {
        String ident = call.getIdent().getContent();
        Function function = manager.getFunctions().get(ident);
        assert function != null;
        if (function.isTimeFunc) {
            return new Instr.Call(function, new ArrayList<>(Collections.singleton(new Constant.ConstantInt(call.lineno))), curBB);
        } else {
            ArrayList<Value> params = new ArrayList<>();
            int idx = 0;
            for (Exp exp : call.getParams()) {
                Function.Param p = function.getParams().get(idx++);
                Value expValue = visitExp(exp);
                if (p.getType() instanceof BasicType) {
                    expValue = trimTo(expValue, (BasicType) p.getType());
                }
                params.add(expValue);
            }
            return new Instr.Call(function, params, curBB);
        }
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

    private void dealCondCount() {
        if (inCond) {
            curLoopCondCount++;
        }
    }

    /***
     * 出来的时候保证curBB为最后一个条件所在的块，不用Jump
     * @param exp
     * @param falseBlock
     * @return 保证返回Int1Type
     * @throws SemanticException
     */
    private Value visitCondLAnd(Exp exp, BasicBlock falseBlock) throws SemanticException {
        if (!(exp instanceof BinaryExp)) {
            return trimTo(visitExp(exp), I1_TYPE);
        }
        BinaryExp lAndExp = (BinaryExp) exp;
        Value first = trimTo(visitExp(lAndExp.getFirst()), I1_TYPE);
        Iterator<Token> iterOp = lAndExp.getOperators().listIterator();
        for (Exp nextExp : lAndExp.getFollows()) {
            assert iterOp.hasNext();
            Token op = iterOp.next();
            assert op.getType() == TokenType.LAND;
            BasicBlock nextBlock = new BasicBlock(curFunc, curLoop); // 实为trueBlock的前驱
            new Branch(first, nextBlock, falseBlock, curBB);
            curBB = nextBlock;
            dealCondCount();
            first = trimTo(visitExp(nextExp), I1_TYPE);
        }
        return first;
    }

    /***
     * 出来的时候保证curBB为最后一个条件所在的块，不用Jump
     * @param exp
     * @param trueBlock
     * @param falseBlock
     * @return 保证返回Int1Type
     * @throws SemanticException
     */
    private Value visitCondLOr(Exp exp, BasicBlock trueBlock, BasicBlock falseBlock) throws SemanticException {
        if (!(exp instanceof BinaryExp)) {
            return trimTo(visitExp(exp), I1_TYPE);
        }
        BinaryExp lOrExp = (BinaryExp) exp;
        boolean flag = false;
        BasicBlock nextBlock = falseBlock;
        if (!((BinaryExp) exp).getFollows().isEmpty()) {
            nextBlock = new BasicBlock(curFunc, curLoop); // 实为trueBlock的前驱
            flag = true;
        }
        Value first = visitCondLAnd(lOrExp.getFirst(), nextBlock);
        assert first.getType().isInt1Type();
        Iterator<Token> iterOp = lOrExp.getOperators().listIterator();
        for (Exp nextExp : lOrExp.getFollows()) {
            assert iterOp.hasNext();
            Token op = iterOp.next();
            assert op.getType() == TokenType.LOR;
            if (flag) {
                flag = false;
            } else {
                nextBlock = new BasicBlock(curFunc, curLoop); // 实为trueBlock的前驱
            }
            new Branch(first, trueBlock, nextBlock, curBB);
            curBB = nextBlock;
            dealCondCount();
            first = visitCondLAnd(nextExp, falseBlock);
            assert first.getType().isInt1Type();
        }
        return first;
    }

    private void visitIfStmt(IfStmt ifStmt) throws SemanticException {
        Stmt thenTarget = ifStmt.getThenTarget();
        Stmt elseTarget = ifStmt.getElseTarget();
        boolean hasElseBlock = elseTarget != null;
        BasicBlock thenBlock = new BasicBlock(curFunc, curLoop);
        BasicBlock elseBlock = null;
        if (hasElseBlock) {
            elseBlock = new BasicBlock(curFunc, curLoop);
        }
        BasicBlock followBlock = new BasicBlock(curFunc, curLoop);
        if (hasElseBlock) {
            inCond = true;
            dealCondCount();
            Value cond = visitCondLOr(ifStmt.getCond(), thenBlock, elseBlock);
            assert cond.getType().isInt1Type();
            new Branch(cond, thenBlock, elseBlock, curBB);
            curBB = thenBlock;
            inCond = false;
            visitStmt(thenTarget);
            new Jump(followBlock, curBB);
            curBB = elseBlock;
            visitStmt(elseTarget);
        } else {
            inCond = true;
            dealCondCount();
            Value cond = visitCondLOr(ifStmt.getCond(), thenBlock, followBlock);
            assert cond.getType().isInt1Type();
            new Branch(cond, thenBlock, followBlock, curBB);
            curBB = thenBlock;
            inCond = false;
            visitStmt(thenTarget); // currentBlock may be modified
        }
        new Jump(followBlock, curBB);
        curBB = followBlock;
    }

    private void visitWhileStmt(WhileStmt whileStmt) throws SemanticException {
        curLoop = new Loop(curLoop);
        curLoop.setFunc(curFunc);
        BasicBlock condBlock = new BasicBlock(curFunc, curLoop);
        condBlock.setLoopStart();
        new Jump(condBlock, curBB);
        BasicBlock body = new BasicBlock(curFunc, curLoop);
        BasicBlock follow = new BasicBlock(curFunc, curLoop.getParentLoop());
        curBB = condBlock;
        inLoop = true;
        inCond = true;
        dealCondCount();
        Value cond = visitCondLOr(whileStmt.getCond(), body, follow);
        assert cond.getType().isInt1Type();
        new Branch(cond, body, follow, curBB);
        curBB = body;
        inCond = false;
        loopHeads.push(condBlock);
        loopFollows.push(follow);
        visitStmt(whileStmt.getBody());
        loopHeads.pop();
        loopFollows.pop();
        new Jump(condBlock, curBB);
        inLoop = false;
        curBB = follow;
        curLoop = curLoop.getParentLoop();
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
            Value retValue = visitExp(retExp);
            new Instr.Return(trimTo(retValue, (BasicType) curBB.getFunction().getRetType()), curBB);
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
        if (sym) {
            currentSymTable = new SymTable(currentSymTable);
        } // 新开一层符号表
        for (BlockItem item : block.getItems()) {
            visitBlockItem(item);
        }
        if (sym) {
            currentSymTable = currentSymTable.getParent();
        } // 退出一层符号表
    }

    private void visitDecl(Decl decl) throws SemanticException {
        for (Def def : decl.getDefs()) {
            visitDef(def, decl.isConstant());
        }
    }

    // private static final ArrayList<Value> const0Pair = new ArrayList<>(Collections.nCopies(2, CONST_0));

    private void initZeroHelper(Value pointer, Type pointeeType) {
        Value ptr = pointer;
        int size = 4;
        ArrayList<Value> idxList = new ArrayList<>();
        idxList.add(CONST_0);
        while (pointeeType instanceof ArrayType) {
            size *= ((ArrayType) pointeeType).getSize();
            pointeeType = ((ArrayType) pointeeType).getBaseType();
            if (__ONLY_PARSE_OUTSIDE_DIM) {
                ptr = new GetElementPtr(pointeeType, ptr, wrapImmutable(CONST_0, CONST_0), curBB);
            } else {
                idxList.add(CONST_0);
            }
        }
        if (!__ONLY_PARSE_OUTSIDE_DIM) {
            ptr = new GetElementPtr(pointeeType, ptr, idxList, curBB);
        }
        assert ptr.getType() instanceof PointerType;
        assert ((PointerType) ptr.getType()).getInnerType() instanceof BasicType;
        if (((PointerType) ptr.getType()).getInnerType().isFloatType()) {
            ptr = new Bitcast(ptr, new PointerType(I32_TYPE), curBB);
        }
        new Instr.Call(Manager.ExternFunction.MEM_SET, wrapImmutable(ptr, CONST_0, new Constant.ConstantInt(size)), curBB);
    }

    public ArrayList<Value> wrapImmutable(Value... values) {
        // return new ArrayList<>(List.of(values));
        // 据说可add的写法里最高效
        ArrayList<Value> arrayList = new ArrayList<>(values.length);
        Collections.addAll(arrayList, values);
        return arrayList;
    }

    private void initLocalVarHelper(Value pointer, Initial init) {
        assert curBB != null && curFunc != null;
        Type type = pointer.getType();
        assert type instanceof PointerType;
        Type baseType = ((PointerType) type).getInnerType();
        if (init instanceof Initial.ArrayInit) {
            Initial.ArrayInit arrayInit = (Initial.ArrayInit) init;
            assert baseType instanceof ArrayType;
            int len = arrayInit.length();
            for (int i = 0; i < len; i++) {
                // PointerType pointeeType = new PointerType(((ArrayType) baseType).getBase());
                Value ptr = new GetElementPtr(((ArrayType) baseType).getBaseType(), pointer, wrapImmutable(CONST_0, new Constant.ConstantInt(i)), curBB);
                initLocalVarHelper(ptr, arrayInit.get(i));
            }
        } else if (init instanceof Initial.ZeroInit) {
            initZeroHelper(pointer, baseType);
        } else {
            Value v;
            if (init instanceof Initial.ExpInit) {
                v = trimTo(((Initial.ExpInit) init).getResult(), (BasicType) baseType);
            } else if (init instanceof Initial.ValueInit) {
                v = trimTo(((Initial.ValueInit) init).getValue(), (BasicType) baseType);
            } else {
                throw new AssertionError("wrong init: " + init + "\nfor: " + pointer);
            }
            new Store(v, pointer, curBB);
        }
    }

    private void visitDef(Def def, boolean constant) throws SemanticException {
        boolean eval = constant || isGlobal;
        String ident = def.getIdent().getContent();
        if (currentSymTable.contains(ident, false)) {
            throw new SemanticException("Duplicated variable definition");
        }
        Type pointeeType = switch (def.bType) {
            case INT -> I32_TYPE;
            case FLOAT -> F32_TYPE;
            default -> throw new SemanticException(String.format("Wrong bType: %s", def.bType));
        };
        // 编译期计算数组每一维的长度，然后从右向左"组装"成数组类型
        ArrayList<Integer> lengths = new ArrayList<>();
        for (Exp len : def.getIndexes()) {
            lengths.add(Evaluate.evalConstIntExp(len));
        }
        for (int i = lengths.size() - 1; i >= 0; i--) {
            int len = lengths.get(i);
            pointeeType = new ArrayType(len, pointeeType);
        }
        // 解析其初始化内容
        Init astInit = def.getInit();
        Initial init = null;
        if (astInit != null) {
            if (pointeeType instanceof BasicType) {
                // if (!(astInit instanceof Exp)) {
                //     throw new SemanticException("Value variable not init by value");
                // }
                if (eval) {
                    init = visitInitVal((BasicType) pointeeType, (Exp) astInit);
                } else {
                    init = visitInitExp((BasicType) pointeeType, (Exp) astInit);
                }
            } else {
                // ArrayType
                // if (!(astInit instanceof InitArray)) {
                //     throw new SemanticException("Array variable not init by a list");
                // }
                init = visitInitArray((InitArray) astInit, (ArrayType) pointeeType, constant, eval);
            }
        }
        // 如果是全局变量且没有初始化，则初始化为零
        if (isGlobal && init == null) {
            if (pointeeType instanceof BasicType) {
                init = new Initial.ValueInit(CONST_0, pointeeType);
            } else {
                init = new Initial.ZeroInit(pointeeType);
            }
        }
        // 全局: 直接给出初始化结果, 局部: 分配空间, store + memset 初始化的值
        Value pointer;
        if (!isGlobal) {
            // 局部
            // 分配的空间指向 pointer
            assert curBB != null;
            pointer = new Alloc(pointeeType, curBB);
            if (VISITOR.__ONLY_PARSE_OUTSIDE_DIM) {
                if (pointeeType instanceof ArrayType) {
                    initZeroHelper(pointer, pointeeType);
                }
                if (init != null) {
                    initLocalVarHelper(pointer, init); // 生成初始化
                }
            } else {
                // Type pointeeType = ((PointerType) pointer.getType()).getInnerType();
                if (!(init == null)) {
                    if (init instanceof Initial.ExpInit) {
                        Value v = trimTo(((Initial.ExpInit) init).getResult(), (BasicType) pointeeType);
                        new Store(v, pointer, curBB);
                    } else if (init instanceof Initial.ValueInit) {
                        Value v = trimTo(((Initial.ValueInit) init).getValue(), (BasicType) pointeeType);
                        new Store(v, pointer, curBB);
                    } else if (init instanceof Initial.ArrayInit) {
                        ArrayList<Value> initValueList = init.getFlattenInit();
                        HashSet<Value> checkSet = new HashSet<>(initValueList);
                        assert checkSet.size() > 0;
                        boolean allZero = false;
                        if (checkSet.size() == 1) {
                            if (checkSet.iterator().next().equals(CONST_0)) {
                                initZeroHelper(pointer, pointeeType);
                                allZero = true;
                            }
                        }
                        if (!allZero) {
                            boolean afterMemset = false;
                            if (initValueList.size() / 5 > checkSet.size() / 3) {
                                initZeroHelper(pointer, pointeeType);
                                afterMemset = true;
                            }
                            ArrayList<Value> dimList = new ArrayList<>();
                            for (int i = 0; i <= ((ArrayType) pointeeType).getDimSize(); i++) {
                                dimList.add(CONST_0);
                            }
                            BasicType basicType = ((ArrayType) pointeeType).getBaseEleType();
                            Value ptr = new GetElementPtr(basicType, pointer, dimList, curBB);
                            if (!(initValueList.get(0) == CONST_0 && afterMemset)) {
                                dimList = new ArrayList<>();
                                dimList.add(CONST_0);
                                new Store(initValueList.get(0), ptr, curBB);
                            }
                            for (int i = 1; i < initValueList.size(); i++) {
                                Value initVal = initValueList.get(i);
                                if (initVal == CONST_0 && afterMemset) {
                                    continue;
                                }
                                dimList = new ArrayList<>();
                                dimList.add(Constant.ConstantInt.getConstInt(i));
                                Value p = new GetElementPtr(basicType, ptr, dimList, curBB);
                                new Store(trimTo(initValueList.get(i), basicType), p, curBB);
                            }
                        }
                    }
                }
            }
        } else {
            pointer = new GlobalVal.GlobalValue(pointeeType, def, init);
        }
        // 构建符号表项并插入符号表
        Symbol symbol = new Symbol(ident, pointeeType, init, constant, pointer);
        currentSymTable.add(symbol);
        if (isGlobal) {
            manager.addGlobal(symbol);
        }
    }

    private Initial.ArrayInit visitInitArray(InitArray initial, ArrayType type, boolean constant, boolean eval) throws SemanticException {
        Initial.ArrayInit arrayInit = new Initial.ArrayInit(type);
        BasicType baseEleType = type.getBaseEleType();
        Type baseType = type.getBaseType();
        int needSize = type.getSize();
        int count = 0;
        while (count < needSize && initial.hasInit(count)) {
            Init init = initial.getNowInit();
            if (baseType instanceof ArrayType) {
                Initial innerInit;
                if (init instanceof InitArray) {
                    innerInit = visitInitArray((InitArray) init, (ArrayType) baseType, constant, eval);
                    initial.nowIdx++;
                } else {
                    innerInit = visitInitArray(initial, (ArrayType) baseType, constant, eval);
                }
                arrayInit.add(innerInit);
            } else {
                assert baseType instanceof BasicType;
                Exp singleInit;
                if (init instanceof Exp) {
                    singleInit = (Exp) init;
                } else {
                    assert init instanceof InitArray && ((InitArray) init).init.size() == 1;
                    Init preInit = ((InitArray) init).init.get(0);
                    assert preInit instanceof Exp;
                    singleInit = (Exp) preInit;
                }
                if (eval) {
                    // 必须编译期计算
                    arrayInit.add(visitInitVal(baseEleType, singleInit));
                } else {
                    assert !constant;
                    arrayInit.add(visitInitExp(baseEleType, singleInit));
                }

                initial.nowIdx++;
            }
            count++; // 统计已经初始化了多少个
        }
        while (count < type.getSize()) {
            // 初始化个数小于当前维度的长度，补零
            count++;
            if (type.getBaseType() instanceof BasicType) {
                arrayInit.add(new Initial.ValueInit(CONST_0, I32_TYPE));
            } else {
                assert type.getBaseType() instanceof ArrayType;
                arrayInit.add(new Initial.ZeroInit(type.getBaseType()));
            }
        }
        return arrayInit;
    }

    private Initial.ValueInit visitInitVal(BasicType basicType, Exp exp) throws SemanticException {
        Object eval = Evaluate.evalConstExp(exp);
        if (eval instanceof Integer) {
            switch (basicType.dataType) {
                case I32 -> {
                    return new Initial.ValueInit(new Constant.ConstantInt((int) eval), I32_TYPE);
                }
                case F32 -> {
                    return new Initial.ValueInit(new Constant.ConstantFloat((float) ((int) eval)), F32_TYPE);
                }
                default -> throw new SemanticException("Wrong init type: " + basicType);
            }
        } else {
            assert eval instanceof Float;
            switch (basicType.dataType) {
                case I32 -> {
                    return new Initial.ValueInit(new Constant.ConstantInt((int) ((float) eval)), I32_TYPE);
                }
                case F32 -> {
                    return new Initial.ValueInit(new Constant.ConstantFloat((float) eval), F32_TYPE);
                }
                default -> throw new SemanticException("Wrong init type: " + basicType);
            }
        }
    }

    private Initial.ExpInit visitInitExp(BasicType basicType, Exp exp) throws SemanticException {
        Value eval = visitExp(exp); // 运行期才计算
        return new Initial.ExpInit(eval, basicType);
    }

    // 由于编译器采用 Load-Store 形式，变量符号全部对应指针，所以在函数入口一开始先把形参全存到栈上 hhh
    private void visitFuncDef(FuncDef def) throws SemanticException {
        curLoop = new Loop(Loop.emptyLoop);
        TokenType funcTypeTk = def.getType().getType();
        Type retType = switch (funcTypeTk) {
            case VOID -> VoidType.getVoidType();
            case INT -> I32_TYPE;
            case FLOAT -> F32_TYPE;
            default -> throw new SemanticException("Wrong func ret type: " + funcTypeTk);
        };
        String ident = def.getIdent().getContent();
        if (manager.getFunctions().containsKey(ident)) {
            throw new SemanticException("Duplicated function defined");
        }
        isGlobal = false;
        // 入口基本块
        BasicBlock entry = new BasicBlock();
        curBB = entry;
        curLoop.setHeader(entry);
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
        curLoop.setFunc(function);
        manager.addFunction(function);
        function.setBody(entry);
        curFunc = function;
        entry.setFunction(curFunc, curLoop);
        visitBlock(def.getBody(), false); // 分析函数体
        if (!curBB.isTerminated()) {
            // 如果没有 return 补上一条
            switch (funcTypeTk) {
                case VOID -> new Instr.Return(curBB);
                case INT -> new Instr.Return(CONST_0, curBB);
                case FLOAT -> new Instr.Return(CONST_0F, curBB);
                default -> throw new SemanticException("Wrong func ret type: " + funcTypeTk);
            }
        }
        currentSymTable = currentSymTable.getParent();
        curBB = null;
        curFunc = null;
        isGlobal = true;
    }

    private Type visitFuncFParam(FuncFParam funcFParam) throws SemanticException {
        // 常数, 常数指针或者数组指针(注意降维)
        if (!funcFParam.isArray()) {
            return getBasicType(funcFParam);
        } else {
            ArrayList<Integer> lengths = new ArrayList<>();
            for (Exp index : funcFParam.getSizes()) {
                int len = Evaluate.evalConstIntExp(index);
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

    public Manager getIr() {
        return this.manager;
    }

}
