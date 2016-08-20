package controllers.objectModeler;

import java.awt.Dimension;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import ann.TrainingParametrization;
import ann.Utils;
import ann.Utils.Avger;
import core.ArcadeMachine;
import core.competition.CompetitionParameters;

public class LaunchObjectModeler {
	private static final String GAMES_PATH = "C:/Users/Alex/Documents/workspace/VGDL/gvgai/examples/gridphysics/";
//	private static final String GAMES_PATH = "C:/Users/Alex/Documents/workspace/gvgaiOLD/examples/gridphysics/";
	private static final String SCRATCH = "SCRATCH";
	private static final String ENSEMBLE = "ENSEMBLE";
	private static String[] ALL_GAMES = allGames(.6);
    
    // note: too high learning rate (0.8) screws it up
	public static LaunchObjectModeler INSTANCE = new LaunchObjectModeler();
    public static TrainingParametrization OBJ_PARAMS = new TrainingParametrization(new int[] {20}, 1500, 0.1, 0.1, 0);
    public static TrainingParametrization TERM_PARAMS = new TrainingParametrization(new int[] {}, 500, 0.3, 0.5, 0);
    private static final String AGENT_NAME = "controllers.objectModeler.Player";
    
    private int epochsSeen;
    private int desiredDataSize;
    private enum Mode {SRC_TRAINING, TESTING, TRANSFER_TRAINING, IDLE, FAILED};
    private Mode mode = Mode.IDLE;
    private String gameName;
    private final Map<String,Player> gamePlayers = new HashMap<String,Player>();
	private final int seed = new Random().nextInt();
	private final int levelIdx = 0;

	private String srcGame, trgGame;
	private Map<String, AllModels4Target> allModels; // Map<trgObject, Map<modelName, model>>
	private Map<String, Double> scratchOrigLL;
	private Map<String, Map<String, Pair<Double, Double>>> trainingLL;
	private Map<String, Map<String, Pair<Double, Double>>> testingLL;
	private int srcTrainingEpochs;
	private int trgTrainingEpochs;
	private int trgTestingEpochs;
	private OnEnoughData oedListener = null;
	private double trainingNoise;


	public static void main(String[] args) {
		
		// ACCURACY EXPERIMENT
		testSrcPoolEnsembles(500, 10, 100, 0.0, "sparse");
		
		// EXPLORATION EXPERIMENTS
		for (int i = 0; i < 5; i++) {
			testTransferExploration(new String[] {"chase"}, "labyrinth2", 500, 100, 500, 0, false);
			testTransferExploration(new String[] {"chase"}, "labyrinth2", 500, 100, 500, 0, true);
		}
		
		// SANITY CHECK
		analysis("chase", "escape", 100, 10, 100, 0.0);
	}
	
	public static void testTransferExploration(String[] srcGames, String trgGame, int srcTrainEpochs,
			int trgTrainEpochs, int testEpochs, double noise, boolean isControl) {
		INSTANCE = new LaunchObjectModeler();
		Collection<ObjectClassModel> sourcePool = isControl ? new ArrayList<ObjectClassModel>()
				: createSourcePool(srcGames, srcTrainEpochs);
		trainTransfer(trgGame, sourcePool, srcTrainEpochs, trgTrainEpochs, noise);
		Player.INSTANCE.getWinModel().train();
		Player.INSTANCE.getLoseModel().train();
		
		String avatarK = Player.INSTANCE.getAvatarModel().getItype();
		if (!isControl) {
			EnsembleClassModel ensembleModel = INSTANCE.allModels.get(avatarK).getEnsembleModelRetrained();
			ensembleModel.describe();
			Player.INSTANCE.setTypeModel(avatarK, ensembleModel); // ensembleModel
		}
		Dimension dim = Player.INSTANCE.getWorldDimension();
		long ms = System.currentTimeMillis();
		double explorePts = 0.5;
		
		// calc accuracy
		Player.INSTANCE.clearExperienceHistory();
		Player.INSTANCE.resetScoreTracking();
		Player.INSTANCE.getPolicy().useExploration(explorePts, Player.INSTANCE.getAvatarType());
		INSTANCE.playFrom0(testEpochs);
		if (isControl) Player.INSTANCE.getPolicy().drawExplorationMap("exploreMap" + trgGame + "R" + ms,
				dim.width, dim.height, Player.INSTANCE.getBlockSize());
		int randRange = Player.INSTANCE.getPolicy().getExplorationRange();
		double testingAccuracy = Player.INSTANCE.getAvatarModel().getAccuracy();

		//explore
		Player.INSTANCE.clearExperienceHistory();
		Player.INSTANCE.resetScoreTracking();
		Player.INSTANCE.getPolicy().setJustDoRandom(false);
		Player.INSTANCE.getPolicy().useExploration(explorePts, Player.INSTANCE.getAvatarType());
		INSTANCE.playFrom0(testEpochs);
		int exploreRange = Player.INSTANCE.getPolicy().getExplorationRange();
		String x = trgGame + (isControl ? "S" : "T") + ms;
		Player.INSTANCE.getPolicy().drawExplorationMap("exploreMap"+x, dim.width, dim.height, Player.INSTANCE.getBlockSize());
		try {
			PrintStream resultsOut = new PrintStream(new FileOutputStream(("exploreResults"+x+".txt")));
			String resultS = trgGame + "	" + testingAccuracy + "	" + Player.INSTANCE.getAvgGameTick()
			+ "	" + exploreRange + "	vs random	" + randRange;
			System.out.println(resultS);
			resultsOut.println(resultS);
			resultsOut.close();
		} catch (FileNotFoundException e) {}
		System.out.println("Finished.");
	}
	
	public static void testSrcPoolEnsembles(int srcTrainEpochs, int trgTrainEpochs, int trgTestEpochs, double noise,
			String fileName) {
		int srcPoolSize = 6;
		String[] srcGames = Arrays.copyOfRange(ALL_GAMES, 0, srcPoolSize);
		String[] trgGames = Arrays.copyOfRange(ALL_GAMES, srcPoolSize, ALL_GAMES.length);
		Collection<ObjectClassModel> sourcePool = createSourcePool(srcGames, srcTrainEpochs);
		INSTANCE.mode = Mode.IDLE;
		try {
			PrintStream resultsOut = new PrintStream(new FileOutputStream(fileName + ".txt"));
			for (String trgGame : trgGames) {
				testScratchVsEnsemble(trgGame, sourcePool, srcTrainEpochs, trgTrainEpochs, trgTestEpochs, noise, resultsOut);
			}
			System.out.println("Done with everything!");
			resultsOut.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	private static void trainTransfer(String trgGame, Collection<ObjectClassModel> srcPool,
			int srcEpochs, int trainEpochs, double noise) {
		INSTANCE.gameName = trgGame;
		INSTANCE.oedListener = new OnEnoughData() {
			@Override
			public void doit() {
				System.out.println("TRAINING");
				trainModels();
				INSTANCE.allModels = new HashMap<String, AllModels4Target>();
				Collection<ObjectClassModel> trgModels = INSTANCE.getNonEmptyModels(trgGame);
				for (ObjectClassModel trgModel : trgModels) {
					AllModels4Target models4trg = AllModels4Target.create(srcPool, trgModel,
							srcEpochs, trainEpochs);
					INSTANCE.allModels.put(trgModel.getItype(), models4trg);
					models4trg.reportLL(trgModel);
				}
				INSTANCE.oedListener = null;
			}
		};
		INSTANCE.setNoise(noise);
		INSTANCE.playFrom0(trainEpochs);
		INSTANCE.setNoise(0); // want to see if you predict CORRECT transitions.. no point in predicting random noise
	}
	
	public static void testScratchVsEnsemble(String trgGame, Collection<ObjectClassModel> srcPool,
			int srcEpochs, int trainEpochs, int testEpochs, double noise, PrintStream resultsOut) {
		INSTANCE = new LaunchObjectModeler();
		trainTransfer(trgGame, srcPool, srcEpochs, trainEpochs, noise);
		INSTANCE.oedListener = new OnEnoughData() {
			@Override
			public void doit() {
				System.out.println("TESTING");
				Collection<ObjectClassModel> trgModels = INSTANCE.getNonEmptyModels(trgGame);
				String avatarType = Player.INSTANCE.getAvatarType();
				for (ObjectClassModel trgModel : trgModels) {
					AllModels4Target models = INSTANCE.allModels.get(trgModel.getItype());
					if (models != null) {
						System.out.println("testing " + models.getEnsembleModel().getUsedSources());
						double scratchLL = models.getModelLL(models.getScratchModel(), trgModel);
						double ensembleLL = models.getModelLL(models.getEnsembleModel(), trgModel);
						double ensembleRLL = models.getModelLL(models.getEnsembleModelRetrained(), trgModel);
						String type = trgModel.itype.equals(avatarType) ? "A" : trgModel.itype;
						String desc = models.getEnsembleModel().getUsedSources();
						resultsOut.println(trgGame + "	" + type + "	" + scratchLL
								+ "	" + ensembleLL + "	" + ensembleRLL + "	diff:	" + (ensembleLL - scratchLL)
								+ "	" + (ensembleRLL - scratchLL) + "	" + desc);
					}
				}
				
				INSTANCE.oedListener = null;
			}
		};
		INSTANCE.playFrom0(testEpochs);
		Player.INSTANCE.release();
	}
	
	public static Collection<ObjectClassModel> createSourcePool(String[] srcGames, int trainEpochs) {
		Collection<ObjectClassModel> result = new ArrayList<ObjectClassModel>();
		INSTANCE = new LaunchObjectModeler();
		for (String srcGame : srcGames) {
			INSTANCE.gameName = srcGame;
			INSTANCE.oedListener = new OnEnoughData() {
				@Override
				public void doit() {
					trainModels();
					INSTANCE.oedListener = null;
				}
			};
			INSTANCE.playFrom0(trainEpochs);
			result.addAll(INSTANCE.getInitializedModels(srcGame));
		}
		INSTANCE = new LaunchObjectModeler();
		return result;
	}
	
	// mostly for debugging/sanity-checking
	public static Collection<AnalysisData> analysis(String srcGame, String trgGame,
			int srcTrainingEpochs, int trgTrainingEpochs, int trgTestingEpochs, double trainingNoise) {
		INSTANCE = new LaunchObjectModeler();
		srcGame = "SRC:" + srcGame;
		trgGame = "TRG:" + trgGame;
		INSTANCE.srcGame = srcGame;
		INSTANCE.trgGame = trgGame;
		INSTANCE.srcTrainingEpochs = srcTrainingEpochs;
		INSTANCE.trgTrainingEpochs = trgTrainingEpochs;
		INSTANCE.trgTestingEpochs = trgTestingEpochs;
		INSTANCE.mode = Mode.SRC_TRAINING;
		INSTANCE.gameName = srcGame;
		INSTANCE.playFrom0(srcTrainingEpochs);
		if (INSTANCE.mode == Mode.FAILED) {
			return new ArrayList<AnalysisData>(); // failed
		}
		ObjectClassModel srcAvatarModel = INSTANCE.getCurrentGamePlayer().getAvatarModel();
		srcAvatarModel.printTrainingSummary();
		INSTANCE.mode = Mode.TRANSFER_TRAINING;
		INSTANCE.setNoise(trainingNoise);
		INSTANCE.gameName = trgGame;
		INSTANCE.playFrom0(trgTrainingEpochs);
		INSTANCE.setNoise(0);
		INSTANCE.mode = Mode.TESTING;
		INSTANCE.playFrom0(trgTestingEpochs);
		ObjectClassModel trgAvatarModel = INSTANCE.getCurrentGamePlayer().getAvatarModel();
		ObjectClassModel transferModel = Transfer.convertModel(srcAvatarModel, trgAvatarModel);
		transferModel.printTrainingSummary();
		return produceData(srcGame, trgGame, srcTrainingEpochs, trgTrainingEpochs, trgTestingEpochs);
	}
	
	private void setNoise(double trainingNoise) {
		this.trainingNoise = trainingNoise;
	}
	
	public double getNoise() {
		return trainingNoise;
	}

	// mostly for debugging/sanity-checking
	public static Collection<AnalysisData> produceData(String srcGame, String trgGame,
			int srcTrainingEpochs, int trgTrainingEpochs, int trgTestingEpochs) {
		Collection<AnalysisData> data = new ArrayList<AnalysisData>();
		for (Entry<String, Map<String, Pair<Double, Double>>> trgEntry : INSTANCE.trainingLL.entrySet()) {
			final String trgObj = trgEntry.getKey();
			final Map<String, Pair<Double, Double>> trainingLLs = trgEntry.getValue();
			Map<String, Pair<Double, Double>> testingLLs = INSTANCE.testingLL.get(trgObj);
			final Pair<Double, Double> scratchTrainingD = trainingLLs.get(SCRATCH);
			final Pair<Double, Double> scratchTestingD = testingLLs.get(SCRATCH);
			final double randomTrainingLL = scratchTrainingD.getT();
			final double randomTestingLL = scratchTestingD.getT();
			final double scratchTrainingLL = scratchTrainingD.getU();
			final double scratchTestingLL = scratchTestingD.getU();
			final double ensembleTestingLL = testingLLs.get(ENSEMBLE).getT();
			for (Entry<String, Pair<Double, Double>> srcEntry : trainingLLs.entrySet()) {
				final String srcObj = srcEntry.getKey();
				if (srcObj.equals(SCRATCH) || srcObj.equals(ENSEMBLE)) continue;
				final Pair<Double, Double> transferTrainingD = srcEntry.getValue();
				final Pair<Double, Double> transferTestingD = testingLLs.get(srcObj);
				final double transferTrainingLL = transferTrainingD.getT();
				final double transferTestingLL = transferTestingD.getT();
				final double transferReTrainingLL = transferTrainingD.getU();
				final double transferReTestingLL = transferTestingD.getU();
				final double scratchOriginalLL = INSTANCE.scratchOrigLL.get(srcObj);
				AnalysisData a = new AnalysisData(srcGame, trgGame, srcObj, trgObj, srcTrainingEpochs, trgTrainingEpochs,
						trgTestingEpochs, scratchOriginalLL, randomTrainingLL, scratchTrainingLL, transferTrainingLL,
						transferReTrainingLL, randomTestingLL, scratchTestingLL, transferTestingLL, transferReTestingLL,
						ensembleTestingLL);
				data.add(a);
			}
		}
		return data;
	}
	
	public void onOneEpoch() {
		if (++epochsSeen >= desiredDataSize) onEnoughData();
	}
	private void onEnoughData() {
		gamePlayers.put(gameName, Player.INSTANCE);
		if (oedListener != null) {
			oedListener.doit();
			return;
		}
		if (mode == Mode.SRC_TRAINING) {
			System.out.println("SOURCE TRAINING " + gameName);
			// train source
			trainModels();
			scratchOrigLL = new HashMap<String, Double>();
			Collection<ObjectClassModel> srcModels = getInitializedModels(srcGame);
			if (srcModels.isEmpty()) {
				System.out.println("FAILING");
				mode = Mode.FAILED; // wtf
			}
			for (ObjectClassModel model : srcModels) scratchOrigLL.put(model.getItype(), model.getAccuracy());
//			gamePlayers.get(srcGame).getType2Model().get("6").printTrainingSummary();
		}
		if (mode == Mode.TRANSFER_TRAINING) {
			Collection<ObjectClassModel> srcModels = getInitializedModels(srcGame);
			Collection<ObjectClassModel> trgModels = getNonEmptyModels(trgGame);
			allModels = new HashMap<String, AllModels4Target>();
			for (ObjectClassModel trgModel : trgModels) {
				AllModels4Target models4trg = AllModels4Target.create(srcModels, trgModel,
						srcTrainingEpochs, trgTrainingEpochs);
				allModels.put(trgModel.getItype(), models4trg);
			}
			System.out.println("TRAINING");
			trainingLL = calcAccuracy(); // should be "measure performance"
		}
		if (mode == Mode.TESTING) {
			// dont train scratch

			System.out.println("TESTING");
			testingLL = calcAccuracy();
			
//			trgPlayer.getEvaluator().findMistakes(false);
//			trgPlayer.getEvaluator().clear();
		}
		mode = Mode.IDLE;
	}
	
	public Collection<ObjectClassModel> getNonEmptyModels(String gameName) {
		Collection<ObjectClassModel> result = new ArrayList<ObjectClassModel>();
		Collection<ObjectClassModel> models = gamePlayers.get(gameName).getType2Model().values();
		for (ObjectClassModel m : models)
			if (!m.getRichMemoryManager().getRichTransitions().isEmpty()) result.add(m);
		return result;
	}
	public Collection<ObjectClassModel> getInitializedModels(String gameName) {
		Collection<ObjectClassModel> result = new ArrayList<ObjectClassModel>();
		Collection<ObjectClassModel> models = gamePlayers.get(gameName).getType2Model().values();
		for (ObjectClassModel m : models)
			if (m.classifier.isInitialized()) result.add(m);
		return result;
	}
	
	public Map<String, Map<String, Pair<Double, Double>>> calcAccuracy() {
		Map<String, Map<String, Pair<Double, Double>>> result = new HashMap<String, Map<String, Pair<Double, Double>>>();
		Collection<ObjectClassModel> srcModels = getInitializedModels(srcGame);
		Collection<ObjectClassModel> trgModels = getNonEmptyModels(trgGame);
		for (ObjectClassModel trgModel : trgModels) {
			String t = trgModel.getItype();
			Map<String, Pair<Double, Double>> forTrg = new HashMap<String, Pair<Double, Double>>();
			AllModels4Target models = allModels.get(t);
			if (models == null) continue; // this object was seen in testing but not in training
			double randoAcc = Transfer.convertModel(models.getRandoModel(), trgModel).getAccuracy();
			double scratchAcc = Transfer.convertModel(models.getScratchModel(), trgModel).getAccuracy();
			forTrg.put(SCRATCH, new Pair<Double, Double>(randoAcc, scratchAcc));
			System.out.println(t + "	RANDO/SCRATCH	" + randoAcc + "	" + scratchAcc);
			for (ObjectClassModel srcModel : srcModels) {
				String s = srcModel.getItype();
				double reuseAcc = Transfer.convertModel(models.getReuseModel(s), trgModel).getAccuracy();
				double retrainedAcc = Transfer.convertModel(models.getRetrainedModel(s), trgModel).getAccuracy();
				forTrg.put(s, new Pair<Double, Double>(reuseAcc, retrainedAcc));
				System.out.println(s + "-" + t + "	TRANSFER	" + reuseAcc + "	" + retrainedAcc);
			}
			EnsembleClassModel ensemble = models.getEnsembleModel();
			double ensembleAcc = Transfer.convertModel(ensemble, trgModel).getAccuracy();
			forTrg.put(ENSEMBLE, new Pair<Double, Double>(ensembleAcc, ensembleAcc));
			System.out.println(t + "	ENSEMBLE"+ensemble.getUsedSources()+"	" + ensembleAcc);
			result.put(t, forTrg);
		}
		return result;
	}
	
	private void playFrom0(int desiredDataSize)  {
		epochsSeen = 0;
		this.desiredDataSize = desiredDataSize;

		while (epochsSeen < desiredDataSize) {
			CompetitionParameters.MAX_TIMESTEPS = desiredDataSize - epochsSeen;
			ArcadeMachine.runOneGame(gameF(gameName), gameL(gameName), true, AGENT_NAME, null, seed);
			gamePlayers.put(gameName, Player.INSTANCE);
			Player.INSTANCE.onGameOver();
		}
	}
	
	private void trainThenTest(String trainGame, String testGame, int trainingSize, int testingSize) {
		gamePlayers.clear();
		this.gameName = trainGame;
		this.mode = null;
		System.out.println("TRAINING");
		playFrom0(trainingSize);
		System.out.println("LEARNING");
		trainModels();
//		Player.INSTANCE.getAvatarModel().printTrainingSummary();

		System.out.println("IN-SAMPLE TESTING");
		Player.INSTANCE.resetEvaluator();
		playFrom0(testingSize);
		Player.INSTANCE.getEvaluator().findMistakes(true);
		Map<String, ObjectClassModel> models = Player.INSTANCE.getType2Model();
		Map<String, Integer> t2k = Player.INSTANCE.getType2K();
		
		System.out.println("TESTING");
		this.gameName = testGame;
		Player p = new Player(null, null);
		p.getType2K().putAll(t2k);
		p.getType2Model().putAll(models);
		gamePlayers.put(testGame, p);
		playFrom0(testingSize);
		Player.INSTANCE.getEvaluator().findMistakes(true);
	}
	
	private void learnThenPlay(String game, int trainingSize, int testingSize,
			PrintStream resultsOut, boolean trainScratch, boolean explore) {
		gamePlayers.clear();
		this.gameName = game;
		this.mode = null;
		System.out.println("TRAINING");
		playFrom0(trainingSize);
		System.out.println("LEARNING");
		if (trainScratch) trainModels();
		else {
			Player.INSTANCE.getWinModel().train();
			Player.INSTANCE.getLoseModel().train();
		}
		Player.INSTANCE.getLoseModel().diagnose();
		Player.INSTANCE.getAvatarModel().printTrainingSummary();
		double trainingAccuracy = Player.INSTANCE.getAvatarModel().getAccuracy();
		System.out.println("training acc: " + trainingAccuracy);
		Player.INSTANCE.getPolicy().setJustDoRandom(false);
		if (explore)
			Player.INSTANCE.getPolicy().useExploration(1, Player.INSTANCE.getAvatarType());
		Player.INSTANCE.clearExperienceHistory();
		Player.INSTANCE.resetScoreTracking();
		System.out.println("TESTING");
		
		playFrom0(testingSize);
//		RichMemoryManager rmm = Player.INSTANCE.getAvatarModel().getRichMemoryManager();
		System.out.println("training acc:	" + trainingAccuracy);
		double testingAccuracy = Player.INSTANCE.getAvatarModel().getAccuracy();
		int exploreRange = Player.INSTANCE.getPolicy().getExplorationRange();
		System.out.println("testing acc:	" + testingAccuracy);
		System.out.println("Score=	"+Player.INSTANCE.getAvgGameScore());
		System.out.println("Tick=	"+Player.INSTANCE.getAvgGameTick());
		System.out.println(testingAccuracy + "	" + Player.INSTANCE.getAvgGameTick() + "	" + exploreRange);
		resultsOut.println(testingAccuracy + "	" + Player.INSTANCE.getAvgGameTick() + "	" + exploreRange);
	}
	
	private static void trainModels() {
		Player.INSTANCE.getWinModel().train();
		Player.INSTANCE.getLoseModel().train();
		Map<String, ObjectClassModel> models = Player.INSTANCE.getType2Model();
		for (Entry<String, ObjectClassModel> entry : models.entrySet()) {
//			System.err.println("Training model " + entry.getKey());
			entry.getValue().train();
		}
	}
	
	private void variousDebug() {
//		Player.INSTANCE.getLoseModel().train();
//		for (TransitionMemory tm : Player.INSTANCE.getLoseModel().getMemories()) System.out.println(tm);
//		Player.INSTANCE.getLoseModel().diagnose();
		Map<String, ObjectClassModel> models = Player.INSTANCE.getType2Model();
//		models.get("1").getRichMemoryManager().printMemories();
//		models.get("1").describeDynamics(0.2);
//		models.get("1").printTrainingSummary();
//		for (Entry<Integer, ObjectClassModel> entry : models.entrySet()) {
//			System.err.println("Training model " + entry.getKey());
//			entry.getValue().train();
//		}
//		models.get("1").diagnose();
		for (ObjectClassModel model : models.values()) {
			System.out.println("Model " + model.getItype());
			model.diagnose(false);
		}
		System.out.println("Lose Condition");
		Player.INSTANCE.getLoseModel().diagnose();
		System.out.println("Win Condition");
		Player.INSTANCE.getWinModel().diagnose();
//		ArcadeMachine.runOneGame(gameF(TARGET_GAME), gameL(TARGET_GAME), true, agentName, recordActionsFile, seed);
		
	}
	
	private String gameF(String gameName) {
		int k = gameName.indexOf(":");
		if (k >= 0) gameName = gameName.substring(k+1);
		try { 
			Integer.parseInt(gameName.substring(gameName.length() - 1)); 
			gameName = gameName.substring(0, gameName.length() - 1);
	    } catch(NumberFormatException e) {}
		return GAMES_PATH + gameName + ".txt";
	}
	private String gameL(String gameName) {
		int k = gameName.indexOf(":");
		if (k >= 0) gameName = gameName.substring(k+1);
		String lvl = gameName.substring(gameName.length() - 1);
		int level = levelIdx;
		try { 
			level = Integer.parseInt(lvl); 
			gameName = gameName.substring(0, gameName.length() - 1);
	    } catch(NumberFormatException e) {}
		return GAMES_PATH + gameName + "_lvl" + level +".txt";
	}
	
	public Player getCurrentGamePlayer() {
		return gamePlayers.get(gameName);
	}

	public static String[] allGames(double pct) {
		File[] files = (new File(GAMES_PATH)).listFiles();
		List<String> fileNames = new ArrayList<String>();
		boolean quick = true;
		for (File file : files) {
			String name = file.getName();
			if (!name.contains("_")
					&& (!quick
					|| (!name.contains("racebet") // slow
					&& !name.contains("sheriff") // slow
					&& !name.contains("boulderchase") // slow
					&& !name.contains("boulderdash") // slow
					&& !name.contains("digdug") // slow
					&& !name.contains("defem") // slow
					&& !name.contains("chopper") // slow
					&& !name.contains("pacman") // slow
					&& !name.contains("blacksmoke") // slow
//					&& !name.contains("gymkhana") // slow
					)))
				fileNames.add(name.replaceAll(".txt", ""));
		}
		ArrayList<String> result = new ArrayList<String>();
		int n = (int) Math.round(fileNames.size() * pct);
		int i = 0;
		for (String fn : fileNames) {
			if (i++ >= n) break;
			result.add(fn);
		}
		Collections.shuffle(result);
		
		boolean useRandomlyPredeterminedSrcPool = true;
		if (useRandomlyPredeterminedSrcPool) {
			String[] srcs = new String[] { "aliens", "bait", "boloadventures", "camelRace", "catapults", "chase"};
	//		String[] srcs = new String[] { "defender", "eggomania", "hungrybirds", "jaws", "labyrinth", "missilecommand"};
	//		String[] srcs = new String[] { "brainman", "butterflies", "cakybaky", "chipschallenge", "crossfire", "escape"};
			for (String src : srcs) {result.remove(src);result.add(src);}
		}
		Collections.reverse(result);
		return result.toArray(new String[] {});
	}
	
	private static interface OnEnoughData {
		public void doit();
	}
}
