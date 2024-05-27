package compiler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import compiler.Lexer.Block;
import compiler.Lexer.CurlyBracketParse;
import compiler.MicroAssembler.Address;
import compiler.MicroAssembler.Arg;
import compiler.MicroAssembler.Instruction;
import compiler.MicroAssembler.InstructionBlock;
import compiler.MicroAssembler.Label;
import compiler.MicroAssembler.Register;
import compiler.NaiveInterpreter.Stack;
import compiler.NaiveParser.CoreFunctionCall;
import compiler.NaiveParser.CoreOp;
import compiler.NaivePolisher.AccessStruct;
import compiler.NaivePolisher.AccessValue;
import compiler.NaivePolisher.Function;
import compiler.NaivePolisher.FunctionType;
import compiler.NaivePolisher.LiteralValue;
import compiler.NaivePolisher.LocalVariable;
import compiler.NaivePolisher.Scope;

public class NaiveAssembler {
	static File inputFile = new File("src/code12.hex");
	public static void main(String[] args) throws IOException {
		String fileContent = new String(Files.readAllBytes(inputFile.toPath())) + " ";
		Arrays.stream(new int[0]).allMatch(n -> true);
		Lexer lexer = new Lexer();
		NaiveParser parser = new NaiveParser();
		NaivePolisher polisher = new NaivePolisher();
		
		System.out.println("Input:\n" + fileContent + "\n===========================================\n");
		CurlyBracketParse b = lexer.parse(fileContent);
		System.out.println("Lexed:\n" + b.toParseString() + "\n===========================================\n");
		b = (CurlyBracketParse) parser.parse(b);

		System.out.println("Parsed:\n" + b.toParseString() + "\n===========================================\n");

		Scope s = polisher.polish(b, NaivePolisher.builtInScope);
		System.out.println("Polished:\n" + s.toParseString() + "\n===========================================\n");
		
		
		System.out.println("Assembled:\n");

		NaiveAssembler naiveAssembler = new NaiveAssembler();
		naiveAssembler.compile(s, polisher);
	}
	

	Map<Block, InstructionBlock> blockToInstrBlock = new HashMap<>();
	void compile(Scope s, NaivePolisher polisher) {
		ArrayList<Instruction[]> instrs = new ArrayList<>();
		MicroAssembler assembler = new MicroAssembler();
		InstructionBlock root = assembler.new InstructionBlock(null, "__main__");
		
		
		
		for(Function fn : polisher.allFunctionDefinitions) {
			InstructionBlock blk = root.addBlock(fn.s);
			blockToInstrBlock.put(fn.body, blk);
			compileBody(blk, fn);
			
		}
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
	
	void compileBody(InstructionBlock ib, Function fn) {
		Scope fnBody = (Scope)fn.body;
		
		ib.prolog();
		ib.allocStack(fnBody.allocateSize/8);
//		instrs.add(assembler.sub(assembler.RSP, fnBody.allocateSize/8));
		ib.argumentsToVariables(((FunctionType)fn.type).parameters.variables.size());
		
//		for(int i = 0 ; i < ((FunctionType)fn.type).parameters.variables.size(); i++)
//			instrs.add(assembler.mov(assembler.new Address(assembler.RBP, null, 0, -i*8-8, 64), (Register)params[i]));
		
		for(Block b : fnBody.blocks) {
			System.out.println(b.getClass());
			compileExpr(ib, b, fnBody);
		}
		ib.epilog();
		ib.ret();
	}
	
	void compileExpr(InstructionBlock ib, Block b, Scope s) {
//		System.out.println(b.getClass());
		if(b instanceof CoreOp op && op.operands.size() == 2) {
			compileExpr(ib,  op.operands.get(0), s);
			compileExpr(ib,  op.operands.get(1), s);
			ib.binOpStack(op.s);
			System.out.println("Binop: " + op.s);
		} else if(b instanceof CoreOp op && op.operands.size() == 1) {
			compileExpr(ib,  op.operands.get(0), s);
			ib.binOpStack(op.s);
		} else if (b instanceof AccessValue av) {
			if(av.value instanceof LocalVariable lv) {
				ib.pushStackVariable(-lv.stackOffset/8-8);
//				instrs.add(new Instruction[] {assembler.new Push(assembler.new Address(assembler.RBP, null, 0, -lv.stackOffset/8-8, 64))});
				
			} else if(av.value instanceof LiteralValue lv) {
				ib.pushLiteral(lv.value.longValue());
//				instrs.add(new Instruction[] {assembler.new Push(assembler.new Immediate(lv.value.longValue()))});
			}
			
		} else if(b instanceof CoreFunctionCall fc) {
			if(fc.function instanceof AccessValue av && fc.argument instanceof AccessStruct aav) {
				int paramCount = ((FunctionType)av.value.type).parameters.variables.size();
				
				for(Block e : aav.expressions)
					compileExpr(ib, e, s);
				
				ib.popArguments(paramCount);
				ib.callFunction(s.getFunction(av.value.s));
			}
			else throw new RuntimeException("Unimplemented: " + b.getClass());
//			s.getFunction(fc.function.);
			
		} else throw new RuntimeException("Invalid operation: " + b.getClass());
		
	}
//	void compileUnOp(ArrayList<Instruction[]> instrs, MicroAssembler assembler, String op, Block a, Scope s) {
//		compileExpr(instrs, assembler, a, s);
//		
//		
//		switch(op) {
//		case "return":
//			instrs.add(new Instruction[] {assembler.new Pop(assembler.RAX)});
//			break;
//		case "print":
//			instrs.add(new Instruction[] {assembler.new Pop(assembler.RCX)});
//			
//			break;
//		default:
//			throw new RuntimeException("Invalid operation: " + op);
//		}
//	}
	
	
//	void compileBinOp(ArrayList<Instruction[]> instrs, MicroAssembler assembler, String op, Block a, Block b, Scope s) {
//		compileExpr(instrs, assembler, a, s);
//		compileExpr(instrs, assembler, b, s);
//		
//		instrs.add(assembler.binOpStack(op));
////		System.out.println(assembler.binOpStack(op));
//		
////		ArrayList<Instruction> is = new ArrayList<>();
////		is.add(assembler.new Pop(assembler.RAX));
////		switch(op) {
////		case "+":
////			is.addAll(Arrays.asList(assembler.binOp(0, assembler.new Address(assembler.RSP, null, 0, 0, 64), assembler.RAX)));
////			break;
////		case "-":
////			is.addAll(Arrays.asList(assembler.binOp(1, assembler.new Address(assembler.RSP, null, 0, 0, 64), assembler.RAX)));
////			break;
////		case "-":
////			is.addAll(Arrays.asList(assembler.binOp(1, assembler.new Address(assembler.RSP, null, 0, 0, 64), assembler.RAX)));
////			break;
////		}
////		
////		instrs.add(is.toArray(Instruction[]::new));
//	}
}
