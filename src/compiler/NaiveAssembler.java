package compiler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;

import javax.management.RuntimeErrorException;

import compiler.Lexer.Block;
import compiler.Lexer.CurlyBracketParse;
import compiler.MicroAssembler.Arg;
import compiler.MicroAssembler.Instruction;
import compiler.MicroAssembler.Register;
import compiler.NaiveParser.CoreFunctionCall;
import compiler.NaiveParser.CoreOp;
import compiler.NaivePolisher.AccessValue;
import compiler.NaivePolisher.Function;
import compiler.NaivePolisher.FunctionType;
import compiler.NaivePolisher.LiteralStringValue;
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
	
	
	
	void compile(Scope s, NaivePolisher polisher) {
		ArrayList<Instruction[]> instrs = new ArrayList<>();
		MicroAssembler assembler = new MicroAssembler();
		
//		instrs.add(assembler.prolog());
//		instrs.add(assembler.sub(assembler.RSP, s.allocateSize/8));
		for(Function fn : polisher.allFunctionDefinitions) {
			compileBody(instrs, assembler, fn);
		}
		for(Block b : s.blocks) {
			compileExpr(instrs, assembler, b, s);
		}
		
		System.out.println();
		for(Instruction[] is : instrs)
			for(Instruction i : is) {
				System.out.println(">\t" + i);
				i.compile();
			}
		for(Instruction[] group : instrs) {
			for(Instruction i : group) {
				for(byte b : i.bytes)
					System.out.print(Integer.toHexString((b&255)|0x100).substring(1) + " ");
			}
			System.out.println();
		}
		
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		for(Instruction[] is : instrs)
			for(Instruction i : is) {
				try {
					bos.write(i.bytes);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		byte[] bytes = bos.toByteArray();
		
		
		assembler.assemble(bytes);
//		for(Instruction[] is : instrs)
//		for(Instruction i : is) {
//			for(byte b : i.bytes)
//				System.out.print(Integer.toHexString((b&255)|0x100).substring(1) + " ");
//			System.out.println();
//		}
	}
	
	void compileBody(ArrayList<Instruction[]> instrs, MicroAssembler assembler, Function fn) {
		Scope fnBody = (Scope)fn.body;
		
		instrs.add(assembler.prolog());
		instrs.add(assembler.sub(assembler.RSP, fnBody.allocateSize/8));
		
		Arg[] params = new Arg[] {
			assembler.RCX,
			assembler.RDX,
			assembler.R8,
			assembler.R9,
		};
		for(int i = 0 ; i < ((FunctionType)fn.type).parameters.variables.size(); i++)
			instrs.add(assembler.mov(assembler.new Address(assembler.RBP, null, 0, -i*8-8, 64), (Register)params[i]));
		
		for(Block b : fnBody.blocks) {
			compileExpr(instrs, assembler, b, fnBody);
		}
		instrs.add(assembler.epilog());
		instrs.add(assembler.ret());
	}
	
	void compileExpr(ArrayList<Instruction[]> instrs, MicroAssembler assembler, Block b, Scope s) {
		System.out.println(b.getClass());
		if(b instanceof CoreOp op && op.operands.size() == 2) {
			compileBinOp(instrs, assembler, op.s, op.operands.get(0), op.operands.get(1), s);
		} else if(b instanceof CoreOp op && op.operands.size() == 1) {
			compileUnOp(instrs, assembler, op.s, op.operands.get(0), s);
		} else if (b instanceof AccessValue av) {
			if(av.value instanceof LocalVariable lv) {
				instrs.add(new Instruction[] {assembler.new Push(assembler.new Address(assembler.RBP, null, 0, -lv.stackOffset/8-8, 64))});
				
			} else if(av.value instanceof LiteralValue lv) {
				instrs.add(new Instruction[] {assembler.new Push(assembler.new Immediate(lv.value.longValue()))});
			}
			
		} else if(b instanceof CoreFunctionCall fc) {
			if(fc.function instanceof AccessValue av) {
				
//				System.out.println(s.getFunction(av.value.s));
//				System.out.println(fc.function);
//				System.out.println(fc.argument);
			}
			else throw new RuntimeException("Unimplemented: " + b.getClass());
//			s.getFunction(fc.function.);
			
		} else throw new RuntimeException("Invalid operation: " + b.getClass());
		
	}
	void compileUnOp(ArrayList<Instruction[]> instrs, MicroAssembler assembler, String op, Block a, Scope s) {
		compileExpr(instrs, assembler, a, s);
		
		
		switch(op) {
		case "return":
			instrs.add(new Instruction[] {assembler.new Pop(assembler.RAX)});
			break;
		case "print":
			instrs.add(new Instruction[] {assembler.new Pop(assembler.RAX)});
			break;
		default:
			throw new RuntimeException("Invalid operation: " + op);
		}
	}
	
	
	void compileBinOp(ArrayList<Instruction[]> instrs, MicroAssembler assembler, String op, Block a, Block b, Scope s) {
		compileExpr(instrs, assembler, a, s);
		compileExpr(instrs, assembler, b, s);
		
		instrs.add(assembler.binOpStack(op));
//		System.out.println(assembler.binOpStack(op));
		
//		ArrayList<Instruction> is = new ArrayList<>();
//		is.add(assembler.new Pop(assembler.RAX));
//		switch(op) {
//		case "+":
//			is.addAll(Arrays.asList(assembler.binOp(0, assembler.new Address(assembler.RSP, null, 0, 0, 64), assembler.RAX)));
//			break;
//		case "-":
//			is.addAll(Arrays.asList(assembler.binOp(1, assembler.new Address(assembler.RSP, null, 0, 0, 64), assembler.RAX)));
//			break;
//		case "-":
//			is.addAll(Arrays.asList(assembler.binOp(1, assembler.new Address(assembler.RSP, null, 0, 0, 64), assembler.RAX)));
//			break;
//		}
//		
//		instrs.add(is.toArray(Instruction[]::new));
	}
}
