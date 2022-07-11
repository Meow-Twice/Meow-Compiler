package mir;

import lir.Machine;
import midend.CloneInfoMap;
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

    private Instr begin;
    private Instr end;

    //TODO: 前驱和后继相关方法
    private ArrayList<BasicBlock> precBBs;//前驱
    private ArrayList<BasicBlock> succBBs;//后继

    // 支配关系
    private HashSet<BasicBlock> doms;
    // 在支配树上的边关系,根据定义:
    // A dominator tree is a tree where
    // the children of each node are those nodes it immediately dominates.
    private HashSet<BasicBlock> idoms;
    // 支配边界,根据定义:
    // By definition,
    // there is a DF-edge (a, b) between every CFG nodes a, b such that a dominates
    // a predecessor of b , but does not strictly dominate b
    private HashSet<BasicBlock> DF;


    public Loop loop = Loop.emptyLoop;


    public boolean isLoopHeader = false;
    private boolean isLoopEntering = false;
    private boolean isLoopExiting = false;
    private boolean isLoopLatch = false;
    private boolean isExit = false;

    private int domTreeDeep;
    private BasicBlock IDominator;

    public void setLoopStart() {
        isLoopHeader = true;
        loop.setHeader(this);
    }

    public void setLoopEntering() {
        isLoopEntering = true;
    }

    public void setLoopExiting() {
        isLoopExiting = true;
    }

    public void setLoopLatch() {
        isLoopLatch = true;
    }

    public void setExit() {
        isExit = true;
    }

    public boolean isLoopHeader() {
        return isLoopHeader;
    }

    public boolean isLoopEntering() {
        return isLoopEntering;
    }

    public boolean isLoopExiting() {
        return isLoopExiting;
    }

    public boolean isLoopLatch() {
        return isLoopLatch;
    }

    public boolean isExit() {
        return isExit;
    }

    public int getLoopDep() {
        return loop.getLoopDepth();
    }

    private String label;

    // 全局计数器, 记录下一个生成基本块的编号
    private static int bb_count = 0;

    private static int empty_bb_cnt = 0;

    public BasicBlock() {
        super(Type.BBType.getBBType());
        init();
        this.label = "EMPTY_BB" + (empty_bb_cnt++);
        begin.setNext(end);
        end.setPrev(begin);
    }

    // 自动命名基本块, 从 "b1" 开始
    public BasicBlock(Function function, Loop loop) {
        super(Type.BBType.getBBType());
        init();
        this.loop = loop;
        this.label = "b" + (++bb_count);
        this.function = function;
        begin.setNext(end);
        end.setPrev(begin);
        if (ENABLE_DEBUG) {
            System.err.println("new Basic block (" + label + ")");
        }
        // 放到这是因为HashSet存的时候需要计算hash，BB的hash靠label
        function.insertAtEnd(this);
        loop.addBB(this);
    }

    public void setFunction(Function function, Loop loop) {
        this.loop = loop;
        this.label = "b" + (bb_count);
        this.function = function;
        function.insertAtBegin(this);
        loop.addBB(this);
    }


//    @Override
//    public String getDescriptor() {
//        return super.getDescriptor();
//    }

    //TODO:构造函数开始调用!!!
    private void init() {
        this.begin = new Instr();
        this.end = new Instr();
        this.precBBs = new ArrayList<>();
        this.succBBs = new ArrayList<>();
        this.doms = new HashSet<>();
        this.idoms = new HashSet<>();
        this.DF = new HashSet<>();
    }

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

    public void setDomTreeDeep(int domTreeDeep) {
        this.domTreeDeep = domTreeDeep;
    }

    public int getDomTreeDeep() {
        return domTreeDeep;
    }

    public void setIDominator(BasicBlock IDominator) {
        assert this.IDominator == null;
        this.IDominator = IDominator;
    }

    public BasicBlock getIDominator() {
        return IDominator;
    }

    @Override
    public String toString() {
        //return this.label + ":\t\t\t\t\t; loopDepth: " + loop.loopDepth + ";\t" + loop + ";\t" + loop.getHash();
        return this.label;
    }

    private Machine.Block mb = null;
    public void setMB(Machine.Block mb) {
        this.mb = mb;
    }

    public Machine.Block getMb(){
        return mb;
    }

    public Loop getLoop() {
        return loop;
    }

    //把当前BB复制到指定函数
    public BasicBlock cloneToFunc(Function function) {
        // 是循环内的BB, 复制的时候,
        // 先创建新的循环, 然后把BB塞到新的loop里面
        Loop srcLoop = this.loop;
        Loop tagLoop = new Loop(loop.getParentLoop());
        CloneInfoMap.addLoopReflect(srcLoop, tagLoop);

        BasicBlock ret = new BasicBlock(function, this.loop);
        CloneInfoMap.addValueReflect(this, ret);
        if (this.isLoopHeader) {
            tagLoop.setHeader(ret);
        }
        Instr instr = this.getBeginInstr();
        while (instr.getNext() != null) {

            instr = (Instr) instr.getNext();
        }
        return null;
    }
}
