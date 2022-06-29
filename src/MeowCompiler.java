import frontend.lexer.Lexer;
import frontend.lexer.Token;
import frontend.lexer.TokenList;
import frontend.semantic.Visitor;
import ir.FuncManager;
import frontend.syntax.Ast;
import frontend.syntax.Parser;
import midEnd.MidEndRunner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class MeowCompiler {

    public static final boolean OUTPUT_LEX = false;

    private static String input() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line).append("\n");
        }
        reader.close();
        return builder.toString();
    }

    public static void main(String[] args) {
        try {
            String source = input();
            System.err.println(source); // output source code via stderr;
            TokenList tokenList = Lexer.lex(source);
            if (OUTPUT_LEX) {
                while (tokenList.hasNext()) {
                    Token token = tokenList.consume();
                    System.out.println(token.getType() + " " + token.getContent());
                }
            }
            Ast ast = new Parser(tokenList).parseAst();
            Visitor visitor = new Visitor();
            visitor.__ONLY_PARSE_OUTSIDE_DIM = true;
            visitor.visitAst(ast);
            FuncManager funcManager = visitor.getIr();
            MidEndRunner midEndRunner = new MidEndRunner(funcManager.getFunctionList());
            midEndRunner.Run();
            funcManager.output();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
