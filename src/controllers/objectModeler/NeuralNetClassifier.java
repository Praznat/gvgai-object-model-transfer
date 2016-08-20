package controllers.objectModeler;

import java.io.Serializable;
import java.util.Collection;

import ann.ActivationFunction;
import ann.ExperienceReplay;
import ann.FFNeuralNetwork;
import ann.TrainingParametrization;
import modeler.TransitionMemory;

@SuppressWarnings("serial")
public class NeuralNetClassifier implements MultidimensionalClassifier, Serializable {

	private final TrainingParametrization params;
	private FFNeuralNetwork ann;
	
	public NeuralNetClassifier(TrainingParametrization params) {
		this.params = params;
	}
	
	@Override
	public double[] getClassifications(double[] input) {
		if (ann == null) throw new IllegalStateException("ann not initialized");
//		if (ann.getInputNodes().size() != input.length) throw new IllegalStateException("wrong input size");
		FFNeuralNetwork.feedForward(ann.getInputNodes(), input);
		return ann.getOutputActivations();
	}
	
	public void train(ExperienceReplay<TransitionMemory> experience) {
		Collection<TransitionMemory> batch = experience.getBatch(1, true);
		if (batch.isEmpty()) return; // no transitions for this object class (maybe game ended when this was spawned)
		if (ann == null) initAnn(batch);
		int epochs = params.getEpochs();
		double lRate = params.getlRate();
		double mRate = params.getmRate();
		double sRate = params.getsRate();
		double err = 0;
		for (int i = 0; i < epochs; i++) {
			err = 0;
			Collection<TransitionMemory> data = experience.getBatch();
			for (TransitionMemory tm : data) {
				double[] input = tm.getPreStateAndAction();
				FFNeuralNetwork.feedForward(ann.getInputNodes(), input);
				FFNeuralNetwork.backPropagate(ann.getOutputNodes(), lRate, mRate, sRate, tm.getPostState());
				err += FFNeuralNetwork.getError(tm.getPostState(), ann.getOutputNodes());
			}
//			System.out.println(err / experience.getBatch().size());
		}
//		System.out.println("Final error:	" + err / experience.getBatch().size());
	}

	public void initAnn(Collection<TransitionMemory> data) {
		TransitionMemory tm0 = data.iterator().next();
		ann = new FFNeuralNetwork(ActivationFunction.SIGMOID0p5,
				tm0.getPreStateAndAction().length, tm0.getPostState().length, params.getNumHidden());
	}

	public boolean isInitialized() {
		return ann != null;
	}

	public TrainingParametrization getParams() {
		return params;
	}
	
	public FFNeuralNetwork getANN() {
		return ann;
	}
	
	public void setANN(FFNeuralNetwork ann) {
		this.ann = ann;
	}

	public NeuralNetClassifier getCopy() {
		NeuralNetClassifier result = new NeuralNetClassifier(params);
		if (ann != null) {
			result.setANN(ann.getCopy());
		}
		return result;
	}
}
