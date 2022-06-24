package ir;

import util.ILinkNode;

/**
 * 基本块, 具有标签名属性, 内部的代码以链表形式组织
 */
public class BasicBlock {
    private static final boolean ENABLE_DEBUG = true;


    private static class EmptyNode extends ILinkNode {
    }

    private ILinkNode head = new EmptyNode();
    private ILinkNode tail = new EmptyNode();
    private String label;
    // 全局计数器, 记录下一个生成基本块的编号
    private static int bb_count = 0;

    // 自动命名基本块, 从 "b1" 开始
    public BasicBlock() {
        this("b" + (++bb_count));
    }

    public BasicBlock(String label) {
        this.label = label;
        head.setNext(tail);
        tail.setPrev(head);
        if (ENABLE_DEBUG) {
            System.err.println("new Basic block (" + label + ")");
        }
    }

    public ILinkNode getEntry() {
        return head.getNext();
    }

    public ILinkNode getLast() {
        return tail.getPrev();
    }

    // 向基本块末尾追加指令, 当且仅当该基本块未结束
    public void append(ILinkNode follow) {
        if (isTerminated()) {
            System.err.println("append after terminator: " + follow);
            return;
        }
        if (ENABLE_DEBUG) {
            System.err.println("append to (" + label + "): " + follow);
        }
        ILinkNode last = getLast();
        last.setNext(follow);
        follow.setPrev(last);
        last = follow;
        while (last.hasNext()) {
            last = last.getNext();
        }
        last.setNext(tail);
        tail.setPrev(last);
    }

    public boolean isTerminated() {
        return getLast() instanceof Instr.Terminator;
    }

    public String getLabel() {
        return this.label;
    }

}
