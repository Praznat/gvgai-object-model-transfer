package controllers.objectModeler;

/** a Vision is just the visually observable properties of an object instance 
 * its type (name) and location (c, r) */
public class Vision {
	String name;
	int c;
	int r;

	public Vision(ObjectInstance obj) {
		this(obj.getItype(), obj.getC(), obj.getR());
	}
	
	public Vision(String name, int c, int r) {
		this.name = name;
		this.c = c;
		this.r = r;
	}
	
	@Override
	public int hashCode() {
		return toString().hashCode();
	}
	@Override
	public boolean equals(Object other) {
		Vision v = (Vision) other;
		return this.toString().equals(v.toString());
	}
	@Override
	public String toString() {
		return name + "@" + c + "," + r;
	}
}