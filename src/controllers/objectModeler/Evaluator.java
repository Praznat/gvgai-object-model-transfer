package controllers.objectModeler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;

import ann.Utils;
import ann.Utils.Avger;
import ontology.Types.ACTIONS;

public class Evaluator {

	private ArrayList<SimulatedFrame> actualFrames = new ArrayList<SimulatedFrame>();
	private ArrayList<SimulatedFrame> predictedFrames = new ArrayList<SimulatedFrame>();

	public void registerFrameAction(SimulatedFrame thisFrame, ACTIONS action) {
		SimulatedFrame nextFrame = thisFrame.createProjection(action);
		if (nextFrame == null) return; // models not trained yet
		if (!predictedFrames.isEmpty()) actualFrames.add(thisFrame); // first actual frame has no corresponding predicted
		predictedFrames.add(nextFrame);
	}
	
	public void clear() {
		actualFrames.clear();
		predictedFrames.clear();
	}

	public void findMistakes(boolean printReports) {
		if (predictedFrames.isEmpty()) {
			System.out.println("No mistakes to find for untrained model");
			return;
		}
		if (predictedFrames.size() != actualFrames.size() + 1) throw new IllegalStateException("#predicted != #actual+1");
		Collection<Mistakes> mistakes = new ArrayList<Mistakes>();
		Map<String, Avger> classErrors = new HashMap<String, Avger>();
		for (int i = 0; i < actualFrames.size(); i++) {
			Mistakes newMistakes = findMistakes(predictedFrames.get(i), actualFrames.get(i));
			mistakes.add(newMistakes);
			newMistakes.countErrors(classErrors);
			if (printReports) newMistakes.report();
		}
		for (Entry<String, Avger> entry : classErrors.entrySet()) {
			System.out.println(entry.getKey() + "	" + entry.getValue().getAvg());
		}
	}
	
	public Mistakes findMistakes(SimulatedFrame actual, SimulatedFrame predicted) {
		Collection<Vision> actualUnaccounted = new HashSet<Vision>(actual.getVisions());
		Collection<Vision> predictedUnaccounted = new HashSet<Vision>(predicted.getVisions());
		Collection<Vision> correctlyAccounted = new HashSet<Vision>(actual.getVisions());
		actualUnaccounted.removeAll(predicted.getVisions());
		predictedUnaccounted.removeAll(actual.getVisions());
		correctlyAccounted.retainAll(predicted.getVisions());
		return new Mistakes(actualUnaccounted, predictedUnaccounted, correctlyAccounted);
	}
	
	private class Mistakes {
		private final Collection<Vision> actualUnaccounted;
		private final Collection<Vision> predictedUnaccounted;
		private final Collection<Vision> correctlyAccounted;

		public Mistakes(Collection<Vision> actualUnaccounted, Collection<Vision> predictedUnaccounted,
				Collection<Vision> correctlyAccounted) {
			this.actualUnaccounted = actualUnaccounted;
			this.predictedUnaccounted = predictedUnaccounted;
			this.correctlyAccounted = correctlyAccounted;
		}
		
		public void countErrors(Map<String, Avger> errMap) {
			for (Vision v : actualUnaccounted) Utils.putOrDefault(errMap, v.name, new Avger()).observe(1);
			for (Vision v : predictedUnaccounted) Utils.putOrDefault(errMap, v.name, new Avger()).observe(1);
			for (Vision v : correctlyAccounted) Utils.putOrDefault(errMap, v.name, new Avger()).observe(0);
		}
		
		public void report() {
			System.out.println((actualUnaccounted.size() + predictedUnaccounted.size()) + " mistakes");
			for (Vision v : actualUnaccounted) System.out.println("Actual unaccounted:	" + v);
			for (Vision v : predictedUnaccounted) System.out.println("Predicted unaccounted:	" + v);
		}
	}
}
