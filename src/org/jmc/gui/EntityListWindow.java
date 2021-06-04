package org.jmc.gui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;

import org.jmc.BlockInfo;
import org.jmc.BlockTypes;
import org.jmc.EntityTypes;
import org.jmc.Options;
import org.jmc.NBT.NBT_Tag;
import org.jmc.NBT.TAG_Byte;
import org.jmc.NBT.TAG_Compound;
import org.jmc.entities.Entity;
import org.jmc.util.Filesystem;
import org.jmc.util.Log;
import org.jmc.util.Messages;

public class EntityListWindow extends JFrame {
	private static final long serialVersionUID = 1L;

	private JPanel mainPanel = new JPanel();

	private List<Entity> entities = new LinkedList<>();
	private List<CheckListItem> listItems = new LinkedList<>();
	private JButton selectAll = new JButton(Messages.getString("EntityListWindow.SEL_ALL"));
	private JButton deselectAll = new JButton(Messages.getString("EntityListWindow.DESEL_ALL"));
	private JButton toggleselect = new JButton(Messages.getString("EntityListWindow.TOGGLE"));

	private JTextField searchbar = new JTextField();

	private JList<CheckListItem> labelList = new JList<>();
	private JScrollPane scrollList = new JScrollPane(labelList);

	public EntityListWindow() {

		setTitle(Messages.getString("EntityListWindow.TITLE"));
		setSize(525, 425);
		// setResizable(false);

		labelList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
		labelList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
		labelList.setVisibleRowCount(-1);

		labelList.setCellRenderer(new CheckBoxListRenderer());
		labelList.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				selectItem(e.getPoint());
				saveList("CurSelection");
			}
		});

		scrollList.setPreferredSize(new Dimension(500, 300));

		searchbar.setEditable(true);
		searchbar.setColumns(30);
		searchbar.setMaximumSize(new Dimension(Integer.MAX_VALUE, searchbar.getPreferredSize().height));
		searchbar.setText(Messages.getString("EntityListWindow.SEARCH"));
		searchbar.addCaretListener(new CaretListener() {

			@Override
			public void caretUpdate(CaretEvent e) {
				Log.info("Searching for: " + searchbar.getText());
				updateListings();
			}
		});
		
		searchbar.addFocusListener(new FocusListener() {

            @Override
            public void focusLost(FocusEvent e) {
                if(searchbar.getText().isEmpty()) {
                	searchbar.setText(Messages.getString("EntityListWindow.SEARCH"));
                }
            }

            @Override
            public void focusGained(FocusEvent e) {
                if(searchbar.getText().equals(Messages.getString("EntityListWindow.SEARCH"))) {
                	searchbar.setText("");
                }
            }

			
        });

		mainPanel.removeAll();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.PAGE_AXIS));
		mainPanel.add(scrollList);

		JPanel buttonspanel = new JPanel();
		buttonspanel.setLayout(new BoxLayout(buttonspanel, BoxLayout.LINE_AXIS));

		buttonspanel.add(searchbar);
		buttonspanel.add(selectAll);
		buttonspanel.add(deselectAll);
		buttonspanel.add(toggleselect);

		mainPanel.add(buttonspanel);

		JPanel presetButtons = new JPanel();
		presetButtons.setLayout(new BoxLayout(presetButtons, BoxLayout.X_AXIS));
		for (int i = 1; i < 6; i++) {
			final int presetnum = i;
			JPanel tempPanel = new JPanel();
			JButton loadButton = new JButton(Messages.getString("EntityListWindow.LOAD_PRESET") + i);
			JButton saveButton = new JButton(Messages.getString("EntityListWindow.SAVE_PRESET") + i);

			tempPanel.setLayout(new BoxLayout(tempPanel, BoxLayout.Y_AXIS));

			tempPanel.add(loadButton);
			tempPanel.add(saveButton);

			loadButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					loadList("Preset" + presetnum);
					revalidate();
					repaint();
				}
			});

			saveButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					saveList("Preset" + presetnum);
				}
			});

			presetButtons.add(tempPanel);

		}

		mainPanel.add(presetButtons);
		mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		add(mainPanel);

		pack();

		selectAll.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				for (int i = 0; i < labelList.getModel().getSize(); i++) {
					labelList.getModel().getElementAt(i).setSelected(true);
				}
				saveList("CurSelection");
				revalidate();
				repaint();
			}
		});

		deselectAll.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				for (int i = 0; i < labelList.getModel().getSize(); i++) {
					labelList.getModel().getElementAt(i).setSelected(false);
				}
				saveList("CurSelection");
				revalidate();
				repaint();
			}
		});

		toggleselect.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				for (int i = 0; i < labelList.getModel().getSize(); i++) {
					labelList
							.getModel()
							.getElementAt(i)
							.setSelected(
									!labelList.getModel().getElementAt(i)
											.isSelected());
				}
				saveList("CurSelection");
				revalidate();
				repaint();
			}
		});

	}
	DefaultListModel<CheckListItem> dlm = new DefaultListModel<>();
	public void initialize() {
		for (Entity info : EntityTypes.getAll().values()) {
			CheckListItem item = new CheckListItem(info);
			item.selected = true;
			listItems.add(item);
		}
		
		updateListings();
		loadList("CurSelection");
		Log.info("Inisalizing Blocks");
	}

	public void updateListings() {
		dlm.removeAllElements();
		for (CheckListItem item : listItems) {
			if(searchbar.getText().equals(Messages.getString("EntityListWindow.SEARCH")))
				dlm.addElement(item);
			else
				if (((Entity) item.getItem()).getName().toLowerCase()
						.contains(searchbar.getText().toLowerCase())) {
					dlm.addElement(item);
				}
		}
		labelList.setModel(dlm);
		revalidate();
		repaint();
	}

	public void saveList(String group) {

		File confFile = new File(Filesystem.getDatafilesDir(),
				"conf/entityselection.dat");
		
		Log.info("Saving EntityList: " + group);
		Log.info(Filesystem.getDatafilesDir().toString());

		try {
			Options.excludeEntities = getExcludedEntityIds();

			TAG_Compound root;
			if (!confFile.exists()) {
				confFile.createNewFile();
				root = new TAG_Compound("");
			} else {
				FileInputStream in = new FileInputStream(confFile);
				root = (TAG_Compound) NBT_Tag.make(in);
				in.close();
			}

			TAG_Compound presetgroup = (TAG_Compound) root.getElement(group);
			if (presetgroup == null) {
				root.elements.add(new TAG_Compound(group));
				presetgroup = (TAG_Compound) root.getElement(group);
			}

		
			for (int i = 0; i < labelList.getModel().getSize(); i++) {
				CheckListItem clItem = labelList.getModel().getElementAt(i);
				String eName = ((Entity) clItem.getItem()).getName();
				TAG_Byte isSelected = (TAG_Byte) presetgroup.getElement(eName);

				//Set pre-existing value, or create new.
				if (isSelected != null)
					isSelected.value = clItem.isSelected() ? (byte) 1 : (byte) 0;
				else
					presetgroup.elements.add(new TAG_Byte(eName, clItem.isSelected() ? (byte) 1 : (byte) 0));

			}

			root.save(new FileOutputStream(confFile));

		} catch (Exception e) {
			Log.error("Error saving entity selection", e);
		}

	}

	public void loadList(String group) {

		File confFile = new File(Filesystem.getDatafilesDir(),
				"conf/entityselection.dat");

		try {

			TAG_Compound root;
			if (!confFile.exists()) {
				saveList(group);
				loadList(group);
				return;
			} else {
				FileInputStream in = new FileInputStream(confFile);
				root = (TAG_Compound) NBT_Tag.make(in);
				in.close();
			}

			TAG_Compound presetgroup = (TAG_Compound) root.getElement(group);
			if (presetgroup == null) {
				saveList(group);
				loadList(group);
				return;
			}

			for (int i = 0; i < labelList.getModel().getSize(); i++) {

				CheckListItem clItem = labelList.getModel().getElementAt(i);
				String eName = ((Entity) clItem.getItem()).getName();
				TAG_Byte isSelected = (TAG_Byte) presetgroup.getElement(eName);

				if (isSelected != null)
					clItem.setSelected(isSelected.value == 1 ? true : false);
				else
					clItem.setSelected(false);

			}

			Options.excludeEntities = getExcludedEntityIds();
			
		} catch (Exception e) {
			Log.error("Error log entity selection", e);
		}

	}

	private Set<String> getExcludedEntityIds() {
		Set<String> result = new HashSet<String>();
		for(CheckListItem lItem : listItems) {
			if(lItem.isSelected())
				result.add(((Entity)lItem.getItem()).getName());
		}
		return result;
	}

	private void selectItem(Point point) {
		int index = labelList.locationToIndex(point);

		if (index >= 0) {
			CheckListItem clItem = (CheckListItem) labelList.getModel()
					.getElementAt(index);
			clItem.setSelected(!clItem.isSelected());
			labelList.repaint(labelList.getCellBounds(index, index));
		}
	}

	/*
	 * private void toggleSelectedItem(){ int index =
	 * labelList.getSelectedIndex();
	 * 
	 * if (index >= 0){ CheckListItem item =
	 * (CheckListItem)labelList.getModel().getElementAt(index);
	 * item.setSelected(!item.isSelected());
	 * labelList.repaint(labelList.getCellBounds(index, index)); } }
	 */

	private class CheckListItem {
		private Object item;
		private boolean selected;

		public CheckListItem(Object item) {
			this.item = item;
		}

		public Object getItem() {
			return item;
		}

		public boolean isSelected() {
			return selected;
		}

		public void setSelected(boolean isSelected) {
			this.selected = isSelected;
		}

		@Override
		public String toString() {
			return ((Entity) item).getName();
		}
	}

	@SuppressWarnings("serial")
	private class CheckBoxListRenderer extends JCheckBox implements
			ListCellRenderer<Object> {
		@SuppressWarnings("rawtypes")
		public Component getListCellRendererComponent(JList comp, Object value,
				int index, boolean isSelected, boolean hasFocus) {
			setEnabled(comp.isEnabled());
			setSelected(((CheckListItem) value).isSelected());
			setFont(comp.getFont());
			setText(value.toString());

			if (isSelected) {
				setBackground(comp.getSelectionBackground());
				setForeground(comp.getSelectionForeground());
			} else {
				setBackground(comp.getBackground());
				setForeground(comp.getForeground());
			}

			return this;
		}
	}

}