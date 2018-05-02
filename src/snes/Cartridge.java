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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class Cartridge 
{
	byte[] rawdata;
	byte[] sram;
	public Cartridge(String filename)
	{
		loadROM(filename);
	}
	public Cartridge(String filename, String sramfilename)
	{
		loadROM(filename);
		loadSRAM(sramfilename);
	}
	public void loadROM(String filename)
	{
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
	}
	public void loadSRAM(String filename)
	{
		try 
		{
			File f=new File(filename);
			FileInputStream fis=new FileInputStream(filename);
			sram=new byte[(int)f.length()];
			fis.read(sram);
			fis.close();
		} 
		catch (FileNotFoundException e) 
		{
			System.out.println("SRAM file not found");
			//e.printStackTrace();
			sram=null;
			return;
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
			sram=null;
			return;
		}
	}
}
