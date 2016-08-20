package controllers.objectModeler;

import java.util.Collection;
import java.util.Map;

public class ExistenceData {

	private final ObjectClassModel[] classes;
	private final double[] counts;

	public ExistenceData(Collection<ObjectInstance> objects, Map<String, Integer> type2k) {
		int n = type2k.size();
		classes = new ObjectClassModel[n];
		counts = new double[n];
		for (ObjectInstance oi : objects) {
			int k = type2k.get(oi.getItype());
			classes[k] = oi.getClassModel();
			counts[k] = 1;
		}
	}

	public ObjectClassModel[] getClasses() {
		return classes;
	}

	public double[] getCounts() {
		return counts;
	}
}
