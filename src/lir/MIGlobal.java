package lir;

import frontend.syntax.Ast;

public class MIGlobal extends MachineInst{
    Machine.Operand dOpd;
    Ast.Decl decl;
    public MIGlobal( Ast.Decl decl ,Machine.Block insertAtBegin){
        super(Tag.Global);
        this.decl = decl;
        insertAtBegin.insts.addFirst(this);

    }

    @Override
    public void genDefUse() {
        defOpds.add(dOpd);
    }
}
