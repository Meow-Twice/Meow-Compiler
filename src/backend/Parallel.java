package backend;

import lir.*;

import java.util.HashMap;

import static lir.Arm.Reg.getRSReg;
import static lir.Arm.Regs.GPRs.*;
import static lir.BJ.*;
import static lir.MC.Operand.I_ONE;
import static lir.MC.Operand.I_ZERO;
import static lir.MachineInst.Tag.Add;
import static lir.MachineInst.Tag.Sub;
import static mir.type.DataType.I32;

public class Parallel {
    public static final Parallel PARALLEL = new Parallel(MC.Program.PROGRAM);
    MC.Program p;
    // TODO parallel的function都需要def r2
    MC.McFunction mf_parallel_start = new MC.McFunction("parallel_start");
    MC.McFunction mf_parallel_end = new MC.McFunction("parallel_end");
    private static MC.McFunction curMF;
    public final HashMap<String, Arm.Glob> tmpGlob = new HashMap<>();
    private String start_r5 = "$start_r5";
    private String start_r7 = "$start_r7";
    private String start_lr = "$start_lr";
    private String end_r7 = "$end_r7";
    private String end_lr = "$end_lr";

    MC.Block mb_parallel_start;
    MC.Block mb_parallel_start1;
    MC.Block mb_parallel_start2;
    MC.Block mb_parallel_end;
    MC.Block mb_parallel_end1;
    MC.Block mb_parallel_end2;
    MC.Block mb_parallel_end3;
    MC.Block mb_parallel_end4;
    private Parallel(MC.Program p) {
        this.p = p;
    }

    public void gen() {
        p.funcList.insertAtEnd(mf_parallel_start);
        p.funcList.insertAtEnd(mf_parallel_end);
        Arm.Glob g = new Arm.Glob(start_r5);
        MC.Program.PROGRAM.globList.add(g);
        tmpGlob.put(start_r5, g);
        g = new Arm.Glob(start_r7);
        MC.Program.PROGRAM.globList.add(g);
        tmpGlob.put(start_r7, g);
        g = new Arm.Glob(start_lr);
        MC.Program.PROGRAM.globList.add(g);
        tmpGlob.put(start_lr, g);
        g = new Arm.Glob(end_r7);
        MC.Program.PROGRAM.globList.add(g);
        tmpGlob.put(end_r7, g);
        g = new Arm.Glob(end_lr);
        MC.Program.PROGRAM.globList.add(g);
        tmpGlob.put(end_lr, g);

        curMF = mf_parallel_start;
        mb_parallel_start = new MC.Block(".parallel_start", curMF);
        mb_parallel_start1 = new MC.Block(".parallel_start1", curMF);
        mb_parallel_start2 = new MC.Block(".parallel_start2", curMF);
        curMF = mf_parallel_end;
        mb_parallel_end = new MC.Block(".parallel_end", curMF);
        mb_parallel_end1 = new MC.Block(".parallel_end1", curMF);
        mb_parallel_end2 = new MC.Block(".parallel_end2", curMF);
        mb_parallel_end3 = new MC.Block(".parallel_end3", curMF);
        mb_parallel_end4 = new MC.Block(".parallel_end4", curMF);
        genStart();
        genEnd();
    }

    private static MC.Block curMB;
    private Arm.Glob getGlob(String name){
        return tmpGlob.get(name);
    }

    /**
     *
     parallel_start:
     movw	r2,	:lower16:$start_r7
     movt	r2,	:upper16:$start_r7
     str	r7,	[r2]
     movw	r2,	:lower16:$start_r5
     movt	r2,	:upper16:$start_r5
     str	r5,	[r2]
     movw	r2,	:lower16:$start_lr
     movt	r2,	:upper16:$start_lr
     str	lr,	[r2]
     mov r5, #4   @4代表总共4个进程，因为处理器有4个核心
     .parallel_start1:
     sub r5, r5, #1
     cmp r5, #0
     beq .parallel_start2+0 @r2每次减一，到0的时候就跳到.__mtstart2
     mov r7, #120   @系统调用号
     mov r0, #273   @系统调用的第一个参数值
     mov r1, sp
     swi #0   @根据寄存器r7的值进行系统调用，r7中为120，所以进行120号系统调用，为clone
     cmp r0, #0 @clone调用者进程返回创建的子进程号，在创建的子进程里返回0
     bne .parallel_start1+0 @如果是clone出来的进程就继续执行.parallel_start2，主进程跳到.parallel_start1，继续开启下一个进程
     .parallel_start2:
     mov r0, r5
     movw	r2,	:lower16:$start_r7
     movt	r2,	:upper16:$start_r7
     ldr	r7,	[r2]
     movw	r2,	:lower16:$start_r5
     movt	r2,	:upper16:$start_r5
     ldr	r5,	[r2]
     movw	r2,	:lower16:$start_lr
     movt	r2,	:upper16:$start_lr
     ldr	lr,	[r2]
     bx  lr
     */
    private void genStart() {
        curMF = mf_parallel_start;
        curMB = mb_parallel_start;
        new I.Mov(getRSReg(r2), getGlob(start_r7), curMB);
        new I.Str(getRSReg(r7), getRSReg(r2), I_ZERO, curMB);

        new I.Mov(getRSReg(r2), getGlob(start_r5), curMB);
        new I.Str(getRSReg(r5), getRSReg(r2), I_ZERO, curMB);

        new I.Mov(getRSReg(r2), getGlob(start_lr), curMB);
        new I.Str(getRSReg(lr), getRSReg(r2), I_ZERO, curMB);

        new I.Mov(getRSReg(r5), new MC.Operand(I32, 4), curMB);
        curMB = mb_parallel_start1;
        new I.Binary(Sub, getRSReg(r5), getRSReg(r5), new MC.Operand(I32, 1), curMB);
        new I.Cmp(Arm.Cond.Eq, getRSReg(r5), I_ZERO, curMB);
        new GDBranch(Arm.Cond.Eq, mb_parallel_start2, curMB);
        new I.Mov(getRSReg(r7), new MC.Operand(I32, 120), curMB);
        new I.Mov(getRSReg(r0), new MC.Operand(I32, 273), curMB);
        new I.Mov(getRSReg(r1), getRSReg(sp), curMB);
        new I.Swi(curMB);
        new I.Cmp(Arm.Cond.Ne, getRSReg(r0), new MC.Operand(I32, 0), curMB);
        new GDBranch(Arm.Cond.Ne, mb_parallel_start1, curMB);
        curMB = mb_parallel_start2;
        new I.Mov(getRSReg(r0), getRSReg(r5), curMB);
        new I.Mov(getRSReg(r2), getGlob(start_r7), curMB);
        new I.Ldr(getRSReg(r7), getRSReg(r2), I_ZERO, curMB);

        new I.Mov(getRSReg(r2), getGlob(start_r5), curMB);
        new I.Ldr(getRSReg(r5), getRSReg(r2), I_ZERO, curMB);

        new I.Mov(getRSReg(r2), getGlob(start_lr), curMB);
        new I.Ldr(getRSReg(lr), getRSReg(r2), I_ZERO, curMB);
        new I.Ret(curMB);
    }

    private void genEnd() {
        curMF = mf_parallel_end;
        curMB = mb_parallel_end;
        new I.Cmp(Arm.Cond.Eq, getRSReg(r0), I_ZERO, curMB);
        new GDBranch(Arm.Cond.Eq, mb_parallel_end2, curMB);
        curMB = mb_parallel_end1;
        new I.Mov(getRSReg(r7), I_ONE, curMB);
        new I.Swi(curMB);
        curMB = mb_parallel_end2;
        new I.Mov(getRSReg(r2), getGlob(end_r7), curMB);
        new I.Str(getRSReg(r7), getRSReg(r2), I_ZERO, curMB);

        new I.Mov(getRSReg(r2), getGlob(end_lr), curMB);
        new I.Str(getRSReg(lr), getRSReg(r2), I_ZERO, curMB);

        new I.Mov(getRSReg(r7), new MC.Operand(I32, 4), curMB);

        curMB = mb_parallel_end3;
        new I.Binary(Sub, getRSReg(r7), getRSReg(r7), I_ONE, curMB);
        new I.Cmp(Arm.Cond.Eq, getRSReg(r7), I_ZERO, curMB);
        new GDBranch(Arm.Cond.Eq, mb_parallel_end4, curMB);
        new I.Binary(Sub, getRSReg(r0), getRSReg(sp), new MC.Operand(I32, 4), curMB);
        new I.Binary(Sub, getRSReg(sp), getRSReg(sp), new MC.Operand(I32, 4), curMB);
        new I.Wait(curMB);
        new I.Binary(Add, getRSReg(sp), getRSReg(sp), new MC.Operand(I32, 4), curMB);
        new GDJump(mb_parallel_end3, curMB);
        curMB = mb_parallel_end4;
        new I.Mov(getRSReg(r2), getGlob(end_r7), curMB);
        new I.Ldr(getRSReg(r7), getRSReg(r2), I_ZERO, curMB);

        new I.Mov(getRSReg(r2), getGlob(end_lr), curMB);
        new I.Ldr(getRSReg(lr), getRSReg(r2), I_ZERO, curMB);

        new I.Ret(curMB);
    }
}
