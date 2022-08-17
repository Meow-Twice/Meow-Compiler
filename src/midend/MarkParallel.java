package midend;

import manage.Manager;
import mir.*;
import mir.type.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class MarkParallel {

    private ArrayList<Function> functions;
    private static int parallel_num = 4;
    HashSet<BasicBlock> know = new HashSet<>();


    public MarkParallel(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        for (Function function: functions) {
            know.clear();
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                if (!know.contains(bb) && bb.isLoopHeader()) {
                    //markLoop(bb.getLoop());
                    markLoopDebug(bb.getLoop());
                }
            }
        }
    }

    private boolean isPureLoop(Loop loop) {
        if (!loop.hasChildLoop()) {
            return true;
        }
        if (loop.getChildrenLoops().size() > 1) {
            return false;
        }
        if (!loop.isSimpleLoop() || !loop.isIdcSet()) {
            return false;
        }
        return isPureLoop(loop.getChildrenLoops().iterator().next());
    }

    private void markLoop(Loop loop) {
        if (loop.getHash() != 7) {
            return;
        }
        if (!isPureLoop(loop)) {
            return;
        }
        HashSet<BasicBlock> bbs = new HashSet<>();
        HashSet<Value> idcVars = new HashSet<>();
        HashSet<Loop> loops = new HashSet<>();
        DFS(bbs, idcVars, loops, loop);

        for (Loop temp: loops) {
            for (Instr instr = temp.getHeader().getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                if (instr instanceof Instr.Phi && !instr.equals(temp.getIdcPHI())) {
                    return;
                }
            }
        }

        HashSet<Value> load = new HashSet<>(), store = new HashSet<>();
        HashMap<Value, Instr> loadGep = new HashMap<>(), storeGep = new HashMap<>();

        for (BasicBlock bb: bbs) {
            for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                if (instr instanceof Instr.Call) {
                    return;
                }
                if (useOutLoops(instr, loops)) {
                    return;
                }
                //trivel写法
                if (instr instanceof Instr.GetElementPtr) {
                    if (!(((Instr.GetElementPtr) instr).getPtr() instanceof GlobalVal.GlobalValue)) {
                        return;
                    }
                    for (Value idc: ((Instr.GetElementPtr) instr).getIdxList()) {
                        if (idc instanceof Constant && (int) ((Constant) idc).getConstVal() == 0) {
                            continue;
                        }
                        if (!idcVars.contains(idc)) {
                            return;
                        }
                    }
                }
//                if (instr instanceof Instr.Store) {
//                    Value array = ((Instr.Store) instr).getPointer();
//                    while (array instanceof Instr.GetElementPtr) {
//                        array = ((Instr.GetElementPtr) array).getPtr();
//                    }
//                    store.add(array);
//
//                } else if (instr instanceof Instr.Load) {
//                    Value array = ((Instr.Load) instr).getPointer();
//                    while (array instanceof Instr.GetElementPtr) {
//                        array = ((Instr.GetElementPtr) array).getPtr();
//                    }
//                    load.add(array);
//
//                }
            }
        }

//        if (store.size() > 1) {
//            return;
//        }
//
//        if (store.size() == 1) {
//            Value storeArray = store.iterator().next();
//            if (load.contains(storeArray)) {
//                for (BasicBlock bb : bbs) {
//                    for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
//                        if (instr instanceof Instr.GetElementPtr) {
//                            Value array = ((Instr.GetElementPtr) instr).getPtr();
//                            while (array instanceof Instr.GetElementPtr) {
//                                array = ((Instr.GetElementPtr) array).getPtr();
//                            }
//                            if (array.equals(storeArray)) {
//                                for (Use use = instr.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
//                                    if (use.getUser() instanceof Instr.Load) {
//                                        if (loadGep.containsKey(array)) {
//                                            return;
//                                        }
//                                        loadGep.put(array, instr);
//                                    } else if (use.getUser() instanceof Instr.Store) {
//                                        if (storeGep.containsKey(array)) {
//                                            return;
//                                        }
//                                        storeGep.put(array, instr);
//                                    } else {
//                                        return;
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
//                if (!storeGep.get(storeArray).equals(loadGep.get(storeArray))) {
//                    return;
//                }
//            }
//        }

        Value idcEnd = loop.getIdcEnd();
        if (idcEnd instanceof Constant) {
            return;
        }
        if (loop.getEnterings().size() > 1) {
            return;
        }
        BasicBlock entering = loop.getEnterings().iterator().next();
        BasicBlock head = loop.getHeader();
        BasicBlock latch = loop.getLatchs().iterator().next();
        BasicBlock exiting = loop.getExitings().iterator().next();
        BasicBlock exit = loop.getExits().iterator().next();
        int index = 1 - head.getPrecBBs().indexOf(latch);
        Instr.Phi idcPhi = (Instr.Phi) loop.getIdcPHI();
        Instr.Icmp cmp = (Instr.Icmp) loop.getIdcCmp();
        Function func = loop.getHeader().getFunction();
        BasicBlock parallelStartBB = new BasicBlock(func, loop.getParentLoop());
        BasicBlock parallelEndBB = new BasicBlock(func, loop.getParentLoop());

        if (!cmp.getOp().equals(Instr.Icmp.Op.SLT)) {
            return;
        }

        //start BB
        Instr.Call startCall = new Instr.Call(Manager.ExternFunction.PARALLEL_START, new ArrayList<>(), parallelStartBB);
        Instr.Alu mul_1 = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.MUL, startCall, idcEnd, parallelStartBB);
        Instr.Alu div_1 = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.DIV, mul_1, new Constant.ConstantInt(parallel_num), parallelStartBB);
        idcPhi.modifyUse(div_1, index);
        Instr.Alu add_1 = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.ADD, startCall, new Constant.ConstantInt(1), parallelStartBB);
        Instr.Alu mul_2 = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.MUL, add_1, idcEnd, parallelStartBB);
        Instr.Alu div_2 = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.DIV, mul_2, new Constant.ConstantInt(parallel_num), parallelStartBB);
        cmp.modifyUse(div_2, 1);
        Instr.Jump jump_1 = new Instr.Jump(head, parallelStartBB);

        entering.modifyBrAToB(head, parallelStartBB);
        entering.modifySuc(head, parallelStartBB);
        parallelStartBB.addPre(entering);
        parallelStartBB.addSuc(head);
        head.modifyPre(entering, parallelStartBB);


        //end BB
        ArrayList<Value> args = new ArrayList<>();
        args.add(startCall);
        Instr.Call endCall = new Instr.Call(Manager.ExternFunction.PARALLEL_END, args, parallelEndBB);
        Instr.Jump jump_2 = new Instr.Jump(exit, parallelEndBB);


        exiting.modifyBrAToB(exit, parallelEndBB);
        exiting.modifySuc(exit, parallelEndBB);
        parallelEndBB.addPre(exiting);
        parallelEndBB.addSuc(exit);
        exit.modifyPre(exiting, parallelEndBB);

        know.addAll(bbs);
    }

    private void DFS(HashSet<BasicBlock> bbs, HashSet<Value>  idcVars, HashSet<Loop> loops, Loop loop) {
        loops.add(loop);
        bbs.addAll(loop.getNowLevelBB());
        idcVars.add(loop.getIdcPHI());
        for (Loop next: loop.getChildrenLoops()) {
            DFS(bbs, idcVars, loops, next);
        }
    }

    private boolean useOutLoops(Value value, HashSet<Loop> loops) {
        for (Use use = value.getBeginUse(); use.getNext() != null; use = (Use) use.getNext()) {
            Instr user = use.getUser();
            if (!loops.contains(user.parentBB().getLoop())) {
                return true;
            }
        }
        return false;
    }


    private void markLoopDebug(Loop loop) {
        if (loop.getHash() != 8) {
            //
            return;
        }

        HashSet<BasicBlock> bbs = new HashSet<>();
        HashSet<Value> idcVars = new HashSet<>();
        HashSet<Loop> loops = new HashSet<>();
        DFS(bbs, idcVars, loops, loop);

        Value idcEnd = loop.getIdcEnd();
        if (idcEnd instanceof Constant) {
            return;
        }
        if (loop.getEnterings().size() > 1) {
            return;
        }
        BasicBlock entering = loop.getEnterings().iterator().next();
        BasicBlock head = loop.getHeader();
        BasicBlock latch = loop.getLatchs().iterator().next();
        BasicBlock exiting = loop.getExitings().iterator().next();
        BasicBlock exit = loop.getExits().iterator().next();
        int index = 1 - head.getPrecBBs().indexOf(latch);
        Instr.Phi idcPhi = (Instr.Phi) loop.getIdcPHI();
        Instr.Icmp cmp = (Instr.Icmp) loop.getIdcCmp();
        Function func = loop.getHeader().getFunction();
        BasicBlock parallelStartBB = new BasicBlock(func, loop.getParentLoop());
        BasicBlock parallelEndBB = new BasicBlock(func, loop.getParentLoop());

        if (!cmp.getOp().equals(Instr.Icmp.Op.SLT)) {
            return;
        }

        //start BB
        Instr.Call startCall = new Instr.Call(Manager.ExternFunction.PARALLEL_START, new ArrayList<>(), parallelStartBB);
        Instr.Alu mul_1 = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.MUL, startCall, idcEnd, parallelStartBB);
        Instr.Alu div_1 = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.DIV, mul_1, new Constant.ConstantInt(parallel_num), parallelStartBB);
        idcPhi.modifyUse(div_1, index);
        Instr.Alu add_1 = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.ADD, startCall, new Constant.ConstantInt(1), parallelStartBB);
        Instr.Alu mul_2 = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.MUL, add_1, idcEnd, parallelStartBB);
        Instr.Alu div_2 = new Instr.Alu(Type.BasicType.getI32Type(), Instr.Alu.Op.DIV, mul_2, new Constant.ConstantInt(parallel_num), parallelStartBB);
        cmp.modifyUse(div_2, 1);
        Instr.Jump jump_1 = new Instr.Jump(head, parallelStartBB);

        entering.modifyBrAToB(head, parallelStartBB);
        entering.modifySuc(head, parallelStartBB);
        parallelStartBB.addPre(entering);
        parallelStartBB.addSuc(head);
        head.modifyPre(entering, parallelStartBB);


        //end BB
        ArrayList<Value> args = new ArrayList<>();
        args.add(startCall);
        Instr.Call endCall = new Instr.Call(Manager.ExternFunction.PARALLEL_END, args, parallelEndBB);
        Instr.Jump jump_2 = new Instr.Jump(exit, parallelEndBB);


        exiting.modifyBrAToB(exit, parallelEndBB);
        exiting.modifySuc(exit, parallelEndBB);
        parallelEndBB.addPre(exiting);
        parallelEndBB.addSuc(exit);
        exit.modifyPre(exiting, parallelEndBB);

        know.addAll(bbs);
    }

}
