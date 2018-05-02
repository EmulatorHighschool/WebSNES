package snes;

public class Video 
{
	int[][] screen;
	SNES snes;
	BGMap[] bg;
	Sprite[] sprite;
	boolean spritesOn;
	boolean windowsEnabled;
	
	Lock renderLock;

	public RenderThread renderThread;
	
	public Video(SNES snes)
	{
		this.snes=snes;
		screen=new int[PPU.SNES_WIDTH][PPU.SNES_HEIGHT];
		sprite=new Sprite[128];
		bg=new BGMap[5]; 
		bg[1]=new BGMap(1); bg[2]=new BGMap(2); bg[3]=new BGMap(3); bg[4]=new BGMap(4);
		spritesOn=true;
		windowsEnabled=true;
		
		renderThread=new RenderThread();
		new Thread(renderThread).start();
		renderLock=new Lock();
	}
	public void startScreenRefresh()
	{
		for (int i=0; i<128; i++)
			sprite[i]=new Sprite();		

	}
	public void endScreenRefresh()
	{
		snes.screen.repaint();
/*		if (snes.applet!=null)
			snes.applet.repaint();
		else
			snes.gui.repaint();*/
	}
	public void updateLines(int scanline)
	{
		generateMap(scanline);
	}
	public void updateMode()
	{
		for(int i=1; i<=4; i++)
			bg[i].updateMode();
	}
	public void drawBGs()
	{
		snes.screen.bggui=new BGGUI[5];
		for (int i=0; i<4; i++)
			snes.screen.bggui[i]=new BGGUI(snes,i+1);
		snes.screen.bggui[0].drawIt(bg[1].getAllPixels());
		snes.screen.bggui[1].drawIt(bg[2].getAllPixels());
		snes.screen.bggui[2].drawIt(bg[3].getAllPixels());
		snes.screen.bggui[3].drawIt(bg[4].getAllPixels());
		snes.screen.bggui[4]=new BGGUI(snes,-1);
		int[][] spritepoints=new int[12*16][12*16];
		for(int i=0; i<128; i++)
		{
			Sprite[] sp=new Sprite[128];
			for (int s=0; s<128; s++)
			{
				sp[s]=new Sprite();
				sp[s].initSprite(s);
				int[][] p=sp[s].getAllPoints();
				for (int x=0; x<p.length; x++)
				{
					for(int y=0; y<p[0].length; y++)
					{
						if (p[x][y]!=-1)
							spritepoints[(((s%12)<<4)+x)%192][(((s/12)<<4)+y)%192]=p[x][y];
					}
				}
			}
		}
		snes.screen.bggui[4].drawIt(spritepoints);
	}
	public void updateWholeScreen()
	{
		sprite=new Sprite[128];
		for (int i=0; i<128; i++)
		{
			sprite[i]=new Sprite();
		}
		for(int l=0; l<PPU.SNES_HEIGHT; l++)
			generateMap(l);
		snes.screen.paintImmediately(0, 0, PPU.SNES_WIDTH*SNESGUI.SCALE, PPU.SNES_HEIGHT*SNESGUI.SCALE);
//		snes.screen.repaint();
	}
	/*
	 * p (Priority) 0             1
Drawn first  BG4, o=0      BG4, o=0
   (Behind)  BG3, o=0      BG3, o=0
      .      sprites with sprite priority 0 (%00)
      .      BG4, o=1      BG4, o=1
      .      BG3, o=1      sprite pri. 1
      .      sprite pri. 1    BG2, o=0
      .      BG2, o=0      BG1, o=0
      .      BG1, o=0      BG2, o=1
      .      sprites with sprite priority 2 (%10)
      .      BG2, o=1      BG1, o=1
Drawn last   BG1, o=1      sprite pri. 3
  (in front) sprite pri. 3    BG3, o=1
	 */
	public void generateMap(int line)
	{
		if((snes.ppu.displayBlanked || snes.ppu.brightness==0)&&!snes.ppu.forceVisible) return;

		for(int s=0; s<128; s++)
			sprite[s].initSprite(s);
		
		int y=line;
		if(snes.ppu.mode==7)
		{
			generateMode7(line);
			return;
		}

		for (int i=1; i<=4; i++)
		{
			bg[i].HOffset=snes.ppu.bg[i].HOffset;
			bg[i].VOffset=snes.ppu.bg[i].VOffset;
		}
		
		for (int x=0; x<PPU.SNES_WIDTH; x++)
//			screen[x][y]=0xffffff;
			screen[x][y]=0;		
		int p=0;
		//low priority BG4
		if(bg[4].active && snes.ppu.bg[4].visible && bg[4].toggledOn)
		{
			for (int x=0; x<PPU.SNES_WIDTH; x++)
				{
					if(bg[4].pixelClippedByWindow(x, y)) continue;
					p=bg[4].getPixelColor(x, y, false);
					if (p>=0)
						screen[x][y]=p;
				}
		}
		//low priority BG3
		if(bg[3].active && snes.ppu.bg[3].visible && bg[3].toggledOn)
		{
			for (int x=0; x<PPU.SNES_WIDTH; x++)
				{
					if(bg[3].pixelClippedByWindow(x, y)) continue;
					p=bg[3].getPixelColor(x, y, false);
					if (p>=0)
						screen[x][y]=p;
				}
		}
		if(spritesOn)		
		//0 priority sprites
		if(snes.ppu.spritesVisible)
			for(int s=sprite.length-1; s>=0; s--)
			{
				if(!sprite[s].active || !sprite[s].visibleOnLine(y,0)) continue;
				for(int x=0; x<PPU.SNES_WIDTH; x++)
				{
					if(spritePixelClippedByWindow(x,y)) continue;
					p=sprite[s].getSpritePixel(x, y, 0);
					if(p>=0)
						screen[x][y]=p;
				}
			}
		//high priority BG4
		if(bg[4].active && snes.ppu.bg[4].visible && bg[4].toggledOn)
		{
			for (int x=0; x<PPU.SNES_WIDTH; x++)
				{
					if(bg[4].pixelClippedByWindow(x, y)) continue;
					p=bg[4].getPixelColor(x, y, true);
					if (p>=0)
						screen[x][y]=p;
				}
		}
		//high priority BG3, if BG3 doesn't have general priority
		if(bg[3].active && snes.ppu.BG3Priority==0 && snes.ppu.bg[3].visible && bg[3].toggledOn)
		{
			for (int x=0; x<PPU.SNES_WIDTH; x++)
				{
					if(bg[3].pixelClippedByWindow(x, y)) continue;
					p=bg[3].getPixelColor(x, y, true);
					if (p>=0)
						screen[x][y]=p;
				}
		}
		if(spritesOn)		
		//1 priority sprites
		if(snes.ppu.spritesVisible)
			for(int s=sprite.length-1; s>=0; s--)
			{
				if(!sprite[s].active || !sprite[s].visibleOnLine(y,1)) continue;
				for(int x=0; x<PPU.SNES_WIDTH; x++)
				{
					if(spritePixelClippedByWindow(x,y)) continue;
					p=sprite[s].getSpritePixel(x, y, 1);
					if(p>=0)
						screen[x][y]=p;
				}
			}
		//low priority BG2
		if(bg[2].active && snes.ppu.bg[2].visible && bg[2].toggledOn)
		{
			for (int x=0; x<PPU.SNES_WIDTH; x++)
				{
					if(bg[2].pixelClippedByWindow(x, y)) continue;
					p=bg[2].getPixelColor(x, y, false);
					if (p>=0)
						screen[x][y]=p;
				}
		}
		if(bg[1].active && snes.ppu.bg[1].visible && bg[1].toggledOn)
		{
			for (int x=0; x<PPU.SNES_WIDTH; x++)
				{
					if(bg[1].pixelClippedByWindow(x, y)) continue;
					p=bg[1].getPixelColor(x, y, false);
					if (p>=0)
						screen[x][y]=p;
				}
		}
		if(spritesOn)		
		//2 priority sprites
		if(snes.ppu.spritesVisible)
			for(int s=sprite.length-1; s>=0; s--)
			{
				if(!sprite[s].active || !sprite[s].visibleOnLine(y,2)) continue;
				for(int x=0; x<PPU.SNES_WIDTH; x++)
				{
					if(spritePixelClippedByWindow(x,y)) continue;
					p=sprite[s].getSpritePixel(x, y, 2);
					if(p>=0)
						screen[x][y]=p;
				}
			}

		//high priority BG2
		if(bg[2].active && snes.ppu.bg[2].visible && bg[2].toggledOn)
		{
			for (int x=0; x<PPU.SNES_WIDTH; x++)
				{
					if(bg[2].pixelClippedByWindow(x, y)) continue;
					p=bg[2].getPixelColor(x, y, true);
					if (p>=0)
						screen[x][y]=p;
				}
		}
		if(bg[1].active && snes.ppu.bg[1].visible && bg[1].toggledOn)
		{
			for (int x=0; x<PPU.SNES_WIDTH; x++)
				{
					if(bg[1].pixelClippedByWindow(x, y)) continue;
					p=bg[1].getPixelColor(x, y, true);
					if (p>=0)
						screen[x][y]=p;
				}
		}
		if(spritesOn)		
		//3 priority sprites
		if(snes.ppu.spritesVisible)
			for(int s=sprite.length-1; s>=0; s--)
			{
				if(!sprite[s].active || !sprite[s].visibleOnLine(y,3)) continue;
				for(int x=0; x<PPU.SNES_WIDTH; x++)
				{
					if(spritePixelClippedByWindow(x,y)) continue;
					p=sprite[s].getSpritePixel(x, y, 3);
					if(p>=0)
						screen[x][y]=p;
				}
			}
		
		//high priority BG3, if BG3 has general priority
		if(bg[3].active && snes.ppu.BG3Priority==1 && snes.ppu.bg[3].visible && bg[3].toggledOn)
		{
			for (int x=0; x<PPU.SNES_WIDTH; x++)
				{
//					if(bg[3].pixelClippedByWindow(x, y)) continue;
					p=bg[3].getPixelColor(x, y, true);
					if (p>=0)
						screen[x][y]=p;
				}
		}
		
	}
	boolean spritePixelClippedByWindow(int x, int y)
	{
		if(windowsEnabled && snes.ppu.spriteWindow1Enabled)
		{
			if(x>=snes.ppu.window1Left && x<=snes.ppu.window1Right)
				return snes.ppu.spriteWindow1ClippedIn;
			else
				return !snes.ppu.spriteWindow1ClippedIn;
		}
		if(windowsEnabled && snes.ppu.spriteWindow2Enabled)
		{
			if(x>=snes.ppu.window2Left && x<=snes.ppu.window2Right)
				return snes.ppu.spriteWindow2ClippedIn;
			else
				return !snes.ppu.spriteWindow2ClippedIn;
		}
		
		return false;
	}
	public class BGMap
	{
		int bg;
		int colordepth;
		boolean active;
		PPU.BG BG;
		int HOffset,VOffset;
		boolean toggledOn;
		
		public BGMap(int bg)
		{
			this.bg=bg;
			BG=snes.ppu.bg[bg];
			toggledOn=true;
		}
		public void updateMode()
		{
//			System.out.println("Graphics mode is now "+snes.ppu.mode);
			colordepth=2;
			active=false;
			switch(snes.ppu.mode)
			{
			case 0: 
				colordepth=2;
				active=true;
				break;
			case 1: 
				if(bg==1 || bg==2) colordepth=4; else colordepth=2;
				active=(bg!=4);
				break;
			case 2: 
				colordepth=4;
				active=(bg<=2);
				break;
			case 3: 
				if(bg==1) colordepth=8; else colordepth=4;
				active=(bg<=2);
				break;
			case 4: 
				if(bg==1) colordepth=8; else colordepth=2;
				active=(bg<=2);
				break;
			}
			if(!active) return;			
		}
		
		boolean pixelClippedByWindow(int x, int y)
		{
			if(windowsEnabled && BG.window1Enabled)
			{
				if(x>=snes.ppu.window1Left && x<=snes.ppu.window1Right)
					return BG.window1ClippedIn;
				else
					return !BG.window1ClippedIn;
			}
			if(windowsEnabled && BG.window2Enabled)
			{
				if(x>=snes.ppu.window2Left && x<=snes.ppu.window2Right)
					return BG.window2ClippedIn;
				else
					return !BG.window2ClippedIn;
			}
			
			return false;
		}
		
		int[][] getAllPixels()
		{
			int tilemapwidth=32, tilemapheight=32;
			switch(BG.Size){
			case 1: tilemapwidth=64; break;
			case 2: tilemapheight=64; break;
			case 3: tilemapwidth=tilemapheight=64; break;
			}
			int tilesize=8;
			if(BG.TileSize!=0) tilesize=16;

			int[][] pixels=new int[tilemapwidth*tilesize][tilemapheight*tilesize];
			for (int x=0; x<tilemapwidth*tilesize; x++)
				for(int y=0; y<tilemapheight*tilesize; y++)
					pixels[x][y]=getPixelColor(x,y,false);
			return pixels;
		}
		
		int getPixelColor(int x, int y, boolean needpriority)
		{
			int tilemapwidth=32, tilemapheight=32;
			switch(BG.Size){
			case 1: tilemapwidth=64; break;
			case 2: tilemapheight=64; break;
			case 3: tilemapwidth=tilemapheight=64; break;
			}
			int tilesizebits=3,tilesize=8;
			if(BG.TileSize!=0) { tilesizebits=4; tilesize=16; }
			
			colordepth=2;
			switch(snes.ppu.mode)
			{
			case 0:	colordepth=2;active=true;break;
			case 1: if(bg==1 || bg==2) colordepth=4; else colordepth=2; active=bg!=4;break;
			case 2:	colordepth=4;active=true;	break;
			case 3: if(bg==1) colordepth=8; else colordepth=4;	active=bg<=2;break;
			case 4: if(bg==1) colordepth=8; else colordepth=2;	active=bg<=2;break;
			}
			if(!active) return -1;

			x=x+HOffset;
			y=y+VOffset;

			int w=tilesize*tilemapwidth,h=tilesize*tilemapheight;
			while(x>=w) x-=w;
			while(y>=h) y-=h;

			//which tile map? y/1or2
			int mapnumber_y=y>>(tilesizebits+5);		//y/tilesize/32
			int mapnumber_x=x>>(tilesizebits+5);		//x/tilesize/32

			//which tile within the map? y/32
			int tilenumber_y=(y>>tilesizebits)&31;		//(y/tilesize)%32
			int tilenumber_x=(x>>tilesizebits)&31;		//(x/tilesize)%32

//			int mapentrynumber=(BG.Base+2*(32*32*(tilemapwidth/32)*mapnumber_y+32*32*mapnumber_x+32*tilenumber_y+tilenumber_x))&0xffff;
			int mapentrynumber=(BG.Base+(((tilemapwidth>>5)*(mapnumber_y<<10)+(mapnumber_x<<10)+(tilenumber_y<<5)+tilenumber_x)<<1))&0xffff;
			int mapentry=snes.ppu.VRAM[mapentrynumber]&0xff;
			mapentry|=(snes.ppu.VRAM[mapentrynumber+1]&0xff)<<8;

			int tilenumber=mapentry&0x3ff;
			int palettenumber=(mapentry>>10)&7;
			boolean priority=(mapentry&0x2000)!=0;
			if(needpriority && !priority) return -1;
			
			boolean hflip=(mapentry&0x4000)!=0;
			boolean vflip=(mapentry&0x8000)!=0;
			int address=BG.TileBase+(colordepth<<3)*tilenumber;

			int point=0;
			int row=y&7;
			int col=x&7;
			if(tilesize==16)
			{
				row=((y&15)>>1)&15;
				col=((x&15)>>1)&15;				
			}

			col=hflip? tilesize-col-1 : col;
			row=vflip? tilesize-row-1 : row;
			
			int plane0=snes.ppu.VRAM[(address+(row<<1))&0xffff]&0xff;
			if((plane0&(1<<(7-col)))!=0)
				point|=1;
			int plane1=snes.ppu.VRAM[(address+(row<<1)+1)&0xffff]&0xff;
			if((plane1&(1<<(7-col)))!=0)
				point|=2;
			if(colordepth>=4)
			{
				int plane2=snes.ppu.VRAM[(address+(row<<1)+16)&0xffff]&0xff;
				if((plane2&(1<<(7-col)))!=0)
					point|=4;
				int plane3=snes.ppu.VRAM[(address+(row<<1)+17)&0xffff]&0xff;
				if((plane3&(1<<(7-col)))!=0)
					point|=8;				
			}
			if(colordepth==8)
			{
				int plane4=snes.ppu.VRAM[(address+(row<<1)+32)&0xffff]&0xff;
				if((plane4&(1<<(7-col)))!=0)
					point|=16;
				int plane5=snes.ppu.VRAM[(address+(row<<1)+33)&0xffff]&0xff;
				if((plane5&(1<<(7-col)))!=0)
					point|=32;				
				int plane6=snes.ppu.VRAM[(address+(row<<1)+48)&0xffff]&0xff;
				if((plane6&(1<<(7-col)))!=0)
					point|=64;
				int plane7=snes.ppu.VRAM[(address+(row<<1)+49)&0xffff]&0xff;
				if((plane7&(1<<(7-col)))!=0)
					point|=128;
			}

			if(point==0) return -1;
			
			int paletteaddr=(point+palettenumber*(1<<colordepth))<<1;
			int color=snes.ppu.paletteRAM[paletteaddr&0x1ff]&0xff;
			color|=(snes.ppu.paletteRAM[(paletteaddr+1)&0x1ff]&0xff)<<8;
			int red=(color&31)<<3;
			int green=((color>>5)&31)<<3;
			int blue=((color>>10)&31)<<3;
			
			return (red<<16)|(green<<8)|(blue);
		}
		
	}
	
	private class Sprite
	{
		int palettenumber,priority,tileaddress,size;
		boolean vflip,hflip;
		boolean active;
		
		int startx,starty;
				
		public void initSprite(int spritenumber)
		{
			active=true;
			startx=0;
			int upperspritebits=(snes.ppu.spriteRAM[512+(spritenumber>>2)]>>((spritenumber&3)<<1))&3;
			startx=startx+((upperspritebits&1)!=0?256:0);
			boolean sizeflag=(upperspritebits&2)!=0;
			size=8;
			if(!sizeflag)
			{
				switch(snes.ppu.spriteSizeSelect)
				{
				case 0: case 1: case 2: size=8; break;
				case 3: case 4: size=16; break;
				case 5: size=32; break;
				}
			}
			else
			{
				switch(snes.ppu.spriteSizeSelect)
				{
				case 0: size=16; break;
				case 1: case 3: size=32; break;
				case 2: case 4: case 5: size=64; break;
				}
			}
			if(startx>=256 && startx<(512-size))
			{
				active=false; return;
			}
					
			int spriteRAMAddress=spritenumber<<2;
			startx=snes.ppu.spriteRAM[spriteRAMAddress]&0xff;
			starty=snes.ppu.spriteRAM[spriteRAMAddress+1]&0xff;			
			int modebits=snes.ppu.spriteRAM[spriteRAMAddress+3]&0xff;
			priority=(modebits>>4)&3;
			int tilenumber=snes.ppu.spriteRAM[spriteRAMAddress+2]&0xff;
			tilenumber|=(modebits&1)<<8;
			palettenumber=(modebits>>1)&7;
			hflip=(modebits&0x40)!=0;
			vflip=(modebits&0x80)!=0;
			tileaddress=snes.ppu.spriteTileBaseAddress+(tilenumber<<5);
			if((tilenumber&0x1000)!=0)
				tileaddress=(tileaddress+snes.ppu.spriteTileAddressOffset)&0xffff;
		}
		
		public int[][] getAllPoints()
		{
			int[][] points=new int[size][size];
			for(int x=startx; x<startx+size; x++)
				for(int y=starty; y<starty+size; y++)
					points[x-startx][y-starty]=getSpritePixel(x,y,0);
			return points;
		}
		
		public boolean visibleOnLine(int y, int priorityneeded)
		{
			if(starty>y || starty+size<=y) return false;
			
			if (priority<priorityneeded) return false;
			return true;
			
		}
		
		public int getSpritePixel(int x, int y, int priorityneeded)
		{
			
			if(startx>x || startx+size<=x) return -1;
			if(starty>y || starty+size<=y) return -1;
			
			if (priority<priorityneeded) return -1;

			
			//pixel within sprite
			int spritex=x-startx, spritey=y-starty;
			spritex=hflip? size-1-spritex:spritex;
			spritey=vflip? size-1-spritey:spritey;

			int sx=spritex>>3, sy=spritey>>3;
			int offset=(sx<<5)+(sy<<9);
			
			int row=spritey&7;
			int col=spritex&7;
			int plane0=snes.ppu.VRAM[(offset+tileaddress+(row<<1))&0xffff]&0xff;
			int plane1=snes.ppu.VRAM[(offset+tileaddress+(row<<1)+1)&0xffff]&0xff;
			int plane2=snes.ppu.VRAM[(offset+tileaddress+(row<<1)+16)&0xffff]&0xff;
			int plane3=snes.ppu.VRAM[(offset+tileaddress+(row<<1)+17)&0xffff]&0xff;
			int point=0;
			if((plane0&(1<<(7-col)))!=0)
				point|=1;
			if((plane1&(1<<(7-col)))!=0)
				point|=2;
			if((plane2&(1<<(7-col)))!=0)
				point|=4;
			if((plane3&(1<<(7-col)))!=0)
				point|=8;
		
			if(point==0) return -1;
			
			int paletteaddr=(point+(palettenumber<<4)+128)<<1;
			int color=snes.ppu.paletteRAM[paletteaddr]&0xff;
			color|=(snes.ppu.paletteRAM[paletteaddr+1]&0xff)<<8;
			int red=(color&31)<<3;
			int green=((color>>5)&31)<<3;
			int blue=((color>>10)&31)<<3;
			return (red<<16)|(green<<8)|(blue);
		
		}		
	}

	//Part of this function is taken from SNES9x Tile.CPP
	public void generateMode7(int line)
	{
		int a,b,c,d;
		a=snes.ppu.matrixA; b=snes.ppu.matrixB; c=snes.ppu.matrixC; d=snes.ppu.matrixD;
		int HOffset=(int)((snes.ppu.mode7HOffset<<19)>>19);
		int VOffset=(int)((snes.ppu.mode7VOffset<<19)>>19);
		int centerX=(int)((snes.ppu.mode7CenterX<<19)>>19);
		int centerY=(int)((snes.ppu.mode7CenterY<<19)>>19);
		int yy=VOffset-centerY;
		yy=(((yy) & 0x2000)!=0) ? ((yy) | ~0x3ff) : ((yy) & 0x3ff);
		int xx=HOffset-centerX;
		xx=(((xx) & 0x2000)!=0) ? ((xx) | ~0x3ff) : ((xx) & 0x3ff);
		int starty=line+1;
		if(snes.ppu.mode7VFlip)
			starty=255-(line+1);
		int BB=((b*starty)&~63)+((b*yy)&~63)+(centerX<<8);
		int DD=((d*starty)&~63)+((d*yy)&~63)+(centerY<<8);

		int startx=0;
		int aa=a;
		int cc=c;
		if(snes.ppu.mode7HFlip)
		{
			startx=PPU.SNES_WIDTH-1;
			aa=-aa;
			cc=-cc;
		}
		int AA=a*startx+((a*xx)&~63);
		int CC=a*startx+((c*xx)&~63);
		if(snes.ppu.mode7Repeat==0)
		{
		for (int x=0; x<PPU.SNES_WIDTH; x++, AA+=aa, CC+=cc)
		{
			int X=((AA+BB)>>8)&0x3ff;
			int Y=((CC+DD)>>8)&0x3ff;
			int tileaddr=(snes.ppu.VRAM[((Y&~7)<<5)+((X>>2)&~1)]&0xff)<<7;
			int point=snes.ppu.VRAM[(tileaddr+((Y&7)<<4)+((X&7)<<1))+1]&0xff;
			int color=snes.ppu.paletteRAM[point<<1]&0xff;
			color|=(snes.ppu.paletteRAM[(point<<1)+1]&0xff)<<8;
			int red=(color&31)<<3;
			int green=((color>>5)&31)<<3;
			int blue=((color>>10)&31)<<3;
			screen[x][line]=(red<<16)|(green<<8)|(blue);
		}
		}
		else
		{
		for (int x=0; x<PPU.SNES_WIDTH; x++, AA+=aa, CC+=cc)
		{
			int X=((AA+BB)>>8);
			int Y=((CC+DD)>>8);
			int point,tileaddr;
			if(((X|Y)&~0x3ff)==0)
			{
				tileaddr=(snes.ppu.VRAM[(((Y&~7)<<5)+((X>>2)&~1))&0xffff]&0xff)<<7;
				point=snes.ppu.VRAM[(tileaddr+((Y&7)<<4)+((X&7)<<1))+1]&0xff;
			}
			else if (snes.ppu.mode7Repeat==3)
			{
				point=snes.ppu.VRAM[(((Y&7)<<4)+((X&7)<<1))+1]&0xff;
			}
			else
				continue;
			int color=snes.ppu.paletteRAM[point<<1]&0xff;
			color|=(snes.ppu.paletteRAM[(point<<1)+1]&0xff)<<8;
			int red=(color&31)<<3;
			int green=((color>>5)&31)<<3;
			int blue=((color>>10)&31)<<3;
			screen[x][line]=(red<<16)|(green<<8)|(blue);
		}
		}
		int p;
		int y=line;
		if(snes.ppu.spritesVisible)
			for(int s=sprite.length-1; s>=0; s--)
			{
				if(!sprite[s].active) continue;
				for(int x=sprite[s].startx; x<sprite[s].startx+sprite[s].size; x++)
				{
					if(x<0 || x>=PPU.SNES_WIDTH) continue;
					for(y=sprite[s].starty; y<sprite[s].starty+sprite[s].size; y++)
					{
						if(y<0 || y>=PPU.SNES_HEIGHT) continue;
						p=sprite[s].getSpritePixel(x, y, 0);
						if(p>=0)
							screen[x][y]=p;
					}
				}
			}
	}
	
	public void startScreenRefreshThreaded()
	{
		renderLock.testandsetlock();
		new Thread(new Runnable(){ public void run(){ 
			startScreenRefresh();
			renderLock.unlock();
		}}).start();
	}
	public void endScreenRefreshThreaded()
	{
		renderLock.testandsetlock();
		new Thread(new Runnable(){ public void run(){ 
			endScreenRefresh();
			renderLock.unlock();
		}}).start();
	}
	public void updateLinesThreaded(final int line)
	{
		renderLock.testandsetlock();
		new Thread(new Runnable(){ public void run(){
			updateLines(line);
			renderLock.unlock();
		}}).start();
		
		renderLock.unlock();
	}
	
	public class RenderThread implements Runnable
	{
		int eventtype=0;
		Lock renderLock,readyLock;
		int a,b;
		public RenderThread()
		{
			eventtype=0;
			renderLock=new Lock();
			readyLock=new Lock();
			readyLock.unlock();
			renderLock.lock();
		}
		public void startRefresh()
		{
			readyLock.testlock();
			eventtype=1;
			renderLock.unlock();
		}
		public void render(int line1)
		{
			readyLock.testlock();
			a=line1;
			eventtype=2;
			renderLock.unlock();
		}
		public void endRefresh()
		{
			readyLock.testlock();
			eventtype=3;
			renderLock.unlock();
		}
		public void run()
		{
			while(true)
			{
				renderLock.testlock();
				readyLock.lock();
				if(eventtype==1)
					startScreenRefresh();
				else if (eventtype==2)
					updateLines(a);
				else if (eventtype==3)
					endScreenRefresh();
				renderLock.lock();
				readyLock.unlock();
			}
		}
	}
}
