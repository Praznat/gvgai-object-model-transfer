package controllers.objectModeler;

import java.io.Serializable;
import java.util.Collection;

public class TrainedObjectModels implements Serializable {

	private static final long serialVersionUID = -2598644198679402763L;
	private final Collection<ObjectClassModel> models;
	
	public TrainedObjectModels(Collection<ObjectClassModel> models) {
		this.models = models;
	}
	
	public Collection<ObjectClassModel> getModels() {
		return models;
	}
}
