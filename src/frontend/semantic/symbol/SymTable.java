package frontend.semantic.symbol;

import java.util.HashMap;
import java.util.HashMap;

/**
 * 分层结构的符号表
 */
public class SymTable {
    private final HashMap<String, Symbol> symbolMap = new HashMap<>();

    private final SymTable parent;

    public SymTable() {
        this(null);
    }

    public boolean hasParent() {
        return parent != null;
    }

    public SymTable(SymTable parent) {
        this.parent = parent;
    }

    public SymTable getParent() {
        return parent;
    }

    public void add(Symbol symbol) {
        assert !symbolMap.containsKey(symbol.getName());
        symbolMap.putIfAbsent(symbol.getName(), symbol);
    }

    public Symbol get(String name, boolean recursive) {
        Symbol symbol = symbolMap.get(name);
        if (null == symbol && recursive && null != parent) {
            return parent.get(name, true);
        }
        return symbol;
    }

    public boolean contains(String name, boolean recursive) {
        return get(name, recursive) != null;
    }
}
