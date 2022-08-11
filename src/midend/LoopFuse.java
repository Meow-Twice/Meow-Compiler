package midend;

import lir.V;
import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class LoopFuse {

    private ArrayList<Function> functions;
    private HashSet<Loop> loops = new HashSet<>();
    private HashSet<Loop> removes = new HashSet<>();

    public LoopFuse(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        init();
        loopFuse();
    }

    private void init() {
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                if (bb.isLoopHeader()) {
                    loops.add(bb.getLoop());
                }
            }
        }
    }

    private void loopFuse() {
        for (Loop loop: loops) {
            tryFuseLoop(loop);
        }
    }

    private void tryFuseLoop(Loop preLoop) {
        if (!preLoop.isSimpleLoop() || !preLoop.isIdcSet()) {
            return;
        }
        BasicBlock preExit = null;
        for (BasicBlock bb: preLoop.getExits()) {
            preExit = bb;
        }
        if (preExit.getSuccBBs().size() != 1) {
            return;
        }
        if (!preExit.getSuccBBs().get(0).isLoopHeader()) {
            return;
        }
        Loop sucLoop = preExit.getSuccBBs().get(0).getLoop();
        if (!sucLoop.isSimpleLoop() || !sucLoop.isIdcSet()) {
            return;
        }
        if (!preLoop.getIdcInit().equals(sucLoop.getIdcInit()) ||
                !preLoop.getIdcStep().equals(sucLoop.getIdcStep()) ||
                !preLoop.getIdcEnd().equals(sucLoop.getIdcEnd())) {
            return;
        }

        if (preLoop.getNowLevelBB().size() > 2 || sucLoop.getNowLevelBB().size() > 2) {
            return;
        }

        BasicBlock preLatch = null, sucLatch = null, preHead = preLoop.getHeader(), sucHead = sucLoop.getHeader();
        for (BasicBlock bb: preLoop.getLatchs()) {
            preLatch = bb;
        }
        for (BasicBlock bb: sucLoop.getLatchs()) {
            sucLatch = bb;
        }
        HashSet<Instr> preIdcInstrs = new HashSet<>(), sucIdcInstrs = new HashSet<>();
        HashMap<Value, Value> map = new HashMap<>();
        preIdcInstrs.add((Instr) preLoop.getIdcPHI());
        preIdcInstrs.add((Instr) preLoop.getIdcAlu());
        preIdcInstrs.add((Instr) preLoop.getIdcCmp());
        preIdcInstrs.add(preHead.getEndInstr());
        preIdcInstrs.add(preLatch.getEndInstr());

        sucIdcInstrs.add((Instr) sucLoop.getIdcPHI());
        sucIdcInstrs.add((Instr) sucLoop.getIdcAlu());
        sucIdcInstrs.add((Instr) sucLoop.getIdcCmp());
        sucIdcInstrs.add(sucHead.getEndInstr());
        sucIdcInstrs.add(sucLatch.getEndInstr());

        if (!((Instr.Icmp) preLoop.getIdcCmp()).getOp().equals(((Instr.Icmp) sucLoop.getIdcCmp()).getOp())) {
            return;
        }


        for (Instr instr: preIdcInstrs) {
            if (useOutLoop(instr, preLoop)) {
                return;
            }
        }
        for (Instr instr: sucIdcInstrs) {
            if (useOutLoop(instr, sucLoop)) {
                return;
            }
        }

        map.put(preLoop.getIdcPHI(), sucLoop.getIdcPHI());
        map.put(preLoop.getIdcAlu(), sucLoop.getIdcAlu());
        map.put(preLoop.getIdcCmp(), sucLoop.getIdcCmp());
        map.put(preHead, sucHead);
        map.put(preLatch, sucLatch);


        for (Instr instr = preHead.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
            if (!preIdcInstrs.contains(instr)) {
                return;
            }
        }
        for (Instr instr = sucHead.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
            if (!sucIdcInstrs.contains(instr)) {
                return;
            }
        }
        HashSet<Value> preLoad = new HashSet<>(), preStore = new HashSet<>();
        HashSet<Instr> preLoadGep = new HashSet<>(), preStoreGep = new HashSet<>();
        HashSet<Value> sucLoad = new HashSet<>(), sucStore = new HashSet<>();
        HashSet<Instr> sucLoadGep = new HashSet<>(), sucStoreGep = new HashSet<>();
        HashSet<Instr> preLatchInstrs = new HashSet<>();
        HashSet<Instr> sucLatchInstrs = new HashSet<>();
        for (Instr instr = preLatch.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
            preLatchInstrs.add(instr);
        }
        for (Instr instr = sucLatch.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
            sucLatchInstrs.add(instr);
        }

        preLatchInstrs.remove(preLoop.getIdcAlu());
        sucLatchInstrs.remove(sucLoop.getIdcAlu());

        //TODO:检查指令对应,判断读写数组
        for (Instr instr: preLatchInstrs) {
            if (useOutLoop(instr, preLoop)) {
                return;
            }
            if (instr instanceof Instr.Load) {
                Value array = ((Instr.Load) instr).getPointer();
                while (array instanceof Instr.GetElementPtr) {
                    array = ((Instr.GetElementPtr) array).getPtr();
                }
                preLoad.add(array);
            } else if (instr instanceof Instr.Store) {
                Value storeValue = ((Instr.Store) instr).getValue();
                if (storeValue instanceof Instr && !((Instr) storeValue).parentBB().equals(preLatch)) {
                    return;
                }
                Value array = ((Instr.Store) instr).getPointer();
                while (array instanceof Instr.GetElementPtr) {
                    array = ((Instr.GetElementPtr) array).getPtr();
                }
                preStore.add(array);
            } else if (instr instanceof Instr.Alu) {
                Value val1 = ((Instr.Alu) instr).getRVal1();
                Value val2 = ((Instr.Alu) instr).getRVal2();
                if (val1 instanceof Instr && !((Instr) val1).parentBB().equals(preLatch)) {
                    return;
                }
                if (val2 instanceof Instr && !((Instr) val2).parentBB().equals(preLatch)) {
                    return;
                }
            } else if (instr instanceof Instr.GetElementPtr) {
                for (Use use = instr.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
                    if (use.getUser() instanceof Instr.Load) {
                        preLoadGep.add(instr);
                    } else if (use.getUser() instanceof Instr.Store) {
                        preStoreGep.add(instr);
                    } else {
                        return;
                    }
                }
            } else {
                return;
            }
        }
        for (Instr instr: sucLatchInstrs) {
            if (useOutLoop(instr, sucLoop)) {
                return;
            }
            if (instr instanceof Instr.Load) {
                Value array = ((Instr.Load) instr).getPointer();
                while (array instanceof Instr.GetElementPtr) {
                    array = ((Instr.GetElementPtr) array).getPtr();
                }
                sucLoad.add(array);
            } else if (instr instanceof Instr.Store) {
                Value storeValue = ((Instr.Store) instr).getValue();
                if (storeValue instanceof Instr && !((Instr) storeValue).parentBB().equals(sucLatch)) {
                    return;
                }
                Value array = ((Instr.Store) instr).getPointer();
                while (array instanceof Instr.GetElementPtr) {
                    array = ((Instr.GetElementPtr) array).getPtr();
                }
                sucStore.add(array);
            } else if (instr instanceof Instr.Alu) {
                Value val1 = ((Instr.Alu) instr).getRVal1();
                Value val2 = ((Instr.Alu) instr).getRVal2();
                if (val1 instanceof Instr && !((Instr) val1).parentBB().equals(sucLatch)) {
                    return;
                }
                if (val2 instanceof Instr && !((Instr) val2).parentBB().equals(sucLatch)) {
                    return;
                }
            } else if (instr instanceof Instr.GetElementPtr) {

            } else {
                return;
            }
        }

        if (preStore.size() != 1 || sucStore.size() != 1 || preStore.containsAll(sucStore)) {
            return;
        }
        Value preStoreArray = preStore.iterator().next();
        Value sucStoreArray = sucStore.iterator().next();

        if (sucLoad.contains(preStoreArray)) {

        }
    }

    private boolean hasReflectInstr(HashMap<Value, Value> map, Instr instr, HashSet<Instr> instrs) {
        return false;
    }

    private boolean check(Instr A, Instr B, HashMap<Value, Value> map) {
        if (A.tag != B.tag) {
            return false;
        }
        if (A instanceof Instr.Icmp) {
            Instr.Icmp icmpA = (Instr.Icmp) A;
            Instr.Icmp icmpB = (Instr.Icmp) B;
            if (!icmpB.getOp().equals(icmpA.getOp()) ||
                    !icmpB.getRVal1().equals(getReflectValue(icmpA.getRVal1(), map)) ||
                    !icmpB.getRVal2().equals(getReflectValue(icmpA.getRVal2(), map))) {
                return false;
            }
        } else if (A instanceof Instr.Load) {
            Instr.Load loadA = (Instr.Load) A;
            Instr.Load loadB = (Instr.Load) B;
            if (!loadB.getPointer().equals(getReflectValue(loadA.getPointer(), map))) {
                return false;
            }
        } else if (A instanceof Instr.Store) {
            Instr.Store storeA = (Instr.Store) A;
            Instr.Store storeB = (Instr.Store) B;
            if (!storeB.getPointer().equals(getReflectValue(storeA.getPointer(), map)) ||
                    !storeB.getValue().equals(getReflectValue(storeA.getValue(), map))) {
                return false;
            }
        } else if (A instanceof Instr.GetElementPtr) {
            if (A.getUseValueList().size() != 3 || B.getUseValueList().size() != 3) {
                return false;
            }
            Instr.GetElementPtr gepA = (Instr.GetElementPtr) A;
            Instr.GetElementPtr gepB = (Instr.GetElementPtr) B;
            if (!gepB.getUseValueList().get(0).equals(getReflectValue(gepA.getUseValueList().get(0), map)) ||
                    !gepB.getUseValueList().get(1).equals(getReflectValue(gepA.getUseValueList().get(1), map)) ||
                    !gepB.getUseValueList().get(2).equals(getReflectValue(gepA.getUseValueList().get(2), map))) {
                return false;
            }
        } else if (A instanceof Instr.Alu) {
            Instr.Alu aluA = (Instr.Alu) A;
            Instr.Alu aluB = (Instr.Alu) B;
            if (!aluB.getOp().equals(aluA.getOp()) ||
                    !aluB.getRVal1().equals(getReflectValue(aluA.getRVal1(), map)) ||
                    !aluB.getRVal2().equals(getReflectValue(aluA.getRVal2(), map))) {
                return false;
            }
        } else {
            return false;
        }
        //添加映射
        map.put(A, B);
        return true;
    }

    private Value getReflectValue(Value value, HashMap<Value, Value> map) {
        if (!map.containsKey(value)) {
            return value;
        }
        return map.get(value);
    }

    private boolean useOutLoop(Instr instr, Loop loop) {
        for (Use use = instr.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
            Instr user = use.getUser();
            if (!user.parentBB().getLoop().equals(loop)) {
                return true;
            }
        }
        return false;
    }

}
