package lir;

import java.io.PrintStream;

/**
 * Fma
 * smmla:Rn + (Rm * Rs)[63:32] or smmls:Rd := Rn – (Rm * Rs)[63:32]
 * mla:Rn + (Rm * Rs)[31:0] or mls:Rd := Rn – (Rm * Rs)[31:0]
 * dst = acc +(-) lhs * rhs
 */
public class MIFma extends MachineInst {
    Machine.Operand acc;
    Machine.Operand dst;
    Machine.Operand lOpd;
    Machine.Operand rOpd;
    boolean add;
    boolean sign;
    Arm.Cond cond;

    public MIFma(boolean add, boolean sign, Machine.Block insertAtEnd, boolean isFloat) {
        super(Tag.FMA, insertAtEnd, isFloat);
        this.add = add;
        this.sign = sign;

    }


    public MIFma(boolean add, boolean sign,
                 Machine.Operand tmpDst, Machine.Operand curAddrVR, Machine.Operand curIdxVR, Machine.Operand offUnitImmVR,
                 Machine.Block insertAtEnd) {
        //dst = acc +(-) lhs * rhs
        super(Tag.FMA, insertAtEnd);
        this.add = add;
        this.sign = sign;
        dst = tmpDst;
        acc = curAddrVR;
        lOpd = curIdxVR;
        rOpd = offUnitImmVR;
    }

    @Override
    public void output(PrintStream os, Machine.McFunction f) {
        transfer_output(os);
        if (sign) {
            os.print("sm");
        }
        String op = "";
        if (add) {
            op = "mla";
        } else {
            op = "mls";
        }
        os.println(op + cond + "\t" + dst.toString() + "," + lOpd.toString() + "," + rOpd.toString() + "," + acc.toString());

    }

    @Override
    public void genDefUse() {
        useOpds.add(acc);
        useOpds.add(lOpd);
        useOpds.add(rOpd);
        defOpds.add(dst);
    }

    @Override
    public String toString() {
        String res = "";
        if (sign) {
            res += "sm";
        }
        String op;
        if (add) {
            op = "mla";
        } else {
            op = "mls";
        }
        res += op + cond + "\t" + dst.toString() + ",\t" + lOpd.toString() + ",\t" + rOpd.toString() + ",\t" + acc.toString();
        return res;
    }
}
