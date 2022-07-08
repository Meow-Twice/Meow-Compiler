package lir;

public class MIStore extends MIAccess{
    Machine.Operand data;
    Machine.Operand addr;
    public MIStore(Machine.Block insertAtEnd){
        super(Tag.Store,insertAtEnd);
    }

    public MIStore(Machine.Operand data, Machine.Operand addr, Machine.Block insertAtEnd) {
        super(Tag.Load, insertAtEnd);
        this.data = data;
        this.addr = addr;
        genDefUse();
    }

    @Override
    public void genDefUse() {
        useOpds.add(data);
        useOpds.add(addr);
    }
}
