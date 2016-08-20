package controllers.objectModeler;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map.Entry;

import ann.FFNeuralNetwork;
import ann.TrainingParametrization;
import ann.Utils;
import ann.Utils.Avger;
import controllers.objectModeler.RichMemoryManager.InputsPointer;
import controllers.objectModeler.RichMemoryManager.RichTransition;
import controllers.objectModeler.RichMemoryManager.State;
import modeler.TransitionMemory;
import modularization.WeightPruner;
import reasoner.DiscreteState;

public class ObjectClassModel extends AbstractModeler implements Serializable {
	
	private static final long serialVersionUID = -3133666046610014293L;
	protected String itype;
	protected RichMemoryManager richMemoryManager = new RichMemoryManager();
	
	public ObjectClassModel(TrainingParametrization params, String itype) {
		super(params);
		this.itype = itype;
	}
	
	public ObjectClassModel(ObjectClassModel srcModel, RichMemoryManager richMemoryManager) {
		this(srcModel.getParams(), srcModel.itype);
		this.classifier = srcModel.classifier == null ? null : srcModel.classifier.getCopy();
		this.richMemoryManager = RichMemoryManager.createTransferRMM(richMemoryManager, richMemoryManager);
	}

	public String getItype() {
		return itype;
	}

	public void setItype(String newI) {
		System.err.println("Changing itypes is not a joke.");
		itype = newI;
	}

	public RichMemoryManager getRichMemoryManager() {
		return richMemoryManager;
	}
	
	public double[] convertAndPredict(Collection<NamedObject> inputObjectVector) {
//		System.out.println(this);
		double[] convertedInput = richMemoryManager.convertPrev(inputObjectVector);
		return this.predict(convertedInput);
	}
	
	public void describeDynamics(double thresh) {
		FFNeuralNetwork ann = classifier.getANN();
		double[][] inOutAbsConnWgt = WeightPruner.inOutAbsConnWgt(ann, false);
		Collection<Entry<String, InputsPointer>> prevFields = richMemoryManager.getOrderedRegFieldsMap(State.PREV);
		Collection<Entry<String, InputsPointer>> nextFields = richMemoryManager.getOrderedRegFieldsMap(State.NEXT);
		String[] nextNames = new String[inOutAbsConnWgt[0].length];
		for (Entry<String, InputsPointer> nextField : nextFields) {
			String name = nextField.getKey();
			InputsPointer ip = nextField.getValue();
			for (int i = ip.getK(); i < ip.getK() + ip.getLen(); i++) {
				nextNames[i] = name;
			}
		}
		for (Entry<String, InputsPointer> prevField : prevFields) {
			String prevName = prevField.getKey();
			InputsPointer ip = prevField.getValue();
			if (ip.getK() >= inOutAbsConnWgt.length) break;
			for (int i = ip.getK(); i < ip.getK() + ip.getLen(); i++) {
				double[] toOutWgts = inOutAbsConnWgt[i];
				for (int j = 0; j < toOutWgts.length; j++) {
					double w = toOutWgts[j];
					if (Math.abs(w) > thresh) {
						String nextName = nextNames[j];
						System.out.println(prevName + (i-ip.getK()) + "	" + Utils.round(w, 2) + "	" + nextName);
					}
				}
			}
		}
	}
	
	@Override
	protected void cleanMemory() {
		Collection<TransitionMemory> convertedMemories = richMemoryManager.convertToTransitionMemories();
		int maxD = 0;
		for (TransitionMemory tm : convertedMemories) maxD = Math.max(maxD, tm.getAllVars().length);
		for (TransitionMemory tm : convertedMemories) {
			if (tm.getAllVars().length < maxD) {
				System.out.println("narfy?	" + tm.getAllVars().length + "	<	" + maxD);
			}
			memories.addMemory(tm);
		}
//		System.out.println();
//		richMemoryManager.debug(RichMemoryManager.State.PREV);
//		richMemoryManager.debug(RichMemoryManager.State.NEXT);
	}
	
	public void printTrainingSummary() {
		System.out.println(richMemoryManager.orderedRegFieldsString());
		Collection<TransitionMemory> batch = cleanAndDedup().getInOrder();
		for (TransitionMemory tm : batch) {
			double[] psaa = tm.getPreStateAndAction();
			DiscreteState ds1 = new DiscreteState(tm.getPostState());
			DiscreteState ds1e = new DiscreteState(classifier.getClassifications(psaa));
			boolean correct = ds1.toString().equals(ds1e.toString());
			System.out.println(new DiscreteState(psaa) + " -> " + ds1
					+ " (guessed " + ds1e + (correct ? ")" : "	XXXXXX)"));
		}
	}

	public Avger getAvgAccuracy() {
		Collection<RichTransition> data = richMemoryManager.getRichTransitions();
		Avger result = new Avger();
		for (RichTransition tm : data) {
			double[] exp = convertAndPredict(tm.get(State.PREV).elements.values());
			double[] act = richMemoryManager.convertNext(tm.get(State.NEXT).elements.values());
			boolean correct = compareOutputs(exp, act);
			result.observe(correct ? 1 : 0);
		}
		return result;
	}
	public double getAccuracy() {
		return getAvgAccuracy().getAvg();
	}
	
	@Override
	public double getLogLikelihood() {
		Collection<RichTransition> data = richMemoryManager.getRichTransitions();
		double sumLog = 0;
		for (RichTransition tm : data) {
			double[] exp = convertAndPredict(tm.get(State.PREV).elements.values());
			double[] act = richMemoryManager.convertNext(tm.get(State.NEXT).elements.values());
			sumLog += calcLL(exp, act);
		}
		return sumLog / data.size();
	}
	
	protected double calcLL(double[] exp, double[] act) {
		double ll = 0;
		for (int i = 0; i < act.length; i++) {
			double p = i < exp.length ? exp[i] : 0.5;
			double l = 1 - Math.abs(act[i] - p);
			ll += Math.log(l);
		}
		return ll / act.length;
	}
	
	protected boolean compareOutputs(double[] exp, double[] act) {
		boolean correct = (new DiscreteState(exp)).toString().equals((new DiscreteState(act)).toString());
		return correct;
	}
	
	@Override
	public String toString() {
		return "Object class " + itype;
	}

}
