package backend;

import lir.Arm;
import lir.Machine;
import lir.Machine.Operand;

import java.util.Objects;

import static lir.Arm.Regs.GPRs.sp;

public class RegAllocator {
    protected final Arm.Reg rSP = Arm.Reg.getR(sp);

    public static class AdjPair {
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

}
