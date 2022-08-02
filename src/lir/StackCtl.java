package lir;

import mir.Instr;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import static backend.CodeGen.sParamCnt;
import static lir.Arm.Regs.GPRs.*;

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
/*            //所有push的reg都算作use，sp是define+use
            this.defs_yyf.add(new Machine.Operand(sp));
            this.uses_yyf.add(new Machine.Operand(sp));
            if(savedRegsMf.mFunc.isExternal){
                this.uses_yyf.add(new Machine.Operand(r2));
                this.uses_yyf.add(new Machine.Operand(r3));
            }
            else{
                if(savedRegsMf.usedCalleeSavedGPRs.size() > 0){
                    for(Arm.Regs.GPRs reg : savedRegsMf.usedCalleeSavedGPRs){
                        this.uses_yyf.add(new Machine.Operand(reg));
                    }
                }
            }*/
            this.mb = mb;
        }
        @Override
        public ArrayList<Machine.Operand> getDefOpds() {
            ArrayList<Machine.Operand> defs_yyf = new ArrayList<>();
            defs_yyf.add(new Machine.Operand(sp));
            return defs_yyf;
        }

        @Override
        public ArrayList<Machine.Operand> getUseOpds() {
            ArrayList<Machine.Operand> uses_yyf = new ArrayList<>();
            uses_yyf.add(new Machine.Operand(sp));
            if(savedRegsMf.mFunc.isExternal){
                uses_yyf.add(new Machine.Operand(r2));
                uses_yyf.add(new Machine.Operand(r3));
            }
            else{
                if(savedRegsMf.usedCalleeSavedGPRs.size() > 0){
                    for(Arm.Regs.GPRs reg : savedRegsMf.usedCalleeSavedGPRs){
                        uses_yyf.add(new Machine.Operand(reg));
                    }
                }
            }
            return uses_yyf;
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
        public void output(PrintStream os, Machine.McFunction f) {
            os.println(this);
        }
    }

    public static class MIPop extends MachineInst {

        protected ArrayList<Machine.Operand> uses_yyf = new ArrayList<>();
        Machine.McFunction savedRegsMf;
        Machine.Block mb;

        public MIPop(Machine.McFunction savedRegsMf, Machine.Block mb) {
            super(Tag.Pop, mb);
            this.savedRegsMf = savedRegsMf;
            this.mb = mb;
            /*//所有pop的reg都算作define，sp是define+use
            this.defs_yyf.add(new Machine.Operand(sp));
            this.uses_yyf.add(new Machine.Operand(sp));
            if(savedRegsMf.mFunc.isExternal){
                this.defs_yyf.add(new Machine.Operand(r2));
                this.defs_yyf.add(new Machine.Operand(r3));
            }
            else{
                if(savedRegsMf.usedCalleeSavedGPRs.size() > 0){
                    for(Arm.Regs.GPRs reg : savedRegsMf.usedCalleeSavedGPRs){
                        this.defs_yyf.add(new Machine.Operand(reg));
                    }
                }
            }*/
        }

        @Override
        public ArrayList<Machine.Operand> getDefOpds() {
            ArrayList<Machine.Operand> defs_yyf = new ArrayList<>();
            defs_yyf.add(new Machine.Operand(sp));
            if(savedRegsMf.mFunc.isExternal){
                defs_yyf.add(new Machine.Operand(r2));
                defs_yyf.add(new Machine.Operand(r3));
            }
            else {
                if (savedRegsMf.usedCalleeSavedGPRs.size() > 0) {
                    for (Arm.Regs.GPRs reg : savedRegsMf.usedCalleeSavedGPRs) {
                        defs_yyf.add(new Machine.Operand(reg));
                    }
                }
            }
            return defs_yyf;
        }

        @Override
        public ArrayList<Machine.Operand> getUseOpds() {
            ArrayList<Machine.Operand> uses_yyf = new ArrayList<>();
            uses_yyf.add(new Machine.Operand(sp));
            return this.uses_yyf;
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
/*            //所有push的reg都算作use，sp是define+use
            this.defs_yyf.add(new Machine.Operand(sp));
            this.uses_yyf.add(new Machine.Operand(sp));
            if(savedRegsMf.mFunc.isExternal){
                for(int i = 2;i<sParamCnt;i++) {
                    this.uses_yyf.add(new Machine.Operand(FPRs.class.getEnumConstants()[i]));
                }
            }
            else{
                if(savedRegsMf.usedCalleeSavedGPRs.size() > 0){
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
                        } else if (end > start) {
                            if (end - start > 15) {
                                end = start + 15;
                            }
                            for(int i = start;i<=end;i++) {
                                this.uses_yyf.add(new Machine.Operand(FPRs.class.getEnumConstants()[i]));
                            }
                        }
                        start = end + 1;
                    }
                }
            }*/
        }

        @Override
        public void output(PrintStream os, Machine.McFunction f) {
            os.println(this);
        }

        @Override
        public ArrayList<Machine.Operand> getDefOpds() {
            ArrayList<Machine.Operand> defs_yyf = new ArrayList<>();
            defs_yyf.add(new Machine.Operand(sp));
            return defs_yyf;
        }

        @Override
        public ArrayList<Machine.Operand> getUseOpds() {
            ArrayList<Machine.Operand> uses_yyf = new ArrayList<>();
            uses_yyf.add(new Machine.Operand(sp));
            if(savedRegsMf.mFunc.isExternal){
                for(int i = 2;i<sParamCnt;i++) {
                    uses_yyf.add(new Machine.Operand(FPRs.class.getEnumConstants()[i]));
                }
            }
            else{
                if(savedRegsMf.usedCalleeSavedGPRs.size() > 0){
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
                        } else if (end > start) {
                            if (end - start > 15) {
                                end = start + 15;
                            }
                            for(int i = start;i<=end;i++) {
                                uses_yyf.add(new Machine.Operand(FPRs.class.getEnumConstants()[i]));
                            }
                        }
                        start = end + 1;
                    }
                }
            }
            return uses_yyf;
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
        Machine.McFunction savedRegsMf;
        Machine.Block mb;

        public VPop(Machine.McFunction savedRegsMf, Machine.Block mb) {
            super(Tag.Pop, mb);
            this.savedRegsMf = savedRegsMf;
            this.mb = mb;
            /*//所有pop的reg都算作define，sp是define+use
            this.defs_yyf.add(new Machine.Operand(sp));
            this.uses_yyf.add(new Machine.Operand(sp));
            if (savedRegsMf.mFunc.isExternal) {
                // throw new AssertionError("vpop in external func");
                // sb.append(String.format("\tvpop\t{s0-s%d}%n", sParamCnt - 1));
                //sb.append(String.format("\tvpop\t{s2-s%d}", sParamCnt - 1));
                for(int i = 2;i<sParamCnt;i++) {
                    this.defs_yyf.add(new Machine.Operand(FPRs.class.getEnumConstants()[i]));
                }
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
                            //sb.append(String.format("\tvpop\t{s%d-s%d}%n", start, end));
                            for(int i = start;i<=end;i++){
                                this.defs_yyf.add(new Machine.Operand(FPRs.class.getEnumConstants()[i]));
                            }
                        }
                        end = start - 1;
                    }
                }
            }*/
        }


        @Override
        public void output(PrintStream os, Machine.McFunction f) {
            os.println(this);
        }

        @Override
        public ArrayList<Machine.Operand> getDefOpds() {
            ArrayList<Machine.Operand> defs_yyf = new ArrayList<>();
            defs_yyf.add(new Machine.Operand(sp));
            if (savedRegsMf.mFunc.isExternal) {
                // throw new AssertionError("vpop in external func");
                // sb.append(String.format("\tvpop\t{s0-s%d}%n", sParamCnt - 1));
                //sb.append(String.format("\tvpop\t{s2-s%d}", sParamCnt - 1));
                for(int i = 2;i<sParamCnt;i++) {
                    defs_yyf.add(new Machine.Operand(FPRs.class.getEnumConstants()[i]));
                }
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
                            //sb.append(String.format("\tvpop\t{s%d-s%d}%n", start, end));
                            for(int i = start;i<=end;i++){
                                defs_yyf.add(new Machine.Operand(FPRs.class.getEnumConstants()[i]));
                            }
                        }
                        end = start - 1;
                    }
                }
            }
            return defs_yyf;
        }

        @Override
        public ArrayList<Machine.Operand> getUseOpds() {
            ArrayList<Machine.Operand> uses_yyf = new ArrayList<>();
            uses_yyf.add(new Machine.Operand(sp));
            return uses_yyf;
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
