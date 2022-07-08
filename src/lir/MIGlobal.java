package lir;

import frontend.syntax.Ast;

import java.io.PrintStream;

public class MIGlobal extends MachineInst{
    Machine.Operand dOpd;
    Ast.Def def;
    public MIGlobal(Ast.Def def , Machine.Block insertAtBegin, boolean isFloat){
        super(Tag.Global,isFloat);
        this.def = def;
        insertAtBegin.insts.addFirst(this);

    }

    @Override
    public void genDefUse() {
        defOpds.add(dOpd);
    }

    public void output(PrintStream os){
        transfer_output(os);
        os.println("ldr\t"+dOpd.toString()+",="+def.ident.content);
    }
}
