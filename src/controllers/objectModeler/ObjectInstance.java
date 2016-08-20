package controllers.objectModeler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import controllers.objectModeler.RichMemoryManager.State;
import core.game.Observation;
import ontology.Types.ACTIONS;
import tools.Vector2d;

public class ObjectInstance {

	private ObjectClassModel classModel;
	
	private boolean isAlive = true;
	private int c, r;
	private double xPos, yPos;
	private double xMoveL, xMoveR, yMoveU, yMoveD;
	private int faceForward;
	private double resource;
	private Set<ObjectClassModel> spawns;
	private Set<Collision> collisions;
	private ExistenceData existenceData;
	private Collection<NamedObject> lastInputs;

	
	public ObjectInstance(ObjectClassModel classModel) {
		this.classModel = classModel;
	}
	
	public void observe(Observation obs, int c, int r, double wWidth, double wHgt) {
		Vector2d position = obs.position;
		double x = position.x / wWidth;
		double y = position.y / wHgt;
		setPosition(x, y);
		this.c = c;
		this.r = r;
	}
	
	public void setPosition(double x, double y) {
		this.faceForward = 0;
		if (xPos > 0) {
			if (x < xPos) {
				this.xMoveL = 1;
			} else {
				this.xMoveL = 0;
			}
			if (x > xPos) {
				this.xMoveR = 1;
			} else {
				this.xMoveR = 0;
			}
		}
		if (yPos > 0) {
			if (y < yPos) {
				this.yMoveU = 1;
			} else {
				this.yMoveU = 0;
			}
			if (y > yPos) {
				this.yMoveD = 1;
			} else {
				this.yMoveD = 0;
			}
		}
		this.xPos = x;
		this.yPos = y;
	}

	public void update(ACTIONS action, ACTIONS orientation) {
		Collection<NamedObject> outputs = new ArrayList<NamedObject>();
		outputs.add(new NamedObject("isAlive-0", isAlive));
		outputs.add(new NamedObject("xMoveL-0", xMoveL));
		outputs.add(new NamedObject("xMoveR-0", xMoveR));
		outputs.add(new NamedObject("yMoveU-0", yMoveU));
		outputs.add(new NamedObject("yMoveD-0", yMoveD));
		outputs.add(new NamedObject("resource-0", resource));
		if (spawns != null) for (ObjectClassModel spawn : spawns)
			outputs.add(new NamedObject(spawn.getItype() + "spawn-0", 1));
		
		spawns = null; // must come after vector calc
		if (lastInputs != null) {
			classModel.getRichMemoryManager().addRichTransition(lastInputs, outputs);
		}
		if (action == orientation) this.faceForward = 1;
		lastInputs = inputObjectVector(action);
		
		// TODO idea: if nothing changes, dont train (save time on all those walls!)
	}
	
	private Collection<NamedObject> inputObjectVector(ACTIONS action) {
		Collection<NamedObject> result = new ArrayList<NamedObject>();
//		result.add(new NamedObject("wasAlive-1", isAlive));
		result.add(new NamedObject("xMoveL-1", xMoveL));
		result.add(new NamedObject("xMoveR-1", xMoveR));
		result.add(new NamedObject("yMoveU-1", yMoveU));
		result.add(new NamedObject("yMoveD-1", yMoveD));
		result.add(new NamedObject("faceForward-1", faceForward));
//		result.add(new NamedObject("resource-1", resource));
//		double[] existenceCounts = existenceData.getCounts();
//		ObjectClassModel[] exClasses = existenceData.getClasses();
//		for (int i = 0; i < existenceCounts.length; i++) {
//			ObjectClassModel objClass = exClasses[i];
//			if (objClass != null) result.add(new NamedObject(objClass+"existence-1", existenceCounts[i]));
//		}
		for (Collision coll : collisions) {
			String collName = coll.collider.itype.equals(getItype())
					? "COLLISIONg"+coll.placement+"-1" : coll.toString()+"-1";
			result.add(new NamedObject(collName, 1)); // coll instead of 1
		}
		result.add(new NamedObject("actionNULDRF", action));
		return result;
	}
	
	private ObjectInstance fromVector(double[] outputVector, int origX, int origY) {
		ObjectInstance result = new ObjectInstance(this.classModel);
		RichMemoryManager rmm = classModel.getRichMemoryManager();
		result.isAlive = Math.random() < rmm.getOutputForField("isAlive", outputVector, State.NEXT)[0];
		result.xMoveR = Math.random() < rmm.getOutputForField("xMoveR", outputVector, State.NEXT)[0] ? 1 : 0;
		result.xMoveL = Math.random() < rmm.getOutputForField("xMoveL", outputVector, State.NEXT)[0] ? 1 : 0;
		result.yMoveU = Math.random() < rmm.getOutputForField("yMoveU", outputVector, State.NEXT)[0] ? 1 : 0;
		result.yMoveD = Math.random() < rmm.getOutputForField("yMoveD", outputVector, State.NEXT)[0] ? 1 : 0;
		result.r = origY + (int)Math.round(result.yMoveD - result.yMoveU);
		result.c = origX + (int)Math.round(result.xMoveR - result.xMoveL);
		
		result.spawns = new HashSet<ObjectClassModel>();
		Collection<Pair<String, double[]>> spawnV = rmm.getOutputsForFields("spawn", outputVector, State.NEXT);
		for (Pair<String, double[]> s : spawnV) {
			String spawnObjType = s.getT().substring(0, s.getT().indexOf("spawn"));
			if (Math.random() < s.getU()[0])
				result.spawns.add(Player.INSTANCE.getType2Model().get(spawnObjType));
		}

		return result;
	}
	
	public void observeSpawn(ObjectClassModel objClass) {
		if (spawns == null) spawns = new HashSet<ObjectClassModel>();
		spawns.add(objClass);
	}

	public void observeCollisionsAlive(ArrayList<Observation>[][] observationGrid,
			Map<String, ObjectClassModel> type2model, boolean alive) {
		collisions = new HashSet<Collision>();
		observeCollision(Direction.SAME, c, r, observationGrid, type2model);
		observeCollision(Direction.ABOVE, c, r-1, observationGrid, type2model);
		observeCollision(Direction.RIGHT, c+1, r, observationGrid, type2model);
		observeCollision(Direction.BELOW, c, r+1, observationGrid, type2model);
		observeCollision(Direction.LEFT, c-1, r, observationGrid, type2model);
		isAlive = alive;
	}
	private void observeCollision(Direction p, int c, int r,
			ArrayList<Observation>[][] observationGrid, Map<String, ObjectClassModel> type2model) {
		// check if inside screen boundaries
		if (c >= 0 && c < observationGrid.length && r >= 0 && r < observationGrid[0].length) {
			ArrayList<Observation> obses = observationGrid[c][r];
			// look through all other objects on indicated spot
			for (Observation obs : obses) {
				ObjectClassModel collClass = type2model.get(String.valueOf(obs.itype));
				if (collClass == null) {
					continue; // this would only be because noise removed this object but collisions detected from "true" objects
				}
				Collision collision = new Collision(collClass, p, Direction.SAME);
				// dont count collisions of same class (including self)
				if (collClass.itype.equals(this.getItype())) continue;
				collisions.add(collision);
			}
		} else if (c < 0) {
			collisions.add(new Collision(EOS_CLASS, p, Direction.LEFT));
		} else if (c >= observationGrid.length) {
			collisions.add(new Collision(EOS_CLASS, p, Direction.RIGHT));
		} else if (r < 0) {
			collisions.add(new Collision(EOS_CLASS, p, Direction.ABOVE));
		} else if (r >= observationGrid[0].length) {
			collisions.add(new Collision(EOS_CLASS, p, Direction.BELOW));
		}
	}
	private static final ObjectClassModel EOS_CLASS = new ObjectClassModel(null, "EOS");

	public void observeExistenceData(ExistenceData ed) {
		existenceData = ed;
	}
	
	public int getC() {
		return c;
	}
	public int getR() {
		return r;
	}
	public boolean isAlive() {
		return isAlive;
	}
	public ObjectClassModel getClassModel() {
		return classModel;
	}
	public String getItype() {
		return classModel.getItype();
	}
	
	public ObjectInstance get1StepPrediction(ACTIONS action) {
		double[] predict = classModel.convertAndPredict(inputObjectVector(action));
		return predict != null ? fromVector(predict, c, r) : null;
	}
	
	public Collection<ObjectInstance> getSpawnPredictions() {
		Collection<ObjectInstance> result = new ArrayList<ObjectInstance>();
		for (ObjectClassModel spawnClass : spawns) {
			ObjectInstance spawn = new ObjectInstance(spawnClass);
			spawn.c = c;
			spawn.r = r;
			result.add(spawn);
		}
		return result;
	}
	
	@Override
	public String toString() {
		return c + "," + r + "	" + classModel;
	}
}
