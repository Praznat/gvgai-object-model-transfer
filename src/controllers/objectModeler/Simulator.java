package controllers.objectModeler;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

import ontology.Types.ACTIONS;

@SuppressWarnings("serial")
public class Simulator implements ActionListener {

	public static Simulator INSTANCE;
	private JFrame frame;
	private Collection<ObjectInstance> objects = new ArrayList<ObjectInstance>();
	private double w;
	private double h;
	private Map<String, ACTIONS> actionMap = new HashMap<String, ACTIONS>();
	int f;
	private SimulatedFrame simulatedFrame;
	private SimulatedFrame futureFrame;

	private Simulator() {
		frame = new JFrame();
	}

	public void repaint(Collection<ObjectInstance> objects, int w, int h) {
		this.objects = objects;
		this.w = w;
		this.h = h;
		frame.repaint();
		f++;
	}


	public static void createDisplay() {
		if (INSTANCE != null) return;
		INSTANCE = new Simulator();
		INSTANCE.frame.setLayout(new GridLayout(0,2));
		INSTANCE.frame.add(createGridPanel());
		INSTANCE.frame.add(createControlPanel(INSTANCE));
		INSTANCE.frame.setVisible(true);
		INSTANCE.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		INSTANCE.frame.pack();
	}

	private static Component createGridPanel() {
		return new JPanel() {
			@Override
			public void paintComponent(Graphics g) {
				super.paintComponent(g);
				Dimension d = getPreferredSize();
				double wm = d.getWidth() / (INSTANCE.w+1);
				double hm = d.getHeight() / (INSTANCE.h+1);
				try {
				for (ObjectInstance obj : INSTANCE.objects) {
					Vision v = new Vision(obj);
					g.drawString(v.name, (int)Math.round((1+v.c)*wm), (int)Math.round((1+v.r)*hm));
				}} catch (ConcurrentModificationException e) {}
			}
			@Override
			public Dimension getPreferredSize() {
				return new Dimension(640, 280);
			}
		};
	}

	private static Component createControlPanel(Simulator simulator) {
		JPanel result = new JPanel() {
			private double buttHgt = 90;
			private int slowness = 15;
			{
				for (ACTIONS act : ACTIONS.values()) {
					if (act == ACTIONS.ACTION_ESCAPE) continue;
					INSTANCE.actionMap.put(act.name(), act);
					JButton b = new JButton(act.name());
					b.addActionListener(simulator);
					this.add(b);
				}
			}
			@Override
			public void paintComponent(Graphics g) {
				super.paintComponent(g);
				if (INSTANCE.futureFrame == null || INSTANCE.futureFrame.getVisions().isEmpty()) return;
				Dimension d = getPreferredSize();
				double wm = d.getWidth() / INSTANCE.w;
				double hm = (d.getHeight() - buttHgt) / INSTANCE.h;
				int k = (INSTANCE.f/slowness) % 3;
				if (k == 0) {
					for (Vision v : INSTANCE.simulatedFrame.getVisions()) {
						g.drawString(v.name, (int)Math.round(v.c*wm), (int)Math.round(buttHgt + v.r*hm));
					}
				} else if (k == 1) {
					for (Vision v : INSTANCE.futureFrame.getVisions()) {
						g.drawString(v.name, (int)Math.round(v.c*wm), (int)Math.round(buttHgt + v.r*hm));
					}
				} else  {
					g.fillRect(0, 0, (int)d.getWidth(), (int)d.getHeight());
				}
			}
			@Override
			public Dimension getPreferredSize() {
				return new Dimension(640, 280);
			}
		};
		return result;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		ACTIONS action = actionMap.get(e.getActionCommand());
		simulatedFrame = new SimulatedFrame(objects, Player.INSTANCE.getType2K());
		futureFrame = simulatedFrame.createProjection(action);
	}
}
