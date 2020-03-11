package com.example.tracking;

import android.annotation.SuppressLint;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.tracking.face.FaceDetectionProcessor;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;

import java.util.List;

public class FrameProcessingTask{
    private final String TAG = this.getClass().getSimpleName();

    public FrameProcessingTask(){
        startThread();
    }

    private static FrameProcessingTask instance;

    public static FrameProcessingTask getInstance(){
        if (instance == null){
            instance = new FrameProcessingTask();
        }
        return instance;
    }

    public FrameProcessingTask setWidth(int width){
        this.width = width;
        return this;
    }

    public FrameProcessingTask setHeight(int height){
        this.height = height;
        return this;
    }

    public FrameProcessingTask setRotation(int rotation){
        this.rotation = rotation;
        return this;
    }

    public FrameProcessingTask setCameraFacing(int cameraFacing){
        this.cameraFacing = cameraFacing;
        return this;
    }

    public void processFrame(byte[] data){
        if (processingRunnable!=null){
            processingRunnable.setNextFrame(data);
        }
    }

    private Thread processingThread;
    private FrameProcessingRunnable processingRunnable;
    private VisionImageProcessor frameProcessor;
    private final Object processorLock = new Object();

    // Tracking required fields
    private int width;
    private int height;
    private int rotation;
    private int cameraFacing;
    private DETECTOR detector = DETECTOR.FACE;

    private GraphicOverlay mGraphicOverlay;

    /**
     * Stops the camera and releases the resources of the camera and underlying detector.
     */
    public void release() {
        synchronized (processorLock) {
            stopThread();
            processingRunnable.release();
//            cleanScreen();

//            isInitialized = false;

            if (frameProcessor != null) {
                frameProcessor.stop();
            }
        }
    }

    private void setMLFrameProcessor(VisionImageProcessor processor) {
        synchronized (processorLock) {
//            cleanScreen();
            if (frameProcessor != null) {
                frameProcessor.stop();
            }
            frameProcessor = processor;
        }
    }

    private FaceDetectionProcessor.IFaceDetecterListener<List<FirebaseVisionFace>> faceDetecterListener = new FaceDetectionProcessor.IFaceDetecterListener<List<FirebaseVisionFace>>() {
        @Override
        public void onSuccess(@NonNull List<FirebaseVisionFace> results, @NonNull FrameMetadata frameMetadata, @NonNull GraphicOverlay graphicOverlay) {
            Log.e(TAG, "Face is detected: "+results.size());
        }

        @Override
        public void onFailure(@NonNull Exception e) {
            Log.e(TAG, "onFailure");
        }

        @Override
        public void onPreview() {
        }

        @Override
        public void onCameraFailed() {

        }
    };

    private void startThread(){
        processingRunnable = new FrameProcessingRunnable();
        processingThread = new Thread(processingRunnable);
        processingRunnable.setActive(true);
        processingThread.start();

        //start face detection processor
        setMLFrameProcessor(new FaceDetectionProcessor(faceDetecterListener));
    }

    private void stopThread(){
        if (processingRunnable!=null){
            processingRunnable.setActive(false);
        }

        if (processingThread != null) {
            try {
                // Wait for the thread to complete to ensure that we can't have multiple threads
                // executing at the same time (i.e., which would happen if we called init too
                // quickly after stop).
                processingThread.join();
            } catch (InterruptedException e) {
                Log.d(TAG, "Frame processing thread interrupted on release.");
            }
            processingThread = null;
        }
    }

    private class FrameProcessingRunnable implements Runnable{

        // This lock guards all of the member variables below.
        private final Object lock = new Object();
        private boolean active = true;

        // These pending variables hold the state associated with the new frame awaiting processing.
        private byte[] pendingFrameData;

        FrameProcessingRunnable() {}

        /**
         * Releases the underlying receiver. This is only safe to do after the associated thread has
         * completed, which is managed in camera source's release method above.
         */
        @SuppressLint("Assert")
        void release() {
            assert (processingThread.getState() == Thread.State.TERMINATED);
        }

        /** Marks the runnable as active/not active. Signals any blocked threads to continue. */
        void setActive(boolean active) {
            synchronized (lock) {
                this.active = active;
                lock.notifyAll();
            }
        }

        /**
         * Sets the frame data received from the camera. This adds the previous unused frame buffer (if
         * present) back to the camera, and keeps a pending reference to the frame data for future use.
         */
        public void setNextFrame(byte[] data) {
            if (data == null)return;
            synchronized (lock) {
                if (pendingFrameData != null) {
                    pendingFrameData = null;
                }

                pendingFrameData =  data;
                // Notify the processor thread if it is waiting on the next frame (see below).
                lock.notifyAll();
            }
        }

        @SuppressLint("InlinedApi")
        @SuppressWarnings("GuardedBy")
        @Override
        public void run() {
            byte[] data;

            while (true) {
                Log.d(TAG,"run");
                synchronized (lock) {
                    Log.d(TAG,"run lock");
                    while (active && (pendingFrameData == null)) {
                        try {
                            Log.d(TAG,"run wait");
                            // Wait for the next frame to be received from the camera, since we
                            // don't have it yet.
                            lock.wait();
                        } catch (InterruptedException e) {
                            Log.e(TAG, "Frame processing loop terminated.", e);
                            return;
                        }
                    }

                    if (!active) {
                        Log.d(TAG,"!active");
                        // Exit the loop once this camera source is stopped or released.  We check
                        // this here, immediately after the wait() above, to handle the case where
                        // setActive(false) had been called, triggering the termination of this
                        // loop.
                        return;
                    }

                    // Hold onto the frame data locally, so that we can use this for detection
                    // below.  We need to clear pendingFrameData to ensure that this buffer isn't
                    // recycled back to the camera before we are done using that data.
                    data = pendingFrameData;
                    pendingFrameData = null;
                }

                // The code below needs to run outside of synchronization, because this will allow
                // the camera to add pending frame(s) while we are running detection on the current
                // frame.
                try {
                    if (detector == DETECTOR.FACE) {
                        synchronized (processorLock) {
                            frameProcessor.process(
                                    data,
                                    new FrameMetadata.Builder()
                                            .setWidth(width)
                                            .setHeight(height)
                                            .setRotation(rotation)
                                            .setCameraFacing(cameraFacing)
                                            .build(),
                                    mGraphicOverlay);
                        }
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "Exception thrown from receiver.", t);
                }
            }
        }
    }


}