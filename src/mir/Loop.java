package mir;

import midend.CloneInfoMap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;

public class Loop {

    public static final Loop emptyLoop = new Loop();
    private static int loop_num = 0;

    private Loop() {
        this.hash = -1;
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
    private HashSet<BasicBlock> enterings = new HashSet<>();
    private HashSet<BasicBlock> exitings = new HashSet<>();
    private HashSet<BasicBlock> latchs = new HashSet<>();
    private HashSet<BasicBlock> exits = new HashSet<>();

    private HashMap<Integer, HashSet<Instr>> conds = new HashMap<>();

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

    public void addCond(Instr instr) {
        int condNum = instr.getLoopCondCount();
        if (!conds.containsKey(condNum)) {
            conds.put(condNum, new HashSet<>());
        }
        conds.get(condNum).add(instr);
    }

    public HashSet<BasicBlock> getNowLevelBB() {
        return nowLevelBB;
    }

    public HashSet<BasicBlock> getExits() {
        return exits;
    }


    public HashMap<Integer, HashSet<Instr>> getConds() {
        return conds;
    }

    public HashSet<BasicBlock> getEnterings() {
        return enterings;
    }


    //把一个循环复制到指定函数
    public void cloneToFunc(Function function) {
        for (BasicBlock bb: nowLevelBB) {
            bb.cloneToFunc(function);
            CloneInfoMap.bbNeedFix.add(bb);
        }
        for (Loop next: childrenLoops) {
            next.cloneToFunc(function);
        }
    }

    public void fix() {
        for (BasicBlock bb: CloneInfoMap.bbNeedFix) {
            bb.fix();
        }
        CloneInfoMap.bbNeedFix.clear();
    }
}
