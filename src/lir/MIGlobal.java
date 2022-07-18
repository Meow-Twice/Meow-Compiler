package lir;

import frontend.syntax.Ast;
import mir.GlobalVal;

import java.io.PrintStream;

public class MIGlobal extends MachineInst{
    public Machine.Operand dOpd;

    // GlobalVal.GlobalValue里有所需的一切信息
    GlobalVal.GlobalValue globalValue;
    public MIGlobal(GlobalVal.GlobalValue globalValue, boolean isFloat){
        super(Tag.Global,isFloat);
        this.globalValue = globalValue;
    }

    public MIGlobal(GlobalVal.GlobalValue globalValue,Machine.Block mb){
        super(Tag.Global);
        this.globalValue = globalValue;
        mb.insertAtHead(this);
    }
    @Override
    public void genDefUse() {
        defOpds.add(dOpd);
    }

    @Override
    public void output(PrintStream os, Machine.McFunction f){
        transfer_output(os);
        os.println("ldr\t"+dOpd.toString()+",="+globalValue.name);
    }
}