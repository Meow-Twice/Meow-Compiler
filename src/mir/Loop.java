package mir;

import lir.V;
import midend.CloneInfoMap;

import java.util.ArrayList;
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

    //归纳变量相关信息:
    private Value idcAlu;
    private Value idcPHI;
    private Value idcCmp;
    private Value idcInit;
    private Value idcEnd;
    private Value idcStep;

    private boolean idcSet = false;

    private int idcTimes;

    private boolean idcTimeSet = false;


    private boolean isCalcLoop = false;
    private Value aluPhiEnterValue;
    private Value calcAlu;
    private Value calcPhi;
    private int aluOtherIndex;

    private boolean isAddAndModLoop = false;
    private Value modPhi;
    private Value addIS;
    private Value remIS;
    private int init;
    private int base;
    private int mod;
    private int addConstIndex;

    private boolean canAggressiveParallel = false;

    public void setCanAggressiveParallel(boolean canAggressiveParallel) {
        this.canAggressiveParallel = canAggressiveParallel;
    }

    public boolean isCanAggressiveParallel() {
        return canAggressiveParallel;
    }

    public void clearAddAndModLoopInfo() {
        this.isAddAndModLoop = false;
        this.modPhi = null;
        this.addIS = null;
        this.remIS = null;
        this.init = 0;
        this.base = 0;
        this.mod = 0;
        this.addConstIndex = 0;
    }

    public void setAddAndModLoopInfo(Value modPhi, Value addIS, Value remIS, int init, int base, int mod, int addConstIndex) {
        this.isAddAndModLoop = true;
        this.modPhi = modPhi;
        this.addIS = addIS;
        this.remIS = remIS;
        this.init = init;
        this.base = base;
        this.mod = mod;
        this.addConstIndex = addConstIndex;
    }

    public boolean isAddAndModLoop() {
        return isAddAndModLoop;
    }

    public Value getModPhi() {
        return modPhi;
    }

    public Value getAddIS() {
        return addIS;
    }

    public Value getRemIS() {
        return remIS;
    }

    public int getInit() {
        return init;
    }

    public int getBase() {
        return base;
    }

    public int getMod() {
        return mod;
    }

    public int getAddConstIndex() {
        return addConstIndex;
    }

    public void clearCalcLoopInfo() {
        isCalcLoop = false;
        this.aluPhiEnterValue = null;
        this.calcAlu = null;
        this.calcPhi = null;
        this.aluOtherIndex = 0;
    }

    public void setCalcLoopInfo(Value aluPhiEnterValue, Value calcAlu, Value calcPhi, int aluOtherIndex) {
        isCalcLoop = true;
        this.aluPhiEnterValue = aluPhiEnterValue;
        this.calcAlu = calcAlu;
        this.calcPhi = calcPhi;
        this.aluOtherIndex = aluOtherIndex;
    }

    public boolean isCalcLoop() {
        return isCalcLoop;
    }

    public Value getAluPhiEnterValue() {
        return aluPhiEnterValue;
    }

    public Value getCalcAlu() {
        return calcAlu;
    }

    public Value getCalcPhi() {
        return calcPhi;
    }

    public int getAluOtherIndex() {
        return aluOtherIndex;
    }

    private boolean isArrayInit = false;
    private int arrayInitDims = 0;
    private Value initArray = null;
    private Value initValue = null;
    private HashSet<Instr> extras = null;

    public void setArrayInitInfo(int arrayInitDims, Value initArray, Value initValue, HashSet<Instr> extras) {
        this.isArrayInit = true;
        this.arrayInitDims = arrayInitDims;
        this.initArray = initArray;
        this.initValue = initValue;
        this.extras = extras;
    }

    public HashSet<Instr> getExtras() {
        return extras;
    }

    public boolean isArrayInit() {
        return isArrayInit;
    }

    public Value getInitValue() {
        return initValue;
    }

    public int getArrayInitDims() {
        return arrayInitDims;
    }

    public Value getInitArray() {
        return initArray;
    }

    public Loop(Loop parentLoop) {
        this.hash = loop_num++;
        this.parentLoop = parentLoop;
        this.loopDepth = parentLoop.loopDepth + 1;
        parentLoop.addChildLoop(this);
        name = parentLoop.name + "-" + (loopDepth == 0 ? 0 : parentLoop.getChildrenLoops().size());
    }

    public int getLoopDepth(){
//        return loopDepth;
        int ret = 0;
        Loop loop = this;
        while (loop.getParentLoop() != emptyLoop) {
            loop = loop.getParentLoop();
            ret++;
        }
        return ret;
//        int res = 0;
//        Loop loop = this.getParentLoop();
//        while (!emptyLoop.equals(loop)) {
//            loop = loop.getParentLoop();
//            res++;
//        }
//        return res;
    }

    // 慎用，visitor用不到到这个
    public void setParentLoop(Loop parentLoop) {
        this.parentLoop = parentLoop;
        this.parentLoop.addChildLoop(this);
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

    public void removeBB(BasicBlock bb) {
        nowLevelBB.remove(bb);
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
        int condNum = instr.getCondCount();
        if (!conds.containsKey(condNum)) {
            conds.put(condNum, new HashSet<>());
        }
        conds.get(condNum).add(instr);
    }

    public void clearCond() {
        conds.clear();
    }

    public void clear() {
        exits.clear();
        exitings.clear();
        enterings.clear();
        latchs.clear();
        //TODO:nowlevelBB手动维护(new BB 和 bb.remove时维护)
        //nowLevelBB.clear();
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

    public HashSet<BasicBlock> getLatchs() {
        return latchs;
    }

    public HashSet<BasicBlock> getExitings() {
        return exitings;
    }

    //把一个循环复制到指定函数
    public void cloneToFunc(Function function) {
        for (BasicBlock bb: nowLevelBB) {
            bb.cloneToFunc(function);
        }

        for (Loop next: childrenLoops) {
            next.cloneToFunc(function);
        }
    }

    //修正当前BB对应BB的use-def,同时修正简单的数据流:前驱后继关系
    public void fix() {
        for (BasicBlock bb: nowLevelBB) {
//            if (bb.getLabel().equals("b174")) {
//                System.err.println("ERR_174");
//            }
            assert CloneInfoMap.valueMap.containsKey(bb);
            BasicBlock needFixBB = (BasicBlock) CloneInfoMap.getReflectedValue(bb);

            ArrayList<BasicBlock> pres = new ArrayList<>();
            for (BasicBlock pre: bb.getPrecBBs()) {
                pres.add((BasicBlock) CloneInfoMap.getReflectedValue(pre));
            }
            //needFixBB.setPrecBBs(pres);
            needFixBB.modifyPres(pres);

            ArrayList<BasicBlock> succs = new ArrayList<>();
            for (BasicBlock succ: bb.getSuccBBs()) {
                succs.add((BasicBlock) CloneInfoMap.getReflectedValue(succ));
            }
            //needFixBB.setSuccBBs(succs);
            needFixBB.modifySucs(succs);

            needFixBB.fix();
            needFixBB.setLoop(CloneInfoMap.getReflectedLoop(bb.getLoop()));
            CloneInfoMap.getReflectedLoop(bb.getLoop()).addBB(needFixBB);
            if (bb.isLoopHeader) {
                needFixBB.setLoopHeader();
            }
        }
        for (Loop next: childrenLoops) {
            next.fix();
            //TODO:check
            CloneInfoMap.getReflectedLoop(this).addChildLoop(CloneInfoMap.getReflectedLoop(next));
        }
    }

    //简单循环符合下述形式:
    //for(int i = X; i < Y; i = i + Z) {
    //  xxx
    //  i不能被更改,没有break,continue
    //}
    // head = exiting need?
    public boolean isSimpleLoop() {
        return header.getPrecBBs().size() == 2 && latchs.size() == 1 && exitings.size() == 1 && exits.size() == 1;
    }

    public String infoString() {
        String ret = "\n";
        ret += "Hash: ";
        ret += String.valueOf(hash);
        ret += "\n";

        ret += "Deep: ";
        ret += String.valueOf(getLoopDepth());
        ret += "\n";


        ret += "Header: ";
        ret += header.getLabel() + " pre_num: " + String.valueOf(header.getPrecBBs().size());
        ret += "\n";

        ret += "latch: ";
        for (BasicBlock bb: latchs) {
            ret += " " + bb.getLabel();
        }
        ret += "\n";

        ret += "exiting: ";
        for (BasicBlock bb: exitings) {
            ret += " " + bb.getLabel();
        }ret += "\n";

        ret += "exit: ";
        for (BasicBlock bb: exits) {
            ret += " " + bb.getLabel();
        }
        ret += "\n";

        ret += "now_level_BB: ";
        for (BasicBlock bb: nowLevelBB) {
            ret += " " + bb.getLabel();
        }
        ret += "\n";

        if (isSimpleLoop() && isIdcSet()) {
            ret += "idcAlu: " + idcAlu.toString() + "\n";
            ret += "idcPHI: " + idcPHI.toString() + "\n";
            ret += "idcCmp: " + idcCmp.toString() + "\n";
            ret += "idcInit: " + idcInit.toString() + "\n";
            ret += "idcEnd: " + idcEnd.toString() + "\n";
            ret += "idcStep: " + idcStep.toString() + "\n";
        }

        return ret;
    }

    public void setIdc(Value idcAlu, Value idcPHI, Value idcCmp, Value idcInit, Value idcEnd, Value idcStep) {
        this.idcAlu = idcAlu;
        this.idcPHI = idcPHI;
        this.idcCmp = idcCmp;
        this.idcInit = idcInit;
        this.idcEnd = idcEnd;
        this.idcStep = idcStep;
        this.idcSet = true;
    }

    public Value getIdcAlu() {
        return idcAlu;
    }

    public Value getIdcPHI() {
        return idcPHI;
    }

    public Value getIdcCmp() {
        return idcCmp;
    }

    public Value getIdcInit() {
        return idcInit;
    }

    public Value getIdcEnd() {
        return idcEnd;
    }

    public Value getIdcStep() {
        return idcStep;
    }

    public boolean isIdcSet() {
        return idcSet;
    }

    public void clearIdcInfo() {
        this.idcSet = false;
    }

    public void setIdcTimes(int idcTimes) {
        this.idcTimes = idcTimes;
        this.idcTimeSet = true;
    }

    public int getIdcTimes() {
        return idcTimes;
    }

    public boolean isIdcTimeSet() {
        return idcTimeSet;
    }

    public boolean hasChildLoop() {
        return childrenLoops.size() != 0;
    }
}
