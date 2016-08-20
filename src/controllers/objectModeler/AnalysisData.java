package controllers.objectModeler;

import java.io.FileWriter;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Collection;

public class AnalysisData implements Serializable {

	private static final long serialVersionUID = 2247806542398637052L;
	public final String src_game;
	public final String trg_game;
	public final String src_obj;
	public final String trg_obj;
	public final int src_training_epochs;
	public final int trg_training_epochs;
	public final int testing_epochs;
	public final double scratch_orig_LL;
	public final double training_random_LL;
	public final double training_scratch_LL;
	public final double training_reuse_LL;
	public final double training_retrain_LL;
	public final double test_random_LL;
	public final double test_scratch_LL;
	public final double test_reuse_LL;
	public final double test_retrain_LL;
	public final double test_ensemble_LL;

	public AnalysisData(String srcGame, String trgGame, String srcObj, String trgObj, int numSrcTrainingEpochs,
			int numTrgTrainingEpochs, int numTestingEpochs, double scratchOriginalLL, double randomTrainingLL,
			double scratchTrainingLL, double transferTrainingLL, double transferReTrainingLL, double randomTestingLL,
			double scratchTestingLL, double transferTestingLL, double transferReTestingLL, double ensembleTestingLL) {
				this.src_game = srcGame;
				this.trg_game = trgGame;
				this.src_obj = srcObj;
				this.trg_obj = trgObj;
				this.src_training_epochs = numSrcTrainingEpochs;
				this.trg_training_epochs = numTrgTrainingEpochs;
				this.testing_epochs = numTestingEpochs;
				this.scratch_orig_LL = scratchOriginalLL;
				this.training_random_LL = randomTrainingLL;
				this.training_scratch_LL = scratchTrainingLL;
				this.training_reuse_LL = transferTrainingLL;
				this.training_retrain_LL = transferReTrainingLL;
				this.test_random_LL = randomTestingLL;
				this.test_scratch_LL = scratchTestingLL;
				this.test_reuse_LL = transferTestingLL;
				this.test_retrain_LL = transferReTestingLL;
				this.test_ensemble_LL = ensembleTestingLL;
	}
	
	public static void report(Collection<AnalysisData> data) {
		StringBuilder sb = new StringBuilder();
		try {
			Field[] fields = AnalysisData.class.getFields();
			for (Field field : fields) sb.append(field.getName() + "	");
			sb.append("\n");
			for (AnalysisData datum : data) {
				for (Field field : fields) sb.append(field.get(datum) + "	");
				sb.append("\n");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println(sb.toString());
	}
	
	public static void saveCSV(Collection<AnalysisData> data, String name) {
		try {
			FileWriter writer = new FileWriter(name + ".csv");
			Field[] fields = AnalysisData.class.getFields();
			for (Field field : fields) writer.append(field.getName() + ",");
			writer.append("\n");
			for (AnalysisData datum : data) {
				for (Field field : fields) writer.append(field.get(datum) + ",");
				writer.append("\n");
			}
			writer.flush();
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static class Save implements Serializable {
		private static final long serialVersionUID = -7190766837293792863L;
		private final Collection<AnalysisData> data;
		public Save(Collection<AnalysisData> data) {
			this.data = data;
		}
		public Collection<AnalysisData> getData() {
			return data;
		}
		public void report() {
			AnalysisData.report(data);
		}
	}
}
