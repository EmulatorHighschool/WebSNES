package snes;

import java.util.Scanner;

public class PPU 
{
	
	static final int HMAX=SNES.CYCLES_UNTIL_HCOUNTER, VMAX=262, SNES_HEIGHT=224, SNES_WIDTH=256, FIRST_VISIBLE_LINE=1;
	final int WRAM_BASE=0x7e0000;
	
	SNES snes;

	public byte[] VRAM=new byte[0x10000];
	public byte[] spriteRAM=new byte[544];
	public byte[] paletteRAM=new byte[512];

	
	private byte apulastWrite=0;
	private boolean apuFirstRead=true;

	int currentDMAChannel=-1;
	boolean inDMA;
	boolean VMAHigh;
	int VCounter;
	int VMAIncrement;
	int VMAAddress;
	int WRAM;
	int BGnxOFS;
	
	int BG3Priority;
	int brightness;
	int spriteAddress;
	boolean recomputeClipWindows, displayBlanked;
	int window1Left,window1Right,window2Left,window2Right;
	int spriteSizeSelect;
	int spriteTileBaseAddress,paletteRAMAddress;
	int mode;
	boolean spriteHighTable;
	int keystate,keylatch,keyserialcount;
	boolean doMatrixMultiply;
	int matrixA,matrixB,matrixC,matrixD,mode7CenterX,mode7CenterY;
	int mode7byte,mode7Repeat,mode7HOffset,mode7VOffset;
	boolean mode7HFlip,mode7VFlip;
	boolean readHigh213c,readHigh213d;
	int HBeamPositionLatch,VBeamPositionLatch;
	boolean spritesVisible;
	int spriteTileAddressOffset;
	boolean spriteWindow1Enabled,spriteWindow2Enabled;
	boolean spriteWindow1ClippedIn,spriteWindow2ClippedIn;
	boolean VInterruptsEnabled, HInterruptsEnabled;
	int IRQVBeamPosition;
	boolean forceVisible;

	//TODO: not backed up yet
	int IRQHBeamPosition,GunVLatch,GunHLatch;
	//TODO: remove from backup
	boolean NMILine;
	
	public class BG
	{
		int bg;
		int Base=0, HOffset=0, VOffset=0, TileBase=0, Size=0, TileSize=0;
		boolean visible;
		boolean window1Enabled,window2Enabled;
		boolean window1ClippedIn,window2ClippedIn;
		
		public BG(int i)
		{
			bg=i;
		}
		public void reset()
		{
			Base=0; HOffset=0; VOffset=0; TileBase=0; Size=0; TileSize=0;
			window1Enabled=false; window2Enabled=false; window1ClippedIn=true; window2ClippedIn=true;
		}
	}
	public BG[] bg;
	
	
	public PPU(SNES snes)
	{
		this.snes=snes;
		bg=new BG[5];
		for (int i=1; i<=4; i++)
			bg[i]=new BG(i);
		for (int i=0; i<8; i++)
			snes.dma[i]=new DMA(snes,i);
		ppureset();
	}

	public void ppureset()
	{
		VCounter=0;
		inDMA=false;
		mode=0;
		paletteRAMAddress=0;
		spriteTileBaseAddress=0;
		spriteTileAddressOffset=0;
		spriteSizeSelect=0; 
		spriteHighTable=false;
		VMAHigh=false;
		VMAIncrement=1;
		VMAAddress=0;
		BGnxOFS=0;
		WRAM=0;
		for (int i=1; i<=4; i++)
			bg[i].reset();
		window1Left=1; window1Right=0; window2Left=1; window2Right=0;
		displayBlanked=false;
		brightness=0;
/*		for (int c=0; c<=0x8000; c+=0x100)
			snes.memory.physicalMemory[c]=(byte)(c>>8);
		snes.memory.physicalMemory[0x2100]=0;
		snes.memory.physicalMemory[0x4200]=0;
		snes.memory.physicalMemory[0x4000]=0;
		snes.memory.physicalMemory[0x1000]=0;
		snes.memory.physicalMemory[0x4201]=(byte)0xff;
		snes.memory.physicalMemory[0x4213]=(byte)0xff;*/
		keystate=0;
		keylatch=0;
		keyserialcount=0;
		
		doMatrixMultiply=false;
		matrixA=0; matrixB=0; matrixC=0; matrixD=0;
		mode7CenterX=0; mode7CenterY=0;
		mode7byte=0;
		mode7Repeat=0; mode7HOffset=0; mode7VOffset=0;
		mode7HFlip=false; mode7VFlip=false;
		readHigh213c=false; readHigh213d=false;
		HBeamPositionLatch=0; VBeamPositionLatch=0;
		spritesVisible=false;
		spriteWindow1Enabled=false; spriteWindow2Enabled=false; spriteWindow1ClippedIn=true; spriteWindow2ClippedIn=true;
		VInterruptsEnabled=false; HInterruptsEnabled=false;
		IRQVBeamPosition=0x1ff;
		forceVisible=false;
		IRQHBeamPosition=0;
		GunVLatch=GunHLatch=0;
	}
	public byte readHardwareRegister(int address)
	{
		int value;
		
		if(address<0x2100)
			return snes.memory.lastWrite;
		if(inDMA)
		{
			if(currentDMAChannel>=0&&!snes.dma[currentDMAChannel].reverseTransfer)
				return snes.memory.lastWrite;
			else if (address>0x21ff)
				address=0x2100+(address&0xff);
		}
		if((address&0xffc0)==0x2140)
			return apuReadPort(address&3);
		if(address<=0x2183)
		{
			switch(address)
			{
			case 0x2134: case 0x2135: case 0x2136:
                if (doMatrixMultiply)
                {
                        int r = matrixA * (matrixB >> 8);
                        snes.memory.physicalMemory[0x2134]=(byte)(r&0xff);
                        snes.memory.physicalMemory[0x2135]=(byte)((r>>8)&0xff);
                        snes.memory.physicalMemory[0x2136]=(byte)((r>>16)&0xff);
                        doMatrixMultiply=false;
                }
                return snes.memory.physicalMemory[address];
				
			case 0x2137:
				if((snes.memory.physicalMemory[0x4213]&0x80)!=0)
				{
					VBeamPositionLatch=VCounter&0xffff;
					if (snes.cycleAccurate)
						HBeamPositionLatch=(int)snes.eventCycles/6;
					else
						HBeamPositionLatch=(int)snes.instructionsSinceHCOUNTER/6;
					snes.memory.physicalMemory[0x213f]|=0x40;
				}
				if(snes.cycleAccurate && (VCounter>GunVLatch || (VCounter==GunVLatch && snes.eventCycles>=GunHLatch*6)))
					GunVLatch=1000;
				if(!snes.cycleAccurate && (VCounter>GunVLatch || (VCounter==GunVLatch && snes.instructionsSinceHCOUNTER>=GunHLatch*6)))
					GunVLatch=1000;
				return 0;
			case 0x2138:
				value=spriteRAM[(spriteAddress)&0x1ff]&0xff;
				spriteAddress=(spriteAddress+1)&0x1ff;
				return (byte)value;
			//read from VRAM low
			case 0x2139:
				value=VRAM[VMAAddress&0xffff]&0xff;
				return (byte)value;
			//read from VRAM high
			case 0x213a:
				value=VRAM[(VMAAddress+1)&0xffff]&0xff;
				if (VMAHigh)
					VMAAddress+=VMAIncrement;
				return (byte)value;
			case 0x213b:
				value=paletteRAM[(paletteRAMAddress++)&0x1ff]&0xff;
				paletteRAMAddress&=0x1ff;
				snes.memory.lastWrite=(byte)value;
				return (byte)value;
			case 0x213c:
				gunLatch(false);
				if(readHigh213c)
				{
					readHigh213c=!readHigh213c;
					return (byte)((HBeamPositionLatch>>8)&1);
				}
				else
				{
					readHigh213c=!readHigh213c;
					return (byte)(HBeamPositionLatch&0xff);
				}
			case 0x213d:
				gunLatch(false);
				//TODO: Chrono trigger - I don't know why this makes it fail?
				if(snes.ischrono)
					if(VBeamPositionLatch<0xf0) 
						return 0;
				if(readHigh213d)
				{
					readHigh213d=!readHigh213d;
					return (byte)((VBeamPositionLatch>>8)&0xff);
				}
				else
				{
					readHigh213d=!readHigh213d;
					return (byte)((VBeamPositionLatch&0xff));					
				}
				/*
				value=VBeamPositionLatch&0xff;
				VBeamPositionLatch>>=8;
				//TODO: Chrono trigger - I don't know why this makes it fail?
System.out.printf("%x\n", value);
								if(snes.ischrono)
					if(value<0xf0) 
						return 0;
				return (byte)value;
*/
			case 0x213e:
				return 0;
			case 0x213f:
				gunLatch(false);
				value=(snes.memory.physicalMemory[0x213f]&0xc0);
				snes.memory.physicalMemory[0x213f]&=~0x40;
				return(byte)value;
			case 0x2180:
				value=snes.memory.physicalMemory[WRAM_BASE+WRAM++];
				WRAM&=0x1ffff;
				return(byte)value;
			}
			return snes.memory.lastWrite;
		}
		if (address==0x4016 || address==0x4017)
			return (byte)readJoySer(address);
		switch(address)
		{
		case 0x4210:
			value=snes.memory.physicalMemory[address];
			snes.memory.physicalMemory[0x4210]=2;
snes.memory.lastWrite=0;
			return (byte)((value&0x80)|(snes.memory.lastWrite&0x7f)|2);
		case 0x4211:
			value=snes.IRQLine? 0x80:0;
			snes.IRQLine=false;
			snes.interruptPending=false;
			return (byte)(value|(snes.memory.lastWrite&0x7f));
//			return snes.memory.lastWrite;
		case 0x4212:
			value=0;
			if(VCounter>=SNES_HEIGHT+FIRST_VISIBLE_LINE && VCounter<SNES_HEIGHT+FIRST_VISIBLE_LINE+3)
				value=1;
			int HBlankEnd=4; int HBlankStart=SNES.CYCLES_UNTIL_HBLANK;
//TODO: Chrono trigger
			long c=(int)(snes.processor.instructionCount%2000);
			if(snes.cycleAccurate)
				c=snes.eventCycles;
			else
				c=snes.instructionsSinceHCOUNTER;
			if(c<HBlankEnd || c>=HBlankStart)
				value|=0x40;
			if(VCounter>=SNES_HEIGHT+FIRST_VISIBLE_LINE)
				value|=0x80;
			return (byte)value;
		case 0x4213: case 0x4214: case 0x4215: case 0x4216: case 0x4217:
			return snes.memory.physicalMemory[address];
		case 0x4218: case 0x4219: 
			return (byte)readJoy(address);
		case 0x421a: case 0x421b: case 0x421c: case 0x421d: case 0x421e: case 0x421f:
			return snes.memory.physicalMemory[address];		
		}
//		return snes.memory.lastWrite;
		return 0;
	}

	public void writeHardwareRegister(int address, byte val)
	{
		int value=val&0xff;
		snes.memory.physicalMemory[address]=val;
		switch(address)
		{
		//screen display
		case 0x2100:
			displayBlanked=((value&0xf0)!=0);
			brightness=(value&0xf);
			break;
		//sprite sprite data area
/*sssnnbbb   s: Object size  n: name selection  b: base selection
           Size bit in sprite table:           0     1
           Bits of object size:    000      8x8   16x16
                                   001      8x8   32x32
                                   010      8x8   64x64
                                   011      16x16 32x32
                                   100      16x16 64x64
                                   101      32x32 64x64
                                   110,111  Unknown behavior
*/
		case 0x2101:
			spriteTileBaseAddress=(value&3)<<14;
			spriteSizeSelect=(value>>5)&7;
			spriteTileAddressOffset=((value>>3)&3)<<13;
			break;
		//sprite address low
		case 0x2102:
			spriteAddress=((value<<1)&0x1ff)|(spriteAddress&0xfe00);
			break;
		//sprite address high
		case 0x2103:
			spriteAddress=(spriteAddress&0x1ff)|(value<<9);
			spriteHighTable=(value&1)!=0;
			break;
		//sprite data write
		case 0x2104:
			spriteHighTable=(spriteAddress&0x200)!=0;
			if(!spriteHighTable)
			{
				spriteRAM[(spriteAddress)%(512+32)]=(byte)value;
				spriteAddress=(spriteAddress+1)%(512+32);
//				spriteAddress=(spriteAddress+1)%(1024);
			}
			else
			{
				spriteRAM[512+((spriteAddress)&0x1f)]=(byte)value;
				spriteAddress=(spriteAddress+1)%(512+32);				
//				spriteAddress=(spriteAddress+1)%(1024);				
			}
			break;
		//BG and tile size
		case 0x2105:
			//0=8x8 tile, 1=16x16 tile
			bg[1].TileSize=((value&16));
			bg[2].TileSize=((value&32));
			bg[3].TileSize=((value&64));
			bg[4].TileSize=((value&128));
/*
 * MODE   # of BGs  Max Colors/Tile   palettes       Colors Total
0      4         4                 32 (8 per BG)  128 (32 per BG*4 BGs)
1      3         BG1/BG2:16 BG3:4  8              BG1/BG2:128 BG3:32
2      2         16                8              128
3      2         BG1:256 BG2:16    BG1:1 BG2:8    BG1:256 BG2:128
4      2         BG1:256 BG2:4     BG1:1 BG2:8    BG1:256 BG2:32
5      2         BG1:16 BG2:4      8              BG1:128 BG2:32 (Interlaced mode)
6      1         16                8              128 (Interlaced mode)
7      1         256               1              256
 */
			mode=value&7;
			BG3Priority=((value&0xf)==0x9?1:0);	//(mode 1 only)
//			snes.video.updateBGs();
			snes.video.updateMode();
			break;
		//mosaic size
		case 0x2106: break;
		//Tile Map locations
		//BG1 address
		case 0x2107:
			bg[1].Size=value&3;		//00=32x32, 01=64x32, 10=32x64, 11=64x64
			bg[1].Base=(value&0x7c)<<9;
			break;
		//BG2 address
		case 0x2108:
			bg[2].Size=value&3;
			bg[2].Base=(value&0x7c)<<9;
			break;
		//BG3 address
		case 0x2109:
			bg[3].Size=value&3;
			bg[3].Base=(value&0x7c)<<9;
			break;
		//BG4 address
		case 0x210a:
			bg[4].Size=value&3;
			bg[4].Base=(value&0x7c)<<9;
			break;
		//BG12 tile base
		case 0x210b:
			bg[1].TileBase=(value&7)<<13;
			bg[2].TileBase=((value>>4)&7)<<13;
			break;
		//BG34 tile base
		case 0x210c:
			bg[3].TileBase=(value&7)<<13;
			bg[4].TileBase=((value>>4)&7)<<13;
			break;
		//BG1 hscroll
		case 0x210d:
			bg[1].HOffset=((value<<8)|(BGnxOFS&~7)|((bg[1].HOffset>>8)&7))&0x7ff;
			BGnxOFS=value;
			mode7HOffset=(value<<8)|mode7byte;
			mode7byte=value&0xff;
			break;
		//BG1 vscroll
		case 0x210e:
			bg[1].VOffset=((value<<8)|BGnxOFS)&0x7ff;
			BGnxOFS=value;
			mode7VOffset=(value<<8)|mode7byte;
			mode7byte=value&0xff;
			break;
		//BG2 hscroll
		case 0x210f:
			bg[2].HOffset=((value<<8)|(BGnxOFS&~7)|((bg[2].HOffset>>8)&7))&0x7ff;
			BGnxOFS=value;
			break;
		//BG2 vscroll
		case 0x2110:
			bg[2].VOffset=((value<<8)|BGnxOFS)&0x7ff;
			BGnxOFS=value;
			break;
		//BG3 hscroll
		case 0x2111:
			bg[3].HOffset=((value<<8)|(BGnxOFS&~7)|((bg[3].HOffset>>8)&7))&0x7ff;
			BGnxOFS=value;
			break;
		//BG3 vscroll
		case 0x2112:
			bg[3].VOffset=((value<<8)|BGnxOFS)&0x7ff;
			BGnxOFS=value;
			break;
		//BG4 hscroll
		case 0x2113:
			bg[4].HOffset=((value<<8)|(BGnxOFS&~7)|((bg[4].HOffset>>8)&7))&0x7ff;
			BGnxOFS=value;
			break;
		//BG4 vscroll
		case 0x2114:
			bg[4].VOffset=((value<<8)|BGnxOFS)&0x7ff;
			BGnxOFS=value;
			break;
		//VRAM increment
		case 0x2115:
			VMAHigh=(value&0x80)!=0;	//access only high byte of VRAM locations?
			switch(value&3)
			{
			case 0: VMAIncrement=2; break;
			case 1: VMAIncrement=64; break;
			case 2: VMAIncrement=128; break; 
			case 3: VMAIncrement=256; break;
			}
			break;
		//VRAM address low
		case 0x2116:
			VMAAddress=(VMAAddress&0xfe00)|(value<<1);
			break;
		//VRAM address high
		case 0x2117:
			VMAAddress=((VMAAddress&0x1ff)|(value<<9))&0xffff;
			break;
		case 0x2118:
//			if(!displayBlanked && VCounter<SNES_HEIGHT+FIRST_VISIBLE_LINE) return;
			VRAM[VMAAddress&0xffff]=(byte)value;
			if(!VMAHigh)
				VMAAddress+=VMAIncrement;
			break;
		case 0x2119:
//			if(!displayBlanked && VCounter<SNES_HEIGHT+FIRST_VISIBLE_LINE) return;
			VRAM[(VMAAddress+1)&0xffff]=(byte)value;
			if (VMAHigh)
				VMAAddress+=VMAIncrement;
			break;
		case 0x211a: 
			mode7HFlip=(value&1)!=0;
			mode7VFlip=(value&2)!=0;
			mode7Repeat=(value>>6)&3;
			break;
		case 0x211b: 
			matrixA=mode7byte|((value&0xff)<<8);
			mode7byte=value&0xff;
			doMatrixMultiply=true;
			break;
		case 0x211c: 
			matrixB=mode7byte|((value&0xff)<<8);
			mode7byte=value&0xff;
			doMatrixMultiply=true;
			break;
		case 0x211d:
			matrixC=mode7byte|((value&0xff)<<8);
			mode7byte=value&0xff;
			break;
		case 0x211e:
			matrixD=mode7byte|((value&0xff)<<8);
			mode7byte=value&0xff;
			break;
		case 0x211f:
			mode7CenterX=mode7byte|((value&0xff)<<8);
			mode7byte=value&0xff;
			break;
		case 0x2120: 
			mode7CenterY=mode7byte|((value&0xff)<<8);
			mode7byte=value&0xff;
			break;

		//paletteRAM (palette) Address
		case 0x2121:
			paletteRAMAddress=(value<<1)&0x1ff;
			break;
		//paletteRAM (palette) Data write
		case 0x2122:
			paletteRAM[paletteRAMAddress]=(byte)(value&0xff);
			if((paletteRAMAddress&1)==1)
				paletteRAM[paletteRAMAddress]&=0x7f;
			paletteRAMAddress=(paletteRAMAddress+1)&0x1ff;
			break;
		case 0x2123:
			bg[2].window2Enabled=(value&0x80)!=0;
			bg[2].window2ClippedIn=(value&0x40)==0;
			bg[2].window1Enabled=(value&0x20)!=0;
			bg[2].window1ClippedIn=(value&0x10)==0;
			bg[1].window2Enabled=(value&0x8)!=0;
			bg[1].window2ClippedIn=(value&0x4)==0;
			bg[1].window1Enabled=(value&0x2)!=0;
			bg[1].window1ClippedIn=(value&0x1)==0;
			break;
		case 0x2124:
			bg[4].window2Enabled=(value&0x80)!=0;
			bg[4].window2ClippedIn=(value&0x40)==0;
			bg[4].window1Enabled=(value&0x20)!=0;
			bg[4].window1ClippedIn=(value&0x10)==0;
			bg[3].window2Enabled=(value&0x8)!=0;
			bg[3].window2ClippedIn=(value&0x4)==0;
			bg[3].window1Enabled=(value&0x2)!=0;
			bg[3].window1ClippedIn=(value&0x1)==0;
			break;
		case 0x2125: 
			spriteWindow2Enabled=(value&0x8)!=0;
			spriteWindow2ClippedIn=(value&0x4)==0;
			spriteWindow1Enabled=(value&0x2)!=0;
			spriteWindow1ClippedIn=(value&0x1)==0;			
			break;
		case 0x2126:
			window1Left=value;
			recomputeClipWindows=true;
			break;
		case 0x2127:
			window1Right=value;
			recomputeClipWindows=true;
			break;
		case 0x2128:
			window2Left=value;
			recomputeClipWindows=true;
			break;
		case 0x2129:
			window2Right=value;
			recomputeClipWindows=true;
			break;
		case 0x212a: break;
		case 0x212b: case 0x212d: case 0x212e: case 0x212f: case 0x2130:
			recomputeClipWindows=true;
			break;
		case 0x212c:
			spritesVisible=(value&0x10)!=0;
			bg[4].visible=(value&0x8)!=0;
			bg[3].visible=(value&0x4)!=0;
			bg[2].visible=(value&0x2)!=0;
			bg[1].visible=(value&0x1)!=0;
			if(forceVisible) { spritesVisible=bg[4].visible=bg[3].visible=bg[2].visible=bg[1].visible=true; }
			break;
		case 0x2131:
			break;
		case 0x2132:
			break;
		
		case 0x2180:
			snes.memory.writeByte(WRAM_BASE+WRAM++,(byte)value);
			WRAM&=0x1ffff;
			break;
		case 0x2181:
			WRAM&=0x1ff00;
			WRAM|=value&0xff;
			break;
		case 0x2182:
			WRAM&=0x100ff;
			WRAM|=((value&0xff)<<8);
			break;
		case 0x2183:
			WRAM&=0x0ffff;
			WRAM|=((value&0xff)<<16);
			WRAM&=0x1ffff;
			break;
		case 0x4016:
			setJoypadLatch(value&1);
			break;
		case 0x4200:
			if((value&0x20)!=0)
				VInterruptsEnabled=true;
			else
				VInterruptsEnabled=false;
			if((value&0x10)!=0)
				HInterruptsEnabled=true;
			else
				HInterruptsEnabled=false;
			if(snes.IRQLine && VInterruptsEnabled && !HInterruptsEnabled)
				snes.interruptPending=true;
			if(!VInterruptsEnabled && !HInterruptsEnabled)
			{
				snes.IRQLine=false;
				snes.interruptPending=false;
			}
			if((value&0x80)!=0 && VCounter>SNES_HEIGHT+FIRST_VISIBLE_LINE && (snes.memory.physicalMemory[0x4200]&0x80)!=0 && (snes.memory.physicalMemory[0x4200]&0x80)!=0)
			{
//				NMILine=true;
				snes.processor.NMItrigger=true;
				if(snes.cycleAccurate)
					snes.processor.NMItriggerPosition=(int)snes.eventCycles+12;
				else
					snes.processor.NMItriggerPosition=0xffff;
//				System.out.println(snes.eventCycles);
			}
			break;
		case 0x4201:
			if((value&0x80)==0 && (snes.memory.physicalMemory[0x4213]&0x80)==0x80)
			{
				VBeamPositionLatch=VCounter&0xffff;
				HBeamPositionLatch=(int)snes.eventCycles/6;
				snes.memory.physicalMemory[0x213f]|=0x40;
				if(snes.cycleAccurate && (VCounter>GunVLatch || (VCounter==GunVLatch && snes.eventCycles>=GunHLatch*6)))
					GunVLatch=1000;
			}
			else
				gunLatch((value&0x80)!=0);
			
			snes.memory.physicalMemory[0x4201]=(byte)value;
			snes.memory.physicalMemory[0x4213]=(byte)value;
			break;
		case 0x4202:
			snes.memory.physicalMemory[0x4202]=(byte)(value&0xff);
//System.out.printf("4202: %x\n", snes.memory.physicalMemory[0x4202]);
			break;
		case 0x4203:
			int res=(snes.memory.physicalMemory[0x4202]&0xff)*(value&0xff);
			snes.memory.physicalMemory[0x4216]=(byte)(res&0xff);
			snes.memory.physicalMemory[0x4217]=(byte)((res>>8)&0xff);
//System.out.printf("4203: %x res: %x 4216: %x 4217: %x\n", value&0xff,res,snes.memory.physicalMemory[0x4216],snes.memory.physicalMemory[0x4217]);
			break;
		case 0x4206:
			int a=(snes.memory.physicalMemory[0x4204]&0xff)|((snes.memory.physicalMemory[0x4205]&0xff)<<8);
			a=a&0xffff;
			int div,rem;
			if(value==0)
				div=0xffff;
			else
				div=a/value;
			if(value==0)
				rem=a;
			else
				rem=a%value;
			snes.memory.physicalMemory[0x4214]=(byte)(div&0xff);
			snes.memory.physicalMemory[0x4215]=(byte)((div>>8)&0xff);
			snes.memory.physicalMemory[0x4216]=(byte)(rem&0xff);
			snes.memory.physicalMemory[0x4217]=(byte)((rem>>8)&0xff);
			break;
		case 0x4207:
			{
				int p=IRQHBeamPosition;
				IRQHBeamPosition=(IRQHBeamPosition&0xff00)|value;
				if(IRQHBeamPosition!=p) updateHVPosition();
			}
			break;
		case 0x4208:
			{
				int p=IRQHBeamPosition;
				IRQHBeamPosition=(IRQVBeamPosition&0xff)|((value&1)<<8);
				if(IRQHBeamPosition!=p) updateHVPosition();
			}
			break;
		case 0x4209:
			{
				int p=IRQVBeamPosition;
				IRQVBeamPosition=(IRQVBeamPosition&0xff00)|(value&0xff);
				if(IRQVBeamPosition!=p) updateHVPosition();
			}
			break;
		case 0x420a:
			{
				int p=IRQVBeamPosition;
				IRQVBeamPosition=(IRQVBeamPosition&0xff)|((value&1)<<8);
				if(IRQVBeamPosition!=p) updateHVPosition();
			}
			break;
		case 0x420b:
			if(inDMA) break;
			for (int d=0; d<8; d++)
				if((value&(1<<d))!=0)
					snes.dma[d].doDMA();
			break;
		case 0x420c:
			if(inDMA) break;
			for (int d=0; d<8; d++)
				snes.dma[d].hdma=(value&(1<<d))!=0;
			break;
		}
		//APU
		if((address&0xffc0)==0x2140)
			apuWritePort(address&3,(byte)value);
		//DMA
		if ((address&0xff80)==0x4300 && !inDMA)
		{
			int d=(address>>4)&7;
			switch(address&0xf)
			{
			case 0:
				snes.dma[d].reverseTransfer=(value&0x80)!=0;		//transfer from CPU to 2100 regs or backwards
				snes.dma[d].hdmaIndirectAddressing=(value&0x40)!=0;
				snes.dma[d].sourceAddressDecrement=(value&0x10)!=0;
				snes.dma[d].sourceAddressFixed=(value&0x8)!=0;
				snes.dma[d].transferMode=value&7;
				break;
			case 1:
				snes.dma[d].destAddress=value+0x2100;
				break;
			case 2:
				snes.dma[d].sourceAddress=(snes.dma[d].sourceAddress&0xff00)|value;
				break;
			case 3:
				snes.dma[d].sourceAddress=(snes.dma[d].sourceAddress&0xff)|(value<<8);
				break;
			case 4:
				snes.dma[d].sourceBank=value;
				break;
			case 5:
				snes.dma[d].DMACount=(snes.dma[d].DMACount&0xff00)|value;
				break;
			case 6:
				snes.dma[d].DMACount=(snes.dma[d].DMACount&0xff)|(value<<8);
				break;
			case 7:
				snes.dma[d].indirectBank=value;
				break;
			case 8:
				snes.dma[d].hdmaCountPointer=(snes.dma[d].hdmaCountPointer&0xff00)|value;
				break;
			case 9:
				snes.dma[d].hdmaCountPointer=(snes.dma[d].hdmaCountPointer&0xff)|(value<<8);
				break;
			case 0xa:
				if((value&0x7f)!=0)
				{
					snes.dma[d].lineCount=value&0x7f;
					snes.dma[d].repeat=(value&0x80)!=0;
				}
				else
				{
					snes.dma[d].lineCount=128;
					snes.dma[d].repeat=(value&0x80)==0;
				}
				break;
			case 0xb: case 0xf:
				break;
			}
		}
	}
	
	private void gunLatch(boolean force)
	{
		if(VCounter>GunVLatch || (VCounter==GunVLatch && snes.eventCycles>=GunHLatch*6))
		{
			if(force || (snes.memory.physicalMemory[0x4213]&0x80)!=0)
			{
				IRQVBeamPosition=GunVLatch;
				IRQHBeamPosition=GunHLatch;
				snes.memory.physicalMemory[0x213f]|=0x40;
			}
			GunVLatch=1000;
		}		
	}
	
	private void updateHVPosition()
	{
		IRQHBeamPosition=this.HBeamPositionLatch*6+10;
		IRQVBeamPosition=this.VBeamPositionLatch;
		if(IRQHBeamPosition>=PPU.HMAX && IRQHBeamPosition<340)
		{
			IRQHBeamPosition-=PPU.HMAX;
			IRQVBeamPosition++;
		}
	}
	
	public void apuWritePort(int address, byte b)
	{
		if(!snes.apuEnabled)
		{
			if (address==0)
				apulastWrite=b;
		}
		else
		{
			snes.spc700.writeInputPort(address&3,b);
		}
	}
	public byte apuReadPort(int address)
	{
		if(!snes.apuEnabled)
		{
			if (address==1)
				return (byte)0xbb;
			if (!snes.ischrono && address==3)
				return 0;
			if (address==0 && apuFirstRead)
			{
				apuFirstRead=false;
				return (byte)0xaa;
			}
	//		if (address==0)
				return apulastWrite;
	//		return 0;
		}
		else
		{
//			return snes.memory.physicalMemory[address+0x2140];
			return snes.spc700.readOutputPort(address&3);
//			return snes.spc700.outport[address];
		}
	}
	public void endScreenRefresh() 
	{
		if (!snes.multithreaded)
			snes.video.endScreenRefresh();
		else
//			snes.video.endScreenRefreshThreaded();
			snes.video.renderThread.endRefresh();
	}
	public void startScreenRefresh() 
	{
		if (!snes.multithreaded)
			snes.video.startScreenRefresh();
		else
//			snes.video.startScreenRefreshThreaded();
			snes.video.renderThread.startRefresh();
	}
	public void renderLine(int line) 
	{
		if (!snes.multithreaded)
			snes.video.updateLines(line);
		else
			snes.video.renderThread.render(line);
//			snes.video.updateLinesThreaded(line);
	}
	
	
	public void setJoypadLatch(int l)
	{
		if(l==0 && keylatch==1)
			keyserialcount=0;
		keylatch=l;
	}
	public int readJoySer(int address)
	{
		if(address!=0x4016) return 0;
		
		if(keyserialcount<8)
			return ((keystate&0xff)>>(7-keyserialcount++))&1;
		if(keyserialcount<16)
			return (((keystate>>8)&0xff)>>(7-(keyserialcount++-8)))&1;
		keyserialcount++;
		if(keyserialcount==32) keyserialcount=0;
		return 0;
	}

	public int readJoy(int address)
	{
/*if(address==0x4218 && snes.AButtonPressed) return 0x80;
if(address==0x4218 && snes.XButtonPressed) return 0x40;
if(address==0x4219 && snes.UpButtonPressed) return 8;
if(address==0x4219 && snes.DownButtonPressed) return 2;
if(address==0x4219 && snes.RightButtonPressed) return 1;
if(address==0x4219 && snes.StartButtonPressed) return 0x10;*/
		if(address==0x4219) return keystate&0xff;
		if(address==0x4218) return (keystate>>8)&0xff;
		return 0;
	}

	private int getkeymask(char k)
	{
		int mask=0;
		switch(k)
		{
		case 'r': mask|=0x1; break;
		case 'l': mask|=0x2; break;
		case 'd': mask|=0x4; break;
		case 'u': mask|=0x8; break;
		case 'T': mask|=0x10; break;
		case 'S': mask|=0x20; break;
		case 'Y': mask|=0x40; break;
		case 'B': mask|=0x80; break;
		case 'R': mask|=0x1000; break;
		case 'L': mask|=0x2000; break;
		case 'X': mask|=0x4000; break;
		case 'A': mask|=0x8000; break;
		}
		return mask;
	}
	public void keydown(char k)
	{
		if (k=='P')
		{
			if(snes.pauselock.islocked())
			{
				snes.snesgui.buttonComponent.step.setEnabled(false);
				snes.snesgui.buttonComponent.pause.setText("Pause");
				snes.pauselock.unlock();
			}
			else
			{
				snes.pauselock.lock();
				snes.snesgui.buttonComponent.step.setEnabled(true);
				snes.snesgui.buttonComponent.pause.setText("Resume");
			}
			return;
		}
		else if (k=='E')
		{
			snes.saveSRAM();
			System.exit(0);
		}
		else if (k=='t')
		{
			snes.pauselock.lock();
			snes.pauselock.sleepUntilLocked();
			snes.multithreaded=!snes.multithreaded;
		}
		else if (k=='1'||k=='2'||k=='3'||k=='4'||k=='5')
		{
			boolean l=false;
			if(!snes.pauselock.islocked())
			{
				snes.pauselock.lock();
				snes.pauselock.sleepUntilLocked();
			}
			else l=true;
			String name=snes.gamename.substring(0,snes.gamename.length()-4)+"_state"+k+".txt";
			snes.dumpStateToFile(name);
			if(!l) snes.pauselock.unlock();
		}
		else if (k=='!'||k=='@'||k=='#'||k=='$'||k=='%')
		{
			boolean l=false;
			if(!snes.pauselock.islocked())
			{
				snes.pauselock.lock();
				snes.pauselock.sleepUntilLocked();
			}
			else l=true;
			char k2=k=='!'?'1':k=='@'?'2':k=='#'?'3':k=='$'?'4':'5';
			String name=snes.gamename.substring(0,snes.gamename.length()-4)+"_state"+k2+".txt";
			snes.loadStateFromFile(name);
			if(!l) snes.pauselock.unlock();
		}
		else if (k=='\\')
		{
			snes.video.drawBGs();
		}
		else if (k=='=')
		{
			snes.debugMode=!snes.debugMode;
			if(snes.doromhack) snes.romhack.dumpKnownROM();
		}
		else if (k=='-')
		{
			snes.mute=!snes.mute;
			if(snes.mute)
				snes.snesgui.settext("Sound off");
			else
				snes.snesgui.settext("Sound on");
		}
		
		int mask=getkeymask(k);
		keystate|=mask;
	}
	public void keyup(char k)
	{
		int mask=getkeymask(k);
		keystate&=(~mask);
	}

	public String dumpPPUState()
	{		
		String ret="ppu\n";
		ret+=String.format("%x ", mode);
		ret+=String.format("%x %x %x %x %x %x ",bg[1].TileBase,bg[1].Base,bg[1].Size,bg[1].TileSize,bg[1].HOffset,bg[1].VOffset);
		ret+=String.format("%x %x %x %x %x %x ",bg[2].TileBase,bg[2].Base,bg[2].Size,bg[2].TileSize,bg[2].HOffset,bg[2].VOffset);
		ret+=String.format("%x %x %x %x %x %x ",bg[3].TileBase,bg[3].Base,bg[3].Size,bg[3].TileSize,bg[3].HOffset,bg[3].VOffset);
		ret+=String.format("%x %x %x %x %x %x ",bg[4].TileBase,bg[4].Base,bg[4].Size,bg[4].TileSize,bg[4].HOffset,bg[4].VOffset);
		ret+=String.format("%d %x %x %x %x %x %x ", currentDMAChannel,VCounter,VMAIncrement,VMAAddress,WRAM,brightness,apulastWrite);
		ret+=String.format("%x %x %x ",keystate,keylatch,keyserialcount);
		ret+=String.format("%x %x %x %x ",window1Left,window1Right,window2Left,window2Right);
		ret+=String.format("%x %x %x %x %x %x ",spriteTileBaseAddress,paletteRAMAddress,spriteSizeSelect,BG3Priority,BGnxOFS,spriteAddress);
		ret+=String.format("%x %x %x %x %x %x %x ",NMILine?1:0,inDMA?1:0,VMAHigh?1:0,recomputeClipWindows?1:0,displayBlanked?1:0,spriteHighTable?1:0,apuFirstRead?1:0);
		StringBuilder s=new StringBuilder();
		s.append(ret);
		s.append(matrixA+" "+matrixB+" "+matrixC+" "+matrixD+" "+mode7CenterX+" "+mode7CenterY+" ");
		s.append(mode7byte+" "+mode7Repeat+" "+mode7HOffset+" "+mode7VOffset+" ");
		s.append(HBeamPositionLatch+" "+VBeamPositionLatch+" "+spriteTileAddressOffset+" "+IRQVBeamPosition+" ");
		s.append(String.format("%x %x %x %x %x %x ",bg[1].bg,bg[1].visible?1:0,bg[1].window1Enabled?1:0,bg[1].window2Enabled?1:0,bg[1].window1ClippedIn?1:0,bg[1].window2ClippedIn?1:0));
		s.append(String.format("%x %x %x %x %x %x ",bg[2].bg,bg[2].visible?1:0,bg[2].window1Enabled?1:0,bg[2].window2Enabled?1:0,bg[2].window1ClippedIn?1:0,bg[2].window2ClippedIn?1:0));
		s.append(String.format("%x %x %x %x %x %x ",bg[3].bg,bg[3].visible?1:0,bg[3].window1Enabled?1:0,bg[3].window2Enabled?1:0,bg[3].window1ClippedIn?1:0,bg[3].window2ClippedIn?1:0));
		s.append(String.format("%x %x %x %x %x %x ",bg[4].bg,bg[4].visible?1:0,bg[4].window1Enabled?1:0,bg[4].window2Enabled?1:0,bg[4].window1ClippedIn?1:0,bg[4].window2ClippedIn?1:0));
		s.append(String.format("%x %x %x %x %x %x %x %x %x %x %x %x %x ",doMatrixMultiply?1:0,mode7HFlip?1:0,mode7VFlip?1:0,spritesVisible?1:0,spriteWindow1Enabled?1:0,spriteWindow2Enabled?1:0,spriteWindow1ClippedIn?1:0,spriteWindow2ClippedIn?1:0,VInterruptsEnabled?1:0,HInterruptsEnabled?1:0,forceVisible?1:0,readHigh213c?1:0,readHigh213d?1:0));
		for(int i=0; i<0x10000; i++)
			s.append(String.format("%x ",VRAM[i]));
		for(int i=0; i<512+32; i++)
			s.append(String.format("%x ",spriteRAM[i]));
		for(int i=0; i<512; i++)
			s.append(String.format("%x ",paletteRAM[i]));
		s.append("ppudone ");
		s.append("\n");
		return s.toString();
	}
	
	public void loadPPUState(String state)
	{
		Scanner s=new Scanner(state);
		mode=s.nextInt(16);
		bg[1].TileBase=s.nextInt(16); bg[1].Base=s.nextInt(16); bg[1].Size=s.nextInt(16); bg[1].TileSize=s.nextInt(16); bg[1].HOffset=s.nextInt(16); bg[1].VOffset=s.nextInt(16);
		bg[2].TileBase=s.nextInt(16); bg[2].Base=s.nextInt(16); bg[2].Size=s.nextInt(16); bg[2].TileSize=s.nextInt(16); bg[2].HOffset=s.nextInt(16); bg[2].VOffset=s.nextInt(16);
		bg[3].TileBase=s.nextInt(16); bg[3].Base=s.nextInt(16); bg[3].Size=s.nextInt(16); bg[3].TileSize=s.nextInt(16); bg[3].HOffset=s.nextInt(16); bg[3].VOffset=s.nextInt(16);
		bg[4].TileBase=s.nextInt(16); bg[4].Base=s.nextInt(16); bg[4].Size=s.nextInt(16); bg[4].TileSize=s.nextInt(16); bg[4].HOffset=s.nextInt(16); bg[4].VOffset=s.nextInt(16);
		currentDMAChannel=s.nextInt(); VCounter=s.nextInt(16); VMAIncrement=s.nextInt(16); VMAAddress=s.nextInt(16); WRAM=s.nextInt(16); brightness=s.nextInt(16); apulastWrite=(byte)s.nextInt(16);
		keystate=s.nextInt(16); keylatch=s.nextInt(16); keyserialcount=s.nextInt(16); 
		window1Left=s.nextInt(16); window1Right=s.nextInt(16); window2Left=s.nextInt(16); window2Right=s.nextInt(16); 
		spriteTileBaseAddress=s.nextInt(16); paletteRAMAddress=s.nextInt(16); spriteSizeSelect=s.nextInt(16); BG3Priority=s.nextInt(16); BGnxOFS=s.nextInt(16); spriteAddress=s.nextInt(16); 
		NMILine=s.nextInt()==1; inDMA=s.nextInt()==1; VMAHigh=s.nextInt()==1; recomputeClipWindows=s.nextInt()==1; displayBlanked=s.nextInt()==1; spriteHighTable=s.nextInt()==1; apuFirstRead=s.nextInt()==1;

		if(snes.savestateversion>=2)
		{
			matrixA=s.nextInt(); matrixB=s.nextInt(); matrixC=s.nextInt(); matrixD=s.nextInt(); mode7CenterX=s.nextInt(); mode7CenterY=s.nextInt();
			mode7byte=s.nextInt(); mode7Repeat=s.nextInt(); mode7HOffset=s.nextInt(); mode7VOffset=s.nextInt();
			HBeamPositionLatch=s.nextInt(); VBeamPositionLatch=s.nextInt(); spriteTileAddressOffset=s.nextInt(); IRQVBeamPosition=s.nextInt();
			bg[1].bg=s.nextInt(); bg[1].visible=s.nextInt()==1; bg[1].window1Enabled=s.nextInt()==1; bg[1].window2Enabled=s.nextInt()==1; bg[1].window1ClippedIn=s.nextInt()==1; bg[1].window2ClippedIn=s.nextInt()==1;
			bg[2].bg=s.nextInt(); bg[2].visible=s.nextInt()==1; bg[2].window1Enabled=s.nextInt()==1; bg[2].window2Enabled=s.nextInt()==1; bg[2].window1ClippedIn=s.nextInt()==1; bg[2].window2ClippedIn=s.nextInt()==1;
			bg[3].bg=s.nextInt(); bg[3].visible=s.nextInt()==1; bg[3].window1Enabled=s.nextInt()==1; bg[3].window2Enabled=s.nextInt()==1; bg[3].window1ClippedIn=s.nextInt()==1; bg[3].window2ClippedIn=s.nextInt()==1;
			bg[4].bg=s.nextInt(); bg[4].visible=s.nextInt()==1; bg[4].window1Enabled=s.nextInt()==1; bg[4].window2Enabled=s.nextInt()==1; bg[4].window1ClippedIn=s.nextInt()==1; bg[4].window2ClippedIn=s.nextInt()==1;
			doMatrixMultiply=s.nextInt()==1;
			mode7HFlip=s.nextInt()==1; 
			mode7VFlip=s.nextInt()==1; 
			spritesVisible=s.nextInt()==1; 
			spriteWindow1Enabled=s.nextInt()==1; 
			spriteWindow2Enabled=s.nextInt()==1; 
			spriteWindow1ClippedIn=s.nextInt()==1; 
			spriteWindow2ClippedIn=s.nextInt()==1; 
			VInterruptsEnabled=s.nextInt()==1; 
			HInterruptsEnabled=s.nextInt()==1; 
			forceVisible=s.nextInt()==1; 
			readHigh213c=s.nextInt()==1; 
			readHigh213d=s.nextInt()==1;
		}
		
		for(int i=0; i<0x10000; i++)
			VRAM[i]=(byte)s.nextInt(16);
		for(int i=0; i<512+32; i++)
			spriteRAM[i]=(byte)s.nextInt(16);
		for(int i=0; i<512; i++)
			paletteRAM[i]=(byte)s.nextInt(16);

		if(snes.savestateversion>=2)
		{
			if(s.next().equals("ppudone"))
				System.out.println("loaded ppu");
			else
				System.out.println("error loading ppu");
		}
	}
}
