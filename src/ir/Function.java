package ir;

import ir.type.DataType;
import ir.type.Type;
import util.ILinkNode;

import java.util.*;

/**
 * 函数声明 (+ 函数体 = 函数定义)
 */
public class Function extends Value{
    private final String name;

    public static class Param extends Value{
        private int idx;
        private Type type;
        private Function parentFunc;
        public Param(Type type, int idx){
            this.type = type;
            this.idx = idx;
        }

        @Override
        public String toString(){
            return type.toString() + " " + name;
        }
    }

//    private final List<Val.Var> params; // 形参表
//    private final Type retType; // 返回值类型, 如果为 null 则表示 param


    private Type retType;
    private ArrayList<Param> params;

    //TODO: entry是否需要保留
    private BasicBlock entry = null; // 函数体

    private BasicBlock begin = new BasicBlock(this);
    private BasicBlock end = new BasicBlock(this);

    //TODO: assign to 刘传
    private Map<BasicBlock, HashSet<BasicBlock>> edge;


    public Function(String name, ArrayList<Param> params, Type retType) {
        begin.setNext(end);
        end.setPrev(begin);
        this.name = name;
        this.params = params;
        for(Param param: params){
            param.parentFunc = this;
        }
        this.retType = retType;
    }

    public boolean hasBody() {
        return entry != null;
    }

    public boolean hasRet() {
        return retType != null;
    }

    //优化所需方法
    public void insertAtBegin(BasicBlock in) {
        in.setPrev(begin);
        in.setNext(begin.getNext());
        begin.getNext().setPrev(in);
        begin.setNext(in);
    }

    public void insertAtEnd(BasicBlock in) {
        in.setPrev(end.getPrev());
        in.setNext(end);
        end.getPrev().setNext(in);
        end.setPrev(in);
    }

    public BasicBlock getBegin() {
        return (BasicBlock) begin.getNext();
    }

    public BasicBlock getEnd() {
        return (BasicBlock) end.getPrev();
    }

    public boolean isEmpty() {
        return begin.getNext().equals(end);
    }


//    private String getTypeStr() {
//        if (hasRet()) {
//            return retType.toString();
//        } else {
//            return "void";
//        }
//    }

    private String getTypeStr() {
        if (retType instanceof Type.BasicType || retType instanceof Type.VoidType) {
            return retType.toString();
        } else {
            System.err.println("func ret type error!!!");
            return null;
        }
    }

    // 输出函数声明
    public String getDeclare() {
        String paramList = params.stream().map(var -> var.getType().toString()).reduce((s, s2) -> s + ", " + s2).orElse("");
        return "declare " + getTypeStr() + " @" + name + "(" + paramList + ")";
    }

    // 输出函数定义
    public String getDefinition() {
        if (this.entry == null) {
            throw new AssertionError("Function without body");
        }
        String paramList = params.stream().map(Value::toString).reduce((s, s2) -> s + ", " + s2).orElse("");
        StringBuilder body = new StringBuilder();
        Queue<BasicBlock> queue = new LinkedList<>();
        Set<BasicBlock> enqueued = new HashSet<>();
        queue.offer(this.entry);
        enqueued.add(this.entry);
        while (!queue.isEmpty()) {
            BasicBlock block = queue.poll();
            ILinkNode node = block.getEntry();
            body.append(block.getLabel()).append(":\n");
            while (node.hasNext()) {
                body.append("  ").append(node).append("\n");
                if (node instanceof Instr.Branch) {
                    BasicBlock thenBlock = ((Instr.Branch) node).getThenTarget();
                    BasicBlock elseBlock = ((Instr.Branch) node).getElseTarget();
                    if (!enqueued.contains(thenBlock)) {
                        queue.offer(thenBlock);
                        enqueued.add(thenBlock);
                    }
                    if (!enqueued.contains(elseBlock)) {
                        queue.offer(elseBlock);
                        enqueued.add(elseBlock);
                    }
                    break;
                } else if (node instanceof Instr.Jump) {
                    BasicBlock target = ((Instr.Jump) node).getTarget();
                    if (!enqueued.contains(target)) {
                        queue.offer(target);
                        enqueued.add(target);
                    }
                }
                node = node.getNext();
            }
            body.append("\n");
        }
        
        return "define dso_local " + getTypeStr() + " @" + name + "(" + paramList + ") {\n" + body + "}\n";
    }

    public String getName() {
        return this.name;
    }

    public ArrayList<Param> getParams() {
        return params;
    }

    public Type getRetType() {
        return this.retType;
    }

    public BasicBlock getBody() {
        return this.entry;
    }

    public void setBody(final BasicBlock body) {
        this.entry = body;
    }
    
}
