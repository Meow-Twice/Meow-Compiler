package backend;

import lir.*;
import mir.Instr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class PeepholeWithDataFlow {
    public Machine.Program program = Machine.Program.PROGRAM;
    public HashMap<HashMap<Machine.Operand,MachineInst>,HashMap<MachineInst,MachineInst>> getLiveRangeInBlock(Machine.Block block){
        HashMap lastDefiner = new HashMap<Machine.Operand,MachineInst>();//Oprand------>last definer
        HashMap lastUserMap = new HashMap<MachineInst,MachineInst>();//last definer---->last user
        for(MachineInst machineInst : block.miList){
            ArrayList<Machine.Operand> defs = machineInst.getMIDefOpds();
            ArrayList<Machine.Operand> uses = machineInst.getMIUseOpds();
            boolean sideEffet = (machineInst instanceof MIBranch) || (machineInst instanceof MICall)
                    ||(machineInst instanceof  MIJump) || (machineInst instanceof MIStore) ||
                    (machineInst instanceof  MIReturn) || (machineInst instanceof MIComment);
            for(Machine.Operand use : uses){
                if(lastDefiner.containsKey(use)){
                    lastUserMap.put(lastDefiner.get(use),machineInst);
                }
            }
            for(Machine.Operand def : defs){
                lastDefiner.put(def,machineInst);
            }
            lastUserMap.put(machineInst,sideEffet?machineInst : null);
        }
        HashMap<HashMap<Machine.Operand,MachineInst>,HashMap<MachineInst,MachineInst>> result = new HashMap<>();
        result.put(lastDefiner,lastUserMap);
        return result;
    }

    public void replaceReg(MachineInst inst, Machine.Operand origin , Machine.Operand target){
        if(inst instanceof MIBinary){
            MIBinary miBinary = (MIBinary) inst;
            if(miBinary.getLOpd().toString().equals(origin.toString())){
                miBinary.setLOpd(target);
            }
            if(miBinary.getROpd().toString().equals(origin.toString())){
                miBinary.setROpd(target);
            }
        }
        else if(inst instanceof MICompare){
            MICompare miCompare = (MICompare) inst;
            if(miCompare.getLOpd().toString().equals(origin.toString())){
                miCompare.setLOpd(target);
            }
            if(miCompare.getROpd().toString().equals(origin.toString())){
                miCompare.setROpd(target);
            }
        }
        else if(inst instanceof MIFma){
            MIFma miFma = (MIFma) inst;
            if(miFma.getlOpd().toString().equals(origin.toString())){
                miFma.setLOpd(target);
            }
            if(miFma.getrOpd().toString().equals(origin.toString())){
                miFma.setROpd(target);
            }
            if(miFma.getAcc().toString().equals(origin.toString())){
                miFma.setAcc(target);
            }
        }
        else if(inst instanceof MILoad){
            MILoad miLoad = (MILoad) inst;
            if(miLoad.getAddr().toString().equals(origin.toString())){
                miLoad.setAddr(target);
            }
            if(miLoad.getOffset().toString().equals(origin.toString())){
                miLoad.setOffset(target);
            }
        }
        else if(inst instanceof MILongMul){
            MILongMul miLongMul = (MILongMul) inst;
            if(miLongMul.getLOpd().toString().equals(origin.toString())){
                miLongMul.setLOpd(target);
            }
            if(miLongMul.getROpd().toString().equals(origin.toString())){
                miLongMul.setROpd(target);
            }
        }
        else if(inst instanceof MIMove){
            MIMove miMove = (MIMove) inst;
            if(miMove.getSrc().toString().equals(origin.toString())){
                miMove.setSrc(target);
            }
        }
        else if(inst instanceof MIStore){
            MIStore miStore = (MIStore) inst;
            if(miStore.getData().toString().equals(origin.toString())){
                miStore.setData(target);
            }
            if(miStore.getAddr().toString().equals(origin.toString())){
                miStore.setAddr(target);
            }
            if(miStore.getOffset().toString().equals(origin.toString())){
                miStore.setOffst(target);
            }
        }
    }

    public boolean run(){
        boolean finish = true;
        for(Machine.McFunction function : program.funcList){
            for(Machine.Block block : function.mbList){
                HashMap<HashMap<Machine.Operand,MachineInst>,HashMap<MachineInst,MachineInst>> liveRange = getLiveRangeInBlock(block);
                HashMap<Machine.Operand,MachineInst> lastDefiner = (HashMap<Machine.Operand,MachineInst>)liveRange.keySet().toArray()[0];
                HashMap<MachineInst,MachineInst> lastuserMap = liveRange.get(lastDefiner);
                HashSet<Machine.Operand> liveout = block.liveOutSet;
                for(MachineInst inst : block.miList){
                    boolean noCond = inst.getCond() == Arm.Cond.Any;
                    boolean noShift = inst.getShift().shift == 0 || inst.getShift().shiftType == Arm.ShiftType.None;
                    MachineInst lastUser = lastuserMap.get(inst);
                    boolean isLastDefInst = inst.defOpds.stream().allMatch(def -> lastDefiner.get(def).equals(inst));
                    boolean defRegInLiveOut = inst.defOpds.stream().anyMatch(liveout::contains);
                    boolean defNoSp = inst.defOpds.stream().noneMatch(def -> def instanceof Arm.Reg && def.getReg() == Arm.Regs.GPRs.sp);

                    if(!(isLastDefInst && defRegInLiveOut) && noCond){
                        if(lastUser == null && noShift && defNoSp){
                            //no side effect and noshift and no sp
                            inst.remove();
                            finish = false;
                            continue;
                        }

                    }

                }
            }
        }
        return finish;
    }

}
