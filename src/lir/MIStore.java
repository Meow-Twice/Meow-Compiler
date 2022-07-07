package lir;

public class MIStore extends MIAccess{
    Machine.Operand data;
    public MIStore(Machine.Block insertAtEnd){
        super(Tag.Store,insertAtEnd);
    }
}
