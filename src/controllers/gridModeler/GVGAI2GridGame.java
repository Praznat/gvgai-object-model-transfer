package controllers.gridModeler;

import java.awt.Color;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;

import ann.testing.GridGame;
import core.game.Observation;

public class GVGAI2GridGame extends GridGame {
	
	private final Map<Integer, int[][]> grids = new HashMap<Integer, int[][]>();
	private final ArrayList<Integer> objClassOrder = new ArrayList<Integer>();
	private final int gUnit;

	public GVGAI2GridGame(int rows, int cols, Collection<double[]> actions) {
		super(rows, cols);
		this.actionChoices.clear();
		this.actionChoices.addAll(actions);
		setupGameDisplay();
		setupControlPanel("-","U","L","D","R","S");
		gUnit = (int) (20 / Math.sqrt(cols*rows) * 20);
	}
	
	public void observe(ArrayList<Observation>[][] observationGrid) {
		grids.clear();
		int c = 0;
		for (ArrayList<Observation>[] array : observationGrid) {
			int r = 0;
			for (ArrayList<Observation> obs : array) {
				for (Observation o : obs) {
					if (!grids.containsKey(o.itype)) {
						grids.put(o.itype, new int[cols][rows]);
						if (!objClassOrder.contains(o.itype)) {
							objClassOrder.add(o.itype);
							System.out.println("New obj class seen");
							System.out.println(objClassOrder);
						}
					}
					grids.get(o.itype)[c][r] = 1;
				}
				r++;
			}
			c++;
		}
		repaint();
	}

	@Override
	public double[] getState() {
		double[] result = new double[rows * cols * (objClassOrder.size())];
		int k = rows * cols;
		int i = 0;
		for (int o : objClassOrder) {
			int[][] g = grids.get(o);
			for (int r = 0; r < rows; r++) {
				for (int c = 0; c < cols; c++) result[i*k + c + r*cols] = g != null ? g[c][r] : 0;
			}
			i++;
		}
		return result;
	}

	@Override
	public void convertFromState(double[] state) {
		int k = cols * rows;
		int i = 0;
		for (int o : objClassOrder) {
			int[][] g = grids.get(o);
			for (int r = 0; r < rows; r++) {
				for (int c = 0; c < cols; c++) {
					if (g != null) g[c][r] = (int)Math.round(state[i*k + c + r*cols]);
				}
			}
			i++;
		}
	}
	
	@Override
	public void oneTurn() {}
	
	@Override
	public void paintGrid(Graphics g) {
		final int gSub = gUnit/4;
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, gUnit*cols, gUnit*rows);
		try {
			for (Map.Entry<Integer, int[][]> entry : grids.entrySet()) {
				int[][] grid = entry.getValue();
				for (int r = 0; r < rows; r++) {
					for (int c = 0; c < cols; c++) {
						if (grid[c][r] > 0.5) {
							g.setColor(Color.BLACK);
							g.drawRect(c*gUnit, r*gUnit, gUnit, gUnit);
							g.drawString(String.valueOf(entry.getKey()), c*gUnit+gSub*2/3, r*gUnit + (gUnit-gSub)/2);
						}
					}
				}
			}
		} catch (ConcurrentModificationException e) {}
	}
	
}
