import arg.Arg;
import frontend.lexer.Lexer;
import frontend.lexer.Token;
import frontend.lexer.TokenList;
import frontend.semantic.Visitor;
import frontend.syntax.Ast;
import frontend.syntax.Parser;
import midend.MidEndRunner;
import mir.FuncManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Compiler {

    public static final boolean OUTPUT_LEX = false;

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
            String source = input(arg.srcStream);
            System.err.println(source); // output source code via stderr;
            TokenList tokenList = Lexer.lex(source);
            if (OUTPUT_LEX) {
                while (tokenList.hasNext()) {
                    Token token = tokenList.consume();
                    System.err.println(token.getType() + " " + token.getContent());
                }
            }
            Ast ast = new Parser(tokenList).parseAst();
            Visitor visitor = new Visitor();
            visitor.__ONLY_PARSE_OUTSIDE_DIM = true;
            visitor.visitAst(ast);
            FuncManager funcManager = visitor.getIr();
            MidEndRunner midEndRunner = new MidEndRunner(funcManager.getFunctionList());
            midEndRunner.Run();
            funcManager.output(arg.llvmStream);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
