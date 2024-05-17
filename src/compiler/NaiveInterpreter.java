package compiler;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;

import compiler.Lexer.AliasParse;
import compiler.Lexer.Block;
import compiler.Lexer.CurlyBracketParse;
import compiler.Lexer.Keyword;
import compiler.Lexer.SquareBracketParse;
import compiler.NaiveParser.CoreForStatement;
import compiler.NaiveParser.CoreFunctionCall;
import compiler.NaiveParser.CoreIfStatement;
import compiler.NaiveParser.CoreOp;
import compiler.NaiveParser.CoreWhileStatement;
import compiler.NaivePolisher.AccessStruct;
import compiler.NaivePolisher.AccessValue;
import compiler.NaivePolisher.BoolValue;
import compiler.NaivePolisher.BuiltInType;
import compiler.NaivePolisher.Function;
import compiler.NaivePolisher.FunctionType;
import compiler.NaivePolisher.IntValue;
import compiler.NaivePolisher.LiteralValue;
import compiler.NaivePolisher.LocalVariable;
import compiler.NaivePolisher.LongValue;
import compiler.NaivePolisher.Scope;
import compiler.NaivePolisher.Structure;
import compiler.NaivePolisher.StructureType;
import compiler.NaivePolisher.Type;
import compiler.NaivePolisher.Value;
import compiler.NaivePolisher.Variable;

public class NaiveInterpreter {

//	static File inputFile = new File("src/fibonacci.hex");
//	static File inputFile = new File("src/sqrt.hex");
//	static File inputFile = new File("src/primes.hex");
//	static File inputFile = new File("src/factorial.hex");
	static File inputFile = new File("src/code12.hex");
//	static File inputFile = new File("src/testbench/if-else-statement.hex");
	
	public static void main(String[] args) throws IOException {
		String fileContent = new String(Files.readAllBytes(inputFile.toPath())) + " ";
		
		Lexer lexer = new Lexer();
		NaiveParser parser = new NaiveParser();
		NaivePolisher polisher = new NaivePolisher();
		NaiveInterpreter interpreter = new NaiveInterpreter();
		
		System.out.println("Input:\n" + fileContent + "\n");
		CurlyBracketParse b = lexer.parse(fileContent);
		System.out.println("Lexed:\n" + b.toParseString()+"\n");
		b = (CurlyBracketParse) parser.parse(b);
		
		System.out.println("Parsed:\n" + b.toParseString()+"\nPolished:");
		Scope s = polisher.polish(b, NaivePolisher.builtInScope);
		System.out.println("\n" + s.toParseString()+"\n");
//		System.out.println(s);
		interpreter.stack.allocFrame();
		interpreter.stack.alloc(s.allocateSize);
		interpreter.interp(s);
		interpreter.stack.dealloc(s.allocateSize);
//		System.out.println("Done");
		System.out.println("\nConsole output:");
		System.out.println(interpreter.console);
	}
	Stack stack = new Stack();
	VirtualMemoryTiny mem = new VirtualMemoryTiny();
	ArrayList<String> console = new ArrayList<>();
	
	Value interp(Scope s){
//		System.out.println("New scope with variables:" + s.variables );
		Value ans = null;
		for(Block b : s.blocks) {
//			System.out.println("Executing "+b.toParseString()+" stack: " + stack.stack);
//			System.out.println("Executing stack: " + stack);
			

//			System.out.println("\n:::\t" + b.toParseString());
			s.ans = ans = getValue(b, s);
//			System.out.println("ans: " + ans);
			if(s.ret != null)
				return s.ret;
//			System.out.println("a");
		}
		return ans;
	}
	
	
	Value getValue(Block b, Scope s) {
		return getValue(b, s, null);
	}
	Value getValue(Block b, Scope s, Type forceCast) {
//		System.out.println("---"+b.getClass());
//		System.out.println(stack);
//		System.out.println("--:"+s.variables());
		if(b instanceof CoreOp) {
			ArrayList<Block> l = ((CoreOp)b).operands;
//			System.out.println(s + "\t" + l);
			
			
			switch (((CoreOp)b).s){
			case "=":{
//				System.out.println("Set " + (Variable)l.get(0) + "\t to " + getValue(l.get(1), s));
				
//				System.out.println("Set " + getValue((Variable)l.get(0), s));
				Value var = getValue(l.get(0), s, BuiltInType.VARIABLE.type);
//				System.out.println(var.type);
//				System.out.println(var);
				Value val = getValue(l.get(1), s);
//				System.out.println("Set " + var + " = " + val);
//				System.out.println("Set " + var.getClass() + " = " + val.getClass());
				
				return stack.set(var, val);
			}
			case "print": {
				Value val = getValue(l.get(0), s);
				System.out.println("> " + val);
				console.add(""+val);
				return val;
			}
			case "return": {
				s.ret = getValue(l.get(0), s);
				return null;
			}
			case ".": {
				if(l.size() != 2)
					throw new RuntimeException("Expected 2 arguments, got: " + l.size() + " in\t" +  b.toParseString());
				Value struc = getValue(l.get(0), s);
				if(l.get(1) instanceof SquareBracketParse) {
					SquareBracketParse p = (SquareBracketParse) l.get(1);
					Value index = getValue(p.expressions.get(0), s);
					if(struc instanceof Structure)
						return ((Structure) struc).values.get((int) builtInTypeToLong(index));
				} else if(l.get(1) instanceof AliasParse) {
					if(struc.type instanceof StructureType) {
//						System.out.println("type: " + struc.type);
						int i = 0;
						for(Variable v : ((StructureType)struc.type).variables) {
							if(v.s != null && v.s.contentEquals(((AliasParse)l.get(1)).s)) {
								if(forceCast == BuiltInType.VARIABLE.type)
									return v;
								else 
									return ((Structure) struc).values.get(i);
							}
							i++;
						}
						throw new RuntimeException("Invalid field: " + ((AliasParse)l.get(1)).s + " not found in\t" +  b.toParseString());
					}
					else 
						throw new RuntimeException("Not supported yet " + l + " in\t" +  b.toParseString());
				} else
					throw new RuntimeException("Not supported yet " + l + " in\t" +  b.toParseString());
			}
			default: 
				if(l.size() == 1) {
					Value val;
					if(((CoreOp)b).s.contentEquals("++_<")) {
						val = getValue(l.get(0), s, BuiltInType.VARIABLE.type);
					}else {
						val = getValue(l.get(0), s);
					}
					return arithUnOpBuiltInType(val, ((CoreOp)b).s);
				}
					
				else
					return arithBinOpBuiltInType(getValue(l.get(0), s), getValue(l.get(1), s), ((CoreOp)b).s);
				
			}
		} else if(b instanceof CoreFunctionCall) {
			Value funVal = getValue(((CoreFunctionCall) b).function, s, BuiltInType.FUNCTION.type);;
			Value arg = getValue(((CoreFunctionCall) b).argument, s);
//			System.out.println(stack.stack)
			if(!(funVal instanceof Function))
				throw new RuntimeException("Not a function: " + funVal.getClass() + "\t" + funVal);
			Function f = (Function) funVal;
			
			ArrayList<LocalVariable> parameters = varUnpack(((FunctionType)f.type).parameters, new ArrayList<>());
			ArrayList<Value> values = unpack(arg, new ArrayList<>(parameters.size()));
			if(parameters.size() != values.size())
				throw new RuntimeException("Incorrect arity: Expected " + parameters.size() + "params, got " + values.size() + ". "  + parameters + "\t" + values);
			
			stack.allocFrame();
			stack.alloc(((Scope)f.body).allocateSize);
			for(int i = 0; i < parameters.size(); i++)
				stack.set(parameters.get(i), values.get(i));
			((Scope)f.body).ans = values.size() == 1 ? values.get(0) : new Structure(values);
			Value ret = interp((Scope)f.body);
			if(((Scope)f.body).ret != null) {
				ret = ((Scope)f.body).ret;
				((Scope)f.body).ret = null;
			}
			stack.dealloc(((Scope)f.body).allocateSize);
			stack.deallocFrame();
			return ret;
			
		} else if (b instanceof CoreIfStatement) {
			CoreIfStatement p = (CoreIfStatement) b;
			if(truthy(getValue(p.argument, s))) {
				if(p.body instanceof Scope) {
					stack.alloc(((Scope) p.body).allocateSize);
					((Scope)p.body).ans = s.ans;
					Value ret = interp((Scope) p.body);
					stack.dealloc(((Scope) p.body).allocateSize);
					if(((Scope) p.body).ret != null) {
						s.ret = ((Scope) p.body).ret;
						return null;
					}
					return ret;
				} else
					throw new RuntimeException("Not a scope: " + p.body);
			} else {
				if(p.elseBody instanceof Scope) {
					stack.alloc(((Scope) p.elseBody).allocateSize);
					((Scope)p.elseBody).ans = s.ans;
					Value ret = interp((Scope) p.elseBody);
					stack.dealloc(((Scope) p.elseBody).allocateSize);
					if(((Scope) p.body).ret != null) {
						s.ret = ((Scope) p.body).ret;
						return null;
					}
					return ret;
				} else
					throw new RuntimeException("Not a scope: " + p.elseBody);
			}
		} else if(b instanceof CoreWhileStatement) {
			CoreWhileStatement p = (CoreWhileStatement) b;
//			int aaa = 0;
			while(truthy(getValue(p.argument, s))) {
//				if(aaa++ == 20)
//					break;
				
				if(p.body instanceof Scope) {
					stack.alloc(((Scope) p.body).allocateSize);
					Value ret = interp((Scope) p.body);
					stack.dealloc(((Scope) p.body).allocateSize);
					if(((Scope) p.body).ret != null) {
						s.ret = ((Scope) p.body).ret;
						return null;
					}
				} else
					throw new RuntimeException("Not a scope: " + p.body);
				
			}
//				
			return null;
		} else if(b instanceof CoreForStatement) {
			CoreForStatement p = (CoreForStatement) b;
//			int aaa = 0;
			ArrayList<Value> returnValues = new ArrayList<>();
			System.out.println("arggss:: " + p.argument);
			Value iterable = getValue(p.argument, s);
			if(iterable instanceof Structure) {
				Structure struc = (Structure) iterable;
				for(int i = 0; i < struc.values.size(); i++) {
//					if(aaa++ == 20)
//						break;
//					System.out.println(struc.values.get(i));
					if(p.body instanceof Scope) {
						stack.alloc(((Scope) p.body).allocateSize);
						((Scope) p.body).ans = struc.values.get(i);
						if(p.each != null) {
							if(p.each instanceof AccessStruct) {
								System.out.println("TESTTTTT: " + struc);
								ArrayList<Value> vals = unpack(struc.values.get(i), new ArrayList<>());
								System.out.println("TESTTTTT: " + vals);
								int i2 = 0;
								for(Block bvar : ((AccessStruct)p.each).expressions) {
									
									System.out.println(bvar);
									System.out.println(vals);
									if(bvar instanceof AccessValue && ((AccessValue)bvar).value instanceof LocalVariable) {
										LocalVariable var = (LocalVariable)((AccessValue)bvar).value;
										stack.set(var, vals.get(i2++));
									}
								}
							} else if (p.each instanceof AccessValue && ((AccessValue)p.each).value instanceof LocalVariable){
								if(struc.values.get(i).type instanceof StructureType) {
									
									ArrayList<Value> vals = unpack(struc.values.get(i), new ArrayList<>());
									ArrayList<Value> vars = new ArrayList<>();
									System.out.println(vals);
									
									int totalOffset = ((LocalVariable)((AccessValue)p.each).value).stackOffset;
									for(Value val : vals) {
										System.out.println(val.type + "\t" + val.type.size);
										LocalVariable var = new LocalVariable(val.type, totalOffset, val.s);
										vars.add(var);
										stack.set(var, val);
										totalOffset += val.type.size;
									}
									
								} else {
									LocalVariable var = (LocalVariable)((AccessValue)p.each).value;
									stack.set(var, struc.values.get(i));
								}

							}
						}
						Value ret = interp((Scope) p.body);
						System.out.println(">>>ANS: " + ((Scope) p.body).ans);
						returnValues.add(((Scope) p.body).ans);
						stack.dealloc(((Scope) p.body).allocateSize);
						if(((Scope) p.body).ret != null) {
							s.ret = ((Scope) p.body).ret;
							return null;
						}
					} else
						throw new RuntimeException("Not a scope: " + p.body);
					
				}
			}
			
			System.out.println("aaaa: " + new Structure(returnValues));
			return new Structure(returnValues);
		} else if(b instanceof AccessValue) {
			if(((AccessValue) b).value instanceof Variable) {
				if(forceCast == BuiltInType.VARIABLE.type)
					return ((AccessValue) b).value;
				return stack.get((LocalVariable)((AccessValue) b).value);
			}
			return ((AccessValue)b).value;
		} else if(b instanceof AccessStruct) {
			System.out.println(b);
			ArrayList<Value> values = new ArrayList<>();
			for(Block p: ((AccessStruct) b).expressions)
				values.add(getValue(p, s, forceCast));
			System.out.println("Expresss: " + values);
			return new Structure(values);
		} else if(b instanceof Keyword) {
			if(((Keyword) b).s.contentEquals("ans")) {
				return s.ans;
			}
			throw new RuntimeException("Invalid use of keyword: \"" + ((Keyword) b).s + "\"");
		} else if(b instanceof SquareBracketParse) {
			throw new RuntimeException("Not implemented: " + b.getClass() + "\t" + b);
		}
		throw new RuntimeException("Not implemented: " + b.getClass() + "\t" + b);
		
//		return 0;
	}

	Value arithBinOpBuiltInType(Value v1, Value v2, String op) {
		switch(op) {
		case "+":
			return longToBuildInType(builtInTypeToLong(v1) + builtInTypeToLong(v2), v1.type);
		case "-":
			return longToBuildInType(builtInTypeToLong(v1) - builtInTypeToLong(v2), v1.type);
		case "*":
			return longToBuildInType(builtInTypeToLong(v1) * builtInTypeToLong(v2), v1.type);
		case "/":
			return longToBuildInType(builtInTypeToLong(v1) / builtInTypeToLong(v2), v1.type);
		case "%":
			return longToBuildInType(builtInTypeToLong(v1) % builtInTypeToLong(v2), v1.type);
		case "==":
			return new BoolValue(builtInTypeToLong(v1) == builtInTypeToLong(v2));
		case "<":
			return new BoolValue(builtInTypeToLong(v1) < builtInTypeToLong(v2));
		case ">":
			return new BoolValue(builtInTypeToLong(v1) > builtInTypeToLong(v2));
		case "!=":
			return new BoolValue(builtInTypeToLong(v1) != builtInTypeToLong(v2));
		case ">=":
			return new BoolValue(builtInTypeToLong(v1) >= builtInTypeToLong(v2));
		case "<=":
			return new BoolValue(builtInTypeToLong(v1) <= builtInTypeToLong(v2));
		}
		throw new RuntimeException("Operation not defined: " + op);
	}
	Value arithUnOpBuiltInType(Value v, String op) {
		switch(op) {
		case "+":
			return v;
		case "++_<":
			Value val = stack.get((LocalVariable)v);
			stack.set(v, longToBuildInType(builtInTypeToLong(val)+1, v.type));
			return val;
		case "-":
			return longToBuildInType(-builtInTypeToLong(v), v.type);
		}
		throw new RuntimeException("Operation not defined: " + op);
	}
	
	long builtInTypeToLong(Value value) {
		Type t = value.type;
		
		if(t == BuiltInType.INT.type)
			return ((IntValue) value).value;
		if(t == BuiltInType.LONG.type)
			return ((LongValue) value).value;
		if(t == BuiltInType.BOOL.type)
			return ((BoolValue) value).value?1:0;
		if(t == BuiltInType.LITERAL.type)
			return ((LiteralValue) value).value.longValue();
		throw new RuntimeException("Unknown type: " + value.getClass() + "\t" + value.type);
	}
	Value longToBuildInType(long value, Type type) {
		if(type == BuiltInType.INT.type)
			return new IntValue((int) value);
		if(type == BuiltInType.LONG.type)
			return new LongValue(value);
		if(type == BuiltInType.BOOL.type)
			return new BoolValue(value != 0);
		if(type == BuiltInType.LITERAL.type)
			return new LiteralValue(BigInteger.valueOf(value));
		throw new RuntimeException("Unknown type: " + type);
	}
	boolean truthy(Value value) {
		if(value.type == BuiltInType.BOOL.type)
			return ((BoolValue)value).value; 
		if(value.type == BuiltInType.INT.type)
			return ((IntValue)value).value != 0;
		throw new RuntimeException("No truthy value for type: " + value.type);
	}
	
	static ArrayList<LocalVariable> varUnpack(Value value, ArrayList<LocalVariable> vars) {
//		System.out.println("varunpack\t" + value.getClass());
		if(value instanceof Structure) {
			for(Value v : ((Structure) value).values)
				varUnpack(v, vars);
			return vars;
		} else if(value instanceof StructureType) {
			for(Value v : ((StructureType) value).variables)
				varUnpack(v, vars);
			return vars;
		} else if (value instanceof LocalVariable){
//			System.out.println("varunpack!\t" + value.getClass());
			if(value.type instanceof StructureType) {
				int offs = ((LocalVariable) value).stackOffset;
				for(LocalVariable v : ((StructureType) value.type).variables) {
					LocalVariable v2 = new LocalVariable(v.type, v.stackOffset + offs, v.s);
					varUnpack(v2, vars); 
				}
				return vars;
			}
			vars.add((LocalVariable) value);
			return vars;
		}
		throw new RuntimeException("Not an unpackable type\t" + value.getClass() + "\t" + value);
	}
	static ArrayList<Value> unpack(Value value, ArrayList<Value> vals) {
//		System.out.println("unpack\t" + value.getClass());
		if(value instanceof Structure) {
			for(Value v : ((Structure) value).values)
				unpack(v, vals);
			return vals;
		} else {
			vals.add(value);
			return vals;
		}
	}
	static ArrayList<Value> unpack_shallow(Value value, ArrayList<Value> vals) {
//		System.out.println("unpack\t" + value.getClass());
		if(value instanceof Structure) {
			vals.add(value);
//			for(Value v : ((Structure) value).values)
//				unpack(v, vals);
			return vals;
		} else {
			vals.add(value);
			return vals;
		}
	}
	
	public class Stack {
		int bp = VirtualMemoryTiny.STACK;
		int sp = VirtualMemoryTiny.STACK;
		void allocFrame() {
			sp += 32;
			if(mem.objectPool[sp/VirtualMemoryTiny.PAGE_SIZE] == null)
				mem.objectPool[sp/VirtualMemoryTiny.PAGE_SIZE] = new long[1024];
			mem.set(sp-32, 32, bp);
			bp = sp;
		}
		void deallocFrame() {
			sp = bp - 32;
			bp = (int) mem.get(sp, 32);
		}
		void alloc(int amount) {
			sp += amount;
			if(mem.objectPool[sp/VirtualMemoryTiny.PAGE_SIZE] == null)
				mem.objectPool[sp/VirtualMemoryTiny.PAGE_SIZE] = new long[1024];
		}
		void dealloc(int amount) {
			sp -= amount;
		}
		
		Value set(Value var, Value value) {
			if(var.type instanceof StructureType) {
				ArrayList<LocalVariable> variables = varUnpack(var, new ArrayList<>());
				ArrayList<Value> values = unpack(value, new ArrayList<>());
				if(variables.size() != values.size())
					throw new RuntimeException("Incorrect arity");
				int i = 0;
				for(LocalVariable v: variables)
					set(v, values.get(i++));
				return value;
			}
			LocalVariable variable = (LocalVariable) var;
			Type t = variable.type;
			long l;
			if(value.type == BuiltInType.INT.type) {
				l = ((IntValue) value).value;
			} else if(value.type == BuiltInType.LONG.type) {
				l = ((LongValue) value).value;
			} else if(value.type == BuiltInType.LITERAL.type) {
				l = ((LiteralValue) value).value.longValue();
			} else if(value.type == BuiltInType.BOOL.type) {
				l = ((BoolValue) value).value?1:0;
			} else throw new RuntimeException("Unknown type: " + value.type);
			
			mem.set(bp+variable.stackOffset, t.size, l);
			return value;
		}
		Value get(LocalVariable variable) {
			Type t = variable.type;
			
			if(t instanceof StructureType) {
				ArrayList<Value> values = new ArrayList<>();
				for(Variable v: ((StructureType) t).variables) {
					Value val = get(new LocalVariable(((LocalVariable) v).type, ((LocalVariable) v).stackOffset + variable.stackOffset, ((LocalVariable) v).s));
					val.s = v.s;
					values.add(val);
				}
				return new Structure(values);
			}
			long l = mem.get(bp+variable.stackOffset, t.size);
			
			if(t == BuiltInType.INT.type)
				return new IntValue((int) l);
			else if(t == BuiltInType.LONG.type)
				return new LongValue(l);
			else if(t == BuiltInType.BOOL.type)
				return new BoolValue(l != 0);
			throw new RuntimeException("Unknown type: " + t);
		}
		@Override
		public String toString() {
			return "stack\tbp" + Long.toHexString(bp) + "\tsp" + Long.toHexString(sp) + "\t" + mem.objectPool[sp/VirtualMemoryTiny.PAGE_SIZE];
		}
		
		String printStack() {
			ArrayList<String> print = new ArrayList<>();
			if(Math.abs(bp - sp) > 1000)
				throw new RuntimeException("aaaaaa");
			for(int a = bp; a < sp; a+=32) {
				int v = (int) mem.get(a, 32);
				if(v > 10000)
					print.add(Long.toHexString(v));
				else
					print.add(Long.toString(v));
			}
			return "stack" + print;
		}
	}
	
	interface VirtualMemory {
		void setL(long addr, int size, long[] data);
		long[] getL(long addr, int size);
		void set(long addr, int size, long data);
		long get(long addr, int size);
		long alloc(int size);
		void free(long addr, int size);
	}
	static class VirtualMemoryTiny implements VirtualMemory {
		long[][] objectPool = new long[65536][];
		
		static final int PAGE_COUNT = 0x1_0000;
		static final int PAGE_SIZE = 0x1_0000;
		
		static final int HEAP =  0x1000_0000;
		static final int STACK = 0x2000_0000;
		

		@Override
		public void set(long addr, int size, long data) {
			final int pageAddr = (int) (addr >>> 16);
			final int subAddr = (int) (addr & 0xffff);
			final int longsAddr = subAddr/64;
			final int bitsAddr = subAddr%64;
			final long[] page = objectPool[pageAddr];
			if (subAddr > page.length * 64 || subAddr + size > page.length * 64)
				throw new RuntimeException("Index out of bounds");
			
			if (bitsAddr == 0)
				if(size == 64)
					page[longsAddr] = data;
				else {
					long moveMask = (1l << size) -1;
					page[longsAddr] = (page[longsAddr] & ~moveMask) | (data & moveMask);
				}
			
			if (longsAddr == (subAddr + size - 1)/64) {
				final long moveMask = ((1l << size)-1) << bitsAddr;
				page[longsAddr] = (page[longsAddr] & ~moveMask) | ((data << bitsAddr) & moveMask);
				return;
			}
			
			final int dsc = 64 - bitsAddr;
			final long moveMask = (-1l) << bitsAddr;
			page[longsAddr] = (page[longsAddr] & ~moveMask) | ((data << bitsAddr ) & moveMask);
			final long moveMask2 = (-1l) << (size + subAddr) % 64;
			page[longsAddr+1] = (page[longsAddr+1] & moveMask2) | ((data >>> dsc) & ~moveMask2);
		}
		@Override
		public long get(long addr, int size) {
			final int pageAddr = (int) (addr >>> 16);
			final int subAddr = (int) (addr & 0xffff);
			final int longsAddr = subAddr/64;
			final int bitsAddr = subAddr%64;
			final long[] page = objectPool[pageAddr];
			if(subAddr > page.length * 64 || subAddr + size > page.length * 64)
				throw new RuntimeException("Index out of bounds");
			
			if(bitsAddr == 0)
				return page[longsAddr] & ((-1l) >>> (64-size));
			return ((page[longsAddr] >>> bitsAddr) | page[longsAddr+1] << (64-bitsAddr)) & ((-1l) >>> (64-size));
		}
		@Override
		public void setL(long addr, int size, long[] data) {
			final int pageAddr = (int) (addr >>> 16);
			final int subAddr = (int) (addr & 0xffff);
			final long[] page = objectPool[pageAddr];
			if(subAddr > page.length * 64 || subAddr + size > page.length * 64)
				throw new RuntimeException("Index out of bounds");
			
			
			if(subAddr % 64 == 0) {
				final int last = (subAddr + size)/64;
				int from = 0, to = subAddr / 64;
				while (to < last)
					page[to++] = data[from++];
				if (size%64 != 0) {
					final long moveMask = (-1l) >>> 64 - size % 64;
					page[to] = (page[to] & ~moveMask) | (data[from] & moveMask);
				}
				return;
			}
//			System.out.println(Arrays.toString(page));
			if(subAddr/64 == (subAddr + size)/64) {
				final long moveMask = ((1l << size)-1) << subAddr % 64;
				page[subAddr / 64] = (page[subAddr / 64] & ~moveMask) | ((data[0] << subAddr % 64) & moveMask);
				return;
			}
//			System.out.println(Arrays.toString(page));
			final int last = (subAddr + size)/64;
			int from = 0, to = subAddr / 64;
			
			final int ds = subAddr % 64;
			final int dsc = 64 - ds;
			{
				final long moveMask = (-1l) << ds; 
				page[to] = (page[to] & ~moveMask) | ((data[from] << ds ) & moveMask);
			}
//			System.out.println(Arrays.toString(page));
			to++;
			while(to < last) {
				page[to++] = data[from++] >>> dsc | data[from] << ds;
			}
//			System.out.println(Arrays.toString(page));
			if ((subAddr + size)%64 != 0) {
//				System.out.println("aaa\t" + to + "\t" + from);
//				System.out.println("---\t" + (data[from] >>> dsc));
				final long moveMask = (-1l) << (size + subAddr) % 64;
//				System.out.println(Long.toHexString(~moveMask));
				page[to] = (page[to] & moveMask) | ((data[from] >>> dsc) & ~moveMask);
			}
//			System.out.println(Arrays.toString(page));
		}
		@Override
		public long[] getL(long addr, int size) {
			
			final int pageAddr = (int) (addr >>> 16);
			final int subAddr = (int) (addr & 0xffff);
			final long[] page = objectPool[pageAddr];
			if(subAddr > page.length * 64 || subAddr + size > page.length * 64)
				throw new RuntimeException("Index out of bounds");
			if(subAddr % 64 == 0) {
				final int offs = subAddr / 64;
				
				long[] cPage = Arrays.copyOfRange(page, offs, (size + 0x3f)/64 + offs);
				if(size % 64 == 0) {
					return cPage;
				}
				cPage[cPage.length - 1] &= (-1l) >>> 64 - size % 64;
				return cPage;
			} else {
				long[] cPage = new long[(size + 0x3f)/64];
				int from = subAddr / 64, to = 0;
//				System.out.println(Arrays.toString(cPage));
				if(subAddr/64 == (subAddr + size)/64) {
					cPage[0] = (page[subAddr/64] >>> (subAddr % 64)) & ((1l << size) - 1);
					return cPage;
				}
//				System.out.println(Arrays.toString(cPage));
				final int ds = subAddr % 64;
				final int dsc = 64 - ds;
				while(to < cPage.length) {
//					System.out.println(Long.toHexString(page[from+0] >>> ds));
//					System.out.println(Long.toHexString(page[from+1]<< dsc));
					if(from < page.length-1)
						cPage[to++] = page[from++] >>> ds | page[from] << dsc;
					else
						cPage[to++] = page[from++] >>> ds;
				}
//				System.out.println(Arrays.toString(cPage));
				if(size % 64 == 0)
					return cPage;
//				System.out.println(Long.toHexString((-1l) >>> 64 - (size) % 64));
				cPage[cPage.length - 1] &= (-1l) >>> 64 - size % 64;
//				System.out.println(Arrays.toString(cPage));
				return cPage;
			}
		}
		@Override
		public long alloc(int size) {
			if(size % 64 != 0)
				throw new RuntimeException("Allocations must be 64-bit aligned");
			for(int i = 0; i < PAGE_COUNT; i++) {
				if(objectPool[i] == null) {
					objectPool[i] = new long[size/64];
					return i * PAGE_SIZE;
				}
			}
			throw new RuntimeException("Fatal: Out of Memory");
		}
		@Override
		public void free(long addr, int size) {
			if(size % 64 != 0)
				throw new RuntimeException("Frees must be 64-bit aligned");
			final int pageAddr = (int) (addr >>> 16);
			final int subAddr = (int) (addr & 0xffff);
			if(subAddr != 0)
				throw new RuntimeException("Invalid free location");
			objectPool[pageAddr] = null;
		}
		
		
	}
//	
//	public static void main(String[] args) {
//		VirtualMemoryTiny vm = new VirtualMemoryTiny();
//		long addr = vm.alloc(64);
//		vm.set(addr, 64, new long[] {0x0123456789abcdefl});
//		System.out.println(vm.get(addr, 64)[0]);
//
//		long addr2 = vm.alloc(64);
//		vm.set(addr2, 64, new long[] {0xfedcba9876543210l});
//		System.out.println(vm.get(addr2, 64)[0]);
//		
//		long addr3 = vm.alloc(128);
//		vm.set(addr3 + 8, 64, new long[] {0x0123456789abcdefl});
//		System.out.println(Arrays.toString(vm.objectPool[2]));
//		System.out.println(vm.get(addr3+8, 64)[0]);
//		
//		long addr4 = vm.alloc(64);
//		vm.set(addr4+7, 16, new long[] {0xcdefl});
////		System.out.println(Arrays.toString(vm.objectPool[3]));
//		System.out.println(vm.get(addr4+7, 16)[0]);
//		
//		vm.free(addr3, 128);
//		addr3 = vm.alloc(256+64);
//		vm.set(addr3+17, 256, new long[] {0x0123456789abcdefl,0xfedcba9876543210l, 0x0123456789abcdefl, 0xfedcba9876543210l});
////		System.out.println(Arrays.toString(vm.objectPool[2]));
//		for(long l : vm.objectPool[2])
//			System.out.println(Long.toHexString(l));
//		for(long l : vm.get(addr3+17, 256))
//			System.out.println(Long.toHexString(l));
//		System.out.println(vm.get(addr3+17, 64)[0]);
//		System.out.println(vm.get(addr3+17+64, 64)[0]);
//		System.out.println(vm.get(addr3+17+128, 64)[0]);
//		System.out.println(vm.get(addr3+17+192, 64)[0]);
//	}
}

