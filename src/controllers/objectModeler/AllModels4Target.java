package controllers.objectModeler;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import ann.Utils.Avger;

/**
 *	this class is just for holding all the models trained for a certain target
 */
public class AllModels4Target {

	private ObjectClassModel rando;
	private ObjectClassModel scratch;
	private Map<String,ObjectClassModel> reuse = new HashMap<String,ObjectClassModel>();
	private Map<String,ObjectClassModel> retrained = new HashMap<String,ObjectClassModel>();
	private Map<String,ObjectClassModel> mapped = new HashMap<String,ObjectClassModel>();
	private EnsembleClassModel ensembleModel;
	private EnsembleClassModel ensembleModelRetrained;
	
	public static AllModels4Target create(Collection<ObjectClassModel> srcModels, ObjectClassModel trgModel,
			int srcTurns, int trainTurns) {
		AllModels4Target result = new AllModels4Target();
//		result.createRandoModel(trgModel);
		result.createScratchModel(trgModel);
//		for (ObjectClassModel srcModel : srcModels) {
//			result.createReuseModel(srcModel, trgModel);
//			result.createRetrainModel(srcModel, trgModel);
//			result.createMappedModel(srcModel, trgModel);
//		}
		result.createEnsembleModel(srcModels, result.scratch, srcTurns, trainTurns); // target must be trained already to get confidence
		return result;
	}
	
	public void createRandoModel(ObjectClassModel trgModel) {
		rando = new ObjectClassModel(trgModel, trgModel.getRichMemoryManager());
		rando.initRandom();
	}
	public void createScratchModel(ObjectClassModel trgModel) {
		scratch = new ObjectClassModel(trgModel, trgModel.getRichMemoryManager());
		scratch.train();
	}
	public void createReuseModel(ObjectClassModel srcModel, ObjectClassModel trgModel) {
		String gameIdentifier = "";//Math.random() + "g"; // TODO omg jesus
		reuse.put(gameIdentifier + srcModel.getItype(), Transfer.convertModel(srcModel, trgModel)); // scratch works S
	}
	public void createRetrainModel(ObjectClassModel srcModel, ObjectClassModel trgModel) {
		ObjectClassModel converted = Transfer.convertModel(srcModel, trgModel);
//		converted.train(); TODO turned this off to speed things up
		String gameIdentifier = "";//Math.random() + "g"; // TODO omg jesus
		retrained.put(gameIdentifier + srcModel.getItype(), converted);
	}
	public void createEnsembleModel(Collection<ObjectClassModel> srcModels, ObjectClassModel trgModel,
			int srcTurns, int trainTurns) {
//		ensembleModel = Transfer.createEnsembleModel(srcModels, trgModel, 1,0);// 5, .5);
		ensembleModel = Transfer.createEnsembleModel(srcModels, trgModel, 1, 0, false);// 5, .5);
		ensembleModelRetrained = Transfer.createEnsembleModel(srcModels, trgModel, 10, 0.5, true);// 5, .5);
//		ensembleModel = Transfer.createEnsembleModel(srcModels, trgModel, ((double)srcTurns) / srcModels.size(), trainTurns);
	}
	public void createMappedModel(ObjectClassModel srcModel, ObjectClassModel trgModel) {
		ObjectClassModel mappedModel = Transfer.createMappingModel(srcModel, trgModel);
		mappedModel.train();
		String gameIdentifier = "";//Math.random() + "g"; // TODO omg jesus
		mapped.put(gameIdentifier + srcModel.getItype(), mappedModel);
	}

	public ObjectClassModel getRandoModel() {
		return rando;
	}
	public ObjectClassModel getScratchModel() {
		return scratch;
	}
	public ObjectClassModel getReuseModel(String srcObj) {
		return reuse.get(srcObj);
	}
	public ObjectClassModel getRetrainedModel(String srcObj) {
		return retrained.get(srcObj);
	}
	public EnsembleClassModel getEnsembleModel() {
		return ensembleModel;
	}
	public EnsembleClassModel getEnsembleModelRetrained() {
		return ensembleModelRetrained;
	}
	
	public void reportLL(ObjectClassModel testModel) {
		System.out.println("\n" + testModel.itype + " testmodel");
//		System.out.println("Rando	" + getModelLL(rando, testModel));
//		System.out.println("Scratch	" + getModelLL(scratch, testModel));
		System.out.println("Ensemble" + ensembleModel.getUsedSources() + " 	" + getModelLL(ensembleModel, testModel));

//		Transfer.convertModel(reuse.get("0"), testModel).printTrainingSummary();
//		Transfer.convertModel(reuse.get("1"), testModel).printTrainingSummary();
//		for (ObjectClassModel r : reuse.values()) {
//			System.out.println(r.getItype() + "Reuse	" + getModelLL(r, testModel));
//		}
//		for (ObjectClassModel r : retrained.values()) {
//			System.out.println(r.getItype() + "Retrain	" + getModelLL(r, testModel));
//		}
//		for (ObjectClassModel r : mapped.values()) {
//			System.out.println(r.getItype() + "Mapped	" + getModelLL(r, testModel));
//		}
	}

	public double getModelLL(ObjectClassModel useModel, ObjectClassModel testDataModel) {
		return Transfer.convertModel(useModel, testDataModel).getAccuracy();
	}
	public double getModelLL(EnsembleClassModel useModel, ObjectClassModel testDataModel) {
		return Transfer.convertModel(useModel, testDataModel).getAccuracy();
	}

	public Avger getAvgAccuracy(ObjectClassModel useModel, ObjectClassModel testDataModel) {
		return Transfer.convertModel(useModel, testDataModel).getAvgAccuracy();
	}
	public Avger getAvgAccuracy(EnsembleClassModel useModel, ObjectClassModel testDataModel) {
		return Transfer.convertModel(useModel, testDataModel).getAvgAccuracy();
	}
}
