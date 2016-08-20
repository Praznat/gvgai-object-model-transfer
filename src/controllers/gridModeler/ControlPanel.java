package controllers.gridModeler;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import ann.ActivationFunction;
import ann.FFNeuralNetwork;
import ann.Utils;
import ann.indirectencodings.RelationManager;
import ann.testing.GridExploreGame;
import ann.testing.GridGame;
import ann.testing.SerializableGridGame;
import controllers.gridModeler.GridTestPlayer.Phase;
import controllers.objectModeler.ExplorationModule;
import core.ArcadeMachine;
import core.competition.CompetitionParameters;
import modeler.ModelLearnerHeavy;
import modeler.ModelLearnerModularPure;
import modeler.TransitionMemory;
import modulemanagement.ModuleDisplayer;
import modulemanagement.ModuleManagerPure;
import reasoner.Planner;
import reasoner.RewardFunction;

@SuppressWarnings("unused")
public class ControlPanel {
	public static final ControlPanel INSTANCE = new ControlPanel();
	private static final String GAMES_PATH = "C:/Users/Alex/Documents/workspace/VGDL/gvgai/examples/gridphysics/";
    private static final String TARGET_GAME = "labyrinth";
    private static final int LVL = 7;
    private static final String TRAINING_DATA_FILE = TARGET_GAME + "_trainingData";
    private static final String GRID_FILE = TARGET_GAME + "_grid";
    private static final String MODULES_FILE = TARGET_GAME + "_modules";
    
	private final String recordActionsFile = null;
	private final int seed = new Random().nextInt();
	private final String agentName = "controllers.modelLearner.AlexTestPlayer";
	private GVGAI2GridGame gridGame;
	private ArrayList<TransitionMemory> trainingData = new ArrayList<TransitionMemory>();

	private RewardFunction rewardFn;
	private RewardFunction rewardFnWrapper = new RewardFunction() {
		@Override
		public double getReward(double[] stateVars) {
			return rewardFn.getReward(stateVars);
		}
	};
	
	public static void main(String[] args) {
//		INSTANCE.accumulateTrainingData(8000);
//		INSTANCE.trainModuleModeler();
		
		
		INSTANCE.trainAndTest(500, 100, new int[] {50}); //new int[] {200, 200});
//		INSTANCE.justTest("lastNet", 100);
	}

	private void accumulateTrainingData(int desiredDataSize)  {
		GridTestPlayer.setPhase(Phase.EXPERIENCING);
        while (trainingData.size() < desiredDataSize) {
        	ArcadeMachine.runOneGame(gameF(TARGET_GAME), gameL(TARGET_GAME), true, agentName, recordActionsFile, seed);
        }
        ArrayList<TransitionMemory> existingTrainingData = Utils.loadTrainingDataFromFile(TRAINING_DATA_FILE);
        if (existingTrainingData != null) trainingData.addAll(existingTrainingData);
        cleanTrainingData(trainingData);
        Utils.saveObjectToFile(TRAINING_DATA_FILE, trainingData);
        initAndSaveGrid(gridGame);
        System.out.println("Done");
	}
	
	/** remove data of smaller than necessary dimension (can form at start of game when certain obj never seen) 
	 * @param trainingData */
	private static void cleanTrainingData(Collection<TransitionMemory> trainingData) {
		int maxD = 0;
		for (TransitionMemory tm : trainingData) maxD = Math.max(maxD, tm.getAllVars().length);
		for (Iterator<TransitionMemory> iter = trainingData.iterator(); iter.hasNext();) {
			TransitionMemory tm = iter.next();
			if (tm.getAllVars().length < maxD) iter.remove();
		}
	}
	
	void addTransitionToTrainingData(TransitionMemory tm) {
		trainingData.add(tm);
	}

	void cacheGridGame(GVGAI2GridGame gridGame) {
		this.gridGame = gridGame;
	}
	
	private static ModelLearnerHeavy createDumbInitModeler(Collection<TransitionMemory> data) {
		int maxReplaySize = Math.max(1000, data.size());
		return new ModelLearnerHeavy(500, new int[] {1}, null, null, ActivationFunction.SIGMOID0p5, maxReplaySize);
	}

	/**
	 * saves a neural network that has seen training data but not "learned" on it (just learned dimensions)
	 */
	private void initAndSaveGrid(GridGame game) {
		game.modeler = createDumbInitModeler(trainingData );
		int i = 0;
		for (TransitionMemory tm : trainingData) {
			if (i++ > 10) break; // just a few to learn the
			game.modeler.saveMemory(tm);
		}
		game.modeler.learnFromMemory(0, 0, 0, false, 1, 10000); // just to get node dimensions
		Utils.saveObjectToFile(GRID_FILE, SerializableGridGame.create(game));
		if (Utils.loadObjectFromFile(GRID_FILE) == null) System.out.println("Problem saving grid");
	}
	
	private void trainModuleModeler() {
		Collection<TransitionMemory> trainingData = Utils.loadTrainingDataFromFile(TRAINING_DATA_FILE);
		SerializableGridGame sgg = (SerializableGridGame) Utils.loadObjectFromFile(GRID_FILE);
		TransitionMemory tm0 = trainingData.iterator().next();
		RelationManager<Integer> relMngr = RelationManager.createFromGridGamePredictor(sgg,
				tm0.getPreStateAndAction().length, tm0.getPostState().length);
		int[] hiddenPerOutput = new int[] {20};
		int samplesPerTrain = 5000;//trainingData.size();//1500;
		int maxModules = 150;
		int maxModVar = 100;
		double killPct = 0.2;
		ModuleManagerPure moduleManager = new ModuleManagerPure(0.95, maxModules, killPct , 0.3, 0.5, 0, hiddenPerOutput, 300);
		ModelLearnerModularPure mlmp = new ModelLearnerModularPure(relMngr, moduleManager, samplesPerTrain, 3);
		mlmp.saveMemories(trainingData);
		mlmp.learnGradually(maxModules-maxModVar, maxModules+maxModVar, 0.9, 0.98, 15);
		ModuleManagerPure.saveState(moduleManager, MODULES_FILE);
	}
	
	private void play(int numGames) {
		Collection<TransitionMemory> trainingData = Utils.loadTrainingDataFromFile(TRAINING_DATA_FILE);
		SerializableGridGame sgg = (SerializableGridGame) Utils.loadObjectFromFile(GRID_FILE);
		TransitionMemory tm0 = trainingData.iterator().next();
		RelationManager<Integer> relMngr = RelationManager.createFromGridGamePredictor(sgg,
				tm0.getPreStateAndAction().length, tm0.getPostState().length);
		ModuleManagerPure moduleManager = ModuleManagerPure.loadState(MODULES_FILE);
		moduleManager.report();
		int samplesPerTrain = 500;
		ModelLearnerModularPure mlmp = new ModelLearnerModularPure(relMngr, moduleManager, samplesPerTrain, 3);
		ModuleDisplayer.create(mlmp, tm0.getPostState().length, sgg);
		int numSteps = 3;
		int numRuns = 1;
		double discRate = 0.05;
		int joints = 1;
		Planner planner = Planner.createMonteCarloPlanner(mlmp, numSteps, numRuns, rewardFnWrapper,
				false, discRate, joints, null, GridExploreGame.actionTranslator);
		
		GridTestPlayer.setPhase(Phase.PLAYING);
		GridTestPlayer.setPlannerAndModeler(planner, mlmp);
        for (int run = 0; run < numRuns; run++) {
        	ArcadeMachine.runOneGame(gameF(TARGET_GAME), gameL(TARGET_GAME), true, agentName, recordActionsFile, seed);
        }
	}
	
	public Collection<TransitionMemory> loadTrainingData() {
		return Utils.loadTrainingDataFromFile(TRAINING_DATA_FILE);
	}

	private String gameF(String gameName) {
		return GAMES_PATH + gameName + ".txt";
	}
	private String gameL(String gameName) {
		return GAMES_PATH + gameName + "_lvl" + LVL +".txt";
	}

	public void checkRewardFn(GVGAI2GridGame gridGame) {
		if (rewardFn != null) return;
		if (TARGET_GAME.equals("aliens")) {
			rewardFn = new RewardFunction() {
				int area = gridGame.rows * gridGame.cols;
				int avatar = area * 3;
				int aliens = area * 4;
				int missiles = area * 5;
				int bombs = area * 6;
				@Override
				public double getReward(double[] stateVars) {
					double reward = 0;
					if (stateVars.length > missiles) for (int i = aliens; i < aliens + area; i++) {
						if (Math.random() < stateVars[i] && Math.random() < stateVars[i+missiles-aliens])
							reward += 10;
					}
					if (stateVars.length > bombs) for (int i = avatar; i < avatar + area; i++) {
						if (Math.random() < stateVars[i] && Math.random() < stateVars[i+bombs-avatar])
							reward -= 100;
					}
					return reward;
				}
			};
		} else if (TARGET_GAME.equals("labyrinth")) {
			if (exploreMod == null) exploreMod = new ExplorationModule(0.5, null);
			final int area = gridGame.rows * gridGame.cols;
			final int avatar = area * 1;
			rewardFn = new RewardFunction() {
				@Override
				public double getReward(double[] stateVars) {
					List<Point> points = new ArrayList<Point>();
					for (int i = avatar; i < avatar + area; i++) {
						if (Math.random() < stateVars[i]) points.add(new Point(i % gridGame.cols, i / gridGame.cols));
					}
					return exploreMod.getExplorationPoints(points);
				}
			};
		} else {
			throw new IllegalStateException("No reward function found for game " + TARGET_GAME);
		}
	}
	
	private ExplorationModule exploreMod;
	
	
	

	private void trainAndTest(int trainingFrames, int testingFrames, int[] hiddenNodes) {
		trainingData = collectDataForModeler(trainingFrames);
		ModelLearnerHeavy modeler = new ModelLearnerHeavy(500, hiddenNodes, null, null,
				ActivationFunction.SIGMOID0p5, Math.max(trainingFrames, testingFrames));
		modeler.saveMemories(trainingData);
		modeler.learnFromMemory(0.1, 0.9, 0, false, 100, 10000);
		double pctMasteredTrain = modeler.getPctMastered();
		System.out.println("PCT TRAINING CORRECT:	" + pctMasteredTrain);
		
		ArrayList<TransitionMemory> testingData = collectDataForModeler(testingFrames);
		modeler.clearExperience();
		modeler.saveMemories(testingData);
		double pctMasteredTest = modeler.getPctMastered();
		System.out.println("PCT TESTING CORRECT:	" + pctMasteredTest);
		
		Utils.saveNetworkToFile("lastNet", modeler.getModelVTA().getNeuralNetwork());
	}
	private void justTest(String loadFile, int testingFrames) {
		FFNeuralNetwork ann = Utils.loadNetworkFromFile(loadFile);
		ModelLearnerHeavy modeler = new ModelLearnerHeavy(500, new int[0], null, null,
				ActivationFunction.SIGMOID0p5, testingFrames);
		modeler.getModelVTA().setANN(ann);
		
		ArrayList<TransitionMemory> testingData = collectDataForModeler(testingFrames);
		modeler.clearExperience();
		modeler.saveMemories(testingData);
//		double pctMasteredTest = modeler.getPctMastered();
//		System.out.println("PCT TESTING CORRECT:	" + pctMasteredTest);
		double pctMasteredTestA = modeler.getPctMastered(28, 56);
		System.out.println("PCT AVATAR TESTING CORRECT:	" + pctMasteredTestA);
	}
	
	
	private ArrayList<TransitionMemory> collectDataForModeler(int frames) {
		GridTestPlayer.setPhase(Phase.EXPERIENCING);
		trainingData = new ArrayList<TransitionMemory>();
		while (trainingData.size() < frames) {
			CompetitionParameters.MAX_TIMESTEPS = frames - trainingData.size();
			ArcadeMachine.runOneGame(gameF(TARGET_GAME), gameL(TARGET_GAME), true, agentName, recordActionsFile, seed);
		}
        cleanTrainingData(trainingData);
        return trainingData;
	}

}
