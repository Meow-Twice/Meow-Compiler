package backend;

import lir.*;

import static lir.Arm.Cond.*;
import static lir.Arm.Cond.Ge;

public class Branch2cond {
    public Machine.Program program = Machine.Program.PROGRAM;
    public final int optimize_cnt = 10;
    public void run(){
        for(Machine.McFunction function : program.funcList){
            for(Machine.Block mb:function.mbList){
                /*
                find pattern:
                block1:
                    b.cond block2
                    b block5
                block2:
                block3:(true)
                    some insts
                block4:(next)
                    some insts
                block5:
                block6:
                    some insts
                    b block4
                */
                if(mb.getEndMI() instanceof MIBranch){
                    MIBranch miBranch = (MIBranch) mb.getEndMI();
                    Machine.Block true_block = (Machine.Block)miBranch.getTrueTargetBlock().getNext();
                    Machine.Block false_block = (Machine.Block)miBranch.getFalseTargetBlock().getNext();
                    Arm.Cond cond = miBranch.getCond();
                    boolean can_optimize = true;
                    int count = 0;
                    for(MachineInst inst : true_block.miList){
                        count++;
                        if(inst.getCond()!= Arm.Cond.Any){
                            can_optimize = false;
                        }
                    }
                    if(count > optimize_cnt){
                        // too many instructions, does not worth it
                        can_optimize = false;
                    }
                     /*
                block1:
                block2:
                block3:(true)
                    some insts+cond
                    b.cond_oppsite block5
                block4:(next)
                    some insts
                block5:
                block6:
                    some insts
                    b block4
                */
                    if(can_optimize){
                        miBranch.remove();
                        for(MachineInst inst : true_block.miList){
                            inst.setCond(miBranch.getCond());
                        }
                        MachineInst end = true_block.getEndMI();
                        new MIJump(getOppCond(miBranch.getCond()),miBranch.getFalseTargetBlock(),true_block);
                    }
                }
            }
        }

    }
    public Arm.Cond getOppCond(Arm.Cond cond){
        return switch (cond) {
            case Eq -> Ne;
            case Ne -> Eq;
            case Ge -> Lt;
            case Gt -> Le;
            case Le -> Gt;
            case Lt -> Ge;
            case Hi, Pl, Any -> Any;
        };
    }
}
