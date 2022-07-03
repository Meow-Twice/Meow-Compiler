package midend;

import mir.*;
import mir.type.Type;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Stack;

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
            temp = (Instr) temp.getNext();
        }
    }

    public void remove(Instr instr) {
        //def 和 use 是指 定义/使用了 alloc出的那块内存地址
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
                    use.modifyAllUseThisToUseA(((Instr.Store) def).getValue());
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
                        BB_pos.modifyAllUseThisToUseA(reachDef);
                    }
                    BB_pos = (Instr) BB_pos.getNext();
                }

                //TODO:对于未定义的使用,是否不必要进行定义,当前实现方法为所有其他BB的use认为使用了唯一的reachDef
                for (Instr userInstr: useInstrs) {
                    if (!userInstr.parentBB().equals(defBB)) {
                        userInstr.modifyAllUseThisToUseA(reachDef);
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
            HashSet<BasicBlock> F = new HashSet<>();
            HashSet<BasicBlock> W = new HashSet<>();

            W.addAll(defBBs);

            while (!W.isEmpty()) {
                BasicBlock X = getRandFromHashSet(W);
                W.remove(X);
                for (BasicBlock Y: X.getDF()) {
                    if (!F.contains(Y)) {
                        F.add(Y);
                        if (!defBBs.contains(Y)) {
                            W.add(Y);
                        }
                    }
                }
            }

            for (BasicBlock bb: F) {
                //TODO:添加phi指令
                //new phi
                //useInstrs.add(phi);
                //defInstrs.add(phi);
                //bb.insertAtEnd();
                assert instr.getType() instanceof Type.PointerType;
                Instr PHI = null;
                ArrayList<Value> optionalValues = new ArrayList<>();
                for (int i = 0; i < bb.getPrecBBs().size(); i++) {
                    //空指令
                    optionalValues.add(new Instr(((Type.PointerType) instr.getType()).getInnerType(), bb));
                }
                if (((Type.PointerType) instr.getType()).getInnerType().isFloatType()) {
                    PHI = new Instr.Phi(Type.BasicType.getF32Type(), optionalValues, bb);
                } else {
                    PHI = new Instr.Phi(Type.BasicType.getI32Type(), optionalValues, bb);
                }
                useInstrs.add(PHI);
                defInstrs.add(PHI);
                //bb.insertAtHead(PHI);
            }

            //TODO:Rename
            Stack<Value> S = new Stack<>();
            RenameDFS(S, instr.parentBB().getFunction().getBeginBB(), useInstrs, defInstrs);
        }



        //
        instr.remove();
    }

    public void RenameDFS(Stack<Value> S, BasicBlock X, HashSet<Instr> useInstrs, HashSet<Instr> defInstrs) {
        int cnt = 0;
        Instr A = X.getBeginInstr();
        while (A.getNext() != null) {
            if (!(A instanceof Instr.Phi) && useInstrs.contains(A)) {
                assert A instanceof Instr.Load;
                A.modifyAllUseThisToUseA(S.peek());
            }
            if (defInstrs.contains(A)) {
                assert A instanceof Instr.Store || A instanceof Instr.Phi;
                if (A instanceof Instr.Store) {
                    S.push(((Instr.Store) A).getValue());
                    A.remove();
                } else {
                    S.push(A);
                }
                cnt++;
            }
            A = (Instr) A.getNext();
        }
        ArrayList<BasicBlock> Succ = X.getSuccBBs();
        for (int i = 0; i < Succ.size(); i++) {
            BasicBlock bb = Succ.get(i);
            Instr instr = Succ.get(i).getBeginInstr();
            while (instr.getNext() != null) {
                if (!(instr instanceof Instr.Phi)) {
                    break;
                }
                if (useInstrs.contains(instr)) {
                    instr.modifyUse(S.peek(), bb.getPrecBBs().indexOf(X));
                }
                instr = (Instr) instr.getNext();
            }
        }

        for (BasicBlock next: X.getIdoms()) {
            RenameDFS(S, next, useInstrs, defInstrs);
        }

        for (int i = 0; i < cnt; i++) {
            S.pop();
        }
    }

    public BasicBlock getRandFromHashSet(HashSet<BasicBlock> hashSet) {
        for (BasicBlock bb: hashSet) {
            return bb;
        }
        return null;
    }


}
