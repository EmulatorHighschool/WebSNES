package snes;

import java.util.Scanner;

public class Z80 
{
	int A, B, C, D, E, H, L, SP, PC;
	int FLAG_C, FLAG_N, FLAG_P, FLAG_H, FLAG_Z, FLAG_S;
	int FLAG_I, interrupt_deferred, interrupts;
	int FLAGS;
	boolean halted;
	
	int opcode,value,imm;
	String instruction;
	int cycles;
	
	SNES snes;
	
	public Z80(SNES snes)
	{
		this.snes=snes;
		reset();
	}
	
	public void reset()
	{
		interrupts=0;
		interrupt_deferred=0;
		PC=0x100;
		A=1; B=0; C=0x13; D=0; E=0xd8; H=1; L=0x4d; SP=0xfffe;
		FLAG_Z=FLAG_H=FLAG_C=1;
		FLAG_N=0;
		FLAG_S=FLAG_P=0;
		FLAG_I=0;
		setFlags();
		halted=false;
	}
	
	public void doInstruction()
	{
		opcode=fetch()&0xff;
		final String instruction;
		final int cyc;
		
		switch(opcode)
		{
		case 0x00: instruction="nop"; cyc=4; break;
		case 0x01: instruction="ld bc imm16"; cyc=12;
			C=fetch()&0xff; B=fetch()&0xff;
			break;
		case 0x02: instruction="ld (bc) a"; cyc=8;
			memory_write((B<<8)|C, (byte)A);
			break;
		case 0x03: instruction="inc bc"; cyc=8;
			value=(((B<<8)|C)+1)&0xffff;
			B=value>>8; C=value&0xff;
			break;
		case 0x04: instruction="inc b"; cyc=4;
			B=incrementDecrement(B,false);
			break;
		case 0x05: instruction="dec b"; cyc=4;
			B=incrementDecrement(B,true);
			break;
		case 0x06: instruction="ld b imm8"; cyc=8;
			B=fetch()&0xff;
			break;
		case 0x07: instruction="rlc a"; cyc=4; A=rlc(A,false); break;
		case 0x08: cyc=20;
			instruction="ld (imm16) sp"; 
			imm=(fetch()&0xff)|((fetch()&0xff)<<8);
//			value=(memory_read(imm)&0xff)|((memory_read((imm+1)&0xffff)&0xff)<<8);
			memory_write(imm,(byte)(SP&0xff));
			memory_write((imm+1)&0xffff,(byte)(SP>>8));
			break;
		case 0x09: instruction="add hl bc"; cyc=8;
			value=arithmetic16((H<<8)|L,(B<<8)|C,false,false);
			H=value>>8; L=value&0xff;
			break;
		case 0x0a: instruction="ld a (bc)"; cyc=8;
			A=memory_read((B<<8)|C)&0xff;
			break;
		case 0x0b: instruction="dec bc"; cyc=8;
			value=(((B<<8)|C)-1)&0xffff;
			B=value>>8; C=value&0xff;
			break;
		case 0x0c: instruction="inc c"; cyc=4;
			C=incrementDecrement(C,false);
			break;
		case 0x0d: instruction="dec c"; cyc=4;
			C=incrementDecrement(C,true);
			break;
		case 0x0e: instruction="ld c imm8"; cyc=8;
			C=fetch()&0xff;
			break;
		case 0x0f: instruction="rrc a"; cyc=4; A=rrc(A,false); break;
		case 0x10: instruction="stop"; fault(0x10); cyc=4; break;
		case 0x11: instruction="ld de imm16"; cyc=12;
			E=fetch()&0xff; D=fetch()&0xff;
			break;
		case 0x12: instruction="ld (de) a"; cyc=8;
			memory_write((D<<8)|E, (byte)A);
			break;
		case 0x13: instruction="inc de"; cyc=8;
			value=(((D<<8)|E)+1)&0xffff;
			D=value>>8; E=value&0xff;
			break;
		case 0x14: instruction="inc d"; cyc=4;
			D=incrementDecrement(D,false);
			break;
		case 0x15: instruction="dec d"; cyc=4;
			D=incrementDecrement(D,true);
			break;
		case 0x16: instruction="ld d imm8"; cyc=8;
			D=fetch()&0xff;
			break;
		case 0x17: instruction="rl a"; cyc=4; A=rl(A,false); break;
		case 0x18: instruction="jr imm8"; cyc=12;
			imm=fetch()&0xff;
			imm = (imm&0xff)>=0x80? imm|0xff00 : imm&0xff;
			PC=(PC+imm)&0xffff;
			break;
		case 0x19: instruction="add hl de"; cyc=8;
			value=arithmetic16((H<<8)|L,(D<<8)|E,false,false);
			H=value>>8; L=value&0xff;
			break;
		case 0x1a: instruction="ld a (de)"; cyc=8;
			A=memory_read((D<<8)|E)&0xff;
			break;
		case 0x1b: instruction="dec de"; cyc=8;
			value=(((D<<8)|E)-1)&0xffff;
			D=value>>8; E=value&0xff;
			break;
		case 0x1c: instruction="inc e"; cyc=4;
			E=incrementDecrement(E,false);
			break;
		case 0x1d: instruction="dec e"; cyc=4;
			E=incrementDecrement(E,true);
			break;
		case 0x1e: instruction="ld e imm8"; cyc=8;
			E=fetch()&0xff;
			break;
		case 0x1f: instruction="rr a"; cyc=4; A=rr(A,false); break;
		case 0x20: instruction="jr nz imm8"; 
			imm=fetch()&0xff;
			if(FLAG_Z==0)
			{
				imm = (imm&0xff)>=0x80? imm|0xff00 : imm&0xff;
				PC=(PC+imm)&0xffff;
				cyc=12;
			}
			else
				cyc=8;
			break;
		case 0x21: instruction="ld hl imm16"; cyc=12; 
			L=fetch()&0xff; H=fetch()&0xff;
			break;
		case 0x22: 
			instruction="ldi (hl) a"; cyc=8;
			value=(H<<8)|L;
			memory_write(value,(byte)A);
			value=(value+1)&0xffff;
			L=value&0xff; H=value>>8;
			break;
		case 0x23: instruction="inc hl"; cyc=8; 
			value=(((H<<8)|L)+1)&0xffff;
			H=value>>8; L=value&0xff;
			break;
		case 0x24: instruction="inc h"; cyc=4;
			H=incrementDecrement(H,false);
			break;
		case 0x25: instruction="dec h"; cyc=4;
			H=incrementDecrement(H,true);
			break;
		case 0x26: instruction="ld h imm8"; cyc=8;
			H=fetch()&0xff;
			break;
		case 0x27: instruction="daa"; cyc=4; daa(); break;
		case 0x28: instruction="jr z imm8"; 
			imm=fetch()&0xff;
			if(FLAG_Z!=0)
			{
				imm = (imm&0xff)>=0x80? imm|0xff00 : imm&0xff;
				PC=(PC+imm)&0xffff;
				cyc=12;
			}
			else
				cyc=8;
			break;
		case 0x29: instruction="add hl hl"; cyc=8;
			value=arithmetic16((H<<8)|L,(H<<8)|L,false,false);
			H=value>>8; L=value&0xff;
			break;
		case 0x2a:
			instruction="ldi a (hl)"; cyc=8;
			value=(H<<8)|L;
			A=memory_read(value)&0xff;
			value=(value+1)&0xffff;
			L=value&0xff; H=value>>8;
			break;
		case 0x2b: instruction="dec hl"; cyc=8; 
			value=(((H<<8)|L)-1)&0xffff;
			H=value>>8; L=value&0xff;
			break;
		case 0x2c: instruction="inc l"; cyc=4;
			L=incrementDecrement(L,false);
			break;
		case 0x2d: instruction="dec l"; cyc=4;
			L=incrementDecrement(L,true);
			break;
		case 0x2e: instruction="ld l imm8"; cyc=8;
			L=fetch()&0xff;
			break;
		case 0x2f: instruction="cpl"; cyc=4;
			A=(~A)&0xff;
			FLAG_N=FLAG_H=1;
			break;
		case 0x30: instruction="jr nc imm8"; 
			imm=fetch()&0xff;
			if(FLAG_C==0)
			{
				imm = (imm&0xff)>=0x80? imm|0xff00 : imm&0xff;
				PC=(PC+imm)&0xffff;
				cyc=12;
			}
			else
				cyc=8;
			break;
		case 0x31: instruction="ld sp imm16"; cyc=12; 
			SP=(fetch()&0xff)|((fetch()&0xff)<<8);
			break;
		case 0x32: 
			instruction="ldd (hl) a"; cyc=8;
			value=(H<<8)|L;
			memory_write(value,(byte)A);
			value=(value-1)&0xffff;
			L=value&0xff; H=value>>8;
			break;
		case 0x33: instruction="inc sp"; cyc=8; SP=(SP+1)&0xffff; break;
		case 0x34: instruction="inc (hl)"; cyc=12;
			memory_write((H<<8)|L,(byte)incrementDecrement(memory_read((H<<8)|L),false));
			break;
		case 0x35: instruction="dec (hl)"; cyc=12;
			memory_write((H<<8)|L,(byte)incrementDecrement(memory_read((H<<8)|L),true));
			break;
		case 0x36: instruction="ld (hl) imm8"; cyc=12;
			imm=fetch()&0xff;
			memory_write((H<<8|L),(byte)imm);
			break;
		case 0x37: instruction="scf"; cyc=4;
			FLAG_C=1; FLAG_N=0; FLAG_H=0;
			break;
		case 0x38: instruction="jr c imm8"; 
			imm=fetch()&0xff;
			if(FLAG_C!=0)
			{
				imm = (imm&0xff)>=0x80? imm|0xff00 : imm&0xff;
				PC=(PC+imm)&0xffff;
				cyc=12;
			}
			else
				cyc=8;
			break;
		case 0x39: instruction="add hl sp"; cyc=8; 
			value=arithmetic16((H<<8)|L,SP,false,false);
			H=value>>8; L=value&0xff;
			break;
		case 0x3a:
			instruction="ldd a (hl)"; cyc=8;
			value=(H<<8)|L;
			A=memory_read(value)&0xff;
			value=(value-1)&0xffff;
			L=value&0xff; H=value>>8;
			break;
		case 0x3b: instruction="dec sp"; cyc=8; SP=(SP-1)&0xffff; break;
		case 0x3c: instruction="inc a"; cyc=4;
			A=incrementDecrement(A,false);
			break;
		case 0x3d: instruction="dec a"; cyc=4;
			A=incrementDecrement(A,true);
			break;
		case 0x3e: instruction="ld a imm8"; cyc=8;
			A=fetch()&0xff;
			break;
		case 0x3f: instruction="ccf"; cyc=4;
			FLAG_C=1-FLAG_C; FLAG_N=0; FLAG_H=0;
			break;
		case 0x40: instruction="ld b b"; cyc=4; break;
		case 0x41: instruction="ld b c"; cyc=4; B=C; break;
		case 0x42: instruction="ld b d"; cyc=4; B=D; break;
		case 0x43: instruction="ld b e"; cyc=4; B=E; break;
		case 0x44: instruction="ld b h"; cyc=4; B=H; break;
		case 0x45: instruction="ld b l"; cyc=4; B=L; break;
		case 0x46: instruction="ld b (hl)"; cyc=8; B=memory_read((H<<8)|L)&0xff; break;
		case 0x47: instruction="ld b a"; cyc=4; B=A; break;
		case 0x48: instruction="ld c b"; cyc=4; C=B; break;
		case 0x49: instruction="ld c c"; cyc=4; break;
		case 0x4a: instruction="ld c d"; cyc=4; C=D; break;
		case 0x4b: instruction="ld c e"; cyc=4; C=E; break;
		case 0x4c: instruction="ld c h"; cyc=4; C=H; break;
		case 0x4d: instruction="ld c l"; cyc=4; C=L; break;
		case 0x4e: instruction="ld c (hl)"; cyc=8; C=memory_read((H<<8)|L)&0xff; break;
		case 0x4f: instruction="ld c a"; cyc=4; C=A; break;
		case 0x50: instruction="ld d b"; cyc=4; D=B; break;
		case 0x51: instruction="ld d c"; cyc=4; D=C; break;
		case 0x52: instruction="ld d d"; cyc=4; break;
		case 0x53: instruction="ld d e"; cyc=4; D=E; break;
		case 0x54: instruction="ld d h"; cyc=4; D=H; break;
		case 0x55: instruction="ld d l"; cyc=4; D=L; break;
		case 0x56: instruction="ld d (hl)"; cyc=8; D=memory_read((H<<8)|L)&0xff; break;
		case 0x57: instruction="ld d a"; cyc=4; D=A; break;
		case 0x58: instruction="ld e b"; cyc=4; E=B; break;
		case 0x59: instruction="ld e c"; cyc=4; E=C; break;
		case 0x5a: instruction="ld e d"; cyc=4; E=D; break;
		case 0x5b: instruction="ld e e"; cyc=4; break;
		case 0x5c: instruction="ld e h"; cyc=4; E=H; break;
		case 0x5d: instruction="ld e l"; cyc=4; E=L; break;
		case 0x5e: instruction="ld e (hl)"; cyc=8; E=memory_read((H<<8)|L)&0xff; break;
		case 0x5f: instruction="ld e a"; cyc=4; E=A; break;
		case 0x60: instruction="ld h b"; cyc=4; H=B; break;
		case 0x61: instruction="ld h c"; cyc=4; H=C; break;
		case 0x62: instruction="ld h d"; cyc=4; H=D; break;
		case 0x63: instruction="ld h e"; cyc=4; H=E; break;
		case 0x64: instruction="ld h h"; cyc=4; break;
		case 0x65: instruction="ld h l"; cyc=4; H=L; break;
		case 0x66: instruction="ld h (hl)"; cyc=8; H=memory_read((H<<8)|L)&0xff; break;
		case 0x67: instruction="ld h a"; cyc=4; H=A; break;
		case 0x68: instruction="ld l b"; cyc=4; L=B; break;
		case 0x69: instruction="ld l c"; cyc=4; L=C; break;
		case 0x6a: instruction="ld l d"; cyc=4; L=D; break;
		case 0x6b: instruction="ld l e"; cyc=4; L=E; break;
		case 0x6c: instruction="ld l h"; cyc=4; L=H; break;
		case 0x6d: instruction="ld l l"; cyc=4; break;
		case 0x6e: instruction="ld l (hl)"; cyc=8; L=memory_read((H<<8)|L)&0xff; break;
		case 0x6f: instruction="ld l a"; cyc=4; L=A; break;
		case 0x70: instruction="ld (hl) b"; cyc=7; memory_write((H<<8)|L,(byte)B); break;
		case 0x71: instruction="ld (hl) c"; cyc=7; memory_write((H<<8)|L,(byte)C); break;
		case 0x72: instruction="ld (hl) d"; cyc=7; memory_write((H<<8)|L,(byte)D); break;
		case 0x73: instruction="ld (hl) e"; cyc=7; memory_write((H<<8)|L,(byte)E); break;
		case 0x74: instruction="ld (hl) h"; cyc=7; memory_write((H<<8)|L,(byte)H); break;
		case 0x75: instruction="ld (hl) l"; cyc=7; memory_write((H<<8)|L,(byte)L); break;
		case 0x76: instruction="halt"; cyc=4; halt(); break;
		case 0x77: instruction="ld (hl) a"; cyc=8; memory_write((H<<8)|L,(byte)A); break;
		case 0x78: instruction="ld a b"; cyc=4; A=B; break;
		case 0x79: instruction="ld a c"; cyc=4; A=C; break;
		case 0x7a: instruction="ld a d"; cyc=4; A=D; break;
		case 0x7b: instruction="ld a e"; cyc=4; A=E; break;
		case 0x7c: instruction="ld a h"; cyc=4; A=H; break;
		case 0x7d: instruction="ld a l"; cyc=4; A=L; break;
		case 0x7e: instruction="ld a (hl)"; cyc=8; A=memory_read((H<<8)|L)&0xff; break;
		case 0x7f: instruction="ld a a"; cyc=4; break;
		case 0x80: instruction="add b"; cyc=4; A=arithmetic(B,false,false); break;
		case 0x81: instruction="add c"; cyc=4; A=arithmetic(C,false,false); break;
		case 0x82: instruction="add d"; cyc=4; A=arithmetic(D,false,false); break;
		case 0x83: instruction="add e"; cyc=4; A=arithmetic(E,false,false); break;
		case 0x84: instruction="add h"; cyc=4; A=arithmetic(H,false,false); break;
		case 0x85: instruction="add l"; cyc=4; A=arithmetic(L,false,false); break;
		case 0x86: instruction="add (hl)"; cyc=8; A=arithmetic(memory_read((H<<8)|L)&0xff,false,false); break;
		case 0x87: instruction="add a"; cyc=4; A=arithmetic(A,false,false); break;
		case 0x88: instruction="adc b"; cyc=4; A=arithmetic(B,false,true); break;
		case 0x89: instruction="adc c"; cyc=4; A=arithmetic(C,false,true); break;
		case 0x8a: instruction="adc d"; cyc=4; A=arithmetic(D,false,true); break;
		case 0x8b: instruction="adc e"; cyc=4; A=arithmetic(E,false,true); break;
		case 0x8c: instruction="adc h"; cyc=4; A=arithmetic(H,false,true); break;
		case 0x8d: instruction="adc l"; cyc=4; A=arithmetic(L,false,true); break;
		case 0x8e: instruction="adc (hl)"; cyc=8; A=arithmetic(memory_read((H<<8)|L)&0xff,false,true); break;
		case 0x8f: instruction="adc a"; cyc=4; A=arithmetic(A,false,true); break;
		case 0x90: instruction="sub b"; cyc=4; A=arithmetic(B,true,false); break;
		case 0x91: instruction="sub c"; cyc=4; A=arithmetic(C,true,false); break;
		case 0x92: instruction="sub d"; cyc=4; A=arithmetic(D,true,false); break;
		case 0x93: instruction="sub e"; cyc=4; A=arithmetic(E,true,false); break;
		case 0x94: instruction="sub h"; cyc=4; A=arithmetic(H,true,false); break;
		case 0x95: instruction="sub l"; cyc=4; A=arithmetic(L,true,false); break;
		case 0x96: instruction="sub (hl)"; cyc=8; A=arithmetic(memory_read((H<<8)|L)&0xff,true,false); break;
		case 0x97: instruction="sub a"; cyc=4; A=arithmetic(A,true,false); break;
		case 0x98: instruction="sbc b"; cyc=4; A=arithmetic(B,true,true); break;
		case 0x99: instruction="sbc c"; cyc=4; A=arithmetic(C,true,true); break;
		case 0x9a: instruction="sbc d"; cyc=4; A=arithmetic(D,true,true); break;
		case 0x9b: instruction="sbc e"; cyc=4; A=arithmetic(E,true,true); break;
		case 0x9c: instruction="sbc h"; cyc=4; A=arithmetic(H,true,true); break;
		case 0x9d: instruction="sbc l"; cyc=4; A=arithmetic(L,true,true); break;
		case 0x9e: instruction="sbc (hl)"; cyc=8; A=arithmetic(memory_read((H<<8|L))&0xff,true,true); break;
		case 0x9f: instruction="sbc a"; cyc=4; A=arithmetic(A,true,true); break;
		case 0xa0: instruction="and b"; A&=B; cyc=4; logicflags(1); break;
		case 0xa1: instruction="and c"; A&=C; cyc=4; logicflags(1); break;
		case 0xa2: instruction="and d"; A&=D; cyc=4; logicflags(1); break;
		case 0xa3: instruction="and e"; A&=E; cyc=4; logicflags(1); break;
		case 0xa4: instruction="and h"; A&=H; cyc=4; logicflags(1); break;
		case 0xa5: instruction="and l"; A&=L; cyc=4; logicflags(1); break;
		case 0xa6: instruction="and (hl)"; cyc=8; A&=(memory_read((H<<8)|L)&0xff); logicflags(1); break;
		case 0xa7: instruction="and a"; cyc=4; A&=A; logicflags(1); break;
		case 0xa8: instruction="xor b"; cyc=4; A^=B; logicflags(0); break;
		case 0xa9: instruction="xor c"; cyc=4; A^=C; logicflags(0); break;
		case 0xaa: instruction="xor d"; cyc=4; A^=D; logicflags(0); break;
		case 0xab: instruction="xor e"; cyc=4; A^=E; logicflags(0); break;
		case 0xac: instruction="xor h"; cyc=4; A^=H; logicflags(0); break;
		case 0xad: instruction="xor l"; cyc=4; A^=L; logicflags(0); break;
		case 0xae: instruction="xor (hl)"; cyc=8; A^=(memory_read((H<<8)|L)&0xff); logicflags(0); break;
		case 0xaf: instruction="xor a"; cyc=4; A^=A; logicflags(0); break;
		case 0xb0: instruction="or b"; cyc=4; A|=B; logicflags(0); break;
		case 0xb1: instruction="or c"; cyc=4; A|=C; logicflags(0); break;
		case 0xb2: instruction="or d"; cyc=4; A|=D; logicflags(0); break;
		case 0xb3: instruction="or e"; cyc=4; A|=E; logicflags(0); break;
		case 0xb4: instruction="or h"; cyc=4; A|=H; logicflags(0); break;
		case 0xb5: instruction="or l"; cyc=4; A|=L; logicflags(0); break;
		case 0xb6: instruction="or (hl)"; cyc=8; A|=(memory_read((H<<8)|L)&0xff); logicflags(0); break;
		case 0xb7: instruction="or a"; cyc=4; A|=A; logicflags(0); break;
		case 0xb8: instruction="cp b"; cyc=4; arithmetic(B,true,false); break;
		case 0xb9: instruction="cp c"; cyc=4; arithmetic(C,true,false); break;
		case 0xba: instruction="cp d"; cyc=4; arithmetic(D,true,false); break;
		case 0xbb: instruction="cp e"; cyc=4; arithmetic(E,true,false); break;
		case 0xbc: instruction="cp h"; cyc=4; arithmetic(H,true,false); break;
		case 0xbd: instruction="cp l"; cyc=4; arithmetic(L,true,false); break;
		case 0xbe: instruction="cp (hl)"; cyc=8; arithmetic(memory_read((H<<8)|L)&0xff,true,false); break;
		case 0xbf: instruction="cp a"; cyc=4; arithmetic(A,true,false); break;
		case 0xc0: instruction="ret nz";
			if(FLAG_Z==0)
			{
				cyc=20;
				PC=pop();
			}
			else
				cyc=8;
			break;
		case 0xc1: instruction="pop bc"; cyc=12; 
			value=pop();
			B=value>>8; C=value&0xff;
			break;
		case 0xc2: instruction="jp nz imm16";
			imm=(fetch()&0xff)|((fetch()&0xff)<<8);
			if(FLAG_Z==0)
			{
				cyc=16;
				PC=imm;
			}
			else
				cyc=12;
			break;
		case 0xc3: instruction="jp imm16"; cyc=16;
			PC=(fetch()&0xff)|((fetch()&0xff)<<8);
			break;
		case 0xc4: instruction="call nz imm16"; 
			imm=(fetch()&0xff)|((fetch()&0xff)<<8);
			if(FLAG_Z==0)
			{
				cyc=24;
				push(PC);
				PC=imm;
			}
			else
				cyc=12;
			break;
		case 0xc5: instruction="push bc"; cyc=16; push((B<<8)|C); break;
		case 0xc6: instruction="add imm8"; cyc=8;
			A=arithmetic(fetch()&0xff,false,false);
			break;
		case 0xc7: instruction="rst 0"; cyc=16; push(PC); PC=0; break; //FLAG_I=0; System.out.println("rst 0"); break;
		case 0xc8: instruction="ret z";
			if(FLAG_Z!=0)
			{
				cyc=20;
				PC=pop();
			}
			else
				cyc=8;
			break;
		case 0xc9: instruction="ret";
			cyc=16;
			PC=pop();
			break;
		case 0xca: instruction="jp z imm16"; 
			imm=(fetch()&0xff)|((fetch()&0xff)<<8);
			if(FLAG_Z!=0)
			{
				cyc=16;
				PC=imm;
			}
			else
				cyc=12;
			break;
		case 0xcb: 
			opcode=(opcode<<8)|(fetch()&0xff);
			switch(opcode)
			{
			case 0xcb00: instruction="rlc b"; cyc=8; B=rlc(B,true); break;
			case 0xcb01: instruction="rlc c"; cyc=8; C=rlc(C,true); break;
			case 0xcb02: instruction="rlc d"; cyc=8; D=rlc(D,true); break;
			case 0xcb03: instruction="rlc e"; cyc=8; E=rlc(E,true); break;
			case 0xcb04: instruction="rlc h"; cyc=8; H=rlc(H,true); break;
			case 0xcb05: instruction="rlc l"; cyc=8; L=rlc(L,true); break;
			case 0xcb06: instruction="rlc (hl)"; cyc=16; memory_write((H<<8)|L,(byte)rlc(memory_read((H<<8)|L)&0xff,true)); break;
			case 0xcb07: instruction="rlc a"; cyc=8; A=rlc(A,true); break;
			case 0xcb08: instruction="rrc b"; cyc=8; B=rrc(B,true); break;
			case 0xcb09: instruction="rrc c"; cyc=8; C=rrc(C,true); break;
			case 0xcb0a: instruction="rrc d"; cyc=8; D=rrc(D,true); break;
			case 0xcb0b: instruction="rrc e"; cyc=8; E=rrc(E,true); break;
			case 0xcb0c: instruction="rrc h"; cyc=8; H=rrc(H,true); break;
			case 0xcb0d: instruction="rrc l"; cyc=8; L=rrc(L,true); break;
			case 0xcb0e: instruction="rrc (hl)"; cyc=16; memory_write((H<<8)|L,(byte)rrc(memory_read((H<<8)|L)&0xff,true)); break;
			case 0xcb0f: instruction="rrc a"; cyc=8; A=rrc(A,true); break;
			case 0xcb10: instruction="rl b"; cyc=8; B=rl(B,true); break;
			case 0xcb11: instruction="rl c"; cyc=8; C=rl(C,true); break;
			case 0xcb12: instruction="rl d"; cyc=8; D=rl(D,true); break;
			case 0xcb13: instruction="rl e"; cyc=8; E=rl(E,true); break;
			case 0xcb14: instruction="rl h"; cyc=8; H=rl(H,true); break;
			case 0xcb15: instruction="rl l"; cyc=8; L=rl(L,true); break;
			case 0xcb16: instruction="rl (hl)"; cyc=16; memory_write((H<<8)|L,(byte)rl(memory_read((H<<8)|L)&0xff,true)); break;
			case 0xcb17: instruction="rl a"; cyc=8; A=rl(A,true); break;
			case 0xcb18: instruction="rr b"; cyc=8; B=rr(B,true); break;
			case 0xcb19: instruction="rr c"; cyc=8; C=rr(C,true); break;
			case 0xcb1a: instruction="rr d"; cyc=8; D=rr(D,true); break;
			case 0xcb1b: instruction="rr e"; cyc=8; E=rr(E,true); break;
			case 0xcb1c: instruction="rr h"; cyc=8; H=rr(H,true); break;
			case 0xcb1d: instruction="rr l"; cyc=8; L=rr(L,true); break;
			case 0xcb1e: instruction="rr (hl)"; cyc=16; memory_write((H<<8)|L,(byte)rr(memory_read((H<<8)|L)&0xff,true)); break;
			case 0xcb1f: instruction="rr a"; cyc=8; A=rr(A,true); break;
			case 0xcb20: instruction="sla b"; cyc=8; B=sl(B,true); break;
			case 0xcb21: instruction="sla c"; cyc=8; C=sl(C,true); break;
			case 0xcb22: instruction="sla d"; cyc=8; D=sl(D,true); break;
			case 0xcb23: instruction="sla e"; cyc=8; E=sl(E,true); break;
			case 0xcb24: instruction="sla h"; cyc=8; H=sl(H,true); break;
			case 0xcb25: instruction="sla l"; cyc=8; L=sl(L,true); break;
			case 0xcb26: instruction="sla (hl)"; cyc=16; memory_write((H<<8)|L,(byte)sl(memory_read((H<<8)|L)&0xff,true)); break;
			case 0xcb27: instruction="sla a"; cyc=8; A=sl(A,true); break;
			case 0xcb28: instruction="sra b"; cyc=8; B=sr(B,true); break;
			case 0xcb29: instruction="sra c"; cyc=8; C=sr(C,true); break;
			case 0xcb2a: instruction="sra d"; cyc=8; D=sr(D,true); break;
			case 0xcb2b: instruction="sra e"; cyc=8; E=sr(E,true); break;
			case 0xcb2c: instruction="sra h"; cyc=8; H=sr(H,true); break;
			case 0xcb2d: instruction="sra l"; cyc=8; L=sr(L,true); break;
			case 0xcb2e: instruction="sra (hl)"; cyc=16; memory_write((H<<8)|L,(byte)sr(memory_read((H<<8)|L)&0xff,true)); break;
			case 0xcb2f: instruction="sra a"; cyc=8; A=sr(A,true); break;
			case 0xcb30: instruction="swap b"; cyc=8; B=swap(B); break;
			case 0xcb31: instruction="swap c";cyc=8;  C=swap(C); break;
			case 0xcb32: instruction="swap d";cyc=8;  D=swap(D); break;
			case 0xcb33: instruction="swap e"; cyc=8; E=swap(E); break;
			case 0xcb34: instruction="swap h"; cyc=8; H=swap(H); break;
			case 0xcb35: instruction="swap l"; cyc=8; L=swap(L); break;
			case 0xcb36: instruction="swap (hl)"; cyc=16; memory_write((H<<8)|L,(byte)swap(memory_read((H<<8)|L)&0xff)); break;
			case 0xcb37: instruction="swap a"; cyc=8; A=swap(A); break;
			case 0xcb38: instruction="srl b"; cyc=8; B=sr(B,false); break;
			case 0xcb39: instruction="srl c"; cyc=8; C=sr(C,false); break;
			case 0xcb3a: instruction="srl d"; cyc=8; D=sr(D,false); break;
			case 0xcb3b: instruction="srl e"; cyc=8; E=sr(E,false); break;
			case 0xcb3c: instruction="srl h"; cyc=8; H=sr(H,false); break;
			case 0xcb3d: instruction="srl l"; cyc=8; L=sr(L,false); break;
			case 0xcb3e: instruction="srl (hl)"; cyc=16; memory_write((H<<8)|L,(byte)sr(memory_read((H<<8)|L)&0xff,false)); break;
			case 0xcb3f: instruction="srl a"; cyc=8; A=sr(A,false); break;
			case 0xcb40: instruction="bit "+((opcode>>3)&7)+" b"; cyc=8; bit(B,(opcode>>3)&7); break;
			case 0xcb41: instruction="bit "+((opcode>>3)&7)+" c"; cyc=8; bit(C,(opcode>>3)&7); break;
			case 0xcb42: instruction="bit "+((opcode>>3)&7)+" d"; cyc=8; bit(D,(opcode>>3)&7); break;
			case 0xcb43: instruction="bit "+((opcode>>3)&7)+" e"; cyc=8; bit(E,(opcode>>3)&7); break;
			case 0xcb44: instruction="bit "+((opcode>>3)&7)+" h"; cyc=8; bit(H,(opcode>>3)&7); break;
			case 0xcb45: instruction="bit "+((opcode>>3)&7)+" l"; cyc=8; bit(L,(opcode>>3)&7); break;
			case 0xcb46: instruction="bit "+((opcode>>3)&7)+" (hl)"; cyc=16; bit(memory_read((H<<8)|L)&0xff,((opcode>>3)&7)); break;
			case 0xcb47: instruction="bit "+((opcode>>3)&7)+" a"; cyc=8; bit(A,(opcode>>3)&7); break;
			case 0xcb48: instruction="bit "+((opcode>>3)&7)+" b"; cyc=8; bit(B,(opcode>>3)&7); break;
			case 0xcb49: instruction="bit "+((opcode>>3)&7)+" c";cyc=8;  bit(C,(opcode>>3)&7); break;
			case 0xcb4a: instruction="bit "+((opcode>>3)&7)+" d"; cyc=8; bit(D,(opcode>>3)&7); break;
			case 0xcb4b: instruction="bit "+((opcode>>3)&7)+" e"; cyc=8; bit(E,(opcode>>3)&7); break;
			case 0xcb4c: instruction="bit "+((opcode>>3)&7)+" h"; cyc=8; bit(H,(opcode>>3)&7); break;
			case 0xcb4d: instruction="bit "+((opcode>>3)&7)+" l"; cyc=8; bit(L,(opcode>>3)&7); break;
			case 0xcb4e: instruction="bit "+((opcode>>3)&7)+" (hl)"; cyc=16; bit(memory_read((H<<8)|L)&0xff,((opcode>>3)&7)); break;
			case 0xcb4f: instruction="bit "+((opcode>>3)&7)+" a"; cyc=8; bit(A,(opcode>>3)&7); break;
			case 0xcb50: instruction="bit "+((opcode>>3)&7)+" b"; cyc=8; bit(B,(opcode>>3)&7); break;
			case 0xcb51: instruction="bit "+((opcode>>3)&7)+" c"; cyc=8; bit(C,(opcode>>3)&7); break;
			case 0xcb52: instruction="bit "+((opcode>>3)&7)+" d"; cyc=8; bit(D,(opcode>>3)&7); break;
			case 0xcb53: instruction="bit "+((opcode>>3)&7)+" e"; cyc=8; bit(E,(opcode>>3)&7); break;
			case 0xcb54: instruction="bit "+((opcode>>3)&7)+" h"; cyc=8; bit(H,(opcode>>3)&7); break;
			case 0xcb55: instruction="bit "+((opcode>>3)&7)+" l"; cyc=8; bit(L,(opcode>>3)&7); break;
			case 0xcb56: instruction="bit "+((opcode>>3)&7)+" (hl)"; cyc=16; bit(memory_read((H<<8)|L)&0xff,((opcode>>3)&7)); break;
			case 0xcb57: instruction="bit "+((opcode>>3)&7)+" a"; cyc=8; bit(A,(opcode>>3)&7); break;
			case 0xcb58: instruction="bit "+((opcode>>3)&7)+" b"; cyc=8; bit(B,(opcode>>3)&7); break;
			case 0xcb59: instruction="bit "+((opcode>>3)&7)+" c"; cyc=8; bit(C,(opcode>>3)&7); break;
			case 0xcb5a: instruction="bit "+((opcode>>3)&7)+" d"; cyc=8; bit(D,(opcode>>3)&7); break;
			case 0xcb5b: instruction="bit "+((opcode>>3)&7)+" e"; cyc=8; bit(E,(opcode>>3)&7); break;
			case 0xcb5c: instruction="bit "+((opcode>>3)&7)+" h"; cyc=8; bit(H,(opcode>>3)&7); break;
			case 0xcb5d: instruction="bit "+((opcode>>3)&7)+" l"; cyc=8; bit(L,(opcode>>3)&7); break;
			case 0xcb5e: instruction="bit "+((opcode>>3)&7)+" (hl)"; cyc=16; bit(memory_read((H<<8)|L)&0xff,((opcode>>3)&7)); break;
			case 0xcb5f: instruction="bit "+((opcode>>3)&7)+" a"; cyc=8; bit(A,(opcode>>3)&7); break;
			case 0xcb60: instruction="bit "+((opcode>>3)&7)+" b"; cyc=8; bit(B,(opcode>>3)&7); break;
			case 0xcb61: instruction="bit "+((opcode>>3)&7)+" c"; cyc=8; bit(C,(opcode>>3)&7); break;
			case 0xcb62: instruction="bit "+((opcode>>3)&7)+" d"; cyc=8; bit(D,(opcode>>3)&7); break;
			case 0xcb63: instruction="bit "+((opcode>>3)&7)+" e"; cyc=8; bit(E,(opcode>>3)&7); break;
			case 0xcb64: instruction="bit "+((opcode>>3)&7)+" h"; cyc=8; bit(H,(opcode>>3)&7); break;
			case 0xcb65: instruction="bit "+((opcode>>3)&7)+" l"; cyc=8; bit(L,(opcode>>3)&7); break;
			case 0xcb66: instruction="bit "+((opcode>>3)&7)+" (hl)"; cyc=16; bit(memory_read((H<<8)|L)&0xff,((opcode>>3)&7)); break;
			case 0xcb67: instruction="bit "+((opcode>>3)&7)+" a"; cyc=8; bit(A,(opcode>>3)&7); break;
			case 0xcb68: instruction="bit "+((opcode>>3)&7)+" b"; cyc=8; bit(B,(opcode>>3)&7); break;
			case 0xcb69: instruction="bit "+((opcode>>3)&7)+" c"; cyc=8; bit(C,(opcode>>3)&7); break;
			case 0xcb6a: instruction="bit "+((opcode>>3)&7)+" d"; cyc=8; bit(D,(opcode>>3)&7); break;
			case 0xcb6b: instruction="bit "+((opcode>>3)&7)+" e"; cyc=8; bit(E,(opcode>>3)&7); break;
			case 0xcb6c: instruction="bit "+((opcode>>3)&7)+" h";cyc=8;  bit(H,(opcode>>3)&7); break;
			case 0xcb6d: instruction="bit "+((opcode>>3)&7)+" l"; cyc=8; bit(L,(opcode>>3)&7); break;
			case 0xcb6e: instruction="bit "+((opcode>>3)&7)+" (hl)"; cyc=16; bit(memory_read((H<<8)|L)&0xff,((opcode>>3)&7)); break;
			case 0xcb6f: instruction="bit "+((opcode>>3)&7)+" a"; cyc=8; bit(A,(opcode>>3)&7); break;
			case 0xcb70: instruction="bit "+((opcode>>3)&7)+" b"; cyc=8; bit(B,(opcode>>3)&7); break;
			case 0xcb71: instruction="bit "+((opcode>>3)&7)+" c"; cyc=8; bit(C,(opcode>>3)&7); break;
			case 0xcb72: instruction="bit "+((opcode>>3)&7)+" d"; cyc=8; bit(D,(opcode>>3)&7); break;
			case 0xcb73: instruction="bit "+((opcode>>3)&7)+" e"; cyc=8; bit(E,(opcode>>3)&7); break;
			case 0xcb74: instruction="bit "+((opcode>>3)&7)+" h"; cyc=8; bit(H,(opcode>>3)&7); break;
			case 0xcb75: instruction="bit "+((opcode>>3)&7)+" l"; cyc=8; bit(L,(opcode>>3)&7); break;
			case 0xcb76: instruction="bit "+((opcode>>3)&7)+" (hl)";cyc=16;  bit(memory_read((H<<8)|L)&0xff,((opcode>>3)&7)); break;
			case 0xcb77: instruction="bit "+((opcode>>3)&7)+" a";cyc=8;  bit(A,(opcode>>3)&7); break;
			case 0xcb78: instruction="bit "+((opcode>>3)&7)+" b"; cyc=8; bit(B,(opcode>>3)&7); break;
			case 0xcb79: instruction="bit "+((opcode>>3)&7)+" c"; cyc=8; bit(C,(opcode>>3)&7); break;
			case 0xcb7a: instruction="bit "+((opcode>>3)&7)+" d"; cyc=8; bit(D,(opcode>>3)&7); break;
			case 0xcb7b: instruction="bit "+((opcode>>3)&7)+" e"; cyc=8; bit(E,(opcode>>3)&7); break;
			case 0xcb7c: instruction="bit "+((opcode>>3)&7)+" h"; cyc=8; bit(H,(opcode>>3)&7); break;
			case 0xcb7d: instruction="bit "+((opcode>>3)&7)+" l"; cyc=8; bit(L,(opcode>>3)&7); break;
			case 0xcb7e: instruction="bit "+((opcode>>3)&7)+" (hl)"; cyc=16; bit(memory_read((H<<8)|L)&0xff,((opcode>>3)&7)); break;
			case 0xcb7f: instruction="bit "+((opcode>>3)&7)+" a"; cyc=8; bit(A,(opcode>>3)&7); break;
			case 0xcb80: instruction="res "+((opcode>>3)&7)+" b"; cyc=8; B&=~(1<<((opcode>>3)&7)); break;
			case 0xcb81: instruction="res "+((opcode>>3)&7)+" c"; cyc=8; C&=~(1<<((opcode>>3)&7)); break;
			case 0xcb82: instruction="res "+((opcode>>3)&7)+" d"; cyc=8; D&=~(1<<((opcode>>3)&7)); break;
			case 0xcb83: instruction="res "+((opcode>>3)&7)+" e"; cyc=8; E&=~(1<<((opcode>>3)&7)); break;
			case 0xcb84: instruction="res "+((opcode>>3)&7)+" h"; cyc=8; H&=~(1<<((opcode>>3)&7)); break;
			case 0xcb85: instruction="res "+((opcode>>3)&7)+" l"; cyc=8; L&=~(1<<((opcode>>3)&7)); break;
			case 0xcb86: instruction="res "+((opcode>>3)&7)+" (hl)"; cyc=16; memory_write((H<<8)|L,(byte)(memory_read((H<<8)|L)&0xff&~(1<<((opcode>>3)&7)))); break;
			case 0xcb87: instruction="res "+((opcode>>3)&7)+" a"; cyc=8; A&=~(1<<((opcode>>3)&7)); break;
			case 0xcb88: instruction="res "+((opcode>>3)&7)+" b"; cyc=8; B&=~(1<<((opcode>>3)&7)); break;
			case 0xcb89: instruction="res "+((opcode>>3)&7)+" c"; cyc=8; C&=~(1<<((opcode>>3)&7)); break;
			case 0xcb8a: instruction="res "+((opcode>>3)&7)+" d"; cyc=8; D&=~(1<<((opcode>>3)&7)); break;
			case 0xcb8b: instruction="res "+((opcode>>3)&7)+" e"; cyc=8; E&=~(1<<((opcode>>3)&7)); break;
			case 0xcb8c: instruction="res "+((opcode>>3)&7)+" h"; cyc=8; H&=~(1<<((opcode>>3)&7)); break;
			case 0xcb8d: instruction="res "+((opcode>>3)&7)+" l"; cyc=8; L&=~(1<<((opcode>>3)&7)); break;
			case 0xcb8e: instruction="res "+((opcode>>3)&7)+" (hl)"; cyc=16; memory_write((H<<8)|L,(byte)(memory_read((H<<8)|L)&0xff&~(1<<((opcode>>3)&7)))); break;
			case 0xcb8f: instruction="res "+((opcode>>3)&7)+" a"; cyc=8; A&=~(1<<((opcode>>3)&7)); break;
			case 0xcb90: instruction="res "+((opcode>>3)&7)+" b"; cyc=8; B&=~(1<<((opcode>>3)&7)); break;
			case 0xcb91: instruction="res "+((opcode>>3)&7)+" c"; cyc=8; C&=~(1<<((opcode>>3)&7)); break;
			case 0xcb92: instruction="res "+((opcode>>3)&7)+" d"; cyc=8; D&=~(1<<((opcode>>3)&7)); break;
			case 0xcb93: instruction="res "+((opcode>>3)&7)+" e"; cyc=8; E&=~(1<<((opcode>>3)&7)); break;
			case 0xcb94: instruction="res "+((opcode>>3)&7)+" h"; cyc=8; H&=~(1<<((opcode>>3)&7)); break;
			case 0xcb95: instruction="res "+((opcode>>3)&7)+" l"; cyc=8; L&=~(1<<((opcode>>3)&7)); break;
			case 0xcb96: instruction="res "+((opcode>>3)&7)+" (hl)"; cyc=16; memory_write((H<<8)|L,(byte)(memory_read((H<<8)|L)&0xff&~(1<<((opcode>>3)&7)))); break;
			case 0xcb97: instruction="res "+((opcode>>3)&7)+" a"; cyc=8; A&=~(1<<((opcode>>3)&7)); break;
			case 0xcb98: instruction="res "+((opcode>>3)&7)+" b"; cyc=8; B&=~(1<<((opcode>>3)&7)); break;
			case 0xcb99: instruction="res "+((opcode>>3)&7)+" c"; cyc=8; C&=~(1<<((opcode>>3)&7)); break;
			case 0xcb9a: instruction="res "+((opcode>>3)&7)+" d"; cyc=8; D&=~(1<<((opcode>>3)&7)); break;
			case 0xcb9b: instruction="res "+((opcode>>3)&7)+" e"; cyc=8; E&=~(1<<((opcode>>3)&7)); break;
			case 0xcb9c: instruction="res "+((opcode>>3)&7)+" h"; cyc=8; H&=~(1<<((opcode>>3)&7)); break;
			case 0xcb9d: instruction="res "+((opcode>>3)&7)+" l"; cyc=8; L&=~(1<<((opcode>>3)&7)); break;
			case 0xcb9e: instruction="res "+((opcode>>3)&7)+" (hl)"; cyc=16; memory_write((H<<8)|L,(byte)(memory_read((H<<8)|L)&0xff&~(1<<((opcode>>3)&7)))); break;
			case 0xcb9f: instruction="res "+((opcode>>3)&7)+" a"; cyc=8; A&=~(1<<((opcode>>3)&7)); break;
			case 0xcba0: instruction="res "+((opcode>>3)&7)+" b"; cyc=8; B&=~(1<<((opcode>>3)&7)); break;
			case 0xcba1: instruction="res "+((opcode>>3)&7)+" c"; cyc=8; C&=~(1<<((opcode>>3)&7)); break;
			case 0xcba2: instruction="res "+((opcode>>3)&7)+" d"; cyc=8; D&=~(1<<((opcode>>3)&7)); break;
			case 0xcba3: instruction="res "+((opcode>>3)&7)+" e"; cyc=8; E&=~(1<<((opcode>>3)&7)); break;
			case 0xcba4: instruction="res "+((opcode>>3)&7)+" h"; cyc=8; H&=~(1<<((opcode>>3)&7)); break;
			case 0xcba5: instruction="res "+((opcode>>3)&7)+" l"; cyc=8; L&=~(1<<((opcode>>3)&7)); break;
			case 0xcba6: instruction="res "+((opcode>>3)&7)+" (hl)"; cyc=16; memory_write((H<<8)|L,(byte)(memory_read((H<<8)|L)&0xff&~(1<<((opcode>>3)&7)))); break;
			case 0xcba7: instruction="res "+((opcode>>3)&7)+" a"; cyc=8; A&=~(1<<((opcode>>3)&7)); break;
			case 0xcba8: instruction="res "+((opcode>>3)&7)+" b"; cyc=8; B&=~(1<<((opcode>>3)&7)); break;
			case 0xcba9: instruction="res "+((opcode>>3)&7)+" c"; cyc=8; C&=~(1<<((opcode>>3)&7)); break;
			case 0xcbaa: instruction="res "+((opcode>>3)&7)+" d"; cyc=8; D&=~(1<<((opcode>>3)&7)); break;
			case 0xcbab: instruction="res "+((opcode>>3)&7)+" e"; cyc=8; E&=~(1<<((opcode>>3)&7)); break;
			case 0xcbac: instruction="res "+((opcode>>3)&7)+" h"; cyc=8; H&=~(1<<((opcode>>3)&7)); break;
			case 0xcbad: instruction="res "+((opcode>>3)&7)+" l"; cyc=8; L&=~(1<<((opcode>>3)&7)); break;
			case 0xcbae: instruction="res "+((opcode>>3)&7)+" (hl)"; cyc=16; memory_write((H<<8)|L,(byte)(memory_read((H<<8)|L)&0xff&~(1<<((opcode>>3)&7)))); break;
			case 0xcbaf: instruction="res "+((opcode>>3)&7)+" a"; cyc=8; A&=~(1<<((opcode>>3)&7)); break;
			case 0xcbb0: instruction="res "+((opcode>>3)&7)+" b"; cyc=8; B&=~(1<<((opcode>>3)&7)); break;
			case 0xcbb1: instruction="res "+((opcode>>3)&7)+" c"; cyc=8; C&=~(1<<((opcode>>3)&7)); break;
			case 0xcbb2: instruction="res "+((opcode>>3)&7)+" d"; cyc=8; D&=~(1<<((opcode>>3)&7)); break;
			case 0xcbb3: instruction="res "+((opcode>>3)&7)+" e"; cyc=8; E&=~(1<<((opcode>>3)&7)); break;
			case 0xcbb4: instruction="res "+((opcode>>3)&7)+" h"; cyc=8; H&=~(1<<((opcode>>3)&7)); break;
			case 0xcbb5: instruction="res "+((opcode>>3)&7)+" l"; cyc=8; L&=~(1<<((opcode>>3)&7)); break;
			case 0xcbb6: instruction="res "+((opcode>>3)&7)+" (hl)"; cyc=16; memory_write((H<<8)|L,(byte)(memory_read((H<<8)|L)&0xff&~(1<<((opcode>>3)&7)))); break;
			case 0xcbb7: instruction="res "+((opcode>>3)&7)+" a";cyc=8;  A&=~(1<<((opcode>>3)&7)); break;
			case 0xcbb8: instruction="res "+((opcode>>3)&7)+" b";cyc=8;  B&=~(1<<((opcode>>3)&7)); break;
			case 0xcbb9: instruction="res "+((opcode>>3)&7)+" c"; cyc=8; C&=~(1<<((opcode>>3)&7)); break;
			case 0xcbba: instruction="res "+((opcode>>3)&7)+" d"; cyc=8; D&=~(1<<((opcode>>3)&7)); break;
			case 0xcbbb: instruction="res "+((opcode>>3)&7)+" e"; cyc=8; E&=~(1<<((opcode>>3)&7)); break;
			case 0xcbbc: instruction="res "+((opcode>>3)&7)+" h";cyc=8;  H&=~(1<<((opcode>>3)&7)); break;
			case 0xcbbd: instruction="res "+((opcode>>3)&7)+" l"; cyc=8; L&=~(1<<((opcode>>3)&7)); break;
			case 0xcbbe: instruction="res "+((opcode>>3)&7)+" (hl)"; cyc=16; memory_write((H<<8)|L,(byte)(memory_read((H<<8)|L)&0xff&~(1<<((opcode>>3)&7)))); break;
			case 0xcbbf: instruction="res "+((opcode>>3)&7)+" a"; cyc=8; A&=~(1<<((opcode>>3)&7)); break;
			case 0xcbc0: instruction="set "+((opcode>>3)&7)+" b"; cyc=8; B|=(1<<((opcode>>3)&7)); break;
			case 0xcbc1: instruction="set "+((opcode>>3)&7)+" c"; cyc=8; C|=(1<<((opcode>>3)&7)); break;
			case 0xcbc2: instruction="set "+((opcode>>3)&7)+" d"; cyc=8; D|=(1<<((opcode>>3)&7)); break;
			case 0xcbc3: instruction="set "+((opcode>>3)&7)+" e"; cyc=8; E|=(1<<((opcode>>3)&7)); break;
			case 0xcbc4: instruction="set "+((opcode>>3)&7)+" h"; cyc=8; H|=(1<<((opcode>>3)&7)); break;
			case 0xcbc5: instruction="set "+((opcode>>3)&7)+" l"; cyc=8; L|=(1<<((opcode>>3)&7)); break;
			case 0xcbc6: instruction="set "+((opcode>>3)&7)+" (hl)"; cyc=16; memory_write((H<<8)|L,(byte)(0xff&memory_read((H<<8)|L)|(1<<((opcode>>3)&7)))); break;
			case 0xcbc7: instruction="set "+((opcode>>3)&7)+" a"; cyc=8; A|=(1<<((opcode>>3)&7)); break;
			case 0xcbc8: instruction="set "+((opcode>>3)&7)+" b"; cyc=8; B|=(1<<((opcode>>3)&7)); break;
			case 0xcbc9: instruction="set "+((opcode>>3)&7)+" c"; cyc=8; C|=(1<<((opcode>>3)&7)); break;
			case 0xcbca: instruction="set "+((opcode>>3)&7)+" d"; cyc=8; D|=(1<<((opcode>>3)&7)); break;
			case 0xcbcb: instruction="set "+((opcode>>3)&7)+" e"; cyc=8; E|=(1<<((opcode>>3)&7)); break;
			case 0xcbcc: instruction="set "+((opcode>>3)&7)+" h"; cyc=8; H|=(1<<((opcode>>3)&7)); break;
			case 0xcbcd: instruction="set "+((opcode>>3)&7)+" l"; cyc=8; L|=(1<<((opcode>>3)&7)); break;
			case 0xcbce: instruction="set "+((opcode>>3)&7)+" (hl)"; cyc=16; memory_write((H<<8)|L,(byte)(0xff&memory_read((H<<8)|L)|(1<<((opcode>>3)&7)))); break;
			case 0xcbcf: instruction="set "+((opcode>>3)&7)+" a"; cyc=8; A|=(1<<((opcode>>3)&7)); break;
			case 0xcbd0: instruction="set "+((opcode>>3)&7)+" b"; cyc=8; B|=(1<<((opcode>>3)&7)); break;
			case 0xcbd1: instruction="set "+((opcode>>3)&7)+" c"; cyc=8; C|=(1<<((opcode>>3)&7)); break;
			case 0xcbd2: instruction="set "+((opcode>>3)&7)+" d"; cyc=8; D|=(1<<((opcode>>3)&7)); break;
			case 0xcbd3: instruction="set "+((opcode>>3)&7)+" e"; cyc=8; E|=(1<<((opcode>>3)&7)); break;
			case 0xcbd4: instruction="set "+((opcode>>3)&7)+" h"; cyc=8; H|=(1<<((opcode>>3)&7)); break;
			case 0xcbd5: instruction="set "+((opcode>>3)&7)+" l"; cyc=8; L|=(1<<((opcode>>3)&7)); break;
			case 0xcbd6: instruction="set "+((opcode>>3)&7)+" (hl)"; cyc=16; memory_write((H<<8)|L,(byte)(0xff&memory_read((H<<8)|L)|(1<<((opcode>>3)&7)))); break;
			case 0xcbd7: instruction="set "+((opcode>>3)&7)+" a"; cyc=8; A|=(1<<((opcode>>3)&7)); break;
			case 0xcbd8: instruction="set "+((opcode>>3)&7)+" b"; cyc=8; B|=(1<<((opcode>>3)&7)); break;
			case 0xcbd9: instruction="set "+((opcode>>3)&7)+" c"; cyc=8; C|=(1<<((opcode>>3)&7)); break;
			case 0xcbda: instruction="set "+((opcode>>3)&7)+" d"; cyc=8; D|=(1<<((opcode>>3)&7)); break;
			case 0xcbdb: instruction="set "+((opcode>>3)&7)+" e"; cyc=8; E|=(1<<((opcode>>3)&7)); break;
			case 0xcbdc: instruction="set "+((opcode>>3)&7)+" h"; cyc=8; H|=(1<<((opcode>>3)&7)); break;
			case 0xcbdd: instruction="set "+((opcode>>3)&7)+" l"; cyc=8; L|=(1<<((opcode>>3)&7)); break;
			case 0xcbde: instruction="set "+((opcode>>3)&7)+" (hl)"; cyc=16; memory_write((H<<8)|L,(byte)(0xff&memory_read((H<<8)|L)|(1<<((opcode>>3)&7)))); break;
			case 0xcbdf: instruction="set "+((opcode>>3)&7)+" a"; cyc=8; A|=(1<<((opcode>>3)&7)); break;
			case 0xcbe0: instruction="set "+((opcode>>3)&7)+" b"; cyc=8; B|=(1<<((opcode>>3)&7)); break;
			case 0xcbe1: instruction="set "+((opcode>>3)&7)+" c"; cyc=8; C|=(1<<((opcode>>3)&7)); break;
			case 0xcbe2: instruction="set "+((opcode>>3)&7)+" d"; cyc=8; D|=(1<<((opcode>>3)&7)); break;
			case 0xcbe3: instruction="set "+((opcode>>3)&7)+" e"; cyc=8; E|=(1<<((opcode>>3)&7)); break;
			case 0xcbe4: instruction="set "+((opcode>>3)&7)+" h"; cyc=8; H|=(1<<((opcode>>3)&7)); break;
			case 0xcbe5: instruction="set "+((opcode>>3)&7)+" l"; cyc=8; L|=(1<<((opcode>>3)&7)); break;
			case 0xcbe6: instruction="set "+((opcode>>3)&7)+" (hl)"; cyc=16; memory_write((H<<8)|L,(byte)(0xff&memory_read((H<<8)|L)|(1<<((opcode>>3)&7)))); break;
			case 0xcbe7: instruction="set "+((opcode>>3)&7)+" a"; cyc=8; A|=(1<<((opcode>>3)&7)); break;
			case 0xcbe8: instruction="set "+((opcode>>3)&7)+" b"; cyc=8; B|=(1<<((opcode>>3)&7)); break;
			case 0xcbe9: instruction="set "+((opcode>>3)&7)+" c"; cyc=8; C|=(1<<((opcode>>3)&7)); break;
			case 0xcbea: instruction="set "+((opcode>>3)&7)+" d"; cyc=8; D|=(1<<((opcode>>3)&7)); break;
			case 0xcbeb: instruction="set "+((opcode>>3)&7)+" e"; cyc=8; E|=(1<<((opcode>>3)&7)); break;
			case 0xcbec: instruction="set "+((opcode>>3)&7)+" h"; cyc=8; H|=(1<<((opcode>>3)&7)); break;
			case 0xcbed: instruction="set "+((opcode>>3)&7)+" l"; cyc=8; L|=(1<<((opcode>>3)&7)); break;
			case 0xcbee: instruction="set "+((opcode>>3)&7)+" (hl)"; cyc=16; memory_write((H<<8)|L,(byte)(0xff&memory_read((H<<8)|L)|(1<<((opcode>>3)&7)))); break;
			case 0xcbef: instruction="set "+((opcode>>3)&7)+" a"; cyc=8; A|=(1<<((opcode>>3)&7)); break;
			case 0xcbf0: instruction="set "+((opcode>>3)&7)+" b"; cyc=8; B|=(1<<((opcode>>3)&7)); break;
			case 0xcbf1: instruction="set "+((opcode>>3)&7)+" c"; cyc=8; C|=(1<<((opcode>>3)&7)); break;
			case 0xcbf2: instruction="set "+((opcode>>3)&7)+" d"; cyc=8; D|=(1<<((opcode>>3)&7)); break;
			case 0xcbf3: instruction="set "+((opcode>>3)&7)+" e"; cyc=8; E|=(1<<((opcode>>3)&7)); break;
			case 0xcbf4: instruction="set "+((opcode>>3)&7)+" h"; cyc=8; H|=(1<<((opcode>>3)&7)); break;
			case 0xcbf5: instruction="set "+((opcode>>3)&7)+" l"; cyc=8; L|=(1<<((opcode>>3)&7)); break;
			case 0xcbf6: instruction="set "+((opcode>>3)&7)+" (hl)"; cyc=16; memory_write((H<<8)|L,(byte)(0xff&memory_read((H<<8)|L)|(1<<((opcode>>3)&7)))); break;
			case 0xcbf7: instruction="set "+((opcode>>3)&7)+" a"; cyc=8; A|=(1<<((opcode>>3)&7)); break;
			case 0xcbf8: instruction="set "+((opcode>>3)&7)+" b"; cyc=8; B|=(1<<((opcode>>3)&7)); break;
			case 0xcbf9: instruction="set "+((opcode>>3)&7)+" c"; cyc=8; C|=(1<<((opcode>>3)&7)); break;
			case 0xcbfa: instruction="set "+((opcode>>3)&7)+" d"; cyc=8; D|=(1<<((opcode>>3)&7)); break;
			case 0xcbfb: instruction="set "+((opcode>>3)&7)+" e"; cyc=8; E|=(1<<((opcode>>3)&7)); break;
			case 0xcbfc: instruction="set "+((opcode>>3)&7)+" h"; cyc=8; H|=(1<<((opcode>>3)&7)); break;
			case 0xcbfd: instruction="set "+((opcode>>3)&7)+" l"; cyc=8; L|=(1<<((opcode>>3)&7)); break;
			case 0xcbfe: instruction="set "+((opcode>>3)&7)+" (hl)"; cyc=16; memory_write((H<<8)|L,(byte)(0xff&memory_read((H<<8)|L)|(1<<((opcode>>3)&7)))); break;
			case 0xcbff: instruction="set "+((opcode>>3)&7)+" a"; cyc=8; A|=(1<<((opcode>>3)&7)); break;
			default: instruction="undefined "+Integer.toHexString(opcode); fault(opcode); cyc=0; break;
			}
			break;
		case 0xcc: instruction="call z imm16"; 
			imm=(fetch()&0xff)|((fetch()&0xff)<<8);
			if(FLAG_Z!=0)
			{
				cyc=24;
				push(PC);
				PC=imm;
			}
			else
				cyc=12;
			break;
		case 0xcd: instruction="call imm16"; cyc=24;
			imm=(fetch()&0xff)|((fetch()&0xff)<<8);
			push(PC);
			PC=imm;
			break;
		case 0xce: instruction="adc imm8"; cyc=8;
			A=arithmetic(fetch()&0xff,false,true);
			break;
		case 0xcf: instruction="rst 8"; cyc=16; push(PC); PC=8; break; //FLAG_I=0; System.out.println("rst 8"); break;
		case 0xd0: instruction="ret nc";
			if(FLAG_C==0)
			{
				cyc=20;
				PC=pop();
			}
			else
				cyc=8;
			break;
		case 0xd1: instruction="pop de"; cyc=12;
			value=pop();
			D=value>>8; E=value&0xff;
			break;
		case 0xd2: instruction="jp nc imm16";
			imm=(fetch()&0xff)|((fetch()&0xff)<<8);
			if(FLAG_C==0)
			{
				cyc=16;
				PC=imm;
			}
			else
				cyc=12;
			break;
		case 0xd3: cyc=0; instruction="out port a"; fault(opcode); break;
		case 0xd4: instruction="call nc imm16"; 
			imm=(fetch()&0xff)|((fetch()&0xff)<<8);
			if(FLAG_C==0)
			{
				cyc=24;
				push(PC);
				PC=imm;
			}
			else
				cyc=12;
			break;
		case 0xd5: instruction="push de"; cyc=16; push((D<<8)|E); break;
		case 0xd6: instruction="sub imm8"; cyc=8;
			A=arithmetic(fetch()&0xff,true,false);
			break;
		case 0xd7: instruction="rst 10"; cyc=16; push(PC); PC=0x10; break; //FLAG_I=0; System.out.println("rst 10"); break;
		case 0xd8: instruction="ret c";
			if(FLAG_C!=0)
			{
				cyc=20;
				PC=pop();
			}
			else
				cyc=8;
			break;
		case 0xd9: instruction="reti";
			cyc=16;
			PC=pop(); FLAG_I=1; break;
		case 0xda: instruction="jp c imm16"; 
			imm=(fetch()&0xff)|((fetch()&0xff)<<8);
			if(FLAG_C!=0)
			{
				cyc=16;
				PC=imm;
			}
			else
				cyc=12;
			break;
		case 0xdb: instruction="in a port"; cyc=0; fault(opcode); break;
		case 0xdc: instruction="call c imm16"; 
			imm=(fetch()&0xff)|((fetch()&0xff)<<8);
			if(FLAG_C!=0)
			{
				cyc=24;
				push(PC);
				PC=imm;
			}
			else
				cyc=12;
			break;
		case 0xdd: instruction="extended_instructions_IX"; cyc=0; fault(opcode); break;
		case 0xde: instruction="sbc imm8"; cyc=8; 
			A=arithmetic(fetch()&0xff,true,true);
			break;
		case 0xdf: instruction="rst 18"; cyc=16; push(PC); PC=0x18; break; //FLAG_I=0; System.out.println("rst 18"); break;
		case 0xe0: instruction="ldh (imm8) a";
			cyc=12;
			imm=fetch()&0xff;
			memory_write(0xff00|imm,(byte)A);
			break;
		case 0xe1: instruction="pop hl"; cyc=12;
			value=pop();
			H=value>>8; L=value&0xff;
			break;
		case 0xe2: instruction="ld (c) a";
			cyc=8;
			memory_write(0xff00|C,(byte)A);
			break;
		case 0xe3: instruction="bad e3"; fault(opcode); cyc=0; break;
		case 0xe4: instruction="bad e4"; fault(opcode); cyc=0; break;
		case 0xe5: instruction="push hl"; cyc=16; push((H<<8)|L); break;
		case 0xe6: instruction="and imm8"; cyc=8;
			A&=fetch()&0xff; logicflags(1);
			break;
		case 0xe7: instruction="rst 20"; cyc=16; push(PC); PC=0x20; break; //FLAG_I=0; System.out.println("rst 20"); break;
		case 0xe8: instruction="add sp imm8"; 
			cyc=16;
			imm=fetch()&0xff;
			imm=(imm>=0x80)? imm|0xff00:imm&0xff;
			SP=arithmetic16(SP,imm,false,false);
			break;
		case 0xe9: instruction="jp (hl)"; cyc=4;
			PC=(H<<8)|L;
			break;
		case 0xea: instruction="ld (imm16) a"; 
			cyc=16;
			imm=(fetch()&0xff)|((fetch()&0xff)<<8);
			memory_write(imm,(byte)A);
			break;
		case 0xeb: instruction="bad eb"; fault(opcode); cyc=0; break;
		case 0xec: instruction="bad ec"; fault(opcode); cyc=0; break;
		case 0xed: instruction="extended_instructions_ED"; cyc=0; fault(opcode); break;
		case 0xee: instruction="xor imm8"; cyc=8;
			A^=fetch()&0xff; logicflags(0);
			break;
		case 0xef: instruction="rst 28"; cyc=16; push(PC); PC=0x28; break; //FLAG_I=0; System.out.println("rst 28"); break;
		case 0xf0: instruction="ldh a (imm8)"; 
			cyc=12;
			imm=fetch()&0xff;
			A=memory_read(0xff00|imm)&0xff;
			break;
		case 0xf1: instruction="pop af"; cyc=12;
			value=pop();
			A=value>>8;
			FLAGS=value&0xff;
			readFlags();
			break;
		case 0xf2: instruction="ld a (c)";
			cyc=8;
			A=memory_read(0xff00|C)&0xff;
			break;
		case 0xf3: instruction="di"; cyc=4; FLAG_I=0; break;
		case 0xf4: instruction="bad f4"; fault(opcode); cyc=0; break;
		case 0xf5: instruction="push af"; cyc=16;
			setFlags();
			push((A<<8)|FLAGS);
			break;
		case 0xf6: instruction="or imm8"; cyc=8;
			A|=fetch()&0xff; logicflags(0);
			break;
		case 0xf7: instruction="rst 30"; cyc=16; push(PC); PC=0x30; break; //FLAG_I=0; System.out.println("rst 30"); break;
		case 0xf8: instruction="ldhl sp imm8"; cyc=12;
			imm=fetch()&0xff;
			imm=(imm>=0x80)? imm|0xff00:imm&0xff;
			value=arithmetic16(SP,imm,false,false);
			H=value>>8; L=value&0xff;
			break;
		case 0xf9: instruction="ld sp hl"; cyc=8;
			SP=(H<<8)|L;
			break;
		case 0xfa: instruction="ld a (imm16)";
			cyc=16;
			A=memory_read((fetch()&0xff)|((fetch()&0xff)<<8))&0xff;
			break;
		case 0xfb: instruction="ei"; cyc=4; ei(); break;
		case 0xfc: instruction="bad fc"; fault(opcode); cyc=0; break;
		case 0xfd: instruction="extended_instructions_IY"; cyc=0; fault(opcode); break;
		case 0xfe: instruction="cp imm8"; cyc=8;
			arithmetic(fetch()&0xff,true,false);
			break;
		case 0xff: instruction="rst 38"; cyc=16; push(PC); PC=0x38; break; //FLAG_I=0; System.out.println("rst 38"); break;			
		default: instruction="undefined "+Integer.toHexString(opcode); fault(opcode); cyc=0; break;
		}
		this.instruction=instruction;
		this.cycles=cyc;
	}
	
	private int arithmetic(int value, boolean operationIsSubtract, boolean includeCarry)
	{
		int result;
		if(!operationIsSubtract)
		{
			FLAG_N=0;
			FLAG_H=(((A&0xf)+(value&0xf))&0x10)!=0? 1:0;
			result=A+value;
			if(includeCarry) result+=FLAG_C;
		}
		else
		{
			FLAG_N=1;
			FLAG_H=(((A&0xf)-(value&0xf))&0x10)!=0? 1:0;
			result=A-value;
			if(includeCarry) result-=FLAG_C;
		}
		FLAG_S=(result&0x80)!=0? 1:0;
		FLAG_C=(result&0x100)!=0? 1:0;
		FLAG_Z=(result&0xff)==0? 1:0;
		if(operationIsSubtract)
			FLAG_P = (A&0x80) != (value&0x80) && (result&0x80) != (A&0x80)? 1:0;
		else
			FLAG_P = (A&0x80) == (value&0x80) && (result&0x80) != (A&0x80)? 1:0;

		return result&0xff;		
	}

	private int arithmetic16(int value1, int value2, boolean operationIsSubtract, boolean includeCarry)
	{
		int result=value1;
		if (includeCarry) value2+=FLAG_C;
		if(!operationIsSubtract)
		{
			result+=value2;
			FLAG_H=(((value1&0xfff)+(value2&0xfff))&0x1000)!=0? 1:0;
			FLAG_N=0;
		}
		else
		{
			result-=value2;
			FLAG_H=(((value1&0xfff)-(value2&0xfff))&0x1000)!=0? 1:0;
			FLAG_N=1;
		}
		FLAG_C=(result&0x10000)!=0?1:0;
		if(operationIsSubtract || includeCarry)
		{
			if(operationIsSubtract)
				FLAG_P = (value1&0x8000) != (value2&0x8000) && (result&0x8000) != (value1&0x8000)? 1:0;
			else
				FLAG_P = (value1&0x8000) == (value2&0x8000) && (result&0x8000) != (value1&0x8000)? 1:0;
			FLAG_S=(result&0x8000)!=0? 1:0;
			FLAG_Z=((result&0xffff)==0)? 1:0;
		}
//		FLAG_Z=((result&0xffff)==0)? 1:0;
		return result&0xffff;
	}
	
	private void daa()
	{
		int correction=0,carry=0,a=A;
		if(A>0x99 || FLAG_C==1)
		{
			correction=0x60; carry=1;
		}
		if((A&0xf)>9 || FLAG_H==1)
			correction|=6;
		if(FLAG_N==1)
			A-=correction;
		else
			A+=correction;
		FLAG_H=((a^A)&0x10)!=0?1:0;
		FLAG_C=carry;
		FLAG_S=(A&0x80)!=0?1:0;
		FLAG_Z=A==0?1:0;
		FLAG_P=((A>>2)&1) ^ ((A>>1)&1) ^ (A&1);
	}
	
	private int incrementDecrement(int value, boolean isDecrement)
	{
	    if (isDecrement)
	    {
	    	FLAG_P=((value&0x80)!=0)&&(((value-1)&0x80)==0)?1:0;
	        value--;
	        FLAG_H=(value&0xf)==0xf?1:0;
	        FLAG_N=1;
	    }
	    else
	    {
	    	FLAG_P=((value&0x80)!=0)&&(((value+1)&0x80)==0)?1:0;
	        value++;
	        FLAG_H=(value&0xf)==0?1:0;
	        FLAG_N=0;
	    }
	    FLAG_S=((value&0x80)!=0)?1:0;
	    FLAG_Z=((value&0xff)==0?1:0);
	    return value&0xff;		
	}
	
	private void logicflags(int H)
	{
		FLAG_S=(A&0x80)!=0? 1:0;
		FLAG_Z=(A==0)? 1:0;
		FLAG_H=H;
		FLAG_N=0;
		FLAG_C=0;
		FLAG_P=((A>>2)&1) ^ ((A>>1)&1) ^ (A&1);
	}
	
	private int sr (int value, boolean isASR)
	{
	    int bit = value & 0x80;
	    FLAG_C=value&1;
	    value>>=1;
		if(isASR)
			value|=bit;
		FLAG_H=0;
		FLAG_N=0;
		FLAG_S=(value&0x80)!=0?1:0;
		FLAG_Z=(value==0)?1:0;
		FLAG_P=((value>>2)&1) ^ ((value>>1)&1) ^ (value&1);
	    return value;
	}

	private int sl (int value, boolean isASL)
	{
	    FLAG_C=(value&0x80)!=0?1:0;
	    value<<=1;
		if(!isASL)
			value|=1;
	    value&=0xff;
		FLAG_H=0;
		FLAG_N=0;
		FLAG_S=(value&0x80)!=0?1:0;
		FLAG_Z=(value==0)?1:0;
		FLAG_P=((value>>2)&1) ^ ((value>>1)&1) ^ (value&1);
	    return value;
	}
	
	private int swap(int value)
	{
		value=((value&0xf)<<4) | ((value&0xf0)>>4);
		FLAG_C=FLAG_H=FLAG_N=0;
		FLAG_Z=(value==0)?1:0;
		return value;
	}

	private int rlc (int value, boolean updateFlags)
	{
	    FLAG_C=(value&0x80)!=0?1:0;
	    value <<= 1;
	    value |= FLAG_C;
	    value&=0xff;

	    FLAG_H=0; FLAG_N=0;
	    if(updateFlags)
	    {
			FLAG_S=(value&0x80)!=0?1:0;
			FLAG_Z=(value==0)?1:0;
			FLAG_P=((value>>2)&1) ^ ((value>>1)&1) ^ (value&1);	    	
	    }
	    return value;
	}

	private int rl (int value, boolean updateFlags)
	{
	    int carry = FLAG_C;
	    FLAG_C= ((value & 0x80) != 0)?1:0;
	    value <<= 1;
	    value |= carry;
	    value&=0xff;

	    FLAG_H=0; FLAG_N=0;
	    if(updateFlags)
	    {
			FLAG_S=(value&0x80)!=0?1:0;
			FLAG_Z=(value==0)?1:0;
			FLAG_P=((value>>2)&1) ^ ((value>>1)&1) ^ (value&1);	    	
	    }
	    return value;
	}

	private int rrc (int value, boolean updateFlags)
	{
		FLAG_C=value&1;
	    value >>= 1;
	    value |= (FLAG_C << 7);

	    FLAG_H=0; FLAG_N=0;
	    if(updateFlags)
	    {
			FLAG_S=(value&0x80)!=0?1:0;
			FLAG_Z=(value==0)?1:0;
			FLAG_P=((value>>2)&1) ^ ((value>>1)&1) ^ (value&1);	    	
	    }
	    return value;
	}

	private int rr (int value, boolean updateFlags)
	{
	    int carry = FLAG_C;
	    FLAG_C=value&1;
	    value >>= 1;
	    value |= (carry << 7);

	    FLAG_H=0; FLAG_N=0;
	    if(updateFlags)
	    {
			FLAG_S=(value&0x80)!=0?1:0;
			FLAG_Z=(value==0)?1:0;
			FLAG_P=((value>>2)&1) ^ ((value>>1)&1) ^ (value&1);	    	
	    }
	    return value;
	}

	private void bit(int value, int bit)
	{
		value&=(1<<bit);
		FLAG_P=FLAG_Z=value==0?1:0;
		FLAG_H=1;
		FLAG_N=0;
		FLAG_S=0;
		if(bit==7 && FLAG_Z==0) FLAG_S=1;
	}
	
	public void setFlags()
	{
//		FLAGS&=0x28;
//		FLAGS|=FLAG_C|(FLAG_N<<1)|(FLAG_P<<2)|(FLAG_H<<4)|(FLAG_Z<<6)|(FLAG_S<<7);
		FLAGS=((FLAG_C<<4)|(FLAG_H<<5)|(FLAG_N<<6)|(FLAG_Z<<7))&0xf0;
	}

	public void readFlags()
	{
//		FLAG_C=FLAGS&1; FLAG_N=(FLAGS>>1)&1; FLAG_P=(FLAGS>>2)&1; FLAG_H=(FLAGS>>4)&1; FLAG_Z=(FLAGS>>6)&1; FLAG_S=(FLAGS>>7)&1;
		FLAG_C=(FLAGS>>4)&1; FLAG_H=(FLAGS>>5)&1; FLAG_N=(FLAGS>>6)&1; FLAG_Z=(FLAGS>>7)&1; 
	}
	
	private void push(int value)
	{
		SP=(SP-1)&0xffff;
		memory_write(SP,(byte)(value>>8));
		SP=(SP-1)&0xffff;
		memory_write(SP,(byte)(value&0xff));
	}
	
	private int pop()
	{
		int value=memory_read(SP)&0xff;
		SP=(SP+1)&0xffff;
		value|=(memory_read(SP)&0xff)<<8;
		SP=(SP+1)&0xffff;
		return value;
	}
	
	private void halt()
	{
		halted=true;
		FLAG_I=1;
	}
	
	private void ei()
	{
		FLAG_I=1;
		interrupt_deferred=3;
	}
	
	private byte fetch()
	{
		PC=(PC+1)&0xffff;
		return memory_read((PC-1)&0xffff);
	}
	
	private void fault(int opcode)
	{
		System.out.println("FAULT: "+opcode+" "+instruction);
		System.exit(0);
	}
	
	public void handleInterrupt(int address)
	{
		push(PC);
		PC=address;
//		FLAG_I=0;
		halted=false;
	}
	
	public void throwInterrupt(int line)
	{
		if((memory_read(0xffff)&line)!=0)
			interrupts|=line;
	}
	
	public void checkForInterrupts()
	{
		if(FLAG_I==0 || interrupt_deferred>0) return;
		
		int interruptToHandle=memory_read(0xffff)&interrupts;
		if(interruptToHandle!=0)
		{
			if((interruptToHandle&1)!=0)
			{
				interrupts&=0xfe;
				handleInterrupt(0x40);
			}
			else if((interruptToHandle&2)!=0)
			{
				interrupts&=0xfd;
				handleInterrupt(0x48);
			}
			else if((interruptToHandle&4)!=0)
			{
				interrupts&=0xfb;
				handleInterrupt(0x50);
			}
			else if((interruptToHandle&8)!=0)
			{
				interrupts&=0xf7;
				handleInterrupt(0x58);
			}
			else if((interruptToHandle&0x10)!=0)
			{
				interrupts&=0xef;
				handleInterrupt(0x60);
			}
		}
	}
	
	private byte memory_read(int address)
	{
		return snes.gameboy.memory_read(address);
	}

	private void memory_write(int address, byte b)
	{
		snes.gameboy.memory_write(address,b);
	}
	
	//save processor state to a snapshot string
	public String dumpProcessorState()
	{
		String ret="z80\n";
		ret+=String.format("%x %x %x %x %x %x %x %x %x ",A,B,C,D,E,H,L,PC,SP);
		ret+=String.format("%x %x %x %x %x %x %x ",FLAG_C,FLAG_N,FLAG_P,FLAG_H,FLAG_Z,FLAG_S,FLAG_I);
		ret+=String.format("%d %d ",interrupts,interrupt_deferred);
		ret+=String.format("%x ",halted?1:0);
		ret+="\n";
		return ret;
	}
	//load processor state from a snapshot string
	public void loadProcessorState(String state)
	{
		Scanner s=new Scanner(state);
		A=s.nextInt(16); B=s.nextInt(16); C=s.nextInt(16); D=s.nextInt(16); E=s.nextInt(16); H=s.nextInt(16); L=s.nextInt(16); PC=s.nextInt(16); SP=s.nextInt(16);
		FLAG_C=s.nextInt(); FLAG_N=s.nextInt(); FLAG_P=s.nextInt(); FLAG_H=s.nextInt(); FLAG_Z=s.nextInt(); FLAG_S=s.nextInt(); FLAG_I=s.nextInt(); 
		interrupts=s.nextInt(); interrupt_deferred=s.nextInt();
		halted=s.nextInt()==1;
		setFlags();
	}
}
