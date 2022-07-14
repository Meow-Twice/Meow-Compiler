package descriptor;

import backend.CodeGen;
import frontend.semantic.Initial;
import lir.*;
import mir.Constant;
import mir.GlobalVal;
import mir.Value;
import mir.type.Type;
import util.FileDealer;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

import static lir.Arm.Regs.GPRs.r0;
import static lir.Arm.Regs.GPRs.sp;

public class MIDescriptor implements Descriptor {
    public final int CPSR_N = 1 << 31;
    public final int CPSR_Z = 1 << 30;
    public static final MIDescriptor MI_DESCRIPTOR = new MIDescriptor();
    Scanner scanner = new Scanner(System.in);
    // BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

    // private MemSimulator MEM_SIM = MemSimulator.MEM_SIMULATOR;
    private final RegSimulator REG_SIM = RegSimulator.REG_SIMULATOR;
    Machine.Program PROGRAM = Machine.Program.PROGRAM;
    StringBuilder sb = new StringBuilder();

    enum RunningState {
        BEFORE_MODE,//刚生成代码
        AFTER_MODE,//分配完所有寄存器
        MIX_MODE
    }

    private HashMap<Machine.McFunction, Stack<ArrayList<Object>>> mf2curVRListMap = new HashMap<>();
    private ArrayList<Object> curVRList;

    private static class MemSimulator {
        // public static final MemSimulator MEM_SIMULATOR = new MemSimulator();
        private static final int N = 10;
        public static final int SP_BOTTOM = 0x40000000 >> 2 >> N;
        public static final int TOTAL_SIZE = 0x7FFFFFFF >> 2 >> N;
        private static final Object[] STACK = new Object[TOTAL_SIZE - SP_BOTTOM];
        private static final Object[] HEAP = new Object[SP_BOTTOM];
        public static int SP = 0;
        public static int GP = 0;

        // private MemSimulator() {
        // }

        public static int GET_OFF(int off) {
            off = off / 4;
            if (off >= SP_BOTTOM) {
                off = TOTAL_SIZE - off;
            }
            return off;
        }

        public static Object GET_MEM_WITH_OFF(int off) {
            off = off / 4;
            assert 0 <= off;
            if (off >= SP_BOTTOM) {
                off = TOTAL_SIZE - off;
                return STACK[off];
            }
            return HEAP[off];
        }

        public static void SET_MEM_VAL_WITH_OFF(Object val, int off) {
            off = off / 4;
            assert 0 <= off;
            if (off >= SP_BOTTOM) {
                off = off - SP_BOTTOM;
                STACK[off] = val;
            }
            HEAP[off] = val;
        }
    }

    private static class RegSimulator {
        private RegSimulator() {
        }

        public static final RegSimulator REG_SIMULATOR = new RegSimulator();
        public static final ArrayList<Integer> GPRS = new ArrayList<>();
        public static final ArrayList<Float> FPRS = new ArrayList<>();
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

    private static final boolean IDEA_MODE = true;
    // private static final StringBuilder sbd = new StringBuilder();

    public void output(String str) {
        if (IDEA_MODE) {
            sb.append(str);
        }
        System.out.println(str);
    }

    public void finalOut() {
        if (IDEA_MODE)
            FileDealer.outputToFile(sb, "output.txt");
    }

    public void run() throws IOException {
        MI_DESCRIPTOR.getStdin();
        Machine.Program p = Machine.Program.PROGRAM;
        SET_VAL_FROM_OPD(MemSimulator.TOTAL_SIZE * 4, Arm.Reg.getR(sp));
        int curOff = 0;
        for (Map.Entry<GlobalVal.GlobalValue, Arm.Glob> g : CodeGen.CODEGEN.globptr2globOpd.entrySet()) {
            GlobalVal.GlobalValue glob = g.getKey();
            Initial init = glob.initial;
            globName2HeapOff.put(glob.name, curOff);
            assert glob.getType().isPointerType();
            Type type = ((Type.PointerType) glob.getType()).getInnerType();
            if (type.isBasicType()) {
                logOut(glob.name + ":" + "[" + curOff + "]" + init.getFlattenInit().get(0));
                setMemValWithOffSet(((Constant) init.getFlattenInit().get(0)).getConstVal(), curOff);
                curOff += 4;
            } else {
                assert type.isArrType();
                int idx = 0;
                for (Value v : init.getFlattenInit()) {
                    logOut(glob.name + "[" + idx++ + "]" + ":" + "[" + curOff + "]" + ((Constant) v).getConstVal());
                    setMemValWithOffSet(((Constant) v).getConstVal(), curOff);
                    curOff += 4;
                }
                assert true;
                // curOff += 4 * ((Type.ArrayType) type).getFlattenSize();
            }
            // System.err.println(1);
        }
        for (Machine.McFunction mf : p.funcList) {
            mf2curVRListMap.put(mf, new Stack<>());
        }
        runMF(p.mainMcFunc);
        output(getFromReg(r0).toString());
        finalOut();
    }

    public void runMF(Machine.McFunction mcFunc) {
        if (mcFunc.mFunc.isExternal) {
            dealExternalFunc();
            return;
        }
        curMF = mcFunc;
        logOut("@now:\t" + mcFunc.mFunc.getName());
        int spVal = (int) GET_VAL_FROM_OPD(Arm.Reg.getR(sp));
        SET_VAL_FROM_OPD(spVal - curMF.getStackSize(), Arm.Reg.getR(sp));
        curVRList = new ArrayList<>(Collections.nCopies(curMF.vrList.size(), 0));
        Machine.Block mb = curMF.getBeginMB();
        // 不这么run会爆栈
        while (mb != null) {
            mb = runMB(mb);
        }
    }

    private void vrListStackPush() {
        Stack<ArrayList<Object>> stack = mf2curVRListMap.get(curMF);
        stack.push(curVRList);
    }

    private ArrayList<Object> vrListStackPop() {
        Stack<ArrayList<Object>> stack = mf2curVRListMap.get(curMF);
        return stack.pop();
    }

    private void dealExternalFunc() {
        if (curMF.mFunc.getName().equals("getint")) {
            String s = inputList.poll();
            assert s != null;
            logOut(s);
            int i = Integer.parseInt(s);
            setToReg(i, r0);
        } else if (curMF.mFunc.getName().equals("putint")) {
            output(getFromReg(r0).toString());
        }
    }

    private Machine.Block runMB(Machine.Block mb) {
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
                    if (!(mi instanceof MILoad)) {
                        throw new AssertionError("Not MILoad: " + mi);
                    }
                    // assert mi instanceof MILoad;
                    MILoad load = (MILoad) mi;
                    Object tmp = GET_VAL_FROM_OPD(load.getOffset());
                    assert tmp instanceof Integer;
                    int offset = (int) tmp;
                    offset = offset << load.getShift().getShift();
                    tmp = GET_VAL_FROM_OPD(load.getAddr());
                    if (tmp instanceof Integer) {
                        offset += (int) tmp;
                    } else {
                        assert tmp instanceof String;
                        String globAddr = (String) tmp;
                        offset += globName2HeapOff.get(globAddr);
                    }
                    SET_VAL_FROM_OPD(getMemValWithOffset(offset), load.getData());
                }
                case Store -> {
                    assert mi instanceof MIStore;
                    MIStore store = (MIStore) mi;
                    Object tmp = GET_VAL_FROM_OPD(store.getOffset());
                    assert tmp instanceof Integer;
                    int offset = (int) tmp;
                    offset = offset << store.getShift().getShift();
                    tmp = GET_VAL_FROM_OPD(store.getAddr());
                    if (tmp instanceof Integer) {
                        offset += (int) tmp;
                    } else {
                        assert tmp instanceof String;
                        String globAddr = (String) tmp;
                        offset += globName2HeapOff.get(globAddr);
                    }
                    tmp = GET_VAL_FROM_OPD(store.getData());
                    assert tmp instanceof Float || tmp instanceof Integer;
                    // // TODO 目前不知道怎么把十进制的int转成float, 理论上前端应该插了转化?
                    setMemValWithOffSet(tmp, offset);
                }
                case Compare -> {
                    REG_SIM.CMP_STATUS = 0;
                    assert mi instanceof MICompare;
                    MICompare cmp = (MICompare) mi;
                    Object lTmp = GET_VAL_FROM_OPD(cmp.getLOpd());
                    Object rTmp = GET_VAL_FROM_OPD(cmp.getROpd());
                    if (!(lTmp instanceof Integer && rTmp instanceof Integer)) {
                        assert false;
                    }
                    // assert lTmp instanceof Integer && rTmp instanceof Integer;
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
                    vrListStackPush();
                    runMF(((MICall) mi).mcFunction);
                    vrListStackPop();
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
            return nextMB;
            // runMB(nextMB);
        }
        return null;
    }

    private HashMap<String, Integer> globName2HeapOff = new HashMap<>();

    private void setMemValWithOffSet(Object val, String globAddr) {
        int offset = globName2HeapOff.get(globAddr);
        MemSimulator.SET_MEM_VAL_WITH_OFF(val, offset);
    }

    private Object getMemValWithOffset(String globAddr) {
        int offset = globName2HeapOff.get(globAddr);
        return MemSimulator.GET_MEM_WITH_OFF(offset);
    }

    private Object getMemValWithOffset(int offset) {
        return MemSimulator.GET_MEM_WITH_OFF(offset);
    }

    private void setMemValWithOffSet(Object val, int offset) {
        MemSimulator.SET_MEM_VAL_WITH_OFF(val, offset);
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
            case Immediate -> o.isGlobPtr() ? o.getGlob() : o.getImm();
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
}
