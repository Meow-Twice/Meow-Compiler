package midend;

import mir.Loop;
import mir.Value;

import java.util.HashMap;

public class CloneInfoMap {

    public static HashMap<Loop, Loop> loopMap = new HashMap<>();
    public static HashMap<Value, Value> valueMap = new HashMap<>();

    public static void addLoopReflect(Loop srcLoop, Loop tagLoop) {
        //assert !loopMap.containsKey(srcLoop);
        loopMap.put(srcLoop, tagLoop);
    }
//
//    public static boolean isReflected(Loop loop) {
//        return loopMap.containsKey(loop);
//    }

    public static Loop getReflectedLoop(Loop loop) {
        if (loopMap.containsKey(loop)) {
            return loopMap.get(loop);
        } else {
            return loop;
        }
    }

    public static void addValueReflect(Value src, Value tag) {
        valueMap.put(src, tag);
    }

    public static Value getReflectedValue(Value value) {
        if (valueMap.containsKey(value)) {
            return valueMap.get(value);
        }
        return value;
    }

}
