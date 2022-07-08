package lir;

import java.io.PrintStream;

public class MIMove extends MachineInst{
    Arm.Cond cond;
    Machine.Operand dOpd;
    Machine.Operand sOpd;
    Arm.Shift shift;

    public MIMove(Machine.Block insertAtEnd,boolean isFloat){
        super(Tag.Mv,insertAtEnd,isFloat);
        this.cond = Arm.Cond.Any;

    }

    public boolean encode_imm(int imm){
        for (int ror = 0; ror < 32; ror += 2) {
            if ((imm & ~0xFF) == 0) {
                return true;
            }
            imm = (imm << 2) | (imm  >> 30);
        }
        return false;
    }
   public MIMove(MachineInst inst,boolean isFloat){
        super(Tag.Mv,inst,isFloat);
        this.cond = Arm.Cond.Any;
   }

   public boolean operator(MIMove move){
        if(this.cond!=move.cond)
            return this.cond.compareTo(move.cond) < 0;
        if(this.dOpd != this.dOpd)
            return this.dOpd.compareTo(this.dOpd);
        if(this.sOpd != this.sOpd)
           return this.sOpd.compareTo(this.sOpd);
       return false;
   }

    @Override
    public void genDefUse() {
        defOpds.add(dOpd);
        useOpds.add(sOpd);
    }

    public void output(PrintStream os){
        transfer_output(os);
        if(sOpd.type == Machine.Operand.Type.Immediate && encode_imm(sOpd.id)){
            int imm = sOpd.id;
            if(imm>>16 == 0){
                os.println("movw"+cond+"\t"+dOpd.toString()+",#"+imm);
            }
            else{
                os.println("ldr"+cond+"\t"+dOpd.toString()+",="+imm);
            }
        }
        else{
            os.print("mov"+cond+"\t"+dOpd.toString()+","+sOpd.toString());
            os.println(","+shift.toString());
        }

    }
}
