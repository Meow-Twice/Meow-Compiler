package frontend.semantic.symbol;

import frontend.semantic.Initial;
import mir.Value;
import mir.type.Type;

/**
 * 符号表中的一条符号信息
 */
public class Symbol {
    private final String name; // 变量名称
    private final Type type; // 变量类型
    private final Initial initial; // 初始值(可以为 null, 如果有则必须和变量匹配)
    private final boolean constant; // 是否为常量
    private final Value allocInst; // IR 中作为该变量地址的 Pointer

    public Symbol(final String name, final Type type, final Initial initial, final boolean constant, final Value allocInst) {
        this.name = name;
        this.type = type;
        this.initial = initial;
        this.constant = constant;
        this.allocInst = allocInst;
    }

    public String getName() {
        return this.name;
    }

    public Type getType() {
        return this.type;
    }

    public Initial getInitial() {
        return this.initial;
    }

    public boolean isConstant() {
        return this.constant;
    }

    public Value getValue() {
        return this.allocInst;
    }
    
}
