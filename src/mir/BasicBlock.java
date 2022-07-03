package mir;

import mir.type.Type;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;

/**
 * 基本块, 具有标签名属性, 内部的代码以链表形式组织
 */
public class BasicBlock extends Value {
    private static final boolean ENABLE_DEBUG = true;
    private Function function;
//    private ILinkNode head = new EmptyNode();
//    private ILinkNode tail = new EmptyNode();

    // private Instr begin = new Instr();
    // private Instr end = new Instr();

    private Instr begin = new Instr(this);
    private Instr end = new Instr(this);

    //TODO: 前驱和后继相关方法
    private ArrayList<BasicBlock> precBBs = new ArrayList<>();//前驱
    private ArrayList<BasicBlock> succBBs = new ArrayList<>();//后继

    // 支配关系
    private HashSet<BasicBlock> doms = new HashSet<>();
    // 在支配树上的边关系,根据定义:
    // A dominator tree is a tree where
    // the children of each node are those nodes it immediately dominates.
    private HashSet<BasicBlock> idoms = new HashSet<>();
    // 支配边界,根据定义:
    // By definition,
    // there is a DF-edge (a, b) between every CFG nodes a, b such that a dominates
    // a predecessor of b , but does not strictly dominate b
    private HashSet<BasicBlock> DF = new HashSet<>();




    private String label;

    // 全局计数器, 记录下一个生成基本块的编号
    private static int bb_count = 0;

    public BasicBlock(){
        super(Type.BBType.getBBType());
        this.label = "b" + (++bb_count);
        begin.setNext(end);
        end.setPrev(begin);
    }

    // 自动命名基本块, 从 "b1" 开始
    public BasicBlock(Function function) {
        super(Type.BBType.getBBType());
        this.label = "b" + (++bb_count);
        this.function = function;
        begin.setNext(end);
        end.setPrev(begin);
        if (ENABLE_DEBUG) {
            System.err.println("new Basic block (" + label + ")");
        }
    }

//    @Override
//    public String getDescriptor() {
//        return super.getDescriptor();
//    }

    public Instr getEntry() {
        return (Instr) begin.getNext();
    }

    public Instr getLast() {
        return (Instr) end.getPrev();
    }

    // 向基本块末尾追加指令, 当且仅当该基本块未结束
    public void insertAtEnd(Instr in) {
        if (isTerminated()) {
            System.err.println("append after terminator: " + in.getName());
            // return;
        }
        // if (ENABLE_DEBUG) {
        //     System.err.println("append to (" + label + "): " + in);
        // }
//        Instr last = getLast();
//        last.setNext(follow);
//        follow.setPrev(last);
//        last = follow;
//        while (last.hasNext()) {
//            last = (Instr) last.getNext();
//        }
//        last.setNext(end);
//        end.setPrev(last);
        in.setPrev(end.getPrev());
        in.setNext(end);
        end.getPrev().setNext(in);
        end.setPrev(in);
    }

    public void insertAtHead(Instr in) {
        in.setPrev(begin);
        in.setNext(begin.getNext());
        begin.getNext().setPrev(in);
        begin.setNext(in);
    }

    public boolean isTerminated() {
        return getLast() instanceof Instr.Terminator;
    }

    public String getLabel() {
        return this.label;
    }

    public Function getFunction() {
        return function;
    }

    public void setFunction(Function function) {
        this.function = function;
        function.insertAtBegin(this);
    }


    public Instr getBeginInstr() {
        assert begin.getNext() instanceof Instr;
        return (Instr) begin.getNext();
    }

    public Instr getEndInstr() {
        assert end.getPrev() instanceof Instr;
        return (Instr) end.getPrev();
    }

    public Instr getBegin() {
        return begin;
    }

    public Instr getEnd() {
        return end;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BasicBlock that = (BasicBlock) o;
        return label.equals(that.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label);
    }

    public void setPrecBBs(ArrayList<BasicBlock> precBBs) {
        if (precBBs == null) {
            this.precBBs = new ArrayList<>();
        } else {
            this.precBBs = precBBs;
        }
    }

    public void setSuccBBs(ArrayList<BasicBlock> succBBs) {
        if (succBBs == null) {
            this.succBBs = new ArrayList<>();
        } else {
            this.succBBs = succBBs;
        }
    }

    public ArrayList<BasicBlock> getPrecBBs() {
        return precBBs;
    }

    public ArrayList<BasicBlock> getSuccBBs() {
        return succBBs;
    }

    public void setDoms(HashSet<BasicBlock> doms) {
        this.doms = doms;
    }

    public void setIdoms(HashSet<BasicBlock> idoms) {
        this.idoms = idoms;
    }

    public HashSet<BasicBlock> getDoms() {
        return doms;
    }

    public HashSet<BasicBlock> getIdoms() {
        return idoms;
    }

    public void setDF(HashSet<BasicBlock> DF) {
        this.DF = DF;
    }

    public HashSet<BasicBlock> getDF() {
        return DF;
    }
}
