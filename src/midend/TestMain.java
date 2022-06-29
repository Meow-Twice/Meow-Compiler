package midend;

import mir.Function;

import java.util.ArrayList;

public class TestMain {
    //fixme:test
    public static void main(String[] args) {
        //input TOP TODO:修改传参格式
        ArrayList<Function> functions = new ArrayList<>();

        //TODO:前端调用方法
        MidEndRunner midEndRunner = new MidEndRunner(functions);
        midEndRunner.Run();
    }
}
