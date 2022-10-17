import arg.Arg;
import frontend.Visitor;
import frontend.lexer.Lexer;
import frontend.lexer.Token;
import frontend.lexer.TokenList;
import frontend.syntax.Ast;
import frontend.syntax.Parser;
import manage.Manager;
import midend.MidEndRunner;
import util.CenterControl;

import java.io.BufferedInputStream;

import static midend.MidEndRunner.O2;
import static util.CenterControl._ONLY_FRONTEND;

public class Compiler {

    public static boolean OUTPUT_LEX = false;
    // public static boolean ONLY_FRONTEND = false;

    public static void main(String[] args) {
        Arg arg;
        if (args.length == 0) {
            arg = Arg.parse(new String[]{"-emit-llvm", "-o", "llvm_ir.txt", "testfile.txt"});
        } else {
            arg = Arg.parse(args);
        }
        CenterControl._HEURISTIC_BASE = arg.heuristicBase;
        try {
            BufferedInputStream source = new BufferedInputStream(arg.srcStream);
            // System.err.println(source); // output source code via stderr;
            TokenList tokenList = new TokenList();
            Lexer.getInstance().lex(source, tokenList);
            if (OUTPUT_LEX) {
                while (tokenList.hasNext()) {
                    Token token = tokenList.consume();
                    System.err.println(token.getType() + " " + token.getContent());
                }
            }
            System.err.println();
            System.err.println("AST out");
            Ast ast = new Parser(tokenList).parseAst();
            Visitor visitor = Visitor.VISITOR;
            visitor.visitAst(ast);
            System.err.println("visit end");
            Manager.MANAGER.outputLLVM();

            _ONLY_FRONTEND = !arg.outputAsm();

            O2 = arg.optLevel == 2;
            MidEndRunner.O0 = arg.optLevel == 0;
            System.err.println("opt level = " + arg.optLevel);
            System.err.println("mid optimization begin");
            long start = System.currentTimeMillis();
            MidEndRunner midEndRunner = new MidEndRunner(Manager.MANAGER.getFunctionList());
            midEndRunner.Run();
            System.err.println("mid optimization end, Use Time: " + ((double) System.currentTimeMillis() - start) / 1000 + "s");

            if (arg.outputLLVM()) {
                Manager.MANAGER.outputLLVM(arg.llvmStream);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(e.getClass().getSimpleName().length());
        }
    }
}
