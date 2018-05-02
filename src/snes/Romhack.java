package snes;

import java.io.FileNotFoundException;
import java.io.PrintWriter;

public class Romhack 
{
	SNES snes;
	ROMEntry[] annotatedROM;
	int lastaddress=-1;
	int entrypoint=-1;
	int currentinstructionaddress=-1;
	
	public Romhack(SNES snes, byte[] rawdata, int memstart, int offset)
	{
		this.snes=snes;
		annotatedROM=new ROMEntry[rawdata.length-offset];
		for (int i=0; i<rawdata.length-offset; i++)
			annotatedROM[i]=new ROMEntry(i,memstart+i,rawdata[offset+i]);
	}
	
	public void onfetch(int address, int value)
	{
		lastaddress=address;
		if(address>=0xc00000 && address<0xc00000+annotatedROM.length)
			annotatedROM[address-0xc00000].markAsInstruction();
	}
	public void firstInstructionByte()
	{
		if(lastaddress>=0xc00000 && lastaddress<0xc00000+annotatedROM.length)
		{
			annotatedROM[lastaddress-0xc00000].markAsFirstInstructionByte();
			currentinstructionaddress=lastaddress;
			if(entrypoint==-1) entrypoint=lastaddress;
		}
	}
	public void instructionInfo(String opcode, boolean mode16, boolean X16, int addr)
	{
		if(currentinstructionaddress>=0xc00000 && currentinstructionaddress<0xc00000+annotatedROM.length)
			annotatedROM[currentinstructionaddress-0xc00000].setinfo(opcode,mode16,X16,addr);
	}
	
	public class ROMEntry
	{
		static final int TYPE_UNKNOWN=0,TYPE_INSTRUCTION=1;
		
		int imageoffset, address, value;
		int type=TYPE_UNKNOWN;
		boolean first_instruction_byte=false;
		String opcode;
		boolean mode16,X16;
		int addr;
		
		public ROMEntry(int imageoffset, int address, byte value)
		{
			this.imageoffset=imageoffset; this.address=address; this.value=value&0xff;
		}
		public void markAsInstruction()
		{
			type=TYPE_INSTRUCTION;
		}
		public void markAsFirstInstructionByte()
		{
			first_instruction_byte=true;
		}
		public void setinfo(String opcode, boolean mode16, boolean X16, int addr)
		{
			this.opcode=opcode; this.mode16=mode16; this.X16=X16; this.addr=addr;
		}
	}
	
	public void dumpKnownROM()
	{
		PrintWriter dump;
		try {
			dump=new PrintWriter("romhack.txt");
			dump.println("Entry point: "+Integer.toHexString(entrypoint));
			dump.println();
			
		for (int i=0; i<annotatedROM.length; i++)
		{
			if(annotatedROM[i].type==ROMEntry.TYPE_INSTRUCTION && annotatedROM[i].first_instruction_byte)
			{
				String dumpstring="";
				dumpstring+=Integer.toHexString(annotatedROM[i].address)+": ";
				dumpstring+=Integer.toHexString(annotatedROM[i].value)+" ";
				for (int j=i+1; j<annotatedROM.length && annotatedROM[j].type==ROMEntry.TYPE_INSTRUCTION && !annotatedROM[j].first_instruction_byte; j++)
					dumpstring+=Integer.toHexString(annotatedROM[j].value)+" ";
				dumpstring+=" : ";
				dumpstring+=annotatedROM[i].opcode+" ";
				if(annotatedROM[i].addr>=0) dumpstring+=Integer.toHexString(annotatedROM[i].addr)+" ";
				if(annotatedROM[i].mode16) dumpstring+="M1 "; else dumpstring+="M0 ";
				if(annotatedROM[i].X16) dumpstring+="X1 "; else dumpstring+="X0 ";
				dump.println(dumpstring);
			}
		}
		dump.close();
		System.out.println("Romhack dumped to romhack.txt");
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
	}
}
