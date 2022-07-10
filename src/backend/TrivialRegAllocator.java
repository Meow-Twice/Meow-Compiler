package backend;

import lir.Machine;
import lir.MachineInst;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class TrivialRegAllocator {
    void livenessAnalysis(Machine.McFunction mcFunc) {
        ArrayList<Machine.Block> bfsMbList = new ArrayList<>();
        for (Machine.Block mb : mcFunc.mbList) {
            mb.liveUseSet.clear();
            mb.defSet.clear();
            for (MachineInst mi : mb.miList) {
                ArrayList<Machine.Operand> defs = mi.defOpds;
                ArrayList<Machine.Operand> uses = mi.useOpds;
                // liveuse 计算
                uses.forEach(use -> {
                    if (!use.isImm() && mb.defSet.contains(use)) {
                        mb.liveUseSet.add(use);
                    }
                });
                // def 计算
                defs.forEach(def -> {
                    if (!def.isImm() && mb.liveUseSet.contains(def)) {
                        mb.defSet.add(def);
                    }
                });
            }
            mb.liveInSet = mb.liveUseSet;
            mb.liveOutSet.clear();

        }

        // 计算LiveIn和LiveOut
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Machine.Block mb : mcFunc.mbList) {
                // 任意succ的liveInSet如果有更新, 则可能更新 (只可能增加, 增量为newLiveOut) 当前MB的liveIn,
                // 且当前MB如果需要更新liveIn, 只可能新增且新增的Opd一定出自newLiveOut
                HashSet<Machine.Operand> newLiveOut = new HashSet<>();
                for (Machine.Block succMB : mb.successor) {
                    for (Machine.Operand liveIn : succMB.liveInSet) {
                        if (mb.liveOutSet.add(liveIn)) {
                            newLiveOut.add(liveIn);
                        }
                    }
                }
                changed = newLiveOut.size() > 0;
                newLiveOut.forEach(newOut -> {
                    if (!mb.defSet.contains(newOut)) {
                        mb.liveInSet.add(newOut);
                    }
                });
            }
        }
    }

    public void AllocateRegister(Machine.Program program){
        for(Machine.McFunction mcFunc: program.funcList){
            for(Machine.Block mb: mcFunc.mbList){

            }
        }
    }
}
