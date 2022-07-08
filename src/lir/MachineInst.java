package lir;

import util.ILinkNode;

import java.io.PrintStream;
import java.util.ArrayList;

public class MachineInst {
    Machine.Block bb;
    Tag tag;
    boolean isFloat;
    public ArrayList<Machine.Operand> defOpds = new ArrayList<>();
    public ArrayList<Machine.Operand> useOpds = new ArrayList<>();
    /*
    init and insert at end of the bb
    */
    public MachineInst(Tag tag,Machine.Block insertAtEnd,boolean isFloat) {
        this.bb = insertAtEnd;
        this.tag = tag;
        this.isFloat = isFloat;
        if (insertAtEnd != null) {
            insertAtEnd.insts.add(insertAtEnd.insts.size - 1, this);
        }
    }

    /*
    init and inset before inst
    */
    public MachineInst(Tag tag,MachineInst inst,boolean isFloat) {
            this.bb = inst.bb;
            this.isFloat = isFloat;
            this.tag = tag;
            if (inst.bb != null) {
                int index = inst.bb.insts.getIndex(inst);
                inst.bb.insts.add(index,this);
            }
    }

    public MachineInst(Tag tag,boolean isFloat) {
        this.tag = tag;
        this.isFloat = isFloat;
    }

    public void genDefUse(){
    }

    public void transfer_output(PrintStream os){
        if(bb!=null && bb.con_tran == this){
            os.println("@ control transfer");
        }
    }
}



