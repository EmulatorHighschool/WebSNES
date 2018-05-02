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

//Lock creates a lock object for synchronizing threads
public class Lock
{
	boolean locked=false;
	
	//if locked, pause thread until unlocked
	public synchronized void testlock()
	{
			if(locked)
				try {this.wait();} catch (InterruptedException e) {e.printStackTrace();}
	}
	//if locked, pause thread until unlocked, then relock it
	public synchronized void testandsetlock()
	{
		if(locked)
			try {this.wait();} catch (InterruptedException e) {e.printStackTrace();}
		locked=true;
	}
	//turn lock on
	public synchronized void lock()
	{
		locked=true;
	}
	//turn lock off and wake up a waiting thread
	public synchronized void unlock()
	{
		if(locked)
		{
			locked=false;
			this.notify();
		}
	}
	
	//is it locked?
	public boolean islocked() { return locked; }
	
	//sleep until thread is locked, but don't pause the thread
	public void sleepUntilLocked()
	{
		while(!locked)
		{
			try{Thread.sleep(100);}catch(Exception e){}
		}
	}
}