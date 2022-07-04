package mir;

import mir.type.Type;
import util.ILinkNode;

public class Value extends ILinkNode {
    public static final String GLOBAL_PREFIX = "@";
    public static final String LOCAL_PREFIX = "%";
    public static final String GLOBAL_NAME_PREFIX = "g";
    public static final String LOCAL_NAME_PREFIX = "v";
    public static final String FPARAM_NAME_PREFIX = "f";
    public String prefix;
    public String name;

    private Use begin = new Use();
    private Use end = new Use();

    protected Type type;

    public Value() {
        super();
        begin.setNext(end);
        end.setPrev(begin);
    }

    public Value(Type type) {
        super();
        this.type = type;
        begin.setNext(end);
        end.setPrev(begin);
    }

    public void insertAtEnd(Use use) {
        //end.insertAfter(use);
        //end = use;
        end.insertBefore(use);
    }

    public String getName() {
        return prefix + name;
    }

    public Type getType() {
        return type;
    }

    public Use getBeginUse() {
        assert begin.getNext() instanceof Use;
        return (Use) begin.getNext();
    }

    public void modifyAllUseThisToUseA(Value A) {
        Use use = (Use) begin.getNext();
        while (use.getNext() != null) {
            Instr user = use.getUser();
            user.modifyUse(this, A, use.getIdx());
            use = (Use) use.getNext();
        }
    }

    public String getDescriptor() {
        return getType() + " " + getName();
    }
}
