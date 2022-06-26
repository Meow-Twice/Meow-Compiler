package ir;

import ir.type.Type;

import java.util.ArrayList;

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
    public ArrayList<BasicBlock> precBBs = new ArrayList<>();//前驱
    public ArrayList<BasicBlock> succBBs = new ArrayList<>();//后继
    //private ArrayList<BasicBlock> pre;
    //private ArrayList<BasicBlock> pre;



    private String label;

    // 全局计数器, 记录下一个生成基本块的编号
    private static int bb_count = 0;

    public BasicBlock(){
        super(Type.BBType.getBBType());
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
            System.err.println("append after terminator: " + in);
            return;
        }
        if (ENABLE_DEBUG) {
            System.err.println("append to (" + label + "): " + in);
        }
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
}
