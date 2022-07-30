import arg.Arg;
import backend.CodeGen;
import backend.FPRegAllocator;
import backend.NaiveRegAllocator;
import backend.TrivialRegAllocator;
// import descriptor.MIDescriptor;
import descriptor.MIDescriptor;
import frontend.Visitor;
import frontend.lexer.Lexer;
import frontend.lexer.Token;
import frontend.lexer.TokenList;
import frontend.syntax.Ast;
import frontend.syntax.Parser;
import lir.Machine;
import manage.Manager;
import midend.MidEndRunner;
import midend.RemovePhi;
import util.CenterControl;

import java.io.*;

public class Compiler {

    public static boolean OUTPUT_LEX = false;
    public static boolean ONLY_FRONTEND = false;

    private static String input(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line).append("\n");
        }
        reader.close();
        return builder.toString();
    }

    public static void main(String[] args) {
        Arg arg = Arg.parse(args);
        try {
            BufferedInputStream source = new BufferedInputStream(arg.srcStream);
            // System.err.println(source); // output source code via stderr;
            TokenList tokenList = new TokenList();
            Lexer.getInstance().lex(source, tokenList);
            // OUTPUT_LEX = true;
            if (OUTPUT_LEX) {
                while (tokenList.hasNext()) {
                    Token token = tokenList.consume();
                    System.err.println(token.getType() + " " + token.getContent());
                }
            }
            System.err.println("AST out");
            Ast ast = new Parser(tokenList).parseAst();
            Visitor visitor = Visitor.VISITOR;
            // visitor.__ONLY_PARSE_OUTSIDE_DIM = false;
            visitor.visitAst(ast);
            System.err.println("visit end");
            // Manager manager = visitor.getIr();
            // GlobalValueLocalize globalValueLocalize = new GlobalValueLocalize(funcManager.globals);
            // globalValueLocalize.Run();
            Manager.MANAGER.outputLLVM();
            MidEndRunner.O2 = arg.optimize;
            System.err.println("mid optimization begin");
            long start = System.currentTimeMillis();
            MidEndRunner midEndRunner = new MidEndRunner(Manager.MANAGER.getFunctionList());
            midEndRunner.Run();
            if (arg.outputLLVM()) {
                Manager.MANAGER.outputLLVM(arg.llvmStream);
            }
            System.err.println("mid optimization end, Use Time: " + String.valueOf(((double) System.currentTimeMillis() - start) / 1000) + "s");

            // DeadCodeDelete deadCodeDelete = new DeadCodeDelete(Manager.MANAGER.getFunctionList());
            // deadCodeDelete.Run();
            if (ONLY_FRONTEND) {
                return;
            }

            RemovePhi removePhi = new RemovePhi(midEndRunner.functions);
            removePhi.Run();

            System.err.println("code gen begin");
            start = System.currentTimeMillis();
            //Manager.MANAGER.outputLLVM();
            CodeGen.CODEGEN.gen();
            System.err.println("code gen end, Use Time: " + String.valueOf(((double) System.currentTimeMillis() - start) / 1000) + "s");
            Machine.Program p = Machine.Program.PROGRAM;
            // 为 MI Descriptor 设置输入输出流
            // MIDescriptor.MI_DESCRIPTOR.setInput(arg.interpretInputStream);
            // MIDescriptor.MI_DESCRIPTOR.setOutput(arg.interpretOutputStream);
            // 用参数给定的输入输出流后，分配寄存器前和分配寄存器后只运行一遍解释器，否则后者的输出会覆盖前者
            // MIDescriptor.MI_DESCRIPTOR.run(); // 分配寄存器前
            // System.err.println("before");
            Manager.MANAGER.outputMI();
            // System.err.println("before end");
            // Manager.outputMI(true);
            System.err.println("Reg Alloc begin");
            start = System.currentTimeMillis();
            if (CenterControl._FAST_REG_ALLOCATE) {
                NaiveRegAllocator regAllocator = new NaiveRegAllocator();
                regAllocator.AllocateRegister(p);
            } else {
                if (CodeGen.needFPU) {
                    FPRegAllocator fpRegAllocator = new FPRegAllocator();
                    fpRegAllocator.AllocateRegister(p);
                }
                // System.err.println("middle");
                // Manager.MANAGER.outputMI();
                // System.err.println("middle end");
                TrivialRegAllocator trivialRegAllocator = new TrivialRegAllocator();
                trivialRegAllocator.AllocateRegister(p);
            }
            System.err.println("Reg Alloc end, Use Time: " + String.valueOf(((double) System.currentTimeMillis() - start) / 1000) + "s");
            // Manager.outputMI(true);
            // System.err.println("after");
            Manager.MANAGER.outputMI(true);
            // System.err.println("after end");
            // System.err.println("BEGIN rerun");
            // MIDescriptor.MI_DESCRIPTOR.setRegMode();
            // MIDescriptor.MI_DESCRIPTOR.run(); // 分配寄存器后
            // File output_file = new File("output.S");
            // PrintStream os = new PrintStream(output_file);
            // p.output(os);

            if (arg.outputAsm()) {
                p.output(new PrintStream(arg.asmStream));
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
