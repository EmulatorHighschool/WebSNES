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
    
References:
	http://simsnex.tripod.com/SNESMem.txt
*/

package snes;

import java.util.Scanner;

public class Memory 
{
	//all of the memory address space, excluding I/O ports, is stored here
	byte[] physicalMemory;
	//true if the cartridge uses the HiROM format
	boolean HiROM=true;
	//some cartridges have a header.  this is the start of the actual ROM
	int cartridgeOffset=0;
	//last byte written to memory.  Openbus returns this when some addresses are read
	byte lastWrite=0;
	Processor65816 processor;
	String name;
	Cartridge cartridge;
	
	SNES snes;
	
	public Memory(SNES snes)
	{
		this.snes=snes;
		
		physicalMemory=new byte[0x1000000];

		//RAM is initialized to 55
		for (int i=0x7e0000; i<0x7fffff; i++)
			physicalMemory[i]=0x55;
		//unused SRAM is initialized to 60
		for (int p=0x30; p<=0x3f; p++)
			for (int a=0x6000; a<=0x7fff; a++)
				physicalMemory[(p<<16)|a]=0x60;
	}
	
	private boolean isValidNameCharacter(char c)
	{
		if(c>='A' && c<='Z') return true;
		if(c>='a' && c<='z') return true;
		if(c>='0' && c<='9') return true;
		if(c==' ') return true;
		if(c=='_') return true;
		if(c=='.') return true;
		if(c=='-') return true;
		if(c=='&') return true;
		if(c=='*') return true;
		if(c=="'".charAt(0)) return true;
		return false;
	}
	
	public void loadCartridge(Cartridge c)
	{
		cartridge=c;
		//you can tell that the game has a header if it is slightly larger (100 or 200 bytes) than expected
		cartridgeOffset=c.rawdata.length%0x1000;
		boolean matches=true;

		//is it HiROM or LoROM?
		//look for the game's name, stored at ffc0 on hirom and 7fc0 on lorom
		//if there are only ascii chars at ffc0, chances are it's hirom
		for(int i=0xffc0+cartridgeOffset; i<5+0xffc0+cartridgeOffset; i++)
			if(!isValidNameCharacter((char)c.rawdata[i]))
				matches=false;
		if(!matches)
		{
			//now check 7fc0 to see if it's lorom
			matches=true;
			for(int i=0x7fc0+cartridgeOffset; i<5+0x7fc0+cartridgeOffset; i++)
				if(!isValidNameCharacter((char)c.rawdata[i]))
					matches=false;
			if(matches)
				HiROM=false;
			else
			{
				System.out.println("Couldn't identify ROM");
				System.exit(0);
			}
		}
		//extract the name
		name="";
		if(HiROM)
		{
			for(int a=0xffc0; a<0xffc0+21; a++)
				name+=(char)c.rawdata[a+cartridgeOffset];
		}
		else
		{
			for(int a=0x7fc0; a<0x7fc0+21; a++)
				name+=(char)c.rawdata[a+cartridgeOffset];
		}
		System.out.println(name);
		if(HiROM) System.out.println("HIROM");
		else System.out.println("LOROM");
		System.out.printf("Header size %x\n",cartridgeOffset);
		String stext=name+" (";
		if(HiROM) stext+="HiROM)"; else stext+="LoROM)";
		snes.snesgui.settext(stext);
		
		//copy all of the cartridge into memory
		//if it's hirom, copy starting at c00000
		//if lorom, copy to 008000-00ffff, 018000-01ffff, 028000-02ffff ...
		if (HiROM)
		{
			if(snes.doromhack)
				snes.romhack=new Romhack(snes,c.rawdata,0xc00000,cartridgeOffset);
			try
			{
				for(int address=0xc00000; address<=0xffffff; address++)
					physicalMemory[address]=c.rawdata[address-0xc00000+cartridgeOffset];
			}
			catch(ArrayIndexOutOfBoundsException e){}
		}
		else
		{
			try
			{
				int count=0;
				for (int bank=0; bank<=0x3f; bank++)
				{
					for (int offset=0x8000; offset<=0xffff; offset++)
					{
						physicalMemory[(bank<<16)|offset]=c.rawdata[count+cartridgeOffset];
						count++;
					}
				}
			}
			catch(ArrayIndexOutOfBoundsException e){}
		}
		
		//now copy the sram
		if(c.sram!=null)
		{
			System.out.println("Loading SRAM");
			if(HiROM)
			{
				try
				{
					for(int address=0x306000; address<=0x307fff; address++)
						physicalMemory[address]=c.sram[address-0x306000];
				}
				catch(ArrayIndexOutOfBoundsException e){}
			}
			else
			{
				try
				{
					for(int address=0x700000; address<=0x707fff; address++)
						physicalMemory[address]=c.sram[address-0x700000];
				}
				catch(ArrayIndexOutOfBoundsException e){}
			}
		}
	}
	
	//read a byte from memory
	public byte readByte(int address)
	{
		if(snes.cycleAccurate)
			snes.docycles(getcycles(address));
		
		if (!HiROM)
			return readByteLowROM(address);

		int bank=(address>>16)&0xff;
		int offset=address&0xffff;
		
		if (bank<=0x2f)
		{
			//lowram shadowed from 7E
			if (offset<=0x1fff)
				return physicalMemory[0x7e0000|offset];
			//hardware registers
			else if (offset<=0x5fff)
				return snes.ppu.readHardwareRegister(offset);
			//RESERVED
			else if (offset<=0x7fff)
				return physicalMemory[address];
			//32k ROM chunks
			else
				return physicalMemory[((bank+0xc0)<<16)|offset];
		}
		else if (bank<=0x3f)
		{
			//lowram shadowed from 7E
			if (offset<=0x1fff)
				return physicalMemory[0x7e0000|offset];
			//hardware registers
			else if (offset<=0x5fff)
				return snes.ppu.readHardwareRegister(offset);
			//Mode 21 SRAM
			else if (offset<=0x7fff)
//				return physicalMemory[address];
return physicalMemory[0x300000|offset];
			//32k ROM chunks
			else
				return physicalMemory[((bank+0xc0)<<16)|offset];
		}
		//64k ROM chunks
		else if (bank<=0x6f)
//			return physicalMemory[address-0x400000+0xc0000];
			return physicalMemory[address-0x400000+0xc00000];
		//mode 20 save RAM
		else if (bank<=0x77)
			return physicalMemory[address];
		//RESERVED
		else if (bank<=0x7d)
			return physicalMemory[address];
		else if (bank==0x7e)
		{
			//low ram
			if (offset<0x1fff)
				return physicalMemory[address];
			//high ram
			else if (offset<0x7fff)
				return physicalMemory[address];
			//expanded ram
			else
				return physicalMemory[address];			
		}
		else if (bank==0x7f)
		{
			//expanded ram
			return physicalMemory[address];
		}
		//mirrors from banks 0x80 to 0xbf
		else if (bank<=0xbf)
			return readByte(address-0x800000);
		//ROM chunk from c0 to end
		else
			return physicalMemory[address];
	}
	
	//write a byte to memory
	public void writeByte(int address, byte value)
	{
		if(snes.cycleAccurate)
			snes.docycles(getcycles(address));
		
		if(!HiROM)
		{
			writeByteLowROM(address,value);
			return;
		}
		
		int bank=(address>>16)&0xff;
		int offset=address&0xffff;
		
		lastWrite=value;
		
		if (bank<=0x2f)
		{
			//lowram shadowed from 7E
			if (offset<=0x1fff)
				physicalMemory[0x7e0000|offset]=value;
			//hardware registers
			else if (offset<=0x5fff)
				snes.ppu.writeHardwareRegister(offset,value);
			//RESERVED - do nothing
			else if (offset<=0x7fff);
			//32k ROM chunks mirrored
			//interrupt vectors can be altered I think
			else if (address>=0xff00 && address<=0xffff)
				physicalMemory[((bank+0xc0)<<16)|offset]=value;
		}
		else if (bank<=0x3f)
		{
			//lowram shadowed from 7E
			if (offset<=0x1fff)
				physicalMemory[0x7e0000|offset]=value;
			//hardware registers
			else if (offset<=0x5fff)
				snes.ppu.writeHardwareRegister(offset,value);
			//Mode 21 SRAM
			else if (offset<=0x7fff)
//				physicalMemory[address]=value;
physicalMemory[0x300000|offset]=value;
			//32k ROM chunks
			else;
		}
		//64k ROM chunks
		else if (bank<=0x6f);
		//mode 20 save RAM
		else if (bank<=0x77)
			physicalMemory[address]=value;
		//RESERVED
		else if (bank<=0x7d);
		else if (bank==0x7e)
		{
			//low ram
			if (offset<=0x1fff)
				physicalMemory[address]=value;
			//high ram
			else if (offset<=0x7fff)
				physicalMemory[address]=value;
			//expanded ram
			else
				physicalMemory[address]=value;			
		}
		else if (bank==0x7f)
		{
			//expanded ram
			physicalMemory[address]=value;
		}
		//mirrors from banks 0x80 to 0xbf
		else if (bank<=0xbf)
			writeByte(address-0x800000,value);
		//ROM chunk from c0 to end
		else if (address<0xffff00);
		//interrupt vectors at bottom - presumably modifiable
		else
			physicalMemory[address]=value;
	}

	public byte readByteLowROM(int address)
	{
		int bank=(address>>16)&0xff;
		int offset=address&0xffff;
		
		if (bank<=0x3f)
		{
			//lowram shadowed from 7E
			if (offset<=0x1fff)
				return physicalMemory[0x7e0000|offset];
			//hardware registers
			else if (offset<=0x5fff)
				return snes.ppu.readHardwareRegister(offset);
			//RESERVED
			else if (offset<=0x7fff)
				return physicalMemory[address];
			//32k ROM chunks
			else
				return physicalMemory[address];
		}
		//64k ROM chunks
		else if (bank<=0x6f)
			return physicalMemory[address];
		//mode 20 save RAM
		else if (bank<=0x77)
			return physicalMemory[address];
		//RESERVED
		else if (bank<=0x7d)
			return physicalMemory[address];
		else if (bank==0x7e)
		{
			//low ram
			if (offset<0x1fff)
				return physicalMemory[address];
			//high ram
			else if (offset<0x7fff)
				return physicalMemory[address];
			//expanded ram
			else
				return physicalMemory[address];			
		}
		else if (bank==0x7f)
		{
			//expanded ram
			return physicalMemory[address];
		}
		//mirrors from banks 0x80 to 0xef
		else if (bank<=0xef)
			return readByteLowROM(address-0x800000);
		//ROM chunk from f0 to end
		else
			return physicalMemory[address];
	}
	
	
	
	public void writeByteLowROM(int address, byte value)
	{
		int bank=(address>>16)&0xff;
		int offset=address&0xffff;
		
		if (bank<=0x3f)
		{
			//lowram shadowed from 7E
			if (offset<=0x1fff)
				physicalMemory[0x7e0000|offset]=value;
			//hardware registers
			else if (offset<=0x4fff)
				snes.ppu.writeHardwareRegister(offset, value);
			//RESERVED
			else if (offset<=0x7fff);
			//interrupt vectors
			else if (address>=0xff00 && address<=0xffff)
				physicalMemory[((bank+0xc0)<<16)|offset]=value;
			//Mode 20 ROM program memory
			else;
		}
		//64k ROM chunks
		else if (bank<=0x6f);
		//mode 20 SRAM
		else if (bank<=0x77)
			physicalMemory[address]=value;
		//RESERVED
		else if (bank<=0x7d);
		else if (bank==0x7e)
		{
			//low ram
			if (offset<0x1fff)
				physicalMemory[address]=value;
			//high ram
			else if (offset<0x7fff)
				physicalMemory[address]=value;
			//expanded ram
			else
				physicalMemory[address]=value;			
		}
		else if (bank==0x7f)
		{
			//expanded ram
			physicalMemory[address]=value;
		}
		//mirrors from banks 0x80 to 0xbf
		else if (bank<=0xef)
			writeByteLowROM(address-0x800000,value);
		//RESERVED from ff to the end
		else if (address<0xffff00);
		//interrupt vectors at bottom - presumably modifiable
		else
			physicalMemory[address]=value;
	}
	
	public String dumpMemoryState()
	{		
		StringBuilder s=new StringBuilder();
		s.append("memory\n");
		s.append(String.format("%d %d ",HiROM?1:0,lastWrite));
		s.append(cartridgeOffset+" ");
		for(int a=0x0000; a<=0xffffff; a++)
			s.append(String.format("%x ",physicalMemory[a]));
		s.append("\n");
		return s.toString();
	}
	public void loadMemoryState(String state)
	{
		Scanner s=new Scanner(state);
		HiROM=s.nextInt()==1; lastWrite=(byte)s.nextInt();
		if(snes.savestateversion>=2)
		{
			cartridgeOffset=s.nextInt();
		}
		for(int a=0x0000; a<=0xffffff; a++)
			physicalMemory[a]=(byte)s.nextInt(16);
	}

	//numbers from SNES9x getset.h
	public int getcycles(int address)
	{
		if ((address&0x408000)!=0)
			return 6;				//really about 1.3
		if (((address+0x6000)&0x4000)!=0)
			return 6;				//really about 1.3
		if (((address-0x4000)&0x7e00)!=0)
			return 4;
		return 8;
	}
}
