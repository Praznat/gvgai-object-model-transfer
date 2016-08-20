package controllers.gridModeler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import core.game.Observation;
import core.game.StateObservation;
import core.player.AbstractPlayer;
import modeler.ModelLearnerHeavy;
import modeler.TransitionMemory;
import ontology.Types.ACTIONS;
import reasoner.Planner;
import tools.ElapsedCpuTimer;

public class GridTestPlayer extends AbstractPlayer {
	public static enum Phase {EXPERIENCING, PLAYING};
	private static Phase PHASE = Phase.EXPERIENCING;
	private static Planner PLANNER;
	private static ModelLearnerHeavy MODELER;
	
	private GVGAI2GridGame gridGame;
	private int epoch;
	private double[] prevState;
	private double[] prevActionNN;

	private Map<ACTIONS, double[]> act2nn = new HashMap<ACTIONS, double[]>();
	private Map<double[], ACTIONS> nn2act = new HashMap<double[], ACTIONS>();
	private List<double[]> actionChoices = new ArrayList<double[]>();
	
	
	public GridTestPlayer(StateObservation so, ElapsedCpuTimer elapsedTimer) {
		int size = ACTIONS.values().length - 1;
		int i = 0;
		for (ACTIONS act : ACTIONS.values()) {
			if (act != ACTIONS.ACTION_ESCAPE) {
				double[] nn = new double[size];
				if (act != ACTIONS.ACTION_NIL) nn[i] = 1; // nil act is just 0 vector
				act2nn.put(act, nn);
				nn2act.put(nn, act);
				i++;
			}
		}
		actionChoices.addAll(nn2act.keySet());
	}

	@Override
	public ACTIONS act(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {
		
		ArrayList<Observation>[][] observationGrid = stateObs.getObservationGrid();
		if (gridGame == null) {
			gridGame = new GVGAI2GridGame(observationGrid[0].length, observationGrid.length, nn2act.keySet());
			gridGame.modeler = MODELER;
		}
		gridGame.observe(observationGrid);

		double[] state = gridGame.getState();
		
		ACTIONS result = null;
		double[] actionNN = new double[] {};
		if (PHASE == Phase.EXPERIENCING) {
			if (PLANNER == null) PLANNER = Planner.createRandomChimp();
			actionNN = PLANNER.getOptimalAction(state, actionChoices, 0, 0.1);
			result = nn2act.get(actionNN);
			if (prevState != null && prevActionNN != null) {
				TransitionMemory tm = new TransitionMemory(prevState, prevActionNN, state);
				ControlPanel.INSTANCE.addTransitionToTrainingData(tm);
				ControlPanel.INSTANCE.cacheGridGame(gridGame);
			}
		} else if (PHASE == Phase.PLAYING) {
			ControlPanel.INSTANCE.checkRewardFn(gridGame);
			actionNN = PLANNER.getOptimalAction(state, actionChoices, 0.00001, 0.001);
			result = nn2act.get(actionNN);
		}
				
		prevState = state;
		prevActionNN = actionNN;
		epoch++;
		return result;
	}
	
	public int getEpoch() {
		return epoch;
	}
	
	public static void setPhase(Phase phase) {
		PHASE = phase;
	}

	public static void setPlannerAndModeler(Planner planner, ModelLearnerHeavy modeler) {
		PLANNER = planner;
		MODELER = modeler;
	}
	
//	private static int debugAvatarPos(double[] state) {
//		for (int i = 1728; i < 1760; i++) {
//			if (state[i] > 0) return i;
//		} return -1;
//	}
	
}
