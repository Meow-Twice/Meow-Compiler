package midend;

import mir.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Pattern;

public class IfComb {

    private HashSet<BasicBlock> know = new HashSet<>();
    private HashMap<Value, Integer> left = new HashMap<>();
    private HashMap<Value, Integer> right = new HashMap<>();
    private HashMap<Value, Integer> leftEq = new HashMap<>();
    private HashMap<Value, Integer> rightEq = new HashMap<>();
    private ArrayList<Function> functions;

    public IfComb(ArrayList<Function> functions) {
        this.functions = functions;
    }


    public void Run() {
        PatternA();
        PatternB();
        PatternC();
    }




    private void PatternA() {
        for (Function function: functions) {
            know.clear();
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                if (!know.contains(bb)) {
                    left.clear();
                    right.clear();
                    leftEq.clear();
                    rightEq.clear();
                    DFS(bb);
                }
            }
        }
    }

    private void DFS(BasicBlock bb) {
        if (know.contains(bb)) {
            return;
        }
        HashMap<Value, Integer> tempLeft = new HashMap<>();
        HashMap<Value, Integer> tempRight = new HashMap<>();
        HashMap<Value, Integer> tempLeftEq = new HashMap<>();
        HashMap<Value, Integer> tempRightEq = new HashMap<>();
        tempLeft.putAll(left);
        tempRight.putAll(right);
        tempLeftEq.putAll(leftEq);
        tempRightEq.putAll(rightEq);
        know.add(bb);
        if (bb.getEndInstr() instanceof Instr.Jump) {
            DFS(bb.getSuccBBs().get(0));
        } else if (bb.getEndInstr() instanceof Instr.Branch &&
                ((Instr.Branch) bb.getEndInstr()).getCond() instanceof Instr.Icmp) {
            Instr.Branch br = (Instr.Branch) bb.getEndInstr();
            Instr.Icmp icmp = (Instr.Icmp) br.getCond();
            BasicBlock trueBB = br.getThenTarget();
            BasicBlock falseBB = br.getElseTarget();
            if (icmp.getRVal2() instanceof Constant) {
                Value value = icmp.getRVal1();
                int num = (int) ((Constant) icmp.getRVal2()).getConstVal();
                switch (icmp.getOp()) {
                    case SLT -> {
                        if (right.containsKey(value) && right.get(value) <= num) {
                            br.modifyUse(new Constant.ConstantInt(1), 0);
                        } else if (rightEq.containsKey(value) && rightEq.get(value) < num) {
                            br.modifyUse(new Constant.ConstantInt(1), 0);
                        } else if (left.containsKey(value) && left.get(value) >= num - 1) {
                            br.modifyUse(new Constant.ConstantInt(0), 0);
                        } else if (leftEq.containsKey(value) && leftEq.get(value) >= num) {
                            br.modifyUse(new Constant.ConstantInt(0), 0);
                        }
                        if (!right.containsKey(value)) {
                            right.put(value, num);
                        }
                        if (num < right.get(value)) {
                            right.put(value, num);
                        }
                    }
                    case SLE -> {
                        if (right.containsKey(value) && right.get(value) <= num + 1) {
                            br.modifyUse(new Constant.ConstantInt(1), 0);
                        } else if (rightEq.containsKey(value) && rightEq.get(value) <= num) {
                            br.modifyUse(new Constant.ConstantInt(1), 0);
                        } else if (left.containsKey(value) && left.get(value) >= num) {
                            br.modifyUse(new Constant.ConstantInt(0), 0);
                        } else if (leftEq.containsKey(value) && leftEq.get(value) >= num + 1) {
                            br.modifyUse(new Constant.ConstantInt(0), 0);
                        }
                        if (!rightEq.containsKey(value)) {
                            rightEq.put(value, num);
                        }
                        if (num < rightEq.get(value)) {
                            rightEq.put(value, num);
                        }
                    }
                    case SGT -> {
                        if (right.containsKey(value) && right.get(value) <= num + 1) {
                            br.modifyUse(new Constant.ConstantInt(0), 0);
                        } else if (rightEq.containsKey(value) && rightEq.get(value) <= num) {
                            br.modifyUse(new Constant.ConstantInt(0), 0);
                        } else if (left.containsKey(value) && left.get(value) >= num) {
                            br.modifyUse(new Constant.ConstantInt(1), 0);
                        } else if (leftEq.containsKey(value) && leftEq.get(value) >= num + 1) {
                            br.modifyUse(new Constant.ConstantInt(1), 0);
                        }
                        if (!left.containsKey(value)) {
                            left.put(value, num);
                        }
                        if (num > left.get(value)) {
                            left.put(value, num);
                        }
                    }
                    case SGE -> {
                        if (right.containsKey(value) && right.get(value) <= num) {
                            br.modifyUse(new Constant.ConstantInt(0), 0);
                        } else if (rightEq.containsKey(value) && rightEq.get(value) <= num - 1) {
                            br.modifyUse(new Constant.ConstantInt(0), 0);
                        } else if (left.containsKey(value) && left.get(value) >= num - 1) {
                            br.modifyUse(new Constant.ConstantInt(1), 0);
                        } else if (leftEq.containsKey(value) && leftEq.get(value) >= num) {
                            br.modifyUse(new Constant.ConstantInt(1), 0);
                        }
                        if (!leftEq.containsKey(value)) {
                            leftEq.put(value, num);
                        }
                        if (num > leftEq.get(value)) {
                            leftEq.put(value, num);
                        }
                    }
                }
            }
            if (trueBB.getPrecBBs().size() == 1 && !know.contains(trueBB)) {
                DFS(trueBB);
            }
            if (falseBB.getPrecBBs().size() == 1 && !know.contains(falseBB)) {
                left.clear();
                right.clear();
                leftEq.clear();
                rightEq.clear();

                left.putAll(tempLeft);
                right.putAll(tempRight);
                leftEq.putAll(tempLeftEq);
                rightEq.putAll(tempRightEq);

                if (icmp.getRVal2() instanceof Constant) {
                    Value value = icmp.getRVal1();
                    int num = (int) ((Constant) icmp.getRVal2()).getConstVal();
                    switch (icmp.getOp()) {
                        case SLT -> {
                            if (!leftEq.containsKey(value)) {
                                leftEq.put(value, num);
                            }
                            if (num > leftEq.get(value)) {
                                leftEq.put(value, num);
                            }
                        }
                        case SLE -> {
                            if (!left.containsKey(value)) {
                                left.put(value, num);
                            }
                            if (num > left.get(value)) {
                                left.put(value, num);
                            }
                        }
                        case SGT -> {
                            if (!rightEq.containsKey(value)) {
                                rightEq.put(value, num);
                            }
                            if (num < rightEq.get(value)) {
                                rightEq.put(value, num);
                            }
                        }
                        case SGE -> {
                            if (!right.containsKey(value)) {
                                right.put(value, num);
                            }
                            if (num < right.get(value)) {
                                right.put(value, num);
                            }
                        }
                    }
                }

                DFS(falseBB);
            }
        }

        left.clear();
        right.clear();
        leftEq.clear();
        rightEq.clear();

        left.putAll(tempLeft);
        right.putAll(tempRight);
        leftEq.putAll(tempLeftEq);
        rightEq.putAll(tempRightEq);
    }

    private HashSet<Value> trueValue = new HashSet<>(), falseValue = new HashSet<>();

    private void PatternB() {
        for (Function function: functions) {
            know.clear();
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                if (!know.contains(bb)) {
                    DFS_B(bb);
                }
            }
        }
    }


    private void DFS_B(BasicBlock bb) {
        if (know.contains(bb)) {
            return;
        }
        know.add(bb);
        if (bb.getEndInstr() instanceof Instr.Jump) {
            DFS_B(bb.getSuccBBs().get(0));
        } else if (bb.getEndInstr() instanceof Instr.Branch &&
                ((Instr.Branch) bb.getEndInstr()).getCond() instanceof Instr.Icmp) {
            Instr.Branch br = (Instr.Branch) bb.getEndInstr();
            Value cond = br.getCond();
            BasicBlock trueBB = br.getThenTarget();
            BasicBlock falseBB = br.getElseTarget();
            if (trueValue.contains(cond)) {
                DFS_B(trueBB);
            } else if (falseValue.contains(cond)) {
                DFS_B(falseBB);
            } else {
                if (trueBB.getPrecBBs().size() == 1) {
                    trueValue.add(cond);
                    DFS_B(trueBB);
                    trueValue.remove(cond);
                }
                if (falseBB.getPrecBBs().size() == 1) {
                    falseValue.add(cond);
                    DFS_B(falseBB);
                    falseValue.remove(cond);
                }
            }


        }
    }

    int cnt = 0;

    private void PatternC() {
        for (Function function: functions) {
            know.clear();
            for (BasicBlock bb = function.getBeginBB(); bb.getNext() != null; bb = (BasicBlock) bb.getNext()) {
                if (bb.getEndInstr() instanceof Instr.Branch) {
                    Instr.Branch br = (Instr.Branch) bb.getEndInstr();
                    Value cond = br.getCond();
                    BasicBlock trueBB = br.getThenTarget();
                    BasicBlock falseBB = br.getElseTarget();
                    if (trueBB.getSuccBBs().size() == 1 && trueBB.getSuccBBs().get(0).equals(falseBB) &&
                            falseBB.getEndInstr() instanceof Instr.Branch && ((Instr.Branch) falseBB.getEndInstr()).getCond().equals(cond)) {
                        cnt++;
                    }
                }
            }
        }
        if (cnt > 10) {
            System.exit(55);
        }
    }
}
