package descriptor;

import lir.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.LinkedBlockingQueue;

public class MIDescriptor implements Descriptor {
    public static final MIDescriptor MI_DESCRIPTOR = new MIDescriptor();
    Scanner scanner = new Scanner(System.in);
    // BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

    private MemSimulator MEM_SIM = MemSimulator.MEM_SIMULATOR;
    private RegSimulator REG_SIM = RegSimulator.REG_SIMULATOR;
    Machine.Program PROGRAM = Machine.Program.PROGRAM;
    StringBuilder sb = new StringBuilder();

    enum RunningState {
        BEFORE_MODE,//刚生成代码
        AFTER_MODE,//分配完所有寄存器
        MIX_MODE
    }

    private ArrayList<Object> curVRList = new ArrayList<>();

    private static class MemSimulator {
        public static final MemSimulator MEM_SIMULATOR = new MemSimulator();
        public static final ArrayList<Integer> STACK = new ArrayList<>();
        public static final ArrayList<Integer> HEAP = new ArrayList<>();
        public static int SP = 0;
        public static int GP = 0;

        private MemSimulator() {
        }
    }

    private static class RegSimulator {
        private RegSimulator() {
        }

        public static final RegSimulator REG_SIMULATOR = new RegSimulator();
        public final ArrayList<Integer> GPRS = new ArrayList<>();
        public final ArrayList<Float> FPRS = new ArrayList<>();
    }

    private int getGPRVal(int i) {
        return REG_SIM.GPRS.get(i / 4);
    }

    private int setGPRVal(int i, int val) {
        return REG_SIM.GPRS.set(i / 4, val);
    }

    private float getFPRVal(int i) {
        return REG_SIM.FPRS.get(i / 4);
    }


    private float setFPRVal(int i, float val) {
        return REG_SIM.FPRS.set(i / 4, val);
    }

    @Override
    public StringBuilder getOutput() {
        return sb;
    }

    private void logOut(String s) {
        System.err.println(s);
    }

    private Machine.McFunction curMF;
    private Machine.Block curMB;
    private MachineInst curMI;

    public void run() throws IOException {
        MI_DESCRIPTOR.getStdin();

        Machine.Program p = Machine.Program.PROGRAM;
        runMF(p.mainMcFunc);
    }

    public void runMF(Machine.McFunction mcFunc) {
        curMF = mcFunc;
        curVRList = new ArrayList<>(curMF.vrList.size());
        runMB(curMF.getBeginMB());
        for (Machine.Block mb : curMF.mbList) {
        }

    }

    private void runMB(Machine.Block mb) {
        curMB = mb;
        logOut("");
        logOut(mb.getDebugLabel());
        curMB = mb;
        for (MachineInst mi : mb.miList) {
            String str = mi instanceof MIComment ? "" : "\t";
            logOut(str + mi);
            curMI = mi;
            exec(mi);
        }
    }

    public LinkedBlockingQueue<String> inputList = new LinkedBlockingQueue<>();

    public void getStdin() {
        while (scanner.hasNext()) {
            inputList.offer(scanner.nextLine());
        }
    }

    //设为Object是为了保证int和float的兼容性
    private Object GET_VAL_FROM_OPD(Machine.Operand o) {
        return switch (o.getType()) {
            case PreColored, Allocated -> loadFromReg(o.getReg());
            case Virtual -> curVRList.get(o.getValue());
            case Immediate -> o.getImm();
        };
    }

    //设为Object是为了保证int和float的兼容性
    private void SET_VAL_FROM_OPD(Object val, Machine.Operand o) {
        switch (o.getType()) {
            case PreColored, Allocated -> storeToReg(val, o);
            case Virtual -> curVRList.set(o.getValue(), val);
            case Immediate -> throw new AssertionError("Try to save |" + val + "| to " + o);
        }
    }

    private Object loadFromReg(Arm.Regs regEnum) {
        if (regEnum instanceof Arm.Regs.GPRs) {
            return getGPRVal(((Arm.Regs.GPRs) regEnum).ordinal());
        } else {
            assert regEnum instanceof Arm.Regs.FPRs;
            return getFPRVal(((Arm.Regs.FPRs) regEnum).ordinal());
        }
    }


    private void storeToReg(Object obj, Machine.Operand opd) {
        if (opd.getReg() instanceof Arm.Regs.GPRs) {
            assert obj instanceof Integer;
            setGPRVal(((Arm.Regs.GPRs) opd.getReg()).ordinal(), (int) obj);
        } else {
            assert opd.getReg() instanceof Arm.Regs.FPRs;
            assert obj instanceof Float;
            setFPRVal(((Arm.Regs.FPRs) opd.getReg()).ordinal(), (float) obj);
        }
    }

    /**
     * true表示当前执行到返回语句了, 当前函数应当直接返回
     */

    public boolean exec(MachineInst mi) {
        if (mi.isMove()) {
            MIMove mv = (MIMove) mi;
            Machine.Operand dst = mv.getDst();
            Machine.Operand src = mv.getSrc();
            Object val = GET_VAL_FROM_OPD(src);
            SET_VAL_FROM_OPD(val, dst);
        } else if (mi.isCall()) {
            runMF(((MICall)mi).mcFunction);
        }else if (mi.isReturn()){

        }
        return false;
    }
}
