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
import java.awt.Image;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JComponent;

//displays the current frame in a jcomponent
public class Screen extends JComponent implements KeyListener
{
	private static final long serialVersionUID = 1L;
	SNES snes;
	BGGUI[] bggui;
	Graphics bufferGraphics;
	Image offscreen;
	public Screen(SNES snes)
	{
		this.snes=snes;
		setBounds(0,0,SNESGUI.SCREENWIDTH,SNESGUI.SCREENHEIGHT);
		snes.snesgui.screenPanel.add(this);

		//initial key mapping.  this can be changed during runtime
		map=new int[256];
		map['A']=KeyEvent.VK_S;
		map['B']=KeyEvent.VK_A;
		map['X']=KeyEvent.VK_X;
		map['Y']=KeyEvent.VK_Z;
		map['u']=KeyEvent.VK_UP;
		map['d']=KeyEvent.VK_DOWN;
		map['l']=KeyEvent.VK_LEFT;
		map['r']=KeyEvent.VK_RIGHT;
		map['T']=KeyEvent.VK_ENTER;
		map['S']=KeyEvent.VK_BACK_SPACE;
		map['L']=KeyEvent.VK_Q;
		map['R']=KeyEvent.VK_W;

		this.addKeyListener(this);
			this.setFocusable(true);
	//		this.requestFocusInWindow();
			this.setFocusTraversalKeysEnabled(false);
			this.requestFocus();
			
	}
	public void paintComponent(Graphics bg)
	{
		if(snes.dogameboy)
		{
			paintComponentGameboy(bg);
			return;
		}
		if(snes.dones)
		{
			paintComponentNES(bg);
			return;
		}
		
		//double buffer to reduce flickering
		offscreen=createImage(PPU.SNES_WIDTH*SNESGUI.SCALE,PPU.SNES_HEIGHT*SNESGUI.SCALE);
		Graphics g=offscreen.getGraphics();

		g.setColor(Color.BLACK);
		g.fillRect(0,0,PPU.SNES_WIDTH*SNESGUI.SCALE, PPU.SNES_HEIGHT*SNESGUI.SCALE);
//		if(!snes.multithreaded && snes.ppu.displayBlanked)return;
		
		double b=(double)(snes.ppu.brightness)/0xf;
		if(snes.ppu.forceVisible)b=1;
		if(snes.multithreaded)b=1;
		
		int margin=8;
		for(int x=margin; x<PPU.SNES_WIDTH-margin; x++)
		{
			for(int y=margin-1; y<PPU.SNES_HEIGHT-margin-1; y++)
			{
				g.setColor(new Color((int)(b*((snes.video.screen[x][y]>>16)&0xff)),(int)(b*((snes.video.screen[x][y]>>8)&0xff)),(int)(b*((snes.video.screen[x][y])&0xff))));
				g.fillRect(x*SNESGUI.SCALE, y*SNESGUI.SCALE, SNESGUI.SCALE, SNESGUI.SCALE);
			}
		}

		bg.drawImage(offscreen,0,0,this);
	}
	
	static final Color[] GAMEBOY_COLOR=new Color[]{new Color(255,255,255), new Color(170,170,170), new Color(85,85,85), new Color(0,0,0)};
	public void paintComponentGameboy(Graphics bg)
	{
		offscreen=createImage(160*SNESGUI.GBSCALE,144*SNESGUI.GBSCALE);
		Graphics g=offscreen.getGraphics();

		g.setColor(Color.BLACK);
		g.fillRect(0,0,160*SNESGUI.GBSCALE, 144*SNESGUI.GBSCALE);

		for(int x=0; x<160; x++)
		{
			for(int y=0; y<144; y++)
			{
				g.setColor(GAMEBOY_COLOR[snes.gameboy.frame[x][y]]);
				g.fillRect(x*SNESGUI.GBSCALE, y*SNESGUI.GBSCALE, SNESGUI.GBSCALE, SNESGUI.GBSCALE);
			}
		}		
		bg.drawImage(offscreen,0,0,this);
	}
	
	public void paintComponentNES(Graphics bg)
	{
		offscreen=createImage(NES.SCREEN_WIDTH*SNESGUI.NESSCALE,NES.SCREEN_HEIGHT*SNESGUI.NESSCALE);
		Graphics g=offscreen.getGraphics();

		g.setColor(Color.BLACK);
		g.fillRect(0,0,NES.SCREEN_WIDTH*SNESGUI.NESSCALE, NES.SCREEN_HEIGHT*SNESGUI.NESSCALE);

		for(int x=0; x<NES.SCREEN_WIDTH; x++)
		{
			for(int y=0; y<NES.SCREEN_HEIGHT; y++)
			{
				g.setColor(snes.nes.frame[x][y]);
				g.fillRect(x*SNESGUI.NESSCALE, y*SNESGUI.NESSCALE, SNESGUI.NESSCALE, SNESGUI.NESSCALE);
			}
		}		
		bg.drawImage(offscreen,0,0,this);
	}
	
	public int[] map;
	
	private char keymap(KeyEvent e)
	{
		char key=' ';
		if(e.getKeyCode()==map['A']) return 'A';
		if(e.getKeyCode()==map['B']) return 'B';
		if(e.getKeyCode()==map['X']) return 'X';
		if(e.getKeyCode()==map['Y']) return 'Y';
		if(e.getKeyCode()==map['u']) return 'u';
		if(e.getKeyCode()==map['d']) return 'd';
		if(e.getKeyCode()==map['l']) return 'l';
		if(e.getKeyCode()==map['r']) return 'r';
		if(e.getKeyCode()==map['S']) return 'S';
		if(e.getKeyCode()==map['T']) return 'T';
		if(e.getKeyCode()==map['L']) return 'L';
		if(e.getKeyCode()==map['R']) return 'R';
		switch(e.getKeyCode())
		{
		case KeyEvent.VK_SPACE:
			key='P'; break;
		case KeyEvent.VK_ESCAPE:
			key='E'; break;
		case KeyEvent.VK_BACK_SLASH:
			key='\\'; break;
		case KeyEvent.VK_EQUALS:
			key='='; break;
		case KeyEvent.VK_MINUS:
			key='-'; break;
		case KeyEvent.VK_BACK_QUOTE:
			key='t'; break;
		}
		if (key!=' ') return key;
		switch(e.getKeyChar())
		{
		case '1': case '2': case '3': case '4': case '5': return e.getKeyChar();
		case '!': case '@': case '#': case '$': case '%': return e.getKeyChar();
		}
		return key;
	}
	
	//call the PPU when a key is pressed
	public void keyPressed(KeyEvent e) 
	{
		if(snes.snesgui.controllerComponent.currentPress!=0)
		{
			map[snes.snesgui.controllerComponent.currentPress]=e.getKeyCode();
			return;
		}
		if(!snes.dogameboy && !snes.dones)
			snes.ppu.keydown(keymap(e));
		else if (snes.dogameboy)
			snes.gameboy.keydown(keymap(e));
		else
			snes.nes.keydown(keymap(e));
		snes.snesgui.controllerComponent.keydown(keymap(e));
	}
	public void keyReleased(KeyEvent e) 
	{
		if(!snes.dogameboy && !snes.dones)
			snes.ppu.keyup(keymap(e));
		else if (snes.dogameboy)
			snes.gameboy.keyup(keymap(e));
		else
			snes.nes.keyup(keymap(e));
		snes.snesgui.controllerComponent.keyup(keymap(e));
	}
	public void keyTyped(KeyEvent e) {}

}
