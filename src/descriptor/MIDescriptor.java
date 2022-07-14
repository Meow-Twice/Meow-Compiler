package descriptor;

import lir.*;
import manage.Manager;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Scanner;
import java.util.concurrent.LinkedBlockingQueue;

import static lir.Arm.Regs.GPRs.r0;

public class MIDescriptor implements Descriptor {
    public final int CPSR_N = 1 << 31;
    public final int CPSR_Z = 1 << 30;
    public static final MIDescriptor MI_DESCRIPTOR = new MIDescriptor();
    Scanner scanner = new Scanner(System.in);
    // BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

    // private MemSimulator MEM_SIM = MemSimulator.MEM_SIMULATOR;
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
        // public static final MemSimulator MEM_SIMULATOR = new MemSimulator();
        public static final ArrayList<Integer> STACK = new ArrayList<>();
        public static final ArrayList<Integer> HEAP = new ArrayList<>();
        public static int SP = 0;
        public static int GP = 0;

        // private MemSimulator() {
        // }
    }

    private static class RegSimulator {
        private RegSimulator() {
        }

        public static final RegSimulator REG_SIMULATOR = new RegSimulator();
        public static final ArrayList<Integer> GPRS = new ArrayList<>(Arm.Regs.GPRs.values().length);
        public static final ArrayList<Float> FPRS = new ArrayList<>(Arm.Regs.FPRs.values().length);
        public int CMP_STATUS = 0;

        static {
            for (int i = 0; i < Arm.Regs.GPRs.values().length; i++) {
                GPRS.add(0);
            }
            for (int i = 0; i < Arm.Regs.FPRs.values().length; i++) {
                FPRS.add((float) 0.0);
            }
        }
    }

    private int getGPRVal(int i) {
        return REG_SIM.GPRS.get(i);
    }

    private int setGPRVal(int i, int val) {
        return REG_SIM.GPRS.set(i, val);
    }

    private float getFPRVal(int i) {
        return REG_SIM.FPRS.get(i);
    }


    private float setFPRVal(int i, float val) {
        return REG_SIM.FPRS.set(i, val);
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
        System.out.println(getFromReg(r0));
    }

    public void runMF(Machine.McFunction mcFunc) {
        logOut("@now:\t" + mcFunc.mFunc.getName());
        curMF = mcFunc;
        if (curMF.mFunc.isExternal) {
            dealExternalFunc();
            return;
        }
        curVRList = new ArrayList<>(Collections.nCopies(curMF.vrList.size(), 0));
        runMB(curMF.getBeginMB());
    }

    private void dealExternalFunc() {
        if (curMF.mFunc.getName().equals("getint")) {
            String s = inputList.poll();
            assert s != null;
            logOut(s);
            int i = Integer.parseInt(s);
            setToReg(i, r0);
        } else if (curMF.mFunc.getName().equals("putint")) {
            System.out.println(getFromReg(r0));
        }
    }

    private void runMB(Machine.Block mb) {
        curMB = mb;
        logOut("");
        logOut(mb.getDebugLabel());
        boolean isBJ = false;
        boolean isRet = false;
        Machine.Block nextMB = null;
        for (MachineInst mi : mb.miList) {
            String str = mi instanceof MIComment ? "" : "\t";
            logOut(str + mi);
            if (mi.getCond() != Arm.Cond.Any && !mi.isBranch() && !satisfyCond(mi.getCond())) {
                logOut("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                continue;
            }
            curMI = mi;
            MIBinary miBinary = null;
            Object lVal = null;
            Object rVal = null;
            if (curMI.isActuallyBino()) {
                miBinary = (MIBinary) curMI;
                lVal = GET_VAL_FROM_OPD(miBinary.getLOpd());
                rVal = GET_VAL_FROM_OPD(miBinary.getROpd());
            }
            switch (curMI.getType()) {
                case Add -> {
                    assert lVal instanceof Integer && rVal instanceof Integer;
                    SET_VAL_FROM_OPD((int) lVal + (int) rVal, miBinary.getDst());
                }
                case FAdd -> {
                    assert lVal instanceof Float && rVal instanceof Float;
                    SET_VAL_FROM_OPD((float) lVal + (float) rVal, miBinary.getDst());
                }
                case Sub -> {
                    assert lVal instanceof Integer && rVal instanceof Integer;
                    SET_VAL_FROM_OPD((int) lVal - (int) rVal, miBinary.getDst());
                }
                case FSub -> {
                    assert lVal instanceof Float && rVal instanceof Float;
                    SET_VAL_FROM_OPD((float) lVal - (float) rVal, miBinary.getDst());
                }
                case Rsb -> {
                    assert lVal instanceof Integer && rVal instanceof Integer;
                    SET_VAL_FROM_OPD((int) rVal - (int) lVal, miBinary.getDst());
                }
                case Mul -> {
                    assert lVal instanceof Integer && rVal instanceof Integer;
                    SET_VAL_FROM_OPD((int) lVal * (int) rVal, miBinary.getDst());
                }
                case FMul -> {
                    assert lVal instanceof Float && rVal instanceof Float;
                    SET_VAL_FROM_OPD((float) lVal * (float) rVal, miBinary.getDst());
                }
                case Div -> {
                    assert lVal instanceof Integer && rVal instanceof Integer;
                    SET_VAL_FROM_OPD((int) lVal / (int) rVal, miBinary.getDst());
                }
                case FDiv -> {
                    assert lVal instanceof Float && rVal instanceof Float;
                    SET_VAL_FROM_OPD((float) lVal / (float) rVal, miBinary.getDst());
                }
                case Mod -> {
                    // a % b= a - (a / b) * b
                    assert lVal instanceof Integer && rVal instanceof Integer;
                    SET_VAL_FROM_OPD((int) lVal - ((int) lVal / (int) rVal) * (int) rVal, miBinary.getDst());
                }
                case FMod -> {
                    // a % b = a - (b * q), 其中：q = int(a / b)
                    assert lVal instanceof Float && rVal instanceof Float;
                    SET_VAL_FROM_OPD((int) lVal - (float) rVal * ((int) ((float) lVal / (float) rVal)), miBinary.getDst());
                }
                case Lt -> {
                }
                case Le -> {
                }
                case Ge -> {
                }
                case Gt -> {
                }
                case Eq -> {
                }
                case Ne -> {
                }
                case And -> {
                }
                case FAnd -> {
                }
                case Or -> {
                }
                case FOr -> {
                }
                case LongMul -> {
                    assert lVal instanceof Integer && rVal instanceof Integer;
                    SET_VAL_FROM_OPD((int) (((long) lVal * (long) rVal) >>> 32), miBinary.getDst());
                }
                case FMA -> {
                    assert mi instanceof MIFma;
                    MIFma fma = (MIFma) mi;
                    Machine.Operand dst = fma.getDst();
                    Machine.Operand acc = fma.getAcc();
                    Machine.Operand lOpd = fma.getlOpd();
                    Machine.Operand rOpd = fma.getrOpd();
                    int res;
                    if (fma.isSign()) {
                        res = (int) (((long) GET_VAL_FROM_OPD(lOpd) * (long) GET_VAL_FROM_OPD(rOpd)) >> 32);
                    } else {
                        res = (int) GET_VAL_FROM_OPD(lOpd) * (int) GET_VAL_FROM_OPD(rOpd);
                    }
                    if (fma.isAdd()) {
                        res = (int) GET_VAL_FROM_OPD(acc) + res;
                    } else {
                        res = (int) GET_VAL_FROM_OPD(acc) - res;
                    }
                    SET_VAL_FROM_OPD(res, dst);
                }
                case Mv -> {
                    assert mi instanceof MIMove;
                    MIMove mv = (MIMove) mi;
                    Object val = GET_VAL_FROM_OPD(mv.getSrc());
                    SET_VAL_FROM_OPD(val, mv.getDst());
                }
                case Branch -> {
                    isBJ = true;
                    assert mi instanceof MIBranch;
                    MIBranch br = (MIBranch) mi;
                    if (satisfyCond(br.getCond()))
                        nextMB = br.getTrueTargetBlock();
                    else
                        nextMB = br.getFalseTargetBlock();
                }
                case Jump -> {
                    isBJ = true;
                    assert mi instanceof MIJump;
                    MIJump j = (MIJump) mi;
                    nextMB = j.getTarget();
                }
                case Return -> isRet = true;
                case Load -> {
                    assert mi instanceof MILoad;
                    MILoad load = (MILoad) mi;
                    Object tmp = GET_VAL_FROM_OPD(load.getOffset());
                    assert tmp instanceof Integer;
                    int offset = (int) tmp;
                    offset = offset << load.getShift();
                    tmp = GET_VAL_FROM_OPD(load.getAddr());
                    assert tmp instanceof Integer;
                    offset += (int) tmp;
                    SET_VAL_FROM_OPD(getMemValWithOffset(offset), load.getData());
                }
                case Store -> {
                    assert mi instanceof MIStore;
                    MIStore store = (MIStore) mi;
                    Object tmp = GET_VAL_FROM_OPD(store.getOffset());
                    assert tmp instanceof Integer;
                    int offset = (int) tmp;
                    offset = offset << store.getShift();
                    tmp = GET_VAL_FROM_OPD(store.getAddr());
                    if(tmp instanceof Integer){
                        offset += (int) tmp;
                        tmp = GET_VAL_FROM_OPD(store.getData());
                        if (tmp instanceof Integer) {
                            setMemValWithOffSet((int) tmp, offset);
                        } else {
                            // TODO 目前不知道怎么把十进制的int转成float, 理论上前端应该插了转化?
                            throw new AssertionError("store a float " + (float) tmp + " not done yet");
                        }
                    }else{
                        assert tmp instanceof String;
                        String globAddr = (String) tmp;

                    }
                }
                case Compare -> {
                    REG_SIM.CMP_STATUS = 0;
                    assert mi instanceof MICompare;
                    MICompare cmp = (MICompare) mi;
                    Object lTmp = GET_VAL_FROM_OPD(cmp.getLOpd());
                    Object rTmp = GET_VAL_FROM_OPD(cmp.getROpd());
                    assert lTmp instanceof Integer && rTmp instanceof Integer;
                    int lhs = (int) lTmp;
                    int rhs = (int) rTmp;
                    if (lhs < rhs) {
                        REG_SIM.CMP_STATUS = REG_SIM.CMP_STATUS | CPSR_N;
                    }
                    if (lhs == rhs) {
                        REG_SIM.CMP_STATUS = REG_SIM.CMP_STATUS | CPSR_Z;
                    }
                }
                case Call -> {
                    assert mi instanceof MICall;
                    runMF(((MICall) mi).mcFunction);
                }
                case Global -> throw new AssertionError("not done yet");
                case Comment -> {
                }
                case Empty -> {
                }
            }
        }
        assert (isRet && !isBJ) || (!isRet && isBJ);
        if (isBJ) {
            assert nextMB != null;
            runMB(nextMB);
        }
    }

    private int getMemValWithOffset(int offset) {
        return MemSimulator.STACK.get(offset / 4);
    }

    private void setMemValWithOffSet(int val, int offset) {
        MemSimulator.STACK.set(offset, val);
    }

    private boolean satisfyCond(Arm.Cond cond) {
        int cmp_status = REG_SIM.CMP_STATUS;
        // (cmp_status & CPSR_Z) != 0: lhs == rhs
        // (cmp_status & CPSR_Z) == 0: lhs != rhs (lhs < rhs || lhs > rhs)
        // (cmp_status & CPSR_N) != 0: lhs < rhs
        // (cmp_status & CPSR_N) == 0: lhs >= rhs
        return switch (cond) {
            case Eq -> (cmp_status & CPSR_Z) != 0;
            case Ne -> (cmp_status & CPSR_Z) == 0;
            case Ge -> (cmp_status & CPSR_N) == 0;
            case Gt -> (cmp_status & CPSR_N) == 0 && (cmp_status & CPSR_Z) == 0;
            case Le -> (cmp_status & CPSR_N) != 0 || (cmp_status & CPSR_Z) != 0;
            case Lt -> (cmp_status & CPSR_N) != 0;
            case Any -> throw new AssertionError("Wrong cmp: " + cmp_status + " compare with " + cond);
        };
    }

    public LinkedBlockingQueue<String> inputList = new LinkedBlockingQueue<>();

    public void getStdin() {
        while (scanner.hasNext()) {
            inputList.offer(scanner.nextLine());
        }
    }

    //设为Object是为了保证int和float的兼容性
    // 可能返回int或者float或者String(glob地址)
    private Object GET_VAL_FROM_OPD(Machine.Operand o) {
        return switch (o.getType()) {
            case PreColored, Allocated -> getFromReg(o.getReg());
            case Virtual -> curVRList.get(o.getValue());
            case Immediate ->
                o.isGlobPtr() ? o.getGlob() : o.getImm();

        };
    }

    //设为Object是为了保证int和float的兼容性
    private void SET_VAL_FROM_OPD(Object val, Machine.Operand o) {
        switch (o.getType()) {
            case PreColored, Allocated -> setToReg(val, o.getReg());
            case Virtual -> curVRList.set(o.getValue(), val);
            case Immediate -> throw new AssertionError("Try to save |" + val + "| to " + o);
        }
    }

    private Object getFromReg(Arm.Regs regEnum) {
        if (regEnum instanceof Arm.Regs.GPRs) {
            return getGPRVal(((Arm.Regs.GPRs) regEnum).ordinal());
        } else {
            assert regEnum instanceof Arm.Regs.FPRs;
            return getFPRVal(((Arm.Regs.FPRs) regEnum).ordinal());
        }
    }


    private void setToReg(Object obj, Arm.Regs regEnum) {
        if (regEnum instanceof Arm.Regs.GPRs) {
            assert obj instanceof Integer;
            setGPRVal(((Arm.Regs.GPRs) regEnum).ordinal(), (int) obj);
        } else {
            assert regEnum instanceof Arm.Regs.FPRs;
            assert obj instanceof Float;
            setFPRVal(((Arm.Regs.FPRs) regEnum).ordinal(), (float) obj);
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
            runMF(((MICall) mi).mcFunction);
        } else if (mi.isReturn()) {
            return true;
        }
        return false;
    }
}
