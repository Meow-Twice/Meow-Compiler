package arg;

import java.io.*;

public class Arg {
    public final String srcFilename; // 源代码文件名 e.g. "testcase.sy"
    public final boolean optimize; // 是否进行性能测试 -O2

    public final String asmFilename; // 目标汇编代码文件名 e.g. "testcase.s"
    public final String llvmFilename; // LLVM IR 文件名 e.g. "testcase.ll"

    public final FileInputStream srcStream; // 源代码输入流
    public final OutputStream asmStream; // 汇编代码输出流
    public final OutputStream llvmStream; // LLVM IR 中间代码输出流

    public final boolean interpretMI; // 是否解释执行
    public final InputStream interpretInputStream; // 解释执行 stdin
    public final OutputStream interpretOutputStream; // 解释执行 stdout

    private Arg(String src, boolean optimize, String asm, String llvm,
                boolean interpret, String interpretIn, String interpretOut) throws FileNotFoundException {
        this.srcFilename = src;
        this.optimize = optimize;
        this.asmFilename = asm;
        this.llvmFilename = llvm;

        this.srcStream = new FileInputStream(srcFilename);
        this.asmStream = asm.isEmpty() ? null : new FileOutputStream(asmFilename);
        this.llvmStream = llvm.isEmpty() ? null : new FileOutputStream(llvmFilename);

        this.interpretMI = interpret;
        this.interpretInputStream = interpretIn.isEmpty() ? System.in : new FileInputStream(interpretIn);
        this.interpretOutputStream = interpretOut.isEmpty() ? System.out : new FileOutputStream(interpretOut);
    }

    public boolean outputAsm() {
        return !asmFilename.isEmpty();
    }

    public boolean outputLLVM() { return !llvmFilename.isEmpty(); }

    public static Arg parse(String[] args) {
        String src = "", asm = "", llvm = "";
        boolean optimize = false;
        boolean interpret = false;
        String interpretIn = "", interpretOut = "";
        for (int i = 0; i < args.length; i++) {
            // detect "-O2"
            if ("-O2".equals(args[i])) {
                optimize = true;
                continue;
            }
            // detect "-S"
            if ("-S".equals(args[i])) {
                if (i + 2 < args.length && "-o".equals(args[i + 1])) {
                    if (!asm.isEmpty()) {
                        throw new RuntimeException("We got more than one assemble file when we expected only one.");
                    }
                    asm = args[i + 2];
                    i += 2;
                    continue;
                } else {
                    throw new RuntimeException("-S expected -o filename");
                }
            }
            // detect "-emit-llvm"
            if ("-emit-llvm".equals(args[i])) {
                if (i + 2 < args.length && "-o".equals(args[i + 1])) {
                    if (!llvm.isEmpty()) {
                        throw new RuntimeException("We got more than one llvm-ir file when we expected only one.");
                    }
                    llvm = args[i + 2];
                    i += 2;
                    continue;
                } else {
                    throw new RuntimeException("-emit-llvm expected -o filename");
                }
            }
            // detect "-i"
            if ("-I".equals(args[i])) {
                if (interpret) {
                    throw new RuntimeException("We got more than one interpret option.");
                }
                interpret = true;
                while (i + 1 < args.length && ("-i".equals(args[i + 1]) || "-o".equals(args[i + 1]))) {
                    if (i + 2 < args.length && "-i".equals(args[i + 1])) {
                        if (!interpretIn.isEmpty()) {
                            throw new RuntimeException("We got more than one interpreter input.");
                        }
                        interpretIn = args[i + 2];
                        i += 2;
                    }
                    if (i + 2 < args.length && "-o".equals(args[i + 1])) {
                        if (!interpretOut.isEmpty()) {
                            throw new RuntimeException("We got more than one interpreter output.");
                        }
                        interpretOut = args[i + 2];
                        i += 2;
                    }
                }
                continue;
            }
            // detect illegal flags
            if (args[i].startsWith("-")) {
                throw new RuntimeException("invalid flag: " + args[i]);
            }
            // source file
            if (!src.isEmpty()) {
                throw new RuntimeException("We got more than one source file when we expected only one.");
            }
            src = args[i];
        }
        if (src.isEmpty()) {
            printHelp();
            throw new RuntimeException("source file should be specified.");
        }
        try {
            return new Arg(src, optimize, asm, llvm, interpret, interpretIn, interpretOut);
        } catch (FileNotFoundException e) {
            printHelp();
            throw new RuntimeException(e);
        }
    }

    public static void printHelp() {
        System.err.println("Usage: compiler {((-S|-emit-llvm) -o filename)|(-I [-i input_file] [-o output_file])} filename [-O2]");
    }
}
