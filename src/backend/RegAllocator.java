package backend;

import lir.*;
import lir.Machine.Operand;
import mir.type.DataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;

import static lir.Arm.Regs.GPRs.r11;
import static lir.Arm.Regs.GPRs.sp;
import static mir.type.DataType.I32;

public class RegAllocator {
    protected Machine.McFunction curMF;
    public static final int SP_ALIGN = 2 * 4;

    protected final boolean DEBUG_STDIN_OUT = false;

    protected int RK = 12;
    // TODO 尝试将sp直接设为Allocated或者不考虑sp add或sub指令
    protected int SK = 32;
    protected DataType dataType;

    protected int SPILL_MAX_LIVE_INTERVAL = 1;
    protected final Arm.Reg rSP = Arm.Reg.getR(sp);
    protected int MAX_DEGREE = Integer.MAX_VALUE >> 2;

    /**
     * 图中冲突边 (u, v) 的集合, 如果(u, v) in adjSet, 则(v, u) in adjSet
     * 用于判断两个Operand是否相邻
     */
    HashSet<AdjPair> adjSet = new HashSet<>();

    protected static class AdjPair {
        public Operand u;
        public Operand v;

        public AdjPair(Operand u, Operand v) {
            this.u = u;
            this.v = v;
        }

        @Override
        public int hashCode() {
            return Objects.hash(u, v);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (!(obj instanceof AdjPair)) return false;
            return (u.equals(((AdjPair) obj).u) && v.equals(((AdjPair) obj).v))
                    /*|| (u.equals(((AdjPair) obj).v) && v.equals(((AdjPair) obj).u))*/;
        }

        @Override
        public String toString() {
            return "(" + u + " ,\t" + v + ")";
        }
    }


    protected void assertDataType(Operand x) {
        assert x.isDataType(dataType);
    }

    protected void addEdge(Operand u, Operand v) {
        assertDataType(u);
        assertDataType(v);
        AdjPair adjPair = new AdjPair(u, v);
        if (!(adjSet.contains(adjPair) || u.equals(v))) {
            adjSet.add(adjPair);
            adjSet.add(new AdjPair(v, u));
            logOut("\tAddEdge: " + u + "\t,\t" + v);
            if (!u.is_I_PreColored()) {
                u.addAdj(v);
                u.degree++;
            }
            if (!v.is_I_PreColored()) {
                v.addAdj(u);
                v.degree++;
            }
        }
    }

    public void logOut(String s) {
        if (DEBUG_STDIN_OUT)
            System.err.println(s);
    }

    public void AllocateRegister(Machine.Program program) {
    }

    protected void fixStack(ArrayList<I> needFixList) {
        for (MachineInst mi : needFixList) {
            // fixStack
            Machine.McFunction mf = mi.getMb().mcFunc;
            if (mi instanceof I.Binary) {
                I.Binary binary = (I.Binary) mi;
                Machine.Operand off;
                int newOff = switch (binary.getFixType()) {
                    case VAR_STACK -> mf.getVarStack();
                    case ONLY_PARAM -> binary.getCallee().getParamStack();
                    default -> {
                        System.exit(110);
                        throw new AssertionError(binary + " of " + binary.getFixType());
                    }
                };
                if (newOff == 0) {
                    // TODO
                    binary.remove();
                } else {
                    if (CodeGen.immCanCode(newOff)) {
                        off = new Machine.Operand(I32, newOff);
                    } else {
                        off = Arm.Reg.getR(r11);
                        new I.Mov(off, new Machine.Operand(I32, newOff), binary);
                    }
                    binary.setROpd(off);
                }
                binary.clearNeedFix();
            } else if (mi.isMove()) {
                I.Mov mv = (I.Mov) mi;
                Machine.Operand off = mv.getSrc();
                assert off.is_I_Imm();
                int newOff = mf.getTotalStackSize() + off.get_I_Imm();
                // mov a, #off
                // add add, sp, a
                // vldr.32 data, [add]
                if (mv.getFixType() == CodeGen.STACK_FIX.FLOAT_TOTAL_STACK) {
                    if (CodeGen.vLdrStrImmEncode(newOff)) {
                        V.Ldr vldr = (V.Ldr) mv.getNext().getNext();
                        vldr.setOffSet(new Machine.Operand(I32, newOff));
                        assert mv.getNext() instanceof I.Binary;
                        mv.getNext().remove();
                        mv.clearNeedFix();
                        mv.remove();
                    } else if (CodeGen.immCanCode(newOff)) {
                        assert mv.getNext() instanceof I.Binary;
                        I.Binary binary = (I.Binary) mv.getNext();
                        mv.clearNeedFix();
                        mv.remove();
                        binary.setROpd(new Machine.Operand(I32, newOff));
                    } else {
                        mv.setSrc(new Machine.Operand(I32, newOff));
                        mv.clearNeedFix();
                    }
                } else if (mv.getFixType() == CodeGen.STACK_FIX.INT_TOTAL_STACK) {
                    // mov dst, offImm
                    // ldr opd, [sp, dst]
                    if (CodeGen.LdrStrImmEncode(newOff)) {
                        I.Ldr ldr = (I.Ldr) mv.getNext();
                        ldr.setOffSet(new Machine.Operand(I32, newOff));
                        mv.clearNeedFix();
                        mv.remove();
                    } else {
                        mv.setSrc(new Machine.Operand(I32, newOff));
                        mv.clearNeedFix();
                    }
                } else {
                    System.exit(120);
                    throw new AssertionError(mv + " of " + mv.getFixType());
                }
            } else {
                System.exit(120);
                throw new AssertionError(mi + " of " + mi.getFixType());
            }
        }
    }
}
