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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

//creates a dialog box for editing parameters
public class Popup 
{
	public static interface PopupAction
	{
		public void doaction(String value);
	}
	public static class Element
	{
		String name;
		PopupAction action;
		JTextField field;
		JCheckBox check;
		boolean fieldOrCheck;
		public Element(String name, PopupAction action, String value)
		{
			this.name=name; this.action=action; fieldOrCheck=true;
			field=new JTextField(value);
		}
		public Element(String name, PopupAction action, boolean value)
		{
			this.name=name; this.action=action; fieldOrCheck=false;
			check=new JCheckBox();
			check.setSelected(value);
		}
		public void doaction()
		{
			String value;
			if(fieldOrCheck)
				value=field.getText();
			else
				value=check.isSelected()? "1":"0";
			action.doaction(value);
		}
	}
	
	SNES snes;
	Element[] element;
	
	public Popup(SNES snes, String title, Element[] element)
	{
		this.snes=snes;
		this.element=element;
		JPanel panel=new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
		for(int i=0; i<element.length; i++)
		{
			JPanel innerpanel=new JPanel();
			
			JLabel label = new JLabel(element[i].name);
			innerpanel.add(label);
			if(element[i].fieldOrCheck)
			{
				element[i].field.setColumns(10);
				innerpanel.add(element[i].field);
			}
			else
			{
				innerpanel.add(element[i].check);
			}
			innerpanel.setBackground(Color.white);
			
			panel.add(innerpanel);
		}
		new PopupDialog(title,panel);
	}
	private void doaction()
	{
		for (int i=0; i<element.length; i++)
			element[i].doaction();
	}
	public class PopupDialog extends JDialog implements ActionListener {
		private static final long serialVersionUID = 1L;

		public PopupDialog(String title, JPanel panel) 
	    {
	    	panel.setBackground(Color.white);
			JScrollPane sp=new JScrollPane(panel);
			JPanel outerpanel=new JPanel();
			outerpanel.setLayout(new BoxLayout(outerpanel, BoxLayout.PAGE_AXIS));
			outerpanel.add(sp);
			getContentPane().add(outerpanel);
			JButton applyButton = new JButton("Apply");
			applyButton.addActionListener(this);
			outerpanel.add(applyButton);	
			JButton closeButton = new JButton("Close");
			closeButton.addActionListener(this);
			outerpanel.add(closeButton);	
			setSize(300,400);
			setVisible(true);
	    }

	    public void actionPerformed(ActionEvent e) 
	    {
	    	if(e.getActionCommand().equals("Close"))
	    	{
	    		doaction();
	    		setVisible(false);
	    		this.dispose();
	    	}
	    	else if(e.getActionCommand().equals("Apply"))
	    	{
	    		doaction();
	    	}
	    }
	    
	}
}
