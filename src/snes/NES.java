//http://nesdev.com/NESDoc.pdf
//http://nesdev.com/NinTech.txt
//http://wiki.nesdev.com/w/index.php/MMC1
//http://fms.komkon.org/EMUL8/NES.html
//http://emu-docs.org/NES/nestech.txt
//http://emu-docs.org/NES/Mappers/nesmapper.txt

package snes;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class NES 
{
	SNES snes;
	byte[] rom,ram,sram,vram,vrom;
	int mmcRegister0,mmcRegister1,mmcRegister2,mmcRegister3;
	int mmcshift,mmcbits;
	int rombanks,vrombanks,mapper;
	int rombankoffset8000,rombankoffsetc000,rombankoffseta000,rombankoffsete000;
	Color[][] frame;
	String savename;
	Processor6502 processor;
	int keylatch,keyserialcount,keystate;
	int HCounter;
	int VCounter;
	boolean NMIEnabled,spriteSize,colorEnabled,spriteEnabled,backgroundEnabled,backgroundClip,spriteClip,writeUpper2006,write2005;
	int backgroundPatternTableAddress,spritePatternTableAddress,ppuAddressIncrement,nameTableAddress,backgroundColor,spriteAddress,vramAddress,hscroll,vscroll;
	byte[] spriteRAM;
	boolean vmirror,hmirror;
	int cyclecount=0;
	private boolean skipframe=false;
	long frameCount=0;
	
//	int NN,TILEX,TILEY,finex,finey;
	
	
	public static final int PPU_WIDTH=341,PPU_HEIGHT=262,PPU_FRAME=PPU_WIDTH*PPU_HEIGHT,SCREEN_WIDTH=256,SCREEN_HEIGHT=240;
	public static final int CYCLES_PER_INSTRUCTION=30;
	
	public NES(SNES snes)
	{
		this.snes=snes;
		processor=new Processor6502(snes);
	}

	public void loadCartridge(String filename) 
	{
		snes.gamename=filename;
		byte[] rawdata;
		try 
		{
			File f=new File(filename);
			savename=filename.substring(0,filename.indexOf(".")+1)+"sav";
			rawdata=new byte[(int)f.length()];
			FileInputStream fis=new FileInputStream(filename);
			fis.read(rawdata);
			fis.close();
		} 
		catch (FileNotFoundException e) 
		{
			e.printStackTrace();
			return;
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
			return;
		}
		
		if(rawdata[0]!=(byte)'N' || rawdata[1]!=(byte)'E' || rawdata[2]!=(byte)'S' || rawdata[3]!=0x1a)
		{
			System.out.println("Not a recognized NES cartridge");
			return;
		}
		
		rombanks=rawdata[4]&0xff;
		vrombanks=rawdata[5]&0xff;
		hmirror=(rawdata[6]&1)==0;
		vmirror=(rawdata[6]&1)!=0;
		mapper=(int)((rawdata[6]>>4)&0xff);
		snes.CYCLES_PER_INSTRUCTION=1;
		
		
		System.out.println("rom banks "+rombanks+" vrom banks "+vrombanks+" mapper type "+mapper);
		
		rom=new byte[rombanks*16384];
		for (int i=0; i<rom.length; i++)
			rom[i]=rawdata[i+16];
		vram=new byte[16384];
		vrom=new byte[vrombanks*8192];
		for (int i=0; i<vrom.length; i++)
			vrom[i]=rawdata[i+16+rom.length];
		sram=new byte[0x2000];
		reset();
	}

	public void reset() 
	{
		frame=new Color[SCREEN_WIDTH][SCREEN_HEIGHT];
		for (int i=0; i<SCREEN_WIDTH; i++)
			for (int j=0; j<SCREEN_HEIGHT; j++)
				frame[i][j]=Color.BLACK;
		
		ram=new byte[0x800];
		spriteRAM=new byte[256];
		
		if (vrom.length>0)
			for (int i=0; i<vrom.length && i<8192; i++)
				vram[i]=vrom[i];

		switch(mapper)
		{
		case 0:		//no mapper
			rombankoffset8000=0;
			rombankoffsetc000=1;
			break;
		case 1:		//mmc1
			rombankoffset8000=0;
			rombankoffsetc000=rombanks-1;
			mmcRegister0=0xc;
			mmcRegister1=mmcRegister2=mmcRegister3=0;
			mmcbits=mmcshift=0;
			break;
		case 2:		//unrom
			rombankoffset8000=0;
			rombankoffsetc000=rombanks-1;
			break;
		case 3:		//cnrom
			rombankoffset8000=0;
			rombankoffsetc000=rombanks-1;
			break;
		case 4:		//mmc3
			rombankoffset8000=0;
			rombankoffseta000=1;
			rombankoffsetc000=rombanks*2-2;
			rombankoffsete000=rombanks*2-1;
			break;
		default:
			System.out.println("Mapper "+mapper+" not yet supported");
			break;
		}
		processor.reset();
		ppuAddressIncrement=1;
	}

	public void mainLoop() 
	{
		while(true)
		{
			processor.doAnInstruction();
		
			HCounter+=snes.CYCLES_PER_INSTRUCTION;
			if(HCounter>=PPU_WIDTH)
			{
				HCounter-=PPU_WIDTH;
				if(VCounter<SCREEN_HEIGHT)
				{
					if(!skipframe)
						renderLine(VCounter);
				}
				VCounter++;
				if(VCounter==PPU_HEIGHT)
				{
					VCounter=0;
					//update frame
					if(!skipframe)
						snes.screen.repaint();
					//update the screen
					frameCount++;
					skipframe=(frameCount%(snes.FRAME_SKIP+1)>0);
				}
				else if (VCounter==SCREEN_HEIGHT)
				{
					//throw nmi
					if(NMIEnabled)
						processor.NMI();
				}
			}
			snes.pauselock.testlock();
		}
	}

	public void writeByte(int address, byte value) 
	{
		if (address<0x2000)
			ram[address&0x7ff]=value;
		else if (address<0x4000)
			writeIO(0x2000|(address&7),value);
		else if (address<0x4020)
			writeIO(address,value);
		//expansion ROM
		else if (address<0x6000)
			return;
		else if (address<0x8000)
			sram[address-0x6000]=value;
		else
			selectRomBank(address,value);
	}
	
	private void selectRomBank(int address, byte value)
	{
		switch(mapper)
		{
		case 0: return;
		case 1:		//mmc1
			if(mmcbits==5) { mmcbits=0; mmcshift=0; }
			if((value&0x80)==0)
			{
				mmcshift>>=1;
				mmcshift|=(value&1)<<4;
				mmcbits++;
				if(mmcbits<5) return;
			}
			switch(address>>12)
			{
			case 8: case 9:
				if((value&0x80)!=0) { mmcRegister0|=0xc; mmcbits=0; mmcshift=0; break; }
				mmcRegister0=mmcshift&0xff;
				hmirror=((mmcRegister0&1)!=0);
				vmirror=((mmcRegister0&1)==0);
				break;
			case 0xa: case 0xb:
				if((value&0x80)!=0) { mmcbits=0; mmcshift=0; break; }
				mmcRegister1=mmcshift&0xff;
				if(vrom.length>0)
				{
					if ((mmcRegister0&0x10)==0)
					{
						for(int i=0; i<8192; i++)
							vram[i]=vrom[i+((mmcRegister1&0xf)<<13)];
					}
					else
					{
						for(int i=0; i<4096; i++)
							vram[i]=vrom[i+((mmcRegister1&0xf)<<12)];
					}
				}
				break;
			case 0xc: case 0xd:
				if((value&0x80)!=0) { mmcbits=0; mmcshift=0; break; }
				mmcRegister2=mmcshift&0xff;
				if(vrom.length>0)
				{
					if ((mmcRegister0&0x10)!=0)
					{
						for(int i=0; i<4096; i++)
							vram[i]=vrom[i+0x1000+((mmcRegister1&0xf)<<12)];
					}
				}
				break;
			case 0xe: case 0xf:
				if((value&0x80)!=0) { mmcbits=0; mmcshift=0; break; }
				mmcRegister3=mmcshift&0xff;
				if((mmcRegister0&8)==0)
				{
					rombankoffset8000=(mmcshift&0xf)<<1;
					rombankoffsetc000=((mmcshift&0xf)<<1)+1;
				}
				else
				{
					if((mmcRegister0&4)==0)
						rombankoffsetc000=(mmcshift&0xf);
					else
						rombankoffset8000=(mmcshift&0xf);
				}
				if(rombanks>128)
				{
					if((mmcRegister0&0x10)==0 && (mmcRegister1&0x10)!=0)
					{
						rombankoffsetc000+=128;
						rombankoffset8000+=128;
					}
					else if ((mmcRegister0&0x10)!=0)
					{
						rombankoffsetc000+=64*(((mmcRegister1>>4)&1)|((mmcRegister2>>3)&2));
						rombankoffset8000+=64*(((mmcRegister1>>4)&1)|((mmcRegister2>>3)&2));
					}
				}
				else if (rombanks>64)
				{
					if((mmcRegister1&0x10)!=0)
					{
						rombankoffsetc000+=64;
						rombankoffset8000+=64;
					}
				}
				break;
			}				
			return;
		case 2:		//unrom
			rombankoffset8000=value&0xff;
			return;
		case 3:		//cnrom
			for (int i=0; i<8192; i++)
				vram[i]=vrom[((value&0xff)<<13)+i];
			return;
		case 4:		//mmc3
		{
			System.out.printf("mmc3 addr %x value %x\n",address,value);
			if(address<=0x9fff && (address&1)==0)
			{
				mmcRegister0=value&0xff;
			}
			else if(address>0xa000 && address<=0xbfff && (address&1)==0)
			{
				vmirror=(value&1)==0;
				hmirror=(value&1)!=0;
			}
			else if (address<=0x9fff && (address&1)==0)
			{
				switch(mmcRegister0&7)
				{
				case 6:
					if((mmcRegister0&0x40)==0)
					{
						rombankoffset8000=value&0xff;
						rombankoffsetc000=rombanks*2-2;
					}
					else
					{
						rombankoffsetc000=value&0xff;
						rombankoffset8000=rombanks*2-2;						
					}
					break;
				case 7:
					rombankoffseta000=value&0xff; break;
				case 0:
					if(vrom.length>0)
					{
						int vramoffset=(mmcRegister0&0x80)!=0? 0x1000:0;
						for(int i=0; i<0x800; i++)
							vram[vramoffset+i]=vrom[i+((value&0xff)<<11)];
					} break;
				case 1:
					if(vrom.length>0)
					{
						int vramoffset=(mmcRegister0&0x80)!=0? 0x1000:0;
						for(int i=0; i<0x800; i++)
							vram[vramoffset+0x800+i]=vrom[i+((value&0xff)<<11)];
					} break;
				case 2:
					if(vrom.length>0)
					{
						int vramoffset=(mmcRegister0&0x80)!=0? 0:0x1000;
						for(int i=0; i<0x400; i++)
							vram[vramoffset+i]=vrom[i+((value&0xff)<<10)];
					} break;
				case 3:
					if(vrom.length>0)
					{
						int vramoffset=(mmcRegister0&0x80)!=0? 0:0x1000;
						for(int i=0; i<0x400; i++)
							vram[vramoffset+i+0x400]=vrom[i+((value&0xff)<<10)];
					} break;
				case 4:
					if(vrom.length>0)
					{
						int vramoffset=(mmcRegister0&0x80)!=0? 0:0x1000;
						for(int i=0; i<0x400; i++)
							vram[vramoffset+i+0x800]=vrom[i+((value&0xff)<<10)];
					} break;
				case 5:
					if(vrom.length>0)
					{
						int vramoffset=(mmcRegister0&0x80)!=0? 0:0x1000;
						for(int i=0; i<0x400; i++)
							vram[vramoffset+i+0xc00]=vrom[i+((value&0xff)<<10)];
					} break;
				}
			}
			return;
		}
		default: return;
		}
	}
	
	private byte readIO(int address)
	{
		byte rval=0;
		switch(address)
		{
		case 0x2002:
		{
			rval=0;
			if(VCounter>=SCREEN_HEIGHT) rval|=0x80;		//VBLANK
			int sp0x=spriteRAM[3]&0xff, sp0y=spriteRAM[0]&0xff;
			if(VCounter>=sp0y && VCounter<sp0y+8 && HCounter>=sp0x) rval|=0x40;
			//if... rval|=0x40;							//sprite 0 encountered at hcounter/vcounter
			//0x20: sprite count on current line >8
			//0x10: vram writing is disabled
			return rval;
		}
		case 0x2004:
			rval=spriteRAM[spriteAddress&0xff];
			spriteAddress++;
			return rval;
		case 0x2007:
			rval=readVRAM(vramAddress);
			vramAddress+=ppuAddressIncrement;
			return rval;
		case 0x4016: case 0x4017:
			return (byte)readJoySer(address);
		}
		return 0;
	}
	
	private void writeVRAM(int address, byte value)
	{
		address&=0x3fff;
//if(address<0x2000) return;
//if(VCounter<SCREEN_HEIGHT) return;
		if(address>=0x3000 && address<0x3f00)
			address-=0x1000;
		if(vmirror && address>=0x2800 && address<0x3000)
			address-=0x800;
		if(hmirror && address>=0x2400 && address<0x2800)
			address-=0x400;
		if(hmirror && address>=0x2c00 && address<0x3000)
			address-=0x400;
		if(address>=0x3f00)
			address&=0xff1f;
		vram[address]=value;
	}
	
	private byte readVRAM(int address)
	{
		address&=0x3fff;
		if(address>=0x3000 && address<0x3f00)
			address-=0x1000;
		if(vmirror && address>=0x2800 && address<0x3000)
			address-=0x800;
		if(hmirror && address>=0x2400 && address<0x2800)
			address-=0x400;
		if(hmirror && address>=0x2c00 && address<0x3000)
			address-=0x400;
		if(address>=0x3f00)
			address&=0xff1f;
		return vram[address];
	}
	
	private void writeIO(int address, byte value)
	{
		switch(address)
		{
		case 0x2000:
			NMIEnabled=(value&0x80)!=0;
			spriteSize=(value&0x20)!=0;
			backgroundPatternTableAddress=(value&0x10)!=0? 0x1000:0;
			spritePatternTableAddress=(value&8)!=0?0x1000:0;
			ppuAddressIncrement=(value&4)!=0?32:1;
			nameTableAddress=0x2000+0x400*(value&3);
//			NN=value&3;
			break;
		case 0x2001:
			colorEnabled=(value&1)!=0;
			backgroundClip=(value&2)!=0;
			spriteClip=(value&4)!=0;
			backgroundEnabled=(value&8)!=0;
			spriteEnabled=(value&0x10)!=0;
			backgroundColor=(value>>5)&7;
			break;
		case 0x2003:
			spriteAddress=value&0xff;
			break;
		case 0x2004:
			spriteRAM[spriteAddress]=value;
			spriteAddress++;
			break;
		case 0x2006:
			if(!writeUpper2006)
			{
				vramAddress=(vramAddress&0x00ff)|((value&0x3f)<<8);
//				NN=(value>>2)&3;
				nameTableAddress=((value>>2)&3)*0x400+0x2000;
//				TILEY=(TILEY&~3)|(value&3);
//				finey=(value>>4)&3;
			}
			else
			{
				vramAddress=(vramAddress&0xff00)|(value&0xff);
//				TILEX=value&31;
//				TILEY=(TILEY&3)|((value>>3)&0x1c);
				hscroll=value>>3;
			}
			writeUpper2006=!writeUpper2006;
			break;
		case 0x2007:
			writeVRAM(vramAddress,value);
			vramAddress+=ppuAddressIncrement;
			break;
		case 0x2005:
			if(write2005)
			{
				if((value&0xff)<=238)
					vscroll=value&0xff;
//				finey=value&7;
//				TILEY=(value>>3)&31;
			}
			else
			{
				hscroll=value&0xff;
//				finex=value&7;
//				TILEX=(value>>3)&31;
			}
			write2005=!write2005;
			break;
		case 0x4014:
			for (int i=0; i<256; i++)
				spriteRAM[i]=readByte(((value&0xff)<<8)+i);
			break;
		case 0x4016:
			setJoypadLatch(value&1);
			break;
		}
	}
	
//http://emu-docs.org/NES/nestech.txt
	public byte readByte(int address) 
	{
		if (address<0x2000)
			return ram[address&0x7ff];
		else if (address<0x4000)
			return readIO(0x2000|(address&7));
		else if (address<0x4020)
			return readIO(address);
		//expansion ROM
		else if (address<0x6000)
			return 0;
		else if (address<0x8000)
			return sram[address-0x6000];
		else if (mapper==4)
		{
			if(address<0xa000)
				return rom[address-0x8000+(rombankoffset8000<<13)];
			else if(address<0xc000)
				return rom[address-0xa000+(rombankoffseta000<<13)];
			else if(address<0xe000)
				return rom[address-0xc000+(rombankoffsetc000<<13)];
			else if (address<0x10000)
				return rom[address-0xe000+(rombankoffsete000<<13)];
			else
				return 0;
		}
		else if (address<0xc000)
			return rom[address-0x8000+(rombankoffset8000<<14)];
		else if (address<0x10000)
			return rom[address-0xc000+(rombankoffsetc000<<14)];
		else
			return 0;
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
//		if(keyserialcount<16)
//			return (((keystate>>8)&0xff)>>(7-(keyserialcount++-8)))&1;
		keyserialcount++;
		if(keyserialcount==8) keyserialcount=0;
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
		case 'B': mask|=0x40; break;
		case 'A': mask|=0x80; break;
		}
		return mask;
	}
	public void keyup(char key) 
	{
		int mask=getkeymask(key);
		keystate&=(~mask);
	}

	public void keydown(char key) 
	{
		int mask=getkeymask(key);
		keystate|=mask;
	}
	
	
	
	private void renderLine(int line)
	{
		for(int x=0; x<SCREEN_WIDTH; x++)
		{
			frame[x][line]=Color.BLACK;
			if (colorEnabled)
				switch(backgroundColor)
				{
				case 1: frame[x][line]=Color.GREEN; break;
				case 2: frame[x][line]=Color.BLUE; break;
				case 4: frame[x][line]=Color.RED; break;
				}
			Color c=getSprites(x,line,true);
			if(c!=null) frame[x][line]=c;
			c=getBackground(x,line);
			if(c!=null) frame[x][line]=c;
			c=getSprites(x,line,false);
			if(c!=null) frame[x][line]=c;
		}
	}
		
	private Color getBackground(int x, int y)
	{
		if(!backgroundEnabled) return null;
		Color color=null;
		
		int xcoor=x+hscroll;

		int ycoor=(y+vscroll)%480;
		int nametablebase=nameTableAddress;
		if(xcoor>=256) { nametablebase+=0x400; xcoor-=256; }
		if(ycoor>=240) { nametablebase+=0x800; ycoor-=240; }
		nametablebase&=0x2fff;
		int attrtablebase=nametablebase+0x3c0;
		int nametableindex=nametablebase+((ycoor>>3)<<5)+(xcoor>>3);
		int patternindex=backgroundPatternTableAddress + ((readVRAM(nametableindex)&0xff)<<4);
		int point0=(readVRAM(patternindex + (ycoor&7))>>(7-(xcoor&7)))&1;
		int point1=(readVRAM(patternindex + (ycoor&7) + 8)>>(7-(xcoor&7)))&1;
		if (point0==0 && point1==0) return null;
		
		int attrtableindex=attrtablebase+((ycoor>>5)<<3)+(xcoor>>5);
		int attrtablevalue=readVRAM(attrtableindex)&0xff;
		int shift=(((ycoor>>4)&1)<<2)+(((xcoor>>4)&1)<<1);
		int point32=(attrtablevalue>>shift)&3;

		int paletteindex=(point32<<2)|(point1<<1)|point0;
		color = colorindex[readVRAM(0x3f00+paletteindex)&0x3f];
		return color;
	}
	
	//http://nesdev.com/NESDoc.pdf
	private Color getSprites(int x, int y, boolean priority)
	{
		if(!spriteEnabled) return null;
		
		Color color=null;
		int height=spriteSize? 16:8;
		for(int spritenumber=63; spritenumber>=0; spritenumber--)
		{
			int ycoor=spriteRAM[(spritenumber<<2)]&0xff;
			if(ycoor>y || ycoor+height<=y) continue;
			int xcoor=spriteRAM[(spritenumber<<2)+3]&0xff;
			if(xcoor>x || xcoor+8<=x) continue;
			int attr=spriteRAM[(spritenumber<<2)+2]&0xff;
			if(((attr&0x20)!=0)!=priority) continue;
			boolean vflip=(attr&0x80)!=0;
			boolean hflip=(attr&0x40)!=0;
			int highcolor=attr&3;
			int tileindex=spriteRAM[(spritenumber<<2)+1]&0xff;
			
			int address=tileindex<<4;

			if(!spriteSize) address|=spritePatternTableAddress;
			else if ((tileindex&1)==1) address|=0x1000;
			
			int pointx=x-xcoor;
			if(hflip) pointx=7-pointx;
			int pointy=y-ycoor;
			if(vflip) pointy=height-1-pointy;
			if (pointy>=8) pointy+=8;
			int point0=(readVRAM(address + pointy)>>(7-pointx))&1;
			int point1=(readVRAM(address + pointy + 8)>>(7-pointx))&1;
			if (point0==0 && point1==0) continue;
				
			int pointPaletteIndex = (highcolor<<2) | (point1<<1) | point0;
			int pointcolor=readVRAM(0x3f10+pointPaletteIndex)&0x3f;
			color=colorindex[pointcolor];
		}
		return color;
	}


	public void docycles(int cycledelay) 
	{
		cyclecount=cycledelay;
	}

	//http://nesdev.com/nespal.txt - "Matthew Conte" <itsbroke@classicgaming.com>
	public static final Color[] colorindex=new Color[]
	{
		   new Color(0x80,0x80,0x80), new Color(0x00,0x00,0xBB), new Color(0x37,0x00,0xBF), new Color(0x84,0x00,0xA6), new Color(0xBB,0x00,0x6A), new Color(0xB7,0x00,0x1E), new Color(0xB3,0x00,0x00), new Color(0x91,0x26,0x00), new Color(0x7B,0x2B,0x00), new Color(0x00,0x3E,0x00), new Color(0x00,0x48,0x0D), new Color(0x00,0x3C,0x22), new Color(0x00,0x2F,0x66), new Color(0x00,0x00,0x00), new Color(0x05,0x05,0x05), new Color(0x05,0x05,0x05),
		   new Color(0xC8,0xC8,0xC8), new Color(0x00,0x59,0xFF), new Color(0x44,0x3C,0xFF), new Color(0xB7,0x33,0xCC), new Color(0xFF,0x33,0xAA), new Color(0xFF,0x37,0x5E), new Color(0xFF,0x37,0x1A), new Color(0xD5,0x4B,0x00), new Color(0xC4,0x62,0x00), new Color(0x3C,0x7B,0x00), new Color(0x1E,0x84,0x15), new Color(0x00,0x95,0x66), new Color(0x00,0x84,0xC4), new Color(0x11,0x11,0x11), new Color(0x09,0x09,0x09), new Color(0x09,0x09,0x09),
		   new Color(0xFF,0xFF,0xFF), new Color(0x00,0x95,0xFF), new Color(0x6F,0x84,0xFF), new Color(0xD5,0x6F,0xFF), new Color(0xFF,0x77,0xCC), new Color(0xFF,0x6F,0x99), new Color(0xFF,0x7B,0x59), new Color(0xFF,0x91,0x5F), new Color(0xFF,0xA2,0x33), new Color(0xA6,0xBF,0x00), new Color(0x51,0xD9,0x6A), new Color(0x4D,0xD5,0xAE), new Color(0x00,0xD9,0xFF), new Color(0x66,0x66,0x66), new Color(0x0D,0x0D,0x0D), new Color(0x0D,0x0D,0x0D),
		   new Color(0xFF,0xFF,0xFF), new Color(0x84,0xBF,0xFF), new Color(0xBB,0xBB,0xFF), new Color(0xD0,0xBB,0xFF), new Color(0xFF,0xBF,0xEA), new Color(0xFF,0xBF,0xCC), new Color(0xFF,0xC4,0xB7), new Color(0xFF,0xCC,0xAE), new Color(0xFF,0xD9,0xA2), new Color(0xCC,0xE1,0x99), new Color(0xAE,0xEE,0xB7), new Color(0xAA,0xF7,0xEE), new Color(0xB3,0xEE,0xFF), new Color(0xDD,0xDD,0xDD), new Color(0x11,0x11,0x11), new Color(0x11,0x11,0x11),
	};
}
