package controllers.objectModeler;

import java.io.Serializable;

public class NullAttribute implements Serializable {
	private static final long serialVersionUID = -5827385536651410535L;
	private final int size;

	public NullAttribute(int size) {
		this.size = size;
	}

	public int getSize() {
		return size;
	}
	
	@Override
	public String toString() {
		return "N/A";
	}
}
