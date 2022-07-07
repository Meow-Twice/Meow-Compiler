package lir;

import util.ILinkNode;

import java.util.ArrayList;

public class MachineInst {
    Machine.Block bb;
    Tag tag;

    public ArrayList<Machine.Operand> defOpds = new ArrayList<>();
    public ArrayList<Machine.Operand> useOpds = new ArrayList<>();

    /*
    init and insert at end of the bb
    */
    public MachineInst(Tag tag,Machine.Block insertAtEnd) {
        this.bb = insertAtEnd;
        this.tag = tag;
        if (insertAtEnd != null) {
            insertAtEnd.insts.add(insertAtEnd.insts.size - 1, this);
        }
    }

    /*
    init and inset before inst
    */
    public MachineInst(Tag tag,MachineInst inst) {
            this.bb = inst.bb;
            this.tag = tag;
            if (inst.bb != null) {
                int index = inst.bb.insts.getIndex(inst);
                inst.bb.insts.add(index,this);
            }
    }

    public MachineInst(Tag tag) {
        this.tag = tag;
    }

    public void genDefUse(){
    }
}



