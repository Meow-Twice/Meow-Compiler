package mir;

import mir.type.Type;
import util.ILinkNode;

public class Value extends ILinkNode {

    public enum AmaTag {
        value,
        func,
        bb,
        bino,
        icmp,
        fcmp,
        fneg,
        zext,
        fptosi,
        sitofp,
        alloc,
        load,
        store,
        gep,
        bitcast,
        call,
        phi,
        pcopy,
        move,
        branch,
        jump,
        ret,
    }

    public AmaTag tag = AmaTag.value;

    public static final String GLOBAL_PREFIX = "@";
    public static final String LOCAL_PREFIX = "%";
    public static final String GLOBAL_NAME_PREFIX = "g";
    public static final String LOCAL_NAME_PREFIX = "v";
    public static final String FPARAM_NAME_PREFIX = "f";
    public String prefix;
    public String name;

    public static boolean debug = true;

    private Use beginUse = new Use();
    private Use endUse = new Use();

    protected Type type;

    public Value() {
        super();
        // if(debug){
        //     System.err.println("fuck");
        // }
        beginUse.setNext(endUse);
        endUse.setPrev(beginUse);
    }

    public Value(Type type) {
        super();
        // if(debug){
        //     System.err.println("fuck");
        // }
        this.type = type;
        beginUse.setNext(endUse);
        endUse.setPrev(beginUse);
    }

    public void insertAtEnd(Use use) {
        //end.insertAfter(use);
        //end = use;
        endUse.insertBefore(use);
    }

    public String getName() {
        return prefix + name;
    }

    public Type getType() {
        return type;
    }

    public Use getBeginUse() {
        assert beginUse.getNext() instanceof Use;
        return (Use) beginUse.getNext();
    }

    public Use getEndUse() {
        assert endUse.getPrev() instanceof Use;
        return (Use) endUse.getPrev();
    }

    public boolean onlyOneUser() {
        return getBeginUse().equals(getEndUse());
    }

    public void modifyAllUseThisToUseA(Value A) {
        Use use = (Use) beginUse.getNext();
        while (use.getNext() != null) {
            Instr user = use.getUser();
            user.modifyUse(this, A, use.getIdx());
            use = (Use) use.getNext();
        }
    }

    public String getDescriptor() {
        return getType() + " " + getName();
    }

    public boolean isNoUse() {
        return beginUse.getNext().equals(endUse);
    }

    public boolean isAlu() {
        return tag == AmaTag.bino;
    }

    public boolean isStore() {
        return tag == AmaTag.store;
    }

    public boolean isLoad() {
        return tag == AmaTag.load;
    }

    public boolean isAlloc() {
        return tag == AmaTag.alloc;
    }

    public boolean isGep() {
        return tag == AmaTag.gep;
    }

    public boolean isCall() {
        return tag == AmaTag.call;
    }

    public boolean isPhi() {
        return tag == AmaTag.phi;
    }

    public boolean isIcmp() {
        return tag == AmaTag.icmp;
    }

    public boolean isFcmp() {
        return tag == AmaTag.fcmp;
    }

    public boolean isBranch() {
        return tag == AmaTag.branch;
    }

    public boolean isConstant() {
        return this instanceof Constant;
    }
    public boolean isConstantInt() {
        return this instanceof Constant.ConstantInt;
    }

}
