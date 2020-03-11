// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.example.tracking.face;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import com.example.tracking.GraphicOverlay;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;

/**
 * Graphic instance for rendering face position, orientation, and landmarks within an associated
 * graphic overlay view.
 */
public class FaceGraphic extends GraphicOverlay.Graphic {
    private static final float ID_TEXT_SIZE = 30.0f;
    private static final float BOX_STROKE_WIDTH = 5.0f;

    private final Paint activeFacePaint;
    private final Paint inactiveFacePaint;

    private final Paint facePositionPaint;
    private final Paint idPaint;
    private final Paint boxPaint;

    private volatile FirebaseVisionFace firebaseVisionFace;

    public FaceGraphic(GraphicOverlay overlay, FirebaseVisionFace face) {
        super(overlay);
        firebaseVisionFace = face;
        final int selectedColor = Color.WHITE;

        activeFacePaint = new Paint();
        activeFacePaint.setColor(Color.RED);
        activeFacePaint.setStyle(Paint.Style.STROKE);
        activeFacePaint.setStrokeWidth(BOX_STROKE_WIDTH);

        inactiveFacePaint = new Paint();
        inactiveFacePaint.setColor(Color.GRAY);
        inactiveFacePaint.setStyle(Paint.Style.STROKE);
        inactiveFacePaint.setStrokeWidth(BOX_STROKE_WIDTH);

        facePositionPaint = new Paint();
        facePositionPaint.setColor(selectedColor);

        idPaint = new Paint();
        idPaint.setColor(selectedColor);
        idPaint.setTextSize(ID_TEXT_SIZE);

        boxPaint = new Paint();
        boxPaint.setColor(selectedColor);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(BOX_STROKE_WIDTH);
    }

    /**
     * Draws the face annotations for position on the supplied canvas.
     */
    @Override
    public void draw(Canvas canvas) {
        FirebaseVisionFace face = firebaseVisionFace;
        if (face == null) {
            return;
        }

        // Draws a circle at the position of the detected face, with the face's track id below.
        float x = translateX(face.getBoundingBox().centerX());
        float y = translateY(face.getBoundingBox().centerY());

        final float xOffset = scaleX(face.getBoundingBox().width() / 2.0f);
        final float yOffset = scaleY(face.getBoundingBox().height() / 2.0f);

        final float left = x - xOffset;
        final float top = y - yOffset;
        final float right = x + xOffset;
        final float bottom = y + yOffset;

        float padding = Math.abs(bottom - top)/(2*10);
        if (padding<5)padding = 2;

        final RectF rect = new RectF(left, top, right, bottom);
        final RectF rect2 = new RectF(left-padding, top-padding, right+padding, bottom+padding);

        activeFacePaint.setStrokeWidth(padding/2);
        inactiveFacePaint.setStrokeWidth(padding/2);

        canvas.drawArc(rect, 270, 270, false, activeFacePaint);
        canvas.drawArc(rect2, 90, 270, false, activeFacePaint);
    }
}
