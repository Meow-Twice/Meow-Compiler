package midend;

import frontend.semantic.Initial;
import mir.*;
import mir.type.Type;

import java.util.*;

public class GlobalValueLocalize {
    private HashMap<Value, Initial> globalValues;
    private ArrayList<Function> functions;

    public GlobalValueLocalize(HashMap<Value, Initial> globalValues) {
        this.globalValues = globalValues;
    }

    public void Run() {

    }
}
