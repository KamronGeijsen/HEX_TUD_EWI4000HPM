package compiler;

import compiler.Lexer.*;
import compiler.MicroAssembler.AddressLabel;
import compiler.MicroAssembler.Instruction;
import compiler.MicroAssembler.InstructionBlock;
import compiler.NaiveParser.*;
import compiler.NaiveTypechecker.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class NaiveAssembler {
//	static File inputFile = new File("src/code12.hex");
//	static File inputFile = new File("examples/mod of PowerOfTwo.hex");
//static File inputFile = new File("examples/debug.hex");
	static File inputFile = new File("examples/boundschecks.hex");
//	static File inputFile = new File("examples/indexOf.hex");
//	static File inputFile = new File("examples/mod of PowerOfTwo automated.hex");
//	static File inputFile = new File("examples/tests.hex");
	public static void main(String[] args) throws IOException {
		String fileContent = new String(Files.readAllBytes(inputFile.toPath())) + " ";
		Arrays.stream(new int[0]).allMatch(n -> true);
		Lexer lexer = new Lexer();
		NaiveParser parser = new NaiveParser();
		NaiveTypechecker polisher = new NaiveTypechecker();
		NaiveOptimizer optimizer = new NaiveOptimizer();
		
		System.out.println("Input:\n" + fileContent + "\n===========================================\n");
		CurlyBracketParse b = lexer.parse(fileContent);
		System.out.println("Lexed:\n" + b.toParseString() + "\n===========================================\n");
		b = (CurlyBracketParse) parser.parse(b);

		System.out.println("Parsed:\n" + b.toParseString() + "\n===========================================\n");
		
		Body moduleBody = (Body)polisher.createContexts(b, polisher.builtins);
		polisher.resolveTypes(moduleBody, moduleBody.context);

		polisher.typeChecker(moduleBody, moduleBody.context);
		System.out.println("Polished:\n" + moduleBody.toParseString() + "\n===========================================\n");


		optimizer.optimize(moduleBody, moduleBody.context);
		System.out.println("Optimized:\n" + moduleBody.toParseString());
//		System.out.println(moduleBody.context.functionDefinitions.get(1).body.context.localValues);
		
		System.out.println("Assembled:\n");

		NaiveAssembler naiveAssembler = new NaiveAssembler();
		naiveAssembler.compile(moduleBody);
	}
	
	class AsmContext {
		Map<String, Variable> variables = new HashMap<>();

		AddressLabel returnLabel;
		AddressLabel continueLabel;
		AddressLabel breakLabel;

		LocalVariable getLocal(String s) {
			Variable v = variables.get(s);
			if(v instanceof LocalVariable lv) {
				return lv;
			}
			throw new RuntimeException("Could not find " + s + " in local context: " + variables);
		}

		void addStructToVariables(StructType struct) {
			int totalOffset = 0;
			for(Variable v : variables.values())
				totalOffset += v.type.size;

			for(int i = 0; i < struct.vars.size(); i++) {
				Variable old = variables.put(struct.vars.get(i), new LocalVariable(struct.types.get(i), totalOffset));
				if (old != null) throw new RuntimeException("Name shadowing! " + struct.types.get(i) + " " +struct.vars.get(i) + " replaces " + old);
				totalOffset+=struct.types.get(i).size;
			}
		}
		void removeStructFromVariables(StructType struct) {
			for(int i = 0; i < struct.vars.size(); i++) {
				Variable old = variables.remove(struct.vars.get(i));
				if (old == null) throw new RuntimeException("FATAL ERROR! SHOULD EXIST " + struct.types.get(i) + " " +struct.vars.get(i));
			}
		}
//		AddressLabel Label;


	}

	Map<FunctionIdentifier, AddressLabel> blockToInstrBlock = new HashMap<>();
	Map<String, AddressLabel> builtinFunctions = new HashMap<>();
	void compile(Body s) {
		ArrayList<Instruction[]> instrs = new ArrayList<>();
		MicroAssembler assembler = new MicroAssembler();
		InstructionBlock root = assembler.new InstructionBlock(null, "__main__");
		
		builtinFunctions.put("print", root.dataLabel(64));
		builtinFunctions.put("scan", root.dataLabel(64));
		builtinFunctions.put("alloc", root.dataLabel(64));
		builtinFunctions.put("dealloc", root.dataLabel(64));
		builtinFunctions.put("print2", root.dataLabel(64));
		
		for(Function fn : s.context.allDefinedFunctions) {
			System.out.println("FN DEF" + fn.functionIdentifier);
			InstructionBlock blk = root.addBlock(fn.functionIdentifier.name);
			blockToInstrBlock.put(fn.functionIdentifier, blk.label());
			compileBody(blk, fn);
		}

		
		System.out.println();
		root.assemble();
		System.out.println();
		root.updateLabel(blockToInstrBlock);
		
		byte[] bytes = root.bytes;
		for(byte b : bytes)
			System.out.print(Integer.toHexString((b&255)|0x100).substring(1));
		System.out.println();
		print(root);
		
		
		assembler.assemble(bytes);
	}
	
	void print(InstructionBlock ib) {
		for(Instruction i : ib.instructions) {
			if(i instanceof InstructionBlock ibb) {
				System.out.println(ibb.name + "{");
				print(ibb);
				System.out.println("}");
			} else {
//				System.out.println("%04x".formatted(i.getAddress()) + "\t" + i);
				System.out.println("%04x".formatted(i.getAddress()) + "\t" + i + "\t" + Arrays.toString(i.bytes));
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
	

	
	void compileBody(InstructionBlock ib, Function fn) {
		Body fnBody = fn.body;
		ib.prolog();
		ib.allocStack(fnBody.context.localValues.size + fn.functionIdentifier.type.args.size);
		ib.argumentsToVariables(fn.functionIdentifier.type.args.types.size());
		
		AsmContext ac = new AsmContext();
		ac.addStructToVariables(fn.functionIdentifier.type.args);
		ac.addStructToVariables(fnBody.context.localValues);
		for(Block b : fnBody.expr) {
			compileExpr(ib, b, fnBody.context, ac);
		}
		ib.epilog();
		ib.ret();
	}
	
	void compileExpr(InstructionBlock ib, Block b, Context context, AsmContext ac) {
		if(b instanceof CoreOp op && op.operands.size() == 2 && op.s.equals("=")) {
			if (op.operands.get(0) instanceof AliasParse s && !context.isType(s.s)) {
				System.out.println(s);
				compileExpr(ib, op.operands.get(1), context, ac);
				ib.setStackVariable(ac.getLocal(s.s).offset);
			} else if (op.operands.get(0) instanceof CoreOp co
					&& co.operands.size() == 2 && co.s.equals(".")
					&& co.operands.get(0) instanceof AliasParse s
					&& co.operands.get(1) instanceof NaiveTypechecker.NaiveArrayGenerator ag
					&& ag.blocks.size() == 1) {
				compileExpr(ib, op.operands.get(1), context, ac);
				compileExpr(ib, ag.blocks.get(0), context, ac);
				ib.setIndexStackArray(ac.getLocal(s.s).offset);
			} else throw new RuntimeException("Invalid settable: " + op);
		} else if(b instanceof CoreOp op && op.operands.size() == 2 && op.s.equals(".")
				&& op.operands.get(0) instanceof AliasParse s
				&& op.operands.get(1) instanceof NaiveTypechecker.NaiveArrayGenerator ag && ag.blocks.size() == 1) {
			compileExpr(ib, ag.blocks.get(0), context, ac);
			System.out.println("HUH " + s.s);
			ib.getIndexStackArray(ac.getLocal(s.s).offset);
		} else if(b instanceof CoreOp op && op.operands.size() == 2 && op.s.equals(".") && op.operands.size() == 2) {
			if(op.operands.get(0) instanceof Symbol s1 && op.operands.get(1) instanceof AliasParse ap && ap.s.equals("length")) {
				ib.getField(ac.getLocal(s1.s).offset, 0);
			}
		} else if(b instanceof CoreOp op && op.operands.size() == 2) {
//			System.out.println(op);
			compileExpr(ib, op.operands.get(0), context, ac);
			compileExpr(ib, op.operands.get(1), context, ac);
			ib.binOpStack(op.s);
		} else if(b instanceof CoreOp op && op.operands.size() == 1 && op.s.equals("print")) {
			System.out.println(op.operands.get(0).getClass());
			if(op.operands.get(0) instanceof CoreOp c && c.s.equals(",") && c.operands.size() == 2) {
				compileExpr(ib, c.operands.get(0), context, ac);
				compileExpr(ib, c.operands.get(1), context, ac);
				ib.popArguments(2);
				ib.callExternal(builtinFunctions.get("print2"));
			} else {
				compileExpr(ib, op.operands.get(0), context, ac);
				ib.popArguments(1);
				ib.callExternal(builtinFunctions.get("print"));
			}
		} else if(b instanceof CoreOp op && op.s.equals("return")) {
			if(op.operands.size() == 0) {

			} else if(op.operands.size() == 1) {
				compileExpr(ib, op.operands.get(0), context, ac);
				ib.popRet();
			} else throw new RuntimeException("Invalid return arity " + b);

			if(ac.returnLabel == null) {
				ib.epilog();
				ib.ret();
			} else {
				ib.jmp(ac.returnLabel);
			}
		} else if(b instanceof CoreOp op && op.operands.size() == 1 && op.s.equals("new") && op.operands.get(0) instanceof TypeObjectGenerator tog && tog.type instanceof NaiveArrayType at) {
			ib.pushLiteral(at.length*8 + 8);
			ib.popArguments(1);
			ib.callExternal(builtinFunctions.get("alloc"));
			ib.pushRet();
			ib.setArrayLength(at.length);
		} else if(b instanceof CoreOp op && op.operands.size() == 1) {
			compileExpr(ib,  op.operands.get(0), context, ac);
			ib.unOpStack(op.s);
		} else if(b instanceof AliasParse s) {
			ib.pushStackVariable(ac.getLocal(s.s).offset);
		} else if(b instanceof NumberParse n) {
			ib.pushLiteral(Long.parseLong(n.s));
		} else if(b instanceof Lexer.Keyword n) {
			switch (n.s){
				case "true": ib.pushLiteral(1); break;
				case "false": ib.pushLiteral(0); break;

			}

		} else if(b instanceof CoreFunctionCall fc) {
			if(fc.function instanceof FunctionObjectGenerator fg) {
				if(fc.argument instanceof CoreOp op && op.s.equals(",")) {
					for(Block o : op.operands)
						compileExpr(ib, o, context, ac);
					ib.popArguments(op.operands.size());
				} else {
					compileExpr(ib, fc.argument, context, ac);
					ib.popArguments(1);
				}
//				System.out.println(fg.functionIdentifier.);
				Function fn = context.overloadedFunction(fg.functionIdentifier.name, fg.functionIdentifier.type.args.types);
				ib.callFunction(fn.functionIdentifier);
				
				if(fg.functionIdentifier.type.rets.types.size() > 0) {
					ib.pushRet();
				}
			} else throw new RuntimeException("Invalid operation: " + fc.function.getClass());
		} else if(b instanceof FunctionObjectGenerator s) {
			
		} else if(b instanceof TypeCast tc && tc.type instanceof RefinementType rt) {
			StructType inheritStruct = (StructType)rt.inheritType;

			compileExpr(ib, tc.value, context, ac);
			ib.dup();
			ib.popArguments(1);
			
			AsmContext newAc = new AsmContext();
			ib.prolog();
			ib.allocStack(inheritStruct.size + rt.customMatch.context.localValues.size);
			newAc.addStructToVariables(inheritStruct);
			newAc.addStructToVariables(rt.customMatch.context.localValues);
			newAc.returnLabel = ib.emptyLabel("retEnd");

			
			ib.argumentsToVariables(1);
			for(Block block : rt.customMatch.expr) {
				compileExpr(ib, block, rt.customMatch.context, newAc);
			}
			ib.label(newAc.returnLabel);
			ib.segFaultOrContinue();
//			ib.deallocStack(inheritStruct.size);
			ib.epilog();
		} else if(b instanceof CoreBenchmarkStatement cbs) {
			
			ib.setupBenchmark();
			if(cbs.expr instanceof NumberParse n) {
				ib.repeatSetupBenchmark(Long.parseLong(n.s));
				AddressLabel label = ib.label();
				compileExpr(ib, cbs.body, context, ac);
				ib.repeatBenchmark(label);
			} else
				compileExpr(ib, cbs.body, context, ac);
			
			ib.measureBenchmark();
		} else if(b instanceof NaiveArrayGenerator ag) {

			for(int i = 0; i < ag.blocks.size(); i++){
				compileExpr(ib, ag.blocks.get(i), context, ac);
			}
			ib.pushLiteral(ag.blocks.size() * 8 + 8);
			ib.popArguments(1);
			ib.callExternal(builtinFunctions.get("alloc"));
			ib.pushRet();
			ib.stackToArray(ag.blocks.size());
		} else if(b instanceof CoreIfStatement ifs) {
			compileExpr(ib, ifs.argument, context, ac);
			AddressLabel ifEnd = ib.emptyLabel("ifEnd");
			ib.condJmp(ifEnd);

			compileExpr(ib, ifs.body, context, ac);

			if(ifs.elseBody instanceof Body elseBody && !elseBody.expr.isEmpty()){
				AddressLabel elseEnd = ib.emptyLabel("elseEnd");
				ib.jmp(elseEnd);
				ib.label(ifEnd);

				compileExpr(ib, ifs.elseBody, context, ac);

				ib.label(elseEnd);
			} else {
				ib.label(ifEnd);
			}

		} else if(b instanceof CoreWhileStatement ws) {

			AddressLabel repeat = ib.label();
			compileExpr(ib, ws.argument, context, ac);
			AddressLabel endAddr = ib.emptyLabel("whileEnd");
			ib.condJmp(endAddr);

			compileExpr(ib, ws.body, context, ac);

			ib.jmp(repeat);
			ib.label(endAddr);


		} else if(b instanceof Body body) {
			ac.addStructToVariables(body.context.localValues);
			ib.allocStack(body.context.localValues.size);
			
			for(Block block : body.expr) {
				compileExpr(ib, block, body.context, ac);
			}
			ac.removeStructFromVariables(body.context.localValues);
			ib.deallocStack(body.context.localValues.size);

		} else throw new RuntimeException("Invalid operation: " + b.getClass());
		
	}
}
