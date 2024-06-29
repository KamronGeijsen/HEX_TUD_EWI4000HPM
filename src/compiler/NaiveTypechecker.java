package compiler;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.sql.Array;
import java.util.*;

import compiler.Lexer.AliasParse;
import compiler.Lexer.Block;
import compiler.Lexer.CurlyBracketParse;
import compiler.Lexer.Expression;
import compiler.Lexer.Keyword;
import compiler.Lexer.NumberParse;
import compiler.Lexer.ParenthesisParse;
import compiler.Lexer.Symbol;
import compiler.NaiveParser.CoreBenchmarkStatement;
import compiler.NaiveParser.CoreFunctionCall;
import compiler.NaiveParser.CoreFunctionDefinition;
import compiler.NaiveParser.CoreIfStatement;
import compiler.NaiveParser.CoreOp;
import compiler.NaiveParser.CoreRefinementDefinition;
import compiler.NaiveParser.CoreStructureDefinition;
import compiler.NaiveParser.CoreWhileStatement;

public class NaiveTypechecker {

	//	static File inputFile = new File("src/snake.hex");
//	static File inputFile = new File("src/code6.hex");
//	static File inputFile = new File("src/fibonacci.hex");
//	static File inputFile = new File("src/primes.hex");
//	static File inputFile = new File("src/code12.hex");
//	static File inputFile = new File("src/factorial.hex");
//	static File inputFile = new File("examples/mod of PowerOfTwo.hex");
	static File inputFile = new File("examples/tests.hex");
	
//	ArrayList<Function> allFunctionDefinitions = new ArrayList<NaivePolisher.Function>();
	
	public static void main(String[] args) throws IOException {
		String fileContent = new String(Files.readAllBytes(inputFile.toPath())) + " ";
		
		Lexer lexer = new Lexer();
		NaiveParser parser = new NaiveParser();
		NaiveTypechecker polisher = new NaiveTypechecker();
		
		System.out.println("Input:\n" + fileContent + "\n===========================================\n");
		CurlyBracketParse b = lexer.parse(fileContent);
		System.out.println("Lexed:\n" + b.toParseString()+"\n===========================================\n");
		b = (CurlyBracketParse) parser.parse(b);
		
		System.out.println("Parsed:\n" + b.toParseString()+"\n===========================================\n");
		
//		System.out.println();
		
		
		
		Body moduleBody = (Body)polisher.createContexts(b, polisher.builtins);
//		System.out.println(moduleBody.context.localValues);
//		System.out.println();
		polisher.resolveTypes(moduleBody, moduleBody.context);
		
		System.out.println(moduleBody.toParseString());
		polisher.typeChecker(moduleBody, moduleBody.context);
		System.out.println(moduleBody.toParseString());

//		System.out.println(moduleBody.context.functionDefinitions.get(1).body.context.localValues);
//		b = (CurlyBracketParse) polisher.unorderedDefinitions(b, NaivePolisher.builtInScope);
//		Scope s = polisher.polish(b, NaivePolisher.builtInScope);
//		for(Value[] vs : s.values.values())
//			for(Value v : vs)
//				if(v instanceof Function f)
//					f.body = polisher.polish(f.body, NaivePolisher.builtInScope);
//		System.out.println("Polished:\n" + s.toParseString()+"\n");
//		
//		
////		System.out.println(s.toParseString());
//		for(Function f : polisher.allFunctionDefinitions) {
//			System.out.println("fn @" + f.s + "(" + f.type + ")" + f.body.toParseString());
//		}
		
	}
	
	final Context builtins = new Context(null);
	public NaiveTypechecker() {
		Type longType, boolType;
		builtins.types = Map.of(
				"int", new Primitive("int", 32),
				"long", longType=new Primitive("long", 64),
				"boolean", boolType=new Primitive("boolean", 8),
				"void", new Primitive("void", 0)
				);
		builtins.addFunction(new BuiltinFunction(new FunctionIdentifier("%",new FunctionType(new StructType(new ArrayList<>(List.of(longType, longType))), new StructType(new ArrayList<>(List.of(longType)))))));
		builtins.addFunction(new BuiltinFunction(new FunctionIdentifier("/",new FunctionType(new StructType(new ArrayList<>(List.of(longType, longType))), new StructType(new ArrayList<>(List.of(longType)))))));
		builtins.addFunction(new BuiltinFunction(new FunctionIdentifier("&",new FunctionType(new StructType(new ArrayList<>(List.of(longType, longType))), new StructType(new ArrayList<>(List.of(longType)))))));
		builtins.addFunction(new BuiltinFunction(new FunctionIdentifier("+",new FunctionType(new StructType(new ArrayList<>(List.of(longType, longType))), new StructType(new ArrayList<>(List.of(longType)))))));
		builtins.addFunction(new BuiltinFunction(new FunctionIdentifier("-",new FunctionType(new StructType(new ArrayList<>(List.of(longType, longType))), new StructType(new ArrayList<>(List.of(longType)))))));
		builtins.addFunction(new BuiltinFunction(new FunctionIdentifier("==",new FunctionType(new StructType(new ArrayList<>(List.of(longType, longType))), new StructType(new ArrayList<>(List.of(boolType)))))));
		builtins.addFunction(new BuiltinFunction(new FunctionIdentifier("!=",new FunctionType(new StructType(new ArrayList<>(List.of(longType, longType))), new StructType(new ArrayList<>(List.of(boolType)))))));
		builtins.addFunction(new BuiltinFunction(new FunctionIdentifier("<=",new FunctionType(new StructType(new ArrayList<>(List.of(longType, longType))), new StructType(new ArrayList<>(List.of(boolType)))))));
		builtins.addFunction(new BuiltinFunction(new FunctionIdentifier(">",new FunctionType(new StructType(new ArrayList<>(List.of(longType, longType))), new StructType(new ArrayList<>(List.of(boolType)))))));
		builtins.addFunction(new BuiltinFunction(new FunctionIdentifier(">=",new FunctionType(new StructType(new ArrayList<>(List.of(longType, longType))), new StructType(new ArrayList<>(List.of(boolType)))))));
		builtins.addFunction(new BuiltinFunction(new FunctionIdentifier("<",new FunctionType(new StructType(new ArrayList<>(List.of(longType, longType))), new StructType(new ArrayList<>(List.of(boolType)))))));
		builtins.addFunction(new BuiltinFunction(new FunctionIdentifier("return",new FunctionType(new StructType(new ArrayList<>(List.of(longType))), new StructType(new ArrayList<>(List.of()))))));
//		builtins.functions = Set.of(
//				);
//		builtins.typeDefinitions = List.of(
//				
//				
//				
//				);
	}
	
	
	
	ArrayList<Block> parseComma(Block b) {
		ArrayList<Block> commas = new ArrayList<Lexer.Block>();
		if(b instanceof CoreOp && ((CoreOp) b).s.contentEquals(",")) {
			ArrayList<Block> ops = ((CoreOp)b).operands;
			if(ops.size() == 0)
				return commas;
			if(ops.size() != 2)
				throw new RuntimeException("Incorrect arity: , should have 2 (or empty), but got " + ops.size());
			commas.addAll(parseComma(ops.get(0)));
			commas.addAll(parseComma(ops.get(1)));
			return commas;
		}
		commas.add(b);
		return commas;
	}
	
	StructType getStructType(Block b, Body args) {
		ArrayList<Block> todo = new ArrayList<>();
		ArrayList<Block> commaSeparated = new ArrayList<>();
		todo.add(b);
		while(!todo.isEmpty()) {
			Block r = todo.remove(0);
			if(r instanceof CoreOp o && o.s.equals(",")) {
				
				todo.addAll(o.operands);
				
			} else {
				commaSeparated.add(r);
			}
		}
		
		
		ArrayList<Type> types = new ArrayList<>();
		ArrayList<String> vars = new ArrayList<>();
		for(int i = 0; i < commaSeparated.size(); i++) {
//			System.out.println(commaSeparated.get(i).getClass());
			
			if(commaSeparated.get(i) instanceof Keyword k && args.context.isType(k.s)) {
				if(k.s.equals("void")){
					continue;
				} else {
					types.add(args.context.getType(k.s));
					vars.add("_");
				}
				
				
			} else if (commaSeparated.get(i) instanceof Symbol s && args.context.isType(s.s)) {
				types.add(args.context.getType(s.s));
				vars.add("_");
			} else if (commaSeparated.get(i) instanceof Symbol s) {
				types.add(args.context.getType("auto"));
				vars.add(s.s);
			} else if (commaSeparated.get(i) instanceof CoreFunctionCall c) {
//				System.out.println(c.function.getClass());
				if(c.function instanceof Keyword k && args.context.isType(k.s) && c.argument instanceof Symbol s) {
					types.add(args.context.getType(k.s));
					vars.add(s.s);
				} else if (c.function instanceof Symbol s && args.context.isType(s.s) && c.argument instanceof Symbol ss) {
					types.add(args.context.getType(s.s));
					vars.add(ss.s);
				} else if (c.function instanceof CoreOp co && co.s.equals(".")
						&& co.operands.size() == 2
						&& co.operands.get(0) instanceof Keyword s
						&& s.s.equals("long") && co.operands.get(1) instanceof Lexer.SquareBracketParse bp
						&& c.argument instanceof AliasParse a) {

					Type type;
					if (bp.expressions.size() == 1 && bp.expressions.get(0) instanceof NumberParse n)
						type = new NaiveArrayType(Long.parseLong(n.s));
					else if (bp.expressions.isEmpty())
						type = new NaiveArrayType(-1);
					else throw new RuntimeException("Invalid arity" + bp.expressions);

					types.add(type);
					vars.add(a.s);
				}
			} else throw new RuntimeException("Unexpected expression: " + commaSeparated.get(i));
		}
		StructType struct = new StructType(types, vars);
//		System.out.println("Defined " + struct);
		args.context.localValues.types.addAll(types);
		args.context.localValues.vars.addAll(vars);
		return struct;
	}
	
	FunctionType getFunctionType(Block b, Body args) {
		if(b instanceof CoreOp o && o.s.equals("->") && o.operands.size() == 2) {
//			getStructType(o.operands.get(0), args);
//			System.out.println(getStructType(o.operands.get(0), args).values);
//			System.out.println("->");
////			getStructType(o.operands.get(1), args);
//			System.out.println(getStructType(o.operands.get(1), args).values);
////			System.out.println(o.operands.get(0).getClass());
////			System.out.println(o.operands.get(1).getClass());
//			System.out.println();
//			System.out.println(b.getClass());
			return new FunctionType(
					getStructType(o.operands.get(0), args),
					getStructType(o.operands.get(1), args));
		}else {
//			System.out.println(getStructType(b, args).values);
//			System.out.println("-> void");
			
			return new FunctionType(
					getStructType(b, args),
					new StructType(new ArrayList<>())
					);
		}
//		throw new RuntimeException("Invalid function pointer");
	}
	
	Block createContexts(Block b, Context parent) {
		if(b instanceof CurlyBracketParse c) {
			Body newBody = new Body(parent);
			for(Block e : c.expressions) {
				if(e instanceof CoreFunctionDefinition fd) {
					newBody.context.functions.put(fd.name, null);
				} else if(e instanceof CoreStructureDefinition sd) {
					newBody.context.types.put(sd.name, new StructType(sd.name));
				} else if(e instanceof CoreRefinementDefinition rd) {
					newBody.context.types.put(rd.name, new RefinementType(new StructType(rd.name), rd.name));
				}
			}
			for(Block e : c.expressions) {
				newBody.expr.add(createContexts(e, newBody.context));
			}
			for(Block e : newBody.expr) e.parent = newBody;
//			System.out.println("Defined {} context " + newBody.context.localValues);
			return newBody;
		} else if(b instanceof CoreFunctionDefinition fd) {
			Body argumentsBody = new Body(parent);
			argumentsBody.context.localValues.name = fd.name;
			FunctionIdentifier fid = new FunctionIdentifier(fd.name, getFunctionType(fd.funType, argumentsBody));
			Body body = (Body) createContexts(fd.body, argumentsBody.context);
			argumentsBody.expr.add(body);
			for(Block e : argumentsBody.expr) e.parent = argumentsBody;
			
			parent.addFunction(new Function(fid, body));
			return new FunctionObjectGenerator(fid);

		} else if(b instanceof CoreRefinementDefinition rd) {
			Body argumentsBody = new Body(parent);
			
			RefinementType rt = (RefinementType) parent.types.get(rd.name);
			argumentsBody.context.localValues = (StructType) rt.inheritType;
//			argumentsBody.context.localValues.name = rd.name;
			
			argumentsBody.context.localValues.types = new ArrayList<>();
			argumentsBody.context.localValues.vars = new ArrayList<>();
			for(Block e : argumentsBody.expr) e.parent = argumentsBody;
			
//			getStructType(b, argumentsBody)
			StructType st = getStructType(rd.inheritType, argumentsBody); 
//			argumentsBody.context.localValues.types = st.types;
//			argumentsBody.context.localValues.vars = st.vars;
			
			Body body = (Body) createContexts(rd.body, argumentsBody.context);
			argumentsBody.expr.add(body);
			Type t = parent.getType(rd.name);
			
			rt.customMatch = body;
			
			return new TypeObjectGenerator(t);
		} else if(b instanceof CoreOp o) {
//			System.out.println(o.s);
			for(int i = 0; i < o.operands.size(); i++) {
				o.operands.set(i, createContexts(o.operands.get(i), parent));
			}
			
			return o;
		} else if(b instanceof CoreFunctionCall fc) {
//			System.out.println(fc + " => " + ((CoreOp)fc.function).operands);
			if (fc.function instanceof Symbol s && parent.isType(s.s) && fc.argument instanceof AliasParse a) {
				parent.localValues.types.add(parent.getType(s.s));
				parent.localValues.vars.add(a.s);
//				System.out.println("added " + a.s);
				return a;
			} else if (fc.function instanceof Symbol s) {
				fc.function = new FunctionObjectGenerator(new FunctionIdentifier(s.s, null));
				return fc;
			} else if (fc.function instanceof CoreOp co && co.s.equals(".")
					&& co.operands.size() == 2
					&& co.operands.get(0) instanceof Keyword s
					&& s.s.equals("long") && co.operands.get(1) instanceof Lexer.SquareBracketParse bp
					&& fc.argument instanceof AliasParse a) {
				if(bp.expressions.size() == 1 && bp.expressions.get(0) instanceof NumberParse n)
					parent.localValues.types.add(new NaiveArrayType(Long.parseLong(n.s)));
				else if(bp.expressions.isEmpty())
					parent.localValues.types.add(new NaiveArrayType(-1));
				else throw new RuntimeException("Invalid arity" + bp.expressions);

				parent.localValues.vars.add(a.s);
//				System.out.println(bp.getClass());
//				fc.function = new FunctionObjectGenerator(new FunctionIdentifier(s.s, null));
				return a;
			} else {
				System.out.println(b);
				
				throw new RuntimeException("Not implemented: " + b.getClass());
			}
		} else if(b instanceof Symbol s) {
//			if(b instanceof Keyword kw && kw.s.equals("true"))
//				return new LiteralGenerator(0, );
			return b;
		} else if(b instanceof CoreStructureDefinition sd){
			Body typeBody = new Body(parent);
			typeBody.context.localValues = (StructType) parent.types.get(sd.name);
			
			Body body = (Body) createContexts(sd.body, typeBody.context);
			body.context.localValues.name = sd.name;
			for(Block e : typeBody.expr) e.parent = typeBody;
			
			return new TypeObjectGenerator(parent.getType(sd.name));
		} else if(b instanceof CoreIfStatement ifs) {
			ifs.argument = createContexts(ifs.argument, parent);
			ifs.body = createContexts(ifs.body, parent);
			if(ifs.elseBody != null)
				ifs.elseBody = createContexts(ifs.elseBody, parent);
			return b;
		} else if(b instanceof CoreWhileStatement iws) {
			iws.argument = createContexts(iws.argument, parent);
			iws.body = createContexts(iws.body, parent);
			return b;
		} else if(b instanceof CoreBenchmarkStatement iws) {
			iws.expr = createContexts(iws.expr, parent);
			iws.body = createContexts(iws.body, parent);
			return b;
		} else if(b instanceof ParenthesisParse p) {
			for (int i = 0; i < p.expressions.size(); i++) {
				p.expressions.set(i, createContexts(p.expressions.get(i), parent));
			}
			return p;
		} else if(b instanceof Lexer.SquareBracketParse bp) {
			;
//			System.out.println(bp.expressions.get(0).getClass() + "aaaaaaaaa");
			return new NaiveArrayGenerator(parseComma(bp.expressions.get(0)));
		} else {
			throw new RuntimeException("Not implemented: " + b.getClass());
		}
	}
	
	boolean finishedType(Type t, Context context) {
		if(t instanceof FunctionType ft) {
			if(finishedType(ft.args, context) && finishedType(ft.rets, context)) {
				t.size = ft.args.size + ft.rets.size;
				return true;
			}
			return false;
		} else if(t instanceof StructType st){
			int size = 0;
			for(Type type : st.types) {
				if(!finishedType(type, context))
					return false;
				size += type.size;
			}
			st.size = size;
			return true;
		} else if(t instanceof RefinementType rt){
			if(!finishedType(rt.inheritType, context))
				return false;
			rt.size = rt.inheritType.size;
			return true;
		} else if(t instanceof Primitive p){
			return true;
		} else if(t instanceof NaiveArrayType a){
			return true;
		} else throw new RuntimeException("Invalid type: " + t.getClass());
	}
	void resolveTypes(Block b, Context context) {
		if(b instanceof Body body) {
			
			int totalFinished = 0;
			int lastFinished;
			do {
				lastFinished = totalFinished;
				totalFinished = 0;
				for(Type t : body.context.types.values()) {
					if(!t.isFinished) {
						boolean finished = finishedType(t, context);
						if(finished) {
							t.isFinished = true;
							totalFinished++;
						}
					} else {
						totalFinished++;
					}
				}
				if(totalFinished == lastFinished && totalFinished < body.context.types.values().size())
					throw new RuntimeException("Infinite loop!");
			} while(totalFinished < body.context.types.values().size());
			
			
			for(FunctionOverload fo : body.context.functions.values()) {
				for(Function f : fo.functions) {
					if(!finishedType(f.functionIdentifier.type, f.body.context)) {
						throw new RuntimeException("Type unfinished!");
					}
					resolveTypes(f.body, f.body.context);					
				}
			}
			for(Type t : body.context.types.values()) {
				if(t instanceof RefinementType rt)
					resolveTypes(rt.customMatch, rt.customMatch.context);
			}
			if(!finishedType(body.context.localValues, body.context))
				throw new RuntimeException("Type unfinished!");
			
			for(int i = 0; i < body.expr.size(); i++) {
				resolveTypes(body.expr.get(i), context);
			}
			
			System.out.println("Local variables for \""+body.context.localValues.name+"\": " + body.context.localValues);
			System.out.println(body.context.localValues.size);
			
		} else if(b instanceof FunctionObjectGenerator fg) {
//			if(fg.functionIdentifier.type == null) {
//				fg.functionIdentifier = context.getFunction(fg.functionIdentifier.name).functionIdentifier;
////				fixType(fg.functionIdentifier.type, context);
//			}
//			System.out.println(fg.functionIdentifier.type.getClass());
		} else if(b instanceof CoreOp o) {
			if(o.s.equals(",")) {
				
				List<Block> c = parseComma(b);
				o.operands.clear();
				o.operands.addAll(c);
				
				for(int i = 0; i < o.operands.size(); i++) {
					resolveTypes(o.operands.get(i), context);
				}
			} else {
				for(int i = 0; i < o.operands.size(); i++) {
					resolveTypes(o.operands.get(i), context);
				}
			}
//			System.out.println(o.s);
			
		} else if(b instanceof CoreFunctionCall fc) {
			resolveTypes(fc.function, context);
			resolveTypes(fc.argument, context);
		} else if(b instanceof Symbol s) {
//			throw new RuntimeException("Not implemented: " + b.getClass());
		} else if(b instanceof TypeObjectGenerator tg){
//			System.out.println(tg.type.getClass());
		} else if(b instanceof CoreIfStatement ifs) {
			resolveTypes(ifs.argument, context);
			resolveTypes(ifs.body, ((Body)ifs.body).context);
			if(ifs.elseBody != null)
				resolveTypes(ifs.elseBody, ((Body)ifs.elseBody).context);
		} else if(b instanceof CoreWhileStatement iws) {
			resolveTypes(iws.argument, context);
			resolveTypes(iws.body, ((Body)iws.body).context);
		} else if(b instanceof CoreBenchmarkStatement iws) {
			resolveTypes(iws.expr, context);
			resolveTypes(iws.body, ((Body)iws.body).context);
		} else if(b instanceof NaiveArrayGenerator ag) {
			for(Block e : ag.blocks) {
				resolveTypes(e, context);
			}
		} else {
			throw new RuntimeException("Not implemented: " + b.getClass());
		}
	}
	
	Type typeChecker(Block b, Context context) {
		if(b instanceof Body body) {
			for(Function f : body.context.allDefinedFunctions) {
				typeChecker(f.body, body.context);
			}
			
			Type ans = null;
			for(Block block : body.expr)
				ans = typeChecker(block, body.context);
			
			return ans;
		} else if(b instanceof CoreOp o) {
			if(o.s.equals("=")) {
				Type force = typeChecker(o.operands.get(0), context);
				Type value = typeChecker(o.operands.get(1), context);

				if(value.staticSubtypeOf(force))
					return force;

				System.out.println("Force " + value + " into " + force + "?");
				if(force.staticSubtypeOf(value)) {
//					System.out.println(force + "=" + value);
					o.operands.set(1, new TypeCast(o.operands.get(1), force));
					return force;
				}
				
				throw new RuntimeException("No match");
			}
			if(o.s.equals("print")) {
				return typeChecker(o.operands.get(0), context);
			}
			if(o.s.equals(",")) {
				ArrayList<Type> args = new ArrayList<>();
				for(int i = 0; i < o.operands.size(); i++) {
					args.addAll(getFlattenedType(typeChecker(o.operands.get(i), context)));
					
				}
				return new StructType(args);
			}

			if(o.s.equals(".") && o.operands.size() == 2 && o.operands.get(1) instanceof NaiveArrayGenerator ag) {
				return context.getType("long");
			}
			ArrayList<Type> args = new ArrayList<>();
			for(int i = 0; i < o.operands.size(); i++) {
				args.addAll(getFlattenedType(typeChecker(o.operands.get(i), context)));
				
			}
			System.out.println(args + o.s);
			return context.overloadedFunction(o.s, args).functionIdentifier.type.rets;
		} else if(b instanceof AliasParse a) {
//			System.out.println("Found: " + a.s + "\t" + context.getVariableType(a.s));
			return context.getVariableType(a.s);
		} else if(b instanceof NumberParse a) {
			return context.getType("long");
		} else if(b instanceof TypeObjectGenerator tog) {
			return tog.type;
		} else if(b instanceof FunctionObjectGenerator fog) {
			return fog.functionIdentifier.type;
		} else if(b instanceof CoreBenchmarkStatement fog) {
			typeChecker(fog.body, context);
			return context.getType("long");
		} else if(b instanceof CoreFunctionCall fc) {
			Type fun = typeChecker(fc.function, context);
			Type args = typeChecker(fc.argument, context);
			if(fc.function instanceof FunctionObjectGenerator fog) {
				FunctionIdentifier fid = context.overloadedFunction(fog.functionIdentifier.name, getFlattenedType(args)).functionIdentifier;
				System.out.println(fid.type.args + " RETURNED FID");
				fog.functionIdentifier = fid;
				return fid.type.rets;
			}
			throw new RuntimeException("Not implemented: " + b.getClass());
		} else if(b instanceof NaiveArrayGenerator ag) {
			int len = 0;
			for(Block e : ag.blocks) {
				len ++;
				typeChecker(e, context).staticSubtypeOf(context.getType("long"));
			}
			return new NaiveArrayType(len);
		} else if(b instanceof CoreWhileStatement cs) {
			typeChecker(cs.argument, context).staticSubtypeOf(context.getType("boolean"));
			typeChecker(cs.body, context).staticSubtypeOf(context.getType("long"));
			return new NaiveArrayType(-1);
		} else if(b instanceof CoreIfStatement cs) {
			typeChecker(cs.argument, context).staticSubtypeOf(context.getType("boolean"));
			Type body = typeChecker(cs.body, context);
			Type elseBody = typeChecker(cs.elseBody, context);
//			if(body.staticSubtypeOf(elseBody))
//				return body;
//			else (elseBody.staticSubtypeOf(body))
//				return elseBody;
//			else throw new RuntimeException("Invalid types")
			return new TypeUnion(body, elseBody);
		} else {
			System.out.println(b);
			throw new RuntimeException("Not implemented: " + b.getClass());
		}
	}
	
	
	class Body extends Block {
		ArrayList<Block> expr = new ArrayList<>();
		Context context;
		Body(Context parent) {
			this.context = new Context(parent);
			System.out.println("I am a created body: " + parent + " -> " + Integer.toHexString(hashCode()));
		}
		
		
		@Override
		public String toString() {
			return this.getClass().getName() + "@" + Integer.toHexString(this.hashCode());
		}
		@Override
		public String toParseString() {
			StringBuilder sb = new StringBuilder();
			sb.append("Context: {\n");
			
			if(!context.localValues.types.isEmpty())
				sb.append(height() + "\t" + context.localValues + "\n\n");
			
			
			for(Block b : expr) {
				sb.append(b.height() + b.toParseString() + "\n");
			}
			
			if(context.functions.size() + context.types.size() > 0) {
				sb.append("\n");
				for(FunctionOverload fo : context.functions.values()) {
					for(Function f : fo.functions) {
						f.body.parent = this;
						sb.append("\n" + f.body.height());
						sb.append(f.functionIdentifier.name + f.functionIdentifier.type.args + " -> " + f.functionIdentifier.type.rets);
						
						sb.append("\n" + f.body.height());
						sb.append(f.body.toParseString());
					}
				}
				for(Type t : context.types.values()) {
					if(t instanceof RefinementType rt) {
						rt.customMatch.parent = this;
						sb.append("\n" + rt.customMatch.height());
						sb.append(rt.name + rt.inheritType + " -> bool");
						
						sb.append("\n" + rt.customMatch.height());
						sb.append(rt.customMatch.toParseString());
					}
				}
			}
				
			sb.append(height() + "}\n");
			return sb.toString();
		}
	}
	
	ArrayList<Type> getFlattenedType(Type t) {
		ArrayList<Type> ts = new ArrayList<>();
		if(t instanceof StructType st) {
			for(Type type : st.types)
				ts.addAll(getFlattenedType(type));
		} else if(t instanceof Primitive p) {
			ts.add(p);
		} else if(t instanceof RefinementType rt) {
			ts.add(rt);
		} else if(t instanceof NaiveArrayType at) {
			ts.add(at);
		} else if(t instanceof TypeUnion tu) {
			ts.add(tu);
		} else throw new RuntimeException("Not implemented: " + t.getClass());
		return ts;
		
		
		// long + long => long
		// long + powerOfTwo => long
		// long = long => long
		// long = powerOfTwo => long
		// powerOfTwo = long => powerOfTwo
	}
	
	class Context {
		Context parent;
		StructType localValues = new StructType(new ArrayList<>(), new ArrayList<>());
		
		ArrayList<Function> allDefinedFunctions = new ArrayList<>();
		Map<String, FunctionOverload> functions = new HashMap<>();
		Map<String, Type> types = new HashMap<>();
		
		public Context(Context parent) {
			this.parent = parent;
		}
		
		boolean isType(String s) {
			return types.containsKey(s) || (parent != null && parent.isType(s));
		}
		Type getType(String s) {
			Type t = types.get(s);
			if(t != null)
				return t;
			if(parent == null)
				throw new RuntimeException("Type does not exist");
			return parent.getType(s);
		}
		Function getFunction(FunctionIdentifier fid) {
			FunctionOverload fo = functions.get(fid.name);
			if(fo != null)
			for(Function f : fo.functions)
				if(f.functionIdentifier == fid)
					return f;
			if(parent == null)
				throw new RuntimeException("Function does not exist: " + fid);
			return parent.getFunction(fid);
		}
		Function overloadedFunction(String name, ArrayList<Type> args) {

			FunctionOverload fo = functions.get(name);
			if(fo != null){
				Function bestFunction = null;
				ArrayList<Type> flattenedBest = null;
				nextFn: for(Function f : fo.functions) {
//					if(args.equals(getFlattenedType(f.functionIdentifier.type.args)))
//						return f;
					System.out.println(f.functionIdentifier.name + f.functionIdentifier.type.args);
					ArrayList<Type> flattened = getFlattenedType(f.functionIdentifier.type.args);

					if (flattened.size() != args.size()) {
//						System.out.println("Nope! " + args + " is not a subtype of " + flattened);
						continue;
					}
					System.out.println(f + "\t" + args + "???");
					for (int i = 0; i < args.size(); i++) {
						if (!args.get(i).staticSubtypeOf(flattened.get(i))) {
//							System.out.println("Nope! " + args.get(i) + " is not a subtype of " + flattened.get(i));
							continue nextFn;
						} else if(bestFunction != null && !flattened.get(i).staticSubtypeOf(flattenedBest.get(i))) {
							continue nextFn;
						}
					}
					System.out.println(f + "\t" + args + "!!!");
					bestFunction = f;
					flattenedBest = flattened;
				}
				System.out.println(bestFunction + "\twas best");
				return bestFunction;
			}
			if(parent == null)
				throw new RuntimeException("Function does not exist: \"" + name + "\" " + args);
			return parent.overloadedFunction(name, args);
		}
		
		void addFunction(Function f) {
			FunctionOverload fo = functions.get(f.functionIdentifier.name);
			if(fo == null) {
				functions.put(f.functionIdentifier.name, fo = new FunctionOverload());
			}
			fo.functions.add(f);
			allDefinedFunctions.add(f);
		}
		Type getVariableType(String s) {
//			FunctionOverload fo = functions.get(name);
			for(int i = 0; i < localValues.vars.size(); i++) {
				if(s.equals(localValues.vars.get(i)))
					return localValues.types.get(i);
				
			}
			if(parent == null)
				throw new RuntimeException("Variable does not exist: " + s);
			return parent.getVariableType(s);
		}
	}
	

	class Type {
		boolean isFinished;
		int size;
		String name;
		Type() {}
		Type(String name, int size) {
			this.size = size;
			this.name = name;
		}
		
		boolean staticSubtypeOf(Type t) {
			return getFlattenedType(this).equals(getFlattenedType(t));
		}
	}
	class Primitive extends Type {
		public Primitive(String name, int size) {
			super(name, size);
			isFinished = true;
		}
		@Override
		public String toString() {
			return "$"+name;
		}
	}
	class StructType extends Type {
		ArrayList<Type> types;
		ArrayList<String> vars;
		public StructType(String name) {
			this.name = name;
			this.isFinished = false;
		}
		public StructType(ArrayList<Type> types) {
			this.types = types;
		}
		public StructType(ArrayList<Type> types, ArrayList<String> vars) {
			this.types = types;
			this.vars = vars;
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			System.out.println("I am " + name);
			if(vars == null) {
				sb.append('(');
				for(Type t : types) {
					sb.append(t);
					sb.append(", ");
				}
				if(sb.length() > 1) {
					sb.setLength(sb.length()-2);
				}
				sb.append(')');
				return sb.toString();
			} else {
				sb.append('(');
				for(int i = 0; i < types.size(); i++) {
					sb.append(types.get(i));
					sb.append(" ");
					sb.append(vars.get(i));
					sb.append(", ");
				}
				if(sb.length() > 1) {
					sb.setLength(sb.length()-2);
				}
				sb.append(')');
				return sb.toString();
			}
			
		}
	
		@Override
		boolean staticSubtypeOf(Type t) {
			if(t == this) {
				return true;
			}
			
			return getFlattenedType(this).equals(getFlattenedType(t));
		}
	}
	
	class FunctionType extends Type {
		StructType args;
		StructType rets;
		FunctionType(StructType args, StructType rets) {
			this.args = args;
			this.rets = rets;
			this.isFinished = args.isFinished && rets.isFinished;
		}
	}
	class RefinementType extends Type {
		Type inheritType;
		Body customMatch;
		public RefinementType(Type inheritType, String name) {
			this.inheritType = inheritType;
			this.name = name;
		}
		
		@Override
		public String toString() {
			return "#" + name;
		}
		
		@Override
		boolean staticSubtypeOf(Type t) {
//			System.out.println(this + " into " +  t + "  ~ " + inheritType);
			ArrayList<Type> t1 = getFlattenedType(this);
			ArrayList<Type> t3 = getFlattenedType(inheritType);
			ArrayList<Type> t2 = getFlattenedType(t);
			for(int i = 0; i < t1.size(); i++){
				if(!t1.get(i).equals(t2.get(i)) && !t3.get(i).staticSubtypeOf(t2.get(i)))

					return false;
			}
			return true;
		}
	}
	class NaiveArrayType extends Type {
		long length;

		public NaiveArrayType(long length) {
			this.length = length;
			size = 64;
		}

		@Override
		boolean staticSubtypeOf(Type t) {
			return t instanceof NaiveArrayType at && (at.length == length || at.length == -1);
		}

		@Override
		public String toString() {
			return "long[" + (length == -1 ? "" : length+"") + "]";
		}
	}
	class TypeUnion extends Type {
		ArrayList<Type> ts;
		TypeUnion(Type... ts) {
			this.ts = new ArrayList<>(Arrays.asList(ts));
		}

		@Override
		public String toString() {
			return "<"+ ts + ">";
		}
	}

	
	class FunctionOverload {
		ArrayList<Function> functions = new ArrayList<>();
	}
	class TypeInitializer extends Block {
		Type type;
		Body body;
		
		TypeInitializer(Type type, Body body) {
			this.type = type;
			this.body = body;
		}

		@Override
		public String toString() {
			// TODO Auto-generated method stub
			return this.getClass().getName() + "@" + Integer.toHexString(this.hashCode());
		}

		@Override
		public String toParseString() {
			// TODO Auto-generated method stub
			return null;
		}
		
	}
	
	
	class Function extends Block {
		FunctionIdentifier functionIdentifier;
		Body body;
		
		
		Function(FunctionIdentifier functionIdentifier, Body body) {
			this.functionIdentifier = functionIdentifier;
			this.body = body;
		}

		@Override
		public String toString() {
			return this.getClass().getName() + "@" + Integer.toHexString(this.hashCode());
			// TODO Auto-generated method stub
//			return null;
		}

		@Override
		public String toParseString() {
			// TODO Auto-generated method stub
			return null;
		}
		
	}
	class BuiltinFunction extends Function {
		
		
		BuiltinFunction(FunctionIdentifier functionIdentifier) {
			super(functionIdentifier, null);
		}

		@Override
		public String toString() {
			return this.getClass().getName() + "@" + Integer.toHexString(this.hashCode());
			// TODO Auto-generated method stub
//			return null;
		}

		@Override
		public String toParseString() {
			// TODO Auto-generated method stub
			throw new RuntimeException("Not implemented");
		}
		
	}
	
	
	
	
	class FunctionIdentifier {
		FunctionType type;
		String name;
		FunctionIdentifier(String name, FunctionType type) {
			this.name = name;
			this.type = type;
		}
	}
	
	
	class LiteralGenerator extends Expression {
		BigInteger value;

		@Override
		public String toString() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String toParseString() {
			// TODO Auto-generated method stub
			return null;
		}
	}
	class StructGenerator extends Expression {

		@Override
		public String toString() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String toParseString() {
			// TODO Auto-generated method stub
			return null;
		}
		
	}
	class FunctionObjectGenerator extends Expression {
		FunctionIdentifier functionIdentifier;
		public FunctionObjectGenerator(FunctionIdentifier functionIdentifier) {
			this.functionIdentifier = functionIdentifier;
		}

		@Override
		public String toString() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String toParseString() {
			return "[fn "+functionIdentifier.name + "]";
		}
		
	}
	class TypeObjectGenerator extends Expression {
		Type type;
		public TypeObjectGenerator(Type type) {
			this.type = type;
		}

		@Override
		public String toString() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String toParseString() {
			
			return "[type "+type.name + "]";
		}
		
	}

	class NaiveArrayGenerator extends Expression {
		ArrayList<Block> blocks;

		public NaiveArrayGenerator(ArrayList<Block> blocks) {
			this.blocks = blocks;
		}

		@Override
		public String toString() {
			return blocks.toString();
		}

		@Override
		public String toParseString() {
			return blocks.toString();
		}
	}

	class TypeCast extends Expression {
		Block value;
		Type type;
		
		public TypeCast(Block value, Type type) {
			this.value = value;
			this.type = type;
		}

		@Override
		public String toString() {
			// TODO Auto-generated method stub
			return getClass() + ":" + Integer.toHexString(hashCode());
		}

		@Override
		public String toParseString() {
			// TODO Auto-generated method stub
			return "(" + value.toParseString() + " as " + type.toString() + ")";
		}
		
	}
	
}
