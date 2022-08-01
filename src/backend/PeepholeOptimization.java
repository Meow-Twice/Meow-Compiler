package backend;

import lir.*;
import mir.Instr;
import mir.type.DataType;
import util.ILinkNode;

import java.util.Objects;

public class PeepholeOptimization {
    public Machine.Program program = Machine.Program.PROGRAM;
    public PeepholeWithDataFlow peepholeWithDataFlow = new PeepholeWithDataFlow();
    public boolean run(){
        boolean finish = true;
        for(Machine.McFunction function : program.funcList){
            for(Machine.Block mb:function.mbList){
                for(MachineInst inst : mb.miList){
                    if(inst instanceof MIBinary && inst!=mb.getBeginMI() && !inst.isFloat){
                        MIBinary mib = (MIBinary) inst;
                        MachineInst inst_prev = (MachineInst) inst.getPrev();
                        if(inst_prev instanceof MIMove && !inst_prev.isFloat){
                            MIMove miMove = (MIMove) inst_prev;
                            if(miMove.canOptimize()){
                                int value = miMove.getSrc().getValue();
                                switch (mib.getType()){
                                    case Add:
                                        if(mib.getROpd().is_I_Imm()){
                                            //TODO:有进一步优化的空间
                                            break;
                                        }
                                        //delete move
                                        if(mib.getLOpd().toString().equals(miMove.getDst().toString())){
                                            //swap
                                            mib.setLOpd(mib.getROpd());
                                            mib.setROpd(new Machine.Operand(DataType.I32,value));
                                            //TODO:直接删MOVE可能会有风险
                                            miMove.remove();
                                            finish = false;
                                        }
                                        else if(mib.getROpd().toString().equals(miMove.getDst().toString())){
                                            mib.setROpd(new Machine.Operand(DataType.I32,value));
                                            miMove.remove();
                                            finish = false;
                                        }
                                        //delete add
                                        if(mib.getROpd().toString().equals("#0") && mib.getDst().toString().equals(mib.getLOpd().toString())){
                                            mib.remove();
                                            finish = false;
                                        }
                                        break;
                                    case Sub:
                                        if(mib.getROpd().is_I_Imm()){
                                            break;
                                        }
                                        //delete move
                                        if(mib.getLOpd().toString().equals(miMove.getDst().toString())){
                                            //swap+rsb
                                            mib.setLOpd(mib.getROpd());
                                            mib.setROpd(new Machine.Operand(DataType.I32,value));
                                            mib.setTag(MachineInst.Tag.Rsb);
                                            miMove.remove();
                                            finish = false;
                                        }
                                        else if(mib.getROpd().toString().equals(miMove.getDst().toString())){
                                            mib.setROpd(new Machine.Operand(DataType.I32,value));
                                            miMove.remove();
                                            finish = false;
                                        }
                                        if(mib.getROpd().toString().equals("#0") && mib.getDst().toString().equals(mib.getLOpd().toString()) && mib.getType() == MachineInst.Tag.Sub){
                                            mib.remove();
                                            finish = false;
                                        }
                                        break;
                                    case Rsb:
                                        if(mib.getROpd().is_I_Imm()){
                                            break;
                                        }
                                        //delete move
                                        if(mib.getLOpd().toString().equals(miMove.getDst().toString())){
                                            //swap+rsb
                                            mib.setLOpd(mib.getROpd());
                                            mib.setROpd(new Machine.Operand(DataType.I32,value));
                                            mib.setTag(MachineInst.Tag.Sub);
                                            miMove.remove();
                                            finish = false;
                                        }
                                        else if(mib.getROpd().toString().equals(miMove.getDst().toString())){
                                            mib.setROpd(new Machine.Operand(DataType.I32,value));
                                            miMove.remove();
                                            finish = false;
                                        }
                                        if(mib.getROpd().toString().equals("#0") && mib.getDst().toString().equals(mib.getLOpd().toString()) && mib.getType() == MachineInst.Tag.Sub){
                                            mib.remove();
                                            finish = false;
                                        }
                                        break;



                                    }
                            }
                        }
                    }
                    else if(inst instanceof MIMove){
                        MIMove miMove = (MIMove) inst;
                        if(miMove.getDst().toString().equals(miMove.getSrc().toString()) && (miMove.getShift()== Arm.Shift.NONE_SHIFT || miMove.getShift().shift == 0)){
                            //move r1,r1(remove)
                                miMove.remove();
                                finish = false;
                        }
                        else if(miMove.getNext() instanceof MIMove){
                            //move r1,r4(remove)
                            //move r1,r5
                            //remove useless move
                            MIMove next = (MIMove) miMove.getNext();
                            //(next.getShift()== Arm.Shift.NONE_SHIFT || next.getShift().shift == 0)
                            if(next.getDst().toString().equals(miMove.getDst().toString()) && !next.getSrc().toString().equals(miMove.getDst().toString()) && miMove.getCond().toString().equals(next.getCond().toString())){
                                miMove.remove();
                                finish = false;
                            }
                            //move r1,r2
                            //move r2,r1(remove)
                            else if(miMove.getDst().toString().equals(next.getSrc().toString()) && miMove.getSrc().toString().equals(next.getDst().toString()) && miMove.getCond().toString().equals(next.getCond().toString()) && (next.getShift()== Arm.Shift.NONE_SHIFT || next.getShift().shift == 0) &&
                                    (miMove.getShift()== Arm.Shift.NONE_SHIFT || miMove.getShift().shift == 0)){
                                next.remove();
                                finish = false;
                            }
                        }
                    }
                    else if(inst instanceof MIJump){
                        if(inst == mb.getEndMI() && noExtraInst(mb,((MIJump) inst).getTarget(),function)){
                            inst.remove();
                            finish = false;
                        }
                    }
                    else if(inst instanceof  MILoad){
                        if(inst.getPrev() instanceof MIStore){
                            MILoad load = (MILoad) inst;
                            MIStore store = (MIStore) inst.getPrev();
                            if(isSameAddress(load,store)){
                                new MIMove(inst,load.getData(),store.getData());
                                inst.remove();
                                finish = false;
                            }
                        }
                    }
                    else if(inst instanceof  MICompare){
                        if(inst.getNext() instanceof MIMove && inst.getNext().getNext() instanceof MIMove){
                            MICompare inst1 = (MICompare) inst;
                            MIMove inst2 = (MIMove) inst.getNext();
                            MIMove inst3 = (MIMove) inst.getNext().getNext();
                            if(inst1.getROpd().is_I_Imm() && inst1.getROpd().get_I_Imm() == 0 && inst2.getSrc().is_I_Imm() && inst2.getSrc().get_I_Imm() == 1 && inst3.getSrc().is_I_Imm() && inst3.getSrc().get_I_Imm() == 0 && inst1.getLOpd().toString().equals(inst2.getDst().toString())
                            && inst1.getLOpd().toString().equals(inst3.getDst().toString()) && inst2.getCond() == Arm.Cond.Ne && inst3.getCond() == Arm.Cond.Eq && inst2.getShift().isNone() && inst3.getShift().isNone()){
                                inst3.remove();
                                finish = false;
                            }
                        }
                    }
                }
            }
        }
        return finish;
    }
    public boolean isSameAddress(MILoad load,MIStore store){
        if(load.getAddr().toString().equals(store.getAddr().toString()) && load.getOffset().toString().equals(store.getOffset().toString()) && load.getShift().toString().equals(store.getShift().toString())){
            return true;
        }
        return false;
    }

    public boolean noExtraInst(Machine.Block mb,Machine.Block target,Machine.McFunction function){
        ILinkNode i =  mb.getNext();
        int size = 0;
        while(i!=function.getEndMB().getNext() && size == 0){
            if(i == target){
                return true;
            }
            size+=((Machine.Block)i).miList.size;
            i =  i.getNext();
        }
        return false;
    }

    public void run_all(){
        boolean finish = false;
        while(!finish){
            finish = run();
       //     finish &= peepholeWithDataFlow.run();
        }
    }
}
