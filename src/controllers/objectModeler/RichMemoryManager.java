package controllers.objectModeler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import modeler.TransitionMemory;
import ontology.Types.ACTIONS;

public class RichMemoryManager {
	
	enum State {PREV, NEXT};

	// value denotes bit size of field
	private Map<String, InputsPointer> registeredPrevFields = new HashMap<String, InputsPointer>();
	private Map<String, InputsPointer> registeredNextFields = new HashMap<String, InputsPointer>();
	private final Collection<RichTransition> richTransitions = new ArrayList<RichTransition>();

	/**
	 * creates new RichMemoryManager with the registered fields of the sourceRMM RichMemoryManager
	 * and the stored memories of the target
	 */
	public static RichMemoryManager createTransferRMM(RichMemoryManager sourceRMM, RichMemoryManager targetRMM) {
		RichMemoryManager result = new RichMemoryManager();
		result.registeredPrevFields = new HashMap<String, InputsPointer>(sourceRMM.registeredPrevFields);
		result.registeredNextFields = new HashMap<String, InputsPointer>(sourceRMM.registeredNextFields);
		if (targetRMM != null) {
			Collection<RichTransition> targetTransitions = targetRMM.getRichTransitions();
			for (RichTransition rt : targetTransitions) {
				result.addRichTransition(rt.prev.elements.values(), rt.next.elements.values());
			}
		}
		return result;
	}

	public Map<String, InputsPointer> getRegFieldsMap(State state) {
		return state == State.PREV ? registeredPrevFields : registeredNextFields;
	}
	public ArrayList<Entry<String, InputsPointer>> getOrderedRegFieldsMap(State state) {
		ArrayList<Entry<String, InputsPointer>> regFieldsMap = new ArrayList<Entry<String, InputsPointer>>(getRegFieldsMap(state).entrySet());
		regFieldsMap.sort(new Comparator<Entry<String, InputsPointer>>() {
			@Override
			public int compare(Entry<String, InputsPointer> o1, Entry<String, InputsPointer> o2) {
				return Integer.compare(o1.getValue().getK(), o2.getValue().getK());
			}
		});
		return regFieldsMap;
	}
	
	public String orderedRegFieldsString() {
		ArrayList<Entry<String, InputsPointer>> prevFields = getOrderedRegFieldsMap(State.PREV);
		ArrayList<Entry<String, InputsPointer>> nextFields = getOrderedRegFieldsMap(State.NEXT);
		StringBuilder sb = new StringBuilder();
		for (Entry<String, InputsPointer> entry : prevFields) sb.append(entry.getValue().k + "	" + entry.getKey() + "\n");
		sb.append("	->	\n");
		for (Entry<String, InputsPointer> entry : nextFields) sb.append(entry.getValue().k + "	" + entry.getKey() + "\n");
		return sb.toString();
	}

	private void registerField(String name, State state, Object object) {
		Map<String, InputsPointer> registeredFields = getRegFieldsMap(state);
		if (!registeredFields.containsKey(name)) {
			int size = FieldTranslator.toVector(object).length;
			NullAttribute na = new NullAttribute(size);
			// go through all the richMemories and add this field
			for (RichTransition rt : richTransitions) {
				rt.get(state).elements.put(name, new NamedObject(name, na));
			}
			registeredFields.put(name, new InputsPointer(getRegFieldsSize(registeredFields), size));
		}
	}
	
	private static int getRegFieldsSize(Map<String, InputsPointer> registeredFields) {
		int result = 0;
		for (InputsPointer ip : registeredFields.values()) result += ip.getLen();
		return result;
	}
	
	public void addRichTransition(Collection<NamedObject> prevs, Collection<NamedObject> nexts) {
		richTransitions.add(new RichTransition(createRichMemory(prevs, State.PREV),
				createRichMemory(nexts, State.NEXT)));
	}
	public RichMemory createRichMemory(Collection<NamedObject> elements, State state) {
		Map<String, InputsPointer> registeredFields = getRegFieldsMap(state);
		RichMemory newMemory = new RichMemory();
		Set<String> unusedFields = new HashSet<String>(registeredFields.keySet());
		for (NamedObject element : elements) {
			String name = element.getName();
			registerField(name, state, element.getObject());
			unusedFields.remove(name);
			if (newMemory.elements.values().contains(element))
				throw new IllegalStateException("shouldnt be be duplicates");
			newMemory.elements.put(name, element);
		}
		// check... TODO delete when code is ready
//		sanityCheck();
		// add null objects for registered fields not in "elements"
		for (String unused : unusedFields)
			newMemory.elements.put(unused, new NamedObject(unused, new NullAttribute(registeredFields.get(unused).getLen())));
		return newMemory;
	}
	
	private void sanityCheck() {
		RichTransition last = null;
		for (RichTransition rt : richTransitions) {
			int l = rt.prev.elements.size() + rt.next.elements.size();
			if (last != null && l != last.prev.elements.size() + last.next.elements.size()) {
				System.out.println("prev	" + last.prev.elements.size() + "	vs	" + rt.prev.elements.size());
				System.out.println("next	" + last.next.elements.size() + "	vs	" + rt.next.elements.size());
				for (NamedObject i : last.prev.elements.values()) System.out.print(i+"	");
				System.out.println();
				for (NamedObject i : rt.prev.elements.values()) System.out.print(i+"	");
				System.out.println();
			}
			last = rt;
		}
	}

	public void debug(RichMemoryManager.State state) {
		System.out.println(this);
		Map<String, InputsPointer> prevFields = getRegFieldsMap(state);
		int sum = getRegFieldsSize(prevFields);
		System.out.println(state + ":	" + sum + "	input spaces");
		System.out.println(prevFields);
	}

	public double[] convertPrev(Collection<NamedObject> elements) {
		Map<String, NamedObject> elementMap = new HashMap<String, NamedObject>();
		for (NamedObject obj : elements) elementMap.put(obj.getName(), obj);
		return convert(elementMap, State.PREV);
	}
	public double[] convertNext(Collection<NamedObject> elements) {
		Map<String, NamedObject> elementMap = new HashMap<String, NamedObject>();
		for (NamedObject obj : elements) elementMap.put(obj.getName(), obj);
		return convert(elementMap, State.NEXT);
	}
	public double[] convert(Map<String, NamedObject> elements, State state) {
		Collection<Entry<String, InputsPointer>> regFieldsMap = getOrderedRegFieldsMap(state);
		Collection<Object> objs = new ArrayList<Object>();
		int sumSize = 0;
		// convert by order of registered fields
		for (Entry<String, InputsPointer> entry : regFieldsMap) {
			int size = entry.getValue().getLen();
			NamedObject namedObject = elements.get(entry.getKey());
			// add existing element, or appropriate null attribute if none existing
			objs.add(namedObject != null ? namedObject : new NullAttribute(size));
			sumSize += size;
		}
		double[] result = FieldTranslator.toVector(objs);
		if (sumSize != result.length) {
			throw new IllegalStateException("unexpected conversion length " + sumSize + " vs " + result.length);
		}
//		System.out.println("conversion length " + sumSize + " vs " + result.length);
//		System.out.println(regFieldsMap);
		return result;
	}
	
	/** conversion from richMemories to transitionMemories */
	public Collection<TransitionMemory> convertToTransitionMemories() {
		Collection<TransitionMemory> result = new ArrayList<TransitionMemory>();
		int actSize = ACTIONS.values().length - 1;
		for (RichTransition rt : richTransitions) {
			double[] prevV = convert(rt.get(State.PREV).elements, State.PREV);
			double[] nextV = convert(rt.get(State.NEXT).elements, State.NEXT);
			int prevSize = prevV.length - actSize;
			TransitionMemory tm = new TransitionMemory(prevV, prevSize, nextV);
			result.add(tm);
		}
		return result;
	}

	public double[] getOutputForField(String fieldContains, double[] output, State state) {
		return getOutputsForFields(fieldContains, output, state).iterator().next().getU();
	}
	public Collection<Pair<String,double[]>> getOutputsForFields(String fieldContains, double[] output, State state) {
		Collection<Pair<String,double[]>> result = new ArrayList<Pair<String,double[]>>();
		Collection<Entry<String, InputsPointer>> regFieldsMap = getOrderedRegFieldsMap(state);
		for (Entry<String, InputsPointer> entry : regFieldsMap) {
			final String fieldName = entry.getKey();
			if (fieldName.contains(fieldContains)) {
				final InputsPointer ip = entry.getValue();
				if (ip.getK() >= output.length) continue; // break? cuz ordered. these are fields unseen in training.
				final double[] v = new double[ip.getLen()];
				System.arraycopy(output, ip.getK(), v, 0, v.length);
				result.add(new Pair<String, double[]>(fieldName, v));
			}
		}
		return result;
	}

	public Collection<RichTransition> getRichTransitions() {
		return richTransitions;
	}

	/** warning: no conversion done here */
	public void addRichTransitions(Collection<RichTransition> richTransitions) {
		this.richTransitions.addAll(richTransitions);
	}

	public void clearData() {
		richTransitions.clear();
	}

	public void printMemories() {
		for (RichTransition rt : richTransitions) System.out.println(rt);
	}

	public static class RichMemory {
		public final Map<String,NamedObject> elements = new HashMap<String,NamedObject>();
		@Override
		public String toString() {
			return elements.values().toString();
		}
	}
	
	public static class RichTransition {
		private RichMemory prev, next;
		private RichTransition(RichMemory prev, RichMemory next) {
			this.prev = prev;
			this.next = next;
		}
		public RichMemory get(State state) {
			if (state == State.PREV) return prev;
			else if (state == State.NEXT) return next;
			else throw new IllegalStateException();
		}
		@Override
		public String toString() {
			return prev + "	->	" + next;
		}
	}
	
	public static class InputsPointer {
		private final int k;
		private final int len;

		private InputsPointer(int k, int len) {
			this.k = k;
			this.len = len;
		}
		public int getK() {
			return k;
		}
		public int getLen() {
			return len;
		}
	}
}
