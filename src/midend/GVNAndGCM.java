package midend;

import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class GVNAndGCM {

    //TODO:GCM更新phi,删除无用phi,添加数组相关分析,
    // 把load,store,get_element_ptr也纳入GCM考虑之中
    private ArrayList<Function> functions;
    private HashMap<Function, HashSet<Instr>> pinnedInstrMap;
    private HashMap<BasicBlock, Instr> insertPos;

    private static HashSet<Instr> know;
    private BasicBlock root;


    HashMap<String, Instr> GvnMap = new HashMap<>();
    HashMap<String, Integer> GvnCnt = new HashMap<>();

    public GVNAndGCM(ArrayList<Function> functions) {
        this.functions = functions;
        this.pinnedInstrMap = new HashMap<>();
        this.insertPos = new HashMap<>();
//        for (Function function: functions) {
//            this.pinnedInstrMap.put(function, new HashSet<>());
//        }
    }

    public void Run() {
        Init();
        GVN();
        GCM();
        //GVN();
    }

    private void Init() {
        for (Function function: functions) {
            HashSet<Instr> pinnedInstr = new HashSet<>();
            BasicBlock bb = function.getBeginBB();
            while (bb.getNext() != null) {
                Instr instr = bb.getBeginInstr();
                while (instr.getNext() != null) {
                    if (isPinned(instr)) {
                        pinnedInstr.add(instr);
                    }
                    instr = (Instr) instr.getNext();
                }
                insertPos.put(bb, (Instr) bb.getEndInstr().getPrev());
                bb = (BasicBlock) bb.getNext();
            }
            pinnedInstrMap.put(function, pinnedInstr);
        }
    }

    //%v44 = phi i32 [ 0, %b0 ], [ %v38, %b2 ];
    //a2 = phi (a0, a1);
    //TODO:phi需要删除,当且仅当,定义构成了一条没有分叉的路径
    //向上dfs 找到第一个到达的定义 即为reach def
    //fixme: 当前认为,phi不能被删除,否则影响正确性 Time:07-08-02:52,
    // 示例样例路径 ~/testcase/07-08-02:52.sy
    private void GCM() {
        for (Function function: functions) {
            scheduleEarlyForFunc(function);
        }
        for (Function function: functions) {
            scheduleLateForFunc(function);
        }
        //printBeforeMove();
        move();
    }

    private void GVN() {
        GvnMap.clear();
        GvnCnt.clear();
        for (Function function: functions) {
            GVNForFunc(function);
        }
    }

    private void GVNForFunc(Function function) {
        BasicBlock bb = function.getBeginBB();
        RPOSearch(bb);
    }

    //逆后序遍历支配树
    //TODO:GVN遍历支配树,在树的一条链上,先发现的定义的生命周期一定更长,因此只记录每条链上第一次发现的def即可
    private void RPOSearch(BasicBlock bb) {
        //Constant folding
        Instr alu = bb.getBeginInstr();
        while (alu.getNext() != null) {
            if (alu instanceof Instr.Alu && ((Instr.Alu) alu).hasTwoConst()) {
                Constant value = calc((Instr.Alu) alu);

                alu.modifyAllUseThisToUseA(value);
                alu.remove();
            }
            alu = (Instr) alu.getNext();
        }

        HashSet<Instr> instrs  = new HashSet<>();
        Instr instr = bb.getBeginInstr();
        while (instr.getNext() != null) {
            if (canGVN(instr)) {
                if (!addInstrToGVN(instr)) {
                    instrs.add(instr);
                }
            }
            instr = (Instr) instr.getNext();
        }

        for (BasicBlock next: bb.getIdoms()) {
            RPOSearch(next);
        }

        for (Instr instr1: instrs) {
            removeInstrFromGVN(instr1);
        }
    }

    private boolean canGVN(Instr instr) {
        return instr instanceof Instr.Alu || instr instanceof Instr.GetElementPtr;
        //return instr instanceof Instr.Alu;
    }




    private void scheduleEarlyForFunc(Function function) {
        HashSet<Instr> pinnedInstr = pinnedInstrMap.get(function);
        know = new HashSet<>();
        root = function.getBeginBB();
        for (Instr instr: pinnedInstr) {
            instr.setEarliestBB(instr.parentBB());
            know.add(instr);
        }
//        for (Instr instr: pinnedInstr) {
//            for (Value value: instr.getUseValueList()) {
//                if (value instanceof Constant || value instanceof BasicBlock ||
//                        value instanceof GlobalVal || value instanceof Function || value instanceof Function.Param) {
//                    continue;
//                }
//                assert value instanceof Instr;
//                scheduleEarly((Instr) value);
//            }
//        }
        BasicBlock bb = function.getBeginBB();
        while (bb.getNext() != null) {
            Instr instr = bb.getBeginInstr();
            while (instr.getNext() != null) {
                if (!know.contains(instr)) {
                    scheduleEarly(instr);
                } else if (pinnedInstr.contains(instr)) {
                    for (Value value: instr.getUseValueList()) {
                        if (value instanceof Constant || value instanceof BasicBlock ||
                                value instanceof GlobalVal || value instanceof Function || value instanceof Function.Param) {
                            continue;
                        }
                        assert value instanceof Instr;
                        scheduleEarly((Instr) value);
                    }
                }
                instr = (Instr) instr.getNext();
            }
            bb = (BasicBlock) bb.getNext();
        }
    }

    private void scheduleEarly(Instr instr) {
        //System.out.println(cnt++);
        //System.out.println(know.size());
        if (know.contains(instr)) {
            return;
        }
        know.add(instr);
        instr.setEarliestBB(root);
        for (Value X: instr.getUseValueList()) {
//            if (X instanceof Constant || X instanceof BasicBlock ||
//                    X instanceof GlobalVal || X instanceof Function || X instanceof Function.Param) {
//                continue;
//            }
//            if (!(X instanceof Instr)) {
//                continue;
//            }
            if (X instanceof Instr) {
                //assert X instanceof Instr;
                scheduleEarly((Instr) X);
                if (instr.getEarliestBB().getDomTreeDeep() < ((Instr) X).getEarliestBB().getDomTreeDeep()) {
                    instr.setEarliestBB(((Instr) X).getEarliestBB());
                }
            }
        }
    }


    private void scheduleLateForFunc(Function function) {
        HashSet<Instr> pinnedInstr = pinnedInstrMap.get(function);
        know = new HashSet<>();
        for (Instr instr: pinnedInstr) {
            instr.setLatestBB(instr.parentBB());
            know.add(instr);
        }
        for (Instr instr: pinnedInstr) {
            Use use = instr.getBeginUse();
            while (use.getNext() != null) {
                scheduleLate(use.getUser());
                use = (Use) use.getNext();
            }
        }
//        BasicBlock bb = function.getBeginBB();
//        while (bb.getNext() != null) {
//            Instr instr = bb.getBeginInstr();
//            while (instr.getNext() != null) {
//                if (!know.contains(instr)) {
//                    scheduleLate(instr);
//                }
//                instr = (Instr) instr.getNext();
//            }
//            bb = (BasicBlock) bb.getNext();
//        }
        BasicBlock bb = function.getBeginBB();
        while (bb.getNext() != null) {
            ArrayList<Instr> instrs = new ArrayList<>();
            Instr instr = bb.getEndInstr();
            while (instr.getPrev() != null) {
                instrs.add(instr);
                instr = (Instr) instr.getPrev();
            }
            for (Instr instr1: instrs) {
                if (!know.contains(instr1)) {
                    scheduleLate(instr1);
                }
            }
            bb = (BasicBlock) bb.getNext();
        }
    }

    private void scheduleLate(Instr instr) {
        if (know.contains(instr)) {
            return;
        }
        know.add(instr);
        BasicBlock lca = null;
        Use usePos = instr.getBeginUse();
        while (usePos.getNext() != null) {
            Instr y = usePos.getUser();

            scheduleLate(y);
            BasicBlock use = y.getLatestBB();
            if (y instanceof Instr.Phi) {
                //int j = ((Instr.Phi) y).getValueIndexInUseValueList(instr);
                int j = usePos.getIdx();
                use = y.getLatestBB().getPrecBBs().get(j);
            }
            lca = findLCA(lca, use);
            usePos = (Use) usePos.getNext();
        }
        // use the latest and earliest blocks to pick final positing
        // now latest is lca
//        if (lca == null) {
//            instr.setLatestBB(instr.getEarliestBB());
//            return;
//        }
        BasicBlock best = lca;
        if (lca == null) {
            System.err.println("err_GCM");
        }
        while (!lca.equals(instr.getEarliestBB())) {
            if (lca.getLoopDep() < best.getLoopDep()) {
                best = lca;
            }
            lca = lca.getIDominator();
            if (lca == null) {
                System.err.println("err_GCM");
            }
        }
        if (lca.getLoopDep() < best.getLoopDep()) {
            best = lca;
        }
        instr.setLatestBB(best);

        if (!instr.getLatestBB().equals(instr.parentBB())) {
            instr.delFromNowBB();
            //TODO:检查 insert 位置 是在头部还是尾部
            //i.getLatestBB().getEndInstr().insertBefore(i);
//            Instr pos = findInsertPos(instr, instr.getLatestBB());
//            pos.insertBefore(instr);
//            Instr pos = instr.getLatestBB().getBeginInstr();
//            while (pos instanceof Instr.Phi) {
//                pos = (Instr) pos.getNext();
//            }
            Instr pos = null;
//            if (instr.getLatestBB().getDomTreeDeep() < instr.parentBB().getDomTreeDeep()) {
//                pos = insertPos.get(instr.getLatestBB());
//            } else {
//                pos = instr.getLatestBB().getBeginInstr();
//                while (pos instanceof Instr.Phi) {
//                    pos = (Instr) pos.getNext();
//                }
//            }
//            pos.insertAfter(instr);
            pos = findInsertPos(instr, instr.getLatestBB());
            pos.insertBefore(instr);
            instr.setBb(instr.getLatestBB());
        }
    }

    private void move() {
        for (Function function: functions) {
            BasicBlock bb = function.getBeginBB();
            while (bb.getNext() != null) {
                Instr instr = bb.getBeginInstr();
                ArrayList<Instr> instrs = new ArrayList<>();
                while (instr.getNext() != null) {
                    instrs.add(instr);
                    instr = (Instr) instr.getNext();
                }
//                Instr instr = bb.getEndInstr();
//                ArrayList<Instr> instrs = new ArrayList<>();
//                while (instr.getPrev() != null) {
//                    instrs.add(instr);
//                    instr = (Instr) instr.getPrev();
//                }
                for (Instr i: instrs) {
                    if (!i.getLatestBB().equals(bb)) {
                        assert false;
//                        i.getPrev().setNext(i.getNext());
//                        i.getNext().setPrev(i.getPrev());
                        i.delFromNowBB();
                        //TODO:检查 insert 位置 是在头部还是尾部
                        //i.getLatestBB().getEndInstr().insertBefore(i);
                        Instr pos = findInsertPos(i, i.getLatestBB());
                        pos.insertBefore(i);
                        //i.getLatestBB().getBeginInstr().insertBefore(i);
                        i.setBb(i.getLatestBB());
                    }
                }

                bb = (BasicBlock) bb.getNext();
            }
        }
    }

    private BasicBlock findLCA(BasicBlock a, BasicBlock b) {
        if (a == null) {
            return b;
        }
        while (a.getDomTreeDeep() > b.getDomTreeDeep()) {
            a = a.getIDominator();
        }
        while (b.getDomTreeDeep() > a.getDomTreeDeep()) {
            b = b.getIDominator();
        }
        while (!a.equals(b)) {
            a = a.getIDominator();
            b = b.getIDominator();
        }
        return a;
    }

    private Instr findInsertPos(Instr instr, BasicBlock bb) {
        ArrayList<Value> users = new ArrayList<>();
        Use use = instr.getBeginUse();
        while (use.getNext() != null) {
            users.add(use.getUser());
            use = (Use) use.getNext();
        }
        Instr later = null;
        Instr pos = bb.getBeginInstr();
        while (pos.getNext() != null) {
            if (pos instanceof Instr.Phi) {
                pos = (Instr) pos.getNext();
                continue;
            }
            if (users.contains(pos)) {
                later = pos;
                break;
            }
            pos = (Instr) pos.getNext();
        }

        if (later != null) {
            return later;
        }

        return bb.getEndInstr();
    }




    // TODO:考虑数组变量读写的GCM 指针是SSA形式 但是内存不是
    //  考虑移动load,store是否会产生影响
    //  移动的上下限是对同一个数组的最近的load/store?
    private boolean isPinned(Instr instr) {
        return instr instanceof Instr.Jump || instr instanceof Instr.Branch ||
                instr instanceof Instr.Phi || instr instanceof Instr.Return ||
                instr instanceof Instr.Store || instr instanceof Instr.Load ||
                // instr instanceof Instr.Icmp || instr instanceof Instr.Fcmp ||
                //instr instanceof Instr.GetElementPtr ||
                instr instanceof Instr.Call;
    }

    private void printBeforeMove() {
        for (Function function: functions) {
            BasicBlock bb = function.getBeginBB();
            System.err.println(function.getName());
            while (bb.getNext() != null) {
                Instr instr = bb.getBeginInstr();
                System.err.println(bb.toString());
                while (instr.getNext() != null) {
                    System.err.println("    " + instr.toString() + " early:" + instr.getEarliestBB().toString() + " last:" + instr.getLatestBB());
                    instr = (Instr) instr.getNext();
                }
                bb = (BasicBlock) bb.getNext();
            }
        }
    }

    private void add(String str, Instr instr) {
        if (!GvnCnt.containsKey(str)) {
            GvnCnt.put(str, 1);
        } else {
            GvnCnt.put(str, GvnCnt.get(str) + 1);
        }
        if (!GvnMap.containsKey(str)) {
            GvnMap.put(str, instr);
        }
    }

    private void remove(String str) {
        GvnCnt.put(str, GvnCnt.get(str) - 1);
        if (GvnCnt.get(str) == 0) {
            GvnMap.remove(str);
        }
    }

    private boolean addInstrToGVN(Instr instr) {
        //进行替换
        boolean tag = false;
        if (instr instanceof Instr.GetElementPtr) {
            String hash = ((Instr.GetElementPtr) instr).getPtr().getName();
            ArrayList<Value> indexs = ((Instr.GetElementPtr) instr).getIdxList();
            for (int i = 0; i < indexs.size(); i++) {
                hash = hash + "[" + indexs.get(i).getName() + "]";
            }
            if (GvnMap.containsKey(hash)) {
                instr.modifyAllUseThisToUseA(GvnMap.get(hash));
                instr.remove();
                return true;
            }
            add(hash, instr);
        } else if (instr instanceof Instr.Alu) {
            String hash = instr.getUseValueList().get(0).getName() + ((Instr.Alu) instr).getOp().getName() + instr.getUseValueList().get(1).getName();
            if (GvnMap.containsKey(hash)) {
                instr.modifyAllUseThisToUseA(GvnMap.get(hash));
                instr.remove();
                return true;
            }
            if (((Instr.Alu) instr).getOp().equals(Instr.Alu.Op.ADD) || ((Instr.Alu) instr).getOp().equals(Instr.Alu.Op.MUL)) {
                String str = instr.getUseValueList().get(0).getName() + ((Instr.Alu) instr).getOp().getName() + instr.getUseValueList().get(1).getName();
                add(str, instr);
                if (!instr.getUseValueList().get(0).getName().equals(instr.getUseValueList().get(1).getName())) {
                    str = instr.getUseValueList().get(1).getName() + ((Instr.Alu) instr).getOp().getName() + instr.getUseValueList().get(0).getName();
                    add(str, instr);
                }
            } else {
                String str = instr.getUseValueList().get(0).getName() + ((Instr.Alu) instr).getOp().getName() + instr.getUseValueList().get(1).getName();
                add(str, instr);
            }
        }
        return tag;
    }

    private void removeInstrFromGVN(Instr instr) {
        if (instr instanceof Instr.GetElementPtr) {
            String hash = ((Instr.GetElementPtr) instr).getPtr().getName();
            ArrayList<Value> indexs = ((Instr.GetElementPtr) instr).getIdxList();
            for (int i = 0; i < indexs.size(); i++) {
                hash = hash + "[" + indexs.get(i).getName() + "]";
            }
            remove(hash);
        } else if (instr instanceof Instr.Alu) {
            if (((Instr.Alu) instr).getOp().equals(Instr.Alu.Op.ADD) || ((Instr.Alu) instr).getOp().equals(Instr.Alu.Op.MUL)) {
                String str = instr.getUseValueList().get(0).getName() + ((Instr.Alu) instr).getOp().getName() + instr.getUseValueList().get(1).getName();
                remove(str);
                if (!instr.getUseValueList().get(0).getName().equals(instr.getUseValueList().get(1).getName())) {
                    str = instr.getUseValueList().get(1).getName() + ((Instr.Alu) instr).getOp().getName() + instr.getUseValueList().get(0).getName();
                    remove(str);
                }
            } else {
                String str = instr.getUseValueList().get(0).getName() + ((Instr.Alu) instr).getOp().getName() + instr.getUseValueList().get(1).getName();
                remove(str);
            }
        }
    }

    private Constant calc(Instr.Alu alu) {
        assert alu.hasTwoConst();
        if (alu.getType().isInt32Type()) {
            Instr.Alu.Op op = alu.getOp();
            int ConstA = (int) ((Constant) alu.getUseValueList().get(0)).getConstVal();
            int ConstB = (int) ((Constant) alu.getUseValueList().get(1)).getConstVal();
            Constant.ConstantInt value = null;
            if (op.equals(Instr.Alu.Op.ADD)) {
                value = new Constant.ConstantInt(ConstA + ConstB);
            } else if (op.equals(Instr.Alu.Op.SUB)) {
                value = new Constant.ConstantInt(ConstA - ConstB);
            } else if (op.equals(Instr.Alu.Op.MUL)) {
                value = new Constant.ConstantInt(ConstA * ConstB);
            } else if (op.equals(Instr.Alu.Op.DIV)) {
                if (ConstB == 0) {
                    ConstB = 1;
                }
                value = new Constant.ConstantInt(ConstA / ConstB);
            } else if (op.equals(Instr.Alu.Op.REM)) {
                if (ConstB == 0) {
                    ConstB = 1;
                }
                value = new Constant.ConstantInt(ConstA % ConstB);
            } else {
                System.err.println("err_1");
            }

            return value;

        } else {
            Instr.Alu.Op op = alu.getOp();
            float ConstA = (float) ((Constant) alu.getUseValueList().get(0)).getConstVal();
            float ConstB = (float) ((Constant) alu.getUseValueList().get(1)).getConstVal();
            Constant.ConstantFloat value = null;
            if (op.equals(Instr.Alu.Op.FADD)) {
                value = new Constant.ConstantFloat(ConstA + ConstB);
            } else if (op.equals(Instr.Alu.Op.FSUB)) {
                value = new Constant.ConstantFloat(ConstA - ConstB);
            } else if (op.equals(Instr.Alu.Op.FMUL)) {
                value = new Constant.ConstantFloat(ConstA * ConstB);
            } else if (op.equals(Instr.Alu.Op.FDIV)) {
                value = new Constant.ConstantFloat(ConstA / ConstB);
            } else if (op.equals(Instr.Alu.Op.FREM)) {
                value = new Constant.ConstantFloat(ConstA % ConstB);
            } else {
                System.err.println("err_2");
            }

            return value;
        }
    }

}
