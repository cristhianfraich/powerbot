package org.powerbot.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.WindowConstants;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.powerbot.misc.Tracker;
import org.powerbot.script.PaintListener;
import org.powerbot.script.rt4.ClientContext;
import org.powerbot.script.rt4.Component;
import org.powerbot.script.rt4.Widget;

class RT4WidgetExplorer extends JFrame implements PaintListener {
	private static final long serialVersionUID = 3674322588956559479L;
	private static final Map<BotChrome, RT4WidgetExplorer> instances = new HashMap<BotChrome, RT4WidgetExplorer>();
	private final BotChrome chrome;
	private final JTree tree;
	private final WidgetTreeModel treeModel;
	private JPanel infoArea;
	private JTextField searchBox;
	private Rectangle highlightArea = null;
	private final List<Component> list = new ArrayList<Component>();

	private RT4WidgetExplorer(final BotChrome chrome) {
		super("Widget Explorer");
		this.chrome = chrome;
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(final WindowEvent e) {
				setVisible(false);
				chrome.bot.get().dispatcher.remove(RT4WidgetExplorer.this);
				highlightArea = null;
				dispose();
				instances.remove(chrome);
			}
		});
		treeModel = new WidgetTreeModel();
		treeModel.update("");
		tree = new JTree(treeModel);
		tree.setRootVisible(false);
		tree.setEditable(false);
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		tree.setCellRenderer(new DefaultTreeCellRenderer() {
			private static final long serialVersionUID = 2674122583955569479L;

			@Override
			public java.awt.Component getTreeCellRendererComponent(final JTree tree,
			                                                       final Object value, final boolean selected, final boolean expanded,
			                                                       final boolean leaf, final int row, final boolean hasFocus) {
				super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
				setForeground(Color.black);
				if (value instanceof ComponentWrapper) {
					if (((ComponentWrapper) value).isHit()) {
						setForeground(Color.red);
					}
				}
				return this;
			}
		});
		tree.addTreeSelectionListener(new TreeSelectionListener() {
			public void valueChanged(final TreeSelectionEvent e) {
				try {
					final Object node = tree.getLastSelectedPathComponent();
					if (node == null || node instanceof WidgetWrapper) {
						return;
					}
					infoArea.removeAll();
					Component c = null;
					if (node instanceof ComponentWrapper) {
						c = ((ComponentWrapper) node).get();
						highlightArea = c.getRect();
					}
					if (c == null) {
						return;
					}
					addInfo("Index: ", Integer.toString(c.index()));
					addInfo("Validated: ", Boolean.toString(c.valid()));
					addInfo("Visible: ", Boolean.toString(c.visible()));
					addInfo("Screen point: ", c.screenPoint().toString());
					addInfo("Width: ", Integer.toString(c.width()));
					addInfo("Height: ", Integer.toString(c.height()));
					addInfo("Id: ", Integer.toString(c.id()));
					addInfo("Type: ", Integer.toString(c.type()));
					addInfo("Special type: ", Integer.toString(c.contentType()));
					addInfo("Texture id: ", Integer.toString(c.textureId()));
					addInfo("Text: ", c.text());
					addInfo("Text color: ", Integer.toString(c.textColor()));
					addInfo("Border thickness: ", Integer.toString(c.borderThickness()));
					addInfo("Model id: ", Integer.toString(c.modelId()));
					addInfo("Model type: ", Integer.toString(c.modelType()));
					addInfo("Model zoom: ", Integer.toString(c.modelZoom()));
					addInfo("Parent id: ", Integer.toString(c.parentId()));
					addInfo("Scroll x: ", Integer.toString(c.scrollX()));
					addInfo("Scroll y: ", Integer.toString(c.scrollY()));
					addInfo("Scroll width: ", Integer.toString(c.scrollWidth()));
					addInfo("Scroll height: ", Integer.toString(c.scrollHeight()));
					addInfo("Ids: ", Arrays.toString(c.itemIds()));
					addInfo("Stacks: ", Arrays.toString(c.itemStackSizes()));
					infoArea.validate();
					infoArea.repaint();
				} catch (final Exception ignored) {
				}
			}

			private void addInfo(final String key, final String value) {
				final JPanel row = new JPanel();
				row.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
				row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
				for (final String data : new String[]{key, value}) {
					final JLabel label = new JLabel(data);
					label.setAlignmentY(java.awt.Component.TOP_ALIGNMENT);
					row.add(label);
				}
				infoArea.add(row);
			}
		});
		JScrollPane scrollPane = new JScrollPane(tree);
		scrollPane.setPreferredSize(new Dimension(250, 500));
		add(scrollPane, BorderLayout.WEST);

		infoArea = new JPanel();
		infoArea.setLayout(new BoxLayout(infoArea, BoxLayout.Y_AXIS));
		scrollPane = new JScrollPane(infoArea);
		scrollPane.setPreferredSize(new Dimension(250, 500));
		add(scrollPane, BorderLayout.CENTER);

		final ActionListener actionListener = new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				treeModel.update(searchBox.getText());
				infoArea.removeAll();
				infoArea.validate();
				infoArea.repaint();
			}
		};

		final JPanel toolArea = new JPanel();
		toolArea.setLayout(new FlowLayout(FlowLayout.LEFT));
		toolArea.add(new JLabel("Filter:"));

		searchBox = new JTextField(20);
		searchBox.addActionListener(actionListener);
		toolArea.add(searchBox);

		final JButton updateButton = new JButton("Update");
		updateButton.addActionListener(actionListener);
		toolArea.add(updateButton);
		add(toolArea, BorderLayout.NORTH);

		pack();
		setLocationRelativeTo(getOwner());
		setVisible(false);

		Tracker.getInstance().trackPage("widgetexplorer/", getTitle());
	}

	public static synchronized RT4WidgetExplorer getInstance(final BotChrome chrome) {
		if (!instances.containsKey(chrome)) {
			instances.put(chrome, new RT4WidgetExplorer(chrome));
		}
		return instances.get(chrome);
	}

	public void display() {
		if (isVisible()) {
			chrome.bot.get().dispatcher.remove(this);
			highlightArea = null;
		}
		treeModel.update("");
		chrome.bot.get().dispatcher.add(this);
		setVisible(true);
	}

	public void repaint(final Graphics g) {
		if (highlightArea != null) {
			g.setColor(Color.orange);
			g.drawRect(highlightArea.x, highlightArea.y, highlightArea.width, highlightArea.height);
		}
	}

	private final class WidgetTreeModel implements TreeModel {
		private final Object root = new Object();
		private final List<TreeModelListener> treeModelListeners = new ArrayList<TreeModelListener>();
		private final List<WidgetWrapper> widgetWrappers = new ArrayList<WidgetWrapper>();

		public Object getRoot() {
			return root;
		}

		public Object getChild(final Object parent, final int index) {
			if (parent == root) {
				return widgetWrappers.get(index);
			} else if (parent instanceof WidgetWrapper) {
				return new ComponentWrapper(((WidgetWrapper) parent).get().components()[index]);
			} else if (parent instanceof ComponentWrapper) {
				return new ComponentWrapper(((ComponentWrapper) parent).get().components()[index]);
			}
			return null;
		}

		public int getChildCount(final Object parent) {
			if (parent == root) {
				return widgetWrappers.size();
			} else if (parent instanceof WidgetWrapper) {
				return ((WidgetWrapper) parent).get().components().length;
			} else if (parent instanceof ComponentWrapper) {
				return ((ComponentWrapper) parent).get().components().length;
			}
			return 0;
		}

		public boolean isLeaf(final Object node) {
			return node instanceof ComponentWrapper && ((ComponentWrapper) node).get().components().length == 0;
		}

		public void valueForPathChanged(final TreePath path, final Object newValue) {
		}

		public int getIndexOfChild(final Object parent, final Object child) {
			if (parent == root) {
				return widgetWrappers.indexOf(child);
			} else if (parent instanceof WidgetWrapper) {
				return Arrays.asList(((WidgetWrapper) parent).get().components()).indexOf(((ComponentWrapper) child).get());
			} else if (parent instanceof ComponentWrapper) {
				return Arrays.asList(((ComponentWrapper) parent).get().components()).indexOf(((ComponentWrapper) child).get());
			}
			return -1;
		}

		public void addTreeModelListener(final TreeModelListener l) {
			treeModelListeners.add(l);
		}

		public void removeTreeModelListener(final TreeModelListener l) {
			treeModelListeners.remove(l);
		}

		private void fireTreeStructureChanged(final Object oldRoot) {
			final TreeModelEvent e = new TreeModelEvent(this, new Object[]{oldRoot});
			for (final TreeModelListener tml : treeModelListeners) {
				tml.treeStructureChanged(e);
			}
		}

		public void update(final String search) {
			widgetWrappers.clear();
			final Widget[] loaded;
			for (final Widget widget : loaded = ((ClientContext) chrome.bot.get().ctx()).widgets.array()) {
				children:
				for (final Component component : widget.components()) {
					if (search(component, search)) {
						widgetWrappers.add(new WidgetWrapper(widget));
						break;
					}
					for (final Component widgetSubChild : component.components()) {
						if (search(widgetSubChild, search)) {
							widgetWrappers.add(new WidgetWrapper(widget));
							break children;
						}
					}
				}
			}
			list.clear();
			if (search != null && !search.isEmpty()) {
				for (final Widget widget : loaded) {
					for (final Component child : widget.components()) {
						if (search(child, search)) {
							list.add(child);
						}
						for (final Component child2 : child.components()) {
							if (search(child2, search)) {
								list.add(child2);
							}
						}
					}
				}
			}
			fireTreeStructureChanged(root);
		}

		private boolean search(final Component child, final String string) {
			try {
				return child.text().toLowerCase().contains(string.toLowerCase());
			} catch (final NullPointerException ignored) {
				return false;
			}
		}
	}

	private final class WidgetWrapper {
		private final Widget widget;

		public WidgetWrapper(final Widget widget) {
			this.widget = widget;
		}

		public Widget get() {
			return widget;
		}

		@Override
		public boolean equals(final Object object) {
			return object != null && object instanceof WidgetWrapper && widget.equals(((WidgetWrapper) object).get());
		}

		@Override
		public String toString() {
			return "Widget-" + widget.index();
		}
	}

	private final class ComponentWrapper {
		private final Component component;

		public ComponentWrapper(final Component component) {
			this.component = component;
		}

		public Component get() {
			return component;
		}

		public boolean isHit() {
			return list.contains(component);
		}

		@Override
		public boolean equals(final Object object) {
			return object != null && object instanceof ComponentWrapper && component.equals(((ComponentWrapper) object).get());
		}

		@Override
		public String toString() {
			return "Component-" + component.index();
		}
	}
}