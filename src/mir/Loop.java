package mir;

import java.util.HashSet;
import java.util.Objects;

public class Loop {

    public static final Loop emptyLoop = new Loop();
    private static int loop_num = 0;

    private Loop() {
    }

    private String funcName = "";
    private String name = "";

    private Loop parentLoop;
    public int loopDepth = -1;
    private final HashSet<Loop> childrenLoops = new HashSet<>();

    private int hash;

    //同一循环内的BB 进一步划分为 entering header exiting latch exit
    //entering为header的前驱
    private HashSet<BasicBlock> nowLevelBB = new HashSet<>();

    private BasicBlock header;
    private HashSet<BasicBlock> enterings;
    private HashSet<BasicBlock> exitings;
    private HashSet<BasicBlock> latchs;
    private HashSet<BasicBlock> exits;


    public Loop(Loop parentLoop) {
        this.hash = loop_num++;
        this.parentLoop = parentLoop;
        this.loopDepth = parentLoop.loopDepth + 1;
        assert parentLoop.addChildLoop(this);
        name = parentLoop.name + "-" + (loopDepth == 0 ? 0 : parentLoop.getChildrenLoops().size());
    }

    public int getLoopDepth(){
        return loopDepth;
    }

    // 慎用，visitor用不到到这个
    public void setParentLoop(Loop parentLoop) {
        this.parentLoop = parentLoop;
    }

    public Loop getParentLoop() {
        return parentLoop;
    }


    /***
     * @param childLoop
     * @return true if this set: childrenLoops did not already contain the childLoop
     * true表示之前没有，成功插进去了
     */
    public boolean addChildLoop(Loop childLoop) {
        return childrenLoops.add(childLoop);
    }

    public HashSet<Loop> getChildrenLoops() {
        return childrenLoops;
    }

    /***
     * @param bb
     * @return true if this set: nowLevelBB did not already contain the bb
     * true表示之前没有，成功插进去了
     */
    public boolean addBB(BasicBlock bb) {
        return nowLevelBB.add(bb);
    }

    public BasicBlock getHeader() {
        return header;
    }

    public void setHeader(BasicBlock basicBlock) {
        header = basicBlock;
    }

    @Override
    public String toString() {
        return funcName + name;
    }

    public void setFunc(Function function) {
        funcName = function.getName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Loop loop = (Loop) o;
        return hash == loop.hash;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash);
    }

    public int getHash() {
        return hash;
    }

    public void addEntering(BasicBlock bb) {
        enterings.add(bb);
        bb.setLoopEntering();
    }

    public void addLatch(BasicBlock bb) {
        latchs.add(bb);
        bb.setLoopLatch();
    }

    public void addExiting(BasicBlock bb) {
        exitings.add(bb);
        bb.setLoopExiting();
    }

    public void addExit(BasicBlock bb) {
        exits.add(bb);
        bb.setExit();
    }
}
