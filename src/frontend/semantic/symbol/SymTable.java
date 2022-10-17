package frontend.semantic.symbol;

import java.util.HashMap;

/**
 * 分层结构的符号表
 */
public class SymTable {
    // 本层次的符号表，从变量名映射到完整信息
    private final HashMap<String, Variable> nameVarMap = new HashMap<>();

    // 指向父层次 (如果是全局符号表，父层次为 null)
    private final SymTable parent;

    // 构造全局符号表
    public SymTable() {
        this(null);
    }

    // 构造局部符号表
    public SymTable(SymTable parent) {
        this.parent = parent;
    }

    public boolean hasParent() {
        return parent != null;
    }

    public SymTable getParent() {
        return parent;
    }

    // 添加变量
    public void add(Variable var) {
        assert !nameVarMap.containsKey(var.getName());
        nameVarMap.putIfAbsent(var.getName(), var);
    }

    // 获取变量信息
    //   recursive: 是否递归查找(即从父层次查找)
    // 使用变量 => 递归查找, 判断重定义错误 => 只查当前层
    public Variable get(String name, boolean recursive) {
        Variable var = nameVarMap.get(name);
        if (null == var && recursive && null != parent) {
            return parent.get(name, true);
        }
        return var;
    }

    // 符号表中是否包含某个变量
    public boolean contains(String name, boolean recursive) {
        return get(name, recursive) != null;
    }
}
