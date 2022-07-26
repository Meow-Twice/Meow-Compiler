package backend;

import lir.Arm;
import lir.Machine;
import lir.Machine.Operand;
import mir.type.DataType;

import java.util.HashSet;
import java.util.Objects;

import static lir.Arm.Regs.GPRs.sp;

public class RegAllocator {
    public static final int SP_ALIGN = 2 * 4;

    protected final boolean DEBUG_STDIN_OUT = false;

    protected int RK = 12;
    // TODO 尝试将sp直接设为Allocated或者不考虑sp add或sub指令
    protected int SK = 16;
    protected DataType dataType;

    protected int SPILL_MAX_LIVE_INTERVAL;
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

}
