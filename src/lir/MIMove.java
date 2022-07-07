package lir;

public class MIMove extends MachineInst{
    Arm.Cond cond;
    Machine.Operand dOpd;
    Machine.Operand rOpd;
    Arm.Shift shift;

    public MIMove(Machine.Block insertAtEnd){
        super(Tag.Mv,insertAtEnd);
        this.cond = Arm.Cond.Any;
    }

   public MIMove(MachineInst inst){
        super(Tag.Mv,inst);
        this.cond = Arm.Cond.Any;
   }

   public boolean operator(MIMove move){
        if(this.cond!=move.cond)
            return this.cond.compareTo(move.cond) < 0;
        if(this.dOpd != this.dOpd)
            return this.dOpd.compareTo(this.dOpd);
        if(this.rOpd != this.rOpd)
           return this.rOpd.compareTo(this.rOpd);
       return false;
   }
}
