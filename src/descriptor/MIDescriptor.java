package descriptor;

import frontend.lexer.Lexer;
import frontend.semantic.Initial;
import lir.*;
import mir.Function;
import mir.GlobalVal;
import mir.type.Type;
import util.FileDealer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

import static lir.Arm.Regs.FPRs.s0;
import static lir.Arm.Regs.GPRs.*;
import static lir.BJ.*;
import static manage.Manager.ExternFunction.*;
import static mir.type.DataType.I32;

public class MIDescriptor implements Descriptor {
    private static final boolean RANDOM_MODE = true;
    private static final boolean OUT_TO_FILE = true;

    private InputStream input = System.in;
    private OutputStream output = System.out;

    public void setInput(InputStream in) {
        this.input = in;
    }

    public void setOutput(OutputStream out) {
        this.output = out;
    }

    private RunningState runningState = RunningState.BEFORE_MODE;

    public void setRegMode() {
        runningState = RunningState.AFTER_MODE;
    }

    private boolean isAfterRegAlloc() {
        return runningState == RunningState.AFTER_MODE;
    }

    private static class MemSimulator {
        // public static final MemSimulator MEM_SIMULATOR = new MemSimulator();
        private static final int N = 4;
        public static final int SP_BOTTOM = 0x40000000 >> N;
        public static final int TOTAL_SIZE = 0x7FFFFFFF >> N;
        private static final Object[] MEM = new Object[TOTAL_SIZE];
        // private static final Object[] STACK = new Object[TOTAL_SIZE - SP_BOTTOM];
        // private static final Object[] HEAP = new Object[SP_BOTTOM];

        public static Object GET_MEM_WITH_OFF(int off) {
            off = off / 4;
            assert 0 <= off;
            if (off >= TOTAL_SIZE) {
                throw new AssertionError(Integer.toHexString(off) + "\t > " + Integer.toHexString(TOTAL_SIZE));
            }
            Object val = MEM[off];
            if (off >= SP_BOTTOM) {
                logOut("! GET\t" + val + "\tfrom\tSTACK+\t0x" + Integer.toHexString(off * 4));
                if (val == null) {
                    if (RANDOM_MODE) {
                        val = random.nextInt();
                    } else {
                        val = 0;
                    }
                    // throw new AssertionError("");
                }
            } else {
                logOut("! GET\t" + val + "\tfrom\tHEAP+\t0x" + Integer.toHexString(off * 4));
                if (val == null) {
                    if (RANDOM_MODE) {
                        val = random.nextInt();
                    } else {
                        val = 0;
                    }
                    // throw new AssertionError("");
                }

            }
            return val;
            // Object val = HEAP[off];
            // if (val == null) {
            //     throw new AssertionError("");
            // }
            // return val;
        }

        public static void SET_MEM_VAL_WITH_OFF(Object val, int off) {
            off = off / 4;
            if (off < 0) {
                throw new AssertionError(Integer.toHexString(off) + "\t < 0");
            }
            if (off >= TOTAL_SIZE) {
                throw new AssertionError(Integer.toHexString(off) + "\t > " + Integer.toHexString(TOTAL_SIZE));
            }
            boolean flag = false;
            if (val == null) {
                flag = true;
                if (RANDOM_MODE) {
                    val = random.nextInt();
                } else {
                    val = 0;
                }
            }
            MEM[off] = val;
            if (off >= SP_BOTTOM) {
                logOut("! SET\t" + val + "\tto\t\tSTACK+\t0x" + Integer.toHexString(off * 4));
                if (flag) {
                    // throw new AssertionError("");
                }
                //     STACK[off] = val;
            } else {
                logOut("! SET\t" + val + "\tto\t\tHEAP+\t0x" + Integer.toHexString(off * 4));
                if (flag) {
                    // throw new AssertionError("");
                }
                // HEAP[off] = val;
            }
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
    // Scanner scanner/* = new Scanner(System.in)*/;
    // BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
    // BufferedInputStream stdin;
    BufferedInputStream bufferedInputStream;
    // private MemSimulator MEM_SIM = MemSimulator.MEM_SIMULATOR;
    private final RegSimulator REG_SIM = RegSimulator.REG_SIMULATOR;
    // Machine.Program PROGRAM = Machine.Program.PROGRAM;
    static StringBuilder out = new StringBuilder();
    static boolean endsWithLF = true; // 当前 out 的最后是否以空行结尾, 初始状态为 true

    enum RunningState {
        BEFORE_MODE,//刚生成代码
        AFTER_MODE,//分配完所有寄存器
        MIX_MODE
    }

    private static StringBuilder err = new StringBuilder();
    // private static final StringBuilder sbd = new StringBuilder();

    long startTime = 0;
    long endTime = 0;
    long totalTime = 0;
    boolean inTimeCul = false;

    public final int CPSR_N = 1 << 31;
    public final int CPSR_Z = 1 << 30;
    private HashMap<MC.McFunction, Stack<ArrayList<Object>>> mf2curVRListMap = new HashMap<>();
    private ArrayList<Object> curVRList;
    private MC.McFunction curMF;
    private MC.Block curMB;
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
            FileDealer.outputToFile(err, "stderr.out" + outputTimes++ + ".txt");
        }
    }

    public void clear() {
        totalTime = 0;
        startTime = 0;
        endTime = 0;
        // scanner = new Scanner(System.in);
        bufferedInputStream = new BufferedInputStream(input);
        // scanner = new Scanner(FileDealer.getNewBufferedInputStream(input));
        out = new StringBuilder();
        err = new StringBuilder();
        mf2curVRListMap = new HashMap<>();
        if (runningState == RunningState.AFTER_MODE) {
            curMF2GPRs = new HashMap<>();
            curMF2FPRs = new HashMap<>();
        }
        Arrays.fill(MemSimulator.MEM, null);
        // Arrays.fill(MemSimulator.STACK, null);
        // Arrays.fill(MemSimulator.HEAP, null);
        RegSimulator.GPRS = new ArrayList<>();
        RegSimulator.FPRS = new ArrayList<>();
        for (int i = 0; i < GPRs.values().length; i++) {
            RegSimulator.GPRS.add(RANDOM_MODE ? random.nextInt() : 0);
        }
        for (int i = 0; i < FPRs.values().length; i++) {
            RegSimulator.FPRS.add(RANDOM_MODE ? random.nextFloat() : (float) 0.0);
        }
        globName2HeapOff = new HashMap<>();
    }


    /**
     * getFlattenInit 不能用了，所以这个 run 函数现在是错的
     */
    public void run() throws IOException {
        clear();
        // MI_DESCRIPTOR.getStdin();
        MC.Program p = MC.Program.PROGRAM;
        setToReg((MemSimulator.TOTAL_SIZE - 1) * 4, sp);
        int curOff = 0;
        for (Arm.Glob g : p.globList) {
            GlobalVal.GlobalValue glob = g.getGlobalValue();
            Initial init = g.getInit();
            globName2HeapOff.put(glob.name, curOff);
            assert glob.getType().isPointerType();
            Type type = ((Type.PointerType) glob.getType()).getInnerType();
            if (type.isBasicType()) {
                // logOut(glob.name + ":" + "[" + curOff + "]" + init.getFlattenInit().get(0));
                // setMemValWithOffSet(((Constant) init.getFlattenInit().get(0)).getConstVal(), curOff);
                curOff += 4;
            } else {
                assert type.isArrType();
                int idx = 0;
//                for (Value v : init.getFlattenInit()) {
//                    logOut(glob.name + "[" + idx++ + "]" + ":" + "[" + curOff + "]" + ((Constant) v).getConstVal());
//                    setMemValWithOffSet(((Constant) v).getConstVal(), curOff);
//                    curOff += 4;
//                }
                assert true;
                // curOff += 4 * ((Type.ArrayType) type).getFlattenSize();
            }
            // System.err.println(1);
        }
        for (MC.McFunction mf : p.funcList) {
            mf2curVRListMap.put(mf, new Stack<>());
            curMF2GPRs.put(mf, new Stack<>());
            curMF2FPRs.put(mf, new Stack<>());
        }
        runMF(p.mainMcFunc);
        outputWithNewline(String.valueOf(((int) getFromReg(r0)) & 255)); // 如果正常 stdout 的最后一行没有换行，需要先添加换行再输出返回值
        finalOut();
    }

    public void runMF(MC.McFunction mcFunc) {
        curMF = mcFunc;
        logOut("&<runMF>& now:\t" + mcFunc.mFunc.getName());
        if (mcFunc.mFunc.isExternal) {
            dealExternalFunc();
            return;
        }
        if (runningState == RunningState.AFTER_MODE) {
            // push();
        }
        // int spVal = (int) getFromReg(sp);
        // setToReg(spVal - curMF.getStackSize(), sp);
        curVRList = new ArrayList<>(Collections.nCopies(curMF.vrList.size(), null));
        MC.Block mb = curMF.getBeginMB();
        // 不这么run会爆栈
        while (mb != null) {
            mb = runMB(mb);
        }
    }

    private void push() {

        if (runningState == RunningState.AFTER_MODE) {
            // push
            List<GPRs> usedRegList = curMF.getUsedRegList();
            Collections.reverse(usedRegList);
            int firstSp = (int) getFromReg(GPRs.sp);
            for (GPRs gpr : usedRegList) {
                int sp = (int) getFromReg(GPRs.sp);
                sp -= 4;
                setToReg(sp, GPRs.sp);
                setMemValWithOffSet(getFromReg(gpr), sp);
            }
            int pushSize = firstSp - (int) getFromReg(GPRs.sp);
            // System.err.println(pushSize);
        }
    }

    private void pop() {
        if (runningState == RunningState.AFTER_MODE) {
            // pop
            int firstSp = (int) getFromReg(GPRs.sp);
            for (GPRs gpr : curMF.getUsedRegList()) {
                int sp = (int) getFromReg(GPRs.sp);
                RegSimulator.GPRS.set(gpr.ordinal(), (int) getMemValWithOffset(sp));
                sp += 4;
                setToReg(sp, GPRs.sp);
            }
            int pushSize = firstSp - (int) getFromReg(GPRs.sp);
            // System.err.println(pushSize);
        }
    }

    private HashMap<MC.McFunction, Stack<ArrayList<Integer>>> curMF2GPRs = new HashMap<>();

    private HashMap<MC.McFunction, Stack<ArrayList<Float>>> curMF2FPRs = new HashMap<>();

    private void vrListStackPush() {
        Stack<ArrayList<Object>> stack = mf2curVRListMap.get(curMF);
        stack.push(curVRList);
    }

    private void vrListStackPop() {
        Stack<ArrayList<Object>> stack = mf2curVRListMap.get(curMF);
        curVRList = stack.pop();
    }

    private String genStr(String funcName) {

        StringBuilder sb = new StringBuilder();
        int c = Lexer.getInstance().myGetc(bufferedInputStream);
        if (c == -1) {
            throw new AssertionError("Can't get input when " + funcName + curMF);
        }
        while (c == (int) '\r' || c == (int) '\n' || c == (int) ' ' || c == '\t') {
            c = Lexer.getInstance().myGetc(bufferedInputStream);
        }
        while (c != -1 && c != (int) '\r' && c != (int) '\n' && c != (int) ' ' && c != '\t') {
            sb.append((char) c);
            c = Lexer.getInstance().myGetc(bufferedInputStream);
        }
        return sb.toString();
    }

    private void dealExternalFunc() {
        Function func = curMF.mFunc;
        if (func.equals(GET_INT)) {
            String s = genStr(func.getName());
            logOut(s);
            int i = Integer.parseInt(s);
            setToReg(i, r0);
        } else if (func.equals(GET_CH)) {
            // Scanner scanner = new Scanner(bufferedInputStream);

            int c = Lexer.getInstance().myGetc(bufferedInputStream);
            if (c == -1) {
                throw new AssertionError("Can't get input when getch\t" + curMF);
            }
            setToReg(c, r0);
        } else if (func.equals(GET_ARR)) {
            String s = genStr(func.getName());
            int cnt = Integer.parseInt(s);
            int baseOff = (int) getFromReg(r0);
            for (int i = 0; i < cnt; i++) {
                s = genStr(func.getName());
                int val = Integer.parseInt(s);
                setMemValWithOffSet(val, baseOff + i * 4);
            }
            setToReg(cnt, r0);
        } else if (func.equals(GET_FARR)) {
            String s = genStr(func.getName());
            int cnt = Integer.parseInt(s);
            int baseOff = (int) getFromReg(s0);
            for (int i = 0; i < cnt; i++) {
                s = genStr(func.getName());
                float val = Float.parseFloat(s);
                setMemValWithOffSet(val, baseOff + i * 4);
            }
            setToReg(cnt, r0);
        } else if (func.equals(PUT_INT)) {
            output(getFromReg(r0).toString());
        } else if (func.equals(PUT_CH)) {
            output(Character.toString((char) (int) getFromReg(r0)));
        } else if (func.equals(PUT_FLOAT)) {
            float value = Float.intBitsToFloat((int) getFromReg(r0));
            output(Float.toString(value));
        } else if (func.equals(PUT_ARR)) {
            int cnt = (int) getFromReg(r0);
            output(cnt + ":");
            int baseOff = (int) getFromReg(r1);
            for (int i = 0; i < cnt; i++) {
                Object val = getMemValWithOffset(baseOff + i * 4);
                assert val instanceof Integer;
                output(" " + val);
            }
        } else if (func.equals(PUT_FARR)) {
            int cnt = (int) getFromReg(r0);
            output(cnt + ":");
            int baseOff = (int) getFromReg(r1);
            for (int i = 0; i < cnt; i++) {
                Object val = getMemValWithOffset(baseOff + i * 4);
                assert val instanceof Float;
                output(" " + val);
            }
        } else if (func.equals(START_TIME)) {
            inTimeCul = true;
            startTime = System.currentTimeMillis();
        } else if (func.equals(STOP_TIME)) {
            inTimeCul = false;
            endTime = System.currentTimeMillis();
            timeClear();
        } else if (func.equals(MEM_SET)) {
            int baseOff = (int) getFromReg(r0);
            int ele = (int) getFromReg(r1);
            int size = (int) getFromReg(r2);
            for (int i = 0; i < size; i += 4) {
                setMemValWithOffSet(ele, baseOff + i);
            }
        }
    }

    private MC.Block runMB(MC.Block mb) {
        curMB = mb;
        logOut("");
        logOut(mb.getLabel());
        boolean isBJ = false;
        boolean isRet = false;
        MC.Block nextMB = null;
        for (MachineInst mi : mb.miList) {
            String str = mi instanceof MIComment ? "" : "\t";
            logOut(str + mi);
            if (mi.getCond() != Arm.Cond.Any && !mi.isBranch() && !satisfyCond(mi.getCond())) {
                logOut("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                continue;
            }
            curMI = mi;
            I.Binary miBinary = null;
            // MILongMul miLongMul = null;
            Object lVal = null;
            Object rVal = null;
            if (curMI instanceof I.Binary) {
                miBinary = (I.Binary) curMI;
                lVal = GET_VAL_FROM_OPD(miBinary.getLOpd());
                rVal = GET_VAL_FROM_OPD(miBinary.getROpd());
            }
            // if(curMI instanceof MILongMul){
            //     miLongMul = (MILongMul) curMI;
            //     lVal = GET_VAL_FROM_OPD(miLongMul.getLOpd());
            //     rVal = GET_VAL_FROM_OPD(miLongMul.getROpd());
            // }
            switch (curMI.getTag()) {
                case Add -> {
                    assert lVal instanceof Integer && rVal instanceof Integer;
                    if (miBinary.isNeedFix()) {
                        assert false;
                        rVal = (int) rVal + switch (miBinary.getFixType()) {
                            case VAR_STACK -> curMF.getVarStack();
                            case ONLY_PARAM -> miBinary.getCallee().getParamStack();
                            default -> throw new AssertionError("");
                        };
                    }
                    SET_VAL_FROM_OPD((int) lVal + (int) rVal, miBinary.getDst());
                }
                case FAdd -> {
                    assert lVal instanceof Float && rVal instanceof Float;
                    SET_VAL_FROM_OPD((float) lVal + (float) rVal, miBinary.getDst());
                }
                case Sub -> {
                    assert lVal instanceof Integer && rVal instanceof Integer;
                    if (miBinary.isNeedFix()) {
                        assert false;
                        rVal = (int) rVal + switch (miBinary.getFixType()) {
                            case VAR_STACK -> curMF.getVarStack();
                            case ONLY_PARAM -> miBinary.getCallee().getParamStack();
                            default -> throw new AssertionError("");
                        };
                    }
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
                // case LongMul -> {
                //     assert lVal instanceof Integer && rVal instanceof Integer;
                //     SET_VAL_FROM_OPD((int) ((((Integer) lVal).longValue() *  ((Integer) rVal).longValue()) >>> 32), miLongMul.getDst());
                // }
                case FMA -> {
                    assert mi instanceof I.Fma;
                    I.Fma fma = (I.Fma) mi;
                    MC.Operand dst = fma.getDst();
                    MC.Operand acc = fma.getAcc();
                    MC.Operand lOpd = fma.getlOpd();
                    MC.Operand rOpd = fma.getrOpd();
                    int res;
                    if (fma.isSign()) {
                        res = (int) (((long) GET_VAL_FROM_OPD(lOpd) * (long) GET_VAL_FROM_OPD(rOpd)) >> 32);
                    } else {
                        res = (int) GET_VAL_FROM_OPD(lOpd) * (int) GET_VAL_FROM_OPD(rOpd);
                    }
                    if (fma.add()) {
                        res = (int) GET_VAL_FROM_OPD(acc) + res;
                    } else {
                        res = (int) GET_VAL_FROM_OPD(acc) - res;
                    }
                    SET_VAL_FROM_OPD(res, dst);
                }
                case IMov -> {
                    assert mi instanceof I.Mov;
                    I.Mov mv = (I.Mov) mi;
                    Object val = GET_VAL_FROM_OPD(mv.getSrc());
                    // 函数传参的时候, 修栈偏移
                    if (mv.isNeedFix()) {
                        assert false;
                        val = (int) val + curMF.getRegStack() + curMF.getVarStack();
                    }
                    //这里好像没考虑shift
                    if(mv.getShift()== Arm.Shift.NONE_SHIFT) {
                        SET_VAL_FROM_OPD(val, mv.getDst());
                    }
                    else if(mv.getShift().shiftType == Arm.ShiftType.Lsl){
                        int result = (int)val<<(mv.getShift().shiftOpd.getValue());
                        SET_VAL_FROM_OPD(result,mv.getDst());
                    }
                    else if(mv.getShift().shiftType == Arm.ShiftType.Asr){
                        SET_VAL_FROM_OPD((int)val>>(mv.getShift().shiftOpd.getValue()),mv.getDst());
                    }
                    else{
                        SET_VAL_FROM_OPD((int)val>>>(mv.getShift().shiftOpd.getValue()),mv.getDst());
                    }
                }
                case Branch -> {
                    isBJ = true;
                    assert mi instanceof GDBranch;
                    GDBranch br = (GDBranch) mi;
                    if (satisfyCond(br.getCond()))
                        nextMB = br.getTrueTargetBlock();
                    else
                        nextMB = br.getFalseTargetBlock();
                }
                case Jump -> {
                    isBJ = true;
                    assert mi instanceof GDJump;
                    GDJump j = (GDJump) mi;
                    nextMB = j.getTarget();
                }
                case IRet -> {
                    if (runningState == RunningState.AFTER_MODE) {
                        // pop();
                    }
                    isRet = true;
                }
                case Ldr -> {
                    if (!(mi instanceof I.Ldr)) {
                        throw new AssertionError("Not MILoad: " + mi);
                    }
                    // assert mi instanceof MILoad;
                    I.Ldr load = (I.Ldr) mi;
                    Object tmp = GET_VAL_FROM_OPD(load.getOffset());
                    assert tmp instanceof Integer;
                    int offset = (int) tmp;
                    offset = offset << load.getShift().getShiftOpd().getValue();
                    tmp = GET_VAL_FROM_OPD(load.getAddr());
                    assert tmp instanceof Integer;
                    // if (tmp instanceof Integer) {
                    offset += (int) tmp;
                    // } else {
                    //     assert tmp instanceof String;
                    //     String globAddr = (String) tmp;
                    //     offset += globName2HeapOff.get(globAddr);
                    // }
                    SET_VAL_FROM_OPD(getMemValWithOffset(offset), load.getData());
                }
                case Str -> {
                    assert mi instanceof I.Str;
                    I.Str store = (I.Str) mi;
                    Object tmp = GET_VAL_FROM_OPD(store.getOffset());
                    assert tmp instanceof Integer;
                    int offset = (int) tmp;
                    offset = offset << store.getShift().getShiftOpd().getValue();
                    tmp = GET_VAL_FROM_OPD(store.getAddr());
                    assert tmp instanceof Integer;
                    // if (tmp instanceof Integer) {
                    offset += (int) tmp;
                    // } else {
                    //     assert tmp instanceof String;
                    //     String globAddr = (String) tmp;
                    //     offset += globName2HeapOff.get(globAddr);
                    // }
                    tmp = GET_VAL_FROM_OPD(store.getData());
                    if (!(tmp instanceof Float || tmp instanceof Integer)) {
                        throw new AssertionError("GET VAL=\t(" + tmp + ")\t<-\t" + store.getData() + "\t{" + mi + "}");
                    }
                    // assert tmp instanceof Float || tmp instanceof Integer;
                    // // TODO 目前不知道怎么把十进制的int转成float, 理论上前端应该插了转化?
                    if (offset > MemSimulator.TOTAL_SIZE * 4) {
                        throw new AssertionError("");
                    }
                    setMemValWithOffSet(tmp, offset);
                }
                case ICmp -> {
                    REG_SIM.CMP_STATUS = 0;
                    assert mi instanceof I.Cmp;
                    I.Cmp cmp = (I.Cmp) mi;
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
                    MC.McFunction tmp = curMF;
                    runMF(((MICall) mi).callee);
                    curMF = tmp;
                    logOut("<--> return to " + curMF.mFunc.getName());
                    vrListStackPop();
                }
                case Global -> throw new AssertionError("not done yet");
                case Comment -> {
                }
                case Empty -> {
                }
                case Push -> {
                    push();
                }
                case Pop -> pop();
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
            case Hi, Pl -> throw new AssertionError("not done yet");
            case Any -> throw new AssertionError("Wrong cmp: " + cmp_status + " compare with " + cond);
        };
    }

    public LinkedBlockingQueue<String> inputList = new LinkedBlockingQueue<>();

    // public void getStdin() {
    //     while (scanner.hasNext()) {
    //         inputList.offer(scanner.nextLine());
    //     }
    // }

    final static int seed = 2022;
    static Random random = new Random(seed);

    // 设为Object是为了保证int和float的兼容性
    // 可能返回int或者float或者String(glob地址)
    private Object GET_VAL_FROM_OPD(MC.Operand o) {
        if (isAfterRegAlloc() && o.isVirtual(I32)) throw new AssertionError("Still has vr: " + o);
        Object val = switch (o.type) {
            case PreColored, Allocated -> getFromReg(o.getReg());
            case Virtual -> curVRList.get(o.getValue());
            case Immediate -> o.isGlobPtr() ? globName2HeapOff.get(o.getGlob()) : o.get_I_Imm();
            default -> throw new IllegalStateException("Unexpected value: " + o.type);
        };
        if (val == null) {
            if (RANDOM_MODE) {
                val = random.nextInt();
            } else {
                val = 0;
            }
            // throw new AssertionError("fuck");
        }
        // String vStr = val instanceof Integer ? Integer.toHexString((int)val) : Float.toHexString((float)val);
        logOut("^ get\t" + val + "\tfrom\t" + o);
        return val;
    }

    // 设为Object是为了保证int和float的兼容性
    private void SET_VAL_FROM_OPD(Object val, MC.Operand o) {
        if (isAfterRegAlloc() && o.isVirtual(I32)) throw new AssertionError("Still has vr: " + o);
        logOut("^ set\t" + val + "\tto\t\t" + o);
        if (val == null) {
            if (RANDOM_MODE) {
                val = random.nextInt();
            } else {
                val = 0;
            }
            // throw new AssertionError("fuck");
        }
        switch (o.type) {
            case PreColored, Allocated -> setToReg(val, o.getReg());
            case Virtual -> curVRList.set(o.getValue(), val);
            case Immediate -> throw new AssertionError("Try to save |" + val + "| to " + o);
        }
    }

    private Object getFromReg(Arm.Regs regEnum) {
        if (regEnum instanceof GPRs) {
            return getGPRVal(((GPRs) regEnum).ordinal());
        } else {
            assert regEnum instanceof FPRs;
            return getFPRVal(((FPRs) regEnum).ordinal());
        }
    }


    private void setToReg(Object obj, Arm.Regs regEnum) {
        if (regEnum instanceof GPRs) {
            assert obj instanceof Integer;
            setGPRVal(((GPRs) regEnum).ordinal(), (int) obj);
        } else {
            assert regEnum instanceof FPRs;
            assert obj instanceof Float;
            setFPRVal(((FPRs) regEnum).ordinal(), (float) obj);
        }
    }
}
