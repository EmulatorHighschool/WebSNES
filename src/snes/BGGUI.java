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

import java.awt.Color;
import java.awt.Graphics;

import javax.swing.JComponent;
import javax.swing.JFrame;

//displays video backgrounds in a separate window for debugging
public class BGGUI extends JComponent
{
	private static final long serialVersionUID = 1L;
	public static final int SCALE=2;
	SNES snes;
	int bg;
	JFrame frame;
	int[][] pixels;

	public BGGUI(SNES snes, int bg)
	{
		this.snes=snes;
		this.bg=bg;
		pixels=new int[0][0];
		if (bg!=-1)
			frame=new JFrame("BG plane "+bg);
		else
			frame=new JFrame("Sprite plane");
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setSize(100,100);
		frame.add(this);
		frame.setVisible(true);
	}
	public void drawIt(int[][] pixels)
	{
		this.pixels=pixels;
		frame.setSize(pixels.length*SCALE,pixels[0].length*SCALE);
		repaint();
	}
	public void paintComponent(Graphics g)
	{
		for(int x=0; x<pixels.length; x++)
		{
			for(int y=0; y<pixels[0].length; y++)
			{
				g.setColor(new Color((pixels[x][y]>>16)&0xff,(pixels[x][y]>>8)&0xff,(pixels[x][y])&0xff));
				g.fillRect(x*SCALE, y*SCALE, SCALE, SCALE);
			}
		}
	}
}
