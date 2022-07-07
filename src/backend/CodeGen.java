package backend;

import lir.Machine;
import mir.Function;

public class CodeGen {

    private static Machine.Function curMachineFunc;
    private static Function curFunc;
    // private

    public CodeGen() {
        // Manager
    }

    public void gen() {

    }

    boolean immCanCode(int imm) {
        int encoding = imm;
        for (int i = 0; i < 32; i += 2) {
            if ((encoding & ~((-1) >>> 24)) != 0) {
                return true;
            }
            encoding = (encoding << 2) | (encoding >> 30);
        }
        return false;
    }
}
