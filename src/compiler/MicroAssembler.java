package compiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Scanner;

import compiler.Lexer.Block;
import compiler.Lexer.CurlyBracketParse;
import compiler.NaiveTypechecker.Function;

public class MicroAssembler {
	
	class Arg {
		Integer size = null;
		
	}
	class Register extends Arg {
		int reg;
		Register(int reg, int size){
			this.reg = reg;
			this.size = (Integer) size;
		}
		@Override
		public String toString() {
			return "%"+"rax rcx rdx rbx rsp rbp rsi rdi r8 r9 r10 r11 r12 r14 r15".split(" ")[reg];
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
		public Address(Register base, int offset, int size) {
			this(base, null, 0, offset, size);
		}
		public Address(Register base, int size) {
			this(base, null, 0, 0, size);
		}
		@Override
		public String toString() {
//			StringBuilder sb = new StringBuilder();
			ArrayList<String> args = new ArrayList<>();
			if(base != null)
				args.add(base+"");
			if(index != null)
				if(scale == 1) args.add(index+"");
				else args.add(index+"*"+scale);
			if(offset != 0 || args.size() == 0)
				args.add(offset+"");
			return "["+String.join("+", args)+"]";
		}
	}
//	class Variable extends Arg {
//		String id;
//		Variable(String id, int size){
//			this.id = id;
//			this.size = size;
//		}
//	}
	class Immediate extends Arg {
		long val;
		public Immediate(long val) {
			this.val = val;
		}
		@Override
		public String toString() {
			return ""+val;
		}
	}
	class Label extends Arg {
		Block b;
		public Label(Block b) {
			this.b = b;
		}
		@Override
		public boolean equals(Object obj) {
			if(obj instanceof Label l)
				return l.b == b;
			else return false;
		}
		@Override
		public int hashCode() {
			return b.hashCode();
		}
	}
	
	abstract class Instruction {
		byte[] bytes;
		int relIP;
		InstructionBlock parent;
		
		abstract void assemble();
		void updateLabel(Map<Block, InstructionBlock> labelOffset) {};
		long getAddress() {
			if(parent == null) {
				return relIP;
			}
			return relIP + parent.getAddress();
		}
	}
	
//	class Goto extends Instruction {
//		
//	}
	class InstructionBlock extends Instruction {
		
		ArrayList<Instruction> instructions;
		String name;
		
		public InstructionBlock(InstructionBlock parent, String name, Instruction... instructions) {
			this.name = name;
			this.parent = parent;
			this.instructions = new ArrayList<Instruction>(Arrays.asList(instructions));
		}
		
		@Override
		void assemble() {

			ByteBuffer bb = ByteBuffer.allocate(256);
			int byteOffset = 0;
			for(Instruction i : instructions) {
				i.parent = this;
				i.assemble();
//				System.out.println(i.getClass());
				i.relIP = bb.position();
				bb.put(i.bytes);
				
//				i.relIP = byteOffset;
//				byteOffset += i.bytes.length;
			}
			bytes = Arrays.copyOf(bb.array(), bb.position());
		}
		
		
		@Override
		void updateLabel(Map<Block, InstructionBlock> labelOffset) {
			ByteBuffer bb = ByteBuffer.allocate(256);
			for(Instruction i : instructions) {
				i.updateLabel(labelOffset);
				bb.put(i.bytes);
			}
			bytes = Arrays.copyOf(bb.array(), bb.position());
		}
	
	
		InstructionBlock addBlock(String name, Instruction... instructions) {
			InstructionBlock ib = new InstructionBlock(this, name, instructions);
			this.instructions.add(ib);
			return ib;
		}
		InstructionBlock prolog() {
			return addBlock("prolog",
					new Push(RBP),
					new Mov(RBP, RSP)
					);
		}
		InstructionBlock epilog() {
			return addBlock("epilog",
					new Mov(RSP, RBP),
					new Pop(RBP)
					);
		}
		InstructionBlock allocStack(int bits) {
			return addBlock("allocStack",
					new BinOp(BinaryOperation.SUB.i, RSP, new Immediate(bits/8))
					);
		}
		InstructionBlock deallocStack(int bits) {
			return addBlock("deallocStack",
					new BinOp(BinaryOperation.ADD.i, RSP, new Immediate(bits/8))
					);
		}
		InstructionBlock argumentsToVariables(int parameters) {
			InstructionBlock  ib = addBlock("arguments");
			Arg[] params = new Arg[] {
					RCX,
					RDX,
					R8,
					R9,
				};
			for(int i = 0; i < parameters; i++) {
				ib.instructions.add(new Mov(new Address(RBP, -i*8-8, 64), params[i]));
			}
			return ib;
		}
		InstructionBlock popArguments(int arguments) {
			InstructionBlock ib = addBlock("popArguments");
			Arg[] params = new Arg[] {
					RCX,
					RDX,
					R8,
					R9,
				};
			for(int i = arguments-1; i >= 0; i--) {
				ib.instructions.add(new Pop(params[i]));
			}
			return ib;
		}
		void pushStackVariable(int bitOffset) {
			instructions.add(new Push(new Address(RBP, -bitOffset/8-8, 64)));
		}
		void pushLiteral(long value) {
			instructions.add(new Push(new Immediate(value)));
		}
		void pushRet() {
			instructions.add(new Push(RAX));
		}
		void callFunction(Function function) {
			System.out.println("Label define:  " + function.body);
//			System.out.println(function.body);
			instructions.add(new Call(new Label(function.body)));
		}
		InstructionBlock binOpStack(String op) {
			InstructionBlock ib;
			Map<String, Integer> op1 = Map.of("+", 0, "|", 1, "&", 4, "-", 5, "^", 6);
			if(op1.containsKey(op))
				ib= addBlock("binOp."+op,
						new Pop(RAX),
						new BinOp(op1.get(op), new Address(RSP, null, 0, 0, 64), RAX)
					);
			else if(op.equals("%"))
				ib= addBlock("binOp."+op,
						new Pop(RAX),
						new DirectBytes(new byte[] {0x48, (byte)0x99}, "cqo"), // cqo  (sign extends RAX into RAX:RDX)
						new DirectBytes(new byte[] {0x48, (byte)0xf7, 0x34, 0x24}, "div\t[rsp]"), // div [rsp]  (RAX,RDX = RAX:RDX / [RSP], RAX:RDX % [RSP])
						new Mov(new Address(RSP, 64), RDX)
					);
			else if(op.equals("/"))
				ib= addBlock("binOp."+op,
						new Pop(RAX),
						new DirectBytes(new byte[] {0x48, (byte)0x99}, "cqo"), // cqo  (sign extends RAX into RAX:RDX)
						new DirectBytes(new byte[] {0x48, (byte)0xf7, 0x34, 0x24}, "div\t[rsp]"), // div [rsp]  (RAX,RDX = RAX:RDX / [RSP], RAX:RDX % [RSP])
						new Mov(new Address(RSP, null, 0, 0, 64), RAX)
					);
			else throw new RuntimeException("Not implemented binop: " + op);
//			instructions.add(ib);
			return ib;
		}
		InstructionBlock unOpStack(String op) {
			InstructionBlock ib;
			if(op.equals("return"))
				ib= addBlock("unOp."+op,
						new Pop(RAX)
					);
			else throw new RuntimeException("Not implemented binop: " + op);
//			instructions.add(ib);
			return ib;
		}
		void ret() {
			instructions.add(new Ret());
			
		}
		void setStackVariable(int bitOffset) {
			instructions.add(new Pop(new Address(RBP, -bitOffset/8-8, 64)));
		}
	}
	
	
	
	class AddressLabel extends Instruction {


		int relIP;
		@Override
		void assemble() {
			bytes = new byte[0];
		}
		
	}
	class DirectBytes extends Instruction {

		final String toPrint;
		public DirectBytes(byte[] bytes, String toPrint) {
			this.bytes = bytes;
			this.toPrint = toPrint;
		}
		public DirectBytes(byte[] bytes) {
			this.bytes = bytes;
			this.toPrint = null;
		}
		@Override
		void assemble() {}
		
		@Override
		public String toString() {
			
			if(toPrint == null) {
				StringBuilder sb = new StringBuilder("bytes\t");
				for(byte b : bytes) {
					sb.append("%02x ".formatted(b));
				}
				return sb.toString();
			}
			return toPrint;
		}
	}
	class Mod extends Instruction {
		void assemble() {
			bytes = new byte[] { 0x48, (byte)0x99, 0x48, (byte)0xF7, (byte)0xFE, 0x48, (byte)0x89, (byte)0xD0 };
		}
	}
	class Mov extends Instruction {
		Arg src;
		Arg dst;
		public Mov(Arg dst, Arg src) {
			this.src = src;
			this.dst = dst;
		}
		@Override
		void assemble() {
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
		@Override
		public String toString() {
			return "mov\t"+dst+", " + src;
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
		void assemble() {
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
		@Override
		public String toString() {
			return "add or adc sbb and sub xor cmp".split(" ")[op]+"\t"+dst+", " + src;
		}
	}
	class Push extends Instruction {
		Arg src;
		public Push(Arg src) {
			this.src = src;
		}
		@Override
		void assemble() {
//			System.out.println(src);
			if(src instanceof Immediate i) {

				ByteBuffer bb = ByteBuffer.allocate(256);
				if(i.val == (long)(byte)i.val) {
					bb.put((byte)0x6a);
					bb.put((byte)i.val);
					
				} else if(i.val == (long)(int)i.val) {
					bb.put((byte)0x68);
					bb.putInt((int)i.val);
				} else
					throw new RuntimeException("Invalid operand: " + i);

				bytes = Arrays.copyOf(bb.array(), bb.position());
				
			} else if(src instanceof Register r) {
				bytes = new byte[] {(byte) (0x50 + r.reg)};
			} else {

				int opSize = src.size;
				ByteBuffer bb = ByteBuffer.allocate(256);
				bb.order(ByteOrder.LITTLE_ENDIAN);
				bb.put((byte)0xff);
				mr(bb, 6, src);
				
				bytes = Arrays.copyOf(bb.array(), bb.position());
			}
		}
		@Override
		public String toString() {
			return "push\t" + src;
		}
	}
	class Pop extends Instruction {
		Arg dst;
		public Pop(Arg dst) {
			this.dst = dst;
		}
		@Override
		void assemble() {
			if(dst instanceof Register r) {
				bytes = new byte[] {(byte) (0x58 + r.reg)};
			} else {

				int opSize = dst.size;
				ByteBuffer bb = ByteBuffer.allocate(256);
				bb.order(ByteOrder.LITTLE_ENDIAN);
				bb.put((byte)0x8f);
				mr(bb, 0, dst);
				
				bytes = Arrays.copyOf(bb.array(), bb.position());
			}
		}
		@Override
		public String toString() {
			return "pop\t"+dst;
		}
	}
	class Call extends Instruction {
		Arg fn;
		public Call(Arg fn) {
			this.fn = fn;
		}
		@Override
		void assemble() {
			if(fn instanceof Register r) {
				bytes = new byte[] {(byte) (0x50 + r.reg)};
			} else if(fn instanceof Label l) {
				bytes = new byte[] {(byte) 0xe8,0,0,0,0};
			}
		}
		
		
		@Override
		void updateLabel(Map<Block, InstructionBlock> labelOffset) {
			
			if(fn instanceof Label l) {
				System.out.println("label thing here: " + l.b);
				int value = (int) (labelOffset.get(l.b).getAddress()-getAddress()-bytes.length);
				bytes[1] = (byte)((value>>0)&0xff);
				bytes[2] = (byte)((value>>8)&0xff);
				bytes[3] = (byte)((value>>16)&0xff);
				bytes[4] = (byte)((value>>24)&0xff);
			}
			
		}
		
		@Override
		public String toString() {
			if(fn instanceof Label l)
				return "call\t%04x".formatted(getAddress() + bytes.length + (bytes[1] | bytes[2]<<8 | bytes[3]<<16 | bytes[4]<<24));
			return super.toString();
		}
	}
	class Ret extends Instruction {
		public Ret() {
		}
		@Override
		void assemble() {
			bytes = new byte[] {(byte) 0xC3};
		}
		@Override
		public String toString() {
			return "ret";
		}	
	}
	void mr(ByteBuffer bb, int r, Arg mr) {
		if(mr instanceof Register s) {
			bb.put((byte) (0xC0 + (r << 3) + s.reg));
		} else if(mr instanceof Address a) {
			if(a.base == RSP) {
				if(a.offset == 0 && a.base.reg != 5) {
					bb.put((byte) (0x00 + (r << 3) + a.base.reg));
					bb.put((byte)0x24);
				} else if(a.offset == (int)(byte)a.offset) {
					bb.put((byte) (0x40 + (r << 3) + a.base.reg));
					bb.put((byte)0x24);
					bb.put((byte) a.offset);
				}  else {
					bb.put((byte) (0x80 + (r << 3) + a.base.reg));
					bb.put((byte)0x24);
					bb.putInt(a.offset);
				}
			} else {
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
		
	}
	
	public Register RAX = new Register(0, 64);
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

	
	
	
	
	
//	Instruction[] prolog() {
//		return new Instruction[] {
//			new Push(RBP),
//			new Mov(RBP, RSP),
//		};
//	}
//	Instruction[] epilog() {
//		return new Instruction[] { 
//			new Mov(RSP, RBP),
//			new Pop(RBP)
//		};
//	}
//	Instruction[] alloc(StackVariable v) {
//		return new Instruction[] { new BinOp(5, RSP, new Immediate(v.size/8)) };
//	}
//	Instruction[] dealloc(StackVariable v) {
//		return new Instruction[] { new BinOp(0, RSP, new Immediate(v.size/8)) };
//	}
//	
//	Instruction[] mov(Register r, long val) {
//		return new Instruction[] {new Mov(r, new Immediate(val))};
//	}
//	Instruction[] mov(StackVariable v, long val) {
//		return new Instruction[] {new Mov(new Address(RBP, null, 0, -v.stackOffset-8, 64), new Immediate(val))};
//	}
//	Instruction[] mov(StackVariable v, Register r) {
//		return new Instruction[] {new Mov(new Address(RBP, null, 0, -v.stackOffset-8, 64), r)};
//	}
//	Instruction[] mov(Register r, StackVariable v) {
//		return new Instruction[] {new Mov(r, new Address(RBP, null, 0, -v.stackOffset-8, 64))};
//	}
//	Instruction[] mov(Register r, Address addr) {
//		return new Instruction[] {new Mov(r, addr)};
//	}
//	Instruction[] mov(Address addr, Register r) {
//		return new Instruction[] {new Mov(addr, r)};
//	}
//	Instruction[] mov(Register d, Register s) {
//		return new Instruction[] {new Mov(d, s)};
//	}
//	
//	Instruction[] binOp(int op, Arg a, long val) {
//		return new Instruction[] {new BinOp(op, a, new Immediate(val))};
//	}
//	Instruction[] binOp(int op, Arg a, Arg b) {
//		if(a instanceof StackVariable v)
//			a = new Address(RBP, null, 0, -v.stackOffset-8, 64);
//		if(b instanceof StackVariable v)
//			b = new Address(RBP, null, 0, -v.stackOffset-8, 64);
//		
//		
//		if(a instanceof Address a1 && b instanceof Address a2)
//			return new Instruction[] {new Mov(RAX, b), new BinOp(op, a, RAX)};
//		return new Instruction[] {new BinOp(op, a, b)};
//	}
//	Instruction[] binOpStack(String op) {
//		Map<String, Integer> op1 = Map.of("+", 0, "|", 1, "&", 4, "-", 5, "^", 6);
//		if(op1.containsKey(op))
//			return new Instruction[] {
//					new Pop(RAX),
//					new BinOp(op1.get(op), new Address(RSP, null, 0, 0, 64), RAX)
//				};
//		if(op.equals("%"))
//			return new Instruction[] {
//					new Pop(RAX),
//					new DirectBytes(new byte[] {0x48, (byte)0x99}, "cqo"), // cqo  (sign extends RAX into RAX:RDX)
//					new DirectBytes(new byte[] {0x48, (byte)0xf7, 0x34, 0x24}, "div\t[rsp]"), // div [rsp]  (RAX,RDX = RAX:RDX / [RSP], RAX:RDX % [RSP])
//					new Mov(new Address(RSP, null, 0, 0, 64), RDX)
//				};
//		if(op.equals("/"))
//			return new Instruction[] {
//					new Pop(RAX),
//					new DirectBytes(new byte[] {0x48, (byte)0x99}, "cqo"), // cqo  (sign extends RAX into RAX:RDX)
//					new DirectBytes(new byte[] {0x48, (byte)0xf7, 0x34, 0x24}, "div\t[rsp]"), // div [rsp]  (RAX,RDX = RAX:RDX / [RSP], RAX:RDX % [RSP])
//					new Mov(new Address(RSP, null, 0, 0, 64), RAX)
//				};
//		
//		throw new RuntimeException("Invalid operation: " + op);
//	}
//	
//	Instruction[] add(Arg a, long val) {
//		return binOp(0, a, val);
//	}
//	Instruction[] add(Arg a, Arg b) {
//		return binOp(0, a, b);
//	}
//	Instruction[] sub(Arg a, long val) {
//		return new Instruction[] {new BinOp(5, a, new Immediate(val))};
//	}
//	Instruction[] returnV(StackVariable v) {
//		return new Instruction[] {
//				new Mov(RAX, new Address(RBP, null, 0, -v.stackOffset-8, 64)),
//				new Mov(RSP, RBP),
//				new Pop(RBP),
//				new Ret()
//			};
//	}
//	Instruction[] returnV(Address a) {
//		return new Instruction[] {
//				new Mov(RAX, a),
//				new Mov(RSP, RBP),
//				new Pop(RBP),
//				new Ret()
//			};
//	}
//	Instruction[] ret(long val) {
//		return new Instruction[] {
//				new Mov(RAX, new Immediate(val)),
//				new Ret()
//		};
//	}
//	Instruction[] ret() {
//		return new Instruction[] {new Ret()};
//	}
	
	enum BinaryOperation {
		ADD(0),
		OR(1),
		ADC(2),
		SBB(3),
		AND(4),
		SUB(5),
		XOR(6),
		CMP(7),
		
		TEST(0),
		//  (1),
		NOT(2),
		NEG(3),
		MUL(4),
		IMUL(5),
		DIV(6),
		IDIV(7),
		
		ROL(0),
		ROR(1),
		RCL(2),
		RCR(3),
		SHL(4),
		SHR(5),
		SAL(6),
		SAR(7),

		M_MUL(0),
		M_DIV(0),
		M_MOD(0);
		
		int i = 0;
		private BinaryOperation(int i) {
			this.i = i;
		}
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
	MicroAssembler(){
//		StackVariable varA = new StackVariable("a", 64, 0);
//		StackVariable varB = new StackVariable("b", 64, 8);
//		Instruction[][] instrs = {
//				prolog(),
//				alloc(varA),
//				alloc(varB),
//				mov(varA, 5),
//				mov(varB, 10),
//				add(varA, varB),
//				returnV(varA)
//		};
		
//		for(Instruction[] is : instrs)
//			for(Instruction i : is) {
//				i.compile();
//			}
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

//		byte[] bytes = toArr("488D1D0000000048B900E40B5402000000488B034883C30048FFC975F4C3         ");
//		byte[] bytes = toArr("488D1D0000000048B900E40B5402000000488D15080000004839D3730D488B034883C30048FFC975EFC3E800000000            ");
//		byte[] bytes = toArr("488D1D0000000048C7C100CA9A3B48C7C7A086010048C7C664000000488D1508000000488D46FF4821F8488B04C34883C30048FFC975ECC3E800000000");
//		byte[] bytes = toArr("488D1D0000000048C7C100CA9A3B48C7C7A086010048C7C664000000488D15080000004889F8489948F7FE4889D0488B04C34883C30048FFC975E8C3E800000000");
		
		// Mod
		byte[] bytes = toArr("4889C84889D6489948F7FE4889D0C3"); // slow
//		byte[] bytes = toArr("488D41FF4821D0C3"); // fast
		
		// Indexing
//		byte[] bytes = toArr("4839D77D05488B04FEC3E800000000"); // slow
//		byte[] bytes = toArr("488B04FEC3   "); // fast
		
		// Indexing fat
//		byte[] bytes = toArr("483B0A7D06488B44CA08C3E800000000"); // slow
//		byte[] bytes = toArr("488B44CA08C3"); // fast
		
		
//		for(byte b : bytes)
//			System.out.print(Integer.toHexString((b&255)|0x100).substring(1) + " ");
//		System.out.println();
//		System.out.println();
//		for(Instruction[] is : instrs)
//			for(Instruction i : is) {
//				for(byte b : i.bytes)
//					System.out.print(Integer.toHexString((b&255)|0x100).substring(1) + " ");
//				System.out.println();
//			}
		assemble(bytes);
		
	}
	
	void assemble(byte[] bytes) {
		
		
		try (FileOutputStream fos = new FileOutputStream("compiled\\code.hexe")) {
			fos.write(bytes);
			System.out.println("written");
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
//	public static void main(String[] args) throws IOException {
//		String fileContent = new String(Files.readAllBytes(inputFile.toPath())) + " ";
//		Lexer lexer = new Lexer();
//		NaiveParser parser = new NaiveParser();
//		
//		System.out.println("Input:\n" + fileContent + "\n===========================================\n");
//		CurlyBracketParse b = lexer.parse(fileContent);
//		System.out.println("Lexed:\n" + b.toParseString()+"\n===========================================\n");
//		b = (CurlyBracketParse) parser.parse(b);
//		
//		System.out.println("Parsed:\n" + b.toParseString()+"\n===========================================\n");
//		
//		new MicroAssembler();
//	}
}
