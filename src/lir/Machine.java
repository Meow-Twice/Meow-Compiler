package lir;

import mir.BasicBlock;
import mir.GlobalVal;
import mir.Instr;
import mir.type.DataType;
import util.DoublelyLinkedList;

import java.util.ArrayList;
import java.util.HashSet;

import static lir.Machine.Operand.Type.*;

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
        String func_name;
        int virRegNum = 0;
        int stackSize = 0;
        mir.Function mFunc;
        HashSet<Arm.Reg> usedCalleeSavedRegs;
        boolean useLr = false;

        public McFunction(mir.Function function) {
            this.mFunc = function;
        }

        public void addStack(int i) {
            stackSize += i;
        }

        public int getStackSize() {
            return stackSize;
        }
    }

    public static class Block {
        public static String BB_Prefix = ".L_BB_";
        public BasicBlock bb;
        public McFunction mcFunc;
        public MachineInst firstMIForBJ = null;
        int index;
        // 双向链表的头
        MachineInst tailMI = new MachineInst();
        // 双向链表的尾巴
        MachineInst headMI = new MachineInst();
        // 上面这两个是空的, 专门做头和尾, 为了减少添加节点(MachineInst)的时候的空指针判断
        // DoublelyLinkedList<MachineInst> insts;

        /**
         * 获取第一条真正的指令
         */
        public MachineInst getBeginMI() {
            assert headMI.getNext() instanceof Instr;
            return (MachineInst) headMI.getNext();
        }

        /**
         * 获取最后一条真正的指令
         */
        public MachineInst getEndMI() {
            assert tailMI.getPrev() instanceof Instr;
            return (MachineInst) tailMI.getPrev();
        }

        public void insertAtEnd(MachineInst in) {
            in.setPrev(tailMI.getPrev());
            in.setNext(tailMI);
            tailMI.getPrev().setNext(in);
            tailMI.setPrev(in);
        }

        public void insertAtHead(MachineInst in) {
            in.setPrev(headMI);
            in.setNext(headMI.getNext());
            headMI.getNext().setPrev(in);
            headMI.setNext(in);
        }

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

        public String toString() {
            return BB_Prefix + index;
        }

    }

    public static class Operand {

        // 立即数, 默认为8888888方便debug
        // public int imm = 88888888;

        private String prefix;

        // 虚拟寄存器id号, 立即数号, 实际寄存器号
        protected int value;

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

        // 默认分配通用寄存器
        public Operand(int virtualRegCnt) {
            this.type = Virtual;
            value = virtualRegCnt;
            prefix = "v";
        }

        public Operand(DataType dataType, int imm) {
            type = Immediate;
            prefix = "#";
            this.dataType = dataType;
            this.value = imm;
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

        public Operand(Arm.Reg reg) {
            this.type = PreColored;
            prefix = switch (reg.dataType) {
                case F32 -> "s";
                case I32 -> "r";
                default -> throw new IllegalStateException("Unexpected reg type: " + reg.dataType);
            };
        }

        public boolean compareTo(Operand other) {
            if (this.type != other.type) {
                return type.compareTo(other.type) < 0;
            } else {
                return this.value < other.value;
            }
        }

        public String toString() {
            return prefix + value;
        }
    }
}
