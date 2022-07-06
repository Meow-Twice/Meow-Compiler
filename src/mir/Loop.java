package mir;

import java.util.HashSet;

public class Loop {

    public static final Loop emptyLoop = new Loop();

    private Loop() {
    }

    private String funcName = "";
    private String name = "";

    private Loop parentLoop;
    public int loopDepth = -1;
    private final HashSet<Loop> childrenLoops = new HashSet<>();
    private final HashSet<BasicBlock> nowLevelBB = new HashSet<>();

    private BasicBlock startBB;

    public Loop(Loop parentLoop) {
        this.parentLoop = parentLoop;
        this.loopDepth = parentLoop.loopDepth + 1;
        assert parentLoop.addChildLoop(this);
        name = parentLoop.name + "-" + (loopDepth == 0 ? 1 : parentLoop.getChildrenLoops().size());
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

    public BasicBlock getStartBB() {
        return startBB;
    }

    public void setStartBB(BasicBlock basicBlock) {
        startBB = basicBlock;
    }

    @Override
    public String toString() {
        return funcName + name;
    }

    public void setFunc(Function function) {
        funcName = function.getName();
    }
}
