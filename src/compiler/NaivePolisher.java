package compiler;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import compiler.Lexer.AliasParse;
import compiler.Lexer.Block;
import compiler.Lexer.CurlyBracketParse;
import compiler.Lexer.Expression;
import compiler.Lexer.Keyword;
import compiler.Lexer.NumberParse;
import compiler.Lexer.SquareBracketParse;
import compiler.Lexer.StringParse;
import compiler.Lexer.Symbol;
import compiler.NaiveParser.CoreClassDefinition;
import compiler.NaiveParser.CoreForStatement;
import compiler.NaiveParser.CoreFunctionCall;
import compiler.NaiveParser.CoreFunctionDefinition;
import compiler.NaiveParser.CoreIfStatement;
import compiler.NaiveParser.CoreOp;
import compiler.NaiveParser.CoreWhileStatement;

public class NaivePolisher {
//	static File inputFile = new File("src/snake.hex");
//	static File inputFile = new File("src/code6.hex");
//	static File inputFile = new File("src/fibonacci.hex");
//	static File inputFile = new File("src/primes.hex");
	static File inputFile = new File("src/code12.hex");
//	static File inputFile = new File("src/factorial.hex");
	
	ArrayList<Function> allFunctionDefinitions = new ArrayList<NaivePolisher.Function>();
	
	public static void main(String[] args) throws IOException {
		String fileContent = new String(Files.readAllBytes(inputFile.toPath())) + " ";
		
		Lexer lexer = new Lexer();
		NaiveParser parser = new NaiveParser();
		NaivePolisher polisher = new NaivePolisher();
		
		System.out.println("Input:\n" + fileContent + "\n===========================================\n");
		CurlyBracketParse b = lexer.parse(fileContent);
		System.out.println("Lexed:\n" + b.toParseString()+"\n===========================================\n");
		b = (CurlyBracketParse) parser.parse(b);
		
		System.out.println("Parsed:\n" + b.toParseString()+"\n===========================================\n");
		
		
		Scope s = polisher.polish(b, NaivePolisher.builtInScope);
		System.out.println("Polished:\n" + s.toParseString()+"\n");
		
//		System.out.println(s.toParseString());
		for(Function f : polisher.allFunctionDefinitions) {
			System.out.println("fn @" + f.s + "(" + f.type + ")" + f.body.toParseString());
		}
		
	}
	

	
	Scope polish(CurlyBracketParse b, Scope inheritScope) {
		Scope s = new Scope(inheritScope);
		return polish(b, inheritScope, s);
	}
	Scope polish(CurlyBracketParse b, Scope inheritScope, Scope s) {
		
		s.parent = b.parent;
		
		b = (CurlyBracketParse) unorderedDefinitions(b, s);
		
		ArrayList<Block> l = b.expressions;
		for(int i = 0; i < b.expressions.size(); i++) {
//			System.out.println(l.get(i));
			Block b2 = subst(l.get(i), s);
			s.add(init(b2, s));
		}
		return s;
	}
	
	
	boolean isCompileTimeDefined(Block b, Scope s) {
		return true;
	}

	Block unorderedDefinitions(Block b, Scope s) {
		if(b instanceof CoreOp) {
			CoreOp p = (CoreOp) b;
			for(int i = 0; i < p.operands.size(); i++)
				p.operands.set(i, unorderedDefinitions(p.operands.get(i), s));
		} else if (b instanceof CoreFunctionCall) {
			CoreFunctionCall p = (CoreFunctionCall) b;
			p.function = unorderedDefinitions(p.function, s);
			p.argument = unorderedDefinitions(p.argument, s);
		} else if (b instanceof Symbol) {
		} else if (b instanceof CurlyBracketParse) {
			ArrayList<Block> l = ((CurlyBracketParse)b).expressions;
			for(int i = 0; i < l.size(); i++) {
				l.set(i, unorderedDefinitions(l.get(i), s));
			}
		} else if (b instanceof SquareBracketParse) {
			ArrayList<Block> l = ((SquareBracketParse)b).expressions;
			for(int i = 0; i < l.size(); i++) {
				l.set(i, unorderedDefinitions(l.get(i), s));
			}
		} else if (b instanceof CoreFunctionDefinition) {
			CoreFunctionDefinition p = (CoreFunctionDefinition) b;
			Scope body = new Scope(s);
			ArrayList<LocalVariable> paramsType = new ArrayList<>(), returnsType = new ArrayList<>();
			if(p.funType instanceof CoreOp fnt && fnt.s.contentEquals("->")) {
				Block params = subst(fnt.operands.get(0), s);
				
				Block returns = subst(fnt.operands.get(1), s);
				if(params instanceof InitializeVariable) {
					Variable var = ((InitializeVariable) params).variable;
//					if(var.s != null)
						paramsType.add(body.init(var.s, var.type));
				} else if(params instanceof InitializeStructVariable) {
					for(Variable var : ((InitializeStructVariable) params).variables) {
//						if(var.s != null)
							paramsType.add(body.init(var.s, var.type));
					}
				} else throw new RuntimeException("Expected a Type, instead got: " + params.getClass() + "\t" + params); 

				if(returns instanceof InitializeVariable) {
					Variable var = ((InitializeVariable) returns).variable;
					if(var.s != null)
						returnsType.add(body.init(var.s, var.type));
				} else if(returns instanceof InitializeStructVariable) {
					for(Variable var : ((InitializeStructVariable) returns).variables) {
						if(var.s != null)
							returnsType.add(body.init(var.s, var.type));
					}
				} else throw new RuntimeException("Expected a Type, instead got: " + returns.getClass() + "\t" + returns);
			} else {
				System.out.println("None!");
				
				Block params = subst(p.funType, s);
				if(params instanceof InitializeVariable) {
					Variable var = ((InitializeVariable) params).variable;
//					if(var.s != null)
						paramsType.add(body.init(var.s, var.type));
				} else if(params instanceof InitializeStructVariable) {
					for(Variable var : ((InitializeStructVariable) params).variables) {
//						if(var.s != null)
							paramsType.add(body.init(var.s, var.type));
					}
				} else throw new RuntimeException("Expected a Type, instead got: " + params.getClass() + "\t" + params);
			}
			System.out.println("Params:" + paramsType);
			System.out.println("Return:" + returnsType);
			Function f = new Function(new FunctionType(p.name, new StructureType(paramsType), new StructureType(returnsType)), body, p.name);
			allFunctionDefinitions.add(f);
			s.addValue(p.name, f);
			body = polish((CurlyBracketParse)p.body, s, body);
			return new AccessValue(f);
		} else if (b instanceof CoreClassDefinition) {
			CoreClassDefinition deftype = (CoreClassDefinition) b;
			s.addValue(deftype.name, new Type(0, deftype.name));
//			s.add(deftype.name);
			return new AccessValue(new Type(0, deftype.name));
		} else if (b instanceof CoreWhileStatement) {
			CoreWhileStatement statement = (CoreWhileStatement) b;
			statement.body = unorderedDefinitions(statement.body, s);
		} else if (b instanceof CoreForStatement) {
			CoreForStatement statement = (CoreForStatement) b;
			statement.body = unorderedDefinitions(statement.body, s);
		} else if (b instanceof CoreIfStatement) {
			CoreIfStatement statement = (CoreIfStatement) b;
			statement.body = unorderedDefinitions(statement.body, s);
		} else
			throw new RuntimeException("Not implemented: " + b.getClass() + "\t" + b);
		return b;
	}
	
	ArrayList<Block> parseComma(Block b) {
		ArrayList<Block> commas = new ArrayList<Lexer.Block>();
		if(b instanceof CoreOp && ((CoreOp) b).s.contentEquals(",")) {
			ArrayList<Block> ops = ((CoreOp)b).operands;
			if(ops.size() != 2)
				throw new RuntimeException("Weird error");
			commas.addAll(parseComma(ops.get(0)));
			commas.addAll(parseComma(ops.get(1)));
			return commas;
		}
		commas.add(b);
		return commas;
	}
	
	Block init(Block b, Scope s) {
		if(b instanceof InitializeVariable) {
//			System.out.println("==="+((InitializeVariable)b).variable.s);
//			System.out.println("initing: " + ((InitializeVariable) b).variable.type);
			Variable var = ((InitializeVariable) b).variable;
//			if(var.s != null)
				return new AccessValue(s.init(var.s, var.type));
//			return new AccessValue(new LocalVariable(var.type, 0, s))
//			return new AccessValue(((InitializeVariable) b).variable);
//			return new AccessValue(s.getValue(((InitializeVariable)b).variable.s));
//			throw new RuntimeException("Initialization without a name");
		} else if(b instanceof InitializeStructVariable) {
			ArrayList<Value> variables = new ArrayList<>();
			
			for(Variable var : ((InitializeStructVariable) b).variables) {
//				if(var.s != null)
					variables.add(s.init(var.s, var.type));
			}
			return new AccessValue(new Structure(variables));
//			return new AccessValue(new Structure(((InitializeStructVariable) b).variables));
		} else if(b instanceof CoreOp) {
			CoreOp p = (CoreOp) b;
			for(int i = 0; i < p.operands.size(); i++)
				p.operands.set(i, init(p.operands.get(i), s));
			return b;
		} else if (b instanceof CoreFunctionCall) {
			CoreFunctionCall p = (CoreFunctionCall) b;
			p.function = init(p.function, s);
			p.argument = init(p.argument, s);
			p.function.parent = p;
			p.argument.parent = p;
		} else if (b instanceof NumberParse) {
		} else if (b instanceof StringParse) {
		} else if (b instanceof AliasParse) {
		} else if (b instanceof AccessValue) {
		} else if (b instanceof Keyword) {
		} else if (b instanceof CoreIfStatement) {
			CoreIfStatement p = (CoreIfStatement) b;
			
			p.argument = init(p.argument, s);
			p.argument.parent = p;
		} else if (b instanceof CoreWhileStatement) {
			CoreWhileStatement p = (CoreWhileStatement) b;
			
			p.argument = init(p.argument, s);
			p.argument.parent = p;
		}
		return b;
	}
	Block subst(Block b, Scope s) {
//		System.out.println(b.toParseString());
		if(b instanceof CoreOp p && p.s.contentEquals(",")) {
			ArrayList<Block> commas = parseComma(p), exprs = new ArrayList<>();
			ArrayList<LocalVariable> variables = new ArrayList<>();
			for(Block c : commas) {
				c = subst(c, s);
				exprs.add(c);
				if(c instanceof AccessValue av && av.value instanceof Type avt) {
					variables.add(new LocalVariable(avt, 0, null));
				} else if(c instanceof InitializeVariable iv) {
					variables.add((LocalVariable) iv.variable);
				}
			}
			if(variables.size() != commas.size()) {
				return new AccessStruct(exprs);
			}
			return new InitializeStructVariable(variables);
		} else if(b instanceof CoreOp && ((CoreOp) b).s.contentEquals(".")) {
			CoreOp p = (CoreOp) b;
			Block b1, b2;
			p.operands.set(0, b1 = subst(p.operands.get(0), s));
			p.operands.set(1, b2 = subst(p.operands.get(1), s));
			if(b1 instanceof InitializeVariable) {
				
//				if(b2 instanceof )
			}
		} else if(b instanceof CoreOp) {
			CoreOp p = (CoreOp) b;
			for(int i = 0; i < p.operands.size(); i++)
				p.operands.set(i, subst(p.operands.get(i), s));
		} else if (b instanceof CoreFunctionCall) {
			CoreFunctionCall p = (CoreFunctionCall) b;
			
			Block f = subst(p.function, s);
//			System.out.println(">>>\t" + f.getClass() + "\t" + f);
			if(p.argument instanceof AliasParse) {
				String name = ((AliasParse)p.argument).s;
				if(f instanceof AccessValue && ((AccessValue) f).value instanceof Type) {
					return new InitializeVariable(new LocalVariable((Type) ((AccessValue) f).value, 0, name));
				} else if(f instanceof InitializeVariable) {
					Type struc;
					if(((InitializeVariable) f).variable.s == null)
						struc = ((InitializeVariable) f).variable.type;
					else 
						struc = new StructureType(new ArrayList<LocalVariable>(Arrays.asList((LocalVariable)((InitializeVariable) f).variable)));
					
					return new InitializeVariable(new LocalVariable(struc, 0, name));
				} else if(f instanceof InitializeStructVariable) {
					Type struc = new StructureType(((InitializeStructVariable) f).variables);
					return new InitializeVariable(new LocalVariable(struc, 0, name));
				}
			}
			Block a = subst(p.argument, s);
			if(f instanceof Keyword && ((Keyword) f).s.contentEquals("new")) {
				Value v;
				if(a instanceof AccessValue && ((AccessValue) a).value instanceof Type) {
//					v = (new StructureType(new ArrayList<LocalVariable>(Arrays.asList(new LocalVariable((Type) ((AccessValue) f).value, 0, null)))));;
					v = (Type) ((AccessValue) a).value;
				} else if(a instanceof InitializeVariable) {
					if(((InitializeVariable) a).variable.s == null)
						v = ((InitializeVariable) a).variable;
					else 
						v = new StructureType(new ArrayList<LocalVariable>(Arrays.asList((LocalVariable)((InitializeVariable) a).variable)));
				} else if(a instanceof InitializeStructVariable) {
					v = new StructureType(((InitializeStructVariable) a).variables);
				} else throw new RuntimeException("Expected a Type, instead got: " + a.getClass() + "\t" + a);
				a = new AccessValue(v);
			}
			
			p.function = f;
			p.argument = a;
		} else if (b instanceof NumberParse) {
			return new AccessValue(new LiteralValue(((NumberParse) b).s));
		} else if (b instanceof StringParse) {
			return new AccessValue(new LiteralStringValue(((StringParse) b).s));
		} else if (b instanceof AliasParse) {
			return new AccessValue(s.getValue(((AliasParse) b).s));
		} else if (b instanceof AccessValue) {
		} else if (b instanceof Keyword) {
			if(((Keyword) b).s.contentEquals("false"))
				return new AccessValue(new BoolValue(false));
			if(((Keyword) b).s.contentEquals("true"))
				return new AccessValue(new BoolValue(true));
			if(Lexer.keywordFuncOps.contains(((Keyword) b).s))
				return new Keyword(((Keyword) b).s);
			if(Lexer.keywordsType.contains(((Keyword) b).s)) {
				return new InitializeVariable(new LocalVariable((Type)s.getValue(((Keyword) b).s), 0, null));
			}
		} else if (b instanceof SquareBracketParse) {
			SquareBracketParse p = (SquareBracketParse) b;
			if(p.expressions.size() != 1)
				throw new RuntimeException("Expected 1 argument, got: " + p.expressions.size() + " in\t" +  p.toParseString());
//			System.out.println(p.expressions);
			
			for(int i = 0; i < p.expressions.size(); i++)
				p.expressions.set(i, subst(p.expressions.get(i), s));
//			p.expressions.set(0, subst(p.expressions.get(0), s));
		} else if (b instanceof CoreIfStatement) {
			CoreIfStatement p = (CoreIfStatement) b;
			
			p.argument = subst(p.argument, s);
			p.body = polish((CurlyBracketParse)p.body, s);
			p.elseBody = polish((CurlyBracketParse)p.elseBody, s);
			p.argument.parent = p;
			p.body.parent = p;
			p.elseBody.parent = p;
		} else if (b instanceof CoreWhileStatement) {
			CoreWhileStatement p = (CoreWhileStatement) b;
			
			p.argument = subst(p.argument, s);
			p.body = polish((CurlyBracketParse)p.body, s);
			p.argument.parent = p;
			p.body.parent = p;
		} else if (b instanceof CoreForStatement) {
			CoreForStatement p = (CoreForStatement) b;
			
			p.argument = subst(p.argument, s);
			Scope body = new Scope(s);
			if(p.each != null) {
				ArrayList<Block> eachAliases = parseComma(p.each);
				ArrayList<Block> vars = new ArrayList<>();
				System.out.println(eachAliases);
				for(Block alias : eachAliases) {
					if(alias instanceof AliasParse) {
						vars.add(new AccessValue(body.init(((AliasParse) alias).s, BuiltInType.LONG.type)));
					} else
						throw new RuntimeException("Invalid: " + alias.getClass());
				}
				if(vars.size() == 1) {
					p.each = vars.get(0);
				} else {
					p.each = new AccessStruct(vars);
				}
				
//				System.out.println(body);
			}
			
			p.body = polish((CurlyBracketParse)p.body, s, body);
			p.argument.parent = p;
			p.body.parent = p;
		}
		else throw new RuntimeException("Not implemented: " + b.getClass() + "\t" + b);
		return b;
	}	
	
	static class Scope extends Block {
		int allocateSize = 0;
		int currentStackSize = 0;

		HashMap<String, Value[]> values = new HashMap<>();
		ArrayList<Block> blocks = new ArrayList<>();
		Value ans = null;
		Value ret = null;
		
		final Scope parentScope;
		
		
		
		public Scope(Scope parentScope) {
			this.parentScope = parentScope;
			if(parentScope != null)
				currentStackSize = parentScope.currentStackSize;
		}


		void addValue(String s, Value value) {
			Value[] overload = values.get(s);
			if(overload == null)
				values.put(s, new Value[] {value});
			else {
				Value[] tmp = new Value[overload.length + 1];
				System.arraycopy(overload, 0, tmp, 0, overload.length);
				values.put(s, tmp);
			}
		}
		
		LocalVariable init(String s, Type type) {
			LocalVariable v = new LocalVariable(type, currentStackSize, s);
			if(s != null)
				addValue(s, v);
			allocateSize+=type.size;
			currentStackSize += type.size;
			return v;
		}
		
		Variable getVariable(String s) {
			Value[] overload = values.get(s);
//			System.out.println("Getting " + s + " in context" + values + "");
			if(overload != null && overload[overload.length-1] instanceof Variable)
				return (Variable)overload[overload.length-1];
			if(parentScope != null)
				return parentScope.getVariable(s);
			
			throw new RuntimeException("Variable \"" + s + "\" not found");
		}
		Function getFunction(String s) {
			Value[] overload = values.get(s);
			if(overload != null && overload[overload.length-1] instanceof Function)
				return (Function)overload[overload.length-1];
			if(parentScope != null)
				return parentScope.getFunction(s);
			
			throw new RuntimeException("Function \"" + s + "\" not found");
		}
		Type getType(String s) {
			Value[] overload = values.get(s);
			if(overload != null && overload[overload.length-1] instanceof Type)
				return (Type)overload[overload.length-1];
			if(parentScope != null)
				return parentScope.getType(s);
			
			throw new RuntimeException("Type \"" + s + "\" not found");
		}
		Value getValue(String s) {
			Value[] overload = values.get(s);
//			System.out.println("Getting " + s + " in context" + values + "");
			if(overload != null)
				return overload[overload.length-1];
			if(parentScope != null)
				return parentScope.getValue(s);
			
			throw new RuntimeException("Value \"" + s + "\" not found");
		}
		
//		@Override
		boolean add(Block b) {
			b.parent = this;
			return blocks.add(b);
		}
		
		@Override
		public String toString() {
			return "Scope: [values:" + values + "]\nblocks" + blocks;
		}
		
		String variables() {
			StringBuilder sb = new StringBuilder();
			sb.append("{");
			String join = "";
			for(Entry<String, Value[]> e : values.entrySet()) {
				sb.append(join);
				join = ", ";
				if(e.getValue().length > 1)
					sb.append(e.getKey() + ": " + Arrays.toString(e.getValue()));
				else
					sb.append(e.getKey() + ": " + e.getValue()[0]);
				
			}
			sb.append("}");
			return sb.toString();
		}
		@Override
		public String toParseString() {
			String h = height();
//			System.out.println(h.length());
//			String vars = values.size() > 0 ? h + "\t$vars:" + variables + "\n" : "";
			String vals = "";
			if(values.size() > 0)
				vals = h + values.entrySet().stream().map(e -> e.getKey() + "=" + Arrays.toString(e.getValue())).collect(Collectors.joining(", ")) + "}\n\n";
			return "{ \n" + vals + blocks.stream().map((Block b)->h+"\t"+b.toParseString()).collect(Collectors.joining("\n")) + "\n"+h+"}";
		}
	}
	static class AccessFunction extends Expression {
		Function function;
		public AccessFunction(Function function) {
			this.function = function;
		}

		@Override
		public String toString() {
			return function.toString();
		}

		@Override
		public String toParseString() {
			return function.toString();
		}
	}
	static class AccessValue extends Expression {
		Value value;
		
		public AccessValue(Value value) {
			this.value = value;
		}
		
		@Override
		public String toString() {
			return value.toString();
		}

		@Override
		public String toParseString() {
			return value.toString();
		}
		
	}
	static class InitializeVariable extends Expression {
		Variable variable;
		public InitializeVariable(Variable variable) {
			this.variable = variable;
		}

		@Override
		public String toString() {
			return variable.toString();
		}

		@Override
		public String toParseString() {
			if(variable.s == null)
				return "_@" + variable;
			return variable.s + "@" + variable;
		}
	}
	static class InitializeStructVariable extends Expression {
		ArrayList<LocalVariable> variables;
		public InitializeStructVariable(ArrayList<LocalVariable> variables) {
			this.variables = variables;
		}

		@Override
		public String toString() {
			return variables.toString();
		}

		@Override
		public String toParseString() {
			return "(" + variables.stream().map(e -> {
				if(e.s == null)
					return "_@" + e;
				return e.s + "@" + e;
			}).collect(Collectors.joining(", ")) + ")";
		}
	}
	static class AccessStruct extends Expression {
		ArrayList<Block> expressions;
		
		public AccessStruct(ArrayList<Block> expressions) {
			this.expressions = expressions;
		}
		
		@Override
		public String toString() {
			return expressions.toString();
		}

		@Override
		public String toParseString() {
			return expressions.toString();
		}
		
	}
	

	static abstract class Assertion {
		
	}
	static abstract class Value {
		String s;
		final Type type;
		
		
		public Value(Type type){
			this.type = type;
		}
	}


	static class Type extends Value {
		
		static final int POINTER_SIZE = 32;
		static final int CHAR_SIZE = 8;
		static final Type metaType = new Type(POINTER_SIZE, "Type");
		final int size;
		
		Type(int size){
			super(metaType);
			this.size = size;
		}
		Type(int size, String s){
			super(metaType);
			this.size = size;
			this.s = s;
		}
		
		@Override
		public String toString() {
			return s;
		}
	}
	static class StructureType extends Type {
		ArrayList<LocalVariable> variables;
		
		StructureType(ArrayList<LocalVariable> variables) {
			super(variables.stream().mapToInt(t -> t.type.size).sum());
			this.variables = variables;
			int offset = 0;
			for(LocalVariable v : variables) {
				v.stackOffset = offset;
				offset += v.type.size;
			}
		}
		StructureType(int size, String s) {
			super(size, s);
		}
		@Override
		public String toString() {
//			System.out.println(variables);
			return "[" + variables.stream().map(e -> e.toString()).collect(Collectors.joining(", ")) + "]";
		}
	}
	static class FunctionType extends Type{
		StructureType parameters;
		StructureType returns;
		FunctionType(String s, StructureType parameters, StructureType returns) {
			super(POINTER_SIZE, s);
			this.parameters = parameters;
			this.returns = returns;
		}
		
		@Override
		public String toString() {
			return parameters + "->" + returns;
		}
	}
	static class PointerType extends Type {
		final Type elementType;
		PointerType(Type elementType, String s) {
			super(POINTER_SIZE, "Ptr[" + s + "]");
			this.elementType = elementType;
		}
		
	}
	static class ArrayType extends Type {
		final int length;
		final Type elementType;
		ArrayType(int length, Type elementType, String s) {
			super(POINTER_SIZE, s);
			this.length = length;
			this.elementType = elementType;
		} 
		
	}
	
	static abstract class Variable extends Value {

		public Variable(Type type) {
			super(type);
		}
		
	}
	static class LocalVariable extends Variable {
		int stackOffset;
		
		LocalVariable(Type type, int stackOffset, String s) {
			super(type);
			this.stackOffset = stackOffset;
			this.s = s;
		}

		
		@Override
		public String toString() {
			if(s != null)
				return s + "@[" + type + " bp+" + stackOffset + "]";
			return "_@[" + type + " bp+" + stackOffset + "]";
		}
	}
	
	
	
	static abstract class Number extends Value {

		public Number(Type type) {
			super(type);
		}
		
		
	}
	static class BoolValue extends Number {

		boolean value;
		
		public BoolValue(boolean value) {
			super(builtInScope.getType("bool"));
			this.value = value;
		}
		
		@Override
		public String toString() {
			return type + ":" + value;
		}
	}
	static class LongValue extends Number {

		long value;
		
		public LongValue(long value) {
			super(builtInScope.getType("long"));
			this.value = value;
		}
		
		@Override
		public String toString() {
			return type + ":" + value;
		}
	}
	static class IntValue extends Number {
		
		int value;
		
		public IntValue(int value) {
			super(builtInScope.getType("int"));
			this.value = value;
		}
		
		@Override
		public String toString() {
			return type + ":" + value;
		}
	}
	static class LiteralValue extends Number {
		
		BigInteger value;
		
		public LiteralValue(BigInteger value) {
			super(builtInScope.getType("lit"));
			this.value = value;
		}
		public LiteralValue(String s) {
			super(builtInScope.getType("lit"));
//			System.out.println(s);
			if(s.startsWith("0x"))
				value = BigInteger.valueOf(Long.parseLong(s.substring(2), 16));
			else if(s.startsWith("0b"))
				value = BigInteger.valueOf(Long.parseLong(s.substring(2), 2));
			else
				value = BigInteger.valueOf(Long.parseLong(s));
		}
		
		@Override
		public String toString() {
			return type + ":" + value;
		}
	}
	static class LiteralStringValue extends Value {
		
		String string;
		public LiteralStringValue(String string) {
			super(BuiltInType.STRINGLITERAL.type);
			this.string = string;
		}
		
		@Override
		public String toString() {
			return string;
		}
	}
	
	static class Function extends Value {
		Block body;
		Function(FunctionType type, Block body) {
			super(type);
			this.body = body;
		}
		Function(FunctionType type, Block body, String s) {
			super(type);
			this.body = body;
			this.s = s;
		}
		
		@Override
		public String toString() {
			return s;
		}
	}
	static class Structure extends Value {
		ArrayList<Value> values;
		public Structure(ArrayList<Value> values) {
			super(new StructureType(values.stream().map(v -> {
					return new LocalVariable(v.type, 0, v.s);
				}).collect(Collectors.toCollection(ArrayList::new))));
			this.values = values;
		}
		@Override
		public String toString() {
			return "(" + values.stream().map(e -> e.toString()).collect(Collectors.joining(", ")) + ")";
		}
	}
	
	static enum BuiltInType {
		TYPE(new Type(0, "Type")),
		VARIABLE(new Type(0, "Variable")),
		FUNCTION(new Type(0, "Function")),
		ANY(new Type(0, "Any")),
		
		CHAR(new Type(Type.CHAR_SIZE, "char")),
		BOOL(new Type(1, "bool")),
		NUM(new Type(Type.POINTER_SIZE, "num")),
		
		BIT(new Type(1, "bit")),
		COUPLE(new Type(2, "couple")),
		NIBBLE(new Type(4, "nibble")),
		BYTE(new Type(8, "byte")),
		SHORT(new Type(16, "short")),
		INT(new Type(32, "int")),
		LONG(new Type(64, "long")),
		BULK(new Type(128, "bulk")),
		
		LITERAL(new Type(Type.POINTER_SIZE, "lit")),
		STRINGLITERAL(new Type(Type.POINTER_SIZE, "StringLit")),
		;
		
		String name;
		Type type;
		
		private BuiltInType(Type type) {
			this.type = type;
			name = type.s;
		}
	}
	
	static final Scope builtInScope = new Scope(null) {
		{
			for(BuiltInType b : BuiltInType.values())
				addValue(b.name, b.type);
			
	}};
	
}
