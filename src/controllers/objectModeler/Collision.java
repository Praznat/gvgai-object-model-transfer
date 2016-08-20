package controllers.objectModeler;

public class Collision {
	
	final ObjectClassModel collider;
	final Direction placement;
	final Direction originDirection; // unused... this is where the collider came from in the previous step
	
	public Collision(ObjectClassModel collider, Direction placement, Direction originDirection) {
		this.collider = collider;
		this.placement = placement;
		this.originDirection = originDirection;
	}
	
	@Override
	public int hashCode() {
		return 31 * collider.hashCode() + placement.hashCode();
	}
	@Override
	public boolean equals(Object other) {
		Collision oc = (Collision)other;
		return this.collider.getItype() == oc.collider.getItype() && this.placement.equals(oc.placement);
	}
	@Override
	public String toString() {
		return "Collision" + collider.getItype() + placement;
	}
}
