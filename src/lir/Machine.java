package lir;

import mir.GlobalVal;
import mir.type.DataType;
import util.DoublelyLinkedList;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashSet;

public class Machine {
    public static String BB_Prefix = ".L_BB_";
    public static class Program {
        ArrayList<Function> funcList;
        ArrayList<GlobalVal> globList;

        // public Program(ArrayList<Function> funcList, ArrayList<GlobalVal> globList){
        //     this.funcList = funcList;
        //     this.globList = globList;
        // }
        public Program() {
        }
    }

    public static class Function {
        ArrayList<MachineInst> instList;
        ArrayList<Block> blockList;
        ArrayList<Operand> params;
        String func_name;
        int virRegNum = 0;
        int stackSize = 0;
        mir.Function mFunc;
        HashSet<Arm.Reg> usedCalleeSavedRegs;
        boolean useLr = false;

        public Function() {
        }
    }

    public static class Block {
        int index;
        DoublelyLinkedList<MachineInst> insts;
        //pred and successor
        ArrayList<Block> pred;
        ArrayList<Block> successor;
        MachineInst con_tran = null;
        HashSet<Operand> liveUseSet;
        HashSet<Operand> defSet;
        HashSet<Operand> liveInSet;
        HashSet<Operand> liveOutSet;
        public String toString(){
            return BB_Prefix+index;
        }

    }

    public static class Operand {
        String prefix;
        int id;

        public enum Type {
            PreColored,
            Allocated,
            Virtual,
            Immediate
        }

        Type type;
        DataType dataType = DataType.I32;

        public Type getType() {
            return type;
        }

        // 默认分配通用寄存器
        public Operand(Type type) {
            this.type = type;
            prefix = switch (type) {
                case Virtual -> "v";
                case Allocated, PreColored -> "r";
                case Immediate -> "#";
            };
        }

        public Operand(Type type, DataType dataType) {
            this.type = type;
            prefix = switch (type) {
                case Virtual -> "v";
                case Allocated, PreColored -> switch (dataType) {
                    case F32 -> "s";
                    case I32 -> "r";
                    default -> throw new IllegalStateException("Unexpected reg type: " + dataType);
                };
                case Immediate -> "#";
            };
        }

        public Operand(Type type, Arm.Reg reg) {
            this.type = type;
            prefix = switch (type) {
                case Virtual -> "v";
                case Allocated, PreColored -> switch (reg.dataType) {
                    case F32 -> "s";
                    case I32 -> "r";
                    default -> throw new IllegalStateException("Unexpected reg type: " + reg.dataType);
                };
                case Immediate -> "#";
            };
        }

        public boolean compareTo(Operand other) {
            if (this.type != other.type) {
                return type.compareTo(other.type) < 0;
            } else {
                return this.id < other.id;
            }
        }

        public String toString(){
            return prefix+id;
        }
    }
}
