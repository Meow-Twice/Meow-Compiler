package midEnd;

import ir.*;
import ir.type.Type;

import java.util.ArrayList;
import java.util.HashSet;

//插phi并重命名
public class Mem2Reg {

    private ArrayList<Function> functions;

    public Mem2Reg(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run() {
        removeAlloc();

    }

    public void removeAlloc() {
        for (Function function: functions) {
            removeFuncAlloc(function);
        }
    }

    public void removeFuncAlloc(Function function) {
        for (BasicBlock bb: function.getBBs()) {
            removeBBAlloc(bb);
        }
    }

    public void removeBBAlloc(BasicBlock basicBlock) {
        Instr temp = basicBlock.getBeginInstr();
        while (!temp.isEnd()) {
            if (temp instanceof Instr.Alloc && !((Instr.Alloc) temp).isArrayAlloc()) {
                remove(temp);
            }
        }
    }

    public void remove(Instr instr) {
        HashSet<BasicBlock> defBBs = new HashSet<>();
        HashSet<BasicBlock> useBBs = new HashSet<>();
        HashSet<Instr> defInstrs = new HashSet<>();
        HashSet<Instr> useInstrs = new HashSet<>();
        Use pos = instr.getBeginUse();
        while (pos.getNext() != null) {
            Instr userInstr = pos.getUser();
            if (userInstr instanceof Instr.Store) {
                defBBs.add(userInstr.parentBB());
                defInstrs.add(userInstr);
            } else if (userInstr instanceof Instr.Load) {
                useBBs.add(userInstr.parentBB());
                useInstrs.add(userInstr);
            } else {
                System.err.println("remove alloc error,this alloc hava been used in pointer instr");
            }
            pos = (Use) pos.getNext();
        }

        if (useBBs.isEmpty()) {
            for (Instr temp: defInstrs) {
                temp.remove();
            }
        } else if (defBBs.size() == 1) {
            if (defInstrs.size() == 1) {
                Instr def = null;
                for (Instr temp: defInstrs) {
                    def = temp;
                }
                for (Instr use: useInstrs) {
                    use.modifyUsedToA(((Instr.Store) def).getValue());
                }
            } else {
                BasicBlock defBB = null;
                for (BasicBlock bb: defBBs) {
                    defBB = bb;
                }

                Instr reachDef = null;
                Instr BB_pos = defBB.getBeginInstr();
                while (BB_pos.getNext() != null) {
                    if (defInstrs.contains(BB_pos)) {
                        reachDef = BB_pos;
                    } else if (useInstrs.contains(BB_pos)) {
                        BB_pos.modifyUsedToA(reachDef);
                    }
                }

                //TODO:对于未定义的使用,是否不必要进行定义
                for (Instr userInstr: useInstrs) {
                    if (!userInstr.parentBB().equals(defBB)) {
                        userInstr.modifyUsedToA(reachDef);
                    }
                }
            }
            for (Instr instr1: defInstrs) {
                instr1.remove();
            }
            for (Instr instr1: useInstrs) {
                instr1.remove();
            }
        } else {
            //TODO:多个块store 此Alloc指令申请的空间

        }



        //
        instr.remove();
    }




}
