package lir;

import java.io.PrintStream;
import java.util.Iterator;

import static backend.CodeGen.sParamCnt;
import static lir.Arm.Regs.GPRs.*;

public class StackCtl extends MachineInst {
    public StackCtl(Tag tag, MC.Block mb) {
        super(tag, mb);
    }

    public static class Push extends StackCtl {
        MC.McFunction savedRegsMf;
        MC.Block mb;

        public Push(MC.McFunction savedRegsMf, MC.Block mb) {
            super(Tag.Push, mb);
            this.savedRegsMf = savedRegsMf;
            this.mb = mb;
        }

        @Override
        public String toString() {
            if (savedRegsMf.mFunc.isExternal) {
                // throw new AssertionError("push in external func");
                // return "\tpush\t{r0,r1,r2,r3}";
                return "\tpush\t{r2,r3}";
            } else {
                StringBuilder sb = new StringBuilder();
                if (savedRegsMf.usedCalleeSavedGPRs.size() > 0) {
                    sb.append("\tpush\t{");
                    Iterator<GPRs> gprIter = savedRegsMf.usedCalleeSavedGPRs.iterator();
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
        public void output(PrintStream os, MC.McFunction f) {
            os.println(this);
        }
    }

    public static class Pop extends MachineInst {
        MC.McFunction savedRegsMf;
        MC.Block mb;

        public Pop(MC.McFunction savedRegsMf, MC.Block mb) {
            super(Tag.Pop, mb);
            this.savedRegsMf = savedRegsMf;
            this.mb = mb;
        }


        @Override
        public String toString() {
            if (savedRegsMf.mFunc.isExternal) {
                // throw new AssertionError("pop in external func");
                // return "\tpop\t{r0,r1,r2,r3}";
                return "\tpop\t{r2,r3}";
            } else {
                StringBuilder sb = new StringBuilder();
                if (savedRegsMf.usedCalleeSavedGPRs.size() > 0) {
                    sb.append("\tpop\t{");
                    Iterator<GPRs> gprIter = savedRegsMf.usedCalleeSavedGPRs.iterator();
                    sb.append(gprIter.next());
                    while (gprIter.hasNext()) {
                        GPRs gpr = gprIter.next();
                        sb.append(",").append(gpr);
                    }
                    sb.append("}");
                }
                return sb.toString();
            }
        }

        @Override
        public void output(PrintStream os, MC.McFunction f) {
            os.println(this);
        }
    }


    public static class VPush extends StackCtl {
        MC.McFunction savedRegsMf;
        MC.Block mb;

        public VPush(MC.McFunction savedRegsMf, MC.Block mb) {
            super(Tag.Push, mb);
            this.savedRegsMf = savedRegsMf;
            this.mb = mb;
        }

        @Override
        public void output(PrintStream os, MC.McFunction f) {
            os.println(this);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (savedRegsMf.mFunc.isExternal) {
                // throw new AssertionError("vpush in external func");
                // sb.append(String.format("\tvpush\t{s0-s%d}", sParamCnt - 1));
                sb.append(String.format("\tvpush\t{s2-s%d}", sParamCnt - 1));
            } else {
                if (savedRegsMf.usedCalleeSavedFPRs.size() > 0) {
                    int fprNum = FPRs.values().length;
                    boolean[] fprBit = new boolean[fprNum];
                    for (FPRs fpr : savedRegsMf.usedCalleeSavedFPRs) {
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
                            throw new AssertionError("illegal vpush");
                            // assert false;
                            // sb.append(String.format("\tvpush\t{s%d}%n", start));
                        } else if (end > start) {
                            if (end - start > 15) {
                                end = start + 15;
                            }
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
        MC.McFunction savedRegsMf;
        MC.Block mb;

        public VPop(MC.McFunction savedRegsMf, MC.Block mb) {
            super(Tag.Pop, mb);
            this.savedRegsMf = savedRegsMf;
            this.mb = mb;
        }


        @Override
        public void output(PrintStream os, MC.McFunction f) {
            os.println(this);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (savedRegsMf.mFunc.isExternal) {
                // throw new AssertionError("vpop in external func");
                // sb.append(String.format("\tvpop\t{s0-s%d}%n", sParamCnt - 1));
                sb.append(String.format("\tvpop\t{s2-s%d}", sParamCnt - 1));
            } else {
                if (savedRegsMf.usedCalleeSavedFPRs.size() > 0) {
                    int fprNum = FPRs.values().length;
                    boolean[] fprBit = new boolean[fprNum];
                    for (FPRs fpr : savedRegsMf.usedCalleeSavedFPRs) {
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
                            throw new AssertionError("illegal vpop");
                            // sb.append(String.format("\tvpop\t{s%d}%n", end));
                        } else if (start < end) {
                            if (end - start > 15) {
                                start = end - 15;
                            }
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
