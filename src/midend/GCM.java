package midend;

import mir.Function;
import mir.Instr;

import java.util.ArrayList;

public class GCM {

    private ArrayList<Function> functions;

    public GCM(ArrayList<Function> functions) {
        this.functions = functions;
    }

    private boolean isPinned(Instr instr) {
        return instr instanceof Instr.Jump || instr instanceof Instr.Branch ||
                instr instanceof Instr.Phi || instr instanceof Instr.Return;
    }

}
