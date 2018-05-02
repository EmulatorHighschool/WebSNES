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

import javax.swing.JApplet;
import javax.swing.JPanel;

public class SNESApplet extends JApplet implements Runnable
{
	private static final long serialVersionUID = 1L;
	JPanel panel;
	SNES snes;
	Thread appletThread;
	boolean suspended=false;
	final Lock appletLock=new Lock();
	
	public void init()
	{
		appletThread=new Thread(this);
		appletThread.start();
	}
	public void start()
	{
		System.out.println("applet start called");
		if (!appletLock.islocked())
		{
			try
			{
				System.out.println("applet resumed");
				appletLock.unlock();
			}
			catch(NullPointerException e)
			{
				System.out.println(e);
			}
		}
	}
	public void stop()
	{
		System.out.println("applet paused");
		appletLock.lock();
	}

	public void run()
	{
		System.out.println("applet run called");
		panel = new JPanel();
		getContentPane().add(panel);
		snes = new SNES(this);
	}
}
