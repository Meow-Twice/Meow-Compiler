import arg.Arg;
import backend.CodeGen;
import backend.TrivialRegAllocator;
import frontend.lexer.Lexer;
import frontend.lexer.Token;
import frontend.lexer.TokenList;
import frontend.Visitor;
import frontend.syntax.Ast;
import frontend.syntax.Parser;
import lir.Machine;
import midend.MidEndRunner;
import manage.Manager;

import java.io.*;

public class Compiler {

    public static boolean OUTPUT_LEX = false;

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
            Visitor visitor = new Visitor();
            visitor.__ONLY_PARSE_OUTSIDE_DIM = false;
            visitor.visitAst(ast);
            // Manager manager = visitor.getIr();
            // GlobalValueLocalize globalValueLocalize = new GlobalValueLocalize(funcManager.globals);
            // globalValueLocalize.Run();
            Manager.MANAGER.outputLLVM();
            MidEndRunner midEndRunner = new MidEndRunner(Manager.MANAGER.getFunctionList());
            midEndRunner.Run();
            // DeadCodeDelete deadCodeDelete = new DeadCodeDelete(Manager.MANAGER.getFunctionList());
            // deadCodeDelete.Run();
            Manager.MANAGER.output(arg.llvmStream);
            CodeGen.CODEGEN.gen();
            CodeGen.CODEGEN.outputMI();
            // Machine.Program p = Machine.Program.PROGRAM;
            // TrivialRegAllocator regAllocator = new TrivialRegAllocator();
            // regAllocator.AllocateRegister(p);
            // p.output(new PrintStream(arg.asmStream));
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
