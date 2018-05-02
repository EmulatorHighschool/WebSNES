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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;

//handles the main window, menus, buttons, and status text
public class SNESGUI 
{
	public static final int SCALE=2, GBSCALE=3, NESSCALE=2;
	public static final int SCREENHEIGHT=PPU.SNES_HEIGHT*SCALE, SCREENWIDTH=PPU.SNES_WIDTH*SCALE;
	public static final int STATUSHEIGHT=30;
	public static final int YMARGIN=40;
	public static final int XMARGIN=5;
	public static final int BUTTONHEIGHT=100;
	public static final int FULLWIDTH=SCREENWIDTH+XMARGIN;
	public static final int FULLHEIGHT=SCREENHEIGHT+BUTTONHEIGHT+STATUSHEIGHT+YMARGIN;
	public static final int CONTROLLERHEIGHT=BUTTONHEIGHT;
	public static final int BUTTONWIDTH=200;
	public static final int CONTROLLERWIDTH=SCREENWIDTH-BUTTONWIDTH;
	
	SNES snes;
	boolean isapplet;
	JPanel screenPanel,buttonPanel,statusPanel,controllerPanel;
	JTextField statusfield;
	JMenuBar menubar;
	ButtonComponent buttonComponent;
	ControllerComponent controllerComponent;
	PrintWriter spc700trace,cputrace;
	long lastRealTime;
	long lastFrameCount;
	
	public SNESGUI(SNES snes, boolean isapplet)
	{
		this.snes=snes;
		this.isapplet=isapplet;
		screenPanel=new JPanel();
		buttonPanel=new JPanel();
		statusPanel=new JPanel();
		controllerPanel=new JPanel();
		screenPanel.setLayout(null);
		buttonPanel.setLayout(null);
		statusPanel.setLayout(null);
		controllerPanel.setLayout(null);
		screenPanel.setBounds(0,0,SCREENWIDTH,SCREENHEIGHT);
		buttonPanel.setBounds(0,SCREENHEIGHT,BUTTONWIDTH,BUTTONHEIGHT);
		statusPanel.setBounds(0,SCREENHEIGHT+BUTTONHEIGHT,SCREENWIDTH,STATUSHEIGHT);
		controllerPanel.setBounds(BUTTONWIDTH,SCREENHEIGHT,CONTROLLERWIDTH,CONTROLLERHEIGHT);
		statusfield=new JTextField();
		statusfield.setBounds(0,0,SCREENWIDTH,STATUSHEIGHT);
		statusfield.setFont(statusfield.getFont().deriveFont(statusfield.getFont().getStyle() ^ Font.BOLD));
		statusPanel.add(statusfield);
		buttonComponent=new ButtonComponent();
		buttonComponent.setBounds(0,0,SCREENWIDTH,BUTTONHEIGHT);
		buttonPanel.add(buttonComponent);
		controllerComponent=new ControllerComponent();
		controllerComponent.setBounds(0,0,CONTROLLERWIDTH,CONTROLLERHEIGHT);
		controllerPanel.add(controllerComponent);
		
		if(!isapplet)
		{
			JFrame snesFrame = new JFrame("Super Nintendo Emulator");
			snesFrame.setSize(FULLWIDTH,FULLHEIGHT);
			snesFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			snesFrame.setLayout(null);

			snesFrame.add(statusPanel);
			snesFrame.add(buttonPanel);
			snesFrame.add(controllerPanel);
			snesFrame.add(screenPanel);
			snesFrame.setJMenuBar(constructMenuBar());
			snesFrame.setVisible(true);

		}
		else
		{
			snes.applet.panel.setLayout(null);
			snes.applet.panel.add(buttonPanel);
			snes.applet.panel.add(statusPanel);
			snes.applet.panel.add(controllerPanel);
			snes.applet.panel.add(screenPanel);
			snes.applet.setJMenuBar(constructMenuBar());
			snes.applet.panel.revalidate();
		}
		lastRealTime=System.currentTimeMillis();
		lastFrameCount=0;
	}
	private JMenuBar constructMenuBar()
	{
		menubar=new JMenuBar();
		JMenu menuGame = new JMenu("Game");
		JMenu menuSnapshot = new JMenu("Snapshot");
		JMenu menuDebug = new JMenu("Debug");
		JMenu menuHelp = new JMenu("Help");
		MenuListener ml=new MenuListener();

		for (String s:new String[]{"Preferences","Save SRAM","Load SPC","Save SPC","Mute","Reset","Exit"})
		{
			JMenuItem item=new JMenuItem(s);
			item.addActionListener(ml);
			menuGame.add(item);
		}
		for (String s:new String[]{"Save 1","Save 2","Save 3","Save 4","Save 5","Load 1","Load 2","Load 3","Load 4","Load 5"})
		{
			JMenuItem item=new JMenuItem(s);
			item.addActionListener(ml);
			menuSnapshot.add(item);
		}
		for (String s:new String[]{"Toggle Components","Open BGs","Flush Sound Buffer","CPU","RAM","SRAM","Interrupt Vectors","Picture Processor","Sound Processor","CPU Trace","SPC700 Trace"})
		{
			JMenuItem item=new JMenuItem(s);
			item.addActionListener(ml);
			menuDebug.add(item);
		}
		for (String s:new String[]{"About","SNES Games supported","Gameboy games supported"})
		{
			JMenuItem item=new JMenuItem(s);
			item.addActionListener(ml);
			menuHelp.add(item);
		}

		menubar.add(menuGame);
		menubar.add(menuSnapshot);
		menubar.add(menuDebug);
		menubar.add(menuHelp);
		return menubar;
	}
	private class MenuListener implements ActionListener
	{
		public void actionPerformed(ActionEvent e)
		{
			if (snes.pauselock!=null)
			{
				snes.pauselock.lock();
				buttonComponent.step.setEnabled(true);
				buttonComponent.pause.setText("Resume");
			}
			if (e.getActionCommand().equals("Exit"))
			{
				snes.saveSRAM();
				if(spc700trace!=null)
					spc700trace.close();
				if(cputrace!=null)
					cputrace.close();
				System.exit(0);				
			}
			else if (e.getActionCommand().equals("About"))
			{
				JOptionPane.showMessageDialog(null, "Java Super Nintendo Emulator\n Created by Michael Black\n 5/2014");
			}
			else if (e.getActionCommand().equals("SNES Games supported"))
			{
				String[] gamelist=new String[]{
				"7th Saga", "7th Saga 2", "Alien 3","Arkanoid","Axelay","Bart's Nightmare","Batman and Robin","Batman Returns","Biker Mice from Mars","Brain Lord","Breath of Fire 1","Breath of Fire 2",
				"Captain Novolin","Castlevania 4","Castlevania 5","Choplifter 3","Chrono Trigger","Civilization","Contra 3","Dragon's Lair","Earthbound","E.V.O.",
				"Final Fantasy II (4)","Final Fantasy III (6)","Final Fantasy V",
				"Fire Striker","First Samurai","Flashback","Gods","Gradius 3","Home Improvement",
				"Joe and Mac 1","Lords of Darkness","Lufia 1","Mark Davis Fishing Master","Mighty Max","Musya","NBA Jam","Q*Bert 3",
				"Paperboy 2","Pocky","Populous","Realm","Road Runner","Romance of the Three Kingdoms 2","Romancing Saga 2","Secret of Mana","SimAnt","SimCity 2000","SimEarth","SkyBlazer","Sonic the Hedgehog","Space Megaforce","Sparkster","Spider Man and Venom Maximum Carnage","Sunset Riders",
				"Super Adventure Island 2","Super Baseball Simulator 1000","Super Mario World","Super Ninja Boy","Super Star Wars","Super Tennis","Super Turrican 1","Super Turrican 2",
				"Teenage Mutent Ninja Turtles 4","Tetris and Dr. Mario","Tiny Toons Adventure","Ultima 6","Ultima Runes of Virtue 2","Wario's Woods","Wizard of Oz","Wolf Child","Worms","Xardion","X-Men 1","Yoshi's Cookie","Zelda - A Link to the Past","Zool",
				};
			    JOptionPane optionPane = new JOptionPane();
			    optionPane.setMessage("These games run correctly or with minor graphical glitches");
			    optionPane.setMessageType(JOptionPane.INFORMATION_MESSAGE);
			    JPanel panel = new JPanel();
			    panel.setLayout(new GridLayout(gamelist.length,1));
			    JButton[] buttons = new JButton[gamelist.length];
			    for (int i = 0; i < gamelist.length; i++)
			    {
			        buttons[i] = new JButton(gamelist[i]);
			        panel.add(buttons[i]);
			    }
			    optionPane.setOptionType(JOptionPane.DEFAULT_OPTION);
			    JScrollPane sp=new JScrollPane(panel);
			    sp.setPreferredSize(new Dimension(100,400));
			    optionPane.add(sp,1);
			    JDialog dialog = optionPane.createDialog(null, "Games supported");
			    dialog.setVisible(true);			
			}
			else if (e.getActionCommand().equals("Gameboy games supported"))
			{
				String[] gamelist=new String[]{
				"Castle Quest","Castlevania Adventure","Castlevania II","Castlevania - Legends","Chessmaster","Donkey Kong","Donkey Kong Land","Donkey Kong Land II","Donkey Kong Land III","Earthworm Jim",
				"Final Fantasy Adventure","Final Fantasy Legend","Final Fantasy Legend II","Final Fantasy Legend III",
				"Harvest Moon","Kid Dracula","Kirby's Dream Land","Kirby's Dream Land 2","Lemmings","Metroid II","NBA Jam","Pokemon Blue","Pokemon Red",
				"Super Mario Land","Super Mario Land 2","Tetris","Tetris 2","Ultima - Runes of Virtue","Yoshi's Cookie","Zelda - Link's Awakening",
				};
			    JOptionPane optionPane = new JOptionPane();
			    optionPane.setMessage("These games run correctly or with minor graphical glitches");
			    optionPane.setMessageType(JOptionPane.INFORMATION_MESSAGE);
			    JPanel panel = new JPanel();
			    panel.setLayout(new GridLayout(gamelist.length,1));
			    JButton[] buttons = new JButton[gamelist.length];
			    for (int i = 0; i < gamelist.length; i++)
			    {
			        buttons[i] = new JButton(gamelist[i]);
			        panel.add(buttons[i]);
			    }
			    optionPane.setOptionType(JOptionPane.DEFAULT_OPTION);
			    JScrollPane sp=new JScrollPane(panel);
			    sp.setPreferredSize(new Dimension(100,400));
			    optionPane.add(sp,1);
			    JDialog dialog = optionPane.createDialog(null, "Games supported");
			    dialog.setVisible(true);			
			}
			else if (e.getActionCommand().equals("RAM"))
			{
				Popup.Element[] ram=new Popup.Element[0x20000];
				for(int i=0; i<ram.length; i++)
				{
					final int j=i;
					ram[i]=new Popup.Element(Integer.toHexString(0x7e0000+i), new Popup.PopupAction() { public void doaction(String value) { snes.memory.writeByte(0x7e0000+j,(byte)Integer.parseInt(value,16)); }}, Integer.toHexString(0xff&snes.memory.readByte(0x7e0000+i)));
				}
				new Popup(snes,"RAM",ram);
			}
			else if (e.getActionCommand().equals("Interrupt Vectors"))
			{
				Popup.Element[] ram=new Popup.Element[0x100];
				for(int i=0; i<ram.length; i++)
				{
					final int j=i;
					ram[i]=new Popup.Element(Integer.toHexString(0xffff00+i), new Popup.PopupAction() { public void doaction(String value) { snes.memory.writeByte(0xffff00+j,(byte)Integer.parseInt(value,16)); }}, Integer.toHexString(0xff&snes.memory.readByte(0xffff00+i)));
				}
				new Popup(snes,"Interrupt Vectors",ram);
			}
			else if (e.getActionCommand().equals("SRAM"))
			{
				Popup.Element[] ram;
				if(snes.memory.HiROM)
				{
					ram=new Popup.Element[0x8000];
					for(int i=0; i<ram.length; i++)
					{
						final int j=i;
						ram[i]=new Popup.Element(Integer.toHexString(0x700000+i), new Popup.PopupAction() { public void doaction(String value) { snes.memory.writeByte(0x700000+j,(byte)Integer.parseInt(value,16)); }}, Integer.toHexString(0xff&snes.memory.readByte(0x700000+i)));
					}
				}
				else
				{
					ram=new Popup.Element[0x2000];
					for(int i=0; i<ram.length; i++)
					{
						final int j=i;
						ram[i]=new Popup.Element(Integer.toHexString(0x306000+i), new Popup.PopupAction() { public void doaction(String value) { snes.memory.writeByte(0x306000+j,(byte)Integer.parseInt(value,16)); }}, Integer.toHexString(0xff&snes.memory.readByte(0x306000+i)));
					}					
				}
				new Popup(snes,"RAM",ram);
			}
			
			else if (e.getActionCommand().equals("Flush Sound Buffer"))
			{
				snes.dsp.rawsound=new ArrayList<Integer>();
				snes.dsp.soundplayer.clip.stop();
				snes.dsp.soundplayer.clip.flush();
			}
			else if (e.getActionCommand().equals("CPU"))
			{
				new Popup(snes,"CPU State",new Popup.Element[]{
						new Popup.Element("PBR", new Popup.PopupAction() { public void doaction(String value) { snes.processor.PBR=Integer.parseInt(value,16); }}, Integer.toHexString(snes.processor.PBR)),
						new Popup.Element("PC", new Popup.PopupAction() { public void doaction(String value) { snes.processor.PC=Integer.parseInt(value,16); }}, Integer.toHexString(snes.processor.PC)),
						new Popup.Element("A", new Popup.PopupAction() { public void doaction(String value) { snes.processor.A=Integer.parseInt(value,16); }}, Integer.toHexString(snes.processor.A)),
						new Popup.Element("X", new Popup.PopupAction() { public void doaction(String value) { snes.processor.X=Integer.parseInt(value,16); }}, Integer.toHexString(snes.processor.X)),
						new Popup.Element("Y", new Popup.PopupAction() { public void doaction(String value) { snes.processor.Y=Integer.parseInt(value,16); }}, Integer.toHexString(snes.processor.Y)),
						new Popup.Element("D", new Popup.PopupAction() { public void doaction(String value) { snes.processor.D=Integer.parseInt(value,16); }}, Integer.toHexString(snes.processor.D)),
						new Popup.Element("DBR", new Popup.PopupAction() { public void doaction(String value) { snes.processor.DBR=Integer.parseInt(value,16); }}, Integer.toHexString(snes.processor.DBR)),
						new Popup.Element("SP", new Popup.PopupAction() { public void doaction(String value) { snes.processor.S=Integer.parseInt(value,16); }}, Integer.toHexString(snes.processor.S)),
						new Popup.Element("Carry", new Popup.PopupAction() { public void doaction(String value) { snes.processor.CARRY=value.equals("1"); snes.processor.updateP(); }}, snes.processor.CARRY),
						new Popup.Element("Zero", new Popup.PopupAction() { public void doaction(String value) { snes.processor.ZERO=value.equals("1"); snes.processor.updateP(); }}, snes.processor.ZERO),
						new Popup.Element("IRQ", new Popup.PopupAction() { public void doaction(String value) { snes.processor.IRQ=value.equals("1"); snes.processor.updateP(); }}, snes.processor.IRQ),
						new Popup.Element("Decimal", new Popup.PopupAction() { public void doaction(String value) { snes.processor.DECIMAL=value.equals("1"); snes.processor.updateP(); }}, snes.processor.DECIMAL),
						new Popup.Element("Index 8/16", new Popup.PopupAction() { public void doaction(String value) { snes.processor.INDEXFLAG=value.equals("1"); snes.processor.updateP(); }}, snes.processor.INDEXFLAG),
						new Popup.Element("Memory 8/16", new Popup.PopupAction() { public void doaction(String value) { snes.processor.MEMORYFLAG=value.equals("1"); snes.processor.updateP(); }}, snes.processor.MEMORYFLAG),
						new Popup.Element("Overflow", new Popup.PopupAction() { public void doaction(String value) { snes.processor.OVERFLOW=value.equals("1"); snes.processor.updateP(); }}, snes.processor.OVERFLOW),
						new Popup.Element("Negative", new Popup.PopupAction() { public void doaction(String value) { snes.processor.NEGATIVE=value.equals("1"); snes.processor.updateP(); }}, snes.processor.NEGATIVE),
						new Popup.Element("Emulate 6502", new Popup.PopupAction() { public void doaction(String value) { snes.processor.EMULATION=value.equals("1"); snes.processor.updateP(); }}, snes.processor.EMULATION),
						new Popup.Element("Last Inst", new Popup.PopupAction() { public void doaction(String value) { }}, Integer.toHexString(snes.processor.opcode)),
						new Popup.Element("Last Opcode", new Popup.PopupAction() { public void doaction(String value) { }}, snes.processor.opcodeName),
						new Popup.Element("Last Address", new Popup.PopupAction() { public void doaction(String value) { }}, Integer.toHexString(snes.processor.addr)),
						new Popup.Element("Instructions", new Popup.PopupAction() { public void doaction(String value) { snes.processor.instructionCount=Long.parseLong(value); }}, Long.toString(snes.processor.instructionCount)),
						new Popup.Element("Cycles", new Popup.PopupAction() { public void doaction(String value) { snes.processor.cycleCount=Long.parseLong(value); }}, Long.toString(snes.processor.cycleCount)),
						new Popup.Element("Insts to event", new Popup.PopupAction() { public void doaction(String value) { snes.processor.cycleCount=Long.parseLong(value); }}, Integer.toString(snes.processor.instructionsUntilEvent)),
						new Popup.Element("NMI trigger pos", new Popup.PopupAction() { public void doaction(String value) { snes.processor.instructionsUntilEvent=Integer.parseInt(value); }}, Integer.toString(snes.processor.NMItriggerPosition)),
						new Popup.Element("NMI trigger", new Popup.PopupAction() { public void doaction(String value) { snes.processor.NMItrigger=value.equals("1"); }}, snes.processor.NMItrigger),
						new Popup.Element("Wait", new Popup.PopupAction() { public void doaction(String value) { snes.processor.waitForInterrupt=value.equals("1"); }}, snes.processor.waitForInterrupt),
				});
			}

			else if (e.getActionCommand().equals("Sound Processor"))
			{
				new Popup(snes,"Sound Processor State",new Popup.Element[]{
						new Popup.Element("PC", new Popup.PopupAction() { public void doaction(String value) { snes.spc700.PC=Integer.parseInt(value,16); }}, Integer.toHexString(snes.spc700.PC)),
						new Popup.Element("A", new Popup.PopupAction() { public void doaction(String value) { snes.spc700.A=Integer.parseInt(value,16); }}, Integer.toHexString(snes.spc700.A)),
						new Popup.Element("X", new Popup.PopupAction() { public void doaction(String value) { snes.spc700.X=Integer.parseInt(value,16); }}, Integer.toHexString(snes.spc700.X)),
						new Popup.Element("Y", new Popup.PopupAction() { public void doaction(String value) { snes.spc700.Y=Integer.parseInt(value,16); }}, Integer.toHexString(snes.spc700.Y)),
						new Popup.Element("SP", new Popup.PopupAction() { public void doaction(String value) { snes.spc700.S=Integer.parseInt(value,16); }}, Integer.toHexString(snes.spc700.S)),
						new Popup.Element("Carry", new Popup.PopupAction() { public void doaction(String value) { snes.spc700.CARRY=value.equals("1"); snes.spc700.updateP(); }}, snes.spc700.CARRY),
						new Popup.Element("Zero", new Popup.PopupAction() { public void doaction(String value) { snes.spc700.ZERO=value.equals("1"); snes.spc700.updateP(); }}, snes.spc700.ZERO),
						new Popup.Element("Interrupts", new Popup.PopupAction() { public void doaction(String value) { snes.spc700.INTERRUPTSENABLED=value.equals("1"); snes.spc700.updateP(); }}, snes.spc700.INTERRUPTSENABLED),
						new Popup.Element("HalfCarry", new Popup.PopupAction() { public void doaction(String value) { snes.spc700.HALFCARRY=value.equals("1"); snes.spc700.updateP(); }}, snes.spc700.HALFCARRY),
						new Popup.Element("Break", new Popup.PopupAction() { public void doaction(String value) { snes.spc700.BREAK=value.equals("1"); snes.spc700.updateP(); }}, snes.spc700.BREAK),
						new Popup.Element("Direct Page", new Popup.PopupAction() { public void doaction(String value) { snes.spc700.DIRECTPAGE=value.equals("1"); snes.spc700.updateP(); }}, snes.spc700.DIRECTPAGE),
						new Popup.Element("Overflow", new Popup.PopupAction() { public void doaction(String value) { snes.spc700.OVERFLOW=value.equals("1"); snes.spc700.updateP(); }}, snes.spc700.OVERFLOW),
						new Popup.Element("Negative", new Popup.PopupAction() { public void doaction(String value) { snes.spc700.NEGATIVE=value.equals("1"); snes.spc700.updateP(); }}, snes.spc700.NEGATIVE),
						new Popup.Element("Last Inst", new Popup.PopupAction() { public void doaction(String value) { }}, Integer.toHexString(snes.spc700.opcode)),
						new Popup.Element("Last Opcode", new Popup.PopupAction() { public void doaction(String value) { }}, snes.spc700.opcodeName),
						new Popup.Element("Instructions", new Popup.PopupAction() { public void doaction(String value) { snes.spc700.instructionCount=Long.parseLong(value); }}, Long.toString(snes.spc700.instructionCount)),
						new Popup.Element("Cycles", new Popup.PopupAction() { public void doaction(String value) { snes.spc700.cycleCount=Long.parseLong(value); }}, Long.toString(snes.spc700.cycleCount)),
						new Popup.Element("Wait", new Popup.PopupAction() { public void doaction(String value) { snes.spc700.waitForInterrupt=value.equals("1"); }}, snes.spc700.waitForInterrupt),
				});
			}

			else if (e.getActionCommand().equals("Picture Processor"))
			{
				new Popup(snes,"PPU State",new Popup.Element[]{
						new Popup.Element("VCounter", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.VCounter=Integer.parseInt(value); }}, Integer.toString(snes.ppu.VCounter)),
						new Popup.Element("Mode", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.mode=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.mode)),
						new Popup.Element("Brightness", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.brightness=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.brightness)),
						new Popup.Element("Display Blanked", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.displayBlanked=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.displayBlanked),
						new Popup.Element("BG1 Base", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[1].Base=Integer.parseInt(value,16); snes.video.updateWholeScreen();}}, Integer.toHexString(snes.ppu.bg[1].Base)),
						new Popup.Element("BG1 Tile Base", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[1].TileBase=Integer.parseInt(value,16); snes.video.updateWholeScreen();}}, Integer.toHexString(snes.ppu.bg[1].TileBase)),
						new Popup.Element("BG1 Size", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[1].Size=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.bg[1].Size)),
						new Popup.Element("BG1 Tile Size", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[1].TileSize=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.bg[1].TileSize)),
						new Popup.Element("BG1 H Offset", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[1].HOffset=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.bg[1].HOffset)),
						new Popup.Element("BG1 V Offset", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[1].VOffset=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.bg[1].VOffset)),
						new Popup.Element("BG1 Visible", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[1].visible=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.bg[1].visible),
						new Popup.Element("BG1 Window 1", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[1].window1Enabled=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.bg[1].window1Enabled),
						new Popup.Element("BG1 Window 2", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[1].window2Enabled=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.bg[1].window2Enabled),
						new Popup.Element("BG1 Win1 Clip In/Out", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[1].window1ClippedIn=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.bg[1].window1ClippedIn),
						new Popup.Element("BG1 Win2 Clip In/Out", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[1].window2ClippedIn=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.bg[1].window2ClippedIn),
						new Popup.Element("BG2 Base", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[2].Base=Integer.parseInt(value,16); snes.video.updateWholeScreen();}}, Integer.toHexString(snes.ppu.bg[2].Base)),
						new Popup.Element("BG2 Tile Base", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[2].TileBase=Integer.parseInt(value,16); snes.video.updateWholeScreen();}}, Integer.toHexString(snes.ppu.bg[2].TileBase)),
						new Popup.Element("BG2 Size", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[2].Size=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.bg[2].Size)),
						new Popup.Element("BG2 Tile Size", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[2].TileSize=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.bg[2].TileSize)),
						new Popup.Element("BG2 H Offset", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[2].HOffset=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.bg[2].HOffset)),
						new Popup.Element("BG2 V Offset", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[2].VOffset=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.bg[2].VOffset)),
						new Popup.Element("BG2 Visible", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[2].visible=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.bg[2].visible),
						new Popup.Element("BG2 Window 1", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[2].window1Enabled=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.bg[2].window1Enabled),
						new Popup.Element("BG2 Window 2", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[2].window2Enabled=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.bg[2].window2Enabled),
						new Popup.Element("BG2 Win1 Clip In/Out", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[2].window1ClippedIn=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.bg[2].window1ClippedIn),
						new Popup.Element("BG2 Win2 Clip In/Out", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[2].window2ClippedIn=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.bg[2].window2ClippedIn),						
						new Popup.Element("BG3 Base", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[3].Base=Integer.parseInt(value,16); snes.video.updateWholeScreen();}}, Integer.toHexString(snes.ppu.bg[3].Base)),
						new Popup.Element("BG3 Tile Base", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[3].TileBase=Integer.parseInt(value,16); snes.video.updateWholeScreen();}}, Integer.toHexString(snes.ppu.bg[3].TileBase)),
						new Popup.Element("BG3 Size", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[3].Size=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.bg[3].Size)),
						new Popup.Element("BG3 Tile Size", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[3].TileSize=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.bg[3].TileSize)),
						new Popup.Element("BG3 H Offset", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[3].HOffset=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.bg[3].HOffset)),
						new Popup.Element("BG3 V Offset", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[3].VOffset=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.bg[3].VOffset)),
						new Popup.Element("BG3 Visible", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[3].visible=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.bg[3].visible),
						new Popup.Element("BG3 Window 1", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[3].window1Enabled=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.bg[3].window1Enabled),
						new Popup.Element("BG3 Window 2", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[3].window2Enabled=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.bg[3].window2Enabled),
						new Popup.Element("BG3 Win1 Clip In/Out", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[3].window1ClippedIn=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.bg[3].window1ClippedIn),
						new Popup.Element("BG3 Win2 Clip In/Out", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[3].window2ClippedIn=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.bg[3].window2ClippedIn),
						new Popup.Element("BG3 Priority", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.BG3Priority=value.equals("1")?1:0; snes.video.updateWholeScreen();}}, snes.ppu.BG3Priority!=0),
						new Popup.Element("BG4 Base", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[4].Base=Integer.parseInt(value,16); snes.video.updateWholeScreen();}}, Integer.toHexString(snes.ppu.bg[4].Base)),
						new Popup.Element("BG4 Tile Base", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[4].TileBase=Integer.parseInt(value,16); snes.video.updateWholeScreen();}}, Integer.toHexString(snes.ppu.bg[4].TileBase)),
						new Popup.Element("BG4 Size", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[4].Size=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.bg[4].Size)),
						new Popup.Element("BG4 Tile Size", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[4].TileSize=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.bg[4].TileSize)),
						new Popup.Element("BG4 H Offset", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[4].HOffset=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.bg[4].HOffset)),
						new Popup.Element("BG4 V Offset", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[4].VOffset=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.bg[4].VOffset)),
						new Popup.Element("BG4 Visible", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[4].visible=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.bg[4].visible),
						new Popup.Element("BG4 Window 1", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[4].window1Enabled=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.bg[4].window1Enabled),
						new Popup.Element("BG4 Window 2", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[4].window2Enabled=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.bg[4].window2Enabled),
						new Popup.Element("BG4 Win1 Clip In/Out", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[4].window1ClippedIn=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.bg[4].window1ClippedIn),
						new Popup.Element("BG4 Win2 Clip In/Out", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.bg[4].window2ClippedIn=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.bg[4].window2ClippedIn),
						new Popup.Element("Sprites Base", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.spriteAddress=Integer.parseInt(value,16); snes.video.updateWholeScreen();}}, Integer.toHexString(snes.ppu.spriteAddress)),
						new Popup.Element("Sprites Tile Base", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.spriteTileBaseAddress=Integer.parseInt(value,16); snes.video.updateWholeScreen();}}, Integer.toHexString(snes.ppu.spriteTileBaseAddress)),
						new Popup.Element("Sprites Size Select", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.spriteSizeSelect=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.spriteSizeSelect)),
						new Popup.Element("Sprites Visible", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.spritesVisible=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.spritesVisible),
						new Popup.Element("Sprites Window 1", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.spriteWindow1Enabled=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.spriteWindow1Enabled),
						new Popup.Element("Sprites Window 2", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.spriteWindow2Enabled=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.spriteWindow2Enabled),
						new Popup.Element("Sprites Win1 Clip In/Out", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.spriteWindow1ClippedIn=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.spriteWindow1ClippedIn),
						new Popup.Element("Sprites Win2 Clip In/Out", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.spriteWindow2ClippedIn=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.spriteWindow2ClippedIn),
						new Popup.Element("Window 1 Left", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.window1Left=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.window1Left)),
						new Popup.Element("Window 1 Right", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.window1Right=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.window1Right)),
						new Popup.Element("Window 2 Left", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.window2Left=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.window2Left)),
						new Popup.Element("Window 2 Right", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.window2Right=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.window2Right)),
						new Popup.Element("Mode 7 MatrixA", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.matrixA=Integer.parseInt(value,16); snes.video.updateWholeScreen();}}, Integer.toHexString(snes.ppu.matrixA)),
						new Popup.Element("Mode 7 MatrixB", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.matrixB=Integer.parseInt(value,16); snes.video.updateWholeScreen();}}, Integer.toHexString(snes.ppu.matrixB)),
						new Popup.Element("Mode 7 MatrixC", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.matrixC=Integer.parseInt(value,16); snes.video.updateWholeScreen();}}, Integer.toHexString(snes.ppu.matrixC)),
						new Popup.Element("Mode 7 MatrixD", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.matrixD=Integer.parseInt(value,16); snes.video.updateWholeScreen();}}, Integer.toHexString(snes.ppu.matrixD)),
						new Popup.Element("Mode 7 CenterX", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.mode7CenterX=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.mode7CenterX)),
						new Popup.Element("Mode 7 CenterY", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.mode7CenterY=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.mode7CenterY)),
						new Popup.Element("Mode 7 H Offset", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.mode7HOffset=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.mode7HOffset)),
						new Popup.Element("Mode 7 V Offset", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.mode7VOffset=Integer.parseInt(value); snes.video.updateWholeScreen();}}, Integer.toString(snes.ppu.mode7VOffset)),
						new Popup.Element("Mode 7 H Flip", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.mode7HFlip=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.mode7HFlip),
						new Popup.Element("Mode 7 V Flip", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.mode7VFlip=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.mode7VFlip),
						new Popup.Element("Force visible", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.forceVisible=value.equals("1"); snes.video.updateWholeScreen();}}, snes.ppu.forceVisible),
						new Popup.Element("Key State", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.keystate=Integer.parseInt(value,16); }}, Integer.toHexString(snes.ppu.keystate)),
						new Popup.Element("Key read latch", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.keylatch=Integer.parseInt(value); }}, Integer.toString(snes.ppu.keylatch)),
						new Popup.Element("Key read count", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.keyserialcount=Integer.parseInt(value); }}, Integer.toString(snes.ppu.keyserialcount)),
						new Popup.Element("HBeam Latch", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.HBeamPositionLatch=Integer.parseInt(value); }}, Integer.toString(snes.ppu.HBeamPositionLatch)),
						new Popup.Element("VBeam Latch", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.VBeamPositionLatch=Integer.parseInt(value); }}, Integer.toString(snes.ppu.VBeamPositionLatch)),
						new Popup.Element("VBeam IRQ Pos", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.IRQVBeamPosition=Integer.parseInt(value); }}, Integer.toString(snes.ppu.IRQVBeamPosition)),
						new Popup.Element("H Interrupts", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.HInterruptsEnabled=value.equals("1"); }}, snes.ppu.HInterruptsEnabled),
						new Popup.Element("V Interrupts", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.VInterruptsEnabled=value.equals("1"); }}, snes.ppu.VInterruptsEnabled),
						new Popup.Element("NMI", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.NMILine=value.equals("1"); }}, snes.ppu.NMILine),
						new Popup.Element("VMA Next Addr", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.VMAAddress=Integer.parseInt(value,16); }}, Integer.toHexString(snes.ppu.VMAAddress)),
						new Popup.Element("VMA Addr Increment", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.VMAIncrement=Integer.parseInt(value,16); }}, Integer.toHexString(snes.ppu.VMAIncrement)),
						new Popup.Element("WRAM Next Addr", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.WRAM=Integer.parseInt(value,16); }}, Integer.toHexString(snes.ppu.WRAM)),
						new Popup.Element("Palette Next Addr", new Popup.PopupAction() { public void doaction(String value) { snes.ppu.paletteRAMAddress=Integer.parseInt(value,16); }}, Integer.toHexString(snes.ppu.paletteRAMAddress)),
				});
			}
			
			else if (e.getActionCommand().equals("Preferences"))
			{
				new Popup(snes,"Preferences",new Popup.Element[]{
						new Popup.Element("Frames skipped", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.FRAME_SKIP=Integer.parseInt(value)+1;
						}}, ""+(snes.FRAME_SKIP-1)),
						new Popup.Element("Maximum FPS", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.MAX_FPS =Double.parseDouble(value);
						}}, ""+snes.MAX_FPS),
						new Popup.Element("Cycles/Instruction", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.CYCLES_PER_INSTRUCTION=Integer.parseInt(value);
						}}, ""+snes.CYCLES_PER_INSTRUCTION),
						new Popup.Element("CPU cycles/APU cycle", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.APU_INSTRUCTIONS_PER_CPU_INSTRUCTION =Integer.parseInt(value);
						}}, ""+snes.APU_INSTRUCTIONS_PER_CPU_INSTRUCTION),
						new Popup.Element("Sound speed", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.dsp.SPEED_FACTOR =Double.parseDouble(value);
						}}, ""+snes.dsp.SPEED_FACTOR),
						new Popup.Element("Inst/sec measured", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.INSTRUCTIONS_PER_SECOND =Double.parseDouble(value);
						}}, ""+snes.INSTRUCTIONS_PER_SECOND),
						new Popup.Element("Enable APU", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.apuEnabled=value.equals("1");
						}}, snes.apuEnabled),
						new Popup.Element("Enable threads", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.multithreaded=value.equals("1");
						}}, snes.multithreaded),
						new Popup.Element("Enable IRQ", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.IRQEnabled=value.equals("1");
						}}, snes.IRQEnabled),
						new Popup.Element("Mute", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.mute=value.equals("1");
						}}, snes.mute),
						new Popup.Element("Audio filters", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.dsp.applyfilter=value.equals("1");
						}}, snes.dsp.applyfilter),
						new Popup.Element("Cycle accurate", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.cycleAccurate=value.equals("1");
						}}, snes.cycleAccurate),
				});
			}
			else if (e.getActionCommand().equals("Toggle Components"))
			{
				new Popup(snes,"Enable",new Popup.Element[]{
						new Popup.Element("Background 1", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.video.bg[1].toggledOn=value.equals("1");
								snes.video.updateWholeScreen();								
						}}, snes.video.bg[1].toggledOn),
						new Popup.Element("Background 2", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.video.bg[2].toggledOn=value.equals("1");
								snes.video.updateWholeScreen();								
						}}, snes.video.bg[2].toggledOn),
						new Popup.Element("Background 3", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.video.bg[3].toggledOn=value.equals("1");
								snes.video.updateWholeScreen();								
						}}, snes.video.bg[3].toggledOn),
						new Popup.Element("Background 4", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.video.bg[4].toggledOn=value.equals("1");
								snes.video.updateWholeScreen();								
						}}, snes.video.bg[4].toggledOn),
						new Popup.Element("Sprites", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.video.spritesOn=value.equals("1");
								snes.video.updateWholeScreen();								
						}}, snes.video.spritesOn),
						new Popup.Element("Windows", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.video.windowsEnabled=value.equals("1");
								snes.video.updateWholeScreen();				
						}}, snes.video.windowsEnabled),
						new Popup.Element("Force Visible", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.ppu.forceVisible=value.equals("1");
								snes.video.updateWholeScreen();				
						}}, snes.ppu.forceVisible),
						new Popup.Element("Voice 0", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.dsp.voice[0].forceDisable=value.equals("0");
								if(snes.dsp.voice[0].forceDisable) snes.dsp.voice[0].enabled=false;
						}}, !snes.dsp.voice[0].forceDisable),
						new Popup.Element("Voice 1", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.dsp.voice[1].forceDisable=value.equals("0");
								if(snes.dsp.voice[1].forceDisable) snes.dsp.voice[1].enabled=false;
						}}, !snes.dsp.voice[1].forceDisable),
						new Popup.Element("Voice 2", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.dsp.voice[2].forceDisable=value.equals("0");
								if(snes.dsp.voice[2].forceDisable) snes.dsp.voice[2].enabled=false;
						}}, !snes.dsp.voice[2].forceDisable),
						new Popup.Element("Voice 3", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.dsp.voice[3].forceDisable=value.equals("0");
								if(snes.dsp.voice[3].forceDisable) snes.dsp.voice[3].enabled=false;
						}}, !snes.dsp.voice[3].forceDisable),
						new Popup.Element("Voice 4", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.dsp.voice[4].forceDisable=value.equals("0");
								if(snes.dsp.voice[4].forceDisable) snes.dsp.voice[4].enabled=false;
						}}, !snes.dsp.voice[4].forceDisable),
						new Popup.Element("Voice 5", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.dsp.voice[5].forceDisable=value.equals("0");
								if(snes.dsp.voice[5].forceDisable) snes.dsp.voice[5].enabled=false;
						}}, !snes.dsp.voice[5].forceDisable),
						new Popup.Element("Voice 6", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.dsp.voice[6].forceDisable=value.equals("0");
								if(snes.dsp.voice[6].forceDisable) snes.dsp.voice[6].enabled=false;
						}}, !snes.dsp.voice[6].forceDisable),
						new Popup.Element("Voice 7", new Popup.PopupAction() {
							public void doaction(String value) {
								snes.dsp.voice[7].forceDisable=value.equals("0");
								if(snes.dsp.voice[7].forceDisable) snes.dsp.voice[7].enabled=false;
						}}, !snes.dsp.voice[7].forceDisable),
				});
			}
			else if (e.getActionCommand().equals("Save SRAM"))
			{
				if(!snes.dogameboy)
					snes.saveSRAM();
				else
					snes.gameboy.saveSAV();
				settext("SRAM saved");
			}
			else if (e.getActionCommand().equals("Open BGs"))
			{
				snes.video.drawBGs();
			}
			else if (e.getActionCommand().equals("Save SPC"))
			{
				JFileChooser fc = new JFileChooser();
				fc.setCurrentDirectory(new File("."));
				FileNameExtensionFilter filter = new FileNameExtensionFilter("SPC", new String[] {"spc","SPC"});
				fc.setFileFilter(filter);
				fc.addChoosableFileFilter(filter);
				fc.showSaveDialog(null);
				if (fc.getSelectedFile()!=null && fc.getSelectedFile().getAbsolutePath()!=null)
				{
					String spcname=(fc.getSelectedFile().getAbsolutePath());
					settext("Saving SPC...");
					snes.dsp.generateSPCFile(spcname);
					settext("Saved to "+spcname);
				}
			}
			else if (e.getActionCommand().equals("Save 1")||e.getActionCommand().equals("Save 2")||e.getActionCommand().equals("Save 3")||e.getActionCommand().equals("Save 4")||e.getActionCommand().equals("Save 5"))
			{
				settext("Saving state...");
				boolean l=false;
				if(!snes.pauselock.islocked())
				{
					snes.pauselock.lock();
					snes.pauselock.sleepUntilLocked();
				}
				else l=true;
				String name;
				if(!snes.dogameboy)
					name=snes.gamename.substring(0,snes.gamename.length()-4)+"_state"+e.getActionCommand().charAt(e.getActionCommand().length()-1)+".txt";
				else
					name=snes.gamename.substring(0,snes.gamename.length()-3)+"_gbstate"+e.getActionCommand().charAt(e.getActionCommand().length()-1)+".txt";
		
				snes.dumpStateToFile(name);
				if(!l) snes.pauselock.unlock();
				settext("Saved state to "+name);
			}
			else if (e.getActionCommand().equals("Load 1")||e.getActionCommand().equals("Load 2")||e.getActionCommand().equals("Load 3")||e.getActionCommand().equals("Load 4")||e.getActionCommand().equals("Load 5"))
			{
				settext("Loading state...");
				boolean l=false;
				if(!snes.pauselock.islocked())
				{
					snes.pauselock.lock();
					snes.pauselock.sleepUntilLocked();
				}
				else l=true;
				String name;
				if(!snes.dogameboy)
					name=snes.gamename.substring(0,snes.gamename.length()-4)+"_state"+e.getActionCommand().charAt(e.getActionCommand().length()-1)+".txt";
				else
					name=snes.gamename.substring(0,snes.gamename.length()-3)+"_gbstate"+e.getActionCommand().charAt(e.getActionCommand().length()-1)+".txt";					
				snes.loadStateFromFile(name);
				if(!l) snes.pauselock.unlock();
				settext("Loaded state from "+name);
			}
			else if (e.getActionCommand().equals("SPC700 Trace"))
			{
				try {
					spc700trace=new PrintWriter("spc700_trace.txt");
					snes.spc700.doprint=true;
				} catch (FileNotFoundException e1) {
					e1.printStackTrace();
				}
			}
			else if (e.getActionCommand().equals("CPU Trace"))
			{
				try {
					cputrace=new PrintWriter("cpu_trace.txt");
					snes.processor.doprint=true;
				} catch (FileNotFoundException e1) {
					e1.printStackTrace();
				}
			}
			else if (e.getActionCommand().equals("Load SPC"))
			{
				JFileChooser fc = new JFileChooser();
				fc.setCurrentDirectory(new File("."));
				FileNameExtensionFilter filter = new FileNameExtensionFilter("SPC", new String[] {"spc","SPC"});
				fc.setFileFilter(filter);
				fc.addChoosableFileFilter(filter);
				fc.showOpenDialog(null);
				if (fc.getSelectedFile()!=null && fc.getSelectedFile().getAbsolutePath()!=null)
				{
					String spcname=(fc.getSelectedFile().getAbsolutePath());
					if(snes.processor.instructionCount!=0)
					{
						snes.dsp.loadSPCFromFile(spcname);
						settext("Loaded "+spcname);
					}
					else
					{
						snes.processor.reset();
						snes.spc700.reset();
						snes.ppu.ppureset();
						snes.dsp.loadSPCFromFile(spcname);
						settext("Playing "+spcname);
						snes.spc700.audiothread=true;
						new Thread(snes.spc700).start();
					}
				}
			}
			else if (e.getActionCommand().equals("Mute"))
			{
				snes.mute=!snes.mute;
				if(snes.mute)
					settext("Sound off");
				else
					settext("Sound on");
			}
			else if (e.getActionCommand().equals("Reset"))
			{
				settext("Resetting...");
				if(!snes.pauselock.islocked())
				{
					snes.pauselock.lock();
					snes.pauselock.sleepUntilLocked();
				}
				snes.processor.reset();
				snes.ppu.ppureset();
				snes.spc700.reset();
				if(snes.dogameboy) snes.gameboy.reset();
				if(snes.dones) snes.nes.reset();
			}
			
		}
	}
	public void settext(String text)
	{
		statusfield.setText(text);
	}
	public void statusUpdate()
	{
		String text=snes.memory.name.replace(" ", "")+": ";
		if (snes.processor.doprint||snes.spc700.doprint)
			text+=snes.processor.instructionCount+" insts ";
		else
			text+=snes.processor.instructionCount/1000000+" M.insts ";
		text+=snes.frameCount+" frames ";
		try{
		text+=(snes.frameCount-lastFrameCount)*1000 / ((System.currentTimeMillis()-lastRealTime)) + " fps ";
		}catch(ArithmeticException e){}
		text+="PBR:PC "+Integer.toHexString(snes.processor.PBR)+":"+Integer.toHexString(snes.processor.PC);
		settext(text);
		lastRealTime=System.currentTimeMillis();
		lastFrameCount=snes.frameCount;
	}
	
	public class ControllerComponent extends JComponent implements MouseListener, MouseMotionListener
	{
		private static final long serialVersionUID = 1L;

		public boolean up,down,left,right,A,B,X,Y,start,select,L,R;
		public char currentPress;
		
		public ControllerComponent()
		{
			currentPress=0;
			this.addMouseListener(this);
			this.addMouseMotionListener(this);
		}
		
		public void keydown(char k)
		{
			if(k=='u') up=true;
			if(k=='d') down=true;
			if(k=='l') left=true;
			if(k=='r') right=true;
			if(k=='A') A=true;
			if(k=='B') B=true;
			if(k=='X') X=true;
			if(k=='Y') Y=true;
			if(k=='S') select=true;
			if(k=='T') start=true;
			if(k=='L') L=true;
			if(k=='R') R=true;
			repaint();
		}
		
		public void keyup(char k)
		{
			if(k=='u') up=false;
			if(k=='d') down=false;
			if(k=='l') left=false;
			if(k=='r') right=false;
			if(k=='A') A=false;
			if(k=='B') B=false;
			if(k=='X') X=false;
			if(k=='Y') Y=false;
			if(k=='S') select=false;
			if(k=='T') start=false;
			if(k=='L') L=false;
			if(k=='R') R=false;
			repaint();
		}
		
		public boolean keystate(char k)
		{
			if(k=='u') return up;
			if(k=='d') return down;
			if(k=='l') return left;
			if(k=='r') return right;
			if(k=='A') return A;
			if(k=='B') return B;
			if(k=='X') return X;
			if(k=='Y') return Y;
			if(k=='S') return select;
			if(k=='T') return start;
			if(k=='L') return L;
			if(k=='R') return R;
			return false;
		}
		
		public String keyname(char k)
		{
			if(k=='u') return "up";
			if(k=='d') return "down";
			if(k=='l') return "left";
			if(k=='r') return "right";
			if(k=='A') return "A";
			if(k=='B') return "B";
			if(k=='X') return "X";
			if(k=='Y') return "Y";
			if(k=='S') return "select";
			if(k=='T') return "start";
			if(k=='L') return "L";
			if(k=='R') return "R";
			return "";			
		}
		
		public void paintComponent(Graphics g)
		{
			g.setColor(Color.white);
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setColor(Color.black);
			g.drawRect(0, 0, getWidth()-1, getHeight()-1);

			g.setColor(new Color(70,70,70));
			g.fillRect(40,40,20,20);
			if(up) g.setColor(new Color(10,10,10)); else g.setColor(new Color(70,70,70));
			g.fillRect(40,20,20,20);
			if(left) g.setColor(new Color(10,10,10)); else g.setColor(new Color(70,70,70));
			g.fillRect(20,40,20,20);
			if(down) g.setColor(new Color(10,10,10)); else g.setColor(new Color(70,70,70));
			g.fillRect(40,60,20,20);
			if(right) g.setColor(new Color(10,10,10)); else g.setColor(new Color(70,70,70));
			g.fillRect(60,40,20,20);
			if(select) g.setColor(new Color(10,10,10)); else g.setColor(new Color(70,70,70));
			g.fillRoundRect(100, 60, 40, 10, 10, 10);
			if(start) g.setColor(new Color(10,10,10)); else g.setColor(new Color(70,70,70));
			g.fillRoundRect(150, 60, 40, 10, 10, 10);
			if(Y) g.setColor(new Color(0,50,0)); else g.setColor(new Color(0,150,0));
			g.fillOval(220,45,20,20);
			if(X) g.setColor(new Color(0,0,50)); else g.setColor(new Color(0,0,150));
			g.fillOval(250,20,20,20);
			if(B) g.setColor(new Color(50,50,0)); else g.setColor(new Color(150,150,0));
			g.fillOval(250,70,20,20);
			if(A) g.setColor(new Color(50,0,0)); else g.setColor(new Color(150,0,0));
			g.fillOval(280,45,20,20);
			if(L) g.setColor(new Color(10,10,10)); else g.setColor(new Color(70,70,70));
			g.fillRect(20,0,60,5);
			if(R) g.setColor(new Color(10,10,10)); else g.setColor(new Color(70,70,70));
			g.fillRect(230,0,60,5);
		}
		public char getKey(int x, int y)
		{
			if(x>=40 && y>=20 && x<=40+20 && y<=20+20) return 'u';
			if(x>=40 && y>=60 && x<=40+20 && y<=60+20) return 'd';
			if(x>=20 && y>=40 && x<=20+20 && y<=40+20) return 'l';
			if(x>=60 && y>=40 && x<=60+20 && y<=40+20) return 'r';
			if(x>=150 && y>=60 && x<=150+40 && y<=60+10) return 'T';
			if(x>=100 && y>=60 && x<=100+40 && y<=60+10) return 'S';
			if(x>=220 && y>=45 && x<=220+20 && y<=45+20) return 'Y';
			if(x>=250 && y>=20 && x<=250+20 && y<=20+20) return 'X';
			if(x>=250 && y>=70 && x<=250+20 && y<=70+20) return 'B';
			if(x>=280 && y>=45 && x<=280+20 && y<=45+20) return 'A';
			if(x>=20 && y>=0 && x<=20+60 && y<=0+5) return 'L';
			if(x>=230 && y>=0 && x<=230+60 && y<=0+5) return 'R';
			return 0;
		}

		public void mouseDragged(MouseEvent e) {}

		public void mouseMoved(MouseEvent e) 
		{
			char key=getKey(e.getX(),e.getY());
			if(key==0) return;
			String message=""+keyname(key)+" key (mapped to "+KeyEvent.getKeyText(snes.screen.map[key])+")";
			if(snes.memory.name==null) return;
			String text=snes.memory.name.replace(" ", "")+": ";
			settext(text+" "+message);
		}

		public void mouseClicked(MouseEvent e) {
			currentPress=0;
			char key=getKey(e.getX(),e.getY());
			if(key==0) return;
			if(e.getButton()==MouseEvent.BUTTON3)
			{
				if(keystate(key))
				{
					if(!snes.dogameboy && !snes.dones)
						snes.ppu.keyup(key);
					else if (snes.dogameboy)
						snes.gameboy.keyup(key);
					else
						snes.nes.keyup(key);
					keyup(key);
				}
				else
				{
					if(!snes.dogameboy && !snes.dones)
						snes.ppu.keydown(key);
					else if (snes.dogameboy)
						snes.gameboy.keydown(key);
					else
						snes.nes.keydown(key);
					keydown(key);
				}
			}
		}

		public void mousePressed(MouseEvent e) {
			currentPress=0;
			char key=getKey(e.getX(),e.getY());
			if(key==0) return;
			if(e.getButton()==MouseEvent.BUTTON1)
			{
				currentPress=key;
				if(!snes.dogameboy && !snes.dones)
					snes.ppu.keydown(key);
				else if(snes.dogameboy)
					snes.gameboy.keydown(key);
				else
					snes.nes.keydown(key);
				keydown(key);
			}
		}

		public void mouseReleased(MouseEvent e) {
			currentPress=0;
			char key=getKey(e.getX(),e.getY());
			if(key==0) return;
			if(e.getButton()==MouseEvent.BUTTON1)
			{
				if(!snes.dogameboy && !snes.dones)
					snes.ppu.keyup(key);
				else if (snes.dogameboy)
					snes.gameboy.keyup(key);
				else
					snes.nes.keyup(key);
				keyup(key);
			}
		}

		public void mouseEntered(MouseEvent e) {}

		public void mouseExited(MouseEvent e) {
			currentPress=0;
		}
	}
	
	public class ButtonComponent extends JComponent
	{
		private static final long serialVersionUID = 1L;
		public boolean A,B,X,Y,START,SELECT,L,R,ARROWUP,ARROWDOWN,ARROWRIGHT,ARROWLEFT;
		public JButton pause,step,load;
		public ButtonComponent()
		{
			pause=new JButton("Pause");
			pause.setBounds(10,5,140,25);
			pause.setEnabled(false);
			pause.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){
						if(snes.pauselock.islocked())
						{
							step.setEnabled(false);
							pause.setText("Pause");
							snes.pauselock.unlock();
						}
						else
						{
							snes.pauselock.lock();
							pause.setText("Resume");
							step.setEnabled(true);
						}
						snes.screen.requestFocus();
			}});
			step=new JButton("Step");
			step.setBounds(10,35,140,25);
			step.setEnabled(false);
			step.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){
				if(snes.pauselock.islocked())
				{
					snes.singlestepframe=true;
					snes.pauselock.unlock();
				}
				snes.screen.requestFocus();
			}});
			load=new JButton("Load Game");
			load.setBounds(10,65,140,25);
			load.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent e){
				if(!snes.pauselock.islocked())
				{
					snes.pauselock.lock();
					snes.pauselock.sleepUntilLocked();
				}

				JFileChooser fc = new JFileChooser();
				fc.setCurrentDirectory(new File("."));
				FileNameExtensionFilter filter = new FileNameExtensionFilter("Super Nintendo or Gameboy ROM", new String[] {"smc","SMC","SFC","sfc","SPC","spc","GB","gb","nes","NES"});
				fc.setFileFilter(filter);
				fc.addChoosableFileFilter(filter);
				fc.showOpenDialog(null);
				if (fc.getSelectedFile()!=null && fc.getSelectedFile().getAbsolutePath()!=null)
				{
					try
					{
						new FileReader(fc.getSelectedFile().getAbsolutePath());
					}
					catch(IOException ex)
					{
						System.out.println("Error: can't open "+fc.getSelectedFile().getAbsolutePath());
						return;
					}
					
					snes.gamename=(fc.getSelectedFile().getAbsolutePath());
					snes.sramname=snes.gamename.substring(0,snes.gamename.length()-3)+"srm";
					pause.setEnabled(true);
					snes.pauselock.unlock();
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
						settext("Playing audio file "+snes.gamename);
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
			}});
			add(pause);
			add(step);
			add(load);
		}
		public void updateButtons()
		{
			int k=snes.ppu.keystate;
			A=(k&0x80)!=0;
			repaint();
		}
		public void paintComponent(Graphics g)
		{
			g.setColor(Color.white);
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setColor(Color.black);
			g.drawRect(0, 0, getWidth()-1, getHeight()-1);
			
			
		}
	}
}
