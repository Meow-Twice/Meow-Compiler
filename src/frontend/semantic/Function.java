package frontend.semantic;

import ir.BasicBlock;
import util.ILinkNode;
import ir.Instr;
import ir.Value;

import java.util.*;

/**
 * 函数声明 (+ 函数体 = 函数定义)
 */
public class Function {
    private final String name;
    private final List<Value.Var> params; // 形参表
    private final Types retType; // 返回值类型, 如果为 null 则表示 param
    private BasicBlock body = null; // 函数体

    public Function(String name, List<Value.Var> params, Types retType) {
        this.name = name;
        this.params = params;
        this.retType = retType;
    }

    public boolean hasBody() {
        return body != null;
    }

    public boolean hasRet() {
        return retType != null;
    }

    private String getTypeStr() {
        if (hasRet()) {
            return retType.toString();
        } else {
            return "void";
        }
    }

    // 输出函数声明
    public String getDeclare() {
        String paramList = params.stream().map(var -> var.getType().toString()).reduce((s, s2) -> s + ", " + s2).orElse("");
        return "declare " + getTypeStr() + " @" + name + "(" + paramList + ")";
    }

    // 输出函数定义
    public String getDefinition() {
        if (this.body == null) {
            throw new AssertionError("Function without body");
        }
        String paramList = params.stream().map(Value::toString).reduce((s, s2) -> s + ", " + s2).orElse("");
        StringBuilder body = new StringBuilder();
        Queue<BasicBlock> queue = new LinkedList<>();
        Set<BasicBlock> enqueued = new HashSet<>();
        queue.offer(this.body);
        enqueued.add(this.body);
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

    public List<Value.Var> getParams() {
        return this.params;
    }

    public Types getRetType() {
        return this.retType;
    }

    public BasicBlock getBody() {
        return this.body;
    }

    public void setBody(final BasicBlock body) {
        this.body = body;
    }
    
}
