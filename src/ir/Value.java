package ir;

import ir.type.Type;
import util.ILinkNode;

public class Value extends ILinkNode {
    public static final String GLOBAL_PREFIX = "@";
    public static final String LOCAL_PREFIX = "%";
//    public static final String BB_NAME_PREFIX = "b";
    public static final String GLOBAL_NAME_PREFIX = "g";
    public static final String LOCAL_NAME_PREFIX = "v";
//    private static int BB_COUNT = 0;
    private static int LOCAL_COUNT = 0;
    private static int GLOBAL_COUNT = 0;
    public String prefix = this instanceof GlobalVal ? GLOBAL_PREFIX : LOCAL_PREFIX;
    public String name = this instanceof GlobalVal ? GLOBAL_NAME_PREFIX + GLOBAL_COUNT++ : LOCAL_NAME_PREFIX + LOCAL_COUNT++;

    private Use begin = new Use();
    private Use end = begin;

    protected Type type;

    public Value() {
        super();
    }

    public Value(Type type) {
        super();
        this.type = type;
    }

    public void insertAtEnd(Use use) {
        end.insertAfter(use);
        end = use;
    }

    public String getDescriptor() {
        return prefix + name;
    }

    public Type getType() {
        return type;
    }
}
