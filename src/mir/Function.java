package mir;

import manage.Manager;
import midend.CloneInfoMap;
import midend.OutParam;
import mir.type.Type;
import util.ILinkNode;
import util.Ilist;

import java.util.*;

/**
 * 函数声明 (+ 函数体 = 函数定义)
 */
public class Function extends Value {
    private final String name;

    boolean isCaller = true;

    public boolean hasCall() {
        return isCaller;
    }

    private boolean isDeleted = false;

    public void setDeleted() {
        isDeleted = true;
    }

    public boolean getDeleted() {
        return isDeleted;
    }


    public static class Param extends Value {

        private static int FPARAM_COUNT = 0;
        private int idx;
        // private Type type;
        private Function parentFunc;

        private HashSet<Instr> loads = new HashSet<>();

        public HashSet<Instr> getLoads() {
            return loads;
        }

        public void setLoads(HashSet<Instr> loads) {
            this.loads = loads;
        }

        public void clearLoads() {
            this.loads.clear();
        }

        public void addLoad(Instr instr) {
            this.loads.add(instr);
        }

        public Param(Type type, int idx) {
            if (!Manager.external) {
                prefix = LOCAL_PREFIX;
                name = FPARAM_NAME_PREFIX + FPARAM_COUNT++;
            }
            this.type = type;
            this.idx = idx;
        }

        public static ArrayList<Param> wrapParam(Type... types) {
            ArrayList<Param> params = new ArrayList<>();
            int i = 0;
            for (Type type : types) {
                params.add(new Param(type, i++));
            }
            return params;
        }

        @Override
        public String toString() {
            return type.toString() + " " + getName();
        }

        @Override
        public Type getType() {
            return type;
        }

    }

//    private final List<Val.Var> params; // 形参表
//    private final Type retType; // 返回值类型, 如果为 null 则表示 param


    private Type retType = null;
    private ArrayList<Param> params;

    //TODO: entry是否需要保留
    public BasicBlock entry = null; // 函数体

    private BasicBlock begin;
    private BasicBlock end;

    public Ilist<BasicBlock> bbList = new Ilist<>();

    //TODO: assign to 刘传
    private HashMap<BasicBlock, ArrayList<BasicBlock>> preMap;
    private HashMap<BasicBlock, ArrayList<BasicBlock>> sucMap;
    private HashSet<BasicBlock> BBs;
    public boolean isExternal = false;

    public Function(boolean flag, String name, ArrayList<Param> params, Type retType) {
        this.begin = new BasicBlock();
        this.end = new BasicBlock();
        begin.setNext(end);
        end.setPrev(begin);
        this.name = name;
        this.params = params;
        for (Param param : params) {
            param.parentFunc = this;
        }
        this.retType = retType;
        isExternal = flag;
        bbList.head = begin;
        bbList.tail = end;
    }

    //loop 相关信息
    private HashSet<BasicBlock> loopHeads = new HashSet<>();


    public Function(String name, ArrayList<Param> params, Type retType) {
        this.begin = new BasicBlock();
        this.end = new BasicBlock();
        begin.setNext(end);
        end.setPrev(begin);
        this.name = name;
        this.params = params;
        for (Param param : params) {
            param.parentFunc = this;
        }
        this.retType = retType;
        bbList.head = begin;
        bbList.tail = end;

    }

    public boolean hasBody() {
        return entry != null;
    }

    public boolean hasRet() {
        return !retType.isVoidType();
    }

    //优化所需方法
    public void insertAtBegin(BasicBlock in) {
        in.setPrev(begin);
        in.setNext(begin.getNext());
        begin.getNext().setPrev(in);
        begin.setNext(in);
    }

    public void insertAtEnd(BasicBlock in) {
        in.setPrev(end.getPrev());
        in.setNext(end);
        end.getPrev().setNext(in);
        end.setPrev(in);
    }


    //获取第一个基本块,不是空的链表头
    public BasicBlock getBeginBB() {
        assert begin.getNext() instanceof BasicBlock;
        return (BasicBlock) begin.getNext();
    }

    //获取最后一个基本块,不是空的链表尾
    public BasicBlock getEndBB() {
        assert end.getPrev() instanceof BasicBlock;
        return (BasicBlock) end.getPrev();
    }

    public BasicBlock getEnd() {
        return end;
    }

    public BasicBlock getBegin() {
        return begin;
    }

    public boolean isEmpty() {
        return begin.getNext().equals(end);
    }


//    private String getTypeStr() {
//        if (hasRet()) {
//            return retType.toString();
//        } else {
//            return "void";
//        }
//    }

    private String getTypeStr() {
        if (retType instanceof Type.BasicType || retType instanceof Type.VoidType) {
            return retType.toString();
        } else {
            System.err.println("func ret type error!!!");
            return null;
        }
    }

    public boolean isTimeFunc = false;

    // 输出函数声明
    public String getDeclare() {
        String paramList = params.stream().map(var -> var.getType().toString()).reduce((s, s2) -> s + ", " + s2).orElse("");
        return "declare " + getTypeStr() + " @" + getName() + "(" + paramList + ")";
    }

    // 输出函数定义
    public String getDefinition() {
        if (this.entry == null) {
            throw new AssertionError("Function without body");
        }
        String paramList = params.stream().map(Value::toString).reduce((s, s2) -> s + ", " + s2).orElse("");
        StringBuilder body = new StringBuilder();
        Queue<BasicBlock> queue = new LinkedList<>();
        Set<BasicBlock> enqueued = new HashSet<>();
        queue.offer(this.entry);
        enqueued.add(this.entry);
        while (!queue.isEmpty()) {
            BasicBlock block = queue.poll();
            ILinkNode node = block.getEntry();
            body.append(block).append(":\n");
            while (node.hasNext()) {
                if (OutParam.COND_CNT_DEBUG_FOR_LC) {
                    body.append("  ").append(node).append("     ").append(((Instr) node).getCondCount()).append(" ").append(((Instr) node).isInLoopCond()).append("\n");
                } else {
                    body.append("  ").append(node).append("\n");
                }
                if (node instanceof Instr.Branch) {
                    BasicBlock thenBlock = ((Instr.Branch) node).getThenTarget();
                    BasicBlock elseBlock = ((Instr.Branch) node).getElseTarget();
                    if (!enqueued.contains(thenBlock)) {
                        queue.offer(thenBlock);
                        enqueued.add(thenBlock);
                    }
                    if (!enqueued.contains(elseBlock)) {
                        queue.offer(elseBlock);
                        enqueued.add(elseBlock);
                    }
                    break;
                } else if (node instanceof Instr.Jump) {
                    BasicBlock target = ((Instr.Jump) node).getTarget();
                    if (!enqueued.contains(target)) {
                        queue.offer(target);
                        enqueued.add(target);
                    }
                }
                node = node.getNext();
            }
            body.append("\n");
        }

        return "define dso_local " + getTypeStr() + " @" + getName() + "(" + paramList + ") {\n" + body + "}\n";
    }

    public String output() {

        String paramList = params.stream().map(Value::toString).reduce((s, s2) -> s + ", " + s2).orElse("");
        StringBuilder str = new StringBuilder();
        str.append("define dso_local ").append(getTypeStr()).append(" @").append(getName()).append("(").append(paramList).append(") {\n");
        // for(ILinkNode node = getBeginBB(); !node.equals(end);node = node.getNext()){
        //     BasicBlock bb = (BasicBlock) node;
        //     str.append(bb).append(":\n");
        //     for (ILinkNode instNode = bb.getBeginInstr(); !instNode.equals(bb.getEnd()); instNode = instNode.getNext()) {
        //         // Instr inst = (Instr) instNode;
        //         str.append("\t").append(instNode).append("\n");
        //     }
        // }
        for (BasicBlock bb : bbList) {
            str.append(bb).append(":\n");
            for (Instr inst : bb.instrList) {
                // Instr inst = (Instr) instNode;
                str.append("\t").append(inst).append("\n");
            }
        }
        str.append("}\n");

        return str.toString();
    }

    @Override
    public String getName() {
        return (isTimeFunc ? "_sysy_" : "") + name;
    }

    public ArrayList<Param> getParams() {
        return params;
    }

    public Type getRetType() {
        return this.retType;
    }

    public BasicBlock getBody() {
        return this.entry;
    }

    public void setBody(final BasicBlock body) {
        this.entry = body;
    }

    public void setSucMap(HashMap<BasicBlock, ArrayList<BasicBlock>> sucMap) {
        this.sucMap = sucMap;
    }

    public void setPreMap(HashMap<BasicBlock, ArrayList<BasicBlock>> preMap) {
        this.preMap = preMap;
    }

    public HashMap<BasicBlock, ArrayList<BasicBlock>> getPreMap() {
        return preMap;
    }

    public HashMap<BasicBlock, ArrayList<BasicBlock>> getSucMap() {
        return sucMap;
    }

    public void setBBs(HashSet<BasicBlock> BBs) {
        this.BBs = BBs;
    }

    public HashSet<BasicBlock> getBBs() {
        return BBs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Function function = (Function) o;
        return Objects.equals(name, function.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    // @Override
    // public boolean equals(Object obj) {
    //     return ;
    // }


    public HashSet<BasicBlock> getLoopHeads() {
        return loopHeads;
    }

    public void addLoopHead(BasicBlock bb) {
        loopHeads.add(bb);
    }

    public void inlineToFunc(Function tagFunc, BasicBlock retBB, Instr.Call call, Loop loop) {
        Instr.Phi retPhi = null;
        if (retBB.getEndInstr() instanceof Instr.Phi) {
            retPhi = (Instr.Phi) retBB.getBeginInstr();
        }
        BasicBlock bb = getBeginBB();
        while (bb.getNext() != null) {
            bb.cloneToFunc(tagFunc, loop);
            //bb.cloneToFunc(tagFunc);
            bb = (BasicBlock) bb.getNext();
        }

        ArrayList<Value> callParams = call.getParamList();
        ArrayList<Param> funcParams = this.getParams();
        for (int i = 0; i < callParams.size(); i++) {
            CloneInfoMap.addValueReflect(funcParams.get(i), callParams.get(i));
        }

        bb = getBeginBB();
        while (bb.getNext() != null) {
            //((BasicBlock) CloneInfoMap.getReflectedValue(bb)).fix();
            BasicBlock needFixBB = (BasicBlock) CloneInfoMap.getReflectedValue(bb);

            //修正数据流
//            if (!bb.equals(getBeginBB())) {
//                ArrayList<BasicBlock> pres = new ArrayList<>();
//                for (BasicBlock pre : bb.getPrecBBs()) {
//                    pres.add((BasicBlock) CloneInfoMap.getReflectedValue(pre));
//                }
//                needFixBB.setPrecBBs(pres);
//            }
//
//            if (!(bb.getEndInstr() instanceof Instr.Return)) {
//                ArrayList<BasicBlock> succs = new ArrayList<>();
//                for (BasicBlock succ : bb.getSuccBBs()) {
//                    succs.add((BasicBlock) CloneInfoMap.getReflectedValue(succ));
//                }
//                needFixBB.setSuccBBs(succs);
//            }


            Instr instr = needFixBB.getBeginInstr();
            while (instr.getNext() != null) {
                instr.fix();
                if (instr instanceof Instr.Return && ((Instr.Return) instr).hasValue()) {
                    Instr jumpToRetBB = new Instr.Jump(retBB, needFixBB);
                    instr.insertBefore(jumpToRetBB);
                    retBB.addPre(needFixBB);
                    assert retPhi != null;
                    retPhi.addOptionalValue(((Instr.Return) instr).getRetValue());
                    instr.remove();
                } else if (instr instanceof Instr.Return) {
                    Instr jumpToRetBB = new Instr.Jump(retBB, needFixBB);
                    instr.insertBefore(jumpToRetBB);
                    retBB.addPre(needFixBB);
                    instr.remove();
                }
                instr = (Instr) instr.getNext();
            }
            bb = (BasicBlock) bb.getNext();
        }


    }
}
