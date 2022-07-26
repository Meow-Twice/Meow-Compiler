package lir;

import java.io.PrintStream;
import java.util.Iterator;

import static backend.CodeGen.sParamCnt;
import static lir.Arm.Regs.GPRs.lr;
import static lir.Arm.Regs.GPRs.pc;

public class StackCtl extends MachineInst {
    public StackCtl(Tag tag, Machine.Block mb) {
        super(tag, mb);
    }

    public static class MIPush extends StackCtl {
        Machine.McFunction savedRegsMf;
        Machine.Block mb;

        public MIPush(Machine.McFunction savedRegsMf, Machine.Block mb) {
            super(Tag.Push, mb);
            this.savedRegsMf = savedRegsMf;
            this.mb = mb;
        }

        @Override
        public String toString() {
            if (savedRegsMf.mFunc.isExternal) {
                return "\tpush\t{r0,r1,r2,r3}";
            } else {
                StringBuilder sb = new StringBuilder();
                if (savedRegsMf.usedCalleeSavedGPRs.size() > 0) {
                    sb.append("\tpush\t{");
                    Iterator<Arm.Regs.GPRs> gprIter = savedRegsMf.usedCalleeSavedGPRs.iterator();
                    sb.append(gprIter.next());
                    while (gprIter.hasNext()) {
                        sb.append(",").append(gprIter.next());
                    }
                    sb.append("}");
                }
                return sb.toString();
            }
        }

        @Override
        public void output(PrintStream os, Machine.McFunction f) {
            os.println(this);
        }
    }

    public static class MIPop extends MachineInst {
        Machine.McFunction savedRegsMf;
        Machine.Block mb;

        public MIPop(Machine.McFunction savedRegsMf, Machine.Block mb) {
            super(Tag.Pop, mb);
            this.savedRegsMf = savedRegsMf;
            this.mb = mb;
        }


        @Override
        public String toString() {
            if (savedRegsMf.mFunc.isExternal) {
                return "\tpop\t{r0,r1,r2,r3}";
            } else {
                StringBuilder sb = new StringBuilder();
                if (savedRegsMf.usedCalleeSavedGPRs.size() > 0) {
                    sb.append("\tpop\t{");
                    Iterator<Arm.Regs.GPRs> gprIter = savedRegsMf.usedCalleeSavedGPRs.iterator();
                    sb.append(gprIter.next());
                    while (gprIter.hasNext()) {
                        Arm.Regs.GPRs gpr = gprIter.next();
                        sb.append(",").append(gpr);
                    }
                    sb.append("}");
                }
                return sb.toString();
            }
        }

        @Override
        public void output(PrintStream os, Machine.McFunction f) {
            os.println(this);
        }
    }


    public static class VPush extends StackCtl {
        Machine.McFunction savedRegsMf;
        Machine.Block mb;

        public VPush(Machine.McFunction savedRegsMf, Machine.Block mb) {
            super(Tag.Push, mb);
            this.savedRegsMf = savedRegsMf;
            this.mb = mb;
        }

        @Override
        public void output(PrintStream os, Machine.McFunction f) {
            os.println(this);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (savedRegsMf.mFunc.isExternal) {
                sb.append(String.format("\tvpush\t{s0-s%d}", sParamCnt - 1));
            } else {
                if (savedRegsMf.usedCalleeSavedFPRs.size() > 0) {
                    int fprNum = Arm.Regs.FPRs.values().length;
                    boolean[] fprBit = new boolean[fprNum];
                    for (Arm.Regs.FPRs fpr : savedRegsMf.usedCalleeSavedFPRs) {
                        fprBit[fpr.ordinal()] = true;
                    }
                    int start = 0;
                    while (start < fprNum) {
                        while (start < fprNum && !fprBit[start])
                            start++;
                        if (start == fprNum)
                            break;
                        int end = start;
                        while (end < fprNum && fprBit[end])
                            end++;
                        end--;
                        if (end == start) {
                            sb.append(String.format("\tvpush\t{s%d}%n", start));
                        } else if (end > start) {
                            sb.append(String.format("\tvpush\t{s%d-s%d}%n", start, end));
                        }
                        start = end + 1;
                    }
                }
            }
            return sb.toString();
        }
    }

    public static class VPop extends MachineInst {
        Machine.McFunction savedRegsMf;
        Machine.Block mb;

        public VPop(Machine.McFunction savedRegsMf, Machine.Block mb) {
            super(Tag.Pop, mb);
            this.savedRegsMf = savedRegsMf;
            this.mb = mb;
        }


        @Override
        public void output(PrintStream os, Machine.McFunction f) {
            os.println(this);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (savedRegsMf.mFunc.isExternal) {
                sb.append(String.format("\tvpop\t{s0-s%d}%n", sParamCnt - 1));
            } else {
                if (savedRegsMf.usedCalleeSavedFPRs.size() > 0) {
                    int fprNum = Arm.Regs.FPRs.values().length;
                    boolean[] fprBit = new boolean[fprNum];
                    for (Arm.Regs.FPRs fpr : savedRegsMf.usedCalleeSavedFPRs) {
                        fprBit[fpr.ordinal()] = true;
                    }
                    int end = fprNum - 1;
                    while (end > -1) {
                        while (end > -1 && !fprBit[end])
                            end--;
                        if (end == -1)
                            break;
                        int start = end;
                        while (start > -1 && fprBit[start])
                            start--;
                        start++;
                        if (start == end) {
                            sb.append(String.format("\tvpop\t{s%d}%n", end));
                        } else if (start < end) {
                            sb.append(String.format("\tvpop\t{s%d-s%d}%n", start, end));
                        }
                        end = start - 1;
                    }
                }
            }
            return sb.toString();
        }
    }
}
