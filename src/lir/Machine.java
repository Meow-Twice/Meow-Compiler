package lir;

import mir.BasicBlock;
import mir.GlobalVal;
import mir.type.DataType;
import util.DoublelyLinkedList;

import java.util.ArrayList;
import java.util.HashSet;

import static lir.Machine.Operand.Type.Virtual;

public class Machine {
    public static class Program {
        ArrayList<McFunction> funcList;
        ArrayList<GlobalVal> globList;

        // public Program(ArrayList<Function> funcList, ArrayList<GlobalVal> globList){
        //     this.funcList = funcList;
        //     this.globList = globList;
        // }
        public Program() {
        }
    }

    public static class McFunction {
        // ArrayList<MachineInst> instList;
        DoublelyLinkedList<Block> blockList;
        ArrayList<Operand> params;

        int virRegNum = 0;
        int stackSize = 0;
        mir.Function mFunc;
        HashSet<Arm.Reg> usedCalleeSavedRegs;
        boolean useLr = false;

        public McFunction(mir.Function function) {
            this.mFunc = function;
        }
    }

    public static class Block {
        public BasicBlock bb;
        public McFunction mcFunc;
        DoublelyLinkedList<MachineInst> insts;
        //pred and successor
        ArrayList<Block> pred;
        ArrayList<Block> successor;
        MachineInst con_tran = null;
        HashSet<Operand> liveUseSet;
        HashSet<Operand> defSet;
        HashSet<Operand> liveInSet;
        HashSet<Operand> liveOutSet;

        public Block(BasicBlock bb, Machine.McFunction insertAtEnd) {
            this.bb = bb;
            this.mcFunc = insertAtEnd;
            mcFunc.blockList.addLast(this);
        }
    }

    public static class Operand {
        private String prefix;
        private int vrId;

        public enum Type {
            PreColored,
            Allocated,
            Virtual,
            Immediate
        }

        private Type type;
        private DataType dataType = DataType.I32;

        // 默认分配通用寄存器
        public Operand(Type type) {
            this.type = type;
            prefix = switch (type) {
                case Virtual -> "v";
                case Allocated, PreColored -> "r";
                case Immediate -> "#";
            };
        }

        // 默认分配通用寄存器
        public Operand(int virtualRegCnt) {
            this.type = Virtual;
            vrId = virtualRegCnt;
            prefix = "v";
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
                return this.vrId < other.vrId;
            }
        }
    }
}
