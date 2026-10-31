package com.larryfarming.panel;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.FontManager;

/**
 * The settings profile control: a dropdown of profiles over one row of New, Save, Rename
 * and Delete.
 *
 * <p>Picking a profile switches to it, asking first when unsaved changes would be lost; a
 * cancel puts the dropdown back. New and Rename swap the dropdown for a name field with OK:
 * Enter confirms, Escape or leaving the field cancels, and OK stays disabled while the name
 * is unusable. Delete asks first. Save is enabled only while there is something to save,
 * and Save, Rename and Delete never act on a fixed entry: the built-in profile, or no
 * profile after a delete.
 *
 * <p>Refreshes update the dropdown's model in place, and a selection made in code never
 * reaches the switch, so a refresh can neither flicker nor switch profiles.
 */
class ProfilePicker extends JPanel
{
	/** The questions the picker asks before it drops something. */
	interface Confirmations
	{
		/** Whether to switch profiles and lose the unsaved changes. */
		boolean confirmSwitch(Component parent);

		boolean confirmDelete(Component parent, String name);
	}

	/** RuneLite's own way of asking: a modal warning over the panel. */
	static final Confirmations DIALOGS = new Confirmations()
	{
		@Override
		public boolean confirmSwitch(Component parent)
		{
			return ask(parent, "Your unsaved changes will be lost.", "Switch");
		}

		@Override
		public boolean confirmDelete(Component parent, String name)
		{
			return ask(parent, "Delete the " + name + " profile?", "Delete");
		}

		private boolean ask(Component parent, String message, String yes)
		{
			String[] options = {yes, "Cancel"};
			return JOptionPane.showOptionDialog(parent, message, "Larry",
				JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options,
				options[1]) == 0;
		}
	};

	private static final String PICK = "pick";
	private static final String NAMING = "naming";
	private static final int ROW_HEIGHT = 24;

	private final DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
	private final JComboBox<String> dropdown = new SmallDropdown<>(model);
	private final JTextField nameField = new JTextField();
	private final SmallButton confirmName = new SmallButton("OK");
	private final SmallButton create = new SmallButton("New");
	private final SmallButton save = new SmallButton("Save");
	private final SmallButton rename = new SmallButton("Rename");
	private final SmallButton delete = new SmallButton("Delete");
	private final CardLayout cards = new CardLayout();
	private final JPanel top = new JPanel(cards);

	private ProfileActions actions;
	private Confirmations confirmations = DIALOGS;
	private List<String> shownNames = List.of();
	private String active;
	private boolean fixed = true;
	private boolean canSave;
	/** False, or the name field is open: for a new profile, or a rename when renaming. */
	private boolean naming;
	private boolean renaming;
	/** True while the dropdown is changed in code, so its selection events are not a pick. */
	private boolean updating;

	ProfilePicker()
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		setAlignmentX(LEFT_ALIGNMENT);
		setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

		dropdown.addActionListener(e -> picked());

		nameField.setFont(FontManager.getRunescapeSmallFont());
		nameField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				updateConfirm();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				updateConfirm();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				updateConfirm();
			}
		});
		nameField.addActionListener(e -> confirmName());
		nameField.getInputMap(JComponent.WHEN_FOCUSED)
			.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelName");
		nameField.getActionMap().put("cancelName", new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				endNaming();
			}
		});
		// Leaving the field cancels, as Escape does. A temporary loss (another window, a
		// popup) gets the focus back, so it leaves the field open.
		nameField.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				if (naming && !e.isTemporary())
				{
					endNaming();
				}
			}
		});
		// Never takes the focus, so clicking OK can't count as leaving the field.
		confirmName.setFocusable(false);
		confirmName.addActionListener(e -> confirmName());

		JPanel nameRow = new JPanel(new BorderLayout(3, 0));
		nameRow.setOpaque(false);
		nameRow.add(nameField, BorderLayout.CENTER);
		nameRow.add(confirmName, BorderLayout.EAST);

		top.setOpaque(false);
		top.add(dropdown, PICK);
		top.add(nameRow, NAMING);
		add(fixedHeight(top));

		create.addActionListener(e -> startNaming(false));
		save.addActionListener(e -> send(ProfileActions::save));
		rename.addActionListener(e -> startNaming(true));
		delete.addActionListener(e -> deleteActive());

		JPanel buttons = new JPanel(new GridLayout(1, 4, 2, 0));
		buttons.setOpaque(false);
		buttons.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
		buttons.add(create);
		buttons.add(save);
		buttons.add(rename);
		buttons.add(delete);
		add(fixedHeight(buttons));
		updateButtons();
	}

	void setActions(ProfileActions actions)
	{
		this.actions = actions;
	}

	/** Replaces the dialogs with other answers. */
	void setConfirmations(Confirmations confirmations)
	{
		this.confirmations = confirmations;
	}

	/**
	 * Shows the profiles, the built-in one first, with {@code active} selected. Changes the
	 * model only where the names moved, and never reports the selection as a pick.
	 *
	 * @param fixed whether the active entry can't be saved, renamed or deleted
	 * @param unsaved whether the live settings differ from the active profile's saved ones
	 */
	void show(List<String> names, String active, boolean fixed, boolean unsaved)
	{
		boolean activeMoved = !active.equals(this.active);
		this.active = active;
		this.fixed = fixed;
		this.canSave = unsaved && !fixed;
		if (!names.equals(shownNames))
		{
			shownNames = List.copyOf(names);
			updating = true;
			try
			{
				model.removeAllElements();
				for (String name : names)
				{
					model.addElement(name);
				}
			}
			finally
			{
				updating = false;
			}
		}
		selectActive();
		// A rename's profile has changed under the field.
		if (renaming && activeMoved)
		{
			endNaming();
		}
		updateButtons();
	}

	private void selectActive()
	{
		if (active == null || active.equals(model.getSelectedItem()))
		{
			return;
		}
		updating = true;
		try
		{
			model.setSelectedItem(active);
		}
		finally
		{
			updating = false;
		}
	}

	private void picked()
	{
		if (updating || actions == null)
		{
			return;
		}
		Object picked = dropdown.getSelectedItem();
		if (picked == null || picked.equals(active))
		{
			return;
		}
		// The list picks before it closes, so close it first or it hangs over the question.
		dropdown.setPopupVisible(false);
		if (!actions.switchTo((String) picked, () -> confirmations.confirmSwitch(this)))
		{
			selectActive();
		}
	}

	private void deleteActive()
	{
		if (fixed || active == null || actions == null)
		{
			return;
		}
		String name = active;
		if (confirmations.confirmDelete(this, name))
		{
			actions.delete(name);
		}
	}

	private void startNaming(boolean forRename)
	{
		if (forRename && fixed)
		{
			return;
		}
		naming = true;
		renaming = forRename;
		nameField.setText(forRename ? active : "");
		nameField.selectAll();
		cards.show(top, NAMING);
		updateButtons();
		nameField.requestFocusInWindow();
	}

	private void confirmName()
	{
		if (!naming || !confirmName.isEnabled())
		{
			return;
		}
		String from = active;
		boolean wasRename = renaming;
		String to = nameField.getText().strip();
		endNaming();
		send(a ->
		{
			if (wasRename)
			{
				a.rename(from, to);
			}
			else
			{
				a.create(to);
			}
		});
	}

	private void endNaming()
	{
		naming = false;
		renaming = false;
		cards.show(top, PICK);
		updateButtons();
	}

	private void updateConfirm()
	{
		confirmName.setEnabled(naming && actions != null
			&& actions.canName(nameField.getText(), renaming ? active : null));
	}

	private void updateButtons()
	{
		create.setEnabled(!naming);
		save.setEnabled(!naming && canSave);
		rename.setEnabled(!naming && !fixed);
		delete.setEnabled(!naming && !fixed);
		dropdown.setEnabled(!naming);
		updateConfirm();
	}

	private void send(Consumer<ProfileActions> call)
	{
		if (actions != null)
		{
			call.accept(actions);
		}
	}

	private static JComponent fixedHeight(JComponent inner)
	{
		inner.setAlignmentX(LEFT_ALIGNMENT);
		inner.setPreferredSize(new Dimension(0, ROW_HEIGHT));
		inner.setMaximumSize(new Dimension(Short.MAX_VALUE, ROW_HEIGHT));
		return inner;
	}
}
