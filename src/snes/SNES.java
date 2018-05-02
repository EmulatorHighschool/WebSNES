/*Java Super Nintendo Emulator "JavaSNES"
* Written and Copyright by Michael Black, 2014  (blackmd@gmail.com)
* 3/2014 - 5/2014
* 
 This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package snes;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Scanner;

//SNES is the main encapsulating class
public class SNES
{
	Memory memory;		//memory mapping and address space
	Processor65816 processor;	//main processor
	PPU ppu;			//picture processing unit: handles the I/O ports
	DMA[] dma=new DMA[8];	//8 dmas for transferring from memory to ports
	Video video;		//video rendering: converts PPU state into an image
	Screen screen;		//jcomponent to display video image
	SPC700 spc700;		//audio processor
	DSP dsp;			//generates waveform from audio state
	SNESGUI snesgui;	//UI: menus, button, etc.

	Romhack romhack;
	public final boolean doromhack=false;
	Gameboy gameboy;
	public boolean dogameboy=false;
	NES nes;
	public boolean dones=false;
	
	public String gamename="";		//ROM cartridge filename
	public String sramname="";		//SRAM save state filename
	public boolean cycleAccurate=false;	//if false, use fixed # cycles/inst
	public boolean IRQEnabled=false;
	public boolean multithreaded=false;	//if true, launch video rendering in its own thread (causes flickering but x2 speedup)
	public boolean apuEnabled=true;	//if false, disable spc700, and just echo back bytes sent to audio port to move games along
	public boolean ischrono=false;	//enable Chrono trigger specific hacks
	public boolean debugMode=false;	//if true. print out debugging messages
	public boolean mute=false;		//disable sound (doesn't affect sound processor and dsp, only playback) 
	public boolean recalculateIPS=true;
	
	//these numbers are from SNES9x
	//they tell how many cycles until HBLANK and VBLANK
	private final int HBLANK_EVENT=0,HDMA_START_EVENT=1,HCOUNTER_EVENT=2,HDMA_INIT_EVENT=3,RENDER_EVENT=4,WRAM_REFRESH_EVENT=5;
	//these numbers are in terms of the NTSC master clock, which runs at 21477272 master cycles / second
	public static final int CYCLES_UNTIL_HDMA_START=1106,CYCLES_UNTIL_HCOUNTER=1364,CYCLES_UNTIL_HDMA_INIT=20,CYCLES_UNTIL_RENDER=192,CYCLES_UNTIL_WRAM_REFRESH=538,CYCLES_UNTIL_HBLANK=1096;
	public static final int WRAM_REFRESH_CYCLES=40;
	
	//if n>1, rendering is done on every n frames to speed up gameplay 
	public int FRAME_SKIP=1;

	//spc700 instruction is done on every n'th 65816 instruction
	//(the name is backwards)
	public int APU_INSTRUCTIONS_PER_CPU_INSTRUCTION=3;	//10
	//if cycleAccurate is false, a fixed # cycles / instruction is used instead
	//higher numbers mean faster processing because fewer instructions are run between VBLANKs
	//if it's too high, however, games become unstable
	public int CYCLES_PER_INSTRUCTION=20;	//35 is pushing it, 25 is safer
	
	//initial VBLANK state
	int eventType=HBLANK_EVENT, nextEvent=4, instructionsSinceHCOUNTER=0;
	
	//if true, processing will halt after the next instruction
	public Lock pauselock;
	//!=null if Javasnes is running as an applet
	public SNESApplet applet=null;
	//total # of video frames so far
	public long frameCount=0;
	//keeps track of whether to draw the next frame, based on FRAME_SKIP
	private boolean skipframe=false;
	//this is for timekeeping and is updated between the first 1M and 10M instructions
	//it keeps sound effects running at a constant rate regardless of emulator speed
	public double INSTRUCTIONS_PER_SECOND=3000000;

	//is there an IRQ interrupt ready to go on the next instruction?
	public boolean interruptPending=false;
	//IRQLine: whether an IRQ interrupt has been requested (if irq is disabled, this will be ignored)
	public boolean IRQLine=false;
	//estimating the # frames per second
	private long frametime=0;
	//if javasnes is running too fast, use sleep to slow it down to this rate
	public double MAX_FPS=60.0;
	//if true, pause CPU after next the frame is rendered 
	public boolean singlestepframe=false;

	//TODO not backed up yet
	//how many cpu insts since last spc700 inst? 
	private int apucountdown=0;
	public long eventCycles=0, lastEventCycles=0;
	
	//entry point if not running as an applet
	public SNES()
	{
		snesgui=new SNESGUI(this,false);
		constructSNES();
	}
	//entry if it is an applet
	public SNES(SNESApplet applet)
	{
		this.applet=applet;
		snesgui=new SNESGUI(this,true);
		constructSNES();
	}
	//instantiate the snes components, but don't set their state yet
	public void constructSNES()
	{
		processor=new Processor65816(this);
		memory=new Memory(this);
		spc700=new SPC700(this);
		dsp=new DSP(this);
		ppu=new PPU(this);
		video=new Video(this);
		screen=new Screen(this);
		gameboy=new Gameboy(this);
		nes=new NES(this);
		pauselock=new Lock();
	}
	//load the ROM and set the components to their initial state
	public void initializeSNES()
	{
		Cartridge ff6;

		//if no SRAM is specified, don't use a save file
		if (!sramname.equals(""))
		{
			ff6=new Cartridge(gamename,sramname);
		}
		else
		{
			ff6=new Cartridge(gamename);
		}

		//get the cartridge into memory
		memory.loadCartridge(ff6);

		//some game specific hacks.
		//these reflect bugs in the emulator and shouldn't be necessary,
		//but it's easier to do this than remove the bugs
		//hopefully I can remove these in future versions
		
		//Chrono trigger: VBlank must return 0 when read
		if(memory.name.toUpperCase().contains("CHRONO TRIGGER"))
			if(!cycleAccurate)
				ischrono=true;
		//Final Fantasy II and Secret of Mana use IRQ interrupts.  Most other games don't so they're disabled by default.
		//IRQ interrupts go off on selected HBLANKs.  If a game doesn't use them, they can slow it down or even destabilize it.
		if(memory.name.toUpperCase().contains("FINAL FANTASY 2")||memory.name.toUpperCase().contains("FINAL FANTASY II")||memory.name.toUpperCase().contains("FINAL FANTASY 4")||memory.name.toUpperCase().contains("FINAL FANTASY IV"))
			IRQEnabled=true;
		if(memory.name.toUpperCase().contains("SUPER METROID"))
			IRQEnabled=true;
		if(memory.name.toUpperCase().contains("STREET FIGHTER"))
			IRQEnabled=true;
		if(memory.name.toUpperCase().contains("FLASHBACK"))
			IRQEnabled=true;
		if(memory.name.toUpperCase().contains("FATAL FURY"))
			IRQEnabled=true;
		if(memory.name.toUpperCase().contains("LEMMINGS"))
			IRQEnabled=true;
		if(memory.name.toUpperCase().contains("ULTIMA VII"))
			IRQEnabled=true;
		if(memory.name.toUpperCase().contains("PILOTWINGS"))
			IRQEnabled=true;
		if(memory.name.toUpperCase().contains("SUPER MARIO"))
			IRQEnabled=true;
		if(memory.name.toUpperCase().contains("WOLF CHILD"))
			IRQEnabled=true;
		if(memory.name.toUpperCase().contains("RR DEATH VALLEY RALLY"))
			IRQEnabled=true;
		if(memory.name.toUpperCase().contains("POCKY"))
		{
			IRQEnabled=true;
			CYCLES_PER_INSTRUCTION=15;
			cycleAccurate=false;
		}
		if(memory.name.toUpperCase().contains("SECRET OF MANA"))
		{
			IRQEnabled=true;
			//Secret of Mana needs a very low cycles/inst for its startup screen.
			//it uses IRQs at this time, but can't handle too many.
			//CPI higher than 2 means too few instructions between IRQs
			//once we're past the title screen we can set this back to normal
//			CYCLES_PER_INSTRUCTION=2;
//			cycleAccurate=true;
		}
		
		//reset processor, audio processor, and video parameters
		processor.reset();
		spc700.reset();
		ppu.ppureset();
		//set initial video state
		eventType=4;
		nextEvent=192;
		processor.instructionsUntilEvent=0;
		apucountdown=0;
	}
	
	//these vars are just used in debugging
	long skip=0l;
	long skipend=3000000l;
	boolean AButtonPressed=false,UpButtonPressed=false,DownButtonPressed=false,RightButtonPressed=false,XButtonPressed=false,StartButtonPressed=false;
	
	//this is the main emulator loop
	//actual game emulation starts here
	public void mainLoop()
	{
		long time0=System.currentTimeMillis();
		long inst0=0;
		frametime=System.currentTimeMillis();
		
		eventCycles=lastEventCycles=182;	//cycle position after reset
		
		//run forever or until user quits the emulator
		while(true)
		{	
			//first handle incoming NMI interrupts
			//these happen on VBLANK
			//most games use these NMIs to drive them
			if(processor.NMItrigger)
			{
				if ((!cycleAccurate && processor.NMItriggerPosition<=instructionsSinceHCOUNTER)||(cycleAccurate&processor.NMItriggerPosition<=eventCycles))
//				if (processor.NMItriggerPosition<=instructionsSinceHCOUNTER)
				{
					processor.NMItrigger=false;
					processor.NMItriggerPosition=0xffff;
					processor.waitForInterrupt=false;
					processor.NMI();
				}
			}

			//next handle incoming IRQ interrupts
			//these are disabled on startup, but can be scheduled to run when VCounter reaches a particular line 
			
			//to run:
			//emulator IRQs have to be enabled (currently I'm disabling them for most games, but FF2 and Secret of Mana use them)
			//the processor's IRQ enable flag must be clear
			//the PPU must have been told by the game to allow IRQs, vis VInterruptsEnabled
			//vertical line counter must have been incremented on the previous instruction - this way an IRQ can run only once at the beginning of a line
			//the line counter reaches the triggering line
			if(IRQEnabled && interruptPending)
			{
				interruptPending=false;
				processor.waitForInterrupt=false;
				if(IRQEnabled && !processor.IRQ)
					processor.IRQ();
			}

/*			//detect if we should throw an IRQ on the next instruction
			if(IRQEnabled)
			{
				boolean thisIRQ=ppu.VInterruptsEnabled;
				//if we reached the trigger line on the last inst and IRQs are enabled in the PPU, we might throw an IRQ on the next instruction
				if(IRQLine && thisIRQ)
					interruptPending=true;
				//but only throw it if VCounter was incremented in the last instruction and passed the trigger line
				if(ppu.VInterruptsEnabled)
				{
					if(ppu.VCounter+1!=ppu.IRQVBeamPosition)
						thisIRQ=false;
				}
				if(thisIRQ)
					IRQLine=true;
			}	*/

			//actually run the instruction now
			if (!processor.waitForInterrupt)
				processor.doAnInstruction();

			checkForInterrupt();
			
			//periodically display status messages and play waveforms
			//there's nothing special about this number
			if((processor.instructionCount&131071)==0)
			{
				//show the # insts in the bottom of the window
				snesgui.statusUpdate();
//				System.out.printf("ICount %d Cycle %d Inst: %s Opcode %x PBR: %x PC: %x DBR: %x D: %x A: %x X: %x Y: %x S: %x P: %x\n", processor.instructionCount,processor.cycleCount,processor.opcodeName,processor.opcode,processor.PBR,processor.PC,processor.DBR,processor.D,processor.A,processor.X,processor.Y,processor.S,processor.P);
				if((processor.instructionCount&131071)==0)
				{
					//sound waveforms build up in a buffer.  empty it now and play the sound.
					if(!mute)
						dsp.soundplayer.dumpsound();
					else
						dsp.rawsound=new ArrayList<Integer>();
				}
				//how many ms passed between 1M and 2M instructions?
				//measure this for timekeeping and to the calibrate audio playback rate
				//note: if the user hits the pause button between 1M and 2M insts, this will be inaccurate and sound won't play right
				if(processor.instructionCount==(131072*20))
				{
					time0=System.currentTimeMillis();
					inst0=processor.instructionCount;
				}
				if(recalculateIPS && processor.instructionCount==131072*40)
				{
					long dtime=System.currentTimeMillis()-time0;
					long dinst=processor.instructionCount-inst0;
					INSTRUCTIONS_PER_SECOND=((double)dinst)/dtime*1000;
					System.out.println(INSTRUCTIONS_PER_SECOND);
				}
			}

			//update screen a bit faster if we're debugging
			if(processor.doprint||spc700.doprint)
			{
				if(processor.instructionCount%1000==0)
				{
					snesgui.statusUpdate();
				}
			}
			//run the audio processor every N instructions
//			if (apuEnabled && processor.instructionCount%APU_INSTRUCTIONS_PER_CPU_INSTRUCTION==0)
			if (apuEnabled && apucountdown++==APU_INSTRUCTIONS_PER_CPU_INSTRUCTION)
			{
				spc700.runOnce();
				apucountdown=0;
			}
			
			//this is how far we should advance video state
			if(!cycleAccurate)
			{
				processor.instructionsUntilEvent+=CYCLES_PER_INSTRUCTION;
				instructionsSinceHCOUNTER+=CYCLES_PER_INSTRUCTION;	
			}						
			
			//if pause was pressed, wait here until resumed
			pauselock.testlock();
			
			//move the video state along
			if(!cycleAccurate)
			{
				while (processor.instructionsUntilEvent>=nextEvent)
				{
					handleEvent();
	//				processor.instructionsUntilEvent=0;
					processor.instructionsUntilEvent-=nextEvent;
				}
			}
			else
			{
//System.out.println(eventCycles);
				while(eventCycles>=nextEvent)
					handleEvent();
			}
		}
	}
	
	public void docycles(int count)
	{
		lastEventCycles=eventCycles;
		eventCycles+=count;
		processor.cycleCount+=count;
		checkForInterrupt();
	}
	
	public void checkForInterrupt()
	{
		if(!IRQEnabled) return;
		
		boolean thisIRQ=ppu.VInterruptsEnabled || ppu.HInterruptsEnabled;
		//if we reached the trigger line on the last inst and IRQs are enabled in the PPU, we might throw an IRQ on the next instruction
		if(IRQLine && thisIRQ)
			interruptPending=true;
		if(ppu.HInterruptsEnabled)
		{
			int t=ppu.IRQHBeamPosition;
			if (cycleAccurate)
			{
				if (eventCycles>=PPU.HMAX)
					t+=PPU.HMAX;
				if(lastEventCycles>=t || eventCycles<t)
					thisIRQ=false;
			}
			else
			{
				if (instructionsSinceHCOUNTER>=PPU.HMAX)
					t+=PPU.HMAX;
				if(instructionsSinceHCOUNTER+CYCLES_PER_INSTRUCTION>=t || instructionsSinceHCOUNTER<t)
					thisIRQ=false;				
			}
		}
		//but only throw it if VCounter was incremented in the last instruction and passed the trigger line
		if(ppu.VInterruptsEnabled)
		{
			int vc=ppu.VCounter;
			if(!cycleAccurate && instructionsSinceHCOUNTER>=PPU.HMAX)
				vc++;
			else if(cycleAccurate && eventCycles>=PPU.HMAX)
				vc++;
			if(vc!=ppu.IRQVBeamPosition)
				thisIRQ=false;
		}
		if (thisIRQ)
			IRQLine=true;
	}
	
	//handle video events.  when the cycle counter reaches certain numbers, handle HBLANK/VBLANK, render frames, etc.
	//these numbers were taken from SNES9x
	public void handleEvent()
	{
		switch(eventType)
		{
		//reached the horizontal blank period at the end of a line
		//don't do anything yet, however
		case HBLANK_EVENT:
			nextEvent=CYCLES_UNTIL_HDMA_START;
			eventType=HDMA_START_EVENT;
			break;
		case HDMA_START_EVENT:
			nextEvent=CYCLES_UNTIL_HCOUNTER;
			eventType=HCOUNTER_EVENT;
			//do the memory transfers
			for(int d=0; d<8; d++)
				dma[d].doHDMA();
			break;
		//reached the end of a line
		//update VCounter, render the line, maybe throw a NMI too
		case HCOUNTER_EVENT:
			if(cycleAccurate)
			{
				eventCycles-=PPU.HMAX;
				lastEventCycles-=PPU.HMAX;
			}
			else
			{
				instructionsSinceHCOUNTER=0;
			}
			//keep track of whether we've reached enough lines for an NMI
			if (processor.NMItriggerPosition!=0xffff && processor.NMItriggerPosition>=PPU.HMAX)
				processor.NMItriggerPosition-=PPU.HMAX;
			//update VCounter
			ppu.VCounter++;
			//bottom of screen? start again from the top and draw the frame
			if (ppu.VCounter>=PPU.VMAX)
			{
				ppu.VCounter=0;
				memory.physicalMemory[0x4210]=(byte)0x02;
				processor.NMItrigger=false;
				processor.NMItriggerPosition=0xffff;
			}
			//bottom of visible screen? update display
			if (ppu.VCounter==PPU.SNES_HEIGHT+PPU.FIRST_VISIBLE_LINE)
			{
				if(!skipframe)
					ppu.endScreenRefresh();
				//update the screen
				frameCount++;
				skipframe=(frameCount%(FRAME_SKIP+1)>0);
				//delay if we're running too fast
				long current=System.currentTimeMillis();
				if(current-frametime<1000.0/MAX_FPS)
				{
					try{Thread.sleep((int)(1000.0/MAX_FPS-(current-frametime)));} catch(InterruptedException e){}
				}
				frametime=current;
				//pause if we're single stepping on frames
				if(singlestepframe)
				{
					singlestepframe=false;
					pauselock.lock();
				}

				ppu.displayBlanked=((memory.physicalMemory[0x2100]>>7)&1)!=0;
				memory.physicalMemory[0x4210]=(byte)0x82;
				//throw an NMI if we've reached the trigger
				if((memory.physicalMemory[0x4200]&0x80)!=0)
				{
					processor.NMItrigger=true;
					processor.NMItriggerPosition=12;
				}
					
			}
			//top of the screen?  prepare for drawing the next frame
			if (ppu.VCounter==PPU.FIRST_VISIBLE_LINE)
			{
				if(!skipframe)
					ppu.startScreenRefresh();
			}
			
			nextEvent=CYCLES_UNTIL_HDMA_INIT;
			eventType=HDMA_INIT_EVENT;
			break;
		//if a HDMA automatic memory-port transfer is set up, we can do it now
		case HDMA_INIT_EVENT:
			nextEvent=CYCLES_UNTIL_RENDER;
			eventType=RENDER_EVENT;
			//if HDMAs are requested, they start running on the next frame
			if(ppu.VCounter==0)
			{
				for (int d=0; d<8; d++)
					dma[d].startHDMA();
			}
			break;
		//end of line?  draw the line
		case RENDER_EVENT:
			if (ppu.VCounter>=PPU.FIRST_VISIBLE_LINE && ppu.VCounter<=PPU.SNES_HEIGHT)
			{
				if(!skipframe)
					ppu.renderLine((ppu.VCounter-PPU.FIRST_VISIBLE_LINE)%PPU.SNES_HEIGHT);
			}
			nextEvent=CYCLES_UNTIL_WRAM_REFRESH;
			eventType=WRAM_REFRESH_EVENT;
			break;
		case WRAM_REFRESH_EVENT:
			if(cycleAccurate)
			{		
				lastEventCycles=eventCycles;
				eventCycles+=WRAM_REFRESH_CYCLES;
			
				checkForInterrupt();
			}
			nextEvent=CYCLES_UNTIL_HBLANK;
			eventType=HBLANK_EVENT;
			break;
		
		}
	}
	
	//if running as a regular application, create a SNES object and load the game
	public static void main(String[] args) 
	{
		String name="";
		if(args.length>0)
		{
			System.out.println("Loading "+args[0]);
			name=args[0];
		}
		final SNES snes = new SNES();
		if(!name.equals(""))
		{
			snes.gamename=name;
			snes.sramname=snes.gamename.substring(0,snes.gamename.length()-3)+"srm";
			snes.snesgui.buttonComponent.pause.setEnabled(true);
			snes.screen.requestFocus();
			
			snes.dogameboy=false;
			snes.dones=false;
			if (snes.gamename.substring(snes.gamename.length()-3,snes.gamename.length()).toLowerCase().equals("spc"))
			{
				if(snes.spc700.audiothread)
				{
					snes.spc700.audiothread=false;
					try{Thread.sleep(1000);}catch(Exception exc){}
				}
				
				snes.processor.reset();
				snes.ppu.ppureset();
				snes.spc700.reset();
				snes.dsp.loadSPCFromFile(snes.gamename);
				snes.spc700.audiothread=true;
				new Thread(snes.spc700).start();						
			}
			else if(snes.gamename.substring(snes.gamename.length()-2,snes.gamename.length()).toLowerCase().equals("gb"))
			{
				snes.spc700.audiothread=false;
				snes.gameboy.loadCartridge(snes.gamename);
				snes.dogameboy=true;
				new Thread(new Runnable(){public void run(){
					snes.gameboy.mainLoop();
				}}).start();						
			}
			else if(snes.gamename.substring(snes.gamename.length()-3,snes.gamename.length()).toLowerCase().equals("nes"))
			{
				snes.spc700.audiothread=false;
				snes.nes.loadCartridge(snes.gamename);
				snes.dones=true;
				new Thread(new Runnable(){public void run(){
					snes.nes.mainLoop();
				}}).start();						
			}
			else
			{
				snes.spc700.audiothread=false;
				snes.initializeSNES();
				new Thread(new Runnable(){public void run(){
					snes.mainLoop();
				}}).start();						
			}
		}
	}

	//save a snapshot of javasnes's state as a single big string
	public String dumpSNESState()
	{		
		String ret="snes2\n";
		ret+=String.format("%d %d %d ",eventType,nextEvent,instructionsSinceHCOUNTER);
		ret+=frameCount+" "+INSTRUCTIONS_PER_SECOND+" "+CYCLES_PER_INSTRUCTION+" "+APU_INSTRUCTIONS_PER_CPU_INSTRUCTION+" "+FRAME_SKIP+" "+gamename+" "+sramname+" ";
		ret+=(skipframe?"1":"0")+" "+(interruptPending?"1":"0")+" "+(IRQLine?"1":"0")+" "+(IRQEnabled?"1":"0")+" ";
		ret+=(cycleAccurate?"1":"0")+" "+(multithreaded?"1":"0")+" "+(apuEnabled?"1":"0")+" "+(ischrono?"1":"0")+" "+(debugMode?"1":"0")+" "+(mute?"1":"0")+" ";
		ret+="\n";
		return ret;
	}
	//load a snapshot as javasnes's state
	public void loadSNESState(String state)
	{
		Scanner s=new Scanner(state);
		eventType=s.nextInt(); nextEvent=s.nextInt(); instructionsSinceHCOUNTER=s.nextInt();
		if(savestateversion<2) return;
		frameCount=s.nextLong(); INSTRUCTIONS_PER_SECOND=s.nextDouble(); CYCLES_PER_INSTRUCTION=s.nextInt(); APU_INSTRUCTIONS_PER_CPU_INSTRUCTION=s.nextInt(); FRAME_SKIP=s.nextInt(); gamename=s.next(); sramname=s.next();
		skipframe=s.nextInt()==1; interruptPending=s.nextInt()==1; IRQLine=s.nextInt()==1; IRQEnabled=s.nextInt()==1;
		cycleAccurate=s.nextInt()==1; multithreaded=s.nextInt()==1; apuEnabled=s.nextInt()==1; ischrono=s.nextInt()==1; debugMode=s.nextInt()==1; mute=s.nextInt()==1;
	}
	
	int savestateversion;
	public void loadState(String state)
	{	
		String[] states=state.split("\n");
		if(states[0].equals("snes")) savestateversion=1;
		else if(states[0].equals("snes2")) savestateversion=2;
		else { System.out.println("Couldn't load snes state\n"); return; }
		this.loadSNESState(states[1]);
		if(!states[2].equals("processor")) { System.out.println("Couldn't load processor state\n"); return; }
		processor.loadProcessorState(states[3]);
		if(!states[4].equals("memory")) { System.out.println("Couldn't load memory state\n"); return; }
		memory.loadMemoryState(states[5]);
		if(!states[6].equals("ppu")) { System.out.println("Couldn't load ppu state\n"); return; }
		ppu.loadPPUState(states[7]);
		for (int i=0; i<8; i++)
			dma[i].loadDMAState(states[9+i*2]);
		if(savestateversion>=2)
		{
			if(!states[24].equals("SPC700")) { System.out.println("Couldn't load spc700 state\n"); return; }
			spc700.loadSPC700State(states[25]);
			if(!states[26].equals("dsp")) { System.out.println("Couldn't load dsp state\n"); return; }
			dsp.loadDSPState(states[27]);
			if(!states[28].equals("done")) { System.out.println("Couldn't find done at end of file"); return; }
		}
		video.updateWholeScreen();
	}

	public String dumpState()
	{
		String ret="";
		ret+=this.dumpSNESState();
		ret+=processor.dumpProcessorState();
		ret+=memory.dumpMemoryState();
		ret+=ppu.dumpPPUState();
		for (int i=0; i<8; i++)
			ret+=dma[i].dumpDMAState();
		ret+=spc700.dumpSPC700State();
		ret+=dsp.dumpDSPState();
		ret+="done\n";
		return ret;
	}
	
	public String dumpGameboyState()
	{
		String ret="gb\n";
		ret+=gamename+" "+sramname+" ";
		ret+=(cycleAccurate?"1":"0")+" "+(multithreaded?"1":"0")+" "+(debugMode?"1":"0")+" "+(mute?"1":"0")+" ";
		ret+="\n";
		ret+=gameboy.z80.dumpProcessorState();
		ret+=gameboy.dumpGameboyState();
		return ret;
	}
	
	public void loadGameboyState(String state)
	{
		String[] states=state.split("\n");
		if(states[0].equals("gb")) savestateversion=1;
		else { System.out.println("Couldn't load gb state\n"); return; }

		Scanner s=new Scanner(states[1]);
		gamename=s.next(); sramname=s.next();
		cycleAccurate=s.nextInt()==1; multithreaded=s.nextInt()==1; debugMode=s.nextInt()==1; mute=s.nextInt()==1;
		
		if(!states[2].equals("z80")) { System.out.println("Couldn't load z80 state\n"); return; }
		gameboy.z80.loadProcessorState(states[3]);
		if(!states[4].equals("gameboy")) { System.out.println("Couldn't load gameboy state\n"); return; }
		gameboy.loadGameboyState(states[5]);		
	}
	
	public void dumpStateToFile(String filename)
	{
		try
		{
			PrintWriter f=new PrintWriter(filename);
			System.out.println("Preparing to save state to "+filename);
			String s;
			if(!dogameboy)
				s=dumpState();
			else
				s=dumpGameboyState();
			f.println(s);
			f.close();
			System.out.println("State saved to "+filename);
		}
		catch(IOException e)
		{
			System.out.println("Couldn't save");
		}
	}
	
	public void loadStateFromFile(String filename)
	{
		try
		{
			File f=new File(filename);
			StringBuilder s=new StringBuilder();
			Scanner sc=new Scanner(f);
			System.out.println("Preparing to load state from "+filename);
			while(sc.hasNextLine())
			{
				s.append(sc.nextLine());
				s.append("\n");
			}
			String state=s.toString();
			if(!dogameboy)
				loadState(state);
			else
				loadGameboyState(state);
			System.out.println("Loaded state from "+filename);
		}
		catch(FileNotFoundException e){}
	}
	
	//save the SRAM to a file
	//call this just before exiting
	public void saveSRAM()
	{
		if(sramname.equals("")) return;
		try
		{
			BufferedOutputStream out=new BufferedOutputStream(new FileOutputStream(sramname));
			for(int address=0x306000; address<=0x307fff; address++)
				out.write(memory.physicalMemory[address]);
			out.close();
			System.out.println("Savefile saved to "+sramname);
		}
		catch(IOException e)
		{
			System.out.println("Couldn't save sram");
		}
	}

}
