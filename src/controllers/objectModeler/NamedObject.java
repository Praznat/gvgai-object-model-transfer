package controllers.objectModeler;

import java.io.Serializable;

public class NamedObject implements Serializable {
	private static final long serialVersionUID = 4398362091363178063L;
	private final String name;
	private final Object object;
	public NamedObject(String name, Object object) {
		this.name = name;
		this.object = object;
	}
	public String getName() {
		return name;
	}
	public Object getObject() {
		return object;
	}
	@Override
	public int hashCode() {
		return name.hashCode();
	}
	@Override
	public boolean equals(Object other) {
		return this.getName().equals(((NamedObject)other).getName());
	}
	@Override
	public String toString() {
		return name + object;
	}
}
