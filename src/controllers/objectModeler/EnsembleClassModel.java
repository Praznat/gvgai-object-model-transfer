package controllers.objectModeler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import controllers.objectModeler.RichMemoryManager.RichTransition;
import controllers.objectModeler.RichMemoryManager.State;
import modeler.TransitionMemory;

public class EnsembleClassModel extends ObjectClassModel {
	
	private static final long serialVersionUID = -5912945945836319302L;
	private final ArrayList<ObjectClassModel> models = new ArrayList<ObjectClassModel>();
	private final ArrayList<Double> confidences = new ArrayList<Double>();
	private final Map<ObjectClassModel,Double> modelConf = new HashMap<ObjectClassModel,Double>();

	public EnsembleClassModel(ObjectClassModel srcModel, RichMemoryManager richMemoryManager) {
		super(srcModel, richMemoryManager);
		classifier = null;
	}
	public EnsembleClassModel(EnsembleClassModel srcModel, RichMemoryManager richMemoryManager) {
		super(srcModel, richMemoryManager);
		this.models.addAll(srcModel.models);
		this.confidences.addAll(srcModel.confidences);
		for (int i = 0; i < models.size(); i++) modelConf.put(models.get(i), confidences.get(i));
		classifier = null;
	}
	
	
	public void addObjModel(ObjectClassModel model, double confidence) {
//		System.out.println("model data size: " + model.getRichMemoryManager().getRichTransitions().size());
		models.add(model);
		confidences.add(confidence);
	}

	@Override
	public double[] convertAndPredict(Collection<NamedObject> inputObjectVector) {
		double denom = 0;
		for (Double d : confidences) denom += d;
		double[] avgOutputs = null;
		for (int i = 0; i < models.size(); i++) {
			ObjectClassModel model = models.get(i);
			double confidence = confidences.get(i);
			if (confidence == 0) continue;
			double[] outputs = model.convertAndPredict(inputObjectVector);
			if (avgOutputs == null) avgOutputs = new double[outputs.length];
//			if (avgOutputs.length != outputs.length) throw new IllegalStateException("unexpected output length");
			int n = Math.min(outputs.length, avgOutputs.length);
			for (int j = 0; j < n; j++) avgOutputs[j] += outputs[j] * confidence / denom;
		}
		return avgOutputs;
	}
	
	public String getUsedSources() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < models.size(); i++) if (confidences.get(i) > 0) sb.append(models.get(i).itype+";");
		return sb.toString();
	}
	
	public void describe() {
		for (int i = 0; i < confidences.size(); i++) {
			System.out.println(models.get(i) + "	" + confidences.get(i));
		}
	}

}
