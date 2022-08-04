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

    public boolean replaceReg(MachineInst inst, Machine.Operand origin , Machine.Operand target){
        if(inst instanceof MIBinary){
            MIBinary miBinary = (MIBinary) inst;
            if(miBinary.getLOpd().toString().equals(origin.toString())){
                miBinary.setLOpd(target);
            }
            if(miBinary.getROpd().toString().equals(origin.toString())){
                miBinary.setROpd(target);
            }
            return true;
        }
        else if(inst instanceof MICompare){
            MICompare miCompare = (MICompare) inst;
            if(miCompare.getLOpd().toString().equals(origin.toString())){
                miCompare.setLOpd(target);
            }
            if(miCompare.getROpd().toString().equals(origin.toString())){
                miCompare.setROpd(target);
            }
            return true;
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
            return true;
        }
        else if(inst instanceof MILoad){
            MILoad miLoad = (MILoad) inst;
            if(miLoad.getAddr().toString().equals(origin.toString())){
                miLoad.setAddr(target);
            }
            if(miLoad.getOffset().toString().equals(origin.toString())){
                miLoad.setOffset(target);
            }
            return true;
        }
        else if(inst instanceof MILongMul){
            MILongMul miLongMul = (MILongMul) inst;
            if(miLongMul.getLOpd().toString().equals(origin.toString())){
                miLongMul.setLOpd(target);
            }
            if(miLongMul.getROpd().toString().equals(origin.toString())){
                miLongMul.setROpd(target);
            }
            return true;
        }
        else if(inst instanceof MIMove){
            MIMove miMove = (MIMove) inst;
            if(miMove.getSrc().toString().equals(origin.toString())){
                miMove.setSrc(target);
            }
            return true;
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
            return true;
        }
        else if(inst instanceof V.Ldr){
            V.Ldr ldr = (V.Ldr) inst;
            if (ldr.getAddr()!=null && ldr.getAddr().toString().equals(origin.toString())) {
                ldr.setAddr(target);
            }
            if(ldr.getOffset()!=null && ldr.getOffset().toString().equals(origin.toString())){
                ldr.setOffset(target);
            }
            return true;
        }
        else if(inst instanceof V.Str){
            V.Str str = (V.Str) inst;
            if(str.getAddr()!=null && str.getAddr().toString().equals(origin.toString())){
                str.setAddr(target);
            }
            if(str.getData()!=null && str.getData().toString().equals(origin.toString())){
                str.setData(target);
            }
            if(str.getOffset() != null && str.getOffset().toString().equals(origin.toString())){
                str.setOffset(target);
            }
            return true;
        }
        else if(inst instanceof V.Mov){
            V.Mov mov = (V.Mov) inst;
            if(mov.getSrc()!=null && mov.getSrc().toString().equals(origin.toString())){
                mov.setSrc(target);
            }
            return true;
        }
        else if(inst instanceof V.Cvt){
            V.Cvt cvt = (V.Cvt) inst;
            if (cvt.getSrc()!=null && cvt.getSrc().toString().equals(origin.toString())) {
                cvt.setSrc(target);
            }
            return true;
        }
        else if(inst instanceof V.Binary){
            V.Binary binary = (V.Binary) inst;
            if(binary.getLOpd()!=null && binary.getLOpd().toString().equals(origin.toString())){
                binary.setLOpd(target);
            }
            if(binary.getROpd()!=null && binary.getROpd().toString().equals(origin.toString())){
                binary.setROpd(target);
            }
            return true;
        }
        else if(inst instanceof V.Neg){
            V.Neg neg = (V.Neg) inst;
            if(neg.getSrc()!=null && neg.getSrc().toString().equals(origin.toString())){
                neg.setSrc(target);
            }
            return true;
        }
        else if(inst instanceof V.Cmp){
            V.Cmp cmp = (V.Cmp) inst;
            if (cmp.getLOpd()!=null && cmp.getLOpd().toString().equals(origin.toString())) {
                cmp.setLOpd(target);
            }
            if(cmp.getROpd()!=null && cmp.getROpd().toString().equals(origin.toString())){
                cmp.setROpd(target);
            }
            return true;
        }
        return false;
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

                    if(!(isLastDefInst && defRegInLiveOut) && noCond) {//后面基本快不再使用此变量
                        if (lastUser == null && noShift && defNoSp) {//本基本块后面不再使用此变量
                            //no side effect and noshift and no sp
                            inst.remove();
                            finish = false;
                            continue;
                        }

                        //mov a,b
                        //inst(last user of a)
                        //--->
                        //inst(replace a with b)
                        if (inst instanceof MIMove && noShift) {
                            MIMove miMove = (MIMove) inst;
                            if (!miMove.getSrc().is_I_Imm() && inst != block.getEndMI()) {
                                MachineInst next = (MachineInst) miMove.getNext();
                                Machine.Operand src = miMove.getSrc();
                                Machine.Operand dst = miMove.getDst();
                                if (next == lastUser) {
                                    if (replaceReg(next, dst, src)) {
                                        //successfully replace---->remove
                                        inst.remove();
                                        finish = false;
                                        continue;
                                    }

                                }
                            }
                        }

                        //vmov a,b(same type)
                        //inst(last user of a)
                        //--->
                        //inst(replace a with b)
                        if (inst instanceof V.Mov && noShift) {
                            V.Mov vMove = (V.Mov) inst;
                            if (!vMove.getSrc().is_I_Imm() && inst != block.getEndMI()) {
                                MachineInst next = (MachineInst) vMove.getNext();
                                Machine.Operand src = vMove.getSrc();
                                Machine.Operand dst = vMove.getDst();
                                if (next == lastUser && src.getReg() instanceof Arm.Regs.FPRs && dst.getReg() instanceof Arm.Regs.FPRs) {
                                    if (replaceReg(next, dst, src)) {
                                        //successfully replace---->remove
                                        inst.remove();
                                        finish = false;
                                        continue;
                                    }

                                }
                            }
                        }


                        //mov a,imm
                        //cmp b,a
                        //----->
                        //cmp b,imm
                        if (inst instanceof MIMove && noShift) {
                            MIMove miMove = (MIMove) inst;
                            if (miMove.getSrc().is_I_Imm() && miMove.encode_imm(miMove.getSrc().get_I_Imm()) && inst != block.getEndMI() && inst.getNext() instanceof MICompare) {
                                //can optimize
                                MICompare next = (MICompare) inst.getNext();
                                if (next == lastUser && next.getShift().isNone() && !next.getLOpd().toString().equals(next.getROpd().toString()) && next.getROpd().toString().equals(next.getLOpd().toString())) {
                                    if (replaceReg(next, miMove.getDst(), miMove.getSrc())) {
                                        inst.remove();
                                        finish = false;
                                        continue;
                                    }
                                }

                            }
                        }

                    }
                }
            }
        }
        return finish;
    }

}
