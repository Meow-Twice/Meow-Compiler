package lir;

import backend.CodeGen;
import frontend.semantic.Initial;
import mir.BasicBlock;
import mir.Constant;
import mir.Value;
import mir.type.DataType;
import mir.type.Type;
import util.CenterControl;
import util.ILinkNode;
import util.Ilist;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeSet;

import static backend.CodeGen.*;
import static backend.RegAllocator.SP_ALIGN;
import static lir.Arm.Reg.getRSReg;
import static lir.Arm.Regs.FPRs.s0;
import static lir.Arm.Regs.GPRs.*;
import static lir.MC.Operand.Type.*;
import static mir.Constant.ConstantInt.CONST_0;
import static mir.type.DataType.F32;
import static mir.type.DataType.I32;

public class MC {

    public static class Program {
        public static final Program PROGRAM = new Program();
        public Ilist<McFunction> funcList = new Ilist<>();
        public final ArrayList<Arm.Glob> globList = new ArrayList<>();
        public McFunction mainMcFunc;
        public ArrayList<I> needFixList = new ArrayList<>();
        public static MachineInst specialMI; // 用于插入 bss 段的初始化代码

        // 对 globList 进行分流
        public final ArrayList<Arm.Glob> globData = new ArrayList<>();
        public final ArrayList<Arm.Glob> globBss = new ArrayList<>();

        private Program() {
        }

        /**
         * 对 .bss 段的全局变量中非零元素用指令进行初始化，插在指定的 specialMI 处
         */
        public void bssInit() {
            // 对 globList 进行分流
            for (Arm.Glob glob : globList) {
                // 放进 .data 条件: 非零值的数量超过 1/3
                Initial.Flatten flatten = glob.init.flatten();
                int countNonZeros = 0;
                for (Initial.Flatten.Entry entry : flatten) {
                    Value value = entry.value;
                    if (value.getType() == Type.BasicType.F32_TYPE) {
                        assert value instanceof Constant.ConstantFloat;
                        int bin = ((Constant.ConstantFloat) value).getIntBits();
                        if (bin != 0) {
                            countNonZeros += entry.count;
                        }
                    } else {
                        assert value instanceof Constant.ConstantInt;
                        if (!value.equals(CONST_0)) {
                            countNonZeros += entry.count;
                        }
                    }

                }
                if (countNonZeros * 3 >= flatten.sizeInWords()) {
                    globData.add(glob);
                } else {
                    globBss.add(glob);
                }
            }

            assert specialMI != null;
            for (Arm.Glob glob : globBss) {
                // 对每个变量初始化
                // 用 movw 和 movt 指令获取基地址
                MC.Operand rBase = getRSReg(r3), rOffset = getRSReg(r2), rData = getRSReg(r1);
                specialMI = new I.Mov(specialMI, rBase, glob); // 获取基地址
                Initial.Flatten flatten = glob.init.flatten();
                int offset = 0;
                for (Initial.Flatten.Entry entry : flatten) {
                    int end = offset + entry.count;
                    // 对每一项: 加载偏移, 存入立即数
                    while (offset < end) {
                        Value value = entry.value;
                        if (value.getType() == Type.BasicType.F32_TYPE) {
                            assert value instanceof Constant.ConstantFloat;
                            int bin = ((Constant.ConstantFloat) value).getIntBits();
                            if (bin != 0) {
                                if (LdrStrImmEncode(offset * 4)) {
                                    specialMI = new I.Mov(specialMI, rData, new Operand(I32, bin)); // mov 写入值
                                    specialMI = new I.Str(specialMI, rData, rBase, new Operand(I32, offset * 4));
                                } else {
                                    specialMI = new I.Mov(specialMI, rData, new Operand(I32, bin)); // mov 写入值
                                    specialMI = new I.Mov(specialMI, rOffset, new Operand(I32, offset)); // mov 写入值
                                    specialMI = new I.Str(specialMI, rData, rBase, rOffset, new Arm.Shift(Arm.ShiftType.Lsl, 2));
                                }
                            }
                        } else {
                            if (!value.equals(CONST_0)) {
                                if (LdrStrImmEncode(offset * 4)) {
                                    specialMI = new I.Mov(specialMI, rData, new Operand(I32, (int) ((Constant) value).getConstVal())); // mov 写入值
                                    specialMI = new I.Str(specialMI, rData, rBase, new Operand(I32, offset * 4));
                                } else {
                                    specialMI = new I.Mov(specialMI, rData, new Operand(I32, (int) ((Constant) value).getConstVal())); // mov 写入值
                                    specialMI = new I.Mov(specialMI, rOffset, new Operand(I32, offset)); // mov 写入值
                                    specialMI = new I.Str(specialMI, rData, rBase, rOffset, new Arm.Shift(Arm.ShiftType.Lsl, 2));
                                }
                            }
                        }
                        offset++;
                    }
                }
            }
        }

        public StringBuilder getSTB() {
            StringBuilder stb = new StringBuilder();
            stb.append("@ ").append(CenterControl._TAG).append("\n");
            stb.append(".arch armv7ve\n.arm\n");
            if (needFPU) {
                stb.append(".fpu vfpv3-d16\n");
            }

            stb.append(".section .text\n");
            for (McFunction function : funcList) {
                stb.append("\n\n.global\t").append(function.getName()).append("\n");
                stb.append("@ regStackSize =\t").append(function.regStack).append(" ;\n@ varStackSize =\t").append(function.varStack).append(" ;\n@ paramStackSize =\t").append(function.paramStack).append(" ;\n");
                stb.append("@ usedCalleeSavedGPRs =\t").append(function.usedCalleeSavedGPRs).append(" ;\n@ usedCalleeSavedFPRs =\t").append(function.usedCalleeSavedFPRs).append(" ;\n");
                stb.append(function.getName()).append(":\n");
                //asm for mb
                for (Block mb : function.mbList) {
                    stb.append(mb.getLabel()).append(":\n");
                    for (MachineInst inst : mb.miList) {
                        if (!inst.isComment()) stb.append("\t");
                        stb.append(inst.getSTB()).append("\n");
                    }
                }
            }

            stb.append("\n\n");
            // output float const
            for (Map.Entry<String, Operand> entry : CodeGen.name2constFOpd.entrySet()) {
                String name = entry.getKey();
                Constant.ConstantFloat constF = entry.getValue().constF;
                int i = constF.getIntBits();
                stb.append(name).append(":\n");
                stb.append("\t.word\t").append(i).append("\n");
            }

            if (CenterControl._GLOBAL_BSS) {
                stb.append(".section .bss\n");
                stb.append(".align 16\n");
                for (Arm.Glob glob : globBss) {
                    stb.append("\n.global\t").append(glob.getGlob()).append("\n");
                    stb.append(glob.getGlob()).append(":\n");

                    if (glob.init != null) {
                        Initial.Flatten flatten = glob.init.flatten();
                        stb.append(".zero ").append(flatten.sizeInBytes()).append("\n");
                    } else {stb.append(".zero 4\n");
                    }
                }
                globalDataStbHelper(stb, globData);
            } else {
                globalDataStbHelper(stb, globList);
            }
            return stb;
        }

        private void globalDataStbHelper(StringBuilder stb, ArrayList<Arm.Glob> globData) {
            stb.append(".section .data\n");
            stb.append(".align 2\n");
            for (Arm.Glob glob : globData) {
                stb.append("\n.global\t").append(glob.getGlob()).append("\n");
                stb.append(glob.getGlob()).append(":\n");
                if (glob.init != null) {
                    Initial.Flatten flatten = glob.init.flatten();
                    stb.append(flatten.toString());
                } else {
                    stb.append("\t.word\t0\n");
                }
            }
        }
    }

    public static class McFunction extends ILinkNode {
        // ArrayList<MachineInst> instList;
        // Block tailBlock = new Block();
        // Block headBlock = new Block();
        public Ilist<Block> mbList = new Ilist<>();
        public int floatParamCount = 0;
        public int intParamCount = 0;
        public boolean isMain = false;
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

        public void insertAtEnd(Block mb) {
            mbList.insertAtEnd(mb);
        }

        /**
         * 获取最后一个真正的Block
         */
        public Block getEndMB() {
            return mbList.getEnd();
            // assert tailBlock.getPrev() instanceof Instr;
            // return (Block) tailBlock.getPrev();
        }

        // ArrayList<Operand> params = new ArrayList<>();
        int varStack = 0;
        int paramStack = 0;
        int regStack = 0;
        public mir.Function mFunc;
        TreeSet<GPRs> usedCalleeSavedGPRs = new TreeSet<>();
        TreeSet<FPRs> usedCalleeSavedFPRs = new TreeSet<>();
        boolean useLr = false;

        public ArrayList<GPRs> getUsedRegList() {
            return new ArrayList<>(usedCalleeSavedGPRs);
        }

        public McFunction(mir.Function function) {
            this.mFunc = function;
        }

        public String name;

        public McFunction(String name) {
            this.name = name;
        }

        public String getName() {
            if (mFunc != null) return mFunc.getName();
            return name;
        }

        public void addVarStack(int i) {
            varStack += i;
        }

        // 方便解释器和后端生成，因为采用把参数放到caller的sp的下面若干位置的方式
        public void addParamStack(int i) {
            paramStack += i;
        }

        public void alignParamStack() {
            int b = paramStack % SP_ALIGN;
            if (b != 0) {
                paramStack += SP_ALIGN - b;
            }
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

        int allocStack = -1;

        public void setAllocStack() {
            allocStack = varStack;
        }

        public int getAllocStack() {
            return allocStack;
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
            if (reg instanceof GPRs) {
                if (reg == sp || ((GPRs) reg).ordinal() < Math.min(intParamCount, rParamCnt) || (this.mFunc.getRetType().isInt32Type() && reg == r0)) {
                    return;
                }
                if (usedCalleeSavedGPRs.add((GPRs) reg)) {
                    addRegStack(4);
                }
            }
        }

        public void addUsedFRPs(Arm.Regs reg) {
            if (reg instanceof FPRs) {
                if (((FPRs) reg).ordinal() < Math.min(floatParamCount, sParamCnt) || (this.mFunc.getRetType().isFloatType() && reg == s0)) {
                    return;
                }
                if (((FPRs) reg).ordinal() < 2) return;
                if (usedCalleeSavedFPRs.add((FPRs) reg)) {
                    addRegStack(4);
                    int idx = ((FPRs) reg).ordinal();
                    if (idx % 2 == 0) {
                        if (usedCalleeSavedFPRs.add((Arm.Reg.getS(idx + 1).fpr))) {
                            addRegStack(4);
                        }
                    } else {
                        if (usedCalleeSavedFPRs.add((Arm.Reg.getS(idx - 1).fpr))) {
                            addRegStack(4);
                        }
                    }
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

        public void alignTotalStackSize() {
            int totalSize = getTotalStackSize();
            int b = totalSize % SP_ALIGN;
            if (b != 0) {
                varStack += SP_ALIGN - b;
            }
        }
    }

    public static class Block extends ILinkNode { //
        // public static String BB_Prefix = ".L_BB_";
        public static String MB_Prefix = "._MB_";
        public BasicBlock bb;
        public McFunction mf;
        // public MachineInst firstMIForBJ = null;
        public Ilist<MachineInst> miList = new Ilist<>();
        static int globIndex = 0;
        int mb_idx;
        String label;
        public ArrayList<Block> predMBs = new ArrayList<>();
        public ArrayList<Block> succMBs = new ArrayList<>();
        public HashSet<Operand> liveUseSet = new HashSet<>();
        public HashSet<Operand> liveDefSet = new HashSet<>();
        public HashSet<Operand> liveInSet = new HashSet<>();
        public HashSet<Operand> liveOutSet = new HashSet<>();

        /**
         * 获取第一条真正的指令
         */
        public MachineInst getBeginMI() {
            return miList.getBegin();
        }

        /**
         * 获取最后一条真正的指令
         */
        public MachineInst getEndMI() {
            return miList.getEnd();
        }

        public void insertAtEnd(MachineInst in) {
            miList.insertAtEnd(in);
        }

        public void insertAtHead(MachineInst in) {
            miList.insertAtBegin(in);
        }

        public Block(BasicBlock bb, McFunction insertAtEnd) {
            this.bb = bb;
            this.mf = insertAtEnd;
            mf.insertAtEnd(this);
        }

        public Block(BasicBlock bb) {
            this.bb = bb;
            mb_idx = globIndex++;
        }

        public Block(String label, McFunction insertAtEnd) {
            this.bb = null;
            this.label = label;
            mf = insertAtEnd;
            mf.insertAtEnd(this);
        }

        public String getLabel() {
            return (bb == null ? label : MB_Prefix + mb_idx + "_" + bb.getLabel());
        }

        @Override
        public String toString() {
            return (bb == null ? label : MB_Prefix + mb_idx + "_" + bb.getLabel());
        }

        public void setMf(McFunction mf) {
            this.mf = mf;
            mf.insertAtEnd(this);
        }

        public Block falseSucc() {
            return succMBs.get(0);
        }


        public Block trueSucc() {
            if(succMBs.size() < 2) return null;
            return succMBs.get(1);
        }

        public void setFalse(Block onlySuccMB) {
            succMBs.set(1, onlySuccMB);
        }

        public void setTrue(Block onlySuccMB) {
            succMBs.set(0, onlySuccMB);
        }
    }

    public static class Operand {
        public static final Operand I_ZERO = new Operand(I32, 0);
        public static final Operand I_ONE = new Operand(I32, 1);
        public int loopCounter = 0;

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
        // public HashSet<I.Mov> iMovSet = new HashSet<>();
        // public HashSet<V.Mov> vMovSet = new HashSet<>();
        public HashSet<MachineInst.MachineMove> movSet = new HashSet<>();
        // public Arm.Reg reg;
        // public Arm.Reg reg;
        public Arm.Regs reg;
        // private static Arm.Reg[] regPool = new Arm.Reg[Arm.Regs.GPRs.values().length + Arm.Regs.FPRs.values().length];

        public boolean is_I_Imm() {
            return type == Immediate && dataType == I32;
        }

        public void addAdj(Operand v) {
            adjOpdSet.add(v);
        }

        // public boolean is_I_PreColored() {
        //     return type == PreColored && dataType == I32;
        // }

        public boolean isPreColored(DataType dataType) {
            return type == PreColored && this.dataType == dataType;
        }

        // public boolean need_I_Color() {
        //     return (type == PreColored || type == Virtual) && dataType == I32;
        // }
        //
        // public boolean need_F_Color() {
        //     return (type == PreColored || type == FVirtual) && dataType == F32;
        // }

        public boolean needColor(DataType dataType) {
            return dataType == this.dataType && (type == PreColored || type == Virtual);
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

        public boolean isVirtual(DataType dataType) {
            return this.dataType == dataType && type == Virtual;
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
            return type == Virtual && dataType == F32;
        }

        public boolean isI32() {
            return dataType == I32;
        }

        public boolean isDataType(DataType dataType) {
            return this.dataType == dataType;
        }

        public boolean isPureImmWithOutGlob(DataType dataType) {
            if (this instanceof Arm.Glob) return false;
            return type == Immediate && this.dataType == dataType;
        }

        public boolean isImm() {
            return type == Immediate;
        }

        public enum Type {
            PreColored,
            Virtual,
            Allocated,
            Immediate,
            // FVirtual,
            FConst,
        }

        public boolean needRegOf(DataType dataType) {
            return (type == PreColored || type == Virtual) && dataType == this.dataType;
        }

        public Type type;
        DataType dataType = I32;

        // 目前只用于立即数
        public Operand(Type type) {
            this.type = type;
            prefix = switch (type) {
                case Virtual -> "v";
                case Allocated, PreColored -> "r";
                case Immediate -> "#";
                // case FVirtual -> "fv";
                case FConst -> "";
            };
        }

        // 新建虚拟寄存器
        public Operand(int virtualRegCnt, DataType dataType) {
            this.type = Virtual;
            this.dataType = dataType;
            if (dataType == I32) {
                value = virtualRegCnt;
                prefix = "v";
            } else if (dataType == F32) {
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
            this.dataType = dataType;
            prefix = switch (type) {
                case Virtual -> switch (dataType) {
                    case I32 -> "v";
                    case F32 -> "fv";
                    default -> throw new IllegalStateException("Unexpected value: " + dataType);
                };
                case Allocated, PreColored -> switch (dataType) {
                    case F32 -> "s";
                    case I32 -> "r";
                    default -> throw new IllegalStateException("Unexpected reg type: " + dataType);
                };
                case Immediate -> "#";
                case FConst -> "";
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
            if (reg instanceof GPRs) {
                prefix = "r";
                // dataType = I32;
            } else if (reg instanceof FPRs) {
                prefix = "s";
                dataType = F32;
            } else {
                throw new AssertionError("Wrong reg: " + reg);
            }
            this.reg = reg;
            value = ((Enum<?>) reg).ordinal();
        }

        public String getPrefix() {
            return switch (type) {
                case Virtual -> switch (dataType) {
                    case F32 -> "fv";
                    case I32 -> "v";
                    default -> throw new IllegalStateException("Unexpected reg type: " + dataType);
                };
                case Allocated, PreColored -> switch (dataType) {
                    case F32 -> "s";
                    case I32 -> "r";
                    default -> throw new IllegalStateException("Unexpected reg type: " + dataType);
                };
                case Immediate -> "#";
                case FConst -> "";
            };
        }

        public double heuristicVal() {
            return (degree << 10) / Math.pow(1.6, loopCounter);
        }

        public boolean isF32() {
            // return type == FVirtual || type == FConst ||
            //         ((type == PreColored || type == Allocated) && (dataType == F32));
            return dataType == F32;
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

        public Operand select(Operand o) {
            return heuristicVal() < o.heuristicVal() ? this : o;
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


    }

}
