package compiler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import compiler.Lexer.AliasParse;
import compiler.Lexer.Block;
import compiler.Lexer.CurlyBracketParse;
import compiler.Lexer.NumberParse;
import compiler.Lexer.Symbol;
import compiler.MicroAssembler.Instruction;
import compiler.MicroAssembler.InstructionBlock;
import compiler.MicroAssembler.AddressLabel;
import compiler.NaiveParser.CoreBenchmarkStatement;
import compiler.NaiveParser.CoreFunctionCall;
import compiler.NaiveParser.CoreOp;
import compiler.NaiveTypechecker.Body;
import compiler.NaiveTypechecker.Context;
import compiler.NaiveTypechecker.Function;
import compiler.NaiveTypechecker.FunctionIdentifier;
import compiler.NaiveTypechecker.FunctionObjectGenerator;
import compiler.NaiveTypechecker.LiteralGenerator;
import compiler.NaiveTypechecker.RefinementType;
import compiler.NaiveTypechecker.StructType;
import compiler.NaiveTypechecker.Type;
import compiler.NaiveTypechecker.TypeCast;

public class NaiveAssembler {
//	static File inputFile = new File("src/code12.hex");
	static File inputFile = new File("examples/mod of PowerOfTwo.hex");
	public static void main(String[] args) throws IOException {
		String fileContent = new String(Files.readAllBytes(inputFile.toPath())) + " ";
		Arrays.stream(new int[0]).allMatch(n -> true);
		Lexer lexer = new Lexer();
		NaiveParser parser = new NaiveParser();
		NaiveTypechecker polisher = new NaiveTypechecker();
		
		System.out.println("Input:\n" + fileContent + "\n===========================================\n");
		CurlyBracketParse b = lexer.parse(fileContent);
		System.out.println("Lexed:\n" + b.toParseString() + "\n===========================================\n");
		b = (CurlyBracketParse) parser.parse(b);

		System.out.println("Parsed:\n" + b.toParseString() + "\n===========================================\n");
		
		Body moduleBody = (Body)polisher.polish(b, polisher.builtins);
		polisher.resolveTypes(moduleBody, moduleBody.context);

		polisher.typeChecker(moduleBody, moduleBody.context);
		System.out.println("Polished:\n" + moduleBody.toParseString() + "\n===========================================\n");
		
//		System.out.println(moduleBody.context.functionDefinitions.get(1).body.context.localValues);
		
		System.out.println("Assembled:\n");

		NaiveAssembler naiveAssembler = new NaiveAssembler();
		naiveAssembler.compile(moduleBody);
	}
	

	Map<FunctionIdentifier, AddressLabel> blockToInstrBlock = new HashMap<>();
	Map<String, AddressLabel> builtinFunctions = new HashMap<>();
//	Map<Function, InstructionLabel> blockToInstrBlock = new HashMap<>();
	void compile(Body s) {
		ArrayList<Instruction[]> instrs = new ArrayList<>();
		MicroAssembler assembler = new MicroAssembler();
		InstructionBlock root = assembler.new InstructionBlock(null, "__main__");
		
		builtinFunctions.put("print", root.dataLabel(64));
		builtinFunctions.put("scan", root.dataLabel(64));
		builtinFunctions.put("alloc", root.dataLabel(64));
		builtinFunctions.put("dealloc", root.dataLabel(64));
		
		for(Function fn : s.context.allDefinedFunctions) {
//			System.out.println();
//			System.out.println("This defin: " + fn.functionIdentifier.name);
			
//			System.out.println(fn.body);
			InstructionBlock blk = root.addBlock(fn.functionIdentifier.name);
			
//			System.out.println("Put in body: " + fn.body);
			blockToInstrBlock.put(fn.functionIdentifier, blk.label());
//			System.out.println("Working on: " + fn.body.context.localValues);
			compileBody(blk, fn);
			
		}
//		System.out.println("\nHashtable");
//		for(Entry<Block, InstructionBlock> e : blockToInstrBlock.entrySet()) {
//			System.out.println(e.getKey());
//			System.out.println(e.getValue());
//			System.out.println();
//		}
		
//		for(Block b : s.blocks) {
//			compileExpr(instrs, assembler, b, s);
//		}
		
		System.out.println();
		root.assemble();
		System.out.println();
		root.updateLabel(blockToInstrBlock);
		
		
//		for(Instruction[] group : instrs) {
//			for(Instruction i : group) {
//				for(byte b : i.bytes)
//					System.out.print(Integer.toHexString((b&255)|0x100).substring(1) + " ");
//			}
//			System.out.println();
//		}
//		
//		ByteArrayOutputStream bos = new ByteArrayOutputStream();
//		for(Instruction[] is : instrs)
//			for(Instruction i : is) {
//				try {
//					bos.write(i.bytes);
//				} catch (IOException e) {
//					e.printStackTrace();
//				}
//			}
//		byte[] bytes = bos.toByteArray();
		byte[] bytes = root.bytes;
		for(byte b : bytes)
			System.out.print(Integer.toHexString((b&255)|0x100).substring(1));
		System.out.println();
		print(root);
		
		
		assembler.assemble(bytes);
//		for(Instruction[] is : instrs)
//		for(Instruction i : is) {
//			for(byte b : i.bytes)
//				System.out.print(Integer.toHexString((b&255)|0x100).substring(1) + " ");
//			System.out.println();
//		}
	}
	
	void print(InstructionBlock ib) {
		for(Instruction i : ib.instructions) {
			if(i instanceof InstructionBlock ibb) {
//				System.out.println(ibb.name+":{");
				print(ibb);
//				System.out.println("}");
			} else {
				System.out.println("%04x".formatted(i.getAddress()) + "\t" + i);
			}
		}
	}
	
	class Variable {
		Type type;
	}
	class LocalVariable extends Variable {
		int offset;
		
		public LocalVariable(Type type, int offset) {
			this.type = type;
			this.offset = offset;
		}
		
		@Override
		public String toString() {
			return "@[rbp+"+offset/8 + "] " + type;
		}
	}
	
	void addStructToVariables(Map<String, Variable> variables, StructType struct) {
		int totalOffset = 0;
		for(Variable v : variables.values())
			totalOffset += v.type.size;
		
		for(int i = 0; i < struct.vars.size(); i++) {
			Variable old = variables.put(struct.vars.get(i), new LocalVariable(struct.types.get(i), totalOffset));
//			System.out.println("Now " + struct.types.get(i) + " " + struct.vars.get(i) + " is on " + totalOffset);
			if (old != null) throw new RuntimeException("Name shadowing! " + struct.types.get(i) + " " +struct.vars.get(i) + " replaces " + old);
			totalOffset+=struct.types.get(i).size;
		}
	}
	void removeStructFromVariables(Map<String, Variable> variables, StructType struct) {
		for(int i = 0; i < struct.vars.size(); i++) {
			Variable old = variables.remove(struct.vars.get(i));
			if (old == null) throw new RuntimeException("FATAL ERROR! SHOULD EXIST " + struct.types.get(i) + " " +struct.vars.get(i));
		}
	}
	
	void compileBody(InstructionBlock ib, Function fn) {
		Body fnBody = fn.body;
//		System.out.println("Here the body is: " + fnBody);
		
		
		ib.prolog();
//		System.out.println(">>>>>: " + fnBody.context.localValues + " " + fnBody.context.localValues.size);
		ib.allocStack(fnBody.context.localValues.size);
		
//		instrs.add(assembler.sub(assembler.RSP, fnBody.allocateSize/8));
		ib.argumentsToVariables(fn.functionIdentifier.type.args.types.size());
		
//		System.out.println(fnBody.context.localValues);
//		for(int i = 0 ; i < ((FunctionType)fn.type).parameters.variables.size(); i++)
//			instrs.add(assembler.mov(assembler.new Address(assembler.RBP, null, 0, -i*8-8, 64), (Register)params[i]));
		HashMap<String, Variable> variables = new HashMap<>();
		addStructToVariables(variables, fn.functionIdentifier.type.args);
		addStructToVariables(variables, fnBody.context.localValues);
//		System.out.println("These should exist: " + variables);
//		System.out.println(variables);
		for(Block b : fnBody.expr) {
//			System.out.println(b.getClass());
			compileExpr(ib, b, fnBody.context, variables);
		}
		ib.epilog();
		ib.ret();
	}
	
	void compileExpr(InstructionBlock ib, Block b, Context context, Map<String, Variable> variables) {
//		System.out.println(b.getClass());
//		System.out.println(variables);
		if(b instanceof CoreOp op && op.operands.size() == 2 && op.s.equals("=")) {
			
			if(op.operands.get(0) instanceof AliasParse s && !context.isType(s.s)) {
				compileExpr(ib, op.operands.get(1), context, variables);
//				System.out.println("Pop S " + s.s + " at " + variables.get(s.s));
				ib.setStackVariable(((LocalVariable)variables.get(s.s)).offset);
			}
		}
		else if(b instanceof CoreOp op && op.operands.size() == 2) {
			compileExpr(ib, op.operands.get(0), context, variables);
			compileExpr(ib, op.operands.get(1), context, variables);
			ib.binOpStack(op.s);
		} else if(b instanceof CoreOp op && op.operands.size() == 1 && op.s.equals("print")) {
			compileExpr(ib,  op.operands.get(0), context, variables);
			ib.popArguments(1);
			ib.callExternal(builtinFunctions.get("print"));
		} else if(b instanceof CoreOp op && op.operands.size() == 1) {
			compileExpr(ib,  op.operands.get(0), context, variables);
			ib.unOpStack(op.s);
		} else if(b instanceof AliasParse s) {
			ib.pushStackVariable(((LocalVariable)variables.get(s.s)).offset);
		} else if(b instanceof NumberParse n) {
//			System.out.println("Pushed thing while at " + variables + " >> " + n);
			ib.pushLiteral(Long.parseLong(n.s));
		} else if(b instanceof CoreFunctionCall fc) {
			if(fc.function instanceof FunctionObjectGenerator fg) {
				if(fc.argument instanceof CoreOp op && op.s.equals(",")) {
					for(Block o : op.operands)
						compileExpr(ib, o, context, variables);
					ib.popArguments(op.operands.size());
				} else {
					compileExpr(ib, fc.argument, context, variables);
					ib.popArguments(1);
				}
				
				Function fn = context.overloadedFunction(fg.functionIdentifier.name, fg.functionIdentifier.type.args.types);
//				System.out.println("Found this function: " + fn.body);
				ib.callFunction(fn.functionIdentifier);
				
				if(fg.functionIdentifier.type.rets.types.size() > 0) {
					ib.pushRet();
				}
			} else throw new RuntimeException("Invalid operation: " + fc.function.getClass());
		} else if(b instanceof FunctionObjectGenerator s) {
//			throw new RuntimeException("Unimplemented: " + b.getClass());
		} else if(b instanceof TypeCast tc && tc.type instanceof RefinementType rt) {
			StructType localVariables = (StructType)rt.inheritType;

			compileExpr(ib, tc.value, context, variables);
			ib.dup();
			ib.popArguments(1);
			
			HashMap<String, Variable> localvariables = new HashMap<>();
			addStructToVariables(localvariables, localVariables);
			ib.prolog();
			ib.allocStack(localVariables.size);
			
			ib.argumentsToVariables(1);
			for(Block block : rt.customMatch.expr) {
				compileExpr(ib, block, rt.customMatch.context, localvariables);
				
			}
			ib.segFaultOrContinue();
			ib.deallocStack(localVariables.size);
			ib.epilog();
		} else if(b instanceof CoreBenchmarkStatement cbs) {
			
			ib.setupBenchmark();
			System.out.println(cbs.expr.getClass());
			if(cbs.expr instanceof NumberParse n) {
				 
				ib.repeatSetupBenchmark(Long.parseLong(n.s));
				AddressLabel label = ib.label();
				compileExpr(ib, cbs.body, context, variables);
				ib.repeatBenchmark(label);
			} else
				compileExpr(ib, cbs.body, context, variables);
			
			
			ib.measureBenchmark();
			
		} else if(b instanceof Body body) {
//			throw new RuntimeException("Unimplemented: " + b.getClass());
			addStructToVariables(variables, body.context.localValues);
			ib.allocStack(body.context.localValues.size);
			
			
//			System.out.println("Entering with " + variables);
			for(Block block : body.expr) {
//				System.out.println(b.getClass());
				compileExpr(ib, block, body.context, variables);
			}
			removeStructFromVariables(variables, body.context.localValues);
			ib.deallocStack(body.context.localValues.size);
			
		} else throw new RuntimeException("Invalid operation: " + b.getClass());
		
	}
}
