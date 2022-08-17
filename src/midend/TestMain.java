package midend;

import java.util.LinkedHashSet;

public class TestMain {
    //fixme:test
    public static void main(String[] args) {
        LinkedHashSet<Integer> iSet = new LinkedHashSet<>();
        iSet.add(1);
        iSet.add(2);
        iSet.add(4);
        iSet.add(3);
        System.out.println(iSet);
        iSet.remove(4);
        System.out.println(iSet);
    }

    // public static Value Jump(){
    //     return new Instr.Jump(new BasicBlock(), new BasicBlock());
    // }
}
