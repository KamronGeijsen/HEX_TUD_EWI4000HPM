package compiler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import compiler.NaiveTypechecker.Body;

public class Lexer {
	
//	static File inputFile = new File("src/testbench/if-statement.hex");
//	static File inputFile = new File("src/testbench/if-else-statement.hex");
//	static File inputFile = new File("src/testbench/function-definition.hex");
//	static File inputFile = new File("src/code6.hex");
//	static File inputFile = new File("src/snake.hex");
	static File inputFile = new File("examples/tests.hex");
	
	public static void main(String[] args) throws IOException {
		String readLines = new String(Files.readAllBytes(inputFile.toPath())) + " ";
		
		Lexer parser = new Lexer();
		
		CurlyBracketParse root = parser.parse(readLines);
		
//		System.out.println(root.toString());
		System.out.println(root.toParseString());
	}
	
	CurlyBracketParse parse(String s) {
		final CurlyBracketParse root = new CurlyBracketParse();
		ExpressionParse parentLine = root;
		ExpressionParse currentLine = null;
		
		StringBuilder line = new StringBuilder();
//		char 
//		System.out.println("start");
		boolean newLine = true;
		boolean op = false;
		int i = 0;
		char c = s.charAt(0);
		stop:for(;;) {
//			System.out.println(c + " - " + i + "\t" + line.length());
			if(c == '\n' || c == '\r') {
				if(!op)
					newLine = true;
				
				if(++i < s.length()) c = s.charAt(i); else break;
			}
			else if(c == ';') {
				newLine = true;
				op = false;
				if(++i < s.length()) c = s.charAt(i); else break;
			}
			else if(c == '{') {
//				if(!op && newLine) {
//					newLine = false;
//					currentLine = new ParenthesisParse();
//					currentLine.parent = parentLine;
//					parentLine.expressions.add(currentLine);
//				}
				CurlyBracketParse sub = new CurlyBracketParse();
				currentLine.expressions.add(sub);
				sub.parent = currentLine;
				parentLine = sub;
				currentLine = null;
				
				newLine = true;
				op = false;
				
				if(++i < s.length()) c = s.charAt(i); else break;
			} else if(c == '}') {
//				System.out.println(parentLine.getClass());
				if(parentLine instanceof CurlyBracketParse) {
					currentLine = new CurlyBracketParse();
					parentLine = (ExpressionParse) parentLine.parent;
					newLine = true;
				}
				else {
					System.out.println("invalid bracket closure");
					return null;
				}
				if(parentLine instanceof CurlyBracketParse) {
					newLine = true;
				}
				currentLine = parentLine;
				parentLine = (ExpressionParse) currentLine.parent;
				
				op = false;
				
				if(++i < s.length()) c = s.charAt(i); else break;
			} else if(c == '[') {
				if(newLine) {
					newLine = false;
					currentLine = new ParenthesisParse();
					currentLine.parent = parentLine;
					parentLine.expressions.add(currentLine);
				}
				op = true;
				ExpressionParse newCurrentLine = new SquareBracketParse();
				currentLine.expressions.add(newCurrentLine);
				newCurrentLine.parent = currentLine;
				currentLine = newCurrentLine;
				
				if(++i < s.length()) c = s.charAt(i); else break;
			} else if(c == ']') {
				if(!(currentLine instanceof SquareBracketParse)) {
					System.out.println("invalid bracket closure");
					return null;
				}
				currentLine = (ExpressionParse) currentLine.parent;
				op = false;

				if(++i < s.length()) c = s.charAt(i); else break;
			} else if(c == '(') {
				if(newLine) {
					newLine = false;
					currentLine = new ParenthesisParse();
					currentLine.parent = parentLine;
					parentLine.expressions.add(currentLine);
				}
				op = true;
				ExpressionParse newCurrentLine = new ParenthesisParse();
				currentLine.expressions.add(newCurrentLine);
				newCurrentLine.parent = currentLine;
				parentLine = currentLine;
				currentLine = newCurrentLine;
				
//				System.out.println(currentLine);
				
				if(++i < s.length()) c = s.charAt(i); else break;
			} else if(c == ')') {
				if(!(currentLine instanceof ParenthesisParse)) {
					System.out.println("invalid bracket closure");
					return null;
				}
//				System.out.println("====="+currentLine.toParseString());
				currentLine = parentLine;
				parentLine = (ExpressionParse) currentLine.parent;
				op = false;
				if(++i < s.length()) c = s.charAt(i); else break;
			} else if(c == '"' || c == '\'') {
				if(newLine) {
					newLine = false;
					currentLine = new ParenthesisParse();
					currentLine.parent = parentLine;
					parentLine.expressions.add(currentLine);
				}
				op = false;
				
				if(++i < s.length())
					c = s.charAt(i);
				while(c != '"') {
					line.append(c);
					if(++i < s.length())
						c = s.charAt(i);
					else break;
				}
				currentLine.expressions.add(new StringParse(line.toString()));

				line.setLength(0);
				if(++i < s.length()) c = s.charAt(i); else break;
			} else if(c == ' ' || c == '\t') {
				if(++i < s.length()) c = s.charAt(i); else break;
			} else {
//				System.out.println(c + " : " + i);
//				System.out.println(isOperation('!'));
				
				if(isOperation(c)) {
					
					if(isRegularOperation(c) && (i+1 < s.length() && (s.charAt(i+1) == c || s.charAt(i+1) == '='))) {
//						System.out.println(c + "" + s.charAt(i+1));
						if(c == '/' && s.charAt(i+1) == '/') {
							while(s.charAt(++i) != '\n') {}
							c = s.charAt(++i);
//							System.out.println(c + " - " + i);
							continue;
						} else if(c == '^' && s.charAt(i+1) == '^') {
							break;
						} else if((c == '+' || c == '-') && s.charAt(i+1) == c) {
//							System.out.println(">===\t" + c + "" +s.charAt(i+1));
							if(op || newLine) { //Prefix
								if(newLine) {
									newLine = false;
									currentLine = new ParenthesisParse();
									currentLine.parent = parentLine;
									parentLine.expressions.add(currentLine);
								}
								newLine = false;
								op = true;
								currentLine.expressions.add(new OperationParse(c + "" + s.charAt(i + 1) + "_>"));
							}
							else { // Postfix
								newLine = true;
								op = false;
								currentLine.expressions.add(new OperationParse(c + "" + s.charAt(i + 1) + "_<"));
							}
							
							
							
							if((i+=2) < s.length())
								c = s.charAt(i);
							continue;
						} else {
							newLine = false;
							op = true;
							currentLine.expressions.add(new OperationParse(c + "" + s.charAt(i+1)));
							if((i+=2) < s.length())
								c = s.charAt(i);
						}
					}
					else {
						if(c == '-' && s.charAt(i+1) == '>') {
//							System.out.println("function");
							newLine = false;
							op = true;
							currentLine.expressions.add(new OperationParse("->"));
//							System.out.println(currentLine);
							if((i+=2) < s.length())
								c = s.charAt(i);
							continue;
						}
						if(c == '~' && (s.charAt(i+1) == '&' || s.charAt(i+1) == '|' || s.charAt(i+1) == '^')) {
							System.out.println("function");
							newLine = false;
							op = true;
							currentLine.expressions.add(new OperationParse(c + "" +s.charAt(i+1)));
							if((i+=2) < s.length())
								c = s.charAt(i);
							continue;
						}
						if(c == '!' && (s.charAt(i+1) == '&' || s.charAt(i+1) == '|' || s.charAt(i+1) == '^' || 
								s.charAt(i+1) == '=')) {
							System.out.println("function");
							newLine = false;
							op = true;
							currentLine.expressions.add(new OperationParse(c + "" +s.charAt(i+1)));
							if((i+=2) < s.length())
								c = s.charAt(i);
							continue;
						}
						if(c == '.' && s.charAt(i+1) == '.') {
							newLine = false;
							op = true;
							currentLine.expressions.add(new OperationParse(c + "" +s.charAt(i+1)));
							if((i+=2) < s.length())
								c = s.charAt(i);
							continue;
						}
						
						if(c == '!') {
							if(newLine) {
								newLine = false;
								currentLine = new ParenthesisParse();
								currentLine.parent = parentLine;
								parentLine.expressions.add(currentLine);
							}
							newLine = false;
							op = true;
							currentLine.expressions.add(new OperationParse(c + ""));
							if((i++) < s.length())
								c = s.charAt(i);
							continue;
						}
						newLine = false;
						op = true;
						currentLine.expressions.add(new OperationParse(Character.toString(c)));
						if(++i < s.length())
							c = s.charAt(i);
					}
					
					continue;
				} else if(isNumber(c) || isLetter(c)){
					
					char start = c;
					line.append(c);
					if(++i < s.length())
						c = s.charAt(i);
					else break stop;
					while(isNumber(c) || isLetter(c) || c == '_') {
						line.append(c);
						if(++i < s.length())
							c = s.charAt(i);
						else break;
					}
					if(isNumber(start)) {
						if(newLine) {
//							System.out.println("newline");
							newLine = false;
							currentLine = new ParenthesisParse();
							currentLine.parent = parentLine;
							parentLine.expressions.add(currentLine);
						}
						op = false;
						
						currentLine.expressions.add(new NumberParse(line.toString()));
						
					} else if(isLetter(start)) {
						String alias = line.toString();
						if(isKeyword(alias)) {
							if(keywordBinOps.contains(line.toString())) {
								newLine = false;
								op = true;
//								System.out.println("==>\t" + line);
							} else {
								if(newLine) {
//									System.out.println("newline");
									newLine = false;
									currentLine = new ParenthesisParse();
									currentLine.parent = parentLine;
									parentLine.expressions.add(currentLine);
								}
								op = false;
							}
							if(alias .contentEquals("elif")) {
								currentLine.expressions.add(new Keyword("else"));
								currentLine.expressions.add(new OperationParse(":"));
								currentLine.expressions.add(new Keyword("if"));
							}else if(alias .contentEquals("else")) {
								currentLine.expressions.add(new Keyword("else"));
								currentLine.expressions.add(new OperationParse(":"));
							}else
								currentLine.expressions.add(new Keyword(alias));
						}
						else {
							if(newLine) {
//								System.out.println("newline");
								newLine = false;
								currentLine = new ParenthesisParse();
								currentLine.parent = parentLine;
								parentLine.expressions.add(currentLine);
							}
							op = false;
							currentLine.expressions.add(new AliasParse(alias));
						}
					}
					
					
					
						
					line.setLength(0);
					continue;
				}

//				if(++i < s.length())
//					c = s.charAt(i);
			}
		}
		return root;
	}
	
	public static boolean isLetter(char c) {
		return ((c|32)-97&0xffff)<26;
	}
	public static boolean isNumber(char c) {
		return (c-48&0xffff)<10;
	}
	public static boolean isOperation(char c) {
		final long bitwise0to63 = 
				(1l << '!') |
				(1l << '%') |
				(1l << '&') |
				(1l << '*') |
				(1l << '+') |
				(1l << ',') |
				(1l << '-') |
				(1l << '.') |
				(1l << '/') |
				(1l << ':') |
				(1l << '<') |
				(1l << '=') |
				(1l << '>') |
				(1l << '?') |
				0l;
		final long bitwise64to127 = 
				(1l << '@') |
				(1l << '^') |
				(1l << '_') |
				(1l << '|') |
				(1l << '~') |
				0l;
//		System.out.println((char)(0b1100_0000 | '^'));
		
		if(c > 0b1000_0000)
		System.out.println(Long.toBinaryString((((c & 0b100_0000) == 0 ? bitwise0to63 : bitwise64to127) >>> c)));
		return ((((c & 0b100_0000) == 0 ? bitwise0to63 : bitwise64to127) >>> c) & 1) != 0;
	}
	public static boolean isRegularOperation(char c) {
		final long bitwise0to63 = 
				(1l << '%') |
				(1l << '&') |
				(1l << '*') |
				(1l << '+') |
				(1l << '-') |
				(1l << '/') |
				(1l << '<') |
				(1l << '=') |
				(1l << '>') |
				0l;
		final long bitwise64to127 = 
				(1l << '^') |
				(1l << '|') |
				0l;
//		System.out.println((char)(0b1100_0000 | '^'));
		
		if(c > 0b1000_0000)
		System.out.println(Long.toBinaryString((((c & 0b100_0000) == 0 ? bitwise0to63 : bitwise64to127) >>> c)));
		return ((((c & 0b100_0000) == 0 ? bitwise0to63 : bitwise64to127) >>> c) & 1) != 0;
	}
	public static boolean isKeyword(String s) {
		return keywords.contains(s);
	}
	public static boolean isSpecialOperation(String s) {
		return operations.contains(s);
	}

	public static abstract class Block {
		Block parent;
		String height() {
			String s = "";
			Block iterator = parent;
			while(iterator != null) {
				if(iterator instanceof CurlyBracketParse
						|| iterator instanceof Body
						)
					s+="\t";
				iterator = iterator.parent;
			}
			return s;
		}
		public abstract String toString();
		public abstract String toParseString();
	}
	public static abstract class Expression extends Block{
		
	}
	
	public static abstract class ExpressionParse extends Expression {
		ArrayList<Block> expressions = new ArrayList<Block>();
	}
	public static class ParenthesisParse extends ExpressionParse {
		
		public String toParseString() {
			return (parent instanceof CurlyBracketParse?"":"(") + expressions.stream().map((Block e)->e.toParseString()).collect(Collectors.joining(" ")) + (parent instanceof CurlyBracketParse?"":")");
		}
		
		@Override
		public String toString() {
			return "expr" + expressions.toString();
		}
		
//		@Override
		public Iterator<Block> iterator() {
			return expressions.iterator();
		}
	}
	public static class SquareBracketParse extends ExpressionParse {
		
		public String toParseString() {
			return "[" + expressions.stream().map((Block e)->e.toParseString()).collect(Collectors.joining(" ")) + "]";
		}
		
		@Override
		public String toString() {
			return "bexpr" + expressions.toString();
		}
		
//		@Override
		public Iterator<Block> iterator() {
			return expressions.iterator();
		}
	}
	
	public static class CurlyBracketParse extends ExpressionParse {
		
		public String toParseString() {
			return "{\n" + expressions.stream().map((Block b)->height()+"\t"+b.toParseString()).collect(Collectors.joining("\n")) + "\n"+height()+"}";
		}
		
		@Override
		public String toString() {
			return "cexpr" + expressions.toString();
		}
		
//		@Override
		public Iterator<Block> iterator() {
			return expressions.iterator();
		}
	}
	
	public static abstract class Symbol extends Block {
		final String s;
		Symbol(String s) {
			this.s = s;
		}
		
		public String toParseString() {
			return toString();
		}
		
	}
	public static class AliasParse extends Symbol {
		
		public AliasParse(String s) {
			super(s);
		}
		
		@Override
		public String toString() {
			return '@'+s;
		}
	}
	public static class Keyword extends Symbol {
		
		public Keyword(String s) {
			super(s);
		}
		
		@Override
		public String toString() {
			return '$'+s;
		}
	}
	public static class NumberParse extends Symbol {
		NumberParse(String s){
			super(s);
		}
		
		@Override
		public String toString() {
			return '#'+s;
		}
	}
	public static class OperationParse extends Symbol {
		OperationParse(String s){
			super(s);
		}
		
		@Override
		public String toString() {
			return '$'+s;
		}
	}
	public static class StringParse extends Symbol {


		public StringParse(String s) {
			super(s);
		}

		@Override
		public String toString() {
			return "s'" + s + "'";
		}
	}
	
	enum BracketType {
		BRACKET("[", "]"), 
		PARENTHESIS("(", ")"), 
		CORNERBRACKET("<", ">"), 
		CURLYBRACKET("{", "}"), 
		QUOTATION("\"", "\""), 
		APOSTROPHE("\'", "\'"),
		VOID("", "");
		
		String open, close;
		BracketType(String open, String close){
			this.open = open;
			this.close = close;
		}
	}
	
	public static Set<String> keywords = new HashSet<String>(Arrays.asList(new String[] {
			"bit",
			"couple",
			"nibble",
			"byte",
			"short",
			"int",
			"long",
			"bulk",
			
			"bool",
			"num",
			
			"auto",
			
			
			"if",
			"else",
			"elif",
			"for",
			"while",
			"each",
			
			"fn",
			"lambda",
			
			"label",
			
			"ns",
			"class",
			"struct",
			"acls", //Anonymous class
			"astc", //Anonymous struct
			"refinement",
			
			
			"and",
			"or",
			"not",
			
			"is",
			"in",
			"as",
			
			
			"new",
			
			"public",
			"private",
			
			
			"print",
			"printv",
			"printt",
			"return",
			"benchmark",
			
			
			"true",
			"false",
			"ans",
			"null",
	}));
	public static Set<String> keywordsType = new HashSet<String>(Arrays.asList(new String[] {
			"bit",
			"couple",
			"nibble",
			"byte",
			"short",
			"int",
			"long",
			"bulk",
			
			"bool",
			"num",
			"char",
			
			"void",
	}));
	public static Set<String> keywordBinOps= new HashSet<String>(Arrays.asList(new String[] {
			"and",
			"or",
			
			"is",
			"in",
			"as",
			
			"each",
			"else",
			"elif",
	}));
	public static Set<String> keywordUnOps = new HashSet<String>(Arrays.asList(new String[] {
			"not",

			"print",
			"return",
	}));
	public static Set<String> keywordFuncOps = new HashSet<String>(Arrays.asList(new String[] {		
			"new",
			"ans",

			"public",
	}));
	public static Set<String> keywordLit= new HashSet<String>(Arrays.asList(new String[] {
			"ans",
			"null",
			"true",
			"false",
	}));
	public static Set<String> operations = new HashSet<String>(Arrays.asList(new String[] {
			"->",
			"~&",
			"~|",
			"~^",
			"!&",
			"!|",
			"!&&",
			"!||",
	}));
	public static Set<String> operationPostfix = new HashSet<String>(Arrays.asList(new String[] {
			"++_<",
			"--_<",
	}));
}