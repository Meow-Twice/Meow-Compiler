package midend;

import mir.Loop;

import java.util.HashMap;

public class LoopMap {

    public static HashMap<Loop, Loop> loopMap = new HashMap<>();

    public static void addLoopReflect(Loop srcLoop, Loop tagLoop) {
        //assert !loopMap.containsKey(srcLoop);
        loopMap.put(srcLoop, tagLoop);
    }

    public static boolean isReflected(Loop loop) {
        return loopMap.containsKey(loop);
    }

    public static Loop getReflectedLoop(Loop loop) {
        return loopMap.get(loop);
    }
}
