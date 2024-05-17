package compiler;

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
			this.size = size;
		}
	}
	class Address extends Arg {
		
	}
	class Immediate extends Arg {
		long val;
		public Immediate(long val) {
			this.val = val;
		}
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
			} 
			
			if(dst instanceof Register r) {
				bb.put((byte) (0xC0 + r.reg));
			}
				
			if(src instanceof Immediate i) {
				if((long)(int)i.val == i.val)
					bb.putInt((int)i.val);
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
			}
			
			if(dst instanceof Register r) {
				bb.put((byte) (0xC0 + r.reg + (op<<3)));
			}
				
			if(src instanceof Immediate i) {
				if((long)(byte)i.val == i.val)
					bb.put((byte)i.val);
				else if((long)(int)i.val == i.val)
					bb.putInt((int)i.val);
			}
			
			bytes = Arrays.copyOf(bb.array(), bb.position());
		}
	}
	class Ret extends Instruction {
		Arg src;
		public Ret(Arg src) {
			this.src = src;
		}
		@Override
		void compile() {
			if(src == null) {
				bytes = new byte[] {(byte) 0xC3};
			}
		}
		
	}
	
	Register RAX = new Register(0, 64);
	Register EAX = new Register(0, 32);
	Instruction[] mov(Register r, long val) {
		return new Instruction[] {new Mov(r, new Immediate(val))};
	}
	Instruction[] add(Register r, long val) {
		return new Instruction[] {new BinOp(0, r, new Immediate(val))};
	}
	Instruction[] sub(Register r, long val) {
		return new Instruction[] {new BinOp(5, r, new Immediate(val))};
	}
	Instruction[] ret() {
		return new Instruction[] {new Ret(null)};
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
		Instruction[][] instrs = {
			mov(RAX, 5),
			sub(RAX, 5),
			ret()
		};
		
		for(Instruction[] is : instrs)
			for(Instruction i : is) {
				i.compile();
				System.out.println(i.bytes);
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
			System.out.print("%02x ".formatted(b));
		System.out.println();
		for(Instruction[] is : instrs)
			for(Instruction i : is) {
				for(byte b : i.bytes)
					System.out.print("%02x ".formatted(b));
				System.out.println();
			}
		
		
		try (FileOutputStream fos = new FileOutputStream("compiled/code.bin")) {
			fos.write(bytes);
		} catch (IOException e) {e.printStackTrace();}
		
		for(int repeat = 0; repeat < 1; repeat++) {
			long start = System.nanoTime();
			try {
//				Runtime.getRuntime().exec("C:\\Users\\kgeijsen\\Desktop\\C++ workspace\\HexRun\\x64\\Debug\\HexRun.exe");
//				Process process = new ProcessBuilder("C:\\Users\\kgeijsen\\Desktop\\C++ workspace\\HexRun\\x64\\Debug\\HexRun.exe").start();
				ProcessBuilder process = new ProcessBuilder("C:\\Users\\kgeijsen\\Desktop\\C++ workspace\\HexRun\\x64\\Release\\HexRun.exe");
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
