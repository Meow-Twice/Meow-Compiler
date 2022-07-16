package lir;

import mir.BasicBlock;
import mir.Constant;
import mir.GlobalVal;
import mir.Value;
import mir.type.DataType;
import util.ILinkNode;
import util.Ilist;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;

import static lir.Machine.Operand.Type.*;


public class Machine {

    public static class Program {
        public static final Program PROGRAM = new Program();
        public Ilist<McFunction> funcList = new Ilist<>();
        public ArrayList<GlobalVal> globList = new ArrayList<>();
        public McFunction mainMcFunc;
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
                move.setSrc(offset_opd);
                //r4
                Machine.Operand r4 = Arm.Reg.getR(Arm.Regs.GPRs.r4);
                move.setDst(r4);
                move.output(os, null);
                os.println(prefix + op + "\t" + "sp, sp, " + r4.toString());
            }
        }

        // public Program(ArrayList<Function> funcList, ArrayList<GlobalVal> globList){
        //     this.funcList = funcList;
        //     this.globList = globList;
        // }
        private Program() {
        }

        public void output(PrintStream os) {
            os.println(".arch armv7ve");
            os.println(".arm");
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
                os.println("\t.type\t" + val.name + ",%object");
                os.println(val.name + ":");

                //TODO for yyf:array init
                int count = 0;
                boolean init = false;
                int last = 0;

                for (Value value : ((GlobalVal.GlobalValue) val).initial.getFlattenInit()) {
                    if (!init) {
                        init = true;
                        last = ((Constant.ConstantInt) value).constIntVal;
                    }
                    if (((Constant.ConstantInt) value).constIntVal == last) {
                        count++;
                    } else {
                        if (count > 1) {
                            //.zero
                            os.println("\t.fill\t" + count + ",4," + last);
                        } else {
                            os.println("\t.word\t" + last);
                        }
                        last = ((Constant.ConstantInt) value).constIntVal;
                        count = 1;
                    }
                }
                if (count > 1) {
                    //.zero
                    os.println("\t.fill\t" + count + ",4," + last);
                } else {
                    os.println("\t.word\t" + last);
                }
            }
        }

    }

    public static class McFunction extends ILinkNode {
        // ArrayList<MachineInst> instList;
        // DoublelyLinkedList<Block> blockList;
        // Block tailBlock = new Block();
        // Block headBlock = new Block();
        public Ilist<Block> mbList = new Ilist<>();
        private int vrCount = 0;

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


        ArrayList<Operand> params = new ArrayList<>();
        String func_name;
        // int vrSize = 0;
        int stackSize = 0;
        int paramStack = 0;
        public mir.Function mFunc;
        HashSet<Arm.Reg> usedCalleeSavedRegs = new HashSet<>();
        boolean useLr = false;

        public McFunction(mir.Function function) {
            this.mFunc = function;
        }

        public void addStack(int i) {
            stackSize += i;
        }

        // 方便解释器和后端生成，因为采用把参数放到caller的sp的下面若干位置的方式
        public void addParamStack(int i) {
            paramStack += i;
        }

        public int getStackSize() {
            return stackSize + paramStack;
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


        public int getVRSize() {
            return vrCount;
        }

        public ArrayList<Operand> vrList = new ArrayList<>();

        public Operand getVR(int idx) {
            assert idx <= vrCount;
            if (idx >= vrCount) {
                Operand newVR = new Operand(vrCount++);
                vrList.add(newVR);
                return newVR;
            }
            return vrList.get(idx);
        }

        public Operand newVR() {
            Operand vr = new Operand(vrCount++);
            vrList.add(vr);
            return vr;
        }

        public void setVRCount(int i) {
            vrCount = i;
        }

        public int addVRCount(int i) {
            assert vrCount == vrList.size() - 1;
            vrCount = vrCount + i;
            return vrCount;
        }

        public void clearVRCount() {
            vrCount = 0;
            this.vrList = new ArrayList<>();
        }
    }

    public static class Block extends ILinkNode {
        // public static String BB_Prefix = ".L_BB_";
        public static String MB_Prefix = "._MB_";
        public BasicBlock bb;
        public McFunction mcFunc;
        public MachineInst firstMIForBJ = null;
        public Ilist<MachineInst> miList = new Ilist<>();
        static int globIndex = 0;
        int index;
        // 双向链表的头
        // MachineInst tailMI = new MachineInst();
        // 双向链表的尾巴
        // MachineInst headMI = new MachineInst();

        // 上面这两个是空的, 专门做头和尾, 为了减少添加节点(MachineInst)的时候的空指针判断
        // DoublelyLinkedList<MachineInst> insts;
        // public Block() {
        // }

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
        public ArrayList<Block> pred = new ArrayList<>();
        public ArrayList<Block> succMB = new ArrayList<>();
        public MachineInst con_tran = null;
        public HashSet<Operand> liveUseSet = new HashSet<>();
        public HashSet<Operand> defSet = new HashSet<>();
        public HashSet<Operand> liveInSet = new HashSet<>();
        public HashSet<Operand> liveOutSet = new HashSet<>();

        public Block(BasicBlock bb, Machine.McFunction insertAtEnd) {
            this.bb = bb;
            this.mcFunc = insertAtEnd;
            mcFunc.insertAtEnd(this);
        }

        public Block(BasicBlock bb) {
            this.bb = bb;
            index = globIndex++;
        }

        public void setMcFunc(McFunction mcFunc) {
            this.mcFunc = mcFunc;
            mcFunc.insertAtEnd(this);
        }

        public String toString() {
            return MB_Prefix + index + "_" + bb.getLabel();
        }

        public String getDebugLabel() {
            return this.toString()/* +"_"+bb.getLabel()*/;
        }
    }

    public static class Operand {
        public int loopCounter = 0;

        // 立即数, 默认为8888888方便debug
        // public int imm = 88888888;

        private String prefix;

        // 虚拟寄存器id号, 立即数号, 实际寄存器号
        protected int value;


        public int getImm() {
            return value;
        }

        /**
         * 对于每一个非预着色的虚拟寄存器 this , adjOpedSet 是与 this 冲突的 Operand 的集合
         */
        public HashSet<Operand> adjOpdSet = new HashSet<>();

        /**
         * 当前度数
         */
        public int degree = 0;
        /**
         * 当一条传送指令 (u, v) 已被合并,
         * 并且 v 已放入到 已合并 Operand 集合 coalescedNodeSet时,
         * alias(v) = u
         */
        private Operand alias;
        /**
         * 与此 Operand 相关的传送指令列表的集合
         */
        public HashSet<MIMove> moveSet = new HashSet<>();

        // public Arm.Reg reg;
        public Arm.Regs reg;
        // private static Arm.Reg[] regPool = new Arm.Reg[Arm.Regs.GPRs.values().length + Arm.Regs.FPRs.values().length];

        public boolean isImm() {
            return type == Immediate;
        }

        public void addAdj(Operand v) {
            adjOpdSet.add(v);
        }

        public boolean isPreColored() {
            return type == PreColored;
        }

        public boolean needColor() {
            return type == PreColored || type == Virtual;
        }

        public boolean isAllocated() {
            return type == Allocated;
        }

        public boolean hasReg() {
            return type == Allocated || type == PreColored;
        }

        public Arm.Regs getReg() {
            return reg;
        }

        public boolean isVirtual() {
            return type == Virtual;
        }

        public void setValue(int i) {
            value = i;
        }

        public Operand getAlias() {
            return alias;
        }

        public void setAlias(Operand u) {
            alias = u;
        }

        public int getValue() {
            return value;
        }

        public boolean isGlobPtr() {
            return this instanceof Arm.Glob;
        }

        public String getGlob() {
            throw new AssertionError("not glob but try to load");
        }

        // static {
        //     // 调用了子类, 所以不行
        //     int i;
        //     for(i = 0 ; i < Arm.Regs.GPRs.values().length; i++){
        //         regPool[i] = Arm.Reg.getR(i);
        //     }
        //     for(i = Arm.Regs.GPRs.values().length ; i < Arm.Regs.GPRs.values().length + Arm.Regs.FPRs.values().length; i++){
        //         regPool[i] = Arm.Reg.getS(i);
        //     }
        // }
        // public static void getR() {
        //
        // }

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

        // 目前只用于立即数
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

        /**
         * data
         *
         * @param type
         * @param dataType
         */
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

        // public Operand(Arm.Reg reg) {
        //     this.type = PreColored;
        //     this.reg = reg.reg;
        //     prefix = switch (reg.dataType) {
        //         case F32 -> "s";
        //         case I32 -> "r";
        //         default -> throw new IllegalStateException("Unexpected reg type: " + reg.dataType);
        //     };
        // }

        public Operand(Arm.Regs reg) {
            this.type = Allocated;
            if (reg instanceof Arm.Regs.GPRs) {
                prefix = "r";
            } else if (reg instanceof Arm.Regs.FPRs) {
                prefix = "s";
            } else {
                throw new AssertionError("Wrong reg: " + reg);
            }
            this.reg = reg;
            value = ((Enum<?>) reg).ordinal();
        }

        public boolean compareTo(Operand other) {
            if (this.type != other.type) {
                return type.compareTo(other.type) < 0;
            } else {
                return this.value < other.value;
            }
        }

        public String getPrefix() {
            return switch (type) {
                case Virtual -> "v";
                case Allocated, PreColored -> switch (dataType) {
                    case F32 -> "s";
                    case I32 -> "r";
                    default -> throw new IllegalStateException("Unexpected reg type: " + dataType);
                };
                case Immediate -> "#";
            };
        }

        public String toString() {
            if (this instanceof Arm.Reg) {
                return getReg().toString();
            } else {
                return getPrefix() + value;
            }
        }

        public double heuristicVal() {
            return (double) degree / (2 << loopCounter);
        }

        public Operand select(Operand o) {
            return heuristicVal() < o.heuristicVal() ? this : o;
        }
    }
}
