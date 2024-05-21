package compiler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;

import compiler.Lexer.AliasParse;
import compiler.Lexer.Block;
import compiler.Lexer.CurlyBracketParse;
import compiler.Lexer.Expression;
import compiler.Lexer.ExpressionParse;
import compiler.Lexer.Keyword;
import compiler.Lexer.NumberParse;
import compiler.Lexer.OperationParse;
import compiler.Lexer.ParenthesisParse;
import compiler.Lexer.SquareBracketParse;
import compiler.Lexer.StringParse;
import compiler.Lexer.Symbol;
import compiler.NaivePolisher.Scope;

public class NaiveParser {
	
//	static File inputFile = new File("src/snake.hex");
//	static File inputFile = new File("src/code3.hex");
//	static File inputFile = new File("src/code6.hex");
//	static File inputFile = new File("src/code7.hex");
//	static File inputFile = new File("src/testbench/for-statement.hex");
//	static File inputFile = new File("src/testbench/function-definition.hex");
//	static File inputFile = new File("src/testbench/if-statement.hex");
//	static File inputFile = new File("src/testbench/if-else-statement.hex");
//	static File inputFile = new File("src/fibonacci.hex");
//	static File inputFile = new File("src/code9.hex");
	static File inputFile = new File("src/code12.hex");
	
	
	
	public static void main(String[] args) throws IOException {
		
		String fileContent = new String(Files.readAllBytes(inputFile.toPath())) + " ";
		
		Lexer lexer = new Lexer();
		NaiveParser parser = new NaiveParser();
		
		CurlyBracketParse b = lexer.parse(fileContent);
		for(Block b2 : ((CurlyBracketParse)b).expressions) {
			System.out.println("\t"+b2.toParseString());
			System.out.println("==================");
		}
		System.out.println("\n\n\n======================================\n\n\n");
		parser.parse(b);
		System.out.println("\n");
//		System.out.println(b.toParseString());
		for(Block b2 : ((CurlyBracketParse)b).expressions) {
			System.out.println("\t"+b2.toParseString());
			System.out.println("==================");
		}
	}
		
	Block parse(CurlyBracketParse root) {
		return orderOfOperation(root);
	}
	
	Block orderOfOperation(Block b) {
		if(b instanceof CoreOp) {
			CoreOp p = (CoreOp) b;
			for(int i = 0; i < p.operands.size(); i++) {
				p.operands.set(i, orderOfOperation(p.operands.get(i)));
//				p.operands.get(i).parent = b;
			}
		}
		else if(b instanceof Keyword) {}
		else if(b instanceof AliasParse) {}
		else if(b instanceof NumberParse) {}
		else if(b instanceof StringParse) {}
		else if(b instanceof CoreFunctionCall) {
			CoreFunctionCall p = (CoreFunctionCall) b;
			p.function = orderOfOperation(p.function);
			p.argument = orderOfOperation(p.argument);
		}
		else if(b instanceof CoreFunctionDefinition) {
			CoreFunctionDefinition p = (CoreFunctionDefinition) b;
			p.funType = orderOfOperation(p.funType);
			p.body = orderOfOperation(p.body);
		}
		else if(b instanceof CoreIfStatement) {
			CoreIfStatement p = (CoreIfStatement) b;
			p.argument = orderOfOperation(p.argument);
			p.body = orderOfOperation(p.body);
			p.elseBody = orderOfOperation(p.elseBody);
		}
		else if(b instanceof CoreElseStatement) {
			CoreElseStatement p = (CoreElseStatement) b;
			p.body = orderOfOperation(p.body);
		}
		else if(b instanceof CoreForStatement) {
			CoreForStatement p = (CoreForStatement) b;
			p.argument = orderOfOperation(p.argument);
			if(p.each != null)
				p.each = orderOfOperation(p.each);
			p.body = orderOfOperation(p.body);
		}
		else if(b instanceof CoreEachStatement) {
			CoreEachStatement p = (CoreEachStatement) b;
			p.argument = orderOfOperation(p.argument);
			p.body = orderOfOperation(p.body);
		}
		else if(b instanceof CoreWhileStatement) {
			CoreWhileStatement p = (CoreWhileStatement) b;
			p.argument = orderOfOperation(p.argument);
			p.body = orderOfOperation(p.body);
		}
		else if(b instanceof CoreNamespaceDefinition) {
			CoreNamespaceDefinition p = (CoreNamespaceDefinition) b;
			p.body = orderOfOperation(p.body);
		}
		else if(b instanceof CoreClassDefinition) {
			CoreClassDefinition p = (CoreClassDefinition) b;
			p.argument = orderOfOperation(p.argument);
			p.body = orderOfOperation(p.body);
		}
		else if(b instanceof CoreStructureDefinition) {
			CoreStructureDefinition p = (CoreStructureDefinition) b;
			p.argument = orderOfOperation(p.argument);
			p.body = orderOfOperation(p.body);
		}
		else if(b instanceof CurlyBracketParse) {
			CurlyBracketParse p = (CurlyBracketParse) b;
			for(int i = 0; i < p.expressions.size(); i++) {
				p.expressions.set(i, orderOfOperation(p.expressions.get(i)));
			}
		}
		else if(b instanceof ExpressionParse) {
//			System.out.println(b);
			ExpressionParse p = (ExpressionParse) b;
			substituteColonOp(p);
			substituteKeywordOp(p);
			substituteAccessOP(p);
			substituteApplyOP(p);
			for(int i = 0; i < orderOfOperation.length; i++) 
				if(i == 0) substituteUnOP(p, orderOfOperation[i]);  //TODO magic number
				else if(orderOfOperation[i][0].contentEquals("print")) 
					substituteUnOP(p, orderOfOperation[i]);  //TODO magic number
				else substituteBinOP(p, orderOfOperation[i]);
			
//			System.out.println("::\t" + p.expressions);
			for(int i = 0; i < p.expressions.size(); i++) {
				p.expressions.set(i, orderOfOperation(p.expressions.get(i)));
			}
			
			if(p.expressions.size() == 1 && p instanceof ParenthesisParse) {
				p.expressions.get(0).parent = b.parent;
				return p.expressions.get(0);
			}
		}
		else throw new RuntimeException("Invalid operation: " + b.getClass() + "\t" + b.toParseString());
		return b;
	}
	
	void substituteColonOp(ExpressionParse e) {
		ArrayList<Block> l = e.expressions;
//		System.out.println(l);
		for(int i = 0; i < l.size(); i++) {
			if(l.get(i) instanceof OperationParse && ((OperationParse)l.get(i)).s.contentEquals(":")) {
				l.remove(i);
				while(l.get(i) instanceof OperationParse && ((OperationParse)l.get(i)).s.contentEquals(":")) {
					l.remove(i);
				}
				CurlyBracketParse comm;
				if(l.get(i) instanceof CurlyBracketParse) {
					continue; 
				}
				comm = new CurlyBracketParse();
				ParenthesisParse paren = new ParenthesisParse();
				
				int ifelse = 0;
				while(i < l.size()) {
					if((l.get(i) instanceof Keyword && ((Keyword)l.get(i)).s.contentEquals("if")))
						ifelse++;
					else if((l.get(i) instanceof Keyword && ((Keyword)l.get(i)).s.contentEquals("else"))) {
						if(ifelse-- == 0)
							break;
					}
					paren.expressions.add(l.remove(i));
				}
				for(Block b : comm.expressions)
					b.parent = comm;
				comm.expressions.add(paren);
				paren.parent = comm;
				comm.parent = e;
				l.add(i, comm);
			}
		}
	}
	void substituteKeywordOp(ExpressionParse e) {
		ArrayList<Block> l = e.expressions;
		for(int i = l.size()-1; i >= 0; i--) {
			final int len = l.size();
//			System.out.println(i + "\t" + l);
			if(l.get(i) instanceof Keyword) {
				String n = ((Keyword)l.get(i)).s; 
				
				switch (n) {
				case "fn":
					if(i < len-1 && l.get(i+1) instanceof AliasParse) {
						l.remove(i);
						String name = ((AliasParse)l.remove(i)).s;
						
						ParenthesisParse arguments = new ParenthesisParse();
						CurlyBracketParse body = new CurlyBracketParse();
						while(i < len){
							Block b = l.remove(i);
							if(b instanceof CurlyBracketParse) {
								body = (CurlyBracketParse) b;
								break;
							}
							arguments.expressions.add(b);
							b.parent = arguments;
						}
						i = l.size();
						
						CoreFunctionDefinition def = new CoreFunctionDefinition(name, arguments, body);
						l.add(i, def);

						arguments.parent = def;
						body.parent = def;
						def.parent = e;
					}
					break;
				case "lambda": {
					l.remove(i);
					
					ParenthesisParse arguments = new ParenthesisParse();
					CurlyBracketParse body = new CurlyBracketParse();
					while(i < len){
						Block b = l.remove(i);
						if(b instanceof CurlyBracketParse) {
							body = (CurlyBracketParse) b;
							break;
						}
						arguments.expressions.add(b);
						b.parent = arguments;
					}
					i = l.size();
					
					CoreLambdaDefinition def = new CoreLambdaDefinition(arguments, body);
					l.add(i, def);

					arguments.parent = def;
					body.parent = def;
					def.parent = e;
					break;
				}
				case "if":{
					l.remove(i);
					ParenthesisParse arguments = new ParenthesisParse();
					CurlyBracketParse body = new CurlyBracketParse();
					CurlyBracketParse elseBody = new CurlyBracketParse();
					while(i < len){
						System.out.println(i + "\tif\t" + l);
						if(l.get(i) instanceof CurlyBracketParse) {
							body = (CurlyBracketParse) l.remove(i);
							break;
						}
						if(l.get(i) instanceof CoreElseStatement)
							break;
						Block b = l.remove(i);
						arguments.expressions.add(b);
						b.parent = arguments;
					}
					if(i < l.size()){
						Block b = l.remove(i);
						if(b instanceof CoreElseStatement) {
							elseBody = (CurlyBracketParse) ((CoreElseStatement) b).body;
						}
					}
					

					CoreIfStatement def = new CoreIfStatement(arguments, body, elseBody);
//					System.out.println(i + "\tif a\t" + l);
					l.add(i, def);
//					System.out.println(i + "\tif b\t" + l);
					i = l.size();
					arguments.parent = def;
					body.parent = def;
					elseBody.parent = def;
					def.parent = e;
					break;
				}
				case "else":{
					l.remove(i);
					CurlyBracketParse body = new CurlyBracketParse();
					
					if(i < l.size()){
						Block b = l.remove(i);
						if(b instanceof CurlyBracketParse) {
							body = (CurlyBracketParse) b;
						}
					}
					CoreElseStatement def = new CoreElseStatement(body);
					l.add(i, def);

					i = l.size();
					
					body.parent = def;
					def.parent = e;
					break;
				}
				case "for":{
					l.remove(i);
					ParenthesisParse arguments = new ParenthesisParse();
					Block body = new CurlyBracketParse();
					while(i < len){
						Block b = l.remove(i);
						if(b instanceof CurlyBracketParse || b instanceof CoreEachStatement) {
							body = b;
							break;
						}
						arguments.expressions.add(b);
						b.parent = arguments;
					}
					i = l.size();
					CoreForStatement def;
					
					if(body instanceof CoreEachStatement) {
						CoreEachStatement p = (CoreEachStatement) body;
						def = new CoreForStatement(arguments, p.argument, p.body);
					}
					else
						def = new CoreForStatement(arguments, null, body);
					l.add(i, def);

					arguments.parent = def;
					body.parent = def;
					def.parent = e;
					break;
				}
				case "each":{
					
					l.remove(i);
					ParenthesisParse arguments = new ParenthesisParse();
					CurlyBracketParse body = new CurlyBracketParse();
					while(i < len) {
						Block b = l.remove(i);
						if(b instanceof CurlyBracketParse) {
							body = (CurlyBracketParse) b;
							break;
						}
						arguments.expressions.add(b);
						b.parent = arguments;
					}
					i = l.size();
					System.out.println("args" + arguments);
					System.out.println("body" + body);
					CoreEachStatement def = new CoreEachStatement(arguments, body);
					l.add(i, def);

					arguments.parent = def;
					body.parent = def;
					def.parent = e;
					break;
				}
				case "while":{
					l.remove(i);
					ParenthesisParse arguments = new ParenthesisParse();
					Block body = new CurlyBracketParse();
					while(i < len){
						Block b = l.remove(i);
						if(b instanceof CurlyBracketParse) {
							body = b;
							break;
						}
						arguments.expressions.add(b);
						b.parent = arguments;
					}
					i = l.size();

					CoreWhileStatement def = new CoreWhileStatement(arguments, body);
					l.add(i, def);

					arguments.parent = def;
					body.parent = def;
					def.parent = e;
					break;
				}
				case "ns":
					if(i < len-1 && l.get(i+1) instanceof AliasParse) {
						l.remove(i);
						String name = ((AliasParse)l.remove(i)).s;
						
						CurlyBracketParse body = new CurlyBracketParse();
						
						if(i < l.size()){
							Block b = l.remove(i);
							if(b instanceof CurlyBracketParse) {
								body = (CurlyBracketParse) b;
							}
						}

						i = l.size();
						
						CoreNamespaceDefinition def = new CoreNamespaceDefinition(name, body);
						l.add(i, def);
						
						body.parent = def;
						def.parent = e;
					}
					break;
				case "class":
					if(i < len-1 && l.get(i+1) instanceof AliasParse) {
						l.remove(i);
						String name = ((AliasParse)l.remove(i)).s;
						
						ParenthesisParse arguments = new ParenthesisParse();
						CurlyBracketParse body = new CurlyBracketParse();
						while(i < len){
							Block b = l.remove(i);
							if(b instanceof CurlyBracketParse) {
								body = (CurlyBracketParse) b;
								break;
							}
							arguments.expressions.add(b);
							b.parent = arguments;
						}
						i = l.size();
						
						CoreClassDefinition def = new CoreClassDefinition(name, arguments, body);
						l.add(i, def);

						arguments.parent = def;
						body.parent = def;
						def.parent = e;
					}
					break;
				case "struct":
					if(i < len-1 && l.get(i+1) instanceof AliasParse) {
						l.remove(i);
						String name = ((AliasParse)l.remove(i)).s;
						
						ParenthesisParse arguments = new ParenthesisParse();
						CurlyBracketParse body = new CurlyBracketParse();
						while(i < len){
							Block b = l.remove(i);
							if(b instanceof CurlyBracketParse) {
								body = (CurlyBracketParse) b;
								break;
							}
							arguments.expressions.add(b);
							b.parent = arguments;
						}
						i = l.size();
						
						CoreStructureDefinition def = new CoreStructureDefinition(name, arguments, body);
						l.add(i, def);

						arguments.parent = def;
						body.parent = def;
						def.parent = e;
					}
					break;
				case "new":
					if(i < len-1)
						l.add(i, new CoreFunctionCall(l.remove(i), l.remove(i)));
					else
						throw new RuntimeException("Invalid keyword usage");
					break;
				default:
					break;
				}
			}
		}
	}
	void substituteAccessOP(ExpressionParse e) {
		ArrayList<Block> l = e.expressions;
	
		for(int i = 0; i < l.size(); i++) {
			if(l.get(i) instanceof Symbol && ((Symbol) l.get(i)).s.contentEquals(".")) {
				ArrayList<Block> operands = new ArrayList<Lexer.Block>(2);
				operands.add(l.remove(i-1));
				l.remove(i-1);
				operands.add(l.remove(i-1));
				l.add(i-1, new CoreOp(".", operands));
				i=0;
			}
			if(l.get(i) instanceof SquareBracketParse && (i > 0 && !(l.get(i-1) instanceof OperationParse) && !(l.get(i-1) instanceof Keyword && Lexer.keywordBinOps.contains(((Keyword)l.get(i-1)).s)))) {
				ArrayList<Block> operands = new ArrayList<Lexer.Block>(2);
				operands.add(l.remove(i-1));
				operands.add(l.remove(i-1));
				l.add(i-1, new CoreOp(".", operands));
				i=0;
			}
		}
	}
	void substituteApplyOP(ExpressionParse e) {
		ArrayList<Block> l = ((ExpressionParse) e).expressions;

		for (int i = 0; i < l.size(); i++) {
//			System.out.println(i + "\t$app_" + l.get(i) + "\t" + l);
			if ((l.get(i) instanceof Keyword && !(Lexer.keywordFuncOps.contains(((Keyword) l.get(i)).s)
					|| Lexer.keywordsType.contains(((Keyword) l.get(i)).s))) || l.get(i) instanceof OperationParse)
				continue;

			if (i + 1 >= l.size() || l.get(i + 1) instanceof OperationParse
					|| (l.get(i + 1) instanceof Keyword && (Lexer.keywordUnOps.contains(((Keyword) l.get(i + 1)).s)
							|| Lexer.keywordBinOps.contains(((Keyword) l.get(i + 1)).s))))
				continue;

//			System.out.println(i + "\t$app_" + l.get(i) + "\t" + l);

//			ArrayList<Block> operands = new ArrayList<Lexer.Block>(2);
//			operands.add(l.remove(i));
//			operands.add(l.remove(i));
			l.add(i, new CoreFunctionCall(l.remove(i), l.remove(i)));

			i--;
		}
	}

	
	void substituteBinOP(ExpressionParse e, String[] operations) {
		ArrayList<Block> l = e.expressions;
		for(int i = 0; i < l.size(); i++) {
			if(l.get(i) instanceof OperationParse || 
					(l.get(i) instanceof Keyword && Lexer.keywordBinOps.contains(((Keyword)l.get(i)).s))) {
//				System.out.println(i + "\t$binop_" + l.get(i) +"\t"+l);
				Block b = l.get(i);
				String s;
				if(b instanceof OperationParse)
					s = ((Symbol) l .get(i)).s;
				else
					s = ((Keyword) l.get(i)).s;
				for(String operation : operations) {
					if(s.contentEquals(operation)) {
						if(s.contentEquals("->") || s.contentEquals("..")) {
							if(i == l.size()-1 || (l.get(i+1) instanceof OperationParse))
								l.add(i+1, new ParenthesisParse());
							if(i == 0 || (l.get(i-1) instanceof OperationParse)) {
								l.add(i, new ParenthesisParse());
								i++;
							}
						}
						ArrayList<Block> operands = new ArrayList<Lexer.Block>(2);
						operands.add(l.remove(i-1));
						l.remove(i-1);
						operands.add(l.remove(i-1));
						
						CoreOp op = new CoreOp(s, operands);
						
						l.add(i-1, op);
						for(Block block : operands)
							block.parent = op;
						op.parent = e;
						
						i--;
					}
				}
			}
		}
	}
	void substituteUnOP(ExpressionParse e, String[] operations) {
		ArrayList<Block> l = ((ExpressionParse) e).expressions;
		for(int i = l.size()-1; i >= 0; i--) {
			if(l.get(i) instanceof OperationParse || 
					(l.get(i) instanceof Keyword && 
					Lexer.keywordUnOps.contains(((Keyword)l.get(i)).s))) {
//				System.out.println(i + "\t$unop_" + l.get(i) +"\t"+l);
//				Block b = l.get(i);
				String s = ((Symbol) l.get(i)).s;
//				System.out.println(i + "\t$unop_" + l.get(i) +"\t" + l);
				for(String operation : operations) {
					if(s.contentEquals(operation)) {
//						System.out.println(i + "\t$unop_" + l.get(i) +"\t" + l);
						
						boolean postfix = s.endsWith("_<");
						
						if(!postfix) {
							if(i == l.size()-1 || l.get(i+1) instanceof OperationParse)
								continue;
//							System.out.println(i + "\t$unop_" + l.get(i) +"\t" + l);
							if((s.contentEquals("+") || s.contentEquals("-")) && i > 0) {
								if(l.get(i-1) instanceof OperationParse) {
									if(Lexer.operationPostfix.contains(((OperationParse)l.get(i-1)).s)) {
										continue;
									} 
									else {
										// keep going
									}
										
								}
								else if(l.get(i-1) instanceof Keyword) {
//									System.out.println(i + "\t$unop_" + l.get(i-1) +"\t" + l);
									if(
//											Lexer.keywordFuncOps.contains(((Keyword)l.get(i-1)).s) || 
											Lexer.keywordUnOps.contains(((Keyword)l.get(i-1)).s) || 
											Lexer.keywordsType.contains(((Keyword)l.get(i-1)).s)) {
										// keep going
									} else {
										continue;
									}
								}
								else 
									continue;
//								System.out.println(i + "\t$unop_" + l.get(i) +"\t" + l);
							}
						}
						
						
						ArrayList<Block> operands = new ArrayList<Lexer.Block>(1);
						CoreOp op;
						if(postfix && i-1 >= 0) {
							
							operands.add(l.remove(i-1));
							l.remove(i-1);
							
							op = new CoreOp(s, operands);
							l.add(i-1, op);
						} else {
							l.remove(i);
							operands.add(l.remove(i));
							
							op = new CoreOp(s, operands);
							l.add(i, op);
						}
						
						
//						System.out.println(op.operands);
						for(Block block : operands)
							block.parent = op;
						op.parent = e;
						i--;
//						System.out.println(l);
					}
				}
			}
			
		}
	}
	
	
	
	
	static class CoreOp extends Expression{
		final String s;
		final ArrayList<Block> operands;
		public CoreOp(String s, ArrayList<Block> operands) {
			this.s = s;
			this.operands = operands;
		}
		
		@Override
		public String toString() {
			if(operands.size() == 0)
				return s;
			if(operands.size() == 1)
				return "(" + s + " " + operands.get(0) + ")";
			if(operands.size() == 2)
				return "(" + operands.get(0) + " " + s + " " + operands.get(1) + ")";
			return "(" + s + " " + operands + ")";
		}

		@Override
		public String toParseString() {
			final boolean verbose = false;
			boolean p = !(parent instanceof CurlyBracketParse || parent instanceof CoreKeywordExpression || parent instanceof Scope);
			if(operands.size() == 0)
				return s;
			if(operands.size() == 1)
				return (p?"(":"") + (verbose?"$un_":"")+ s + " " + operands.get(0).toParseString() + (p?")":"");
			if(operands.size() == 2)
				return (p?"(":"") + operands.get(0).toParseString() + " " + (verbose?"$bin_":"")+ s + " " + operands.get(1).toParseString() + (p?")":"");
			return (p?"(":"") +  (verbose?"$bin_":"") + s + " " + operands.stream() + (p?")":"");
		}

//		@Override
		public Iterator<Block> iterator() {
			return operands.iterator();
		}
	}
	static class CoreFunctionCall extends Expression {
		Block function;
		Block argument;
		public CoreFunctionCall(Block function, Block argument) {
			this.function = function;
			this.argument = argument;
		}
		@Override
		public String toString() {
			return "(" + function + " " + argument + ")";
		}
		@Override
		public String toParseString() {
			return function.toParseString() + "(" + argument.toParseString() + ")";
		}
	}
	
	
	static abstract class CoreKeywordExpression extends Expression {
		abstract Block[] iterate();
	}
	static class CoreFunctionDefinition extends CoreKeywordExpression {
		String name;
		Block funType;
		Block body;
		
		public CoreFunctionDefinition(String name, ParenthesisParse argument, CurlyBracketParse body) {
			this.name = name;
			this.funType = argument;
			this.body = body;
		}
		
		@Override
		public String toString() {
			return "function @" + name + "" + funType + "" + body;
		}

		@Override
		public String toParseString() {
			// TODO Auto-generated method stub
			return "function @" + name + "(" + funType.toParseString() + "): "
					+ "" + body.toParseString();
		}
		
		@Override
		Block[] iterate() {
			return new Block[] {funType, body};
		}
		
	}
	static class CoreLambdaDefinition extends CoreKeywordExpression {
		Block argument;
		Block body;
		
		public CoreLambdaDefinition(Block argument, CurlyBracketParse body) {
			this.argument = argument;
			this.body = body;
		}
		
		@Override
		public String toString() {
			return "lambda " + argument + "" + body;
		}

		@Override
		public String toParseString() {
			// TODO Auto-generated method stub
			return "lambda (" + argument.toParseString() + "): "
					+ "" + body.toParseString();
		}
		
		@Override
		Block[] iterate() {
			return new Block[] {argument, body};
		}
		
	}
	static class CoreIfStatement extends CoreKeywordExpression {
		Block argument;
		Block body;
		Block elseBody;
		
		public CoreIfStatement(ParenthesisParse argument, Block body, Block elseBody) {
			this.argument = argument;
			this.body = body;
			this.elseBody = elseBody;
		}
		
		@Override
		public String toString() {
			if(elseBody instanceof CurlyBracketParse && ((CurlyBracketParse) elseBody).expressions.size() == 0)
				return "if " + argument + ": " + body;
			return "if " + argument + ": " + body + " else: " + elseBody;
		}

		@Override
		public String toParseString() {
			if(elseBody instanceof CurlyBracketParse && ((CurlyBracketParse) elseBody).expressions.size() == 0 || 
				elseBody instanceof Scope && ((Scope) elseBody).blocks.size() == 0)
				return "if (" + argument.toParseString() + "): " + body.toParseString();
			return "if (" + argument.toParseString() + "): " + body.toParseString() + " else: " + elseBody.toParseString();
		}
		
		@Override
		Block[] iterate() {
			return new Block[] {argument, body};
		}
	}
	static class CoreElseStatement extends CoreKeywordExpression {
		Block body;
		
		public CoreElseStatement(CurlyBracketParse body) {
			this.body = body;
		}
		
		@Override
		public String toString() {
			return "else: " + body;
		}

		@Override
		public String toParseString() {
			return "else: " + body.toParseString();
		}
		
		@Override
		Block[] iterate() {
			return new Block[] {body};
		}
	}
	static class CoreEachStatement extends CoreKeywordExpression {
		Block argument;
		Block body;
		
		public CoreEachStatement(ParenthesisParse argument, CurlyBracketParse body) {
			this.argument = argument;
			this.body = body;
		}
		
		@Override
		public String toString() {
			return "each " + argument + ": " + body;
		}

		@Override
		public String toParseString() {
			// TODO Auto-generated method stub
			return "each(" + argument.toParseString() + "): "
					+ "" + body.toParseString();
		}

		@Override
		Block[] iterate() {
			return new Block[] {argument, body};
		}
	}
	static class CoreForStatement extends CoreKeywordExpression {
		Block argument;
		Block each;
		Block body;
		
		public CoreForStatement(ParenthesisParse argument, Block each, Block body) {
			System.out.println("Parsss: " + argument);
			this.argument = argument;
			this.each = each;
			this.body = body;
		}
		
		@Override
		public String toString() {
			return "for " + argument + ": " + body;
		}

		@Override
		public String toParseString() {
			// TODO Auto-generated method stub
			return "for(" + argument.toParseString() + ")" + (each==null?"":" each " + each.toParseString()) + ": "
					+ "" + body.toParseString();
		}

		@Override
		Block[] iterate() {
			return new Block[] {argument, body};
		}
	}
	static class CoreWhileStatement extends CoreKeywordExpression {
		Block argument;
		Block body;
		
		public CoreWhileStatement(ParenthesisParse argument, Block body) {
			this.argument = argument;
			this.body = body;
		}
		
		@Override
		public String toString() {
			return "while " + argument + ": " + body;
		}

		@Override
		public String toParseString() {
			// TODO Auto-generated method stub
			return "while(" + argument.toParseString() + "): "
					+ "" + body.toParseString();
		}

		@Override
		Block[] iterate() {
			return new Block[] {argument, body};
		}
	}
	
	static class CoreNamespaceDefinition extends CoreKeywordExpression {
		String name;
		Block body;
		
		public CoreNamespaceDefinition(String name, CurlyBracketParse body) {
			this.name = name;
			this.body = body;
		}
		
		@Override
		public String toString() {
			return "namespace @" + name + "" + body;
		}

		@Override
		public String toParseString() {
			// TODO Auto-generated method stub
			return "namespace @" + name + ": " + body.toParseString();
		}
		
		@Override
		Block[] iterate() {
			return new Block[] {body};
		}
	}
	static class CoreStructureDefinition extends CoreKeywordExpression {
		String name;
		Block argument;
		Block body;
		
		public CoreStructureDefinition(String name, ParenthesisParse argument, CurlyBracketParse body) {
			this.name = name;
			this.argument = argument;
			this.body = body;
		}
		
		@Override
		public String toString() {
			return "structure @" + name + "" + argument + "" + body;
		}

		@Override
		public String toParseString() {
			return "structure @" + name + "(" + argument.toParseString() + "): "
					+ body.toParseString();
		}
		
		@Override
		Block[] iterate() {
			return new Block[] {argument, body};
		}
		
	}
	static class CoreClassDefinition extends CoreKeywordExpression {
		String name;
		Block argument;
		Block body;
		
		public CoreClassDefinition(String name, ParenthesisParse argument, CurlyBracketParse body) {
			this.name = name;
			this.argument = argument;
			this.body = body;
		}
		
		@Override
		public String toString() {
			return "class @" + name + "" + argument + "" + body;
		}

		@Override
		public String toParseString() {
			return "class @" + name + "(" + argument.toParseString() + "): "
					+ body.toParseString();
		}
		
		@Override
		Block[] iterate() {
			return new Block[] {argument, body};
		}
		
	}
	
	
	final String[][] orderOfOperation = {{
				"-",
				"+",
				"!",
				"~",
				
				"not",
				
				"++_<",
				"++_>"
			},{
				"*",
				"/",
				"%",
			},{
				"+",
				"-",
			},{
				">>",
				"<<",
			},{
				">",
				">=",
				"<",
				"<=",
				"==",
				"!=",
				
				"is",
				"in",
			},{
				"&",
				"&&",
				"|",
				"||",
				"^",
				"^^",
				
				"and",
				"or",
			},{
				"=",
				
				"*=",
				"/=",
				"%=",
				
				"+=",
				"-=",
				
				"&=",
				"|=",
				"^=",
				
				">>=",
				"<<=",
			},{
				"..",
				",",
				",,",
			},{
				"->",
				"|>",
			},{
				"print", 
				"printv",
				"printt",
				"return",
	}};

}
