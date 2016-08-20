package controllers.objectModeler;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import com.sun.org.apache.xpath.internal.operations.Gte;

import java.util.Set;

import ann.Utils.Avger;
import ann.Utils.OneToOneMap;
import core.game.Observation;
import core.game.StateObservation;
import core.player.AbstractPlayer;
import ontology.Types.ACTIONS;
import ontology.Types.WINNER;
import tools.ElapsedCpuTimer;
import tools.Vector2d;

public class Player extends AbstractPlayer {

	public static Player INSTANCE;
	
	private StateObservation lastStateObs;
	private Map<Observation, ObjectInstance> objectMap
		= new HashMap<Observation, ObjectInstance>();
	private OneToOneMap<String, Integer> type2k = new OneToOneMap<String, Integer>();
	private Map<String, ObjectClassModel> type2model = new HashMap<String, ObjectClassModel>();
	private TerminationModel winModel = new TerminationModel(LaunchObjectModeler.TERM_PARAMS);
	private TerminationModel loseModel = new TerminationModel(LaunchObjectModeler.TERM_PARAMS);
	private MCPolicy policy = new MCPolicy(winModel, loseModel);
	private ACTIONS lastAction = ACTIONS.ACTION_NIL;
	private int numClassesKnown;
	private int avatarType = -1;
	private Evaluator evaluator = new Evaluator();
	private Avger avgScore;
	private Avger avgTick;
	private Dimension dim;
	
	public static ACTIONS[] OK_ACTIONS = getOkActions();

	private int blockSize;

	
	public Player(StateObservation so, ElapsedCpuTimer elapsedTimer) {
		// TODO so.model.classConst & so.model.charMapping
		Player existing = LaunchObjectModeler.INSTANCE.getCurrentGamePlayer();
		if (existing != null) { // copy class models to new Player
			this.type2k = existing.type2k;
			this.type2model = existing.type2model;
			this.winModel = existing.winModel;
			this.loseModel = existing.loseModel;
			this.policy = existing.policy;
			this.numClassesKnown = existing.numClassesKnown;
			this.evaluator = existing.evaluator;
			this.avgScore = existing.avgScore;
			this.avgTick = existing.avgTick;
			existing.release();
		}
//		ObjectClassModel tmp = this.type2model.get("0");
//		System.out.println("num 0-obj memories:	" + (tmp == null ? 0 : tmp.getRichMemoryManager().getRichTransitions().size()));
		
		INSTANCE = this;
		Simulator.createDisplay();
	}
	
	public void release() {
		this.lastStateObs = null;
		this.objectMap = null;
		this.type2k = null;
		this.type2model = null;
		this.winModel = null;
		this.loseModel = null;
		this.policy = null;
		this.evaluator = null;
	}
	
	public void clearExperienceHistory() {
		for (ObjectClassModel model : type2model.values()) {
			model.getRichMemoryManager().clearData();
		}
	}
	
	@Override
	public ACTIONS act(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {
		onAct(stateObs);
		ACTIONS[] acts = ACTIONS.values();
//		ACTIONS result = acts[(int) (Math.random() * (acts.length-1))];
		SimulatedFrame thisFrame = new SimulatedFrame(objectMap.values(), type2k);
		ACTIONS result = policy.chooseBestAction(thisFrame, 0, 1);
		if (evaluator != null) evaluator.registerFrameAction(thisFrame, result);
		lastAction = result;

		// store memory!
		for (Iterator<Entry<Observation,ObjectInstance>> iter = objectMap.entrySet().iterator(); iter.hasNext();) {
			ObjectInstance oim = iter.next().getValue();
			oim.update(result, orientation2Action(stateObs.getAvatarOrientation()));
			if (!oim.isAlive()) {
				iter.remove();
			}
		}

		// termination
		terminationEval(getExistenceData(), lastStateObs.getGameWinner());
		
		return result;
	}
	
	private static ACTIONS orientation2Action(Vector2d avatarOrientation) {
		if (avatarOrientation.x < 0) return ACTIONS.ACTION_LEFT;
		if (avatarOrientation.x > 0) return ACTIONS.ACTION_RIGHT;
		if (avatarOrientation.y < 0) return ACTIONS.ACTION_UP;
		if (avatarOrientation.y > 0) return ACTIONS.ACTION_DOWN;
		return null;
	}
	
	private void onAct(StateObservation stateObs) {
		double noise = LaunchObjectModeler.INSTANCE.getNoise();
		lastStateObs = stateObs;
		if (avatarType < 0) {
			try { avatarType = stateObs.getAvatarType(); } catch(Exception e) {}
		}
		dim = stateObs.getWorldDimension();
		blockSize = stateObs.getBlockSize();
		ArrayList<Observation>[][] observationGrid = stateObs.getObservationGrid();
		Set<ObjectInstance> observed = new HashSet<ObjectInstance>();
		Map<Observation, ArrayList<Observation>> spawns = new HashMap<Observation, ArrayList<Observation>>();
		for (int c = 0; c < observationGrid.length; c++) {
			ArrayList<Observation>[] alv = observationGrid[c];
			for (int r = 0; r < alv.length; r++) {
				ArrayList<Observation> al = alv[r];
				for (Observation obs : al) {
					int cc = c, rr = r;
					if (noise != 0) {
						cc = Math.random() < noise ? (int)(c + Math.round(Math.random())*2 - 1) : 0;
						rr = Math.random() < noise ? (int)(r + Math.round(Math.random())*2 - 1) : 0;
//						if (Math.random() < noise*noise) obs.itype = randIType(); // class change (moot if not new instance)
						if (Math.random() < noise) continue; // death
					}
					if (observe(obs, cc, rr, dim.getWidth(), dim.getHeight(), al, observed))
						spawns.put(obs, al);
				}
			}
		}
		// new spawns
		for (Entry<Observation, ArrayList<Observation>> entry : spawns.entrySet()) {
			Observation spawned = entry.getKey();
			ArrayList<Observation> spawners = entry.getValue();
			for (Observation spawner : spawners) if (spawner != spawned) {
				ObjectInstance spawnerObj = objectMap.get(spawner);
				if (spawnerObj != null) spawnerObj.observeSpawn(type2model.get(String.valueOf(spawned.itype)));
			}
		}
		// collisions and alive
		for (ObjectInstance oim : objectMap.values()) {
			if (!oim.isAlive()) throw new IllegalStateException("Should be alive until it dies");
			boolean isAlive = observed.contains(oim);
			oim.observeCollisionsAlive(observationGrid, type2model, isAlive); // TODO resources?
		}
		// existence data
		final ExistenceData ed = getExistenceData();
		for (ObjectInstance oim : objectMap.values()) {
			oim.observeExistenceData(ed);
		}
		LaunchObjectModeler.INSTANCE.onOneEpoch();
		Simulator.INSTANCE.repaint(objectMap.values(), observationGrid.length, observationGrid[0].length);
	}
	
	private int randIType() {
		String alphabet = "0123456789abcdefghijklmnop";
		return alphabet.charAt((int)(Math.random()*alphabet.length()));
	}

	private ObjectInstance objectMapAvatar() {
		for (ObjectInstance oi : objectMap.values()) if (oi.getItype().equals(getAvatarType())) return oi;
		return null;
	}
	
	public void onGameOver() {
		System.out.println(lastAction);
		lastStateObs.advance(lastAction); // argh
		WINNER gameWinner = lastStateObs.getGameWinner();
		if (gameWinner != WINNER.NO_WINNER) {
			act(lastStateObs, null);
		}
		final ExistenceData ed = getExistenceData();
		terminationEval(ed, gameWinner);
		if (avgScore != null && gameWinner != WINNER.NO_WINNER) {
			avgScore.observe(lastStateObs.getGameScore());
			avgTick.observe(lastStateObs.getGameTick());
		}
	}
	
	private void terminationEval(ExistenceData ed, WINNER gameWinner) {
		winModel.observe(ed, gameWinner == WINNER.PLAYER_WINS);
		loseModel.observe(ed, gameWinner == WINNER.PLAYER_LOSES);
	}
	
	private ExistenceData getExistenceData() {
		return new ExistenceData(objectMap.values(), type2k);
	}

	/** adds observation to map, returns true if it's new instance
	 * @param obsHere 
	 * @param observed */
	private boolean observe(Observation obs, int c, int r, double wWidth, double wHgt,
			ArrayList<Observation> obsHere, Set<ObjectInstance> observed) {
		boolean newInstance = false;
		ObjectInstance instance = objectMap.get(obs);
		if (instance == null) { // new object instance!
			newInstance = true;
			String iType = String.valueOf(obs.itype);
			if (!type2k.containsKey(iType)) { // new object class!
				type2model.put(iType, new ObjectClassModel(LaunchObjectModeler.OBJ_PARAMS, iType));
				int n = type2k.size();
				type2k.put(iType, n);
				numClassesKnown = Math.max(n+1, numClassesKnown);
				System.out.println(type2k);
			}
			objectMap.put(obs, instance = new ObjectInstance(type2model.get(iType)));
		}
		instance.observe(obs, c, r, wWidth, wHgt);
		observed.add(instance);
		return newInstance;
	}

	public OneToOneMap<String, Integer> getType2K() {
		return type2k;
	}

	public Map<String, ObjectClassModel> getType2Model() {
		return type2model;
	}
	
	public void setTypeModel(String type, ObjectClassModel model) {
		if (!type.equals(model.getItype())) throw new IllegalStateException("Different itypes for object " +
				"and model will cause big problems");
		type2model.put(type, model);
	}

	public TerminationModel getWinModel() {
		return winModel;
	}

	public TerminationModel getLoseModel() {
		return loseModel;
	}

	private static ACTIONS[] getOkActions() {
		ACTIONS[] result = new ACTIONS[ACTIONS.values().length-1];
		System.arraycopy(ACTIONS.values(), 0, result, 0, result.length);
		return result;
	}

	public int getNumClasses() {
		return numClassesKnown;
	}

	public Evaluator getEvaluator() {
		return evaluator;
	}
	
	public void resetEvaluator() {
		this.evaluator = new Evaluator();
	}
	
	public String getAvatarType() {
		return String.valueOf(avatarType);
	}

	public ObjectClassModel getAvatarModel() {
		return type2model.get(getAvatarType());
	}
	
	public MCPolicy getPolicy() {
		return policy;
	}

	public Avger getAvgGameScore() {
		return avgScore;
	}

	public Avger getAvgGameTick() {
		return avgTick;
	}
	
	public Dimension getWorldDimension() {
		return dim;
	}

	public int getBlockSize() {
		return blockSize;
	}

	public void resetScoreTracking() {
		avgScore = new Avger();
		avgTick = new Avger();
	}
}
