package com.example.tracking;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

public class PinchDetector extends ScaleGestureDetector.SimpleOnScaleGestureListener {

    private CustomPinchListener listener;
    private ScaleGestureDetector detector;
    private Context context;
    private boolean isPinching;

    private static final double DEFAULT_ZOOM = 1.0;

    private static final int DP_PER_ZOOM_FACTOR = 500;
    public float scaleFactor = 1.0f;
    private float startingSpan;
    private double intermediateZoomLevel;
    private double zoomLevel;
    private final static double maxZoom = 4.0;

    public PinchDetector(Context context, CustomPinchListener listener) {
        this.context = context;
        this.detector = new ScaleGestureDetector(context,  this);
        this.listener = listener;
    }

    public boolean onTouchEvent(MotionEvent event){
        this.detector.onTouchEvent(event);
        return true;
    }

    public boolean isPinching() {
        return isPinching;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        this.listener.onPinchBegin();
        isPinching = true;
        startingSpan = detector.getCurrentSpan();

        return super.onScaleBegin(detector);
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        super.onScaleEnd(detector);
        zoomLevel = intermediateZoomLevel;
        isPinching = false;
        this.listener.onPinchEnd();
    }

    public void reset() {
        zoomLevel = DEFAULT_ZOOM;
        scaleFactor = 1.0f;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {

        float currentSpan = detector.getCurrentSpan();
        double distanceChange = currentSpan - startingSpan;
        double zoomLevelChange = (distanceChange / DP_PER_ZOOM_FACTOR);

        // Clamp the zoom level to valid intervals.
        intermediateZoomLevel = Math.min(Math.max(zoomLevel + zoomLevelChange, DEFAULT_ZOOM), maxZoom);
        this.listener.onPinch(intermediateZoomLevel);

        return true;
    }

    public void setCurrentZoom(double zoom) {
        zoomLevel = zoom;
    }


    public interface CustomPinchListener {
        void onPinch(double scale);
        void onPinchEnd();
        void onPinchBegin();
    }
}
