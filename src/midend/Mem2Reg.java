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
        Type InnerType = ((Instr.Alloc) instr).getContentType();
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
        } else if (defBBs.size() == 1 && check(defBBs, useBBs)) {
            //fixme:本来我认为如果只有一个定义指令 就可以直接进行替换,即如下代码
            //          但是 实际上这样替换的前提还需要定义指令支配所有使用指令,
            //          当存在未定义的使用的时候会直接生成不符合llvm的语法,所以直接删除这一分支
            //if (defInstrs.size() == 1) {
            //                Instr def = null;
            //                for (Instr temp: defInstrs) {
            //                    def = temp;
            //                }
            //                for (Instr use: useInstrs) {
            //                    use.modifyAllUseThisToUseA(((Instr.Store) def).getValue());
            //                }
            //            }

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
                    if (reachDef == null) {
                        BB_pos.modifyAllUseThisToUseA(new GlobalVal.UndefValue(InnerType));
                    } else {
                        BB_pos.modifyAllUseThisToUseA(((Instr.Store) reachDef).getValue());
                    }
                }
                BB_pos = (Instr) BB_pos.getNext();
            }

            //TODO:对于未定义的使用,是否不必要进行定义,当前实现方法为所有其他BB的use认为使用了唯一的reachDef
            for (Instr userInstr: useInstrs) {
                if (!userInstr.parentBB().equals(defBB)) {
                    assert reachDef != null;
                    userInstr.modifyAllUseThisToUseA(((Instr.Store) reachDef).getValue());
                }
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
                    optionalValues.add(new Instr());
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
            Type type = ((Type.PointerType) instr.getType()).getInnerType();
            RenameDFS(S, instr.parentBB().getFunction().getBeginBB(), useInstrs, defInstrs, type);
        }



        //
        instr.remove();
        if (!useBBs.isEmpty()) {
            for (Instr instr1 : defInstrs) {
                if (!(instr1 instanceof Instr.Phi)) {
                    instr1.remove();
                }
            }
            for (Instr instr1 : useInstrs) {
                if (!(instr1 instanceof Instr.Phi)) {
                    instr1.remove();
                }
            }
        }
    }

    private boolean check(HashSet<BasicBlock> defBBs, HashSet<BasicBlock> useBBs) {
        BasicBlock defBB = null;
        assert defBBs.size() == 1;
        for (BasicBlock bb: defBBs) {
            defBB = bb;
        }
        for (BasicBlock bb: useBBs) {
            if (!defBB.getDoms().contains(bb)) {
                return false;
            }
        }
        return true;
    }

    public void RenameDFS(Stack<Value> S, BasicBlock X, HashSet<Instr> useInstrs, HashSet<Instr> defInstrs, Type type) {
        int cnt = 0;
        Instr A = X.getBeginInstr();
        while (A.getNext() != null) {
            if (!(A instanceof Instr.Phi) && useInstrs.contains(A)) {
                assert A instanceof Instr.Load;
                //A.modifyAllUseThisToUseA(S.peek());
                A.modifyAllUseThisToUseA(getStackTopValue(S, type));
            }
            if (defInstrs.contains(A)) {
                assert A instanceof Instr.Store || A instanceof Instr.Phi;
                if (A instanceof Instr.Store) {
                    S.push(((Instr.Store) A).getValue());
                    //A.remove();
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
                    //instr.modifyUse(S.peek(), bb.getPrecBBs().indexOf(X));
                    instr.modifyUse(getStackTopValue(S, type), bb.getPrecBBs().indexOf(X));

                    //instr.remove();
                }
                instr = (Instr) instr.getNext();
            }
        }

        for (BasicBlock next: X.getIdoms()) {
            RenameDFS(S, next, useInstrs, defInstrs, type);
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

    public Value getStackTopValue(Stack<Value> S, Type type) {
        if (S.empty()) {
//            if (type.isFloatType()) {
//                return new Constant.ConstantFloat(0);
//            } else {
//                return new Constant.ConstantInt(0);
//            }
            return new GlobalVal.UndefValue();
        }
        return S.peek();
    }


}
