package snes;

import java.util.Scanner;

public class SPC700 implements Runnable
{
	public static final int CYCLES_PER_64KHZTICK=1;
	public static final int MILLISECONDS_PER_INSTRUCTION=0;
	public boolean doprint=false;
	
	//registers
	int A,X,Y,S,P;
	int PC;
	boolean CARRY,ZERO,INTERRUPTSENABLED,HALFCARRY,BREAK,DIRECTPAGE,OVERFLOW,NEGATIVE;

	long instructionCount=0;
	long cycleCount=6;
	boolean waitForInterrupt=false;
	
	String opcodeName;
	int opcode;
	
	public byte[] memory;
	
	public boolean[] timerenabled;
	public int[] timermax;
	public int[] timercount;
	public int[] timerticks;
	public long[] timercycles;
	public byte[] outport;
	public int dspAddress;
	
	//flag bits
	private static final int CARRYFLAGBIT=1,ZEROFLAGBIT=2,INTERRUPTSENABLEDFLAGBIT=4,HALFCARRYFLAGBIT=8,BREAKFLAGBIT=16,DIRECTPAGEFLAGBIT=32,OVERFLOWFLAGBIT=64,NEGATIVEFLAGBIT=128;

	SNES snes;
	public SPC700(SNES snes)
	{
		this.snes=snes;
		memory=new byte[0x10000];
		timerenabled=new boolean[3];
		timermax=new int[3];
		timercount=new int[3];
		timerticks=new int[3];
		timercycles=new long[3];
		outport=new byte[4];		
	}
	
	public void reset()
	{
		PC=0xffc0;
		S=0xef;
		P=0; A=0; X=0; Y=0;
		instructionCount=0;
		cycleCount=6;
		memory=new byte[0x10000];
		memory[0xf1]=(byte)0xb0;
		waitForInterrupt=false;
		for (int i=0; i<3; i++)
		{
			timerenabled[i]=false;
			timercount[i]=0;
			timermax[i]=256;
			timerticks[i]=0;
			timercycles[i]=0;
		}
		outport=new byte[4];
		dspAddress=0;
		snes.dsp.reset();
		updateP();
	}
	
	public void runOnce()
	{
//		long cyclediff=cycleCount;
		if(doprint)
			printState();
		doAnInstruction();
//		cyclediff=cycleCount-cyclediff;
		for (int i=0; i<3; i++)
			updateTimer(i);		
	}
	
	public boolean audiothread=false;
	public void run()
	{
		long cyclediff=0;
		while(audiothread)
		{			
			cyclediff=cycleCount;
			doAnInstruction();
			cyclediff=cycleCount-cyclediff;
			
			if(doprint)
				printState();
			for (int i=0; i<3; i++)
				updateTimer(i);

			snes.pauselock.testlock();
			snes.processor.instructionCount+=snes.APU_INSTRUCTIONS_PER_CPU_INSTRUCTION;
			if((snes.processor.instructionCount&131071)==0)
			{
				snes.dsp.soundplayer.dumpsound();
			}
		}
	}

//	byte lastdspwrite=0;
	public void write_dsp(byte value)
	{
		snes.dsp.write(dspAddress,value);
//		System.out.printf("WRITING DSP %x: %x,  %d\n",dspAddress,value,instructionCount);
	}
	
	public byte read_dsp()
	{
//		System.out.println("READ FROM DSP "+instructionCount);
		return snes.dsp.read(dspAddress);
	}
	
	public byte readOutputPort(int address)
	{
		if(doprint)
			snes.snesgui.spc700trace.printf("out %x %x\n",address,outport[address]&0xff);
		return outport[address];
	}
//	int lastapuwrite=0;
//	boolean aputransferongoing=false;
	public void writeInputPort(int address, byte b)
	{
/*if(address==0) 
	{
	if (b==lastapuwrite+1 && !aputransferongoing)
	{
		System.out.printf("APU data transfer starting %x\n", b);
		aputransferongoing=true;
	}
	if (b!=0 && b!=lastapuwrite && b!=lastapuwrite+1 && aputransferongoing)
	{
			System.out.printf("APU data transfer done %x\n", b);
			aputransferongoing=false;
			snes.dsp.synchronizeSound();
	}
	lastapuwrite=b;
}*/
		if(doprint)
			snes.snesgui.spc700trace.printf("in %x %x\n",address,b&0xff);
		memory[address+0xf4]=b;
	}

	public byte readByte(int address)
	{
		address&=0xffff;
//		if(address==0xf4)
//			System.out.printf("%x\n",memory[0xf4]&0xff);
		//dsp
		if (address==0xf3)
			{
//			System.out.println("READ FROM DSP "+instructionCount);
			return read_dsp();
			}
		
		if(address>=0xfa && address<=0xfc) return 0;
		if (address>=0xfd && address<=0xff)
		{
			updateTimer(address-0xfd);
			int rval=timerticks[address-0xfd];
			timerticks[address-0xfd]=0;
//System.out.println("read timer "+address+" "+rval+", "+instructionCount);
			return (byte)rval;
		}
		if (address>=0xffc0 && (memory[0xf1]&0x80)!=0)
//		if (address>=0xffc0)
			return (byte)ROM[address-0xffc0];
		return memory[address];
	}
	
	public void writeByte(int address, byte value)
	{
//if(address==0x119f) {System.out.printf("%x %x %d\n", address,value,instructionCount);}

		
		address&=0xffff;
//if(instructionCount>=97119){ System.out.printf("write to %x: %x\n", address,value); }
		if(address==0xf2)
			dspAddress=value&0xff;
		if(address==0xf3)
			write_dsp(value);

		if(address>=0xf4 && address<=0xf7)
		{
//			snes.memory.physicalMemory[0x2140+(address-0xf4)]=value;
			outport[address-0xf4]=value;
//			System.out.printf("out %x %x\n",address,outport[address]&0xff);
		}
		else if(address==0xf1)
			writeControlRegister(value);
		else if (address>=0xfa && address<=0xfc)
		{
			updateTimer(address-0xfa);
			timermax[address-0xfa]=value&0xff;
			if(timermax[address-0xfa]==0) timermax[address-0xfa]=256;
			if(timercount[address-0xfa]>timermax[address-0xfa])
				timercount[address-0xfa]-=256;
			memory[address]=value;
		}
		else
		{
			snes.dsp.update();
			memory[address]=value;
		}
	}
	public void writeControlRegister(byte value)
	{
		if((value&0x10)!=0)
		{
			memory[0xf4]=0;
			memory[0xf5]=0;
		}
		if((value&0x20)!=0)
		{
			memory[0xf6]=0;
			memory[0xf7]=0;
		}
		timerenabled[0]=((value&1)!=0);
		timerenabled[1]=((value&2)!=0);
		timerenabled[2]=((value&4)!=0);
		for(int i=0; i<=2; i++)
		{
			long shift=7;
			if(i==2) shift=4;
			long mask=-(1<<shift);
			if (timerenabled[i] && (memory[0xf1]&(1<<i))==0)
			{
				timercount[i]=0;
				timerticks[i]=0;
				timercycles[i]=cycleCount&mask;
			}
		}
		memory[0xf1]=value;
	}
	public void updateTimer(int timer)
	{
		long shift=7;
		if(timer==2) shift=4;
		long mask=-(1<<shift);
		long cycles=cycleCount-timercycles[timer];

		timercycles[timer]+=cycles&mask;
		if(!timerenabled[timer]) return;
		
		timercount[timer]=timercount[timer]+(int)(cycles>>shift);
//System.out.println("Timer count "+timer+" "+timercount[timer]+" max "+timermax[timer] +" latch "+timercycles[timer]);
		if(timercount[timer]<timermax[timer]) return;
		
		if(timermax[timer]>0) { timerticks[timer]+=timercount[timer]/timermax[timer];
		timerticks[timer]&=0xf;
		timercount[timer]=timercount[timer]%timermax[timer]; }
//System.out.println("Timer ticks "+timer+" "+timerticks[timer]);
		
/*		
		timercycles[timer]+=(int)cycles;
		int cyclewrap=128;
		if(timer==2) cyclewrap=16;
		if (timercycles[timer]>=cyclewrap)
		{
			timercycles[timer]-=cyclewrap;
			if(timerenabled[timer])
			{
				timercount[timer]++;
//System.out.println("Timer count "+timer+" "+timercount[timer]+" max "+timermax[timer]);
			}
		}		
		if(!timerenabled[timer]) return;
		int max=timermax[timer];
		if(max==0)max=256;
		if(timercount[timer]>=max)
		{
			timercount[timer]-=max;
			timerticks[timer]=(timerticks[timer]+1)&0xf;
System.out.println("Timer ticks "+timer+" "+timerticks[timer]);
		}*/
	}
	
	private int fetch()
	{
		int value=readByte(PC&0xffff);
		PC=(PC+1)&0xffff;
		return value;
	}	
	
	
	public void doAnInstruction()
	{
		opcodeName="";
		opcode=fetch()&0xff;
		int value,addr,addr2,YA;
		
		//http://wiki.superfamicom.org/snes/show/SPC700+Reference
		switch(opcode)
		{
		//ADC (X), (Y) 	(X) = (X)+(Y)+C 	99	NV–H-ZC	1	5
		case 0x99:
			opcodeName="ADC (X),(Y)";
			writeByte((X&0xff)+((DIRECTPAGE)?0x100:0),(byte)ADC(readByte((0xff&X)+((DIRECTPAGE)?0x100:0)),readByte((0xff&Y)+((DIRECTPAGE)?0x100:0))));
			cycleCount+=5;
			break;
		//ADC A, #i 	A = A+i+C 	88	NV–H-ZC	2	2
		case 0x88:
			opcodeName="ADC A,#i";
			A=(ADC(A&0xff,fetch()&0xff)&0xff);
			cycleCount+=2;
			break;
		//ADC A, (X) 	A = A+(X)+C 	86	NV–H-ZC	1	3
		case 0x86:
			opcodeName="ADC A,(X)";
			A=(ADC(A&0xff,readByte((X&0xff)+((DIRECTPAGE)?0x100:0))&0xff)&0xff);
			cycleCount+=3;
			break;
		//ADC A, [d]+Y 	A = A+([d]+Y)+C 	97	NV–H-ZC	2	6
		case 0x97:
			opcodeName="ADC A,[d]+Y";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0);
			addr=(readByte(addr)&0xff)+((readByte(addr+1)&0xff)<<8)+(Y&0xff);
			A=(ADC(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=6;
			break;
		//ADC A, [d+X] 	A = A+([d+X])+C 	87	NV–H-ZC	2	6
		case 0x87:
			opcodeName="ADC A,[d+X]";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0);
			addr+=X&0xff;
			addr=(readByte(addr)&0xff)+((readByte(addr+1)&0xff)<<8);
			A=(ADC(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=6;
			break;
		//ADC A, d 	A = A+(d)+C 	84	NV–H-ZC	2	3
		case 0x84:
			opcodeName="ADC A,d";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			A=(ADC(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=3;
			break;
		//ADC A, d+X 	A = A+(d+X)+C 	94	NV–H-ZC	2	4
		case 0x94:
			opcodeName="ADC A,d+X";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff)+(X&0xff);
			A=(ADC(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=4;
			break;
		//ADC A, !a 	A = A+(a)+C 	85	NV–H-ZC	3	4
		case 0x85:
			opcodeName="ADC A,a";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			A=(ADC(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=4;
			break;
		//ADC A, !a+X 	A = A+(a+X)+C 	95	NV–H-ZC	3	5
		case 0x95:
			opcodeName="ADC A,a+X";
			addr=((fetch()&0xff)|((fetch()&0xff)<<8))+(X&0xff);
			A=(ADC(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=5;
			break;
		//ADC A, !a+Y 	A = A+(a+Y)+C 	96	NV–H-ZC	3	5
		case 0x96:
			opcodeName="ADC A,a+Y";
			addr=((fetch()&0xff)|((fetch()&0xff)<<8))+(Y&0xff);
			A=(ADC(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=5;
			break;
		//ADC dd, ds 	(dd) = (dd)+(d)+C 	89	NV–H-ZC	3	6
		case 0x89:
			opcodeName="ADC dd,ds";
			addr2=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(ADC(readByte(addr)&0xff,readByte(addr2)&0xff)&0xff));
			cycleCount+=6;
			break;
		//ADC d, #i 	(d) = (d)+i+C 	98	NV–H-ZC	3	5
		case 0x98:
			opcodeName="ADC d,#i";
			value=fetch()&0xff;
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(ADC(readByte(addr)&0xff,value)&0xff));
			cycleCount+=5;
			break;
		//ADDW YA, d 	YA = YA + (d), H on high byte 	7A	NV–H-ZC	2	5
		case 0x7a:
			opcodeName="ADDW YA,d";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			value=readByte(addr)&0xff;
			value|=(readByte(addr+1)&0xff)<<8;
			YA=Y<<8|A;
			CARRY=(YA+value>=0x10000);
			HALFCARRY=((YA&0xff)+(value&0xff)>=0x100);
			OVERFLOW=((~(YA^value)&(value^((YA+value)&0xffff)) & 0x8000)!=0);
			YA=((YA+value)&0xffff);
			ZERO= ((YA&0xffff)==0);
			NEGATIVE= ((YA&0x8000)!=0);
			A=YA&0xff;
			Y=(YA>>8)&0xff;
			cycleCount+=5;
			break;
		//AND (X), (Y) 	(X) = (X) & (Y) 	39	N—–Z-	1	5
		case 0x39:
			opcodeName="AND (X),(Y)";
			writeByte((X&0xff)+((DIRECTPAGE)?0x100:0),(byte)AND(readByte((0xff&X)+((DIRECTPAGE)?0x100:0)),readByte((0xff&Y)+((DIRECTPAGE)?0x100:0))));
			cycleCount+=5;
			break;
		//AND A, #i 	A = A & i 	28	N—–Z-	2	2
		case 0x28:
			opcodeName="AND A,#i";
			A=(AND(A&0xff,fetch()&0xff)&0xff);
			cycleCount+=2;
			break;
		//AND A, (X) 	A = A & (X) 	26	N—–Z-	1	3
		case 0x26:
			opcodeName="AND A,(X)";
			A=(AND(A&0xff,readByte((X&0xff)+((DIRECTPAGE)?0x100:0))&0xff)&0xff);
			cycleCount+=3;
			break;
		//AND A, [d]+Y 	A = A & ([d]+Y) 	37	N—–Z-	2	6
		case 0x37:
			opcodeName="AND A,[d]+Y";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0);
			addr=(readByte(addr)&0xff)+((readByte(addr+1)&0xff)<<8)+(Y&0xff);
			A=(AND(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=6;
			break;
		//AND A, [d+X] 	A = A & ([d+X]) 	27	N—–Z-	2	6
		case 0x27:
			opcodeName="AND A,[d+X]";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0);
			addr+=X&0xff;
			addr=(readByte(addr)&0xff)+((readByte(addr+1)&0xff)<<8);
			A=(AND(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=6;
			break;
		//AND A, d 	A = A & (d) 	24	N—–Z-	2	3
		case 0x24:
			opcodeName="AND A,d";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			A=(AND(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=3;
			break;
		//AND A, d+X 	A = A & (d+X) 	34	N—–Z-	2	4
		case 0x34:
			opcodeName="AND A,d+X";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff)+(X&0xff);
			A=(AND(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=4;
			break;
		//AND A, !a 	A = A & (a) 	25	N—–Z-	3	4
		case 0x25:
			opcodeName="AND A,a";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			A=(AND(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=4;
			break;
		//AND A, !a+X 	A = A & (a+X) 	35	N—–Z-	3	5
		case 0x35:
			opcodeName="AND A,a+X";
			addr=((fetch()&0xff)|((fetch()&0xff)<<8))+(X&0xff);
			A=(AND(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=5;
			break;
		//AND A, !a+Y 	A = A & (a+Y) 	36	N—–Z-	3	5
		case 0x36:
			opcodeName="AND A,a+Y";
			addr=((fetch()&0xff)|((fetch()&0xff)<<8))+(Y&0xff);
			A=(AND(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=5;
			break;
		//AND dd, ds 	(dd) = (dd) & (ds) 	29	N—–Z-	3	6
		case 0x29:
			opcodeName="AND dd,ds";
			addr2=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(AND(readByte(addr)&0xff,readByte(addr2)&0xff)&0xff));
			cycleCount+=6;
			break;
		//AND d, #i 	(d) = (d) & i 	38	N—–Z-	3	5
		case 0x38:
			opcodeName="AND d,#i";
			value=fetch()&0xff;
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(AND(readByte(addr)&0xff,value)&0xff));
			cycleCount+=5;
			break;
		//AND1 C, /m.b 	C = C & ~(m.b)	6A	——-C	3	4
		case 0x6a:
			opcodeName="AND1 C,~m.b";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			addr2=1<<(addr>>13);
			addr=addr&0x1fff;
			value=readByte(addr)&addr2;
			if(value!=0)
				CARRY=false;
			cycleCount+=4;
			break;
		//AND1 C, m.b 	C = C & (m.b)	4A	——-C	3	4
		case 0x4a:
			opcodeName="AND1 C,m.b";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			addr2=1<<(addr>>13);
			addr=addr&0x1fff;
			value=readByte(addr)&addr2;
			if(value!=0)
				CARRY=false;
			cycleCount+=4;
			break;
		//ASL A 	Left shift A: high->C, 0->low 	1C	N—–ZC	1	2
		case 0x1c:
			opcodeName="ASL A";
			A=(ASL(A&0xff)&0xff);
			cycleCount+=2;
			break;
		//ASL d 	Left shift (d) as above 	0B	N—–ZC	2	4
		case 0x0b:
			opcodeName="ASL A";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(ASL(readByte(addr)&0xff)&0xff));
			cycleCount+=4;
			break;
		//ASL d+X 	Left shift (d+X) as above 	1B	N—–ZC	2	5
		case 0x1b:
			opcodeName="ASL d+X";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff)+(X&0xff);
			writeByte(addr,(byte)(ASL(readByte(addr)&0xff)&0xff));
			cycleCount+=4;
			break;
		//ASL !a 	Left shift (a) as above 	0C	N—–ZC	3	5
		case 0x0c:
			opcodeName="ASL a";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			writeByte(addr,(byte)(ASL(readByte(addr)&0xff)&0xff));
			cycleCount+=5;
			break;
		//BBC d.0, r 	PC+=r if d.0 == 0 	13	——–	3	5/7
		case 0x13:
			opcodeName="BBC d.0,r";
			value=readByte(((DIRECTPAGE)?0x100:0)+(fetch()&0xff))&0xff;
			addr=fetch();
			cycleCount+=5;
			if((value&0x1)==0)
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;
		//BBC d.1, r 	PC+=r if d.1 == 0 	33	——–	3	5/7
		case 0x33:
			opcodeName="BBC d.1,r";
			value=readByte(((DIRECTPAGE)?0x100:0)+(fetch()&0xff))&0xff;
			addr=fetch();
			cycleCount+=5;
			if((value&0x2)==0)
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;
		//BBC d.2, r 	PC+=r if d.2 == 0 	53	——–	3	5/7
		case 0x53:
			opcodeName="BBC d.2,r";
			value=readByte(((DIRECTPAGE)?0x100:0)+(fetch()&0xff))&0xff;
			addr=fetch();
			cycleCount+=5;
			if((value&0x4)==0)
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;
		//BBC d.3, r 	PC+=r if d.3 == 0 	73	——–	3	5/7
		case 0x73:
			opcodeName="BBC d.3,r";
			value=readByte(((DIRECTPAGE)?0x100:0)+(fetch()&0xff))&0xff;
			addr=fetch();
			cycleCount+=5;
			if((value&0x8)==0)
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;
		//BBC d.4, r 	PC+=r if d.4 == 0 	93	——–	3	5/7
		case 0x93:
			opcodeName="BBC d.4,r";
			value=readByte(((DIRECTPAGE)?0x100:0)+(fetch()&0xff))&0xff;
			addr=fetch();
			cycleCount+=5;
			if((value&0x10)==0)
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;
		//BBC d.5, r 	PC+=r if d.5 == 0 	B3	——–	3	5/7
		case 0xB3:
			opcodeName="BBC d.0,r";
			value=readByte(((DIRECTPAGE)?0x100:0)+(fetch()&0xff))&0xff;
			addr=fetch();
			cycleCount+=5;
			if((value&0x20)==0)
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;
		//BBC d.6, r 	PC+=r if d.6 == 0 	D3	——–	3	5/7
		case 0xD3:
			opcodeName="BBC d.6,r";
			value=readByte(((DIRECTPAGE)?0x100:0)+(fetch()&0xff))&0xff;
			addr=fetch();
			cycleCount+=5;
			if((value&0x40)==0)
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;
		//BBC d.7, r 	PC+=r if d.7 == 0 	F3	——–	3	5/7
		case 0xF3:
			opcodeName="BBC d.7,r";
			value=readByte(((DIRECTPAGE)?0x100:0)+(fetch()&0xff))&0xff;
			addr=fetch();
			cycleCount+=5;
			if((value&0x80)==0)
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;
		//BBS d.0, r 	PC+=r if d.0 == 0 	03	——–	3	5/7
		case 0x03:
			opcodeName="BBS d.0,r";
			value=readByte(((DIRECTPAGE)?0x100:0)+(fetch()&0xff))&0xff;
			addr=fetch();
			cycleCount+=5;
			if((value&0x1)!=0)
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;
		//BBS d.1, r 	PC+=r if d.1 == 0 	23	——–	3	5/7
		case 0x23:
			opcodeName="BBS d.1,r";
			value=readByte(((DIRECTPAGE)?0x100:0)+(fetch()&0xff))&0xff;
			addr=fetch();
			cycleCount+=5;
			if((value&0x2)!=0)
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;
		//BBS d.2, r 	PC+=r if d.2 == 0 	43	——–	3	5/7
		case 0x43:
			opcodeName="BBS d.2,r";
			value=readByte(((DIRECTPAGE)?0x100:0)+(fetch()&0xff))&0xff;
			addr=fetch();
			cycleCount+=5;
			if((value&0x4)!=0)
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;
		//BBS d.3, r 	PC+=r if d.3 == 0 	63	——–	3	5/7
		case 0x63:
			opcodeName="BBS d.3,r";
			value=readByte(((DIRECTPAGE)?0x100:0)+(fetch()&0xff))&0xff;
			addr=fetch();
			cycleCount+=5;
			if((value&0x8)!=0)
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;
		//BBS d.4, r 	PC+=r if d.4 == 0 	83	——–	3	5/7
		case 0x83:
			opcodeName="BBS d.4,r";
			value=readByte(((DIRECTPAGE)?0x100:0)+(fetch()&0xff))&0xff;
			addr=fetch();
			cycleCount+=5;
			if((value&0x10)!=0)
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;
		//BBS d.5, r 	PC+=r if d.5 == 0 	A3	——–	3	5/7
		case 0xA3:
			opcodeName="BBS d.0,r";
			value=readByte(((DIRECTPAGE)?0x100:0)+(fetch()&0xff))&0xff;
			addr=fetch();
			cycleCount+=5;
			if((value&0x20)!=0)
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;
		//BBS d.6, r 	PC+=r if d.6 == 0 	C3	——–	3	5/7
		case 0xC3:
			opcodeName="BBS d.6,r";
			value=readByte(((DIRECTPAGE)?0x100:0)+(fetch()&0xff))&0xff;
			addr=fetch();
			cycleCount+=5;
			if((value&0x40)!=0)
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;
		//BBS d.7, r 	PC+=r if d.7 == 0 	E3	——–	3	5/7
		case 0xE3:
			opcodeName="BBS d.7,r";
			value=readByte(((DIRECTPAGE)?0x100:0)+(fetch()&0xff))&0xff;
			addr=fetch();
			cycleCount+=5;
			if((value&0x80)!=0)
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;
		//BCC r 	PC+=r if C == 0 	90	——–	2	2/4
		case 0x90:
			opcodeName="BCC r";
			addr=fetch();
			cycleCount+=2;
			if(!(CARRY))
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;
		//BCS r 	PC+=r if C == 1 	B0	——–	2	2/4
		case 0xB0:
			opcodeName="BCS r";
			addr=fetch();
			cycleCount+=2;
			if((CARRY))
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;

		//BEQ r 	PC+=r if Z == 1 	F0	——–	2	2/4
		case 0xF0:
			opcodeName="BEQ r";
			addr=fetch();
			cycleCount+=2;
			if((ZERO))
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;

		//BMI r 	PC+=r if N == 1 	30	——–	2	2/4
		case 0x30:
			opcodeName="BMI r";
			addr=fetch();
			cycleCount+=2;
			if((NEGATIVE))
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;

		//BNE r 	PC+=r if Z == 0 	D0	——–	2	2/4
		case 0xD0:
			opcodeName="BNE r";
			addr=fetch();
//if (instructionCount>=125314){System.out.printf("%x %x %x %x %x\n", PC,addr,(!(ZERO)?1:0),(PC+addr)&0xffff,fetch()); System.exit(0);}
			
			cycleCount+=2;
			if(!(ZERO))
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;

		//BPL r 	PC+=r if N == 0 	10	——–	2	2/4
		case 0x10:
			opcodeName="BPL r";
			addr=fetch();
			cycleCount+=2;
			if(!(NEGATIVE))
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;

		//BVC r 	PC+=r if V == 0 	50	——–	2	2/4
		case 0x50:
			opcodeName="BVC r";
			addr=fetch();
			cycleCount+=2;
			if(!(OVERFLOW))
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;

		//BVS r 	PC+=r if V == 1 	70	——–	2	2/4
		case 0x70:
			opcodeName="BVS r";
			addr=fetch();
			cycleCount+=2;
			if((OVERFLOW))
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;				
			
		//BRA r 	PC+=r 	2F	——–	2	4
		case 0x2F:
			opcodeName="BRA r";
			addr=fetch();
			PC=(PC+addr)&0xffff;
			cycleCount+=4;
			break;

		//BRK 	Push PC and Flags, PC = [$FFDE] 	0F	—1-0–	1	8
		case 0x0f:
			opcodeName="BRK";
			BRK();
			cycleCount+=8;
			break;
			
		//CALL !a 	(SP--)=PCh, (SP--)=PCl, PC=a 	3F	——–	3	8
		case 0x3f:
			opcodeName="CALL a";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			PUSHW(PC);
			PC=addr;
			cycleCount+=8;
			break;
					
		//CBNE d+X, r 	CMP A, (d+X) then BNE 	DE	——–	3	6/8
		case 0xde:
			opcodeName="CBNE d+X,r";
			addr=fetch()&0xff;
			addr+=(DIRECTPAGE)?0x100:0;
			addr+=X&0xff;
			value=readByte(addr)&0xff;
			addr=fetch();
			cycleCount+=6;
			if((A&0xff)-value!=0)
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;
			
		//CBNE d, r 	CMP A, (d) then BNE 	2E	——–	3	5/7
		case 0x2e:
			opcodeName="CBNE d,r";
			addr=fetch()&0xff;
			addr+=(DIRECTPAGE)?0x100:0;
			value=readByte(addr)&0xff;
			addr=fetch();
			cycleCount+=5;
			if((A&0xff)-value!=0)
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;
					
		//CLR1 d.0 	d.0 = 0 	12	——–	2	4
		case 0x12:
			opcodeName="CLR1 d.0";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(readByte(addr)&(~0x1)));
			cycleCount+=4;
			break;
		//CLR1 d.1 	d.1 = 0 	32	——–	2	4
		case 0x32:
			opcodeName="CLR1 d.1";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(readByte(addr)&(~0x2)));
			cycleCount+=4;
			break;
		//CLR1 d.2 	d.2 = 0 	52	——–	2	4
		case 0x52:
			opcodeName="CLR1 d.2";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(readByte(addr)&(~0x4)));
			cycleCount+=4;
			break;
		//CLR1 d.3 	d.3 = 0 	72	——–	2	4
		case 0x72:
			opcodeName="CLR1 d.3";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(readByte(addr)&(~0x8)));
			cycleCount+=4;
			break;
		//CLR1 d.4 	d.4 = 0 	92	——–	2	4
		case 0x92:
			opcodeName="CLR1 d.4";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(readByte(addr)&(~0x10)));
			cycleCount+=4;
			break;
		//CLR1 d.5 	d.5 = 0 	B2	——–	2	4
		case 0xb2:
			opcodeName="CLR1 d.5";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(readByte(addr)&(~0x20)));
			cycleCount+=4;
			break;
		//CLR1 d.6 	d.6 = 0 	D2	——–	2	4
		case 0xd2:
			opcodeName="CLR1 d.6";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(readByte(addr)&(~0x40)));
			cycleCount+=4;
			break;
		//CLR1 d.7 	d.7 = 0 	F2	——–	2	4
		case 0xf2:
			opcodeName="CLR1 d.7";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(readByte(addr)&(~0x80)));
			cycleCount+=4;
			break;
			
		//CLRC 	C = 0 	60	——-0	1	2
		case 0x60:
			opcodeName="CLRC";
			CARRY=false;
			cycleCount+=2;
			break;

		//CLRP 	P = 0 	20	–0—–	1	2
		case 0x20:
			opcodeName="CLRP";
			DIRECTPAGE=false;
			cycleCount+=2;
			break;
		//CLRV 	V = 0, H = 0 	E0	-0–0—	1	2
		case 0xe0:
			opcodeName="CLRV";
			OVERFLOW=false;
			HALFCARRY=false;
			cycleCount+=2;
			break;
					
		//CMP (X), (Y) 	(X) = (X)+(Y)+C 	79	NV–H-ZC	1	5
		case 0x79:
			opcodeName="CMP (X),(Y)";
			CMP(readByte((0xff&X)+((DIRECTPAGE)?0x100:0)),readByte((0xff&Y)+((DIRECTPAGE)?0x100:0)));
			cycleCount+=5;
			break;
		//CMP A, #i 	A = A+i+C 	68	NV–H-ZC	2	2
		case 0x68:
			opcodeName="CMP A,#i";
			CMP(A&0xff,fetch()&0xff);
			cycleCount+=2;
			break;
		//CMP A, (X) 	A = A+(X)+C 	66	NV–H-ZC	1	3
		case 0x66:
			opcodeName="CMP A,(X)";
			CMP(A&0xff,readByte((X&0xff)+((DIRECTPAGE)?0x100:0))&0xff);
			cycleCount+=3;
			break;
		//CMP A, [d]+Y 	A = A+([d]+Y)+C 	77	NV–H-ZC	2	6
		case 0x77:
			opcodeName="CMP A,[d]+Y";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0);
			addr=(readByte(addr)&0xff)+((readByte(addr+1)&0xff)<<8)+(Y&0xff);
			CMP(A&0xff,readByte(addr)&0xff);
			cycleCount+=6;
			break;
		//CMP A, [d+X] 	A = A+([d+X])+C 	67	NV–H-ZC	2	6
		case 0x67:
			opcodeName="CMP A,[d+X]";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0);
			addr+=X&0xff;
			addr=(readByte(addr)&0xff)+((readByte(addr+1)&0xff)<<8);
			CMP(A&0xff,readByte(addr)&0xff);
			cycleCount+=6;
			break;
		//CMP A, d 	A = A+(d)+C 	64	NV–H-ZC	2	3
		case 0x64:
			opcodeName="CMP A,d";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			CMP(A&0xff,readByte(addr)&0xff);
			cycleCount+=3;
			break;
		//CMP A, d+X 	A = A+(d+X)+C 	74	NV–H-ZC	2	4
		case 0x74:
			opcodeName="CMP A,d+X";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff)+(X&0xff);
			CMP(A&0xff,readByte(addr)&0xff);
			cycleCount+=4;
			break;
		//CMP A, !a 	A = A+(a)+C 	65	NV–H-ZC	3	4
		case 0x65:
			opcodeName="CMP A,a";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			CMP(A&0xff,readByte(addr)&0xff);
			cycleCount+=4;
			break;
		//CMP A, !a+X 	A = A+(a+X)+C 	75	NV–H-ZC	3	5
		case 0x75:
			opcodeName="CMP A,a+X";
			addr=((fetch()&0xff)|((fetch()&0xff)<<8))+(X&0xff);
			CMP(A&0xff,readByte(addr)&0xff);
			cycleCount+=5;
			break;
		//CMP A, !a+Y 	A = A+(a+Y)+C 	76	NV–H-ZC	3	5
		case 0x76:
			opcodeName="CMP A,a+Y";
			addr=((fetch()&0xff)|((fetch()&0xff)<<8))+(Y&0xff);
			CMP(A&0xff,readByte(addr)&0xff);
			cycleCount+=5;
			break;
		//CMP X, #i 	A = A+i+C 	c8	NV–H-ZC	2	3
		case 0xc8:
			opcodeName="CMP X,#i";
			CMP(X&0xff,fetch()&0xff);
			cycleCount+=2;
			break;
		//CMP X, d 	A = A+(d)+C 	3e	NV–H-ZC	2	3
		case 0x3e:
			opcodeName="CMP X,d";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			CMP(X&0xff,readByte(addr)&0xff);
//if(instructionCount>=1804128){ System.out.printf("3e: %x %x %d\n", addr,X,cycleCount); System.exit(0);}

			cycleCount+=3;
			break;
		//CMP X, !a 	A = A+(a)+C 	1e	NV–H-ZC	3	4
		case 0x1e:
			opcodeName="CMP X,a";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			CMP(X&0xff,readByte(addr)&0xff);
			cycleCount+=4;
			break;
		//CMP Y, #i 	A = A+i+C 	ad	NV–H-ZC	2	3
		case 0xad:
			opcodeName="CMP Y,#i";
			CMP(Y&0xff,fetch()&0xff);
			cycleCount+=2;
			break;
		//CMP Y, d 	A = A+(d)+C 	7e	NV–H-ZC	2	3
		case 0x7e:
			opcodeName="CMP Y,d";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			CMP(Y&0xff,readByte(addr)&0xff);
			cycleCount+=3;
			break;
		//CMP Y, !a 	A = A+(a)+C 	5e	NV–H-ZC	3	4
		case 0x5e:
			opcodeName="CMP Y,a";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			CMP(Y&0xff,readByte(addr)&0xff);
			cycleCount+=4;
			break;
		//CMP dd, ds 	(dd) = (dd)+(d)+C 	69	NV–H-ZC	3	6
		case 0x69:
			opcodeName="CMP dd,ds";
			addr2=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			CMP(readByte(addr)&0xff,readByte(addr2)&0xff);
			cycleCount+=6;
			break;
		//CMP d, #i 	(d) = (d)+i+C 	78	NV–H-ZC	3	5
		case 0x78:
			opcodeName="CMP d,#i";
			value=fetch()&0xff;
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
//System.out.println(addr+" "+readByte(addr)+" "+value);
//if(readByte(addr)!=0) System.exit(0);
			CMP(readByte(addr)&0xff,value);
			cycleCount+=5;
			break;
		//CMPW YA, d 	YA - (d) 	5A	N—–ZC	2	4
		case 0x5a:
			opcodeName="CMPW YA,d";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			value=readByte(addr)&0xff;
			value|=(readByte((addr+1)&0xffff)&0xff)<<8;
			CMP16((Y<<8)|A,value);
			cycleCount+=4;
			break;

		//DAA A 	decimal adjust for addition 	DF	N—–ZC	1	3
		case 0xdf:
			opcodeName="DAA A";
			if((A&0xff)>0x99 || (CARRY))
			{
				A=(A+0x60);
				CARRY=true;
			}
			if((A&0xf)>9 || (HALFCARRY))
			{
				A=(A+0x6);
			}
			cycleCount+=3;
			break;
		//DAS A 	decimal adjust for subtraction 	BE	N—–ZC	1	3
		case 0xbe:
			opcodeName="DAS A";
			if((A&0xff)>0x99 || (CARRY))
			{
				A=(A-0x60);
				CARRY=true;
			}
			if((A&0xf)>9 || (HALFCARRY))
			{
				A=(A-0x6);
			}
			cycleCount+=3;
			break;
		//DBNZ Y, r 	Y-- then JNZ 	FE	——–	2	4/6
		case 0xfe:
			opcodeName="DBNZ Y,r";
			addr=fetch();
			Y=(Y-1)&0xff;
			cycleCount+=4;
			if(Y!=0)
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;
			
		//DBNZ d, r 	(d)-- then JNZ 	6E	——–	3	5/7
		case 0x6e:
			opcodeName="DBNZ d,r";
			addr2=fetch()&0xff;
			addr2+=(DIRECTPAGE)?0x100:0;
			addr=fetch();
			writeByte(addr2,(byte)((readByte(addr2)-1)&0xff));
			cycleCount+=5;
			if(readByte(addr2)!=0)
			{
				PC=(PC+addr)&0xffff;
				cycleCount+=2;
			}
			break;

		//DEC A 	A++ 	9C	N—–Z-	1	2
		case 0x9c:
			opcodeName="DEC A";
			A=((A-1)&0xff);
			ZERO= ((A&0xff)==0);
			NEGATIVE= ((A&0x80)!=0);
			cycleCount+=2;
			break;
		//DEC X 	X++ 	1D	N—–Z-	1	2
		case 0x1d:
			opcodeName="DEC X";
			X=(X-1)&0xff;
			ZERO= ((X&0xff)==0);
			NEGATIVE= ((X&0x80)!=0);
			cycleCount+=2;
			break;
		//DEC Y 	Y++ 	dC	N—–Z-	1	2
		case 0xdc:
			opcodeName="DEC Y";
			Y=(Y-1)&0xff;
			ZERO= ((Y&0xff)==0);
			NEGATIVE= ((Y&0x80)!=0);
			cycleCount+=2;
			break;
		//DEC d 	(d)++ 	8B	N—–Z-	2	4
		case 0x8b:
			opcodeName="DEC d";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			value=(readByte(addr)-1)&0xff;
			ZERO= ((value&0xff)==0);
			NEGATIVE= ((value&0x80)!=0);
			writeByte(addr,(byte)value);
			cycleCount+=4;
			break;
		//DEC d+X 	(d+X)++ 	9B	N—–Z-	2	5
		case 0x9b:
			opcodeName="DEC d+X";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff)+(X&0xff);
			value=(readByte(addr)-1)&0xff;
			ZERO= ((value&0xff)==0);
			NEGATIVE= ((value&0x80)!=0);
			writeByte(addr,(byte)value);
			cycleCount+=5;
			break;
		//DEC !a 	(a)++ 	8C	N—–Z-	3	5
		case 0x8c:
			opcodeName="DEC a";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			value=(readByte(addr)-1)&0xff;
			ZERO= ((value&0xff)==0);
			NEGATIVE= ((value&0x80)!=0);
			writeByte(addr,(byte)value);
			cycleCount+=5;
			break;
		//DECW d 	Word (d)++ 	1A	N—–Z-	2	6
		case 0x1a:
			opcodeName="DECW d";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			value=readByte(addr)&0xff;
			value|=(readByte((addr+1)&0xffff)&0xff)<<8;
			value=(value-1)&0xffff;
			ZERO= ((value&0xffff)==0);
			NEGATIVE= ((value&0x8000)!=0);
			writeByte(addr,(byte)(value&0xff));
			writeByte((addr+1)&0xffff,(byte)(value>>8));
			cycleCount+=6;
			break;
		//DI 	I = 0 	C0	—–0–	1	3
		case 0xc0:
			opcodeName="DI";
			INTERRUPTSENABLED=false;
			cycleCount+=2;
			break;
		//DIV YA, X 	A=YA/X, Y=mod(YA,X) 	9E	NV–H-Z-	1	12
		case 0x9e:
			opcodeName="DIV";
			value=(Y<<8)|A;
			if(X!=0)
			{
				A=(value/X)&0xff;
				Y=(value%X)&0xff;
			}
			cycleCount+=12;
			break;
		//EI 	I = 1 	A0	—–1–	1	3
		case 0xa0:
			opcodeName="EI";
			INTERRUPTSENABLED=true;
			cycleCount+=3;
			break;
		//EOR (X), (Y) 	(X) = (X) & (Y) 	59	N—–Z-	1	5
		case 0x59:
			opcodeName="EOR (X),(Y)";
			writeByte((X&0xff)+((DIRECTPAGE)?0x100:0),(byte)EOR(readByte((0xff&X)+((DIRECTPAGE)?0x100:0)),readByte((0xff&Y)+((DIRECTPAGE)?0x100:0))));
			cycleCount+=5;
			break;
		//EOR A, #i 	A = A & i 	48	N—–Z-	2	2
		case 0x48:
			opcodeName="EOR A,#i";
			A=(EOR(A&0xff,fetch()&0xff)&0xff);
			cycleCount+=2;
			break;
		//EOR A, (X) 	A = A & (X) 	46	N—–Z-	1	3
		case 0x46:
			opcodeName="EOR A,(X)";
			A=(EOR(A&0xff,readByte((X&0xff)+((DIRECTPAGE)?0x100:0))&0xff)&0xff);
			cycleCount+=3;
			break;
		//EOR A, [d]+Y 	A = A & ([d]+Y) 	57	N—–Z-	2	6
		case 0x57:
			opcodeName="EOR A,[d]+Y";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0);
			addr=(readByte(addr)&0xff)+((readByte(addr+1)&0xff)<<8)+(Y&0xff);
			A=(EOR(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=6;
			break;
		//EOR A, [d+X] 	A = A & ([d+X]) 	47	N—–Z-	2	6
		case 0x47:
			opcodeName="EOR A,[d+X]";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0);
			addr+=X&0xff;
			addr=(readByte(addr)&0xff)+((readByte(addr+1)&0xff)<<8);
			A=(EOR(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=6;
			break;
		//EOR A, d 	A = A & (d) 	44	N—–Z-	2	3
		case 0x44:
			opcodeName="EOR A,d";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			A=(EOR(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=3;
			break;
		//EOR A, d+X 	A = A & (d+X) 	54	N—–Z-	2	4
		case 0x54:
			opcodeName="EOR A,d+X";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff)+(X&0xff);
			A=(EOR(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=4;
			break;
		//EOR A, !a 	A = A & (a) 	45	N—–Z-	3	4
		case 0x45:
			opcodeName="EOR A,a";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			A=(EOR(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=4;
			break;
		//EOR A, !a+X 	A = A & (a+X) 	55	N—–Z-	3	5
		case 0x55:
			opcodeName="EOR A,a+X";
			addr=((fetch()&0xff)|((fetch()&0xff)<<8))+(X&0xff);
			A=(EOR(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=5;
			break;
		//EOR A, !a+Y 	A = A & (a+Y) 	56	N—–Z-	3	5
		case 0x56:
			opcodeName="EOR A,a+Y";
			addr=((fetch()&0xff)|((fetch()&0xff)<<8))+(Y&0xff);
			A=(EOR(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=5;
			break;
		//EOR dd, ds 	(dd) = (dd) & (ds) 	49	N—–Z-	3	6
		case 0x49:
			opcodeName="EOR dd,ds";
			addr2=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(EOR(readByte(addr)&0xff,readByte(addr2)&0xff)&0xff));
			cycleCount+=6;
			break;
		//EOR d, #i 	(d) = (d) & i 	58	N—–Z-	3	5
		case 0x58:
			opcodeName="EOR d,#i";
			value=fetch()&0xff;
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(EOR(readByte(addr)&0xff,value)&0xff));
			cycleCount+=5;
			break;
		//EOR1 C, m.b 	C = C EOR (m.b) 	8A	——-C	3	5
		case 0x8a:
			opcodeName="EOR1 C,m.b";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			addr2=1<<(addr>>13);
			addr=addr&0x1fff;
			value=readByte(addr)&addr2;
			if(value!=1 && !(CARRY))
				CARRY=true;
			else if (value==0 && (CARRY))
				CARRY=true;
			else
				CARRY=false;
			cycleCount+=5;
			break;
		//INC A 	A++ 	BC	N—–Z-	1	2
		case 0xbc:
			opcodeName="INC A";
			A=((A+1)&0xff);
			ZERO= ((A&0xff)==0);
			NEGATIVE= ((A&0x80)!=0);
			cycleCount+=2;
			break;
		//INC X 	X++ 	3D	N—–Z-	1	2
		case 0x3d:
			opcodeName="INC X";
			X=(X+1)&0xff;
			ZERO= ((X&0xff)==0);
			NEGATIVE= ((X&0x80)!=0);
			cycleCount+=2;
			break;
		//INC Y 	Y++ 	FC	N—–Z-	1	2
		case 0xfc:
			opcodeName="INC Y";
			Y=(Y+1)&0xff;
			ZERO= ((Y&0xff)==0);
			NEGATIVE= ((Y&0x80)!=0);
			cycleCount+=2;
			break;
		//INC d 	(d)++ 	AB	N—–Z-	2	4
		case 0xab:
			opcodeName="INC d";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			value=(readByte(addr)+1)&0xff;
			ZERO= ((value&0xff)==0);
			NEGATIVE= ((value&0x80)!=0);
			writeByte(addr,(byte)value);
			cycleCount+=4;
			break;
		//INC d+X 	(d+X)++ 	BB	N—–Z-	2	5
		case 0xbb:
			opcodeName="INC d+X";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff)+(X&0xff);
			value=(readByte(addr)+1)&0xff;
			ZERO= ((value&0xff)==0);
			NEGATIVE= ((value&0x80)!=0);
			writeByte(addr,(byte)value);
			cycleCount+=5;
			break;
		//INC !a 	(a)++ 	AC	N—–Z-	3	5
		case 0xac:
			opcodeName="INC a";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			value=(readByte(addr)+1)&0xff;
			ZERO= ((value&0xff)==0);
			NEGATIVE= ((value&0x80)!=0);
			writeByte(addr,(byte)value);
			cycleCount+=5;
			break;
		//INCW d 	Word (d)++ 	3A	N—–Z-	2	6
		case 0x3a:
			opcodeName="INCW d";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			value=readByte(addr)&0xff;
			value|=(readByte((addr+1)&0xffff)&0xff)<<8;
			value=(value+1)&0xffff;
			ZERO= ((value&0xffff)==0);
			NEGATIVE= ((value&0x8000)!=0);
			writeByte(addr,(byte)(value&0xff));
			writeByte((addr+1)&0xffff,(byte)(value>>8));
			cycleCount+=6;
			break;
		//JMP [!a+X] 	PC = [a+X] 	1F	——–	3	6
		case 0x1f:
			opcodeName="JMP [a+X]";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			addr+=X&0xff;
			value=readByte(addr)&0xff;
			value|=(readByte((addr+1)&0xffff)&0xff)<<8;
			PC=value;
			cycleCount+=6;
			break;
		//JMP !a 	PC = a 	5F	——–	3	3
		case 0x5f:
			opcodeName="JMP a";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			PC=addr;
			cycleCount+=3;
			break;
		//LSR A 	Left shift A: high->C, 0->low 	1C	N—–ZC	1	2
		case 0x5c:
			opcodeName="LSR A";
			A=(LSR(A&0xff)&0xff);
			cycleCount+=2;
			break;
		//LSR d 	Left shift (d) as above 	0B	N—–ZC	2	4
		case 0x4b:
			opcodeName="LSR A";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(LSR(readByte(addr)&0xff)&0xff));
			cycleCount+=4;
			break;
		//LSR d+X 	Left shift (d+X) as above 	1B	N—–ZC	2	5
		case 0x5b:
			opcodeName="LSR d+X";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff)+(X&0xff);
			writeByte(addr,(byte)(LSR(readByte(addr)&0xff)&0xff));
			cycleCount+=4;
			break;
		//LSR !a 	Left shift (a) as above 	0C	N—–ZC	3	5
		case 0x4c:
			opcodeName="LSR a";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			writeByte(addr,(byte)(LSR(readByte(addr)&0xff)&0xff));
			cycleCount+=5;
			break;
			
		//MOV (X)+, A 	(X++) = A (no read) 	AF	——–	1	4
		case 0xaf:
			opcodeName="MOV (X)+,A";
			writeByte((X&0xff)+((DIRECTPAGE)?0x100:0),(byte)(A&0xff));
			X=(X+1)&0xff;
			cycleCount+=4;
			break;
		//MOV (X), A 	(X) = A (read) 	C6	——–	1	4
		case 0xc6:
			opcodeName="MOV (X),A";
			writeByte((X&0xff)+((DIRECTPAGE)?0x100:0),(byte)(A&0xff));
			cycleCount+=4;
			break;
		//MOV [d]+Y, A 	([d]+Y) = A (read) 	D7	——–	2	7
		case 0xd7:
			opcodeName="MOV [d]+Y,A";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0);
			addr=(readByte(addr)&0xff)+((readByte(addr+1)&0xff)<<8)+(Y&0xff);
//if(instructionCount>=28763){System.out.printf("d7 %x %x %d\n",addr,A,instructionCount); System.exit(0);}
			writeByte(addr,(byte)(A&0xff));
			cycleCount+=7;
			break;
		//MOV [d+X], A 	([d+X]) = A (read) 	C7	——–	2	7
		case 0xc7:
			opcodeName="MOV [d+X],A";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0);
			addr+=X&0xff;
			addr=(readByte(addr)&0xff)+((readByte(addr+1)&0xff)<<8);
			writeByte(addr,(byte)(A&0xff));
			cycleCount+=7;
			break;
		//MOV A, #i 	A = i 	E8	N—–Z-	2	2
		case 0xe8:
			opcodeName="MOV A,#i";
			A=(fetch()&0xff);
			cycleCount+=2;
			ZERO= ((A&0xff)==0);
			NEGATIVE= ((A&0x80)!=0);
			break;
		//MOV A, (X) 	A = (X) 	E6	N—–Z-	1	3
		case 0xe6:
			opcodeName="MOV A,(X)";
			value=readByte((X&0xff)+((DIRECTPAGE)?0x100:0))&0xff;
			A=(value);
			cycleCount+=3;
			ZERO= ((A&0xff)==0);
			NEGATIVE= ((A&0x80)!=0);
			break;
		//MOV A, (X)+ 	A = (X++) 	BF	N—–Z-	1	4
		case 0xbf:
			opcodeName="MOV A,(X)+";
			value=readByte((X&0xff)+((DIRECTPAGE)?0x100:0))&0xff;
			A=(value);
			X=(X+1)&0xff;
			cycleCount+=4;
			ZERO= ((A&0xff)==0);
			NEGATIVE= ((A&0x80)!=0);
			break;
		//MOV A, [d]+Y 	A = ([d]+Y) 	F7	N—–Z-	2	6
		case 0xf7:
			opcodeName="MOV A,[d]+Y";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0);
			addr=(readByte(addr)&0xff)+((readByte(addr+1)&0xff)<<8)+(Y&0xff);
			value=readByte(addr)&0xff;
			A=(value);
			cycleCount+=6;
			ZERO= ((A&0xff)==0);
			NEGATIVE= ((A&0x80)!=0);
			break;
		//MOV A, [d+X] 	A = ([d+X]) 	E7	N—–Z-	2	6
		case 0xe7:
			opcodeName="MOV A,[d+X]";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0);
			addr+=X&0xff;
			addr=(readByte(addr)&0xff)+((readByte(addr+1)&0xff)<<8);
			value=readByte(addr)&0xff;
			A=(value);
			cycleCount+=6;
			ZERO= ((A&0xff)==0);
			NEGATIVE= ((A&0x80)!=0);
			break;
		//MOV A, X 	A = X 	7D	N—–Z-	1	2
		case 0x7d:
			opcodeName="MOV A,X";
			A=(X&0xff);
			cycleCount+=2;
			ZERO= ((A&0xff)==0);
			NEGATIVE= ((A&0x80)!=0);
			break;
		//MOV A, Y 	A = Y 	DD	N—–Z-	1	2
		case 0xdd:
			opcodeName="MOV A,Y";
			A=(Y&0xff);
			cycleCount+=2;
			ZERO= ((A&0xff)==0);
			NEGATIVE= ((A&0x80)!=0);
			break;
		//MOV A, d 	A = (d) 	E4	N—–Z-	2	3
		case 0xe4:
			opcodeName="MOV A,d";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0);
			value=readByte(addr)&0xff;
//if(instructionCount>=97219){ System.out.printf("e4: %x %x %x\n", addr,value,((DIRECTPAGE)?0x100:0)); System.exit(0);}
			A=(value);
			cycleCount+=3;
			ZERO= ((A&0xff)==0);
			NEGATIVE= ((A&0x80)!=0);
			break;
		//MOV A, d+X 	A = (d+X) 	F4	N—–Z-	2	4
		case 0xf4:
			opcodeName="MOV A,d+X";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0)+(X&0xff);
			value=readByte(addr)&0xff;
			A=(value);
			cycleCount+=4;
			ZERO= ((A&0xff)==0);
			NEGATIVE= ((A&0x80)!=0);
			break;
		//MOV A, !a 	A = (a) 	E5	N—–Z-	3	4
		case 0xe5:
			opcodeName="MOV A,a";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			value=readByte(addr)&0xff;
			A=(value);
			cycleCount+=4;
			ZERO= ((A&0xff)==0);
			NEGATIVE= ((A&0x80)!=0);
			break;
		//MOV A, !a+X 	A = (a+X) 	F5	N—–Z-	3	5
		case 0xf5:
			opcodeName="MOV A,a+X";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			addr+=(X&0xff);
			value=readByte(addr)&0xff;
			A=(value);
			cycleCount+=5;
			ZERO= ((A&0xff)==0);
			NEGATIVE= ((A&0x80)!=0);
			break;
		//MOV A, !a+Y 	A = (a+Y) 	F6	N—–Z-	3	5
		case 0xf6:
			opcodeName="MOV A,a+Y";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			addr+=(Y&0xff);
			value=readByte(addr)&0xff;
			A=(value);
			cycleCount+=5;
			ZERO= ((A&0xff)==0);
			NEGATIVE= ((A&0x80)!=0);
			break;
		//MOV SP, X 	SP = X 	BD	——–	1	2
		case 0xbd:
			opcodeName="MOV SP,X";
			S=X;
			cycleCount+=2;
			break;
		//MOV X, #i 	X = i 	CD	N—–Z-	2	2
		case 0xcd:
			opcodeName="MOV X,#i";
			X=fetch()&0xff;
			cycleCount+=2;
			ZERO= ((X&0xff)==0);
			NEGATIVE= ((X&0x80)!=0);
			break;
		//MOV X, A 	X = A 	5D	N—–Z-	1	2
		case 0x5d:
			opcodeName="MOV X,A";
			X=A&0xff;
			cycleCount+=2;
			ZERO= ((X&0xff)==0);
			NEGATIVE= ((X&0x80)!=0);
			break;
		//MOV X, SP 	X = SP 	9D	N—–Z-	1	2
		case 0x9d:
			opcodeName="MOV X,SP";
			X=S;
			cycleCount+=2;
			ZERO= ((X&0xff)==0);
			NEGATIVE= ((X&0x80)!=0);
			break;
		//MOV X, d 	X = (d) 	F8	N—–Z-	2	3
		case 0xf8:
			opcodeName="MOV X,d";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0);
			X=readByte(addr)&0xff;
			cycleCount+=3;
			ZERO= ((X&0xff)==0);
			NEGATIVE= ((X&0x80)!=0);
			break;
		//MOV X, d+Y 	X = (d+Y) 	F9	N—–Z-	2	4
		case 0xf9:
			opcodeName="MOV X,d+Y";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0)+(Y&0xff);
			X=readByte(addr)&0xff;
			cycleCount+=4;
			ZERO= ((X&0xff)==0);
			NEGATIVE= ((X&0x80)!=0);
			break;
		//MOV X, !a 	X = (a) 	E9	N—–Z-	3	4
		case 0xe9:
			opcodeName="MOV X,a";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			X=readByte(addr)&0xff;
			cycleCount+=4;
			ZERO= ((X&0xff)==0);
			NEGATIVE= ((X&0x80)!=0);
			break;
		//MOV Y, #i 	Y = i 	8D	N—–Z-	2	2
		case 0x8d:
			opcodeName="MOV Y,#i";
			Y=fetch()&0xff;
			cycleCount+=2;
			ZERO= ((Y&0xff)==0);
			NEGATIVE= ((Y&0x80)!=0);
			break;
		//MOV Y, A 	Y = A 	FD	N—–Z-	1	2
		case 0xfd:
			opcodeName="MOV Y,A";
			Y=A&0xff;
			cycleCount+=2;
			ZERO= ((Y&0xff)==0);
			NEGATIVE= ((Y&0x80)!=0);
			break;
		//MOV Y, d 	Y = (d) 	EB	N—–Z-	2	3
		case 0xeb:
			opcodeName="MOV Y,d";
			cycleCount+=2;
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0);
			Y=readByte(addr)&0xff;
//if(instructionCount>=106569){ System.out.printf("eb: %x %x %d\n", addr,Y,cycleCount); System.exit(0);}
			ZERO= ((Y&0xff)==0);
			NEGATIVE= ((Y&0x80)!=0);
			cycleCount+=1;
			break;
		//MOV Y, d+X 	Y = (d+X) 	FB	N—–Z-	2	4
		case 0xfb:
			opcodeName="MOV Y,d+X";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0)+(X&0xff);
			Y=readByte(addr)&0xff;
			cycleCount+=4;
			ZERO= ((Y&0xff)==0);
			NEGATIVE= ((Y&0x80)!=0);
			break;
		//MOV Y, !a 	Y = (a) 	EC	N—–Z-	3	4
		case 0xec:
			opcodeName="MOV Y,a";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			Y=readByte(addr)&0xff;
			cycleCount+=4;
			ZERO= ((Y&0xff)==0);
			NEGATIVE= ((Y&0x80)!=0);
			break;
		//MOV dd, ds 	(dd) = (ds) (no read) 	FA	——–	3	5
		case 0xfa:
			opcodeName="MOV dd,ss";
			addr2=(fetch()&0xff)+((DIRECTPAGE)?0x100:0);
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0);
			writeByte(addr,readByte(addr2));
			cycleCount+=5;
			break;
		//MOV d+X, A 	(d+X) = A (read) 	D4	——–	2	5
		case 0xd4:
			opcodeName="MOV d+X,A";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0)+(X&0xff);
			writeByte(addr,(byte)(A&0xff));
			cycleCount+=5;
			break;
		//MOV d+X, Y 	(d+X) = Y (read) 	DB	——–	2	5
		case 0xdb:
			opcodeName="MOV d+X,Y";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0)+(X&0xff);
			writeByte(addr,(byte)(Y&0xff));
			cycleCount+=5;
			break;
		//MOV d+Y, X 	(d+Y) = X (read) 	D9	——–	2	5
		case 0xd9:
			opcodeName="MOV d+Y,X";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0)+(Y&0xff);
			writeByte(addr,(byte)(X&0xff));
			cycleCount+=5;
			break;
		//MOV d, #i 	(d) = i (read) 	8F	——–	3	5
		case 0x8f:
			opcodeName="MOV d,i";
			value=fetch()&0xff;
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0);
			writeByte(addr,(byte)(value));
			cycleCount+=5;
			break;
		//MOV d, A 	(d) = A (read) 	C4	——–	2	4
		case 0xc4:
			opcodeName="MOV d,A";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0);
			value=A&0xff;
			writeByte(addr,(byte)(value));
			cycleCount+=4;
			break;
		//MOV d, X 	(d) = X (read) 	D8	——–	2	4
		case 0xd8:
			opcodeName="MOV d,X";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0);
			writeByte(addr,(byte)(X));
			cycleCount+=4;
			break;
		//MOV d, Y 	(d) = Y (read) 	CB	——–	2	4
		case 0xcb:
			opcodeName="MOV d,Y";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0);
//if(instructionCount>=7582){ System.out.printf("cb %x %x\n", addr,Y);}
			writeByte(addr,(byte)(Y));
			cycleCount+=4;
			break;
		//MOV !a+X, A 	(a+X) = A (read) 	D5	——–	3	6
		case 0xd5:
			opcodeName="MOV a+X,A";
			addr=((fetch()&0xff)+((fetch()&0xff)<<8))+(X&0xff);
			value=A&0xff;
			writeByte(addr,(byte)(value));
			cycleCount+=6;
			break;
		//MOV !a+Y, A 	(a+Y) = A (read) 	D6	——–	3	6
		case 0xd6:
			opcodeName="MOV a+Y,A";
			addr=((fetch()&0xff)+((fetch()&0xff)<<8))+(Y&0xff);
			value=A&0xff;
			writeByte(addr,(byte)(value));
			cycleCount+=6;
			break;
		//MOV !a, A 	(a) = A (read) 	C5	——–	3	5
		case 0xc5:
			opcodeName="MOV a,A";
			addr=(fetch()&0xff)+((fetch()&0xff)<<8);
			value=A&0xff;
			writeByte(addr,(byte)(value));
			cycleCount+=5;
			break;
		//MOV !a, X 	(a) = X (read) 	C9	——–	3	5
		case 0xc9:
			opcodeName="MOV a,X";
			addr=(fetch()&0xff)+((fetch()&0xff)<<8);
			value=X&0xff;
			writeByte(addr,(byte)(value));
			cycleCount+=5;
			break;
		//MOV !a, Y 	(a) = Y (read) 	CC	——–	3	5
		case 0xcc:
			opcodeName="MOV a,Y";
			addr=(fetch()&0xff)+((fetch()&0xff)<<8);
			value=Y&0xff;
			writeByte(addr,(byte)(value));
			cycleCount+=5;
			break;
		//MOV1 C, m.b 	C = (m.b) 	AA	——-C	3	4
		case 0xaa:
			opcodeName="MOV1 C,m.b";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			addr2=1<<(addr>>13);
			addr=addr&0x1fff;
			value=readByte(addr)&addr2;
			CARRY=(value!=0);
			cycleCount+=4;
			break;
		//MOV1 m.b, C 	(m.b) = C 	CA	——–	3	6
		case 0xca:
			opcodeName="MOV1 m.b,C";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			addr2=1<<(addr>>13);
			addr=addr&0x1fff;
			value=readByte(addr)&(~addr2);
			if((CARRY))
				value|=addr2;
			writeByte(addr,(byte)value);
			cycleCount+=6;
			break;
		//MOVW YA, d 	YA = word (d) 	BA	N—–Z-	2	5
		case 0xba:
			opcodeName="MOVW YA,d";
			addr=fetch()&0xff;
			addr+=(DIRECTPAGE)?0x100:0;
			A=readByte(addr)&0xff;
			Y=(readByte((addr+1)&0xffff)&0xff);
			ZERO= (((Y<<8)|A)&0xffff)==0;
			NEGATIVE= (((Y<<8)|A)&0x8000)!=0;
			cycleCount+=5;
			break;
		//MOVW d, YA 	word (d) = YA (read low only) 	DA	——–	2	5
		case 0xda:
			opcodeName="MOVW d,YA";
			addr=fetch()&0xff;
			addr+=(DIRECTPAGE)?0x100:0;
			writeByte(addr,(byte)(A&0xff));
			writeByte(addr+1,(byte)((Y)&0xff));
			cycleCount+=5;
			break;
		//MUL YA 	YA = Y * A, NZ on Y only 	CF	N—–Z-	1	9
		case 0xcf:
			opcodeName="MUL YA";
			YA=Y*A;
			A=YA&0xff;
			Y=YA>>8;
			ZERO= ((Y&0xff)==0);
			NEGATIVE= ((Y&0x80)!=0);
			cycleCount+=9;
			break;
		//NOP 	do nothing 	00	——–	1	2
		case 0x00:
			opcodeName="NOP";
			cycleCount+=2;
			break;
		//NOT1 m.b 	m.b = ~m.b 	EA	——–	3	5
		case 0xea:
			opcodeName="NOT1 m.b";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			addr2=1<<(addr>>13);
			addr=addr&0x1fff;
			value=readByte(addr)&0xff;
			value^=addr2;
			writeByte(addr,(byte)value);
			cycleCount+=5;
			break;
		//NOTC 	C = !C 	ED	——-C	1	3
		case 0xed:
			opcodeName="NOTC";
			CARRY=!CARRY;
			cycleCount+=3;
			break;
		//OR (X), (Y) 	(X) = (X) & (Y) 	19	N—–Z-	1	5
		case 0x19:
			opcodeName="OR (X),(Y)";
			writeByte((X&0xff)+((DIRECTPAGE)?0x100:0),(byte)OR(readByte((0xff&X)+((DIRECTPAGE)?0x100:0)),readByte((0xff&Y)+((DIRECTPAGE)?0x100:0))));
			cycleCount+=5;
			break;
		//OR A, #i 	A = A & i 	08	N—–Z-	2	2
		case 0x08:
			opcodeName="OR A,#i";
			A=(OR(A&0xff,fetch()&0xff)&0xff);
			cycleCount+=2;
			break;
		//OR A, (X) 	A = A & (X) 	06	N—–Z-	1	3
		case 0x06:
			opcodeName="OR A,(X)";
			A=(OR(A&0xff,readByte((X&0xff)+((DIRECTPAGE)?0x100:0))&0xff)&0xff);
			cycleCount+=3;
			break;
		//OR A, [d]+Y 	A = A & ([d]+Y) 	17	N—–Z-	2	6
		case 0x17:
			opcodeName="OR A,[d]+Y";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0);
			addr=(readByte(addr)&0xff)+((readByte(addr+1)&0xff)<<8)+(Y&0xff);
			A=(OR(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=6;
			break;
		//OR A, [d+X] 	A = A & ([d+X]) 	07	N—–Z-	2	6
		case 0x07:
			opcodeName="OR A,[d+X]";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0);
			addr+=X&0xff;
			addr=(readByte(addr)&0xff)+((readByte(addr+1)&0xff)<<8);
			A=(OR(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=6;
			break;
		//OR A, d 	A = A & (d) 	04	N—–Z-	2	3
		case 0x04:
			opcodeName="OR A,d";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			A=(OR(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=3;
			break;
		//OR A, d+X 	A = A & (d+X) 	14	N—–Z-	2	4
		case 0x14:
			opcodeName="OR A,d+X";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff)+(X&0xff);
			A=(OR(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=4;
			break;
		//OR A, !a 	A = A & (a) 	05	N—–Z-	3	4
		case 0x05:
			opcodeName="OR A,a";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			A=(OR(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=4;
			break;
		//OR A, !a+X 	A = A & (a+X) 	15	N—–Z-	3	5
		case 0x15:
			opcodeName="OR A,a+X";
			addr=((fetch()&0xff)|((fetch()&0xff)<<8))+(X&0xff);
			A=(OR(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=5;
			break;
		//OR A, !a+Y 	A = A & (a+Y) 	16	N—–Z-	3	5
		case 0x16:
			opcodeName="OR A,a+Y";
			addr=((fetch()&0xff)|((fetch()&0xff)<<8))+(Y&0xff);
			A=(OR(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=5;
			break;
		//OR dd, ds 	(dd) = (dd) & (ds) 	09	N—–Z-	3	6
		case 0x09:
			opcodeName="OR dd,ds";
			addr2=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(OR(readByte(addr)&0xff,readByte(addr2)&0xff)&0xff));
			cycleCount+=6;
			break;
		//OR d, #i 	(d) = (d) & i 	18	N—–Z-	3	5
		case 0x18:
			opcodeName="OR d,#i";
			value=fetch()&0xff;
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(OR(readByte(addr)&0xff,value)&0xff));
			cycleCount+=5;
			break;
		//OR1 C, /m.b 	C = C | ~(m.b)	2A	——-C	3	5
		case 0x2a:
			opcodeName="OR1 C,~m.b";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			addr2=1<<(addr>>13);
			addr=addr&0x1fff;
			value=readByte(addr)&addr2;
			if(value==0)
				CARRY=true;
			cycleCount+=5;
			break;
		//OR1 C, m.b 	C = C | (m.b)	0A	——-C	3	5
		case 0x0a:
			opcodeName="OR1 C,m.b";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			addr2=1<<(addr>>13);
			addr=addr&0x1fff;
			value=readByte(addr)&addr2;
			if(value!=0)
				CARRY=true;
			cycleCount+=5;
			break;
			
		//PCALL u 	CALL $FF00+u 	4F	——–	2	6
		case 0x4f:
			opcodeName="PCALL u";
			addr=fetch()&0xff;
			addr+=0xff00;
			PUSHW(PC);
			PC=readByte(addr)&0xff;
			PC|=(readByte(addr+1)&0xff)<<8;
			cycleCount+=6;
			break;
		//POP A 	A = (++SP) 	AE	——–	1	4
		case 0xae:
			opcodeName="POP A";
			A=(PULLB()&0xff);
			cycleCount+=4;
			break;
		//POP PSW 	Flags = (++SP) 	8E	NVPBHIZC	1	4
		case 0x8e:
			opcodeName="POP PSW";
			P=(PULLB()&0xff);
			updateFlagsFromP();
			cycleCount+=4;
			break;
		//POP X 	X = (++SP) 	CE	——–	1	4
		case 0xce:
			opcodeName="POP X";
			X=(PULLB()&0xff);
			cycleCount+=4;
			break;
		//POP Y 	Y = (++SP) 	EE	——–	1	4
		case 0xee:
			opcodeName="POP Y";
			Y=(PULLB()&0xff);
			cycleCount+=4;
			break;
		//PUSH A 	(SP--) = A 	2D	——–	1	4
		case 0x2d:
			opcodeName="PUSH A";
			PUSHB(A&0xff);
			cycleCount+=4;
			break;
		//PUSH PSW 	(SP--) = Flags 	0D	——–	1	4
		case 0x0d:
			opcodeName="PUSH PSW";
			updateP();
			PUSHB(P&0xff);
			cycleCount+=4;
			break;
		//PUSH X 	(SP--) = X 	4D	——–	1	4
		case 0x4d:
			opcodeName="PUSH X";
			PUSHB(X&0xff);
			cycleCount+=4;
			break;
		//PUSH Y 	(SP--) = Y 	6D	——–	1	4
		case 0x6d:
			opcodeName="PUSH Y";
			PUSHB(Y&0xff);
			cycleCount+=4;
			break;
		//RET 	Pop PC 	6F	——–	1	5
		case 0x6f:
			opcodeName="RET";
			PC=PULLW()&0xffff;
			cycleCount+=5;
			break;
		//RETI 	Pop Flags, PC 	7F	NVPBHIZC	1	6
		case 0x7f:
			opcodeName="RETI";
			P=PULLB()&0xff;
			updateFlagsFromP();
			PC=PULLW()&0xffff;
			cycleCount+=6;
			break;
		//ROL A 	Left shift A: high->C, 0->low 	1C	N—–ZC	1	2
		case 0x3c:
			opcodeName="ROL A";
			A=(ROL(A&0xff)&0xff);
			cycleCount+=2;
			break;
		//ROL d 	Left shift (d) as above 	0B	N—–ZC	2	4
		case 0x2b:
			opcodeName="ROL A";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(ROL(readByte(addr)&0xff)&0xff));
			cycleCount+=4;
			break;
		//ROL d+X 	Left shift (d+X) as above 	1B	N—–ZC	2	5
		case 0x3b:
			opcodeName="ROL d+X";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff)+(X&0xff);
			writeByte(addr,(byte)(ROL(readByte(addr)&0xff)&0xff));
			cycleCount+=4;
			break;
		//ROL !a 	Left shift (a) as above 	0C	N—–ZC	3	5
		case 0x2c:
			opcodeName="ROL a";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			writeByte(addr,(byte)(ROL(readByte(addr)&0xff)&0xff));
			cycleCount+=5;
			break;
		//ROR A 	Left shift A: high->C, 0->low 	1C	N—–ZC	1	2
		case 0x7c:
			opcodeName="ROR A";
			A=(ROR(A&0xff)&0xff);
			cycleCount+=2;
			break;
		//ROR d 	Left shift (d) as above 	0B	N—–ZC	2	4
		case 0x6b:
			opcodeName="ROR A";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(ROR(readByte(addr)&0xff)&0xff));
			cycleCount+=4;
			break;
		//ROR d+X 	Left shift (d+X) as above 	1B	N—–ZC	2	5
		case 0x7b:
			opcodeName="ROR d+X";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff)+(X&0xff);
			writeByte(addr,(byte)(ROR(readByte(addr)&0xff)&0xff));
			cycleCount+=4;
			break;
		//ROR !a 	Left shift (a) as above 	0C	N—–ZC	3	5
		case 0x6c:
			opcodeName="ROR a";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			writeByte(addr,(byte)(ROR(readByte(addr)&0xff)&0xff));
			cycleCount+=5;
			break;

		//SBC (X), (Y) 	(X) = (X)+(Y)+C 	B9	NV–H-ZC	1	5
		case 0xB9:
			opcodeName="SBC (X),(Y)";
			writeByte((X&0xff)+((DIRECTPAGE)?0x100:0),(byte)SBC(readByte((0xff&X)+((DIRECTPAGE)?0x100:0)),readByte((0xff&Y)+((DIRECTPAGE)?0x100:0))));
			cycleCount+=5;
			break;
		//SBC A, #i 	A = A+i+C 	A8	NV–H-ZC	2	2
		case 0xA8:
			opcodeName="SBC A,#i";
			A=(SBC(A&0xff,fetch()&0xff)&0xff);
			cycleCount+=2;
			break;
		//SBC A, (X) 	A = A+(X)+C 	A6	NV–H-ZC	1	3
		case 0xA6:
			opcodeName="SBC A,(X)";
			A=(SBC(A&0xff,readByte((X&0xff)+((DIRECTPAGE)?0x100:0))&0xff)&0xff);
			cycleCount+=3;
			break;
		//SBC A, [d]+Y 	A = A+([d]+Y)+C 	B7	NV–H-ZC	2	6
		case 0xB7:
			opcodeName="SBC A,[d]+Y";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0);
			addr=(readByte(addr)&0xff)+((readByte(addr+1)&0xff)<<8)+(Y&0xff);
			A=(SBC(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=6;
			break;
		//SBC A, [d+X] 	A = A+([d+X])+C 	A7	NV–H-ZC	2	6
		case 0xA7:
			opcodeName="SBC A,[d+X]";
			addr=(fetch()&0xff)+((DIRECTPAGE)?0x100:0);
			addr+=X&0xff;
			addr=(readByte(addr)&0xff)+((readByte(addr+1)&0xff)<<8);
			A=(SBC(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=6;
			break;
		//SBC A, d 	A = A+(d)+C 	A4	NV–H-ZC	2	3
		case 0xA4:
			opcodeName="SBC A,d";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			A=(SBC(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=3;
			break;
		//SBC A, d+X 	A = A+(d+X)+C 	B4	NV–H-ZC	2	4
		case 0xB4:
			opcodeName="SBC A,d+X";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff)+(X&0xff);
			A=(SBC(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=4;
			break;
		//SBC A, !a 	A = A+(a)+C 	A5	NV–H-ZC	3	4
		case 0xA5:
			opcodeName="SBC A,a";
			addr=(fetch()&0xff)|((fetch()&0xff)<<8);
			A=(SBC(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=4;
			break;
		//SBC A, !a+X 	A = A+(a+X)+C 	B5	NV–H-ZC	3	5
		case 0xB5:
			opcodeName="SBC A,a+X";
			addr=((fetch()&0xff)|((fetch()&0xff)<<8))+(X&0xff);
			A=(SBC(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=5;
			break;
		//SBC A, !a+Y 	A = A+(a+Y)+C 	B6	NV–H-ZC	3	5
		case 0xB6:
			opcodeName="SBC A,a+Y";
			addr=((fetch()&0xff)|((fetch()&0xff)<<8))+(Y&0xff);
			A=(SBC(A&0xff,readByte(addr)&0xff)&0xff);
			cycleCount+=5;
			break;
		//SBC dd, ds 	(dd) = (dd)+(d)+C 	A9	NV–H-ZC	3	6
		case 0xA9:
			opcodeName="SBC dd,ds";
			addr2=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(SBC(readByte(addr)&0xff,readByte(addr2)&0xff)&0xff));
			cycleCount+=6;
			break;
		//SBC d, #i 	(d) = (d)+i+C 	B8	NV–H-ZC	3	5
		case 0xB8:
			opcodeName="SBC d,#i";
			value=fetch()&0xff;
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(SBC(readByte(addr)&0xff,value)&0xff));
			cycleCount+=5;
			break;
			
		//SET1 d.0 	d.0 = 1 	02	——–	2	4
		case 0x02:
			opcodeName="SET1 d.0";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(readByte(addr)|(0x1)));
			cycleCount+=4;
			break;

		//SET1 d.1 	d.1 = 1 	22	——–	2	4
		case 0x22:
			opcodeName="SET1 d.1";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(readByte(addr)|(0x2)));
			cycleCount+=4;
			break;
		//SET1 d.2 	d.2 = 1 	42	——–	2	4
		case 0x42:
			opcodeName="SET1 d.2";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(readByte(addr)|(0x4)));
			cycleCount+=4;
			break;
		//SET1 d.3 	d.3 = 1 	62	——–	2	4
		case 0x62:
			opcodeName="SET1 d.3";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(readByte(addr)|(0x8)));
			cycleCount+=4;
			break;
		//SET1 d.4 	d.4 = 1 	82	——–	2	4
		case 0x82:
			opcodeName="SET1 d.4";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(readByte(addr)|(0x10)));
			cycleCount+=4;
			break;
		//SET1 d.5 	d.5 = 1 	A2	——–	2	4
		case 0xa2:
			opcodeName="SET1 d.5";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(readByte(addr)|(0x20)));
			cycleCount+=4;
			break;
		//SET1 d.6 	d.6 = 1 	C2	——–	2	4
		case 0xc2:
			opcodeName="SET1 d.6";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(readByte(addr)|(0x40)));
			cycleCount+=4;
			break;
		//SET1 d.7 	d.7 = 1 	E2	——–	2	4
		case 0xe2:
			opcodeName="SET1 d.7";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			writeByte(addr,(byte)(readByte(addr)|(0x80)));
			cycleCount+=4;
			break;
		//SETC 	C = 1 	80	——-1	1	2
		case 0x80:
			opcodeName="SETC";
			CARRY=true;
			cycleCount+=2;
			break;
		//SETP 	P = 1 	40	–1—–	1	2
		case 0x40:
			opcodeName="SETP";
			DIRECTPAGE=true;
			cycleCount+=2;
			break;

		//SLEEP 	Halts the processor 	EF	——–	1	?
		//STOP 	Halts the processor 	FF	——–	1	?
		case 0xef: case 0xff:
			opcodeName="STOP";
			cycleCount+=1;
			waitForInterrupt=true;
			break;
			
		//SUBW YA, d 	YA = YA - (d), H on high byte 	9A	NV–H-ZC	2	5
		case 0x9a:
			opcodeName="SUBW YA,d";
			addr=((DIRECTPAGE)?0x100:0)+(fetch()&0xff);
			value=readByte(addr)&0xff;
			value|=(readByte(addr+1)&0xff)<<8;
			YA=(Y<<8)|A;
			CARRY=YA-value>=0;
			HALFCARRY=((A&0xff)-(value&0xff)>=0);
			OVERFLOW= (((YA^value)&(YA^((YA-value)&0xffff)) & 0x8000)!=0);
			YA=((YA-value)&0xffff);
			ZERO= ((YA&0xffff)==0);
			NEGATIVE= ((YA&0x8000)!=0);
			A=YA&0xff;
			Y=YA>>8;
			cycleCount+=5;
			break;
		//TCALL 0 	CALL [$FFDE] 	01	——–	1	8
		case 0x01:
			opcodeName="TCALL 0";
			PUSHW(PC);
			PC=readByte(0xffde)&0xff;
			PC|=(readByte(0xffdf)&0xff)<<8;
			cycleCount+=8;
			break;
		//TCALL 1 	CALL [$FFDC] 	11	——–	1	8
		case 0x11:
			opcodeName="TCALL 1";
			PUSHW(PC);
			PC=readByte(0xffdc)&0xff;
			PC|=(readByte(0xffdd)&0xff)<<8;
			cycleCount+=8;
			break;
		//TCALL 2 	CALL [$FFDA] 	21	——–	1	8
		case 0x21:
			opcodeName="TCALL 2";
			PUSHW(PC);
			PC=readByte(0xffda)&0xff;
			PC|=(readByte(0xffdb)&0xff)<<8;
			cycleCount+=8;
			break;
		//TCALL 3 	CALL [$FFD8] 	31	——–	1	8
		case 0x31:
			opcodeName="TCALL 3";
			PUSHW(PC);
			PC=readByte(0xffd8)&0xff;
			PC|=(readByte(0xffd9)&0xff)<<8;
			cycleCount+=8;
			break;
		//TCALL 4 	CALL [$FFD6] 	41	——–	1	8
		case 0x41:
			opcodeName="TCALL 4";
			PUSHW(PC);
			PC=readByte(0xffd6)&0xff;
			PC|=(readByte(0xffd7)&0xff)<<8;
			cycleCount+=8;
			break;
		//TCALL 5 	CALL [$FFD4] 	51	——–	1	8
		case 0x51:
			opcodeName="TCALL 5";
			PUSHW(PC);
			PC=readByte(0xffd4)&0xff;
			PC|=(readByte(0xffd5)&0xff)<<8;
			cycleCount+=8;
			break;
		//TCALL 6 	CALL [$FFD2] 	61	——–	1	8
		case 0x61:
			opcodeName="TCALL 6";
			PUSHW(PC);
			PC=readByte(0xffd2)&0xff;
			PC|=(readByte(0xffd3)&0xff)<<8;
			cycleCount+=8;
			break;
		//TCALL 7 	CALL [$FFD0] 	71	——–	1	8
		case 0x71:
			opcodeName="TCALL 7";
			PUSHW(PC);
			PC=readByte(0xffd0)&0xff;
			PC|=(readByte(0xffd1)&0xff)<<8;
			cycleCount+=8;
			break;
		//TCALL 8 	CALL [$FFCE] 	81	——–	1	8
		case 0x81:
			opcodeName="TCALL 8";
			PUSHW(PC);
			PC=readByte(0xffce)&0xff;
			PC|=(readByte(0xffcf)&0xff)<<8;
			cycleCount+=8;
			break;
		//TCALL 9 	CALL [$FFCC] 	91	——–	1	8
		case 0x91:
			opcodeName="TCALL 9";
			PUSHW(PC);
			PC=readByte(0xffcc)&0xff;
			PC|=(readByte(0xffcd)&0xff)<<8;
			cycleCount+=8;
			break;
		//TCALL 10 	CALL [$FFCA] 	A1	——–	1	8
		case 0xa1:
			opcodeName="TCALL 10";
			PUSHW(PC);
			PC=readByte(0xffca)&0xff;
			PC|=(readByte(0xffcb)&0xff)<<8;
			cycleCount+=8;
			break;
		//TCALL 11 	CALL [$FFC8] 	B1	——–	1	8
		case 0xb1:
			opcodeName="TCALL 11";
			PUSHW(PC);
			PC=readByte(0xffc8)&0xff;
			PC|=(readByte(0xffc9)&0xff)<<8;
			cycleCount+=8;
			break;
		//TCALL 12 	CALL [$FFC6] 	C1	——–	1	8
		case 0xc1:
			opcodeName="TCALL 12";
			PUSHW(PC);
			PC=readByte(0xffc6)&0xff;
			PC|=(readByte(0xffc7)&0xff)<<8;
			cycleCount+=8;
			break;
		//TCALL 13 	CALL [$FFC4] 	D1	——–	1	8
		case 0xd1:
			opcodeName="TCALL 13";
			PUSHW(PC);
			PC=readByte(0xffc4)&0xff;
			PC|=(readByte(0xffc5)&0xff)<<8;
			cycleCount+=8;
			break;
		//TCALL 14 	CALL [$FFC2] 	E1	——–	1	8
		case 0xe1:
			opcodeName="TCALL 14";
			PUSHW(PC);
			PC=readByte(0xffc2)&0xff;
			PC|=(readByte(0xffc3)&0xff)<<8;
			cycleCount+=8;
			break;
		//TCALL 15 	CALL [$FFC0] 	F1	——–	1	8
		case 0xf1:
			opcodeName="TCALL 15";
			PUSHW(PC);
			PC=readByte(0xffc0)&0xff;
			PC|=(readByte(0xffc1)&0xff)<<8;
			cycleCount+=8;
			break;
		//TCLR1 !a 	(a) = (a)&~A, ZN as for A-(a) 	4E	N—–Z-	3	6
		case 0x4e:
			opcodeName="TCLR1 a";
			addr=fetch()&0xff;
			addr|=(fetch()&0xff)<<8;
			value=readByte(addr)&0xff;
			ZERO= ((((A&0xff)-value)&0xff)==0);
			NEGATIVE= ((((A&0xff)-value)&0x80)!=0);
			writeByte(addr,(byte)(value&(~(A&0xff))));
			cycleCount+=6;
			break;
		//TSET1 !a 	(a) = (a)|A, ZN as for A-(a)	0E	N—–Z-	3	6
		case 0x0e:
			opcodeName="TSET1 a";
			addr=fetch()&0xff;
			addr|=(fetch()&0xff)<<8;
			value=readByte(addr)&0xff;
			ZERO= ((((A&0xff)-value)&0xff)==0);
			NEGATIVE= ((((A&0xff)-value)&0x80)!=0);
			writeByte(addr,(byte)(value|(A&0xff)));
			cycleCount+=6;
			break;
		//XCN A 	A = (A>>4) | (A<<4)	9F	N—–Z-	1	5
		case 0x9f:
			opcodeName="XCN A";
			A=((A>>4)&0xf)|((A&0xf)<<4);
			ZERO= ((A&0xff)==0);
			NEGATIVE= ((A&0x80)!=0);
			cycleCount+=5;
			break;
		default:
			fault("Unrecognized opcode: "+opcode);
			break;
		}
		
		
		instructionCount++;
//		printState();
	}	
	
	private int ADC(int valueA, int valueB)
	{
		int result;
		result=valueA+valueB+((CARRY)?1:0);
		CARRY=(result>=0x100);
		OVERFLOW= ((~(valueA^valueB)&(valueB^(result&0xff)) & 0x80)!=0);
		//TODO: half carry
		ZERO= ((result&0xff)==0);
		NEGATIVE= ((result&0x80)!=0);
		return result&0xff;
	}
	private int AND(int valueA, int valueB)
	{
		int result=valueA&valueB;
		ZERO= ((result&0xff)==0);
		NEGATIVE= ((result&0x80)!=0);
		return result&0xff;
	}
	private int OR(int valueA, int valueB)
	{
		int result=valueA|valueB;
		ZERO= ((result&0xff)==0);
		NEGATIVE= ((result&0x80)!=0);
		return result&0xff;
	}
	private int ASL(int value)
	{
		int result=value&0xff;
		CARRY=((result&0x80)!=0);
		result=result<<1;
		ZERO= ((result&0xff)==0);
		NEGATIVE= ((result&0x80)!=0);
		return (result&0xff);
	}
	private void CMP(int valueA, int valueB)
	{
		int result=(valueA&0xff)-(valueB&0xff);
		CARRY=(result>=0);
		ZERO= ((result&0xff)==0);
		NEGATIVE= ((result&0x80)!=0);
	}
	private void CMP16(int valueA, int valueB)
	{
		int result=(valueA&0xffff)-(valueB&0xffff);
		CARRY=(result>=0);
		ZERO= ((result&0xffff)==0);
		NEGATIVE= ((result&0x8000)!=0);
	}
	private int EOR(int valueA, int valueB)
	{
		int result=valueA^valueB;
		result=(valueA&(~0xff))|(result&0xff);
		ZERO= ((result&0xff)==0);
		NEGATIVE= ((result&0x80)!=0);
		return result&0xff;
	}
	private int LSR(int value)
	{
		int result=value&0xff;
		CARRY=((result&1)!=0);
		result=result>>1;
		ZERO= ((result&0xff)==0);
		NEGATIVE= ((result&0x80)!=0);
		return (result&0xff);
	}

	private int ROR(int value)
	{
		int result=value&0xff;
		if ((CARRY))
			result+=0x100;
		CARRY=((result&1)!=0);
		result=result>>1;
		ZERO= ((result&0xff)==0);
		NEGATIVE= ((result&0x80)!=0);
		return (result&0xff);
	}

	private int ROL(int value)
	{
		int result=value&0xff;
		result=result<<1;
		if((CARRY))
			result+=1;
		CARRY=((result&0x100)!=0);
		ZERO= ((result&0xff)==0);
		NEGATIVE= ((result&0x80)!=0);
		return (result&0xff);
	}

	private int SBC(int valueA, int valueB)
	{
		valueB=valueB&0xff;
		int result=(valueA&0xff)-valueB+((CARRY)?1:0)-1;
		CARRY=(result>=0);
		OVERFLOW= ((((valueA&0xff)^valueB)&(valueA^(result&0xff)) & 0x80)!=0);
		ZERO= ((result&0xff)==0);
		NEGATIVE= ((result&0x80)!=0);
		return result&0xff;
	}
	public void BRK()
	{
		PUSHW((PC+1)&0xffff);
		updateP();
		PUSHB(P&0xff);
		INTERRUPTSENABLED=false;
		BREAK=true;
		PC=(readByte(0xffde)&0xff)|((readByte(0xffdf)<<8)&0xff00);

//		waitForInterrupt=true;
	}
	
	private void PUSHW(int word)
	{
		int low=word&0xff;
		int high=(word>>8)&0xff;
		S=((S-1)&0xff);
		writeByte(0x100+S, (byte)low);
		writeByte(0x100+((S+1)&0xff), (byte)high);
		S=((S-1)&0xff);
	}
	private void PUSHB(int b)
	{
		b=b&0xff;
		writeByte(S+0x100, (byte)b);		
		S=((S-1)&0xff);
	}
	
   private int PULLW()
	{
		S=((S+1)&0xff);
		int low=readByte(S+0x100)&0xff;
		int high=readByte(((S+1)&0xff)+0x100)&0xff;
		S=((S+1)&0xff);
		return (high<<8)|low;
	}
	private int PULLB()
	{
		S=((S+1)&0xff);
		int low=readByte(S+0x100)&0xff;
		return low;	
	}

	public void updateP()
	{
		P=0;
		if(CARRY) P|=CARRYFLAGBIT;
		if(ZERO) P|=ZEROFLAGBIT;
		if(INTERRUPTSENABLED) P|=INTERRUPTSENABLEDFLAGBIT;
		if(HALFCARRY) P|=HALFCARRYFLAGBIT;
		if(DIRECTPAGE) P|=DIRECTPAGEFLAGBIT;
		if(OVERFLOW) P|=OVERFLOWFLAGBIT;
		if(NEGATIVE) P|=NEGATIVEFLAGBIT;
		if(BREAK) P|=BREAKFLAGBIT;
	}
	private void updateFlagsFromP()
	{
		CARRY=(P&CARRYFLAGBIT)!=0;
		ZERO=(P&ZEROFLAGBIT)!=0;
		INTERRUPTSENABLED=(P&INTERRUPTSENABLEDFLAGBIT)!=0;
		HALFCARRY=(P&HALFCARRYFLAGBIT)!=0;
		DIRECTPAGE=(P&DIRECTPAGEFLAGBIT)!=0;
		BREAK=(P&BREAKFLAGBIT)!=0;
		OVERFLOW=(P&OVERFLOWFLAGBIT)!=0;
		NEGATIVE=(P&NEGATIVEFLAGBIT)!=0;
	}

	private void fault(String message)
	{
		System.out.println(message);
		System.exit(0);
	}
	
	public void printState()
	{
		updateP();
		snes.snesgui.spc700trace.printf("SPC700: ICount %d Cycle %d Inst: %s Opcode %x PC: %x A: %x X: %x Y: %x S: %x P: %x\n", instructionCount,cycleCount,opcodeName,opcode,PC,A,X,Y,S,P);
	}
	
	public static final int[] ROM=new int[]{
        0xCD , 0xEF , 0xBD , 0xE8 , 0x00 , 0xC6 , 0x1D , 0xD0 , 0xFC , 0x8F , 0xAA , 0xF4 , 0x8F , 0xBB , 0xF5 , 0x78
        , 0xCC , 0xF4 , 0xD0 , 0xFB , 0x2F , 0x19 , 0xEB , 0xF4 , 0xD0 , 0xFC , 0x7E , 0xF4 , 0xD0 , 0x0B , 0xE4 , 0xF5
        , 0xCB , 0xF4 , 0xD7 , 0x00 , 0xFC , 0xD0 , 0xF3 , 0xAB , 0x01 , 0x10 , 0xEF , 0x7E , 0xF4 , 0x10 , 0xEB , 0xBA
        , 0xF6 , 0xDA , 0x00 , 0xBA , 0xF4 , 0xC4 , 0xF4 , 0xDD , 0x5D , 0xD0 , 0xDB , 0x1F , 0x00 , 0x00 , 0xC0 , 0xFF		
	};
	
	public String dumpSPC700State()
	{		
		updateP();
		String ret="SPC700\n";
		ret+=String.format("%x %x %x %x %x %x ",A,X,Y,S,P,PC);
		ret+=instructionCount+" "+cycleCount+" ";
		ret+=String.format("%x ",waitForInterrupt?1:0);
		ret+=dspAddress+" ";
		ret+=(timerenabled[0]?"1":"0")+" "+timermax[0]+" "+ timercount[0]+" "+ timerticks[0]+" "+ timercycles[0]+" "+ outport[0]+" ";
		ret+=(timerenabled[1]?"1":"0")+" "+timermax[1]+" "+ timercount[1]+" "+ timerticks[1]+" "+ timercycles[1]+" "+ outport[1]+" ";
		ret+=(timerenabled[2]?"1":"0")+" "+timermax[2]+" "+ timercount[2]+" "+ timerticks[2]+" "+ timercycles[2]+" "+ outport[2]+" ";
		ret+=outport[3]+" ";
		
		StringBuilder s=new StringBuilder();
		s.append(ret);
		for(int i=0; i<0x10000; i++)
			s.append(String.format("%x ",memory[i]));
		s.append("spc700done ");
		s.append("\n");
		return s.toString();
	}
	public void loadSPC700State(String state)
	{
		Scanner s=new Scanner(state);
		A=s.nextInt(16); X=s.nextInt(16); Y=s.nextInt(16); S=s.nextInt(16); P=s.nextInt(16); PC=s.nextInt(16);
		updateFlagsFromP();
		instructionCount=s.nextLong(); cycleCount=s.nextLong();
		waitForInterrupt=s.nextInt()==1;
		dspAddress=s.nextInt();
		timerenabled[0]=s.nextInt()==1; timermax[0]=s.nextInt(); timercount[0]=s.nextInt(); timerticks[0]=s.nextInt(); timercycles[0]=s.nextLong(); outport[0]=(byte)s.nextInt();
		timerenabled[1]=s.nextInt()==1; timermax[1]=s.nextInt(); timercount[1]=s.nextInt(); timerticks[1]=s.nextInt(); timercycles[1]=s.nextLong(); outport[1]=(byte)s.nextInt();
		timerenabled[2]=s.nextInt()==1; timermax[2]=s.nextInt(); timercount[2]=s.nextInt(); timerticks[2]=s.nextInt(); timercycles[2]=s.nextLong(); outport[2]=(byte)s.nextInt();
		outport[3]=(byte)s.nextInt();
		for(int i=0; i<0x10000; i++)
			memory[i]=(byte)s.nextInt(16);
		if(s.next().equals("spc700done"))
			System.out.println("loaded spc700");
		else
			System.out.println("error loading spc700");		
	}
}
