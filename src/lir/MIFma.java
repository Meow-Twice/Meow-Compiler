package lir;

import java.io.PrintStream;

/**
 * Fma
 * smmla:Rn + (Rm * Rs)[63:32] or smmls:Rd := Rn – (Rm * Rs)[63:32]
 * mla:Rn + (Rm * Rs)[31:0] or mls:Rd := Rn – (Rm * Rs)[31:0]
 * dst = acc +(-) lhs * rhs
 */
public class MIFma extends MachineInst {
    // Machine.Operand acc;
    // Machine.Operand dst;
    // Machine.Operand lOpd;
    // Machine.Operand rOpd;

    public Machine.Operand getDst() {
        return defOpds.get(0);
    }

    public Machine.Operand getAcc() {
        return useOpds.get(0);
    }

    public Machine.Operand getlOpd() {
        return useOpds.get(1);
    }

    public Machine.Operand getrOpd() {
        return useOpds.get(2);
    }

    public boolean isAdd() {
        return add;
    }

    public boolean isSign() {
        return sign;
    }

    public Arm.Cond getCond() {
        return cond;
    }

    boolean add;
    boolean sign;
    // Arm.Cond cond;

    public MIFma(boolean add, boolean sign, Machine.Block insertAtEnd, boolean isFloat) {
        super(Tag.FMA, insertAtEnd, isFloat);
        this.add = add;
        this.sign = sign;
    }


    public MIFma(boolean add, boolean sign,
                 Machine.Operand dst, Machine.Operand acc, Machine.Operand lOpd, Machine.Operand rOpd,
                 Machine.Block insertAtEnd) {
        //dst = acc +(-) lhs * rhs
        super(Tag.FMA, insertAtEnd);
        this.add = add;
        this.sign = sign;
        defOpds.add(dst);
        useOpds.add(acc);
        useOpds.add(lOpd);
        useOpds.add(rOpd);
    }

    @Override
    public void output(PrintStream os, Machine.McFunction f) {
        transfer_output(os);
        if (sign) {
            os.print("\tsm");
        }
        String op = "";
        if (add) {
            op = "mla";
        } else {
            op = "mls";
        }
        os.println(op + cond + "\t" + getDst().toString() + ",\t" + getAcc().toString() + ",\t" + getlOpd().toString() + ",\t" + getrOpd().toString());

    }

    // @Override
    // public void genDefUse() {
    //     defOpds.add(dst);
    //     useOpds.add(acc);
    //     useOpds.add(lOpd);
    //     useOpds.add(rOpd);
    // }

    @Override
    public String toString() {
        if (!isFloat) {
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
            res += op + cond + "\t" + getDst().toString() + ",\t" + getAcc().toString() + ",\t" + getlOpd().toString() + ",\t" + getrOpd().toString();
            return res;
        } else {
            String res = "";
            String op;
            if (add) {
                op = "vmla";
            } else {
                op = "vmls";
            }
            res += op + cond +".f32"+ "\t" + getDst().toString() + ",\t" + getAcc().toString() + ",\t" + getlOpd().toString() + ",\t" + getrOpd().toString();
            return res;
        }
    }
}
