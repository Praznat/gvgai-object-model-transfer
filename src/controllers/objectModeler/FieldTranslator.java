package controllers.objectModeler;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import ann.Utils;
import ontology.Types.ACTIONS;

public abstract class FieldTranslator<T> {
	protected abstract double[] toVect(T field);
	public abstract T toField(double[] vector);
	
	public static double[] translateToVector(Object field) {
		if (field instanceof NamedObject) return translateToVector(((NamedObject)field).getObject());
		if (field instanceof Integer) return DoubleTranslator.toVect(((Integer)field).doubleValue());
		if (field instanceof Double) return DoubleTranslator.toVect((Double)field);
		if (field instanceof double[]) return VectorTranslator.toVect((double[])field);
		if (field instanceof double[][]) return Matrix5Translator.toVect((double[][])field); // only if length 5
		if (field instanceof ACTIONS) return ActionTranslator.toVect((ACTIONS)field);
		if (field instanceof Boolean) return BooleanTranslator.toVect((Boolean)field);
		if (field instanceof NullAttribute) return NullTranslator.toVect((NullAttribute)field);
		if (field instanceof Collision) return CollisionTranslator.toVect((Collision)field);
		return null;
	}

	public static double[] toVector(Object... fields) {
		double[] vv = {};
		for (Object field : fields) {
			double[] v = FieldTranslator.translateToVector(field);
			vv = Utils.concat(vv, v);
		}
		return vv;
	}
	public static double[] toVector(Collection<Object> fields) {
		double[] vv = {};
		for (Object field : fields) {
			double[] v = FieldTranslator.translateToVector(field);
			vv = Utils.concat(vv, v);
		}
		return vv;
	}

	public static final FieldTranslator<Boolean> BooleanTranslator = new FieldTranslator<Boolean>() {
		protected double[] toVect(Boolean field) {
			return new double[] {field ? 1 : 0};
		}
		public Boolean toField(double[] vector) {
			return vector[0] > 0;
		}
	};
	public static final FieldTranslator<Double> DoubleTranslator = new FieldTranslator<Double>() {
		protected double[] toVect(Double field) {
			return new double[] {field};
		}
		public Double toField(double[] vector) {
			return vector[0];
		}
	};
	public static final FieldTranslator<double[]> VectorTranslator = new FieldTranslator<double[]>() {
		protected double[] toVect(double[] field) {
			return field.clone();
		}
		public double[] toField(double[] vector) {
			return vector.clone();
		}
	};
	public static final FieldTranslator<double[][]> Matrix5Translator = new FieldTranslator<double[][]>() {
		protected double[] toVect(double[][] field) {
			if (field.length == 0) return new double[] {};
			double[] result = new double[field.length * field[0].length];
			int i = 0;
			for (double[] v : field) for (double d : v) result[i++] = d;
			return result;
		}
		public double[][] toField(double[] vector) {
			double[][] result = new double[5][];
			int rows = vector.length / 5;
			int i = 0;
			for (int c = 0; c < 5; c++) for (int r = 0; r < rows; r++) result[c][r] = vector[i++];
			return result;
		}
	};
	public static final FieldTranslator<NullAttribute> NullTranslator = new FieldTranslator<NullAttribute>() {
		protected double[] toVect(NullAttribute field) {
			return new double[field.getSize()];
		}
		public NullAttribute toField(double[] vector) {
			return new NullAttribute(vector.length);
		}
	};
	public static final FieldTranslator<ACTIONS> ActionTranslator = new FieldTranslator<ACTIONS>() {
		private Map<ACTIONS, double[]> act2nn = new HashMap<ACTIONS, double[]>();
		private Map<double[], ACTIONS> nn2act = new HashMap<double[], ACTIONS>();
		{
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
		}
		protected double[] toVect(ACTIONS field) {
			return act2nn.get(field);
		}
		public ACTIONS toField(double[] vector) {
			return nn2act.get(vector);
		}
	};
	public static final FieldTranslator<Collision> CollisionTranslator = new FieldTranslator<Collision>() {
		protected double[] toVect(Collision field) {
			double[] result = new double[Direction.values().length];
			result[field.placement.ordinal()] = 1;
			return result;
		}
		public Collision toField(double[] vector) {
			double max = -1;
			Direction placement = null;
			Direction[] dirs = Direction.values();
			for (int i = 0; i < vector.length; i++) {
				double v = vector[i];
				if (v > max) {
					max = v;
					placement = dirs[i];
				}
			}
			return new Collision(null, placement, null);
		}
	};
}
