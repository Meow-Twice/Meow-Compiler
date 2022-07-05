package midend;

import frontend.semantic.Initial;
import mir.*;
import mir.type.Type;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GlobalValueLocalize {
    Map<Value, Initial> globalValues;

    public GlobalValueLocalize(Map<Value, Initial> globalValues) {
        this.globalValues = globalValues;
    }

    public void Run() {
        local();
    }

    private void local() {
        for (Map.Entry<Value, Initial> entry : globalValues.entrySet()) {
            Value value = entry.getKey();
            assert entry.getKey() instanceof GlobalVal.GlobalValue;
            GlobalVal.GlobalValue g = (GlobalVal.GlobalValue) value;
            Use use = g.getBeginUse();
            boolean flag = false;
            HashSet<Function> functions = new HashSet<>();
            Function function = null;
            while (use.hasNext() && use.getNext().hasNext()) {
                assert (use.getUser() != null);
                if (!functions.add(use.getUser().bb.getFunction())) {
                    flag = true;
                    break;
                }
                function = use.getUser().bb.getFunction();
            }
            if(!flag){
                assert function != null;
                Type innerType = ((Type.PointerType) g.getType()).getInnerType();
                if(innerType instanceof Type.BasicType){
                    Value alloc = new Instr.Alloc(innerType, function.entry);
                    new Instr.Store();
                    g.modifyAllUseThisToUseA(alloc);
                }

            }
        }
    }


}
