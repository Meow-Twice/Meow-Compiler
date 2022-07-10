package lir;

import mir.BasicBlock;
import mir.GlobalVal;
import mir.Instr;
import mir.type.DataType;
import util.DoublelyLinkedList;
import util.ILinkNode;
import util.Ilist;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;

import static lir.Machine.Operand.Type.*;


public class Machine {

    public static class Program {
        ArrayList<McFunction> funcList;
        ArrayList<GlobalVal> globList;
        int pool_count = 0;
        int inst_count = 0;

        public void insertConsPool(PrintStream os, boolean insert_jump) {
            inst_count = 0;
            int pool_num = pool_count++;
            String pName = "_POOL_" + (pool_num++);
            String sName = ".L" + pName;
            String after_name = ".L_AFTER" + pName;
            if (insert_jump) {
                os.println("\tb\t" + after_name + " @ forcibly insert constant pool");
            }
            os.println(sName + ":");
            os.println("\t.pool");
            os.println(after_name + ":");
        }

        public void increase_count(int count) {
            inst_count += count;
        }

        public static boolean encode_imm(int imm) {
            for (int ror = 0; ror < 32; ror += 2) {
                if ((imm & ~0xFF) == 0) {
                    return true;
                }
                imm = (imm << 2) | (imm >> 30);
            }
            return false;
        }

        public static void stack_output(PrintStream os, boolean push, int offset, String prefix) {
            String op = push ? "sub" : "add";
            Machine.Operand offset_opd = new Operand(Immediate);
            offset_opd.value = offset;
            if (encode_imm(-offset)) {
                op = push ? "add" : "sub";
                offset_opd.value = -offset;
            }
            if (encode_imm(offset) || encode_imm(-offset)) {
                os.println(op + "\t" + "sp, sp," + offset_opd.toString());
            } else {
                //move to r4
                MIMove move = new MIMove();
                move.sOpd = offset_opd;
                //r4
                Machine.Operand r4 = new Operand(Allocated);
                r4.value = 4;
                move.dOpd = r4;
                move.output(os, null);
                os.println(prefix + op + "\t" + "sp, sp, " + r4.toString());
            }
        }

        // public Program(ArrayList<Function> funcList, ArrayList<GlobalVal> globList){
        //     this.funcList = funcList;
        //     this.globList = globList;
        // }
        public Program() {
        }

        public void output(PrintStream os) {
            os.println(".arch armv7ve");
            os.println(".section .text");
            for (McFunction function : funcList) {
                os.println();
                os.println(".global" + function.func_name);
                os.println("\t.type\t" + function.func_name + ",%function");
                os.println(function.func_name + ":");

                if (function.useLr || !function.usedCalleeSavedRegs.isEmpty()) {
                    os.print("\tpush\t{");
                    function.output_reg_list(os);
                }

                if (function.useLr) {
                    if (!function.usedCalleeSavedRegs.isEmpty()) {
                        os.print(",");
                    }
                    os.print("lr");
                }
                os.println("}");
                if (function.stackSize > 0) {
                    stack_output(os, true, function.stackSize, "\t");
                }

                //asm for bb
                // TODO 这里原来用法有问题，因为有空头部，所以headMB原来是空的, 现在用for循环就没问题了
                for (Block bb : function.mbList) {
                    os.println(bb.toString() + ":");
                    for (MachineInst inst : bb.miList) {
                        inst.output(os, function);
                    }
                }

            }
            os.println("\tblx getint");
            os.println();
            os.println();
            os.println(".section .data");
            os.println(".align 4");
            for (GlobalVal val : globList) {
                os.println();
                os.println(".global" + val.name);
                os.println("\t.type\t" + val.name + ",object");
                os.println(val.name + ":");

                //TODO for yyf:array init
                // arr你再仔细看看, 不用你做

            }
        }

    }

    public static class McFunction {
        // ArrayList<MachineInst> instList;
        // DoublelyLinkedList<Block> blockList;
        // Block tailBlock = new Block();
        // Block headBlock = new Block();
        public Ilist<Block> mbList = new Ilist<>();

        /**
         * 获取真正的第一个Block
         */
        public Block getBeginMB() {
            return mbList.getBegin();
            // assert headBlock.getNext() instanceof Instr;
            // return (Block) headBlock.getNext();
        }

        /**
         * 获取最后一个真正的Block
         */
        public Block getEndMB() {
            return mbList.getEnd();
            // assert tailBlock.getPrev() instanceof Instr;
            // return (Block) tailBlock.getPrev();
        }

        public void insertAtEnd(Block mb) {
            mbList.insertAtEnd(mb);
            // in.setPrev(tailBlock.getPrev());
            // in.setNext(tailBlock);
            // tailBlock.getPrev().setNext(in);
            // tailBlock.setPrev(in);
        }


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

        public void output_reg_list(PrintStream os) {
            int i = 0;
            for (Arm.Reg reg : usedCalleeSavedRegs) {
                if (i > 0) {
                    os.print(",");
                }
                os.print(reg);
                i++;
            }
        }


    }

    public static class Block extends ILinkNode {
        public static String BB_Prefix = ".L_BB_";
        public BasicBlock bb;
        public McFunction mcFunc;
        public MachineInst firstMIForBJ = null;
        public Ilist<MachineInst> miList;
        int index;
        // 双向链表的头
        // MachineInst tailMI = new MachineInst();
        // 双向链表的尾巴
        // MachineInst headMI = new MachineInst();

        // 上面这两个是空的, 专门做头和尾, 为了减少添加节点(MachineInst)的时候的空指针判断
        // DoublelyLinkedList<MachineInst> insts;
        public Block() {
        }

        /**
         * 获取第一条真正的指令
         */
        public MachineInst getBeginMI() {
            return miList.getBegin();
            // assert headMI.getNext() instanceof Instr;
            // return (MachineInst) headMI.getNext();
        }

        /**
         * 获取最后一条真正的指令
         */
        public MachineInst getEndMI() {
            return miList.getEnd();
            // assert tailMI.getPrev() instanceof Instr;
            // return (MachineInst) tailMI.getPrev();
        }

        public void insertAtEnd(MachineInst in) {
            miList.insertAtEnd(in);
            // in.setPrev(tailMI.getPrev());
            // in.setNext(tailMI);
            // tailMI.getPrev().setNext(in);
            // tailMI.setPrev(in);
        }

        public void insertAtHead(MachineInst in) {
            miList.insertAtBegin(in);
            // in.setPrev(headMI);
            // in.setNext(headMI.getNext());
            // headMI.getNext().setPrev(in);
            // headMI.setNext(in);
        }

        //pred and successor
        public ArrayList<Block> pred;
        public ArrayList<Block> successor;
        public MachineInst con_tran = null;
        public HashSet<Operand> liveUseSet;
        public HashSet<Operand> defSet;
        public HashSet<Operand> liveInSet;
        public HashSet<Operand> liveOutSet;

        public Block(BasicBlock bb, Machine.McFunction insertAtEnd) {
            this.bb = bb;
            this.mcFunc = insertAtEnd;
            mcFunc.insertAtEnd(this);
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

        public boolean isImm() {
            return type == Immediate;
        }

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
                return this.value < other.value;
            }
        }

        public String toString() {
            return prefix + value;
        }
    }
}
