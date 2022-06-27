package midEnd;

import ir.Function;

import java.util.ArrayList;

public class TestMain {
    //fixme:test
    public static void main(String[] args) {
        ArrayList<Function> functions = new ArrayList<>();

        //TODO:前端调用方法
        MidEndRunner midEndRunner = new MidEndRunner(functions);
        midEndRunner.Run();
    }
}
