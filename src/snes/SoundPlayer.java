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

import java.io.ByteArrayInputStream;
import java.util.ArrayList;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;

public class SoundPlayer 
{
	public static final int SAMPLESIZE=32000, GAMEBOYSAMPLESIZE=16000;
	
	public SNES snes;
	int bits;
	AudioFormat af;
	AudioFormat gameboyAudioFormat;
	AudioInputStream ais;
	Clip clip;
	int isplaying=0;
	boolean oneException=false;
	
	public SoundPlayer(SNES snes, int bits)
	{
		this.snes=snes;
		this.bits=bits;
		af=new AudioFormat(SAMPLESIZE,16,1,true,false);
		gameboyAudioFormat=new AudioFormat(GAMEBOYSAMPLESIZE,8,1,true,false);
		
		try {
			clip=AudioSystem.getClip();
			clip.addLineListener(new LineListener(){
				public void update(LineEvent event) {
					if(isplaying==1)
					{
//						System.out.println("done playing");
					}
					if(isplaying>0) isplaying--;
					
				}});
		} catch (LineUnavailableException e) {
			e.printStackTrace();
		}
	}
	
	byte[] buffer=new byte[0];
	
	//this is called to play all the sound in the DSP buffer
	public void dumpsound()
	{
		if(isplaying!=0) return;
		
		if(buffer.length!=0)
			playbytes(buffer);
		if(snes.dsp.rawsound.size()<1) return;
		new Thread(new Runnable(){public void run(){
			try{
			Integer[] buffer0=new Integer[snes.dsp.rawsound.size()];
			buffer0=snes.dsp.rawsound.toArray(buffer0);
			if(!snes.dogameboy)
			{
		buffer=new byte[buffer0.length*2];
		for (int i=0; i<buffer0.length; i++)
		{
			buffer[i*2]=(byte)(buffer0[i]&0xff);
			buffer[i*2+1]=(byte)(buffer0[i]>>8);
		}
			}
			else
			{
				buffer=new byte[buffer0.length];
				for (int i=0; i<buffer0.length; i++)
					buffer[i]=(byte)(buffer0[i]&0xff);
			}
		snes.dsp.rawsound=new ArrayList<Integer>();
			}catch(Exception e){}
		}}).start();
		}

	public void playRawSound()
	{
		if(snes.dsp.rawsound.size()<1) return;
		byte[] s=new byte[snes.dsp.rawsound.size()*2];
		for (int i=0; i<snes.dsp.rawsound.size(); i++)
		{
			s[i*2]=(byte)(snes.dsp.rawsound.get(i)&0xff);
			s[i*2+1]=(byte)(snes.dsp.rawsound.get(i)>>8);
		}
		playbytes(s);
		snes.dsp.rawsound=new ArrayList<Integer>();
	}
	
	public void playbytes(byte[] t)
	{
		AudioInputStream ais;
		if(!snes.dogameboy)
			ais=new AudioInputStream(new ByteArrayInputStream(t),af,t.length);
		else
			ais=new AudioInputStream(new ByteArrayInputStream(t),gameboyAudioFormat,t.length);
		try {
			clip.close();
		}catch(Exception e){}
		try {
			clip.open(ais);
			clip.setFramePosition(0);
//			System.out.println("starting playing");
			isplaying=2;
			clip.loop(0);
			clip.drain();
		}
		catch (Exception e) {
			if(!oneException)
				e.printStackTrace();
			oneException=true;
		}		
	}

}
