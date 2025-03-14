package com.espertech.esper.example.IOT.helpers;
import java.util.List;

public class SimilarityUtils {
    public static double cosineSimilarity(List<Float> features1, List<Float> features2) {
        if (features1 == null || features2 == null || features1.size() != features2.size()) {
            throw new IllegalArgumentException("Feature lists must be non-null and of the same size");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < features1.size(); i++) {
            float f1 = features1.get(i);
            float f2 = features2.get(i);
            dotProduct += f1 * f2;
            normA += f1 * f1;
            normB += f2 * f2;
        }

        return (normA == 0 || normB == 0) ? 0 : (dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)));
    }
}
