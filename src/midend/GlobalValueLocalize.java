package midend;

import frontend.semantic.Initial;
import mir.*;
import mir.type.Type;

import java.util.*;

public class GlobalValueLocalize {
    private HashMap<Value, Initial> globalValues;
    private ArrayList<Function> functions;

    public GlobalValueLocalize(ArrayList<Function> functions, HashMap<Value, Initial> globalValues) {
        this.functions = functions;
        this.globalValues = globalValues;
    }

    public void Run() {
        for (Value value: globalValues.keySet()) {
            localizeSingleValue(value);
        }
    }

    private void localizeSingleValue(Value value) {

    }



}
