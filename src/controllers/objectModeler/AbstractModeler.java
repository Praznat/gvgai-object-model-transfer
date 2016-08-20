package controllers.objectModeler;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import ann.ExperienceReplay;
import ann.FFNeuralNetwork;
import ann.TrainingParametrization;
import ann.Utils;
import ann.Utils.Avger;
import modeler.TransitionMemory;
import reasoner.DiscreteState;

public class AbstractModeler implements Serializable {

	private static final long serialVersionUID = 8108502617612846940L;
	protected final ExperienceReplay<TransitionMemory> memories = new ExperienceReplay<TransitionMemory>(50000);
	protected NeuralNetClassifier classifier;
	private final TrainingParametrization params;
	
	public AbstractModeler(TrainingParametrization params) {
		this.params = params;
		this.classifier = new NeuralNetClassifier(params);
	}
	
	public double[] predict(double[] input) {
		if (!classifier.isInitialized()) return null;
		return classifier.getClassifications(input);
	}

	public void storeMemory(TransitionMemory tm) {
		memories.addMemory(tm);
	}
	
	public void train() {
		classifier.train(cleanAndDedup());
	}
	
	public void initRandom() {
		classifier.initAnn(cleanAndDedup().getBatch(1, false));
	}
	
	public void setClassifier(FFNeuralNetwork ann) {
		classifier = new NeuralNetClassifier(getParams());
		classifier.setANN(ann);
	}
	
	public ExperienceReplay<TransitionMemory> cleanAndDedup() {
		cleanMemory();
		return dedupMemory();
	}
	
	// this is only used for termination conditions (i think)
	protected void cleanMemory() {
		int maxD = 0;
		Collection<TransitionMemory> trainingData = memories.getBatch();
		for (TransitionMemory tm : trainingData) maxD = Math.max(maxD, tm.getAllVars().length);
		for (Iterator<TransitionMemory> iter = trainingData.iterator(); iter.hasNext();) {
			TransitionMemory tm = iter.next();
			if (tm.getAllVars().length < maxD) iter.remove();
		}
	}

	protected ExperienceReplay<TransitionMemory> dedupMemory() {
		Collection<TransitionMemory> trainingData = memories.getBatch();
		return dedupMemory(trainingData);
	}
	
	// speed things up a bit..
	public static ExperienceReplay<TransitionMemory> dedupMemory(Collection<TransitionMemory> transitions) {
		Map<DiscreteState, Avger[]> frequencies = new HashMap<DiscreteState, Avger[]>();
		for (TransitionMemory tm : transitions) {
			DiscreteState inputDS = new DiscreteState(tm.getPreStateAndAction());
			double[] output = tm.getPostState();
			Avger[] avgers = frequencies.get(inputDS);
			if (avgers == null) {
				avgers = new Avger[output.length];
				for (int i = 0; i < avgers.length; i++) avgers[i] = new Avger();
				frequencies.put(inputDS, avgers);
			}
			int n = Math.min(output.length, avgers.length);
			for (int i = 0; i < n; i++) avgers[i].observe(output[i]);
		}
		ExperienceReplay<TransitionMemory> result = new ExperienceReplay<TransitionMemory>(50000);
		for (Entry<DiscreteState, Avger[]> entry : frequencies.entrySet()) {
			Avger[] freqs = entry.getValue();
			double[] output = new double[freqs.length];
			for (int i = 0; i < freqs.length; i++) output[i] = freqs[i].getAvg();
			result.addMemory(new TransitionMemory(entry.getKey().getRawState(), null, output));
		}
		return result;
	}
	
	public double getLogLikelihood() {
		throw new IllegalStateException("shouldnt getLogLikelihood this from AbstractModeler class");
		// though i dont remember why...
	}

	public void diagnose() { diagnose(true); }
	public void diagnose(boolean debug) {
		if (!classifier.isInitialized()) return;
		ArrayList<TransitionMemory> trainingData = new ArrayList<TransitionMemory>(dedupMemory().getBatch());
		double[] errors = {};
		double[][][] stuff = new double[trainingData.size()][2][];
		int t = 0;
		for (TransitionMemory tm : trainingData) {
			double[] target = tm.getPostState();
			if (target.length != errors.length) errors = new double[target.length];
			double[] output = classifier.getClassifications(tm.getPreStateAndAction());
			stuff[t][0] = new double[tm.getPostState().length];
			stuff[t][1] = new double[tm.getPostState().length];
			for (int i = 0; i < output.length; i++) {
				stuff[t][0][i] = target[i];
				stuff[t][1][i] = output[i];
				errors[i] += Math.pow(output[i] - target[i], 2);
			}
			t++;
		}
		if (debug) {
			System.out.println("Target	Output	| ... |	<-	Prev");
			for (t = 0; t < stuff.length; t++) {
				for (int i = 0; i < stuff[t][0].length; i++) {
					System.out.print(Utils.round(stuff[t][0][i],2) + "	" + Utils.round(stuff[t][1][i], 2) + "	|	");
				}
				System.out.println("	<-	" + new DiscreteState(trainingData.get(t).getPreStateAndAction()));
			}
		}
		System.out.print("Errors:	");
		for (int i = 0; i < errors.length; i++) {
			errors[i] /= trainingData.size();
			System.out.print(Utils.round(errors[i], 4) + "	");
		}
		System.out.println();
		System.out.println();
	}
	
	public Collection<TransitionMemory> getMemories() {
		return memories.getBatch();
	}

	public TrainingParametrization getParams() {
		return params;
	}
	
}
