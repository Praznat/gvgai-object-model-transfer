package controllers.objectModeler;

import java.util.Collection;

import ann.FFNeuralNetwork;
import transfer.ReuseNetwork;

public class Transfer {

	/** creates copy of srcModel but holding data from trgModel reorganized by fields from srcModel */
	public static ObjectClassModel convertModel(ObjectClassModel srcModel, ObjectClassModel trgModel) {
		RichMemoryManager transferRMM = RichMemoryManager.createTransferRMM(srcModel.getRichMemoryManager(),
				trgModel.getRichMemoryManager());
		return new ObjectClassModel(srcModel, transferRMM);
	}
	
	/** creates copy of srcModel but holding data from trgModel reorganized by fields from srcModel */
	public static ObjectClassModel convertModel(EnsembleClassModel srcModel, ObjectClassModel trgModel) {
		RichMemoryManager transferRMM = RichMemoryManager.createTransferRMM(srcModel.getRichMemoryManager(),
				trgModel.getRichMemoryManager());
		return new EnsembleClassModel(srcModel, transferRMM);
	}

	// use only single best source (inferior to true ensemble)
	public static EnsembleClassModel createEnsembleModelSelect1(Collection<ObjectClassModel> srcModels, ObjectClassModel trgModel,
			double srcMult, double threshM) {
		EnsembleClassModel ensemble = new EnsembleClassModel(trgModel, trgModel.getRichMemoryManager());
		double bestLL = trgModel.getAccuracy() * threshM;
		ObjectClassModel bestModel = trgModel;
		ensemble.addObjModel(trgModel, 1);
		for (ObjectClassModel srcModel : srcModels) {
			ObjectClassModel convertedModel = convertModel(srcModel, trgModel);
			double reuseLL = convertedModel.getAccuracy();
			if (reuseLL > bestLL) {
				bestLL = reuseLL;
				bestModel = convertedModel;
			}
		}
		if (trgModel != bestModel) ensemble.addObjModel(bestModel, 1);
		return ensemble;
	}
	
	// transfer ensemble as in paper
	public static EnsembleClassModel createEnsembleModel(Collection<ObjectClassModel> srcModels, ObjectClassModel trgModel,
			double srcMult, double threshM, boolean retrain) {
		EnsembleClassModel ensemble = new EnsembleClassModel(trgModel, trgModel.getRichMemoryManager());
		double scratchLL = trgModel.getAccuracy();
		double thresh = threshM * scratchLL; // to avert negative transfer
		ensemble.addObjModel(trgModel, scratchLL);
		srcMult = srcMult / (1-thresh) / srcModels.size();
		for (ObjectClassModel srcModel : srcModels) {
//			if (srcModel.itype.equals("0")) continue; // IGNORE '0' CLASS comment out for normal
			ObjectClassModel convertedModel = convertModel(srcModel, trgModel);
//			convertedModel.initRandom(); // RANDO NET AS SOURCE comment out for normal
			double reuseLL = convertedModel.getAccuracy();
			double w = Math.max(0, reuseLL - thresh) * srcMult; // must come before retraining
			if (retrain) convertedModel.train(); // retrain to reduce negative transfer THIS IS IMPORTANT
			if (w > 0) ensemble.addObjModel(convertedModel, w);
		}
		return ensemble;
	}
	
	public static ObjectClassModel createMappingModel(ObjectClassModel srcModel, ObjectClassModel trgModel) {
		ObjectClassModel convertedModel = convertModel(srcModel, trgModel);
		FFNeuralNetwork ann = convertedModel.classifier.getANN();
		FFNeuralNetwork sandwich = ReuseNetwork.createSandwichedNetwork(ann, true);
		convertedModel.setClassifier(sandwich);
		return convertedModel;
	}
}
