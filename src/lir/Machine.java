package lir;

import backend.CodeGen;
import mir.BasicBlock;
import mir.Constant;
import mir.GlobalVal;
import mir.Value;
import mir.type.DataType;
import util.ILinkNode;
import util.Ilist;

import java.io.PrintStream;
import java.util.*;

import static lir.Arm.Regs.GPRs.*;
import static lir.Machine.Operand.Type.*;
import static mir.type.DataType.F32;
import static mir.type.DataType.I32;


public class Machine {

    public static class Program {
        public static final Program PROGRAM = new Program();
        public Ilist<McFunction> funcList = new Ilist<>();
        public ArrayList<Arm.Glob> globList = CodeGen.CODEGEN.globList;
        public McFunction mainMcFunc;
        public ArrayList<MachineInst> needFixList = new ArrayList<>();
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

        private Program() {
        }

        public void output(PrintStream os) {
            os.println(".arch armv7ve");
            os.println(".arm");
            os.println(".section .text");
            for (McFunction function : funcList) {
                os.println();
                os.println(".global\t" + function.mFunc.getName());
                os.println("\t.type\t" + function.mFunc.getName() + ",%function");
                os.println(function.mFunc.getName() + ":");
                if (function.usedCalleeSavedGPRs.size() > 0) {
                    os.print("\tpush\t{");
                    Iterator<Arm.Regs.GPRs> gprIter = function.usedCalleeSavedGPRs.iterator();
                    os.print(gprIter.next());
                    while (gprIter.hasNext()) {
                        os.print("," + gprIter.next());
                    }
                    os.println("}");
                }
                if (function.usedCalleeSavedFPRs.size() > 0) {
                    os.print("\tvpush\t{");
                    Iterator<Arm.Regs.FPRs> fprIter = function.usedCalleeSavedFPRs.iterator();
                    os.print(fprIter.next());
                    while (fprIter.hasNext()) {
                        os.print("," + fprIter.next());
                    }
                    os.println("}");
                }

                //asm for mb
                for (Block mb : function.mbList) {
                    os.println(mb.toString() + ":");
                    for (MachineInst inst : mb.miList) {
                        inst.output(os, function);
                    }
                }

                // 前端保证一定有return语句
                // pop_output(os, function);
            }

            os.println();
            os.println();
            // output float const
            for (Map.Entry<String, Operand> entry : CodeGen.name2constFOpd.entrySet()) {
                String name = entry.getKey();
                Constant.ConstantFloat constF = entry.getValue().constF;
                int i = constF.getIntBits();
                os.println(name+":");
                os.println("\t.word\t" + i);
            }
            os.println(".section .data");
            os.println(".align 4");
            for (Arm.Glob glob : globList) {
                GlobalVal.GlobalValue val = glob.getGlobalValue();
                os.println();
                os.println(".global\t" + val.name);
                // os.println("\t.type\t" + val.name + ",%object");
                os.println(val.name + ":");

                //TODO for yyf:array init
                int count = 0;
                boolean init = false;
                int last = 0;

                for (Value value : glob.getInit().getFlattenInit()) {
                    if (!init) {
                        init = true;
                        if (value.isConstantInt()) {
                            last = ((Constant.ConstantInt) value).constIntVal;
                        } else {
                            last = ((Constant.ConstantFloat) value).getIntBits();
                        }
                    }
                    int now = value instanceof Constant.ConstantInt ? ((Constant.ConstantInt) value).constIntVal : ((Constant.ConstantFloat) value).getIntBits();
                    if (now == last) {
                        count++;
                    } else {
                        if (count > 1) {
                            //.zero
                            os.println("\t.fill\t" + count + ",\t4,\t" + last);
                        } else {
                            os.println("\t.word\t" + last);
                        }
                        last = value instanceof Constant.ConstantInt ? ((Constant.ConstantInt) value).constIntVal : ((Constant.ConstantFloat) value).getIntBits();
                        count = 1;
                    }
                }
                if (count > 1) {
                    //.zero
                    os.println("\t.fill\t" + count + ",\t4,\t" + last);
                } else {
                    os.println("\t.word\t" + last);
                }
            }
        }

        public static void pop_output(PrintStream os, McFunction function) {
            boolean retByBx = true;
            if (function.usedCalleeSavedFPRs.size() > 0) {
                os.print("\tvpop\t{");
                Iterator<Arm.Regs.FPRs> fprIter = function.usedCalleeSavedFPRs.iterator();
                os.print(fprIter.next());
                while (fprIter.hasNext()) {
                    Arm.Regs.FPRs fpr = fprIter.next();
                    os.print("," + fpr);
                }
                os.println("}");
            }
            if (function.usedCalleeSavedGPRs.size() > 0) {
                os.print("\tpop\t{");
                Iterator<Arm.Regs.GPRs> gprIter = function.usedCalleeSavedGPRs.iterator();
                os.print(gprIter.next());
                while (gprIter.hasNext()) {
                    Arm.Regs.GPRs gpr = gprIter.next();
                    if (gpr == lr) {
                        gpr = pc;
                        retByBx = false;
                    }
                    os.print("," + gpr);
                }
                os.println("}");
            }
            if (retByBx) {
                os.println("\tbx\tlr");
            }
        }

    }

    public static class McFunction extends ILinkNode {
        // ArrayList<MachineInst> instList;
        // Block tailBlock = new Block();
        // Block headBlock = new Block();
        public Ilist<Block> mbList = new Ilist<>();
        private int vrCount = 0;

        private int sVrCount = 0;

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


        // ArrayList<Operand> params = new ArrayList<>();
        String func_name;
        // int vrSize = 0;
        int varStack = 0;
        int paramStack = 0;
        int regStack = 0;
        public mir.Function mFunc;
        TreeSet<Arm.Regs.GPRs> usedCalleeSavedGPRs = new TreeSet<>();
        TreeSet<Arm.Regs.FPRs> usedCalleeSavedFPRs = new TreeSet<>();
        boolean useLr = false;

        public ArrayList<Arm.Regs.GPRs> getUsedRegList() {
            return new ArrayList<>(usedCalleeSavedGPRs);
        }

        public McFunction(mir.Function function) {
            this.mFunc = function;
        }

        public void addVarStack(int i) {
            varStack += i;
        }

        // 方便解释器和后端生成，因为采用把参数放到caller的sp的下面若干位置的方式
        public void addParamStack(int i) {
            paramStack += i;
        }

        public void addRegStack(int i) {
            regStack += i;
        }

        public int getTotalStackSize() {
            return varStack + paramStack + regStack;
        }

        public int getParamStack() {
            return paramStack;
        }

        public int getVarStack() {
            return varStack;
        }

        public int getVRSize() {
            return vrCount;
        }


        public int getSVRSize() {
            return sVrCount;
        }

        public ArrayList<Operand> vrList = new ArrayList<>();

        public ArrayList<Operand> sVrList = new ArrayList<>();

        public Operand getVR(int idx) {
            assert idx <= vrCount;
            // if (idx >= vrCount) {
            //     Operand newVR = new Operand(vrCount++);
            //     vrList.add(newVR);
            //     return newVR;
            // }
            return vrList.get(idx);
        }

        public Operand getSVR(int idx) {
            assert idx <= sVrCount;
            return sVrList.get(idx);
        }

        public Operand newVR() {
            Operand vr = new Operand(vrCount++, I32);
            vrList.add(vr);
            return vr;
        }

        public Operand newSVR() {
            Operand sVr = new Operand(sVrCount++, F32);
            sVrList.add(sVr);
            return sVr;
        }

        // public void setVRCount(int i) {
        //     vrCount = i;
        // }
        //
        // public int addVRCount(int i) {
        //     assert vrCount == vrList.size() - 1;
        //     vrCount = vrCount + i;
        //     return vrCount;
        // }

        public void clearVRCount() {
            vrCount = 0;
            this.vrList = new ArrayList<>();
        }

        public void clearSVRCount() {
            sVrCount = 0;
            this.sVrList = new ArrayList<>();
        }

        public void addUsedGPRs(Arm.Regs reg) {
            if (reg instanceof Arm.Regs.GPRs) {
                if (reg == sp || ((Arm.Regs.GPRs) reg).ordinal() < Math.min(4, mFunc.getParams().size()) || (this.mFunc.hasRet() && reg == r0)) {
                    return;
                }
                if (usedCalleeSavedGPRs.add((Arm.Regs.GPRs) reg)) {
                    addRegStack(4);
                }
            }
        }

        public void addUsedFRPs(Arm.Regs reg) {
            if (reg instanceof Arm.Regs.FPRs) {
                if (usedCalleeSavedFPRs.add((Arm.Regs.FPRs) reg)) {
                    addRegStack(4);
                }
            }
        }

        public void setUseLr() {
            useLr = true;
            addUsedGPRs(lr);
        }

        public int getRegStack() {
            return regStack;
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

        @Override
        public String toString() {
            return MB_Prefix + index + (bb == null ? "" : "_" + bb.getLabel());
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


        public int get_I_Imm() {
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
        public HashSet<V.Mov> vMovSet = new HashSet<>();
        // public Arm.Reg reg;
        public Arm.Regs reg;
        // private static Arm.Reg[] regPool = new Arm.Reg[Arm.Regs.GPRs.values().length + Arm.Regs.FPRs.values().length];

        public boolean is_I_Imm() {
            return type == Immediate;
        }

        public void addAdj(Operand v) {
            adjOpdSet.add(v);
        }

        public boolean is_I_PreColored() {
            return type == PreColored && dataType == I32;
        }

        public boolean is_F_PreColored() {
            return type == PreColored && dataType == F32;
        }

        public boolean need_I_Color() {
            return (type == PreColored || type == Virtual) && dataType == I32;
        }

        public boolean need_F_Color() {
            return (type == PreColored || type == FVirtual) && dataType == F32;
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

        public boolean is_I_Virtual() {
            return type == Virtual;
        }

        public boolean is_F_Virtual() {
            return type == FVirtual;
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

        public boolean fNotConst() {
            return type != Immediate && type != FConst && dataType == F32;
        }

        public boolean isI32() {
            return dataType == I32;
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
            Immediate,
            FVirtual,
            FConst,
        }

        Type type;
        DataType dataType = I32;

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
                case FVirtual -> "fv";
                case FConst -> "";
            };
        }

        // 新建虚拟寄存器
        public Operand(int virtualRegCnt, DataType dataType) {
            if (dataType == I32) {
                this.type = Virtual;
                value = virtualRegCnt;
                prefix = "v";
            } else if (dataType == F32) {
                this.type = FVirtual;
                value = virtualRegCnt;
                prefix = "fv";
            } else {
                throw new AssertionError("Bad dataType when new Operand of (S)VR:\t" + dataType);
            }
        }

        /**
         * 整数立即数
         * 用的太多了没法改了就这样吧
         *
         * @param dataType
         * @param imm
         */
        public Operand(DataType dataType, int imm) {
            assert dataType == I32;
            type = Immediate;
            prefix = "#";
            // this.dataType = dataType;
            this.value = imm;
        }

        Constant.ConstantFloat constF = null;

        /**
         * 浮点常量
         *
         * @param constF
         */
        public Operand(DataType dataType, Constant.ConstantFloat constF) {
            assert dataType == F32;
            type = FConst;
            this.constF = constF;
        }

        /**
         * 只有reg初始化会用
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
                case FConst -> "";
                case FVirtual -> "fv";
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

        /**
         * 真正分配寄存器的时候, 即最终染色
         *
         * @param reg
         */
        public Operand(Arm.Regs reg) {
            this.type = Allocated;
            if (reg instanceof Arm.Regs.GPRs) {
                prefix = "r";
            } else if (reg instanceof Arm.Regs.FPRs) {
                prefix = "s";
                dataType = F32;
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
                case FVirtual -> "fv";
                case FConst -> "";
            };
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (!(obj instanceof Operand)) {
                return false;
            }
            return ((Operand) obj).type == this.type
                    && ((Operand) obj).value == this.value
                    && ((Operand) obj).dataType == this.dataType;
        }

        @Override
        public String toString() {
            if (this instanceof Arm.Reg) {
                return getReg().toString();
            } else if (type == FConst) {
                return constF.getAsmName();
            } else {
                return getPrefix() + value;
            }
        }

        public double heuristicVal() {
            return (double) degree / (2 << loopCounter);
        }

        public boolean isFloat() {
            // return type == FVirtual || type == FConst ||
            //         ((type == PreColored || type == Allocated) && (dataType == F32));
            return dataType == F32;
        }

        public Operand select(Operand o) {
            return heuristicVal() < o.heuristicVal() ? this : o;
        }
    }
}
