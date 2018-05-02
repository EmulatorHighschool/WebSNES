package snes;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

public class DSP
{
	public static final int SAMPLESIZE=32000;
	public static final int SAMPLES_PER_SECOND=32000;
	
	public static final int MAX_SOUND_BUFFER_SIZE=1024*256;

	public boolean applyfilter=true;
	public static final double PITCH_REDUCTION_FACTOR=1.0;
	
	public static final int ENVX_PRECISION=11;
	public static final int ENVX_DOWNSHIFT_BITS=ENVX_PRECISION-4;
	public static final int ENVX_MAX_BASE=1<<ENVX_PRECISION;
	public static final int ENVX_MAX=ENVX_MAX_BASE-1;

	public double SPEED_FACTOR=3.5;
	
	enum ENVELOPE_STATE {ATTACK,DECAY,SUSTAIN,RELEASE,DECREASE,EXP,INCREASE,BENT,DIRECT,VOICE_OFF};
	
	public SNES snes;

	public Voice[] voice;
	int mainVolumeLeft,mainVolumeRight,echoVolumeLeft,echoVolumeRight,keyOn,keyOff,flag,endx;
	int echoFeedback,pitchModulation,noiseOn,echoOn,sourceDirectory,echoStartAddress,echoDelay;
	int[] filterCoefficient;
	Lock pauselock;
	long lastCount;
	
	ArrayList<Integer> rawsound;
	boolean recording;
	ArrayList<Byte> pitches;
	
	SoundPlayer soundplayer;
	
	public DSP(SNES snes)
	{
		this.snes=snes;
		voice=new Voice[8];
		for (int i=0; i<8; i++)
			voice[i]=new Voice(i);
		filterCoefficient=new int[8];
		pauselock=new Lock();
		soundplayer=new SoundPlayer(snes,16);
	}
	
	public void reset()
	{
		for(int i=0; i<8; i++)
			voice[i].reset();
		for(int i=0; i<8; i++)
			filterCoefficient[i]=0;
		mainVolumeLeft=0;mainVolumeRight=0;echoVolumeLeft=0;echoVolumeRight=0;keyOn=0;keyOff=0;flag=0;endx=0;
		echoFeedback=0;pitchModulation=0;noiseOn=0;echoOn=0;sourceDirectory=0;echoStartAddress=0;echoDelay=0;
		
		flag=0x60;
		lastCount=snes.processor.instructionCount;
//		lastCount=System.currentTimeMillis();
//		new Thread(this).start();
		rawsound=new ArrayList<Integer>();
		pitches=new ArrayList<Byte>();
	}
	

	public byte read(int registernumber)
	{
		update();
		
		if((registernumber&0xf)<=9)
			return voice[(registernumber>>4)&7].read(registernumber&0xf);
		else if ((registernumber&0xf)==0xf)
			return (byte)filterCoefficient[(registernumber>>4)&7];
		else
		{
			switch(registernumber)
			{
			case 0x0c: return (byte)mainVolumeLeft;
			case 0x1c: return (byte)mainVolumeRight;
			case 0x2c: return (byte)echoVolumeLeft;
			case 0x3c: return (byte)echoVolumeRight;
			case 0x4c: return (byte)keyOn;
			case 0x5c: return (byte)keyOff;
			case 0x6c: return (byte)flag;
			case 0x7c: return (byte)endx;
			case 0x0d: return (byte)echoFeedback;
			case 0x2d: return (byte)pitchModulation;
			case 0x3d: return (byte)noiseOn;
			case 0x4d: return (byte)echoOn;
			case 0x5d: return (byte)sourceDirectory;
			case 0x6d: return (byte)echoStartAddress;
			case 0x7d: return (byte)echoDelay;
			}
		}
		
		return 0;
	}
	
	public void write(int registernumber, byte b)
	{
		update();
		
		if((registernumber&0xf)<=9)
			voice[(registernumber>>4)&7].write(registernumber&0xf,b);
		else if ((registernumber&0xf)==0xf)
			filterCoefficient[(registernumber>>4)&7]=b&0xff;
		else
		{
			switch(registernumber)
			{
			case 0x0c: mainVolumeLeft=b&0xff; break;
			case 0x1c: mainVolumeRight=b&0xff; break;
			case 0x2c: echoVolumeLeft=b&0xff; break;
			case 0x3c: echoVolumeRight=b&0xff; break;
			case 0x4c: keyOn=b&0xff; break;
			case 0x5c: keyOff=b&0xff; break;
			case 0x6c: flag=b&0xff; break;
			case 0x7c: endx=b&0xff; break;
			case 0x0d: echoFeedback=b&0xff; break;
			case 0x2d: pitchModulation=b&0xff; break;
			case 0x3d: noiseOn=b&0xff;
				if(snes.debugMode) System.out.println(noiseOn);
				break;
			case 0x4d: echoOn=b&0xff; break;
			case 0x5d: sourceDirectory=b&0xff; break;
			case 0x6d: echoStartAddress=b&0xff; break;
			case 0x7d: echoDelay=b&0xff; break;
			}
		}
	}
		
	public void off()
	{
		for(int i=0; i<8; i++)
			this.voice[i].off();
	}
	
	public void keyon()
	{
		int voices=keyOn;
		keyOn&=keyOff;
		voices&=~keyOff;
		for(int v=0; v<8; v++)
		{
			if ((voices&(1<<v))==0) continue;
			//TODO 8 sample delay
			endx&=~(1<<v);
			voice[v].keyon();
		}
	}
	
	//called on all reads and writes to synchronize with spc700
	public void update()
	{
//		long f=snes.frameCount;
		long inst=snes.processor.instructionCount;

//		long t=System.currentTimeMillis();
//		int samples=(int)(t-lastCount)*SAMPLES_PER_SECOND/1000;
		int samples=(int)((inst-lastCount)*SAMPLES_PER_SECOND/snes.INSTRUCTIONS_PER_SECOND*SPEED_FACTOR);
		if(samples<=0) return;
		lastCount=inst;
//		lastCount=System.currentTimeMillis();
	
		if((flag&0x80)>0)	//reset
		{
			off();
			endx=keyOn=keyOff=0;
		}
		keyon();
		
		//mix
		for(int s=0; s<samples; s++)
		{
			int output=0;
			for(int v=0; v<8; v++)
			{
				if(!voice[v].enabled) continue;
				voice[v].getNextBRRBlock();
				int d=(voice[v].pitchcounter>>4)&0xff;
				voice[v].outx=(gauss[255-d]*voice[v].buf[(voice[v].bufpointer-3)&3])>>11;
				voice[v].outx+=(gauss[511-d]*voice[v].buf[(voice[v].bufpointer-2)&3])>>11;
				voice[v].outx+=(gauss[256+d]*voice[v].buf[(voice[v].bufpointer-1)&3])>>11;
				voice[v].outx=((voice[v].outx&0x7fff)^0x4000)-0x4000;
				voice[v].outx+=(gauss[d]*voice[v].buf[(voice[v].bufpointer&3)])>>11;
				voice[v].updatePitch();
				voice[v].outx=(voice[v].outx*voice[v].getEnvelope())>>ENVX_PRECISION;
				
				int vol=(voice[v].volumeLeft+voice[v].volumeRight)>>1;
				voice[v].outx=(voice[v].outx*vol)>>8;

				if(voice[v].outx<-32768) voice[v].outx=-32768;
				if(voice[v].outx>32767) voice[v].outx=32767;
				
				voice[v].outstanding_samples++;
			}
			for(int v=7; v>=0; v--)
				if (voice[v].enabled)
					output=mix(voice[v].outx,output,32767);
			if(rawsound.size()<MAX_SOUND_BUFFER_SIZE)
				rawsound.add(output);
		}
	}
	public int mix(int s1, int s2, int max)
	{
		if(s1>0 && s2>0)
			return s1+s2-s1*s2/max;
		if(s1<0 && s2<0)
			return s1+s2-s1*s2/(-max);
		return s1+s2;
	}
	
	public class Voice
	{
		int volumeLeft;
		int volumeRight;
		int pitchLow;
		int pitchHigh;
		int sourceNumber;
		int adsr1,adsr2;
		int gain;
		int envx,outx;
		int voicenumber;
		boolean enabled;
		boolean forceDisable;
		int last1;
		int last2;
		int pitchcounter;
		int samplesleft;
		int brrheader;
		int brrpointer;
		int[] buf=new int[4];
		int bufpointer;		
		long lasttime;

		int envelope_update_count;
		int gain_update_count;
		ENVELOPE_STATE envelope_state, adsr_state;
		int envelope_counter;
		int counter_reset_value=0x7800;
		int ar,dr,sl,sr;
		int outstanding_samples;
		
		public Voice(int v)
		{
			voicenumber=v;
			forceDisable=false;
			lasttime=System.currentTimeMillis();
		}
		
		public void reset()
		{
			volumeLeft=volumeRight=pitchLow=pitchHigh=sourceNumber=adsr1=adsr2=gain=envx=outx=0;
			last1=last2=0;
			envelope_state=adsr_state=ENVELOPE_STATE.VOICE_OFF;
			outstanding_samples=0;
			ar=attack_time(0);
			dr=decay_time(0);
			sr=exp_time(0);
			sl=ENVX_MAX_BASE/8;
			gain_update_count=counter_reset_value;
			enabled=false;
		}
		public void write(int registernumber,byte b)
		{
			switch(registernumber)
			{
			case 0: volumeLeft=b&0xff; break;
			case 1: volumeRight=b&0xff; break;
			case 2: pitchLow=b&0xff; 
			{
/*				if (voicenumber==0)
				{
					double pitch=pitchHigh*256+pitchLow;
//					double pitch=pitchLow;
					pitch=440.0*(pitch/4096);
//					pitch=32000*(pitch/4096);
					double duration=(System.currentTimeMillis()-lasttime)/1000.;
					lasttime=System.currentTimeMillis();
//					System.out.println(pitch+" "+duration);
					if (duration>2) duration=2;
					addtonetopitches(pitch,duration);
				}*/
			}
			
			break;
			case 3: pitchHigh=b&0xff; break;
			case 4: sourceNumber=b&0xff; break;
			case 5:
			{
				int oldadsr1=adsr1;
				adsr1=b&0xff;
				if(enabled)
					getEnvelope();
				ar=attack_time(adsr1&0xf);
				dr=decay_time((adsr1>>4)&7);
				if(!enabled || envelope_state==ENVELOPE_STATE.RELEASE)
					break;
				if(envelope_state==ENVELOPE_STATE.ATTACK)
					envelope_update_count=ar;
				else if(envelope_state==ENVELOPE_STATE.DECAY)
					envelope_update_count=dr;
				if((adsr1&0x80)!=0)
				{
					if((oldadsr1&0x80)==0)
					{
						envelope_state=adsr_state;
						switch(envelope_state)
						{
						case ATTACK: envelope_update_count=ar; break;
						case DECAY: envelope_update_count=dr; break;
						case SUSTAIN: envelope_update_count=sr; break;
						default:break;
						}
					}
				}
				else
				{
					envelope_update_count=gain_update_count;
					if((gain&0x80)!=0)
						envelope_state=new ENVELOPE_STATE[]{ENVELOPE_STATE.DECREASE,ENVELOPE_STATE.EXP,ENVELOPE_STATE.INCREASE,ENVELOPE_STATE.BENT}[(gain>>5)&3];
					else
						envelope_state=ENVELOPE_STATE.DIRECT;
				}
				break;
			}
			case 6: 
				adsr2=b&0xff; 
				if(enabled)
					getEnvelope();
				sr=exp_time(adsr2&0x1f);
				sl=((adsr2>>5)==7)?ENVX_MAX:(ENVX_MAX_BASE/8)*((adsr2>>5)+1);
				if(envelope_state==ENVELOPE_STATE.SUSTAIN)
					envelope_update_count=sr;
				break;
			case 7:
			{
				int oldgain=gain;
				gain=b&0xff;
				if(enabled)
					getEnvelope();
				if((gain&0x80)!=0)
				{
					switch(new ENVELOPE_STATE[]{ENVELOPE_STATE.DECREASE,ENVELOPE_STATE.EXP,ENVELOPE_STATE.INCREASE,ENVELOPE_STATE.BENT}[(gain>>5)&3])
					{
					case INCREASE: case DECREASE:
						gain_update_count=linear_time(gain&0x1f);
						break;
					case BENT:
						gain_update_count=bent_time(gain&0x1f);
						break;
					case EXP:
						gain_update_count=exp_time(gain&0x1f);
						break;
					default: break;
					}
				}
				else
					gain_update_count=counter_reset_value;

				if(!enabled || envelope_state==ENVELOPE_STATE.RELEASE)
					break;
				
				if((oldgain&0x80)==0)
				{
					envelope_update_count=gain_update_count;
					if((gain&0x80)!=0)
						envelope_state=new ENVELOPE_STATE[]{ENVELOPE_STATE.DECREASE,ENVELOPE_STATE.EXP,ENVELOPE_STATE.INCREASE,ENVELOPE_STATE.BENT}[(gain>>5)&3];
					else
						envelope_state=ENVELOPE_STATE.DIRECT;						
				}
				break;
			}
			}
		}
		public byte read(int registernumber)
		{
			switch(registernumber)
			{
			case 0: return (byte)volumeLeft;
			case 1: return (byte)volumeRight;
			case 2: return (byte)pitchLow;
			case 3: return (byte)pitchHigh;
			case 4: return (byte)sourceNumber;
			case 5: return (byte)adsr1;
			case 6: return (byte)adsr2;
			case 7: return (byte)gain;
			case 8: return (byte)envx;
			case 9: return (byte)outx;
			}
			return 0;
		}
		
		public void off()
		{
			enabled=false;
			outx=0;
			envx=0;
			envelope_state=ENVELOPE_STATE.RELEASE;
			envelope_update_count=counter_reset_value;
		}
		public void keyon()
		{
			int directoryAddress=sourceDirectory*0x100+sourceNumber*4;
			brrpointer=(snes.spc700.memory[directoryAddress]&0xff);
			brrpointer|=((snes.spc700.memory[directoryAddress+1]&0xff)<<8);
			pitchcounter=0x3000;
			bufpointer=-1;
			samplesleft=0;
			brrheader=0;
			envx=0;
			outx=0;
			outstanding_samples=0;
			envelope_counter=counter_reset_value;
			adsr_state=ENVELOPE_STATE.ATTACK;
			if((adsr1&0x80)>0)
			{
				envelope_state=adsr_state;
				envelope_update_count=ar;
			}
			else
			{
				envelope_update_count=gain_update_count;
				if((gain&0x80)>0)
					envelope_state=new ENVELOPE_STATE[]{ENVELOPE_STATE.DECREASE,ENVELOPE_STATE.EXP,ENVELOPE_STATE.INCREASE,ENVELOPE_STATE.BENT}[(gain>>5)&3];
				else
					envelope_state=ENVELOPE_STATE.DIRECT;
			}
			
			if(!forceDisable)
				enabled=true;
		}
		
		
		public int getNextBRRBlock()
		{
			int output=0;
			while(pitchcounter>=0)
			{
				if(samplesleft==0)
				{
					if ((brrheader&1)!=0)	//END
					{
						if ((brrheader&2)!=0)	//Loop
						{
							int directoryAddress=sourceDirectory*0x100+sourceNumber*4;
							brrpointer=(snes.spc700.memory[directoryAddress+2]&0xff);
							brrpointer|=((snes.spc700.memory[directoryAddress+3]&0xff)<<8);
						}
						else
							off();
					}
					brrpointer&=0xffff;
					brrheader=snes.spc700.memory[brrpointer];
					brrpointer++;
					if((brrheader&1)!=0)	//END packet
							endx|=(1<<voicenumber);
					samplesleft=16;
				}
				int range=(brrheader>>4)&0xf;
				int filter=(brrheader>>2)&3;
				int input;
				brrpointer&=0xffff;
				if((samplesleft--&1)==0)
					input=(snes.spc700.memory[brrpointer]>>4)&0xf;
				else
					input=snes.spc700.memory[brrpointer++]&0xf;
				output=(input^8)-8;
				if(range<=12)
					output=(output<<range)>>1;
				else
					output&=~0x7ff;

				if(!applyfilter)
					filter=0;
				switch(filter)
				{
				case 0: break;
				case 1:
					output+=(last1>>1)+((-last1)>>5);
					break;
				case 2:
					output+=last1+((-(last1+(last1>>1)))>>5)+(-last2>>1)+(last2>>5);
					break;
				case 3:
					output+=last1+((-(last1+(last1<<2)+(last1<<3)))>>7)+(-last2>>1)+((last2+(last2>>1))>>4);
					break;
				}
				if(output>0x7fff) output=0x7fff;
				if(output<-0x8000) output=-0x8000;
				last2=last1;
				bufpointer=(bufpointer+1)&3;
				last1=buf[bufpointer]=(short)(output<<1);
				pitchcounter-=0x1000;
			}
			return output;
		}
		
		public void updatePitch()
		{
			int pitch=(pitchLow+pitchHigh*256)&0xffff;
//			int pitch=pitchLow;
			pitch/=PITCH_REDUCTION_FACTOR;

			if((pitchModulation&(1<<voicenumber))>0 && voicenumber>0)
				pitchcounter+=pitch*(voice[voicenumber-1].outx+32768)/32768;
			else
				pitchcounter+=pitch;
//if(voicenumber==0) System.out.println(pitch+" "+pitchcounter);
		}
		
		
		private int attack_time(int x){return counter_update_table[x*2+1];}
		private int decay_time(int x){return counter_update_table[x*2+16];}
		private int linear_time(int x){return counter_update_table[x];}
		private int exp_time(int x){return counter_update_table[x];}
		private int bent_time(int x){return counter_update_table[x];}

		public int getEnvelope()
		{
			int samples;
			while((samples=outstanding_samples)!=0)
			{
				if(envelope_update_count==0)
				{
					outstanding_samples=0;
					break;
				}
				for(;samples>0 && envelope_counter>0; samples--,outstanding_samples--,envelope_counter-=envelope_update_count);

				if(envelope_counter<=0)
					{
						envelope_counter=counter_reset_value;
						switch(envelope_state)
						{
						case ATTACK:
							if(envelope_update_count==attack_time(15))
								envx+=ENVX_MAX_BASE/2;
							else
								envx+=ENVX_MAX_BASE/64;
							if(envx>=ENVX_MAX)
							{
								envx=ENVX_MAX;
								envelope_state=adsr_state=ENVELOPE_STATE.DECAY;
								envelope_update_count=dr;
							}
							continue;
						case DECAY:
							envx-=(((int)envx-1)>>8)+1;
							if(envx<=sl || envx>ENVX_MAX)
							{
								envelope_state=adsr_state=ENVELOPE_STATE.SUSTAIN;
								envelope_update_count=sr;
							}
							continue;
						case SUSTAIN:
							envx-=(((int)envx-1)>>8)+1;
							continue;
						case RELEASE:
							envx-=ENVX_MAX_BASE>>256;
							if(envx==0||envx>ENVX_MAX)
							{
								envx=0;
								off();
								break;
							}
							continue;
						case INCREASE:
							envx+=ENVX_MAX_BASE>>6;
							if(envx>=ENVX_MAX)
							{
								outstanding_samples=0;
								envx=ENVX_MAX;
								break;
							}
							continue;
						case DECREASE:
							envx-=ENVX_MAX_BASE>>6;
							if(envx==0 || envx>ENVX_MAX)
							{
								outstanding_samples=0;
								envx=0;
								break;
							}
							continue;
						case EXP:
							envx-=(((int)envx-1)>>8)+1;
							if(envx==0 || envx>ENVX_MAX)
							{
								outstanding_samples=0;
								envx=0;
								break;
							}
							continue;
						case BENT:
							if(envx<(ENVX_MAX_BASE/4*3))
								envx+=ENVX_MAX_BASE/64;
							else
								envx+=ENVX_MAX_BASE/256;
							if(envx>=ENVX_MAX)
							{
								outstanding_samples=0;
								envx=ENVX_MAX;
								break;
							}
							continue;
						case DIRECT:
							envx=(gain&0x7f)<<ENVX_DOWNSHIFT_BITS;
							outstanding_samples=0;
							break;
						case VOICE_OFF:
							outstanding_samples=0;
							break;
						}
					}
				break;				
			}
			return envx;
		}
	}
	
	public void generateSPCFile(String filename)
	{
		try
		{
		String s=generateSPC();
		BufferedOutputStream out=new BufferedOutputStream(new FileOutputStream(filename));
		for (int i=0; i<s.length(); i++)
			out.write((byte)s.charAt(i));
		out.close();
		System.out.println("Generated "+filename);
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}
	
	public String generateSPC()
	{
		String s="SNES-SPC700 Sound File Data v0.30";
		s+=(char)26;
		s+=(char)26;
		s+=(char)27;	//no tag
		s+=(char)30;	//v.30
		s+=(char)(snes.spc700.PC&0xff);		//SPC700 registers
		s+=(char)(snes.spc700.PC>>8);
		s+=(char)snes.spc700.A;
		s+=(char)snes.spc700.X;
		s+=(char)snes.spc700.Y;
		s+=(char)snes.spc700.P;
		s+=(char)snes.spc700.S;
		s+=(char)0;				//"reserved"
		s+=(char)0;
		//id666 tag - set to 0 for now
		for(int i=0; i<32+32+16+32+11+3+5+32+1+1+45; i++)
			s+=(char)0;
		//SPC RAM
		for(int i=0; i<65536; i++)
			s+=(char)snes.spc700.memory[i];
		//DSP regs
		for (int i=0; i<128; i++)
			s+=(char)read(i);
		//unused
		for (int i=0; i<128; i++)
			s+=(char)0;
		return s;
	}
	public void loadSPCFromFile(String filename)
	{
		byte[] rawdata;
		try 
		{
			File f=new File(filename);
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
		snes.spc700.PC=rawdata[0x25]&0xff;
		snes.spc700.PC|=(rawdata[0x26]&0xff)<<8;
		snes.spc700.A=rawdata[0x27]&0xff;
		snes.spc700.X=rawdata[0x28]&0xff;
		snes.spc700.Y=rawdata[0x29]&0xff;
		snes.spc700.P=rawdata[0x2a]&0xff;
		snes.spc700.S=rawdata[0x2b]&0xff;
		//SPC RAM
		for(int i=0; i<65536; i++)
			snes.spc700.memory[i]=rawdata[i+0x100];
		//DSP regs
		for (int i=0; i<128; i++)
			write(i,rawdata[0x10100+i]);
		snes.spc700.writeControlRegister(snes.spc700.memory[0xf1]);
		for (int address=0xfa; address<=0xfc; address++)
		{
			snes.spc700.updateTimer(address-0xfa);
			snes.spc700.timermax[address-0xfa]=snes.spc700.memory[address]&0xff;
			if(snes.spc700.timermax[address-0xfa]==0) snes.spc700.timermax[address-0xfa]=256;
			if(snes.spc700.timercount[address-0xfa]>snes.spc700.timermax[address-0xfa])
				snes.spc700.timercount[address-0xfa]-=256;
		}
	}

	public byte[] tone(double frequency, double duration, double phase)
	{
		byte[] data=new byte[(int)(SAMPLESIZE*duration)];
		int max=127;
		
		for (int t=0; t<data.length; t++)
		{
			data[t]=(byte)(max*Math.cos(phase+2*Math.PI*frequency*t/SAMPLESIZE));
		}
		
		
		return data;
	}
	public void addtonetopitches(double frequency, double duration)
	{
		byte[] data=tone(frequency,duration,0);
		for (int i=0; i<data.length; i++)
			pitches.add(data[i]);
	}
	
    static int[] gauss = new int[]{
	0x000, 0x000, 0x000, 0x000, 0x000, 0x000, 0x000, 0x000,
	0x000, 0x000, 0x000, 0x000, 0x000, 0x000, 0x000, 0x000,
	0x001, 0x001, 0x001, 0x001, 0x001, 0x001, 0x001, 0x001,
	0x001, 0x001, 0x001, 0x002, 0x002, 0x002, 0x002, 0x002,
	0x002, 0x002, 0x003, 0x003, 0x003, 0x003, 0x003, 0x004,
	0x004, 0x004, 0x004, 0x004, 0x005, 0x005, 0x005, 0x005,
	0x006, 0x006, 0x006, 0x006, 0x007, 0x007, 0x007, 0x008,
	0x008, 0x008, 0x009, 0x009, 0x009, 0x00A, 0x00A, 0x00A,
	0x00B, 0x00B, 0x00B, 0x00C, 0x00C, 0x00D, 0x00D, 0x00E,
	0x00E, 0x00F, 0x00F, 0x00F, 0x010, 0x010, 0x011, 0x011,
	0x012, 0x013, 0x013, 0x014, 0x014, 0x015, 0x015, 0x016,
	0x017, 0x017, 0x018, 0x018, 0x019, 0x01A, 0x01B, 0x01B,
	0x01C, 0x01D, 0x01D, 0x01E, 0x01F, 0x020, 0x020, 0x021,
	0x022, 0x023, 0x024, 0x024, 0x025, 0x026, 0x027, 0x028,
	0x029, 0x02A, 0x02B, 0x02C, 0x02D, 0x02E, 0x02F, 0x030,
	0x031, 0x032, 0x033, 0x034, 0x035, 0x036, 0x037, 0x038,
	0x03A, 0x03B, 0x03C, 0x03D, 0x03E, 0x040, 0x041, 0x042,
	0x043, 0x045, 0x046, 0x047, 0x049, 0x04A, 0x04C, 0x04D,
	0x04E, 0x050, 0x051, 0x053, 0x054, 0x056, 0x057, 0x059,
	0x05A, 0x05C, 0x05E, 0x05F, 0x061, 0x063, 0x064, 0x066,
	0x068, 0x06A, 0x06B, 0x06D, 0x06F, 0x071, 0x073, 0x075,
	0x076, 0x078, 0x07A, 0x07C, 0x07E, 0x080, 0x082, 0x084,
	0x086, 0x089, 0x08B, 0x08D, 0x08F, 0x091, 0x093, 0x096,
	0x098, 0x09A, 0x09C, 0x09F, 0x0A1, 0x0A3, 0x0A6, 0x0A8,
	0x0AB, 0x0AD, 0x0AF, 0x0B2, 0x0B4, 0x0B7, 0x0BA, 0x0BC,
	0x0BF, 0x0C1, 0x0C4, 0x0C7, 0x0C9, 0x0CC, 0x0CF, 0x0D2,
	0x0D4, 0x0D7, 0x0DA, 0x0DD, 0x0E0, 0x0E3, 0x0E6, 0x0E9,
	0x0EC, 0x0EF, 0x0F2, 0x0F5, 0x0F8, 0x0FB, 0x0FE, 0x101,
	0x104, 0x107, 0x10B, 0x10E, 0x111, 0x114, 0x118, 0x11B,
	0x11E, 0x122, 0x125, 0x129, 0x12C, 0x130, 0x133, 0x137,
	0x13A, 0x13E, 0x141, 0x145, 0x148, 0x14C, 0x150, 0x153,
	0x157, 0x15B, 0x15F, 0x162, 0x166, 0x16A, 0x16E, 0x172,
	0x176, 0x17A, 0x17D, 0x181, 0x185, 0x189, 0x18D, 0x191,
	0x195, 0x19A, 0x19E, 0x1A2, 0x1A6, 0x1AA, 0x1AE, 0x1B2,
	0x1B7, 0x1BB, 0x1BF, 0x1C3, 0x1C8, 0x1CC, 0x1D0, 0x1D5,
	0x1D9, 0x1DD, 0x1E2, 0x1E6, 0x1EB, 0x1EF, 0x1F3, 0x1F8,
	0x1FC, 0x201, 0x205, 0x20A, 0x20F, 0x213, 0x218, 0x21C,
	0x221, 0x226, 0x22A, 0x22F, 0x233, 0x238, 0x23D, 0x241,
	0x246, 0x24B, 0x250, 0x254, 0x259, 0x25E, 0x263, 0x267,
	0x26C, 0x271, 0x276, 0x27B, 0x280, 0x284, 0x289, 0x28E,
	0x293, 0x298, 0x29D, 0x2A2, 0x2A6, 0x2AB, 0x2B0, 0x2B5,
	0x2BA, 0x2BF, 0x2C4, 0x2C9, 0x2CE, 0x2D3, 0x2D8, 0x2DC,
	0x2E1, 0x2E6, 0x2EB, 0x2F0, 0x2F5, 0x2FA, 0x2FF, 0x304,
	0x309, 0x30E, 0x313, 0x318, 0x31D, 0x322, 0x326, 0x32B,
	0x330, 0x335, 0x33A, 0x33F, 0x344, 0x349, 0x34E, 0x353,
	0x357, 0x35C, 0x361, 0x366, 0x36B, 0x370, 0x374, 0x379,
	0x37E, 0x383, 0x388, 0x38C, 0x391, 0x396, 0x39B, 0x39F,
	0x3A4, 0x3A9, 0x3AD, 0x3B2, 0x3B7, 0x3BB, 0x3C0, 0x3C5,
	0x3C9, 0x3CE, 0x3D2, 0x3D7, 0x3DC, 0x3E0, 0x3E5, 0x3E9,
	0x3ED, 0x3F2, 0x3F6, 0x3FB, 0x3FF, 0x403, 0x408, 0x40C,
	0x410, 0x415, 0x419, 0x41D, 0x421, 0x425, 0x42A, 0x42E,
	0x432, 0x436, 0x43A, 0x43E, 0x442, 0x446, 0x44A, 0x44E,
	0x452, 0x455, 0x459, 0x45D, 0x461, 0x465, 0x468, 0x46C,
	0x470, 0x473, 0x477, 0x47A, 0x47E, 0x481, 0x485, 0x488,
	0x48C, 0x48F, 0x492, 0x496, 0x499, 0x49C, 0x49F, 0x4A2,
	0x4A6, 0x4A9, 0x4AC, 0x4AF, 0x4B2, 0x4B5, 0x4B7, 0x4BA,
	0x4BD, 0x4C0, 0x4C3, 0x4C5, 0x4C8, 0x4CB, 0x4CD, 0x4D0,
	0x4D2, 0x4D5, 0x4D7, 0x4D9, 0x4DC, 0x4DE, 0x4E0, 0x4E3,
	0x4E5, 0x4E7, 0x4E9, 0x4EB, 0x4ED, 0x4EF, 0x4F1, 0x4F3,
	0x4F5, 0x4F6, 0x4F8, 0x4FA, 0x4FB, 0x4FD, 0x4FF, 0x500,
	0x502, 0x503, 0x504, 0x506, 0x507, 0x508, 0x50A, 0x50B,
	0x50C, 0x50D, 0x50E, 0x50F, 0x510, 0x511, 0x511, 0x512,
	0x513, 0x514, 0x514, 0x515, 0x516, 0x516, 0x517, 0x517,
	0x517, 0x518, 0x518, 0x518, 0x518, 0x518, 0x519, 0x519
    };
    static int[] counter_update_table = new int[]
    	{
    	 0x0000, 0x000F, 0x0014, 0x0018, 0x001E, 0x0028, 0x0030, 0x003C,
    	 0x0050, 0x0060, 0x0078, 0x00A0, 0x00C0, 0x00F0, 0x0140, 0x0180,
    	 0x01E0, 0x0280, 0x0300, 0x03C0, 0x0500, 0x0600, 0x0780, 0x0A00,
    	 0x0C00, 0x0F00, 0x1400, 0x1800, 0x1E00, 0x2800, 0x3C00, 0x7800
    	};

	public void playpitches()
	{
		byte[] s=new byte[snes.dsp.pitches.size()];
		for (int i=0; i<snes.dsp.pitches.size(); i++)
		{
			s[i]=(byte)(snes.dsp.pitches.get(i));
		}
		AudioFormat af=new AudioFormat(SAMPLESIZE,8,1,true,false);
		AudioInputStream ais=new AudioInputStream(new ByteArrayInputStream(s),af,s.length);
		try {
			Clip clip=AudioSystem.getClip();
			clip.open(ais);
			clip.setFramePosition(0);
			clip.loop(0);
			clip.drain();
		} catch (Exception e) {
			e.printStackTrace();
		}				
	}

	public String dumpDSPState()
	{
		String ret="dsp\n";
		ret+=(applyfilter?"1":"0")+" "+SPEED_FACTOR+" "+mainVolumeLeft+" "+mainVolumeRight+" "+echoVolumeLeft+" "+echoVolumeRight+" "+keyOn+" "+keyOff+" "+flag+" "+endx+" "+echoFeedback+" "+pitchModulation+" "+noiseOn+" "+echoOn+" "+sourceDirectory+" "+echoStartAddress+" "+echoDelay+" "+lastCount+" "+(recording?"1":"0")+" ";
		for(int i=0; i<8; i++)
			ret+=filterCoefficient[i]+" ";
		for(int i=0; i<8; i++)
		{
			ret+=voice[i].volumeLeft+" "+voice[i].volumeRight+" "+voice[i].pitchLow+" "+voice[i].pitchHigh+" "+voice[i].sourceNumber+" "+voice[i].adsr1+" "+voice[i].adsr2+" "+voice[i].gain+" "+voice[i].envx+" "+voice[i].outx+" "+voice[i].voicenumber+" "+(voice[i].enabled?"1":"0")+" "+(voice[i].forceDisable?"1":"0")+" "+voice[i].last1+" "+voice[i].last2+" "+voice[i].pitchcounter+" "+voice[i].samplesleft+" "+voice[i].brrheader+" "+voice[i].brrpointer+" "+voice[i].bufpointer+" "+voice[i].lasttime+" "+voice[i].envelope_update_count+" "+voice[i].gain_update_count+" "+voice[i].envelope_counter+" "+voice[i].counter_reset_value+" "+voice[i].ar+" "+voice[i].dr+" "+voice[i].sl+" "+voice[i].sr+" "+voice[i].outstanding_samples+" ";
			ret+=voice[i].buf[0]+" "+voice[i].buf[1]+" "+voice[i].buf[2]+" "+voice[i].buf[3]+" ";
			switch(voice[i].envelope_state){case ATTACK: ret+="0 "; break; case DECAY: ret+="1 "; break;case SUSTAIN: ret+="2 "; break;case RELEASE: ret+="3 "; break;case DECREASE: ret+="4 "; break;case EXP: ret+="5 "; break;case INCREASE: ret+="6 "; break;case BENT: ret+="7 "; break;case DIRECT: ret+="8 "; break;case VOICE_OFF: ret+="9 "; break;}
			switch(voice[i].adsr_state){case ATTACK: ret+="0 "; break; case DECAY: ret+="1 "; break;case SUSTAIN: ret+="2 "; break;case RELEASE: ret+="3 "; break;case DECREASE: ret+="4 "; break;case EXP: ret+="5 "; break;case INCREASE: ret+="6 "; break;case BENT: ret+="7 "; break;case DIRECT: ret+="8 "; break;case VOICE_OFF: ret+="9 "; break;}
		}
		ret+="dspdone ";
		ret+="\n";
		return ret;
	}
	public void loadDSPState(String state)
	{
		Scanner s=new Scanner(state);
		applyfilter=s.nextInt()==1; SPEED_FACTOR=s.nextDouble(); mainVolumeLeft=s.nextInt(); mainVolumeRight=s.nextInt(); echoVolumeLeft=s.nextInt(); echoVolumeRight=s.nextInt(); keyOn=s.nextInt(); keyOff=s.nextInt(); flag=s.nextInt(); endx=s.nextInt(); echoFeedback=s.nextInt(); pitchModulation=s.nextInt(); noiseOn=s.nextInt(); echoOn=s.nextInt(); sourceDirectory=s.nextInt(); echoStartAddress=s.nextInt(); echoDelay=s.nextInt(); lastCount=s.nextLong(); recording=s.nextInt()==1;
		for (int i=0; i<8; i++)
			filterCoefficient[i]=s.nextInt();
		for(int i=0; i<8; i++)
		{
			voice[i].volumeLeft=s.nextInt(); voice[i].volumeRight=s.nextInt(); voice[i].pitchLow=s.nextInt(); voice[i].pitchHigh=s.nextInt(); voice[i].sourceNumber=s.nextInt(); voice[i].adsr1=s.nextInt(); voice[i].adsr2=s.nextInt(); voice[i].gain=s.nextInt(); voice[i].envx=s.nextInt(); voice[i].outx=s.nextInt(); voice[i].voicenumber=s.nextInt(); voice[i].enabled=s.nextInt()==1; voice[i].forceDisable=s.nextInt()==1; voice[i].last1=s.nextInt(); voice[i].last2=s.nextInt(); voice[i].pitchcounter=s.nextInt(); voice[i].samplesleft=s.nextInt(); voice[i].brrheader=s.nextInt(); voice[i].brrpointer=s.nextInt(); voice[i].bufpointer=s.nextInt(); voice[i].lasttime=s.nextLong(); voice[i].envelope_update_count=s.nextInt(); voice[i].gain_update_count=s.nextInt(); voice[i].envelope_counter=s.nextInt(); voice[i].counter_reset_value=s.nextInt(); voice[i].ar=s.nextInt(); voice[i].dr=s.nextInt(); voice[i].sl=s.nextInt(); voice[i].sr=s.nextInt(); voice[i].outstanding_samples=s.nextInt();
			voice[i].buf[0]=s.nextInt(); voice[i].buf[1]=s.nextInt(); voice[i].buf[2]=s.nextInt(); voice[i].buf[3]=s.nextInt();
			switch(s.nextInt()){case 0: voice[i].envelope_state=ENVELOPE_STATE.ATTACK; break; case 1: voice[i].envelope_state=ENVELOPE_STATE.DECAY; break; case 2: voice[i].envelope_state=ENVELOPE_STATE.SUSTAIN; break; case 3: voice[i].envelope_state=ENVELOPE_STATE.RELEASE; break; case 4: voice[i].envelope_state=ENVELOPE_STATE.DECREASE; break; case 5: voice[i].envelope_state=ENVELOPE_STATE.EXP; break; case 6: voice[i].envelope_state=ENVELOPE_STATE.INCREASE; break; case 7: voice[i].envelope_state=ENVELOPE_STATE.BENT; break; case 8: voice[i].envelope_state=ENVELOPE_STATE.DIRECT; break; case 9: voice[i].envelope_state=ENVELOPE_STATE.VOICE_OFF; break; }
			switch(s.nextInt()){case 0: voice[i].adsr_state=ENVELOPE_STATE.ATTACK; break; case 1: voice[i].adsr_state=ENVELOPE_STATE.DECAY; break; case 2: voice[i].adsr_state=ENVELOPE_STATE.SUSTAIN; break; case 3: voice[i].adsr_state=ENVELOPE_STATE.RELEASE; break; case 4: voice[i].adsr_state=ENVELOPE_STATE.DECREASE; break; case 5: voice[i].adsr_state=ENVELOPE_STATE.EXP; break; case 6: voice[i].adsr_state=ENVELOPE_STATE.INCREASE; break; case 7: voice[i].adsr_state=ENVELOPE_STATE.BENT; break; case 8: voice[i].adsr_state=ENVELOPE_STATE.DIRECT; break; case 9: voice[i].adsr_state=ENVELOPE_STATE.VOICE_OFF; break; }
		}
		rawsound=new ArrayList<Integer>();
		pitches=new ArrayList<Byte>();
		if(s.next().equals("dspdone"))
			System.out.println("loaded dsp");
		else
			System.out.println("error loading dsp");		
	}
}
