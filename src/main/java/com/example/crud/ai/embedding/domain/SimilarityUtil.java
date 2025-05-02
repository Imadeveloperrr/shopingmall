package com.example.crud.ai.embedding.domain;

public final class SimilarityUtil {
    public static double cosine(float[] a, float[] b) {
        double dot=0, na=0, nb=0;
        for (int i=0; i<a.length; i++) {
            dot += a[i]*b[i];
            na += a[i]*a[i];
            nb += b[i]*b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
