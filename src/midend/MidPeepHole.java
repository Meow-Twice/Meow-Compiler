package midend;

import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class MidPeepHole {

    private ArrayList<Function> functions;

    public MidPeepHole(ArrayList<Function> functions) {
        this.functions = functions;
    }

    public void Run(){
        // time = b - 1; add = init + base; mul = time * base; ret = add + mul
        // mul = b * base; ret = add + mul;
        peepHoleA();
        // A = load ptr_A, store A ptr_B, store A ptr_A
        // A = load ptr_A, store A ptr_B
        peepHoleB();
        //
        peepHoleC();
    }

    private void peepHoleA() {
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {

                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (instr instanceof Instr.Alu && ((Instr.Alu) instr).getOp().equals(Instr.Alu.Op.ADD)) {
                        Instr add_A = instr;
                        Instr mul = (Instr) instr.getNext();
                        Instr add_B = (Instr) mul.getNext();
                        if (mul instanceof Instr.Alu && ((Instr.Alu) mul).getOp().equals(Instr.Alu.Op.MUL) &&
                                add_B instanceof Instr.Alu && ((Instr.Alu) add_B).getOp().equals(Instr.Alu.Op.ADD)) {
                            Value A = ((Instr.Alu) add_A).getRVal1();
                            Value B = ((Instr.Alu) add_A).getRVal2();
                            Value C = ((Instr.Alu) mul).getRVal1();
                            Value D = ((Instr.Alu) mul).getRVal2();
                            Value E = ((Instr.Alu) add_B).getRVal1();
                            Value F = ((Instr.Alu) add_B).getRVal2();

                            Value init, time, base;
                            int time_index;
                            if (((E.equals(add_A) && F.equals(mul))) || ((F.equals(add_A) && E.equals(mul)))) {
                                if (A.equals(C)) {
                                    base = A;
                                    init = B;
                                    time = D;
                                    time_index = 1;
                                } else if (A.equals(D)) {
                                    base = A;
                                    init = B;
                                    time = C;
                                    time_index = 0;
                                } else if (B.equals(C)) {
                                    base = B;
                                    init = A;
                                    time = D;
                                    time_index = 1;
                                } else if (B.equals(D)) {
                                    base = B;
                                    init = A;
                                    time = C;
                                    time_index = 0;
                                } else {
                                    continue;
                                }

                                if (!(time instanceof Instr.Alu) || !((Instr.Alu) time).getOp().equals(Instr.Alu.Op.SUB)) {
                                    continue;
                                }
                                Value val = ((Instr.Alu) time).getRVal2();
                                if (!(val instanceof Constant.ConstantInt) || ((int) ((Constant.ConstantInt) val).getConstVal()) != 1) {
                                    return;
                                }
                                if (!time.onlyOneUser()) {
                                    return;
                                }
                                mul.modifyUse(((Instr.Alu) time).getRVal1(), time_index);
                                int init_index = (F.equals(mul))? 0:1;
                                add_B.modifyUse(init, init_index);
                                instr = add_B;
                                add_A.remove();
                                time.remove();
                            }
                        }
                    }
                }
            }
        }
    }

    private void peepHoleB() {
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                ArrayList<Instr> instrs = new ArrayList<>();
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (instr instanceof Instr.Load || instr instanceof Instr.Store) {
                        instrs.add(instr);
                    }
                }
                if (instrs.size() != 3 || !(instrs.get(0) instanceof Instr.Load) ||
                        !(instrs.get(1) instanceof Instr.Store) || !(instrs.get(2) instanceof Instr.Store)) {
                    continue;
                }
                Instr.Load load = (Instr.Load) instrs.get(0);
                Instr.Store storeA = (Instr.Store) instrs.get(1);
                Instr.Store storeB = (Instr.Store) instrs.get(2);

                if (!storeA.getValue().equals(load) || !storeB.getValue().equals(load) ||
                        !storeB.getPointer().equals(load.getPointer())) {
                    continue;
                }
                storeB.remove();
            }
        }
    }

    private HashSet<BasicBlock> bb_has_store_call = new HashSet<>();
    private BasicBlock E = null, F = null;

    private void peepHoleC() {
        peepHoleCInit();
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                checkC(bb);
            }
        }
    }

    private void peepHoleCInit() {
        for (Function function: functions) {
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                for (Instr instr = bb.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
                    if (instr instanceof Instr.Store || instr instanceof Instr.Call) {
                        bb_has_store_call.add(bb);
                        break;
                    }
                }
            }
        }
    }

    private void checkC(BasicBlock A) {
        if (A.getSuccBBs().size() != 2) {
            return;
        }
        BasicBlock B = null, C = null;
        if (A.getSuccBBs().get(0).getSuccBBs().size() == 1) {
            C = A.getSuccBBs().get(0);
            B = A.getSuccBBs().get(1);
        } else if (A.getSuccBBs().get(1).getSuccBBs().size() == 1) {
            B = A.getSuccBBs().get(0);
            C = A.getSuccBBs().get(1);
        } else {
            return;
        }

        if (B.getPrecBBs().size() != 1 || B.getSuccBBs().size() != 2) {
            return;
        }

        if (bb_has_store_call.contains(A) || bb_has_store_call.contains(B)) {
            return;
        }

        BasicBlock B_left = B.getSuccBBs().get(0), B_right = B.getSuccBBs().get(1);
        BasicBlock D = null, G = null;
        if (patternC(B_left, B_right)) {
            D = B_left;
            G = B_right;
        } else if (patternC(B_right, B_left)) {
            D = B_right;
            G = B_left;
        } else {
            return;
        }

        HashMap<String, Instr> gvn = new HashMap<>();
        for (Instr instr = A.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
            if (instr instanceof Instr.Load) {
                String hash = "load " + ((Instr.Load) instr).getPointer().getName();
                gvn.put(hash, instr);
            }
        }

        for (Instr instr = E.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
            if (instr instanceof Instr.Load) {
                String hash = "load " + ((Instr.Load) instr).getPointer().getName();
                if (gvn.containsKey(hash)) {
                    instr.modifyAllUseThisToUseA(gvn.get(hash));
                }
            }
        }

        for (Instr instr = G.getBeginInstr(); instr.getNext() != null; instr = (Instr) instr.getNext()) {
            if (instr instanceof Instr.Load) {
                String hash = "load " + ((Instr.Load) instr).getPointer().getName();
                if (gvn.containsKey(hash)) {
                    instr.modifyAllUseThisToUseA(gvn.get(hash));
                }
            }
        }
    }

    private boolean patternC(BasicBlock D, BasicBlock G) {
        if (D.getSuccBBs().size() != 2) {
            return false;
        }
        if (D.getPrecBBs().size() != 1) {
            return false;
        }
        if (G.getPrecBBs().size() != 2) {
            return false;
        }
        BasicBlock D_left = D.getSuccBBs().get(0), D_right = D.getSuccBBs().get(1);
        if (D_right.getPrecBBs().size() == 2 && D_right.getSuccBBs().size() == 1 &&
                D_left.getPrecBBs().size() == 1 && D_left.getSuccBBs().size() == 1 &&
                D_left.getSuccBBs().get(0).equals(D_right)) {
            E = D_left;
            F = D_right;
        } else if (D_left.getPrecBBs().size() == 2 && D_left.getSuccBBs().size() == 1 &&
                D_right.getPrecBBs().size() == 1 && D_right.getSuccBBs().size() == 1 &&
                D_right.getSuccBBs().get(0).equals(D_left)) {
            E = D_right;
            F = D_left;
        } else {
            return false;
        }

        if (!F.getSuccBBs().get(0).equals(G)) {
            return false;
        }



        if (bb_has_store_call.contains(D) || bb_has_store_call.contains(E) ||
                bb_has_store_call.contains(F) || bb_has_store_call.contains(G)) {
            return false;
        }

        return true;
    }

}
