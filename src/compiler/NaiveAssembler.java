package compiler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;

import compiler.Lexer.Block;
import compiler.Lexer.CurlyBracketParse;
import compiler.MicroAssembler.Address;
import compiler.MicroAssembler.Instruction;
import compiler.NaiveParser.CoreOp;
import compiler.NaivePolisher.AccessValue;
import compiler.NaivePolisher.Function;
import compiler.NaivePolisher.LocalVariable;
import compiler.NaivePolisher.Scope;

public class NaiveAssembler {
	static File inputFile = new File("src/code12.hex");
	public static void main(String[] args) throws IOException {
		String fileContent = new String(Files.readAllBytes(inputFile.toPath())) + " ";
		
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
			compileBody(instrs, assembler, (Scope)fn.body);
		}
		for(Block b : s.blocks) {
			compileExpr(instrs, assembler, b);
		}
		
		for(Instruction[] is : instrs)
			for(Instruction i : is) {
				i.compile();
			}
		for(Instruction[] group : instrs) {
			for(Instruction i : group) {
				for(byte b : i.bytes)
					System.out.print(Integer.toHexString((b&255)|0x100).substring(1) + " ");
			}
			System.out.println();
		}
//		for(Instruction[] is : instrs)
//		for(Instruction i : is) {
//			for(byte b : i.bytes)
//				System.out.print(Integer.toHexString((b&255)|0x100).substring(1) + " ");
//			System.out.println();
//		}
	}
	
	void compileBody(ArrayList<Instruction[]> instrs, MicroAssembler assembler, Scope fnBody) {
		
		instrs.add(assembler.prolog());
		instrs.add(assembler.sub(assembler.RSP, fnBody.allocateSize/8));
		for(Block b : fnBody.blocks) {
			compileExpr(instrs, assembler, b);
		}
	}
	
	void compileExpr(ArrayList<Instruction[]> instrs, MicroAssembler assembler, Block b) {
		System.out.println(b.getClass());
		if(b instanceof CoreOp op && op.s.equals("%")) {
			compileBinOp(instrs, assembler, op.s, op.operands.get(0), op.operands.get(1));
		} else if(b instanceof CoreOp op && op.s.equals("return")) {
			compileUnOp(instrs, assembler, op.s, op.operands.get(0));
		} else if (b instanceof AccessValue av) {
			if(av.value instanceof LocalVariable lv) {
				instrs.add(new Instruction[] {assembler.new Push(assembler.new Address(assembler.RBP, null, 0, lv.stackOffset/8, 64))});
				
			}
			
		}
		
	}
	void compileUnOp(ArrayList<Instruction[]> instrs, MicroAssembler assembler, String op, Block a) {
		compileExpr(instrs, assembler, a);
		
		
		
		
	}
	
	void compileBinOp(ArrayList<Instruction[]> instrs, MicroAssembler assembler, String op, Block a, Block b) {
		compileExpr(instrs, assembler, a);
		compileExpr(instrs, assembler, b);
		
		instrs.add(assembler.add(null, null));
		switch(op) {
		case "+":
			instrs.add(assembler.add(null, null));
		}
		
		
	}
}
