package lir;

import mir.GlobalVal;

import java.util.ArrayList;
import java.util.HashSet;

public class Machine {
    public static class Program{
        ArrayList<Function> funcList;
        ArrayList<GlobalVal> globList;

        // public Program(ArrayList<Function> funcList, ArrayList<GlobalVal> globList){
        //     this.funcList = funcList;
        //     this.globList = globList;
        // }
        public Program(){}
    }

    public static class Function {
        ArrayList<MachineInst> instList;
        ArrayList<MachineBlock> blockList;

        int virRegNum = 0;
        int stackSize = 0;
        mir.Function mFunc;
        HashSet<Arm.Reg> usedCalleeSavedRegs;
        boolean useLr = false;
        public Function(){
        }
    }
    public static class MachineBlock{
        HashSet<Operand> liveUseSet;
        HashSet<Operand> defSet;
        HashSet<Operand> liveInSet;
        HashSet<Operand> liveOutSet;
    }

    public static class Operand{
        private String prefix;
        private int id;
        public enum Type{
            PreColored,
            Allocated,
            Virtual,
            Immediate
        }

        public Operand(Type type){
            prefix = switch (type){
                case Virtual -> "v";
                case Allocated, PreColored -> "r";
                case Immediate -> "#";
            };
        }
    }
}
