package midend;

import lir.Arm;
import lir.MC;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;

import static lir.Arm.Regs.GPRs.sp;
import static mir.type.DataType.I32;

public class TestMain {
    public static class A implements Cloneable{
        public ArrayList<MC.Operand> opdList = new ArrayList<>();
        public HashSet<Integer> aSet = new HashSet<>();

        @Override
        public String toString() {
            return "A{" +
                    "opdList=" + opdList +
                    ", aSet=" + aSet +
                    '}';
        }

        @Override
        public A clone() {
            try {
                A clone = (A) super.clone();
                // TODO: copy mutable state here, so the clone can't change the internals of the original
                clone.opdList = new ArrayList<>(this.opdList);
                clone.aSet = new HashSet<>(this.aSet);
                return clone;
            } catch (CloneNotSupportedException e) {
                throw new AssertionError();
            }
        }
    }
    //fixme:test
    public static void main(String[] args) {
        // A a = new A();
        // a.opdList = new ArrayList<>();
        // a.opdList.add(new MC.Operand(I32, 0));
        // a.aSet.add(0);
        // System.out.println(a);
        // A b = a.clone();
        // b.aSet.add(1);
        // b.opdList.add(new MC.Operand(I32, 1));
        // System.out.println(a);
        // System.out.println(b);
        MC.Operand o = Arm.Reg.getRSReg(sp);
        System.out.println(o.reg);
    }

    // public static Value Jump(){
    //     return new Instr.Jump(new BasicBlock(), new BasicBlock());
    // }
}
