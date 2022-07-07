package lir;

public class MIBranch extends MachineInst{
    Arm.Cond cond ;
    Machine.Block target;
    public MIBranch(Machine.Block insertAtEnd){
        super(Tag.Branch,insertAtEnd);
    }
}
