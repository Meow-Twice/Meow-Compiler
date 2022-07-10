package backend;

import lir.Machine;
import lir.MachineInst;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class TrivialRegAllocator {
    void livenessAnalysis(Machine.McFunction mcFunc) {
        for (Machine.Block mb : mcFunc.mbList) {
            // }
            // for (Machine.Block mb = mcFunc.getBeginMB(); mb != mcFunc.getEndMB(); mb = (Machine.Block) mb.getNext()) {
            //     final Machine.Block mb = mb;
            mb.liveUseSet.clear();
            mb.defSet.clear();
            for(MachineInst mi : mb.miList){
            // for (MachineInst mi = mb.getBeginMI(); mi != mb.getEndMI(); mi = (MachineInst) mi.getNext()) {
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
            for(Machine.Block mb : mcFunc.mbList){
            // for (Machine.Block mb = mcFunc.getBeginMB(); mb != mcFunc.getEndMB(); mb = (Machine.Block) mb.getNext()) {
            //     final Machine.Block finalMb = mb;
                HashSet<Machine.Operand> newLiveOut = new HashSet<>();
                mb.successor.forEach(succMB -> newLiveOut.addAll(succMB.liveInSet));
                if (newLiveOut.size() != mb.liveOutSet.size()) {
                    changed = true;
                } else if (newLiveOut.containsAll(mb.liveOutSet)) {
                    changed = true;
                }
                if (changed) {
                    mb.liveOutSet = newLiveOut;
                    HashSet<Machine.Operand> newLiveIn = mb.liveUseSet;
                    mb.liveOutSet.forEach(out -> {
                        if (mb.defSet.contains(out)) {
                            newLiveIn.add(out);
                        }
                    });
                    mb.liveInSet = newLiveIn;
                }
            }
        }
    }
}
