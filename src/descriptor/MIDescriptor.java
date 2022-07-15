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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

import static lir.Arm.Regs.FPRs.s0;
import static lir.Arm.Regs.GPRs.*;
import static manage.Manager.ExternFunction.*;

public class MIDescriptor implements Descriptor {

    private InputStream input = System.in;
    private OutputStream output = System.out;

    public void setInput(InputStream in) {
        this.input = in;
    }

    public void setOutput(OutputStream out) {
        this.output = out;
    }

    private static class MemSimulator {
        // public static final MemSimulator MEM_SIMULATOR = new MemSimulator();
        private static final int N = 10;
        public static final int SP_BOTTOM = 0x40000000 >> 2 >> N;
        public static final int TOTAL_SIZE = 0x7FFFFFFF >> 2 >> N;
        private static final Object[] STACK = new Object[TOTAL_SIZE - SP_BOTTOM];
        private static final Object[] HEAP = new Object[SP_BOTTOM];

        public static Object GET_MEM_WITH_OFF(int off) {
            off = off / 4;
            assert 0 <= off;
            if (off >= SP_BOTTOM) {
                off = TOTAL_SIZE - off;
                Object val = STACK[off];
                logOut("! GET\t" + val + "\tfrom\tSTACK+\t" + off);
                return val;
            }
            Object val = HEAP[off];
            logOut("! GET\t" + val + "\tfrom\tHEAP+\t" + off);
            return val;
        }

        public static void SET_MEM_VAL_WITH_OFF(Object val, int off) {
            off = off / 4;
            assert 0 <= off;
            if (off >= SP_BOTTOM) {
                off = off - SP_BOTTOM;
                logOut("! SET\t" + val + "\tto\t\tSTACK+\t" + off);
                STACK[off] = val;
                return;
            }
            logOut("! SET\t" + val + "\tto\t\tHEAP+\t" + off);
            HEAP[off] = val;
        }
    }

    private static class RegSimulator {
        private RegSimulator() {
        }

        public static final RegSimulator REG_SIMULATOR = new RegSimulator();
        public static ArrayList<Integer> GPRS = new ArrayList<>();
        public static ArrayList<Float> FPRS = new ArrayList<>();
        public int CMP_STATUS = 0;

    }

    private int getGPRVal(int i) {
        return RegSimulator.GPRS.get(i);
    }

    private void setGPRVal(int i, int val) {
        RegSimulator.GPRS.set(i, val);
    }

    private float getFPRVal(int i) {
        return RegSimulator.FPRS.get(i);
    }


    private void setFPRVal(int i, float val) {
        RegSimulator.FPRS.set(i, val);
    }

    public static final MIDescriptor MI_DESCRIPTOR = new MIDescriptor();
    Scanner scanner/* = new Scanner(System.in)*/;
    // BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
    // BufferedInputStream stdin;
    // private MemSimulator MEM_SIM = MemSimulator.MEM_SIMULATOR;
    private final RegSimulator REG_SIM = RegSimulator.REG_SIMULATOR;
    // Machine.Program PROGRAM = Machine.Program.PROGRAM;
    static StringBuilder out = new StringBuilder();
    static boolean endsWithLF = true; // 当前 out 的最后是否以空行结尾, 初始状态为 true

    // enum RunningState {
    //     BEFORE_MODE,//刚生成代码
    //     AFTER_MODE,//分配完所有寄存器
    //     MIX_MODE
    // }

    private static StringBuilder err = new StringBuilder();
    private static final boolean OUT_TO_FILE = true;
    // private static final StringBuilder sbd = new StringBuilder();

    long startTime = 0;
    long endTime = 0;
    long totalTime = 0;
    boolean inTimeCul = false;

    public final int CPSR_N = 1 << 31;
    public final int CPSR_Z = 1 << 30;
    private HashMap<Machine.McFunction, Stack<ArrayList<Object>>> mf2curVRListMap = new HashMap<>();
    private ArrayList<Object> curVRList;
    private Machine.McFunction curMF;
    private Machine.Block curMB;
    private MachineInst curMI;

    private void timeClear() {
        totalTime += endTime - startTime;
        startTime = 0;
        endTime = 0;
    }


    @Override
    public StringBuilder getOutput() {
        return out;
    }

    public static void logOut(String s) {
        if (OUT_TO_FILE) {
            err.append(s).append("\n");
        } else {
            System.err.println(s);
        }
    }

    public static void output(String str) {
        if (OUT_TO_FILE) {
            out.append(str);
        } else {
            System.out.println(str);
        }
        endsWithLF = str.endsWith("\n");
    }

    public static void outputWithNewline(String str) {
        if (!endsWithLF) {
            output("\n");
        }
        output(str);
    }

    public static int outputTimes = 0;

    public void finalOut() {
        if (OUT_TO_FILE) {
            FileDealer.outputToStream(out, output);
            FileDealer.outputToFile(err, "stderr" + outputTimes++ + ".txt");
        }
    }

    public void clear() {
        totalTime = 0;
        startTime = 0;
        endTime = 0;
        // scanner = new Scanner(System.in);
        scanner = new Scanner(FileDealer.getNewBufferedInputStream(input));
        out = new StringBuilder();
        err = new StringBuilder();
        mf2curVRListMap = new HashMap<>();
        Arrays.fill(MemSimulator.STACK, null);
        Arrays.fill(MemSimulator.HEAP, null);
        RegSimulator.GPRS = new ArrayList<>();
        RegSimulator.FPRS = new ArrayList<>();
        for (int i = 0; i < Arm.Regs.GPRs.values().length; i++) {
            RegSimulator.GPRS.add(0);
        }
        for (int i = 0; i < Arm.Regs.FPRs.values().length; i++) {
            RegSimulator.FPRS.add((float) 0.0);
        }
        globName2HeapOff = new HashMap<>();
    }


    public void run() throws IOException {
        clear();
        // MI_DESCRIPTOR.getStdin();
        Machine.Program p = Machine.Program.PROGRAM;
        setToReg(MemSimulator.TOTAL_SIZE * 4, sp);
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
        outputWithNewline(getFromReg(r0).toString()); // 如果正常 stdout 的最后一行没有换行，需要先添加换行再输出返回值
        finalOut();
    }

    public void runMF(Machine.McFunction mcFunc) {
        curMF = mcFunc;
        logOut("&<runMF>& now:\t" + mcFunc.mFunc.getName());
        if (mcFunc.mFunc.isExternal) {
            dealExternalFunc();
            return;
        }
        int spVal = (int) getFromReg(sp);
        setToReg(spVal - curMF.getStackSize(), sp);
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

    private void vrListStackPop() {
        Stack<ArrayList<Object>> stack = mf2curVRListMap.get(curMF);
        curVRList = stack.pop();
    }

    private void dealExternalFunc() {
        if (curMF.mFunc.equals(GET_INT)) {
            if (!scanner.hasNext()) {
                throw new AssertionError("Can't get input when getint" + curMF);
            }
            String s = scanner.next();
            assert s != null;
            logOut(s);
            int i = Integer.parseInt(s);
            setToReg(i, r0);
        } else if (curMF.mFunc.equals(GET_CH)) {
            if (!scanner.hasNext()) {
                throw new AssertionError("Can't get input when getch" + curMF);
            }
            String s = scanner.next();
            assert s != null && s.length() == 1;
            logOut(s);
            int i = s.charAt(0);
            setToReg(i, r0);
        } else if (curMF.mFunc.equals(GET_ARR)) {
            String s = scanner.next();
            int cnt = Integer.parseInt(s);
            int baseOff = (int) getFromReg(r0);
            for (int i = 0; i < cnt; i++) {
                s = scanner.next();
                int val = Integer.parseInt(s);
                setMemValWithOffSet(val, baseOff + i * 4);
            }
        } else if (curMF.mFunc.equals(GET_FARR)) {
            String s = scanner.next();
            int cnt = Integer.parseInt(s);
            int baseOff = (int) getFromReg(s0);
            for (int i = 0; i < cnt; i++) {
                s = scanner.next();
                float val = Float.parseFloat(s);
                setMemValWithOffSet(val, baseOff + i * 4);
            }
        } else if (curMF.mFunc.equals(PUT_INT)) {
            output(getFromReg(r0).toString());
        } else if (curMF.mFunc.equals(PUT_CH)) {
            output(Character.toString((char) (int) getFromReg(r0)));
        } else if (curMF.mFunc.equals(PUT_FLOAT)) {
            float value = Float.intBitsToFloat((int) getFromReg(r0));
            output(Float.toString(value));
        } else if (curMF.mFunc.equals(PUT_ARR)) {
            int cnt = (int) getFromReg(r0);
            int baseOff = (int) getFromReg(r1);
            for (int i = 0; i < cnt; i++) {
                Object val = getMemValWithOffset(baseOff + i * 4);
                assert val instanceof Integer;
                output(val.toString());
            }
        } else if (curMF.mFunc.equals(PUT_FARR)) {
            int cnt = (int) getFromReg(r0);
            int baseOff = (int) getFromReg(r1);
            for (int i = 0; i < cnt; i++) {
                Object val = getMemValWithOffset(baseOff + i * 4);
                assert val instanceof Float;
                output(val.toString());
            }
        } else if (curMF.mFunc.equals(START_TIME)) {
            inTimeCul = true;
            startTime = System.currentTimeMillis();
        } else if (curMF.mFunc.equals(STOP_TIME)) {
            inTimeCul = false;
            endTime = System.currentTimeMillis();
            timeClear();
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
                    // 函数传参的时候, 修栈偏移
                    if (load.isNeedFix()) {
                        offset = offset + curMF.getStackSize();
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
                    Machine.McFunction tmp = curMF;
                    runMF(((MICall) mi).mcFunction);
                    curMF = tmp;
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

    // public void getStdin() {
    //     while (scanner.hasNext()) {
    //         inputList.offer(scanner.nextLine());
    //     }
    // }

    //设为Object是为了保证int和float的兼容性
    // 可能返回int或者float或者String(glob地址)
    private Object GET_VAL_FROM_OPD(Machine.Operand o) {
        Object val = switch (o.getType()) {
            case PreColored, Allocated -> getFromReg(o.getReg());
            case Virtual -> curVRList.get(o.getValue());
            case Immediate -> o.isGlobPtr() ? o.getGlob() : o.getImm();
        };
        logOut("^ get\t" + val + "\tfrom\t" + o);
        return val;
    }

    //设为Object是为了保证int和float的兼容性
    private void SET_VAL_FROM_OPD(Object val, Machine.Operand o) {
        logOut("^ set\t" + val + "\tto\t\t" + o);
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
