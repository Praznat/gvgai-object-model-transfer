package controllers.objectModeler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import ann.Utils.OneToOneMap;
import ontology.Types.ACTIONS;

public class SimulatedFrame {

	private final Collection<ObjectInstance> objects;
	private final Collection<Vision> visions;
	private final ExistenceData existenceData; // aliens avatar can only shoot if there is no missile out
	private final Map<String, Integer> type2k;

	public SimulatedFrame(Collection<ObjectInstance> objects, Map<String, Integer> type2k) {
		this.objects = objects;
		this.type2k = type2k;
		this.visions = new ArrayList<Vision>();
		for (ObjectInstance obj : objects) {
			visions.add(new Vision(obj));
		}
		this.existenceData = new ExistenceData(objects, type2k);
	}
	
	public SimulatedFrame createProjection(ACTIONS action) {
		Collection<ObjectInstance> futureObjects = new ArrayList<ObjectInstance>();
		for (ObjectInstance obj : objects) {
			// TODO dont forget spawns, counts
			ObjectInstance next = obj.get1StepPrediction(action);
			if (next == null) return null;
			if (next.isAlive()) futureObjects.add(next);
			futureObjects.addAll(next.getSpawnPredictions());
		}
		return new SimulatedFrame(futureObjects, type2k);
	}

	public Collection<ObjectInstance> getObjects() {
		return objects;
	}

	public Collection<Vision> getVisions() {
		return visions;
	}

	public ExistenceData getExistenceData() {
		return existenceData;
	}
}
