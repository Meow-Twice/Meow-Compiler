package midend;

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
                !preLoop.getIdcEnd().equals(sucLoop.getIdcEnd())) {
            return;
        }
        if (!preLoop.getIdcStep().equals(sucLoop.getIdcStep())) {
            Value preStep = preLoop.getIdcStep();
            Value sucStep = sucLoop.getIdcStep();
            if (preStep instanceof Constant.ConstantInt && sucStep instanceof Constant.ConstantInt) {
                int preStepVal = (int) ((Constant.ConstantInt) preStep).getConstVal();
                int sucStepVal = (int) ((Constant.ConstantInt) sucStep).getConstVal();
                if (preStepVal != sucStepVal) {
                    return;
                }
            } else {
                return;
            }
        }

        if (preLoop.getNowLevelBB().size() > 2 || sucLoop.getNowLevelBB().size() > 2) {
            return;
        }
        if (preLoop.getEnterings().size() != 1 || sucLoop.getEnterings().size() != 1) {
            return;
        }

        BasicBlock preLatch =  preLoop.getLatchs().iterator().next(),
                sucLatch = sucLoop.getLatchs().iterator().next(),
                preHead = preLoop.getHeader(), sucHead = sucLoop.getHeader(),
                preEntering = preLoop.getEnterings().iterator().next(),
                sucEntering = sucLoop.getEnterings().iterator().next();

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
        if (!preLatch.getEndInstr().getPrev().equals(preLoop.getIdcAlu())) {
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
        HashMap<Value, Instr> preLoadGep = new HashMap<>(), preStoreGep = new HashMap<>();
        HashSet<Value> sucLoad = new HashSet<>(), sucStore = new HashSet<>();
        HashMap<Value, Instr> sucLoadGep = new HashMap<>(), sucStoreGep = new HashMap<>();
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
            if (instr instanceof Instr.Jump) {
                //nothing
            } else if (instr instanceof Instr.Load) {
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
                Value array = ((Instr.GetElementPtr) instr).getPtr();
                while (array instanceof Instr.GetElementPtr) {
                    array = ((Instr.GetElementPtr) array).getPtr();
                }
                for (Use use = instr.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
                    if (use.getUser() instanceof Instr.Load) {
                        if (preLoadGep.containsKey(array)) {
                            return;
                        }
                        preLoadGep.put(array, instr);
                    } else if (use.getUser() instanceof Instr.Store) {
                        if (preStoreGep.containsKey(array)) {
                            return;
                        }
                        preStoreGep.put(array, instr);
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
            if (instr instanceof Instr.Jump) {
                //nothing
            } else if (instr instanceof Instr.Load) {
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
                if (val1 instanceof Instr &&
                        ((Instr) val1).parentBB().getLoop().equals(preLoop)) {
                    return;
                }
                if (val2 instanceof Instr &&
                        ((Instr) val2).parentBB().getLoop().equals(preLoop)) {
                    return;
                }
            } else if (instr instanceof Instr.GetElementPtr) {
                Value array = ((Instr.GetElementPtr) instr).getPtr();
                while (array instanceof Instr.GetElementPtr) {
                    array = ((Instr.GetElementPtr) array).getPtr();
                }
                for (Use use = instr.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
                    if (use.getUser() instanceof Instr.Load) {
                        if (sucLoadGep.containsKey(array)) {
                            return;
                        }
                        sucLoadGep.put(array, instr);
                    } else if (use.getUser() instanceof Instr.Store) {
                        if (sucStoreGep.containsKey(array)) {
                            return;
                        }
                        sucStoreGep.put(array, instr);
                    } else {
                        return;
                    }
                }
            } else {
                return;
            }
        }

        //!preLoad.equals(sucLoad)这个条件并不需要?
        if (preStore.size() != 1 || sucStore.size() != 1 || !preStore.equals(sucStore)) {
            return;
        }
        Value preStoreArray = preStore.iterator().next();
        Value sucStoreArray = sucStore.iterator().next();
        Value preStoreIndex = preStoreGep.get(preStoreArray);
        Value sucStoreIndex = sucStoreGep.get(sucStoreArray);

        //fixme:仔细考虑并不是一个数组的时候
        if (!preStoreArray.equals(sucStoreArray)) {
            return;
        }

        if (preLoadGep.containsKey(preStoreArray) && !preStoreIndex.equals(preLoadGep.get(preStoreArray))) {
            return;
        }

        if (sucLoadGep.containsKey(sucStoreArray) && !sucStoreIndex.equals(sucLoadGep.get(sucStoreArray))) {
            return;
        }
        boolean change = true;
        while (change) {
            change = false;
            int size = map.size();
            for (Instr instr: preLatchInstrs) {
                hasReflectInstr(map, instr, sucLatchInstrs);
            }
            if (map.size() > size) {
                change = true;
            }
        }



        //sucEntering 没有对preStoreArray 的读写
        for (Instr instr = sucEntering.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
            if (instr instanceof Instr.Load) {
                Value array = ((Instr.Load) instr).getPointer();
                while (array instanceof Instr.GetElementPtr) {
                    array = ((Instr.GetElementPtr) array).getPtr();
                }
                if (array.equals(preStoreArray)) {
                    return;
                }
            } else if (instr instanceof Instr.Store) {
                Value array = ((Instr.Store) instr).getPointer();
                while (array instanceof Instr.GetElementPtr) {
                    array = ((Instr.GetElementPtr) array).getPtr();
                }
                if (array.equals(preStoreArray)) {
                    return;
                }
            }
        }


        if (sucLoad.contains(preStoreArray)) {
            if (!getReflectValue(preStoreIndex, map).equals(sucLoadGep.get(preStoreArray))) {
                return;
            }
        }

        ArrayList<Instr> temp = new ArrayList<>();
        for (Instr instr = sucEntering.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
            if (!(instr instanceof Instr.Branch) && !(instr instanceof Instr.Jump)) {
                temp.add(instr);
            }
        }
        for (int i = 0; i < temp.size(); i++) {
            Instr instr = temp.get(i);
            instr.delFromNowBB();
            preEntering.getEndInstr().insertBefore(instr);
            instr.setBb(preEntering);
        }
        temp.clear();
        for (Instr instr = sucLatch.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
            if (!(instr instanceof Instr.Branch) && !(instr instanceof Instr.Jump) && !instr.equals(sucLoop.getIdcAlu())) {
                temp.add(instr);
            }
        }
        for (int i = 0; i < temp.size(); i++) {
            Instr instr = temp.get(i);
            instr.delFromNowBB();
            preLatch.getEndInstr().getPrev().insertBefore(instr);
            instr.setBb(preLatch);

            for (int index = 0; index < instr.getUseValueList().size(); index++) {
                Value value = instr.getUseValueList().get(index);
                if (value.equals(sucLoop.getIdcPHI())) {
                    instr.modifyUse(preLoop.getIdcPHI(), index);
                }
            }
        }
    }

    private boolean hasReflectInstr(HashMap<Value, Value> map, Instr instr, HashSet<Instr> instrs) {
        for (Instr instr1: instrs) {
            if (check(instr, instr1, map)) {
                return true;
            }
        }
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
