package backend;

import lir.*;
import mir.Instr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class PeepholeWithDataFlow {
    public Machine.Program program = Machine.Program.PROGRAM;
    public HashMap<HashMap<Machine.Operand,MachineInst>,HashMap<MachineInst,MachineInst>> getLiveRangeInBlock(Machine.Block block){
        HashMap lastDefiner = new HashMap<String,MachineInst>();//Oprand------>last definer
        HashMap lastUserMap = new HashMap<MachineInst,MachineInst>();//last definer---->last user
        for(MachineInst machineInst : block.miList){
            ArrayList<Machine.Operand> defs = machineInst.getMIDefOpds();
            ArrayList<Machine.Operand> uses = machineInst.getMIUseOpds();
            boolean sideEffect = (machineInst instanceof MIBranch) || (machineInst instanceof MICall)
                    ||(machineInst instanceof  MIJump) || (machineInst instanceof MIStore) ||(machineInst instanceof V.Str) ||
                    (machineInst instanceof  MIReturn) ||(machineInst instanceof V.Ret) || (machineInst instanceof MIComment);
            for(Machine.Operand use : uses){
                if(lastDefiner.containsKey(use)){
                    lastUserMap.put(lastDefiner.get(use),machineInst);
                }
            }
            for(Machine.Operand def : defs){
                lastDefiner.put(def,machineInst);
            }
            lastUserMap.put(machineInst,sideEffect?machineInst : null);
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

    private static class BlockLiveInfo {
        private final HashSet<Machine.Operand> liveUse = new HashSet<>();
        private final HashSet<Machine.Operand> liveDef = new HashSet<>();
        private HashSet<Machine.Operand> liveIn = new HashSet<>();
        private HashSet<Machine.Operand> liveOut = new HashSet<>();

    }

    private HashMap<Machine.Block,BlockLiveInfo> analysis(Machine.McFunction func){
        HashMap<Machine.Block,BlockLiveInfo> liveInfoMap = new HashMap<>();
        for(Machine.Block block : func.mbList){
            BlockLiveInfo blockLiveInfo = new BlockLiveInfo();
            liveInfoMap.put(block,blockLiveInfo);

            for(MachineInst inst : block.miList){
                inst.getUseOpds().stream().filter(use -> !blockLiveInfo.liveDef.contains(use)).forEach(blockLiveInfo.liveUse::add);
                inst.getDefOpds().stream().filter(def -> !blockLiveInfo.liveUse.contains(def)).forEach(blockLiveInfo.liveDef::add);

            }

            blockLiveInfo.liveIn.addAll(blockLiveInfo.liveUse);
        }
        boolean finish = false;
        while(!finish){
            finish = true;
            for(Machine.Block block : func.mbList){
                BlockLiveInfo blockLiveInfo = liveInfoMap.get(block);
                HashSet<Machine.Operand> newliveOut = new HashSet<>();
                if(!block.succMB.isEmpty()){
                    for(Machine.Block succ : block.succMB){
                        newliveOut.addAll(liveInfoMap.get(succ).liveIn);
                    }
                }
                if(!newliveOut.equals(blockLiveInfo.liveOut)){
                    finish = false;
                    blockLiveInfo.liveOut = newliveOut;
                    blockLiveInfo.liveIn = new HashSet<>(blockLiveInfo.liveUse);
                    blockLiveInfo.liveOut.stream().filter(operand -> !blockLiveInfo.liveDef.contains(operand)).forEach(blockLiveInfo.liveIn::add);
                }
            }
        }
        return liveInfoMap;
    }

   // private HashMap
    public boolean run(){
        boolean finish = true;
        for(Machine.McFunction function : program.funcList){
            HashMap<Machine.Block,BlockLiveInfo> liveInfoMap = analysis(function);
            for(Machine.Block block : function.mbList){
                HashMap<HashMap<Machine.Operand,MachineInst>,HashMap<MachineInst,MachineInst>> liveRange = getLiveRangeInBlock(block);
                HashMap<Machine.Operand,MachineInst> lastDefiner = (HashMap<Machine.Operand,MachineInst>)liveRange.keySet().toArray()[0];
                HashMap<MachineInst,MachineInst> lastuserMap = liveRange.get(lastDefiner);
                HashSet<Machine.Operand> liveout = liveInfoMap.get(block).liveOut;
                for(MachineInst inst : block.miList){
                    boolean noCond = inst.getCond() == Arm.Cond.Any;
                    boolean noShift = inst.getShift().shift == 0 || inst.getShift().shiftType == Arm.ShiftType.None;
                    MachineInst lastUser = lastuserMap.get(inst);
                    boolean isLastDefInst = inst.getDefOpds().stream().allMatch(def -> lastDefiner.get(def).equals(inst));//这个指令是最后的def
                    boolean defRegInLiveOut = inst.getDefOpds().stream().anyMatch(liveout::contains);//def的后面还要用
                    boolean defNoSp = inst.getDefOpds().stream().noneMatch(def ->  (def.getReg() == Arm.Regs.GPRs.sp));

                    if(!(isLastDefInst && defRegInLiveOut) && noCond){//后面基本快不再使用此变量
                        if(lastUser == null && noShift && defNoSp){//本基本块后面不再使用此变量
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
