package controllers.objectModeler;

import ann.TrainingParametrization;
import modeler.TransitionMemory;

public class TerminationModel extends AbstractModeler {
	
	private static final long serialVersionUID = -2891659626679025927L;

	public TerminationModel(TrainingParametrization params) {
		super(params);
	}
	
	public void observe(ExistenceData countData, boolean terminated) {
		storeMemory(new TransitionMemory(countData.getCounts(), null, new double[] {terminated ? 1 : 0}));
	}

}
