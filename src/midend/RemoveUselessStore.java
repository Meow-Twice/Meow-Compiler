package midend;

import mir.BasicBlock;
import mir.Function;
import mir.Instr;
import mir.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class RemoveUselessStore {

    //如果想跨基本快做,需要当一个store所有的idoms都没有在下一次store前use的时候,才能删除
    private ArrayList<Function> functions;

    public RemoveUselessStore(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        HashSet<Instr> removes = new HashSet<>();
        HashMap<Value, Instr> storeAddress = new HashMap<>();
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                storeAddress.clear();
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (instr instanceof Instr.Store) {
                        if (storeAddress.containsKey(((Instr.Store) instr).getPointer())) {
                            removes.add(storeAddress.get(((Instr.Store) instr).getPointer()));
                        }
                        storeAddress.put(((Instr.Store) instr).getPointer(), instr);
                    } else if (!(instr instanceof Instr.Alu)) {
                        storeAddress.clear();
                    }
                }
            }
        }
        for (Instr instr: removes) {
            instr.remove();
        }
    }
}
