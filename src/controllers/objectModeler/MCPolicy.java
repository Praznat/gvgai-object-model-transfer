package controllers.objectModeler;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import ontology.Types.ACTIONS;

public class MCPolicy {
	private boolean justDoRandom = true;
	
	private final TerminationModel winModel;
	private final TerminationModel loseModel;
	
	private ExplorationModule exploreMod;

	public MCPolicy(TerminationModel winModel, TerminationModel loseModel) {
		this.winModel = winModel;
		this.loseModel = loseModel;
	}
	
	public ACTIONS chooseBestAction(SimulatedFrame frame, int stepsAhead, int runs) {
		// TODO discount rate?
		double bestScore = -666666;
		ACTIONS bestAction = ACTIONS.ACTION_NIL;
		if (exploreMod != null) exploreMod.visit(frame);
		for (ACTIONS action : Player.OK_ACTIONS) {
			double[] winVlose = new double[2];
			for (int i = 0; i < runs; i++) {
				SimulatedFrame nextFrame = evalNext(frame, action, winVlose);
				for (int j = 0; j < stepsAhead; j++) {
					ACTIONS randAction = Player.OK_ACTIONS[(int)(Math.random()*Player.OK_ACTIONS.length)];
					nextFrame = evalNext(nextFrame, randAction, winVlose);
				}
			}
			double score = 3 * winVlose[0] - winVlose[1] + Math.random() / 10000;
			if (score > bestScore) {
				bestAction = action;
				bestScore = score;
			}
		}
		return bestAction;
	}
	
	private SimulatedFrame evalNext(SimulatedFrame frame, ACTIONS action, double[] winVlose) {
		if (justDoRandom) return frame;
		SimulatedFrame nextFrame = frame.createProjection(action);
		if (nextFrame == null || !winModel.classifier.isInitialized()) return frame; // models not trained yet
		double[] counts = nextFrame.getExistenceData().getCounts();
		winVlose[0] += winModel.predict(counts)[0];
		winVlose[1] += loseModel.predict(counts)[0];
		if (exploreMod != null) winVlose[0] += exploreMod.getExplorationPoints(nextFrame);
		return nextFrame;
	}
	
	public void setJustDoRandom(boolean b) {
		justDoRandom = b;
	}
	
	public void useExploration(double explorationPoints, String explorerObjectClass) {
		exploreMod = new ExplorationModule(explorationPoints, explorerObjectClass);
	}
	
	public int getExplorationRange() {
		return exploreMod.getVisited().size();
	}
	
	public void drawExplorationMap(String fileName, int width, int height, int size) {
		BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D ig2 = bi.createGraphics();
		ig2.setPaint(Color.black);
		ig2.fillRect(0, 0, width, height);
		ig2.setPaint(Color.white);
		for (List<Point> v : exploreMod.getVisited().keySet()) {
			for (Point p : v) {
				ig2.fillRect((int)p.getX() * size, (int)p.getY() * size, size, size);
			}
		}
		try {
			ImageIO.write(bi, "PNG", new File(fileName + ".PNG"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
