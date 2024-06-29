package compiler;

import compiler.Lexer.AliasParse;
import compiler.Lexer.Block;
import compiler.Lexer.NumberParse;
import compiler.Lexer.Symbol;
import compiler.NaiveParser.CoreKeywordExpression;
import compiler.NaiveParser.CoreOp;
import compiler.NaiveTypechecker.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;

public class NaiveOptimizer {
//    static File inputFile = new File("examples/mod of PowerOfTwo.hex");
    static File inputFile = new File("examples/mod of PowerOfTwo automated.hex");
    //	static File inputFile = new File("examples/tests.hex");
    public static void main(String[] args) throws IOException {
        String fileContent = new String(Files.readAllBytes(inputFile.toPath())) + " ";
        Arrays.stream(new int[0]).allMatch(n -> true);
        Lexer lexer = new Lexer();
        NaiveParser parser = new NaiveParser();
        NaiveTypechecker polisher = new NaiveTypechecker();
        NaiveOptimizer optimizer = new NaiveOptimizer();

        System.out.println("Input:\n" + fileContent + "\n===========================================\n");
        Lexer.CurlyBracketParse b = lexer.parse(fileContent);
        System.out.println("Lexed:\n" + b.toParseString() + "\n===========================================\n");
        b = (Lexer.CurlyBracketParse) parser.parse(b);

        System.out.println("Parsed:\n" + b.toParseString() + "\n===========================================\n");

        NaiveTypechecker.Body moduleBody = (NaiveTypechecker.Body)polisher.createContexts(b, polisher.builtins);
        polisher.resolveTypes(moduleBody, moduleBody.context);

        polisher.typeChecker(moduleBody, moduleBody.context);
        System.out.println("Polished:\n" + moduleBody.toParseString() + "\n===========================================\n");

//		System.out.println(moduleBody.context.functionDefinitions.get(1).body.context.localValues);

        optimizer.optimize(moduleBody, moduleBody.context);
        System.out.println("Optimized:\n" + moduleBody.toParseString());


    }

    NaiveOptimizer() {

    }

    void optimize(Block b, Context context) {
        if(b instanceof Body body) {
            for(Function f : body.context.allDefinedFunctions)
                optimize(f.body, f.body.context);
            for(Block e : body.expr)
                optimize(e, body.context);
        } else if(b instanceof CoreOp co) {
//            System.out.println("OPTIMIZE");
            if(co.s.equals("%") && co.operands.size() == 2
                    && getType(co.operands.get(0), context).staticSubtypeOf(context.getType("long"))
//                    && getType(co.operands.get(1)).staticSubtypeOf(context.getType("long"))
                    && getType(co.operands.get(1), context) instanceof RefinementType rt
                    && rt.staticSubtypeOf(context.getType("long"))
                    && refinementOperationEqual(rt, context,
                        new CoreOp("==",
                                new CoreOp("&",
                                        new AliasParse("x"),
                                        new CoreOp("-",
                                                new AliasParse("x"),
                                                new NumberParse("1"))),
                                new NumberParse("0")))) {

                CoreOp subst = new CoreOp("&", co.operands.get(0), new CoreOp("-", co.operands.get(1), new NumberParse("1")));
                co.s = subst.s;
                co.operands.clear();
                co.operands.addAll(subst.operands);
//                System.out.println("OPTIMIZE");
            }
            for(Block e : co.operands)
                optimize(e, context);
        } else if(b instanceof CoreKeywordExpression kw) {
            for(Block e : kw.iterate())
                optimize(e, context);
        }
    }

    boolean refinementOperationEqual(RefinementType rt, Context context, CoreOp co) {
        if(rt.inheritType instanceof StructType st &&
            st.vars.size() == 1){
            String variable = st.vars.get(0);
            return operationEqual(rt.customMatch, co, variable);
        }
        return false;
    }

    boolean operationEqual(Block b1, Block b2, String var) {
        if(b1 instanceof Body b && b.expr.size() == 1
                && b.expr.get(0) instanceof CoreOp op && op.s.equals("return") && op.operands.size() == 1) {
//            System.out.println("RETURNSS");
            return operationEqual(op.operands.get(0), b2, var);
        } else if(b1 instanceof CoreOp o1 && b2 instanceof CoreOp o2){
//            System.out.println("OPSSSS");
            if(!o1.s.equals(o2.s) || o1.operands.size() != o2.operands.size()) return false;
            for(int i = 0; i < o1.operands.size(); i++){
                if(!operationEqual(o1.operands.get(i), o2.operands.get(i), var))
                    return false;
            }
            return true;
        } else if(b1 instanceof AliasParse a1 && b2 instanceof AliasParse a2){
//            System.out.println("AaAAAAAA");
            return a1.s.equals(var);
        } else if(b1 instanceof NumberParse a1 && b2 instanceof NumberParse a2){
            return a1.s.equals(a2.s);
        }
        return false;
    }

    Type getType(Block b, Context context) {
        if (b instanceof Symbol s){
            return context.getVariableType(s.s);
        }
        throw new RuntimeException("Not implemented: " + b.getClass());
    }
}
