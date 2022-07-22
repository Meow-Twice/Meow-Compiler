package backend;

import lir.*;
import mir.Instr;
import mir.type.DataType;
import util.ILinkNode;

import java.util.Objects;

public class PeepholeOptimization {
    public Machine.Program program = Machine.Program.PROGRAM;
    public void run(){
        for(Machine.McFunction function : program.funcList){
            for(Machine.Block mb:function.mbList){
                for(MachineInst inst : mb.miList){
                    if(inst instanceof MIBinary && inst!=mb.getBeginMI()){
                        MIBinary mib = (MIBinary) inst;
                        MachineInst inst_prev = (MachineInst) inst.getPrev();
                        if(inst_prev instanceof MIMove){
                            MIMove miMove = (MIMove) inst_prev;
                            if(miMove.canOptimize()){
                                int value = miMove.getSrc().getValue();
                                switch (mib.getType()){
                                    case Add:
                                        if(mib.getROpd().isImm()){
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
                                        }
                                        else if(mib.getROpd().toString().equals(miMove.getDst().toString())){
                                            mib.setROpd(new Machine.Operand(DataType.I32,value));
                                            miMove.remove();
                                        }
                                        //delete add
                                        if(mib.getROpd().toString().equals("#0") && mib.getDst().toString().equals(mib.getLOpd().toString())){
                                            mib.remove();
                                        }
                                        break;
                                    case Sub:
                                        if(mib.getROpd().isImm()){
                                            break;
                                        }
                                        //delete move
                                        if(mib.getLOpd().toString().equals(miMove.getDst().toString())){
                                            //swap+rsb
                                            mib.setLOpd(mib.getROpd());
                                            mib.setROpd(new Machine.Operand(DataType.I32,value));
                                            mib.setTag(MachineInst.Tag.Rsb);
                                            miMove.remove();
                                        }
                                        else if(mib.getROpd().toString().equals(miMove.getDst().toString())){
                                            mib.setROpd(new Machine.Operand(DataType.I32,value));
                                            miMove.remove();
                                        }
                                        if(mib.getROpd().toString().equals("#0") && mib.getDst().toString().equals(mib.getLOpd().toString()) && mib.getType() == MachineInst.Tag.Sub){
                                            mib.remove();
                                        }
                                        break;
                                    case Rsb:
                                        if(mib.getROpd().isImm()){
                                            break;
                                        }
                                        //delete move
                                        if(mib.getLOpd().toString().equals(miMove.getDst().toString())){
                                            //swap+rsb
                                            mib.setLOpd(mib.getROpd());
                                            mib.setROpd(new Machine.Operand(DataType.I32,value));
                                            mib.setTag(MachineInst.Tag.Sub);
                                            miMove.remove();
                                        }
                                        else if(mib.getROpd().toString().equals(miMove.getDst().toString())){
                                            mib.setROpd(new Machine.Operand(DataType.I32,value));
                                            miMove.remove();
                                        }
                                        if(mib.getROpd().toString().equals("#0") && mib.getDst().toString().equals(mib.getLOpd().toString()) && mib.getType() == MachineInst.Tag.Sub){
                                            mib.remove();
                                        }
                                        break;



                                    }
                            }
                        }
                    }
                    else if(inst instanceof MIMove){
                        MIMove miMove = (MIMove) inst;
                        if(miMove.getDst().toString().equals(miMove.getSrc().toString())){
                            if(miMove.getShift()== Arm.Shift.NONE_SHIFT || miMove.getShift().shift == 0){
                                miMove.remove();
                            }
                        }
                    }
                    else if(inst instanceof MIJump){
                        if(inst == mb.getEndMI() && noExtraInst(mb,((MIJump) inst).getTarget())){
                            inst.remove();
                        }
                    }
                }
            }
        }
    }
    public boolean noExtraInst(Machine.Block mb,Machine.Block target){
        ILinkNode i =  mb.getNext();
        int size = 0;
        while(i!=program.mainMcFunc.getEndMB().getNext() && size == 0){
            if(i == target){
                return true;
            }
            size+=((Machine.Block)i).miList.size;
            i = (Machine.Block) i.getNext();
        }
        return false;
    }
}
