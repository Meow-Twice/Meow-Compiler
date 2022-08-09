package frontend.semantic;

import mir.Value;
import mir.type.Type;
import util.ILinkNode;
import util.Ilist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static mir.Constant.ConstantInt.CONST_0;

/**
 * 储存变量初始化的初值
 * <p>
 * 人为约束: 初始化器的种类以及数组维度/长度，必须保证和类型中的数组信息一致
 */
public abstract class Initial {
    private final Type type; // LLVM IR 的初始值是含有类型信息的

    public abstract ArrayList<Value> getFlattenInit();

    public abstract Flatten flatten();

    @Override
    public abstract String toString();

    public static class ArrayInit extends Initial {

        public ArrayInit(Type type) {
            super(type);
        }

        private final ArrayList<Initial> inits = new ArrayList<>();

        public int length() {
            return inits.size();
        }

        public void add(Initial init) {
            inits.add(init);
        }

        public Initial get(int index) {
            return inits.get(index);
        }

        @Override
        public String toString() {
            String init = inits.stream().map(Initial::toString).reduce((s, s2) -> s + ", " + s2).orElse("");
            return getType() + " [" + init + "]";
        }

        @Override
        public ArrayList<Value> getFlattenInit() {
            ArrayList<Value> result = new ArrayList<>();
            for (Initial init : inits) {
                ArrayList<Value> list1 = init.getFlattenInit();
                result.addAll(list1);
            }
            return result;
        }

        @Override
        public Flatten flatten() {
            return null;
        }
    }

    public static class ValueInit extends Initial {
        private final Value value;

        public ValueInit(Value value, Type type) {
            super(type);
            this.value = value;
        }

        @Override
        public String toString() {
            return getType() + " " + value;
        }

        public Value getValue() {
            return this.value;
        }

        @Override
        public ArrayList<Value> getFlattenInit() {
            ArrayList<Value> result = new ArrayList<>();
            result.add(value);
            return result;
        }

        @Override
        public Flatten flatten() {
            return null;
        }

    }

    // 初值为0的初始化
    public static class ZeroInit extends Initial {

        public ZeroInit(Type type) {
            super(type);
        }

        @Override
        public String toString() {
            return getType() + " zeroinitializer";
        }

        @Override
        public ArrayList<Value> getFlattenInit() {
            int size;
            if (getType().isArrType()) {
                size = ((Type.ArrayType) getType()).getFlattenSize();
            } else {
                assert getType().isBasicType();
                size = 1;
            }
            return new ArrayList<>(Collections.nCopies(size, CONST_0));
            // ArrayList<Value> result = new ArrayList<>();
            // result.add(new Constant.ConstantInt(0));
            // return result;
        }

        @Override
        public Flatten flatten() {
            return null;
        }
    }

    // 用于编译期不可求值的初始化
    public static class ExpInit extends Initial {
        private final Value result;

        public ExpInit(Value result, Type type) {
            super(type);
            this.result = result;
        }

        @Override
        public String toString() {
            throw new AssertionError("non-evaluable initializer should never be output");
        }

        public Value getResult() {
            return this.result;
        }

        @Override
        public ArrayList<Value> getFlattenInit() {
            ArrayList<Value> result = new ArrayList<>();
            result.add(this.result);
            return result;
        }

        @Override
        public Flatten flatten() {
            return null;
        }

    }

    public Initial(final Type type) {
        this.type = type;
    }

    public Type getType() {
        return this.type;
    }

    /**
     * 展平初始化，得到分块形式, 对应汇编里的 .fill (如果是单个则为 .word)
     */
    public static class Flatten extends Ilist<Flatten.Entry> {
        public static class Entry extends ILinkNode {
            public Value value;
            public int count;

            public Entry(Value value, int count) {
                this.value = value;
                this.count = count;
            }

            public boolean canMerge(Entry that) {
                assert that == this.getNext();
                return this.value.equals(that.value);
            }

            public void merge(Entry that) {
                assert that == this.getNext();
                assert canMerge(that);
                this.count += that.count;
                that.remove();
            }

            @Override
            public String toString() {
                if (count == 1) {
                    return ".word\t" + value;
                } else {
                    return ".fill\t" + count + ", 4, " + value;
                }
            }
        }

        // 判断一段初始化是否全零
        public boolean isZero() {
            for (Entry e : this) {
                if (e.value.equals(CONST_0)) {
                    return false;
                }
            }
            return true;
        }

        // 相邻的初始化块不能是相同值
        public boolean isFullyMerged() {
            for (Entry e : this) {
                if (e == getEnd()) {
                    break;
                }
                if (e.canMerge((Entry) e.getNext())) {
                    return false;
                }

            }
            return true;
        }

        // 前后两段的拼接
        public void concat(Flatten that) {
            that.getBegin().setPrev(this.getEnd());
            that.getEnd().setNext(this.tail);
            this.getEnd().setNext(that.getBegin());
            this.tail.setPrev(that.getEnd());
            size += that.size;
        }

        // 合并所有可以合并的项
        public void mergeAll() {
            int sizeBefore = sizeInWords();
            for (ILinkNode cur = getBegin(); cur.hasNext(); cur = cur.getNext()) {
                assert cur instanceof Entry;
                Entry entry = (Entry) cur;
                while (entry.getNext() != tail && entry.getNext() instanceof Entry && entry.canMerge((Entry) entry.getNext())) {
                    entry.merge((Entry) entry.getNext());
                    size--;
                }
            }
            int sizeAfter = sizeInWords();
            assert sizeBefore == sizeAfter;
        }

        public int sizeInWords() {
            int size = 0;
            for (Entry e : this) {
                size += e.count;
            }
            return size;
        }

        public int sizeInBytes() {
            return sizeInWords() * 4;
        }

        @Override
        public String toString() {
            StringBuilder stb = new StringBuilder();
            for (Entry e : this) {
                stb.append(e).append("\n");
            }
            return stb.toString();
        }

        // 获取当前初始化中所有非零的项，转化成 <Index, Value> 形式，其中 Index 是偏移的字数 (寻址时需要左移 2)
        // 一个 Map.Entry 是一组 <Index, Value> 对
        public Map<Integer, Value> listNonZeros() {
            int index = 0;
            LinkedHashMap<Integer, Value> nonZeros = new LinkedHashMap<>(); // 遍历顺序是插入顺序
            for (Entry e : this) {
                if (e.value.equals(CONST_0)) {
                    // 连续 count 个值都要手动赋值
                    for (int k = 0; k < e.count; k++) {
                        nonZeros.put(index, e.value);
                        index++;
                    }
                } else {
                    index += e.count; // 略过
                }
            }
            return nonZeros;
        }
    }
}
