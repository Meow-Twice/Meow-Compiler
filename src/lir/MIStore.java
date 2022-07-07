package lir;

public class MIStore extends MIAccess{
    Machine.Operand data;
    Machine.Operand addr;
    public MIStore(Machine.Block insertAtEnd){
        super(Tag.Store,insertAtEnd);
    }

    @Override
    public void genDefUse() {
        useOpds.add(data);
        useOpds.add(addr);
    }
}
