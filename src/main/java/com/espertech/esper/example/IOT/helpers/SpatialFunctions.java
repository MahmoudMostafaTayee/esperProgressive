package com.espertech.esper.example.IOT.helpers;

import com.espertech.esper.example.IOT.streams.EmbeddingFeature;

public class SpatialFunctions {

    public static float iou(EmbeddingFeature a, EmbeddingFeature b) {
        // Get coordinates for both boxes
        int aX1 = a.getX1(), aY1 = a.getY1();
        int aX2 = a.getX2(), aY2 = a.getY2();
        int bX1 = b.getX1(), bY1 = b.getY1();
        int bX2 = b.getX2(), bY2 = b.getY2();

        // Calculate intersection area
        int xLeft = Math.max(aX1, bX1);
        int yTop = Math.max(aY1, bY1);
        int xRight = Math.min(aX2, bX2);
        int yBottom = Math.min(aY2, bY2);

        if (xRight < xLeft || yBottom < yTop) {
            return 0.0f;
        }

        float intersectionArea = (xRight - xLeft) * (yBottom - yTop);

        // Calculate union area
        float areaA = (aX2 - aX1) * (aY2 - aY1);
        float areaB = (bX2 - bX1) * (bY2 - bY1);
        float unionArea = areaA + areaB - intersectionArea;

        if (unionArea == 0.0f) return 0.0f;  // Prevent division by zero

        return intersectionArea / unionArea;
    }
}
