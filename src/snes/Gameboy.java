package snes;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

public class Gameboy 
{
	SNES snes;
	Z80 z80;

	byte[] rom, graphicsRAM, externalRAM, workingRAM, spriteRAM, page0RAM;
	int rombank,rambank,ramenabled,rommode,romoffset,ramoffset,cartridgeType,romSizeMask,romSize;
	int timerDivider,timerCounter,timerModulo,timerControl,timerCycles;
	boolean background,window,display,sprites;
	int scrollx,scrolly,windowx,windowy,gpumode,gpucycles,line,cmpline,lcdstate,windowmap,tilemap,tileset,spriteSizeX,spriteSizeY;
	int[] palette,objpalette0,objpalette1;
	int[][] frame;
	int soundRegister2a,soundRegister2b,soundRegister2c,soundRegister2d;
	int keyboardColumn;
	long frametime;
	String filename,name,savename;
	long frameCount,instructions,cycles;
	long lastRealTime;
	long lastFrameCount;

	static final int HBLANK=0,VRAM=1,SPRITE=2,VBLANK=3;
	static final int INSTRS_PER_HBLANK=61, INSTRS_PER_DIV=33, INSTRS_PER_TIMA=6000;

	public Gameboy(SNES snes)
	{
		this.snes=snes;
		z80=new Z80(snes);
	}
	
	public void loadCartridge(String filename)
	{
		this.filename=filename;
		snes.gamename=filename;
		byte[] rawdata;
		try 
		{
			File f=new File(filename);
			name=f.getName();
			savename=filename.substring(0,filename.indexOf(".")+1)+"sav";
			rawdata=new byte[(int)f.length()];
			romSize=(int)f.length();
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
		
		rom=rawdata;
		reset();
	}
	
	public void constructMemory()
	{
		graphicsRAM=new byte[0x2000];
		externalRAM=new byte[0x8000];
		workingRAM=new byte[0x2000];
		spriteRAM=new byte[0xa0];
		page0RAM=new byte[0x80];
		rombank=0;
		rambank=0;
		ramenabled=0;
		rommode=0;
		romoffset=0x4000;
		ramoffset=0;
//		cartridgeType=rom[0x147]&3;
		cartridgeType=rom[0x147]&0xff;
if (cartridgeType==6) cartridgeType&=3;
		name="";
		for (int i=0x134; i<=0x142; i++)
		{
			if(rom[i]==0) break;
			name+=(char)rom[i];
		}
		System.out.println(name);
		System.out.println("Cartridge type: "+cartridgeType);
		romSizeMask=new int[]{0x7fff,0xffff,0x1ffff,0x3ffff,0x7ffff,0xfffff,0x1fffff,0x3fffff,0x7fffff}[rom[0x148]];
	}
	
	public void reset()
	{
		z80.reset();
		constructMemory();
		timerDivider=timerCounter=timerModulo=timerControl=timerCycles=0;
		memory_write(0xffff,(byte)1);
		background=false;
		sprites=false;
		spriteSizeX=spriteSizeY=8;
		tilemap=tileset=0;
		window=false;
		windowmap=0;
		display=false;
		windowx=windowy=0;
		palette=new int[]{0,1,2,3};
		objpalette0=new int[]{0,1,2,3};
		objpalette1=new int[]{0,1,2,3};
		scrollx=scrolly=0;
		gpucycles=0;
		gpumode=HBLANK;
		line=0;
		lcdstate=0;
		cmpline=0;
		frame=new int[160][144];
		keyboardColumn=0;
		soundRegister2a=soundRegister2b=soundRegister2c=soundRegister2d=0;
		frameCount=instructions=cycles=0;
		loadSAV();
	}
	
//	long skip=139985, skipend=139987+2;
//	long skip=100000000,skipend=-1;
	
	public void mainLoop()
	{
		int cyclepos;
		boolean updateScanLine;
		snes.multithreaded=true;
		frametime=System.currentTimeMillis();
		snes.dsp.rawsound=new ArrayList<Integer>();
		
		while(true)
		{
			if(!z80.halted)
			{
				z80.doInstruction();
//				System.out.println(instructions+" "+z80.PC+" "+z80.A+" "+z80.B+" "+z80.C+" "+z80.D+" "+z80.E+" "+z80.H+" "+z80.L+" "+z80.SP);
//				z80.setFlags(); System.out.printf("%s\tpc=%x\taf=%x\tbc=%x\tde=%x\thl=%x\tsp=%x\n",z80.instruction,z80.PC,(z80.A<<8)|z80.FLAGS,(z80.B<<8)|z80.C,(z80.D<<8)|z80.E,(z80.H<<8)|z80.L,z80.SP);
				cycles+=z80.cycles;
			}
			else
				cycles+=4;
			
			if(z80.interrupt_deferred>0)
			{
				z80.interrupt_deferred--;
				if(z80.interrupt_deferred==1)
				{
					z80.interrupt_deferred=0;
					z80.FLAG_I=1;
				}
			}
			z80.checkForInterrupts();
			
			if((timerControl&4)!=0)
			{
				boolean dotimer=false;
				if(!snes.cycleAccurate)
				{
					if((instructions+1)%INSTRS_PER_TIMA==0)
						dotimer=true;
				}
				else
					if((cycles+1)%INSTRS_PER_TIMA==0)
						dotimer=true;
				if(dotimer)
				{
					if(timerCounter>0xff)
					{
						timerCounter=timerModulo;
						z80.throwInterrupt(4);
					}
					else
						timerCounter++;
				}
			}
			if(!snes.cycleAccurate)
			{
				if((instructions+1)%INSTRS_PER_DIV==0)
					timerDivider=(timerDivider+1)&0xff;				
			}
			else
			{
				if((cycles+1)%INSTRS_PER_DIV==0)
					timerDivider=(timerDivider+1)&0xff;				
			}
			
			if(!snes.cycleAccurate)
				cyclepos=(int)((instructions+1)%INSTRS_PER_HBLANK);
			else
				cyclepos=(int)((cycles+1)%INSTRS_PER_HBLANK);
			updateScanLine=cyclepos==0;
			if(line>144) 
			{
				if (gpumode!=VBLANK)
				{
					gpumode=VBLANK;
				}
			}
			else if(cyclepos<=3*INSTRS_PER_HBLANK/6)
			{
				if (gpumode!=HBLANK)
				{
					gpumode=HBLANK;
				}
			}
			else if(cyclepos<=4*INSTRS_PER_HBLANK/6)
			{
				if(gpumode!=SPRITE)
				{
					gpumode=SPRITE;
				}
			}
			else
			{
				if(gpumode!=VRAM)
				{
					gpumode=VRAM;
				}
			}
			if(updateScanLine)
				gpuRefresh();

			instructions++;

			if((instructions&131071)==0)
			{
				statusUpdate();
				if(!snes.mute)
					snes.dsp.soundplayer.dumpsound();
				else
					snes.dsp.rawsound=new ArrayList<Integer>();
			}
			snes.pauselock.testlock();
		}
	}
	
	public byte memory_read(int address)
	{
		address&=0xffff;
		if(address<=0x3fff)
			return rom[address&0x3fff];
		if(address<=0x7fff)
		{
			if(romSize>romoffset)
				return rom[romoffset+(address&0x3fff)];
			else
				return 0;
		}
		if(address<=0x9fff)
			return graphicsRAM[address&0x1fff];
		if(address<=0xbfff)
			return externalRAM[(ramoffset&0xffff)+(address&0x1fff)];
		if(address<=0xdfff)
			return workingRAM[address&0x1fff];
		if(address<=0xfdff)
			return workingRAM[address&0x1fff];
		if(address<=0xfe9f)
			return spriteRAM[address&0xff];
		if(address>=0xff80 && address<=0xffff)
			return page0RAM[address&0x7f];
		if(address>=0xff00 && address<=0xff7f)
			return ioReadByte(address);

		return 0;
	}
	
	public void memory_write(int address, byte b)
	{
		int mbc=0;
		boolean hasram=false;
		switch(cartridgeType)
		{
		case 0: mbc=0; break;
		case 1: case 2: case 3: mbc=1; break;
//		case 5: case 6: mbc=2; break;
		case 0x12: case 0x13: mbc=3; break;
//		case 0x1a: case 0x1b: case 0x1c: case 0x1d: case 0x1e: mbc=5; break;
		default: System.out.println("Unhandled cartridge type "+cartridgeType); System.exit(0);
		}
		switch(cartridgeType)
		{
		case 2: case 3: case 8: case 9: case 0x12: case 0x13: case 0x1a: case 0x1b: hasram=true;
		}
		
		address&=0xffff;
		int by=b&0xff;
		
		if(address<=0x1fff)
		{
//			if(mbc==1 && hasram)
			if(hasram)
			{
				if((by&0xf)==0xa)
					ramenabled=1;
				else
					ramenabled=0;
			}
		}
		else if (address<=0x3fff)
		{
			if(mbc==1)
			{
				int value=by&0x1f;
				if(value==0) value=1;
				rombank=(rombank&0x60)+value;
				romoffset=(rombank*0x4000)&romSizeMask;
//System.out.printf("Setting rom to bank %x offset %x\n", rombank,romoffset);
			}
			if(mbc==3)
			{
				int value=by&0x7f;
				if(value==0) value=1;
//				rombank=(rombank&0x60)+value;
				rombank=value;
				romoffset=(rombank*0x4000)&romSizeMask;
			}
		}
		else if (address<=0x5fff)
		{
			if(mbc==1)
			{
				if(rommode==1)
				{
					rambank=by&3;
					ramoffset=rambank*0x2000;
				}
				else
				{
					rombank=(rombank&0x1f)+((by&3)<<5);
					romoffset=(rombank*0x4000)&romSizeMask;
				}
			}			
			if(mbc==3)
			{
				if(rommode==1)
				{
//					rambank=by&3;
					rambank=by;
					ramoffset=rambank*0x2000;
				}
				else
				{
//					rombank=(rombank&0x7f)+((by&3)<<5);
//					romoffset=(rombank*0x4000)&romSizeMask;
				}
			}			
		}
		else if (address<=0x7fff)
		{
//			if (cartridgeType==2 || cartridgeType==3)
			if ((mbc==1 || mbc==3) && hasram)
				rommode=by&1;
		}
		else if (address<=0x9fff)
			graphicsRAM[address&0x1fff]=b;
		else if (address<=0xbfff)
		{
			if(ramenabled==1)
				externalRAM[(ramoffset&0xffff)+(address&0x1fff)]=b;
		}
		else if (address<=0xdfff)
			workingRAM[address&0x1fff]=b;
		else if (address<=0xfdff)
			workingRAM[address&0x1fff]=b;
		else if (address<=0xfe9f)
			spriteRAM[address&0xff]=b;
		else if (address>=0xff80 && address<=0xffff)
			page0RAM[address&0x7f]=b;
		else if (address>=0xff00 && address<=0xff7f)
			ioWriteByte(address,b);
	}

	private byte ioReadByte(int address)
	{
//System.out.printf("io read from %x\n", address);
		switch(address)
		{
		case 0xff40: return gpuGetControlByte();
		case 0xff41: return gpuGetLCDState();
		case 0xff42: return (byte)(scrolly&0xff);
		case 0xff43: return (byte)(scrollx&0xff);
		case 0xff44: return (byte)(line&0xff);
		case 0xff45: return (byte)(cmpline&0xff);
		case 0xff4a: return (byte)(windowy&0xff);
		case 0xff4b: return (byte)(windowx&0xff);
		case 0xff00: return getkey();
		case 0xff04: return (byte)(timerDivider&0xff);
		case 0xff05: return (byte)(timerCounter&0xff);
		case 0xff06: return (byte)(timerModulo&0xff);
		case 0xff07: return (byte)(timerControl&0xff);
//		case 0xff0f: return (byte)(z80.interrupts&0xff);
		case 0xff0f: return (byte)(0xe0|(z80.interrupts&0xff));
		case 0xff47: return (byte)((palette[3]<<6)|(palette[2]<<4)|(palette[1]<<2)|(palette[0]));
		default: fault("Unhandled I/O read "+Integer.toHexString(address));
		}
		return 0;
	}
	
	private void ioWriteByte(int address, byte b)
	{
//System.out.printf("io write to %x: %x\n", address,b&0xff);
		int by=b&0xff;
		switch(address)
		{
		case 0xff40: gpuSetControlByte(b); break;
		case 0xff41: lcdstate=b&0xff; break;
		case 0xff42: scrolly=b&0xff; break;
		case 0xff43: scrollx=b&0xff; break;
		case 0xff44: line=0; break;
		case 0xff45: cmpline=b&0xff; break;
		case 0xff47: palette[0]=by&3; palette[1]=(by>>2)&3; palette[2]=(by>>4)&3; palette[3]=(by>>6)&3; break;
		case 0xff48: objpalette0[0]=by&3; objpalette0[1]=(by>>2)&3; objpalette0[2]=(by>>4)&3; objpalette0[3]=(by>>6)&3; break;
		case 0xff49: objpalette1[0]=by&3; objpalette1[1]=(by>>2)&3; objpalette1[2]=(by>>4)&3; objpalette1[3]=(by>>6)&3; break;
		case 0xff4a: windowy=b&0xff; break;
		case 0xff4b: windowx=b&0xff; break;
		case 0xff00: keyboardColumn=b&0xff; break;
//		case 0xff04: timerDivider=b&0xff; break;
		case 0xff04: timerDivider=0; break;
		case 0xff05: timerCounter=b&0xff; break;
		case 0xff06: timerModulo=b&0xff; break;
		case 0xff07: timerControl=b&0xff; break;
		case 0xff46: dma(b); break;
		case 0xff10: case 0xff11: case 0xff12: case 0xff13: case 0xff14: break; 		//sound channel 1
		case 0xff15: case 0xff1a: case 0xff1b: case 0xff1c: case 0xff1d: case 0xff1e: case 0xff1f: case 0xff20: case 0xff21: case 0xff22: case 0xff23: case 0xff24: case 0xff25: case 0xff26: break;
		case 0xff16: soundRegister2a=b&0xff; break;
		case 0xff17: soundRegister2b=b&0xff; break;
		case 0xff18: soundRegister2c=b&0xff; break;
		case 0xff19: soundRegister2d=b&0xff; if((b&0x80)!=0) soundchannel(2); break;
		case 0xff0f: z80.interrupts=b&0xff; break;
		default: fault("Unhandled I/O write "+Integer.toHexString(address)+" "+Integer.toHexString(b));
		}
	}
	
	private void fault(String error)
	{
//		System.out.println(error);
	}
	
	private byte gpuGetLCDState()
	{
		int by=0;
		if(line==cmpline) by|=4;
		if(gpumode==VBLANK) by|=1;
		if(gpumode==SPRITE) by|=2;
		if(gpumode==VRAM) by|=3;
		return (byte)((by|(lcdstate&0xf8))&0xff);
	}
	
	private byte gpuGetControlByte()
	{
		int by=0;
		if(background) by|=1;
		if(sprites) by|=2;
		if(spriteSizeY==16) by|=4;
		if(tilemap==1) by|=8;
		if(tileset==1) by|=16;
		if(window) by|=32;
		if(windowmap==1) by|=64;
		if(display) by|=128;
		return (byte)by;
	}
	
	private void gpuSetControlByte(byte b)
	{
		background=(b&1)!=0;
		sprites=(b&2)!=0;
		spriteSizeY=(b&4)!=0?16:8;
		tilemap=(b&8)!=0?1:0;
		tileset=(b&16)!=0?1:0;
		window=(b&32)!=0;
		windowmap=(b&64)!=0?1:0;
		display=(b&128)!=0;
	}

	private void dma(int address)
	{
		address=(address&0xff)<<8;
		for (int i=0; i<0xa0; i++)
			memory_write(0xfe00+i,memory_read(address+i));
	}
	
	private byte getkey()
	{
		int key=0xf|(keyboardColumn&0x30);
		if((keyboardColumn&0x30)==0x10)
		{
			if(snes.snesgui.controllerComponent.A) key&=0xe;
			if(snes.snesgui.controllerComponent.B) key&=0xd;
			if(snes.snesgui.controllerComponent.select) key&=0xb;
			if(snes.snesgui.controllerComponent.start) key&=0x7;
			return (byte)key;
		}
		else if ((keyboardColumn&0x30)==0x20)
		{
			if(snes.snesgui.controllerComponent.right) key&=0xe;
			if(snes.snesgui.controllerComponent.left) key&=0xd;
			if(snes.snesgui.controllerComponent.up) key&=0xb;
			if(snes.snesgui.controllerComponent.down) key&=0x7;
			return (byte)key;		
		}
		return (byte)key;
	}
	
	private void gpuRefresh()
	{
		if(line<144)
		{
			if(line==143)
//				if((lcdstate&0x10)!=0)
					z80.throwInterrupt(1);
			for(int x=0; x<160; x++)
				frame[x][line]=0;
			gpuGetBackground();
			gpuGetWindow();
			gpuGetSprite();
		}

		line++;
		if(line%153==cmpline)
			if((lcdstate&0x40)!=0)
				z80.throwInterrupt(2);
		if(line>=153)
		{
			line=0;
			gpuPaintFrame();
		}
	}
	private void gpuPaintFrame()
	{
		if(snes.multithreaded)
			snes.screen.repaint();
		else
			snes.screen.paintImmediately(0, 0, 160*SNESGUI.GBSCALE, 144*SNESGUI.GBSCALE);

		frameCount++;
		long current=System.currentTimeMillis();
		if(current-frametime<1000.0/snes.MAX_FPS)
		{
			try{Thread.sleep((int)(1000.0/snes.MAX_FPS-(current-frametime)));} catch(InterruptedException e){}
		}
		frametime=current;
		//pause if we're single stepping on frames
		if(snes.singlestepframe)
		{
			snes.singlestepframe=false;
			snes.pauselock.lock();
		}
	}
	private void gpuGetBackground()
	{
		if(!background) return;

		int ys=(line+scrolly)&0xff;
		for(int x=0; x<160; x++)
		{
			int xs=(scrollx+x)&0xff;
			int tileaddr=graphicsRAM[(ys/8*32+xs/8+new int[]{0x1800,0x1c00}[tilemap])&0x1fff]&0xff;
			if(tileset==0)
			{
				if(tileaddr>=0x80)
					tileaddr-=256;
				tileaddr=(tileaddr*16+0x1000)&0xffff;
			}
			else
				tileaddr=(tileaddr*16)&0xffff;
			int pixely=ys&7;
			int pixelnumber=(pixely<<3)+(xs&7);
			int pixel=(((graphicsRAM[(tileaddr+pixely*2+1)&0x1fff]>>(7-(pixelnumber&7)))&1)*2)|((graphicsRAM[tileaddr+pixely*2]>>(7-(pixelnumber&7)))&1);
			frame[x][line]=palette[pixel]&3;
		}
	}
	private void gpuGetSprite()
	{
		if(!sprites) return;

		for (int spritenumber=0; spritenumber<40; spritenumber++)
		{
			int spritey=(spriteRAM[spritenumber*4]&0xff)-16;
			if(spritey+spriteSizeY<=line || spritey>line) continue;
			int options=(spriteRAM[spritenumber*4+3]&0xff);
			if((options&0x80)!=0) continue;
			int spritex=(spriteRAM[spritenumber*4+1]&0xff)-8;
			if(spritex+8<=0 || spritex>160) continue;
			
			//get the tile
			int tileaddr=spriteRAM[spritenumber*4+2]&0xff;
			tileaddr=(tileaddr<<4)&0xffff;
			//figure out where x,y is in the sprite
			int offsety=line-spritey;
			if((options&0x40)!=0)
				offsety=spriteSizeY-1-offsety;
			int pixely=offsety&(spriteSizeY-1);
			for (int x=spritex; x<spritex+8; x++)
			{
				if(x>=160 || x<0) continue;
				int offsetx=x-spritex;
				if((options&0x20)!=0)
					offsetx=7-offsetx;
				int pixelx=offsetx&7;
				int pixelnumber=(pixely<<3)+pixelx;
				int pixeloffset=pixelnumber>>3;
				int pixelbyte0=graphicsRAM[(tileaddr+(pixeloffset<<1))&0x1fff]&0xff;
				int pixelbyte1=graphicsRAM[(tileaddr+(pixeloffset<<1)+1)&0x1fff]&0xff;
				int pixelbit0=(pixelbyte0>>(7-(pixelnumber&7)))&1;
				int pixelbit1=(pixelbyte1>>(7-(pixelnumber&7)))&1;
				int pixel=(pixelbit1<<1)|pixelbit0;
				//get the color from the palette
				int scolor=0;
				if((options&0x10)==0)
					scolor=objpalette0[pixel];
				else
					scolor=objpalette1[pixel];
				if(pixel!=0)
					frame[x][line]=scolor;
			}
		}
	}
	private void gpuGetWindow()
	{
		if(!window) return;
		for (int x=0; x<160; x++)
		{
			int wx=windowx-7;
			int wy=windowy;
			if (wx>x || wy>line) continue;
			int startaddr=0x1800;
			if(windowmap!=0)
				startaddr=0x1c00;
			int tilex=(x-wx)>>3;
			int tiley=(line-wy)>>3;
			int tileaddr=graphicsRAM[startaddr+tiley*32+tilex];
			if (tileset==0)
			{
				if(tileaddr>=0x80) tileaddr-=256;
				tileaddr=tileaddr<<4;
				tileaddr=(tileaddr+0x1000)&0xffff;
			}
			else
			{
				tileaddr=tileaddr<<4;
				tileaddr&=0xffff;
			}
			int pixelx=(x-wx)&7;
			int pixely=(line-wy)&7;
			int pixelnumber=pixely*8+pixelx;
			int pixeloffset=pixelnumber>>3;
			int pixelbyte0=graphicsRAM[(tileaddr+pixeloffset*2)&0x1fff];
			int pixelbyte1=graphicsRAM[(tileaddr+pixeloffset*2+1)&0x1fff];
			int pixelbit0=(pixelbyte0>>(7-(pixelnumber&7)))&1;
			int pixelbit1=(pixelbyte1>>(7-(pixelnumber&7)))&1;
			int pixel=(pixelbit1<<1)|pixelbit0;
			frame[x][line]=palette[pixel]&3;
		}
	}

	private void soundchannel(int channel)
	{
		if(snes.mute) return;

		int registera=0,registerb=0,registerc=0,registerd=0;
		switch(channel)
		{
		case 2:
			registera=soundRegister2a; registerb=soundRegister2b; registerc=soundRegister2c; registerd=soundRegister2d; break;
		}
//System.out.printf("a %x b %x c %x d %x\n",registera,registerb,registerc,registerd);
		double duty = new double[]{0.125,.25,.5,.75}[(registera>>6)&3];
		int gblength=registera&0x3f;
		if(gblength==0) gblength=0x3f;
		if((registerd&0x40)==0) gblength=0;
		
		double gbfrequency=registerc|((registerd&7)<<8);
		double gbamplitude=(registerb>>4)&0xf;
		double amplitudestep=registerb&7;
		double frequency;
		if((registerb&0x8)==0)
			amplitudestep*=-1;
		if(gbfrequency!=2048)
			frequency=131072.0/(2048.0-gbfrequency);
		else
			frequency=64.0;
//System.out.println(frequency);
//		double baselength=1/64.0;
//baselength/=8;
		double amplitude=1.0;
	//	double duration=baselength*gblength;
		double duration=(64-gblength)/256.0;
		double maxduration=64/256.0;
duration/=(snes.dsp.SPEED_FACTOR/1.5);
maxduration/=(snes.dsp.SPEED_FACTOR/1.5);
		int resolution=SoundPlayer.GAMEBOYSAMPLESIZE;
if(frequency<100.0) duration=0;
//		int[] s=new int[(int)(duration*resolution)];
		int[] s=new int[(int)(maxduration*resolution)];
		for (int i=0; i<(int)(duration*resolution); i++)
		{
			try{
			if(i%(resolution/frequency)<duty*resolution/frequency)
				s[i]=1;
			else
				s[i]=0;
			}catch(ArithmeticException e){}
		}
		for (int i=0; i<s.length; i++)
		{
			try{
			if((i%((int)(duration*resolution)))==0)
			{
				amplitude+=amplitudestep*(1.0/16.0)*(gbamplitude/16.0);
				if(amplitude>1.0) amplitude=1.0;
				if(amplitude<0.0) amplitude=0.0;
			}
			}catch(ArithmeticException e){}
			s[i]=(int)(0x7f*amplitude*s[i]);
			snes.dsp.rawsound.add(s[i]);
		}
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
			saveSAV();
			System.exit(0);
		}
		else if (k=='t')
		{
			snes.pauselock.lock();
			snes.pauselock.sleepUntilLocked();
			snes.multithreaded=!snes.multithreaded;			
		}
		else if (k=='-')
		{
			snes.mute=!snes.mute;
			if(snes.mute)
				snes.snesgui.settext("Sound off");
			else
				snes.snesgui.settext("Sound on");
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
			String name=snes.gamename.substring(0,snes.gamename.length()-3)+"_gbstate"+k+".txt";
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
			String name=snes.gamename.substring(0,snes.gamename.length()-3)+"_gbstate"+k2+".txt";
			snes.loadStateFromFile(name);
			if(!l) snes.pauselock.unlock();
		}
		else if(k=='A'||k=='B'||k=='S'||k=='T'||k=='u'||k=='d'||k=='l'||k=='r')
			z80.throwInterrupt(0x10);
	}
	
	public void keyup(char k)
	{
		if(k=='A'||k=='B'||k=='S'||k=='T'||k=='u'||k=='d'||k=='l'||k=='r')
			z80.throwInterrupt(0x10);
	}

	public void statusUpdate()
	{
		String text=name+": ";
		text+=instructions/1000000+" M.insts "+cycles/1000000+" M.cycles ";
		text+=frameCount+" frames ";
		try{
		text+=(frameCount-lastFrameCount)*1000 / ((System.currentTimeMillis()-lastRealTime)) + " fps ";
		}catch(ArithmeticException e){}
		text+="PC "+Integer.toHexString(z80.PC);
		snes.snesgui.settext(text);
		lastRealTime=System.currentTimeMillis();
		lastFrameCount=frameCount;
	}

	public void saveSAV()
	{
		try
		{
			BufferedOutputStream out=new BufferedOutputStream(new FileOutputStream(savename));
			for(int address=0; address<externalRAM.length; address++)
				out.write(externalRAM[address]);
			out.close();
			System.out.println("Savefile saved to "+savename);
		}
		catch(IOException e)
		{
			System.out.println("Couldn't save sram");
		}
	}

	public void loadSAV()
	{
		try 
		{
			byte[] sav;
			File f=new File(savename);
			FileInputStream fis=new FileInputStream(savename);
			sav=new byte[(int)f.length()];
			fis.read(sav);
			fis.close();
			for (int i=0; i<sav.length && i<externalRAM.length; i++)
				externalRAM[i]=sav[i];
		} 
		catch (FileNotFoundException e) 
		{
			System.out.println("SRAM file "+savename+" not found");
			return;
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
			return;
		}
	}
	public String dumpGameboyState()
	{		
		StringBuilder s=new StringBuilder();
		s.append("gameboy\n");
		s.append(String.format("%d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d ",rombank,rambank,ramenabled,rommode,romoffset,ramoffset,cartridgeType,romSizeMask,romSize,timerDivider,timerCounter,timerModulo,timerControl,timerCycles,background?1:0,window?1:0,display?1:0,sprites?1:0,scrollx,scrolly,windowx,windowy,gpumode,gpucycles,line,cmpline,lcdstate,windowmap,tilemap,tileset,spriteSizeX,spriteSizeY));
		s.append(keyboardColumn+" "+soundRegister2a+" "+soundRegister2b+" "+soundRegister2c+" "+soundRegister2d+" "+filename+" "+name+" "+savename+" "+frameCount+" "+instructions+" "+cycles+" ");
		for(int a=0x0000; a<romSize; a++)
			s.append(String.format("%x ",rom[a]));
		for(int a=0x0000; a<0x2000; a++)
			s.append(String.format("%x ",graphicsRAM[a]));
		for(int a=0x0000; a<0x8000; a++)
			s.append(String.format("%x ",externalRAM[a]));
		for(int a=0x0000; a<0x2000; a++)
			s.append(String.format("%x ",workingRAM[a]));
		for(int a=0x0000; a<0xa0; a++)
			s.append(String.format("%x ",spriteRAM[a]));
		for(int a=0x0000; a<0x80; a++)
			s.append(String.format("%x ",page0RAM[a]));
		for(int a=0x0000; a<4; a++)
			s.append(String.format("%x ",palette[a]));
		for(int a=0x0000; a<4; a++)
			s.append(String.format("%x ",objpalette0[a]));
		for(int a=0x0000; a<4; a++)
			s.append(String.format("%x ",objpalette1[a]));
		for(int x=0; x<160; x++)
			for(int y=0; y<144; y++)
				s.append(String.format("%x ",frame[x][y]));
		s.append("\n");
		return s.toString();
	}
	
	public void loadGameboyState(String state)
	{
		Scanner s=new Scanner(state);
		rombank=s.nextInt(); rambank=s.nextInt(); ramenabled=s.nextInt(); rommode=s.nextInt(); romoffset=s.nextInt(); ramoffset=s.nextInt(); cartridgeType=s.nextInt(); romSizeMask=s.nextInt(); romSize=s.nextInt(); timerDivider=s.nextInt(); timerCounter=s.nextInt(); timerModulo=s.nextInt(); timerControl=s.nextInt(); timerCycles=s.nextInt();
		background=s.nextInt()==1; window=s.nextInt()==1; display=s.nextInt()==1; sprites=s.nextInt()==1; 
		scrollx=s.nextInt(); scrolly=s.nextInt(); windowx=s.nextInt(); windowy=s.nextInt(); gpumode=s.nextInt(); gpucycles=s.nextInt(); line=s.nextInt(); cmpline=s.nextInt(); lcdstate=s.nextInt(); windowmap=s.nextInt(); tilemap=s.nextInt(); tileset=s.nextInt(); spriteSizeX=s.nextInt(); spriteSizeY=s.nextInt();
		keyboardColumn=s.nextInt(); soundRegister2a=s.nextInt(); soundRegister2b=s.nextInt(); soundRegister2c=s.nextInt(); soundRegister2d=s.nextInt(); filename=s.next(); name=s.next(); savename=s.next(); 
		frameCount=s.nextLong(); instructions=s.nextLong(); cycles=s.nextLong();

		rom=new byte[romSize];
		for(int a=0x0000; a<romSize; a++)
			rom[a]=(byte)s.nextInt(16);
		for(int a=0x0000; a<0x2000; a++)
			graphicsRAM[a]=(byte)s.nextInt(16);
		for(int a=0x0000; a<0x8000; a++)
			externalRAM[a]=(byte)s.nextInt(16);
		for(int a=0x0000; a<0x2000; a++)
			workingRAM[a]=(byte)s.nextInt(16);
		for(int a=0x0000; a<0xa0; a++)
			spriteRAM[a]=(byte)s.nextInt(16);
		for(int a=0x0000; a<0x80; a++)
			page0RAM[a]=(byte)s.nextInt(16);
		for(int a=0x0000; a<4; a++)
			palette[a]=(byte)s.nextInt(16);
		for(int a=0x0000; a<4; a++)
			objpalette0[a]=(byte)s.nextInt(16);
		for(int a=0x0000; a<4; a++)
			objpalette1[a]=(byte)s.nextInt(16);
		for(int x=0; x<160; x++)
			for(int y=0; y<144; y++)
				frame[x][y]=(byte)s.nextInt(16);
	}
}
