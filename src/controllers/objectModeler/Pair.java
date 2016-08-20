package controllers.objectModeler;

public class Pair<T, U> {
	
	private final T t;
	private final U u;

	public Pair(T t, U u) {
		this.t = t;
		this.u = u;
	}

	public T getT() {
		return t;
	}

	public U getU() {
		return u;
	}
	
}
