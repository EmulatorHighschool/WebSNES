package snes;

import java.util.Scanner;

public class DMA 
{
	int hdmaCountPointer,lineCount,sourceBank,DMACount,indirectBank,transferMode,sourceAddress,destAddress,repeatCount,indirectSource;
	boolean hdma,repeat,reverseTransfer,hdmaIndirectAddressing,sourceAddressDecrement,sourceAddressFixed;
	boolean hdmaActive;	
	int address,channel;
	
	SNES snes;
	
	public DMA(SNES snes, int channel)
	{
		this.snes=snes; this.channel=channel;
		hdma=false; hdmaActive=false;
	}
	public void doDMA()
	{
		snes.ppu.inDMA=true;
		snes.ppu.currentDMAChannel=channel;
		int count=DMACount;
		if (count==0) count=0x10000;
		int source=(sourceAddress&0xffff)|(sourceBank<<16);
		int increment=sourceAddressFixed? 0: sourceAddressDecrement? -1: 1;
		int order=0;
		int dest=destAddress;

//		long memoryCycles=snes.eventCycles;

		while(count>0)
		{
			switch(transferMode)
			{
			case 0: case 2: dest=destAddress; break;		//dest unchanged
			case 1: dest=destAddress+order%2; break;	//2 dest regs
			case 3: dest=destAddress+order/2; break;	//2 write to 1 dest reg, then 2 to the other
			case 4: dest=destAddress+order; break;	//write to 4 regs
			}
			if(!reverseTransfer)
				snes.memory.writeByte(dest, snes.memory.readByte(source));
			else
				snes.memory.writeByte(source, snes.memory.readByte(dest));
				
			source=(((source&0xffff)+increment)&0xffff)|(source&~0xffff);
			DMACount--;
			count--;
			order=(order+1)&3;
		}
		if(snes.cycleAccurate)
		{
//			long subtract=snes.eventCycles-memoryCycles;
//			snes.eventCycles-=subtract/2;
//			snes.processor.cycleCount-=subtract/2;
			while(snes.eventCycles>=snes.nextEvent)
				snes.handleEvent();
		}
		sourceAddress=(source&0xffff);
		snes.ppu.inDMA=false;
		snes.ppu.currentDMAChannel=-1;
		
	}
	public void startHDMA()
	{
		if(hdma)
		{
			address=sourceAddress&0xffff;
			hdmaCountPointer=0;
			lineCount=0;
			repeat=false;
			repeatCount=0;
			hdmaActive=true;
//System.out.printf("Activating hdma %d for address %x\n",channel,address);
		}
	}
	public void doHDMA()
	{
		if(!hdmaActive) return;
		if(!repeat && lineCount>0) { lineCount--; return; }
		if(!repeat || repeatCount<=0)
		{
			lineCount=snes.memory.readByte(((address+hdmaCountPointer++)&0xffff)|(sourceBank<<16))&0xff;
			if (lineCount==0)
			{
				hdmaActive=false; return;
			}
			repeat=lineCount>0x80;
			if (repeat)
				repeatCount=lineCount&0x7f;
			if(hdmaIndirectAddressing)
			{
				indirectSource=snes.memory.readByte(((address+hdmaCountPointer++)&0xffff)|(sourceBank<<16))&0xff;
				indirectSource|=(snes.memory.readByte(((address+hdmaCountPointer++)&0xffff)|(sourceBank<<16))&0xff)<<8;
			}
		}
		snes.ppu.inDMA=true;
		snes.ppu.currentDMAChannel=channel;
		
//		long memoryCycles=snes.eventCycles;
		byte[] value=new byte[4];
		if (!hdmaIndirectAddressing)
		{
			switch(transferMode)
			{
			case 0:
				value[0]=snes.memory.readByte(((address+hdmaCountPointer++)&0xffff)|(sourceBank<<16));
				break;
			case 1: case 2:
				value[0]=snes.memory.readByte(((address+hdmaCountPointer++)&0xffff)|(sourceBank<<16));
				value[1]=snes.memory.readByte(((address+hdmaCountPointer++)&0xffff)|(sourceBank<<16));
				break;
			case 3: case 4:
				value[0]=snes.memory.readByte(((address+hdmaCountPointer++)&0xffff)|(sourceBank<<16));
				value[1]=snes.memory.readByte(((address+hdmaCountPointer++)&0xffff)|(sourceBank<<16));
				value[2]=snes.memory.readByte(((address+hdmaCountPointer++)&0xffff)|(sourceBank<<16));
				value[3]=snes.memory.readByte(((address+hdmaCountPointer++)&0xffff)|(sourceBank<<16));
				break;
			}
		}
		else
		{
			switch(transferMode)
			{
			case 0:
				value[0]=snes.memory.readByte(((indirectSource++)&0xffff)|(indirectBank<<16));
				break;
			case 1: case 2:
				value[0]=snes.memory.readByte(((indirectSource++)&0xffff)|(indirectBank<<16));
				value[1]=snes.memory.readByte(((indirectSource++)&0xffff)|(indirectBank<<16));
				break;
			case 3: case 4:
				value[0]=snes.memory.readByte(((indirectSource++)&0xffff)|(indirectBank<<16));
				value[1]=snes.memory.readByte(((indirectSource++)&0xffff)|(indirectBank<<16));
				value[2]=snes.memory.readByte(((indirectSource++)&0xffff)|(indirectBank<<16));
				value[3]=snes.memory.readByte(((indirectSource++)&0xffff)|(indirectBank<<16));
				break;
			}
		}
		switch(transferMode)
		{
		case 0:
			snes.memory.writeByte(destAddress, value[0]);
			break;
		case 1:
			snes.memory.writeByte(destAddress, value[0]);
			snes.memory.writeByte(destAddress+1, value[1]);
			break;
		case 2:
			snes.memory.writeByte(destAddress, value[0]);
			snes.memory.writeByte(destAddress, value[1]);
			break;
		case 3:
			snes.memory.writeByte(destAddress, value[0]);
			snes.memory.writeByte(destAddress, value[1]);
			snes.memory.writeByte(destAddress+1, value[2]);
			snes.memory.writeByte(destAddress+1, value[3]);
			break;
		case 4:
			snes.memory.writeByte(destAddress, value[0]);
			snes.memory.writeByte(destAddress+1, value[1]);
			snes.memory.writeByte(destAddress+2, value[2]);
			snes.memory.writeByte(destAddress+3, value[3]);
			break;
		}
		if (snes.cycleAccurate)
		{
//			long subtract=(snes.eventCycles-memoryCycles)/2;
//			snes.eventCycles-=subtract;
//			snes.processor.cycleCount-=subtract;
			while(snes.eventCycles>=snes.nextEvent)
				snes.handleEvent();
		}
		if(repeat)
			repeatCount--;
		snes.ppu.inDMA=false;
		snes.ppu.currentDMAChannel=-1;
	}
	public String dumpDMAState()
	{		
		String ret="dma "+channel+"\n";
		ret+=String.format("%x %x %x %x %x %x %x %x %x %x %x %x ",address,channel,hdmaCountPointer,lineCount,sourceBank,DMACount,indirectBank,transferMode,sourceAddress,destAddress,repeatCount,indirectSource);
		ret+=String.format("%x %x %x %x %x %x %x ",hdma?1:0,repeat?1:0,reverseTransfer?1:0,hdmaIndirectAddressing?1:0,sourceAddressDecrement?1:0,sourceAddressFixed?1:0,hdmaActive?1:0);
		ret+="\n";
		return ret;
	}
	public void loadDMAState(String state)
	{
		Scanner s=new Scanner(state);
		address=s.nextInt(16); channel=s.nextInt(16); hdmaCountPointer=s.nextInt(16); lineCount=s.nextInt(16); sourceBank=s.nextInt(16); DMACount=s.nextInt(16); indirectBank=s.nextInt(16); transferMode=s.nextInt(16); sourceAddress=s.nextInt(16); destAddress=s.nextInt(16); repeatCount=s.nextInt(16); indirectSource=s.nextInt(16);
		hdma=s.nextInt()==1; repeat=s.nextInt()==1; reverseTransfer=s.nextInt()==1; hdmaIndirectAddressing=s.nextInt()==1; sourceAddressDecrement=s.nextInt()==1; sourceAddressFixed=s.nextInt()==1; hdmaActive=s.nextInt()==1;
		if (s.hasNextInt(16)) System.out.println("Error in dma");
		while(s.hasNext())System.out.println(s.next());
	}

}
