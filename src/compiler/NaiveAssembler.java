package compiler;

import java.awt.*;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Scanner;

import compiler.Lexer.CurlyBracketParse;
import compiler.NaiveAssembler.Arg;

public class NaiveAssembler {
	
	class Arg {
		Integer size = null;
		
	}
	class Register extends Arg {
		int reg;
		Register(int reg, int size){
			this.reg = reg;
			this.size = (Integer) size;
		}
	}
	class Address extends Arg {
		Register base;
		Register index;
		int scale;
		int offset;
		public Address(Register base, Register index, int scale, int offset, int size) {
			this.base = base;
			this.index = index;
			this.scale = scale;
			this.offset = offset;
			this.size = size;
		}
	}
	class Variable extends Arg {
		String id;
		Variable(String id, int size){
			this.id = id;
			this.size = size;
		}
	}
	class StackVariable extends Variable {
		final int stackOffset;
		StackVariable(String id, int size, int stackOffset) {
			super(id, size);
			this.stackOffset = stackOffset;
		}
		
	}
	class Immediate extends Arg {
		long val;
		public Immediate(long val) {
			this.val = val;
		}
	}
	class Label extends Arg {

	}
	
	abstract class Instruction {
		int macroAddr;
		byte[] bytes;
		
		abstract void compile();
	}
	
	class Mov extends Instruction {
		Arg src;
		Arg dst;
		public Mov(Arg dst, Arg src) {
			this.src = src;
			this.dst = dst;
		}
		@Override
		void compile() {
			int opSize = dst.size;
			ByteBuffer bb = ByteBuffer.allocate(256);
			bb.order(ByteOrder.LITTLE_ENDIAN);
			
			if(src instanceof Immediate i) {
				if(opSize == 64)
					bb.put(new byte	[] { 0x48, (byte) 0xC7});
				else if(opSize == 32)
					bb.put(new byte	[] { (byte) 0xC7});
				
				mr(bb, 0, dst);

				if((long)(int)i.val == i.val)
					bb.putInt((int)i.val);
			} else if(dst instanceof Address a) {
				bb.put(new byte	[] {0x48, (byte)0x89});
				if(src instanceof Register d) {
					mr(bb, d.reg, a);
				}
			} else if(dst instanceof Register s) {
				bb.put(new byte	[] {0x48, (byte)0x8B});
				mr(bb, s.reg, src);
			}
			bytes = Arrays.copyOf(bb.array(), bb.position());
		}
	}
	class BinOp extends Instruction {
		int op;
		Arg src;
		Arg dst;
		public BinOp(int op, Arg dst, Arg src) {
			this.op = op;
			this.src = src;
			this.dst = dst;
		}
		@Override
		void compile() {
			int opSize = dst.size;
			ByteBuffer bb = ByteBuffer.allocate(256);
			bb.order(ByteOrder.LITTLE_ENDIAN);
			
			if(src instanceof Immediate i) {
				if((long)(byte)i.val == i.val) {
					if(opSize == 64)
						bb.put(new byte	[] { 0x48, (byte) (0x83)});
					else if(opSize == 32)
						bb.put(new byte	[] { (byte) 0x83});
				} else if((long)(int)i.val == i.val) {
					if(opSize == 64)
						bb.put(new byte	[] { 0x48, (byte) (0x81)});
					else if(opSize == 32)
						bb.put(new byte	[] { (byte) 0x81});
				}

				
				mr(bb, op, dst);
				if((long)(byte)i.val == i.val)
					bb.put((byte)i.val);
				else if((long)(int)i.val == i.val)
					bb.putInt((int)i.val);
			}else if(dst instanceof Address a) {

				
				if(opSize == 64)
					bb.put(new byte	[] { 0x48, (byte) (0x01 + (op << 3))});
				else if(opSize == 32)
					bb.put(new byte	[] {(byte) (0x01 + (op << 3))});
//				System.out.println(op + "\t" + dst + "\t" + src);
//				System.out.println(Arrays.toString(Arrays.copyOf(bb.array(), bb.position())));
				if(src instanceof Register r) {
					mr(bb, r.reg, dst);
				} else throw new RuntimeException("invalid opcode");
				
				
			} else if(dst instanceof Register r) {
				if(opSize == 64)
					bb.put(new byte	[] { 0x48, (byte) (0x03 + (op << 3))});
				else if(opSize == 32)
					bb.put(new byte	[] {(byte) (0x03 + (op << 3))});
				mr(bb, r.reg, src);
				
			}
			
			bytes = Arrays.copyOf(bb.array(), bb.position());
		}
	}
	class Push extends Instruction {
		Arg src;
		public Push(Arg src) {
			this.src = src;
		}
		@Override
		void compile() {
			if(src instanceof Register r) {
				bytes = new byte[] {(byte) (0x50 + r.reg)};
			}
		}

	}
	class Pop extends Instruction {
		Arg dst;
		public Pop(Arg dst) {
			this.dst = dst;
		}
		@Override
		void compile() {
			if(dst instanceof Register r) {
				bytes = new byte[] {(byte) (0x58 + r.reg)};
			}
		}

	}
	class Call extends Instruction {
		Arg src;
		public Call(Arg fn, Arg ... args) {
			this.src = src;
		}
		@Override
		void compile() {
			if(src instanceof Register r) {
				bytes = new byte[] {(byte) (0x50 + r.reg)};
			}
		}
	}
	class Ret extends Instruction {
		public Ret() {
		}
		@Override
		void compile() {
			bytes = new byte[] {(byte) 0xC3};
		}
		
	}
	void mr(ByteBuffer bb, int r, Arg mr) {
		if(mr instanceof Register s) {
			bb.put((byte) (0xC0 + (r << 3) + s.reg));
		} else if(mr instanceof Address a) {
			if(a.offset == 0 && a.base.reg != 5) {
				bb.put((byte) (0x00 + (r << 3) + a.base.reg));
			} else if(a.offset == (int)(byte)a.offset) {
				bb.put((byte) (0x40 + (r << 3) + a.base.reg));
				bb.put((byte) a.offset);
			}  else {
				bb.put((byte) (0x80 + (r << 3) + a.base.reg));
				bb.putInt(a.offset);
			}
		}
		
	}
	
	Register RAX = new Register(0, 64);
	Register RCX = new Register(1, 64);
	Register RDX = new Register(2, 64);
	Register RBX = new Register(3, 64);
	Register RSP = new Register(4, 64);
	Register RBP = new Register(5, 64);
	Register RSI = new Register(6, 64);
	Register RDI = new Register(7, 64);
	Register R8 = new Register(8, 64);
	Register R9 = new Register(9, 64);
	Register R10 = new Register(10, 64);
	Register R11 = new Register(11, 64);
	Register R12 = new Register(12, 64);
	Register R13 = new Register(13, 64);
	Register R14 = new Register(14, 64);
	Register R15 = new Register(15, 64);

	
	
	Instruction[] prolog() {
		return new Instruction[] {
			new Push(RBP),
			new Mov(RBP, RSP),
		};
	}
	Instruction[] epilog() {
		return new Instruction[] { 
			new Mov(RSP, RBP),
			new Pop(RBP)
		};
	}
	Instruction[] alloc(StackVariable v) {
		return new Instruction[] { new BinOp(5, RSP, new Immediate(v.size/8)) };
	}
	Instruction[] dealloc(StackVariable v) {
		return new Instruction[] { new BinOp(0, RSP, new Immediate(v.size/8)) };
	}
	
	Instruction[] mov(Register r, long val) {
		return new Instruction[] {new Mov(r, new Immediate(val))};
	}
	Instruction[] mov(StackVariable v, long val) {
		return new Instruction[] {new Mov(new Address(RBP, null, 0, -v.stackOffset-8, 64), new Immediate(val))};
	}
	Instruction[] mov(StackVariable v, Register r) {
		return new Instruction[] {new Mov(new Address(RBP, null, 0, -v.stackOffset-8, 64), r)};
	}
	Instruction[] mov(Register r, StackVariable v) {
		return new Instruction[] {new Mov(r, new Address(RBP, null, 0, -v.stackOffset-8, 64))};
	}
	Instruction[] mov(Register d, Register s) {
		return new Instruction[] {new Mov(d, s)};
	}
	
	Instruction[] binOp(int op, Arg a, long val) {
		return new Instruction[] {new BinOp(op, a, new Immediate(val))};
	}
	Instruction[] binOp(int op, Arg a, Arg b) {
		if(a instanceof StackVariable v)
			a = new Address(RBP, null, 0, -v.stackOffset-8, 64);
		if(b instanceof StackVariable v)
			b = new Address(RBP, null, 0, -v.stackOffset-8, 64);
		
		
		if(a instanceof Address a1 && b instanceof Address a2)
			return new Instruction[] {new Mov(RAX, b), new BinOp(op, a, RAX)};
		return new Instruction[] {new BinOp(op, a, b)};
	}
	Instruction[] add(Arg a, long val) {
		return binOp(0, a, val);
	}
	Instruction[] add(Arg a, Arg b) {
		return binOp(0, a, b);
	}
	Instruction[] sub(Arg a, long val) {
		return new Instruction[] {new BinOp(5, a, new Immediate(val))};
	}
	Instruction[] returnV(StackVariable v) {
		return new Instruction[] {
				new Mov(RAX, new Address(RBP, null, 0, -v.stackOffset-8, 64)),
				new Mov(RSP, RBP),
				new Pop(RBP),
				new Ret()
		};
	}
	Instruction[] ret(long val) {
		return new Instruction[] {
				new Mov(RAX, new Immediate(val)),
				new Ret()
		};
	}
	Instruction[] ret() {
		return new Instruction[] {new Ret()};
	}
	
	
	
	public static byte[] toArr(String s) {
		s=s.trim();
		int len = s.length();
		byte[] data = new byte[len / 2]; // Each byte is represented by two characters in the string
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
		}
		return data;
	}
	NaiveAssembler(){
		StackVariable varA = new StackVariable("a", 64, 0);
		StackVariable varB = new StackVariable("b", 64, 8);
		Instruction[][] instrs = {
				prolog(),
				alloc(varA),
				alloc(varB),
				mov(varA, 5),
				mov(varB, 10),
				add(varA, varB),
				returnV(varA)
		};
		
		for(Instruction[] is : instrs)
			for(Instruction i : is) {
				i.compile();
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

//		byte[] bytes = toArr("488D1D0000000048B900E40B5402000000488B034883C30048FFC975F4C3         ");
//		byte[] bytes = toArr("488D1D0000000048B900E40B5402000000488D15080000004839D3730D488B034883C30048FFC975EFC3E800000000            ");
//		byte[] bytes = toArr("488D1D0000000048C7C100CA9A3B48C7C7A086010048C7C664000000488D1508000000488D46FF4821F8488B04C34883C30048FFC975ECC3E800000000");
//		byte[] bytes = toArr("488D1D0000000048C7C100CA9A3B48C7C7A086010048C7C664000000488D15080000004889F8489948F7FE4889D0488B04C34883C30048FFC975E8C3E800000000");
		
		// Mod
//		byte[] bytes = toArr("4889C84889D6489948F7FE4889D0C3"); // slow
//		byte[] bytes = toArr("488D41FF4821D0C3"); // fast
		
		// Indexing
//		byte[] bytes = toArr("4839D77D05488B04FEC3E800000000"); // slow
//		byte[] bytes = toArr("488B04FEC3   "); // fast
		
		// Indexing fat
//		byte[] bytes = toArr("483B0A7D06488B44CA08C3E800000000"); // slow
//		byte[] bytes = toArr("488B44CA08C3"); // fast
		
		
		for(byte b : bytes)
			System.out.print(Integer.toHexString((b&255)|0x100).substring(1) + " ");
		System.out.println();
		System.out.println();
		for(Instruction[] is : instrs)
			for(Instruction i : is) {
				for(byte b : i.bytes)
					System.out.print(Integer.toHexString((b&255)|0x100).substring(1) + " ");
				System.out.println();
			}
		
		try (FileOutputStream fos = new FileOutputStream("compiled\\code.hexe")) {
			fos.write(bytes);
		} catch (IOException e) {
			e.printStackTrace();
		}
		for(int repeat = 0; repeat < 1; repeat++) {
			long start = System.nanoTime();
			try {
//				Runtime.getRuntime().exec("C:\\Users\\kgeijsen\\Desktop\\C++ workspace\\HexRun\\x64\\Debug\\HexRun.exe");
//				Process process = new ProcessBuilder("C:\\Users\\kgeijsen\\Desktop\\C++ workspace\\HexRun\\x64\\Debug\\HexRun.exe").start();
				ProcessBuilder process = new ProcessBuilder("compiled\\hexrun.exe", "compiled\\code.hexe");
				process.inheritIO();
				Process p = process.start();
				try (Scanner scanner = new Scanner(System.in);
						OutputStream processOutputStream = p.getOutputStream();
						BufferedReader processInputStream = new BufferedReader(new InputStreamReader(p.getInputStream()))) {

//					while (p.isAlive() && System.in.available() > 0 && scanner.hasNextLine()) {
//						scanner.n
//						String input = scanner.nextLine() + "\n";
//						processOutputStream.write(input.getBytes());
//						processOutputStream.flush();
//
//					}

					while(p.isAlive()) {
						while (processInputStream.ready()) {
							System.out.println(processInputStream.readLine());
						}
					}

				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
//			System.out.println((System.nanoTime()-start)/1000000/1000.0);
		}
		
	}
	static File inputFile = new File("src/code12.hex");
	public static void main(String[] args) throws IOException {
		String fileContent = new String(Files.readAllBytes(inputFile.toPath())) + " ";
		Lexer lexer = new Lexer();
		NaiveParser parser = new NaiveParser();
		
		System.out.println("Input:\n" + fileContent + "\n===========================================\n");
		CurlyBracketParse b = lexer.parse(fileContent);
		System.out.println("Lexed:\n" + b.toParseString()+"\n===========================================\n");
		b = (CurlyBracketParse) parser.parse(b);
		
		System.out.println("Parsed:\n" + b.toParseString()+"\n===========================================\n");
		
		new NaiveAssembler();
	}
}
