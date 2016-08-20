package controllers.objectModeler;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExplorationModule {
	private final Map<List<Point>, Integer> visited = new HashMap<List<Point>, Integer>();
	private double explorationPoints;
	private final String explorerObjectClass;
	private int age;
	
	public ExplorationModule(double explorationPoints, String explorerObjectClass) {
		this.explorationPoints = explorationPoints;
		this.explorerObjectClass = explorerObjectClass;
	}
	public void visit(SimulatedFrame frame) {
		visit(visitedPlace(frame));
	}
	public void visit(List<Point> places) {
		getVisited().put(places, age++);
	}
	private List<Point> visitedPlace(SimulatedFrame frame) {
		List<Point> places = new ArrayList<Point>();
		for (Vision v : frame.getVisions()) {
			if (v.name.equals(explorerObjectClass)) places.add(new Point(v.c, v.r));
		}
		return places;
	}
	public double getExplorationPoints(SimulatedFrame frame) {
		return getExplorationPoints(visitedPlace(frame));
	}
	public double getExplorationPoints(List<Point> places) {
		if (places.isEmpty()) return -explorationPoints;
		Integer visitAge = getVisited().get(places);
		boolean alreadyVisited = visitAge != null;
		return explorationPoints * (alreadyVisited ? 1 - ((double)visitAge / age) : 1);
//		return explorationPoints * (alreadyVisited ? Math.exp(visitAge - age) : 1);
	}
	public Map<List<Point>, Integer> getVisited() {
		return visited;
	}
}
