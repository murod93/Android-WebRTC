/*
 *  Copyright 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.tracking.DETECTOR;
import com.example.tracking.FrameMetadata;
import com.example.tracking.GraphicOverlay;
import com.example.tracking.VisionImageProcessor;
import com.example.tracking.face.FaceDetectionProcessor;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("deprecation")
abstract class CameraCapturer implements CameraVideoCapturer {
    enum SwitchState {
        IDLE, // No switch requested.
        PENDING, // Waiting for previous capture session to open.
        IN_PROGRESS, // Waiting for new switched capture session to start.
    }

    private static final String TAG = "CameraCapturer";
    private final static int MAX_OPEN_CAMERA_ATTEMPTS = 3;
    private final static int OPEN_CAMERA_DELAY_MS = 500;
    private final static int OPEN_CAMERA_TIMEOUT = 10000;

    private final CameraEnumerator cameraEnumerator;
    private final CameraEventsHandler eventsHandler;
    private final Handler uiThreadHandler;

    // face tracking
    private Thread processingThread;
    private final Object processorLock = new Object();

    private FrameProcessingRunnable processingRunnable = new FrameProcessingRunnable();
    private VisionImageProcessor frameProcessor;

    private GraphicOverlay mGraphicOverlay;

    private DETECTOR detector = DETECTOR.FACE;

    FaceDetectionProcessor.IFaceDetecterListener<List<FirebaseVisionFace>> faceDetecterListener = new FaceDetectionProcessor.IFaceDetecterListener<List<FirebaseVisionFace>>() {
        @Override
        public void onSuccess(@NonNull List<FirebaseVisionFace> results, @NonNull FrameMetadata frameMetadata, @NonNull GraphicOverlay graphicOverlay) {
            Logging.e(TAG, "Face is detected: "+results.size());
        }

        @Override
        public void onFailure(@NonNull Exception e) {
            Logging.e(TAG, "onFailure");
        }

        @Override
        public void onPreview() {
        }

        @Override
        public void onCameraFailed() {

        }
    };

    public void setMachineLearningFrameProcessor(VisionImageProcessor processor) {
        synchronized (processorLock) {
//            cleanScreen();
            if (frameProcessor != null) {
                frameProcessor.stop();
            }
            frameProcessor = processor;
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
        void setNextFrame(byte[] data) {
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
                Logging.d(TAG,"run");
                synchronized (lock) {
                    Logging.d(TAG,"run lock");
                    while (active && (pendingFrameData == null)) {
                        try {
                            Logging.d(TAG,"run wait");
                            // Wait for the next frame to be received from the camera, since we
                            // don't have it yet.
                            lock.wait();
                        } catch (InterruptedException e) {
                            Log.e(TAG, "Frame processing loop terminated.", e);
                            return;
                        }
                    }

                    if (!active) {
                        Logging.d(TAG,"!active");
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
                                            .setRotation(0)
                                            .setCameraFacing(Camera.CameraInfo.CAMERA_FACING_FRONT)//(isFrontCamera ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK)
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

    private final CameraSession.CreateSessionCallback createSessionCallback =
            new CameraSession.CreateSessionCallback() {
                @Override
                public void onDone(CameraSession session) {
                    checkIsOnCameraThread();
                    Logging.d(TAG, "Create session done. Switch state: " + switchState);
                    uiThreadHandler.removeCallbacks(openCameraTimeoutRunnable);
                    synchronized (stateLock) {
                        capturerObserver.onCapturerStarted(true /* success */);
                        sessionOpening = false;
                        currentSession = session;
                        cameraStatistics = new CameraStatistics(surfaceHelper, eventsHandler);
                        firstFrameObserved = false;
                        stateLock.notifyAll();

                        if (switchState == SwitchState.IN_PROGRESS) {
                            switchState = SwitchState.IDLE;
                            if (switchEventsHandler != null) {
                                switchEventsHandler.onCameraSwitchDone(cameraEnumerator.isFrontFacing(cameraName));
                                switchEventsHandler = null;
                            }
                        } else if (switchState == SwitchState.PENDING) {
                            switchState = SwitchState.IDLE;
                            switchCameraInternal(switchEventsHandler);
                        }
                    }
                }

                @Override
                public void onFailure(CameraSession.FailureType failureType, String error) {
                    checkIsOnCameraThread();
                    uiThreadHandler.removeCallbacks(openCameraTimeoutRunnable);
                    synchronized (stateLock) {
                        capturerObserver.onCapturerStarted(false /* success */);
                        openAttemptsRemaining--;

                        if (openAttemptsRemaining <= 0) {
                            Logging.w(TAG, "Opening camera failed, passing: " + error);
                            sessionOpening = false;
                            stateLock.notifyAll();

                            if (switchState != SwitchState.IDLE) {
                                if (switchEventsHandler != null) {
                                    switchEventsHandler.onCameraSwitchError(error);
                                    switchEventsHandler = null;
                                }
                                switchState = SwitchState.IDLE;
                            }

                            if (failureType == CameraSession.FailureType.DISCONNECTED) {
                                eventsHandler.onCameraDisconnected();
                            } else {
                                eventsHandler.onCameraError(error);
                            }
                        } else {
                            Logging.w(TAG, "Opening camera failed, retry: " + error);
                            createSessionInternal(OPEN_CAMERA_DELAY_MS);
                        }
                    }
                }
            };


    private final CameraSession.Events cameraSessionEventsHandler = new CameraSession.Events() {
        @Override
        public void onCameraOpening() {
            checkIsOnCameraThread();
            synchronized (stateLock) {
                if (currentSession != null) {
                    Logging.w(TAG, "onCameraOpening while session was open.");
                    return;
                }
                eventsHandler.onCameraOpening(cameraName);
                //added by me
                processingThread = new Thread(processingRunnable);
                processingRunnable.setActive(true);
                processingThread.start();
            }
        }

        @Override
        public void onCameraError(CameraSession session, String error) {
            checkIsOnCameraThread();
            synchronized (stateLock) {
                if (session != currentSession) {
                    Logging.w(TAG, "onCameraError from another session: " + error);
                    return;
                }
                eventsHandler.onCameraError(error);
                stopCapture();
            }
        }

        @Override
        public void onCameraDisconnected(CameraSession session) {
            checkIsOnCameraThread();
            synchronized (stateLock) {
                if (session != currentSession) {
                    Logging.w(TAG, "onCameraDisconnected from another session.");
                    return;
                }
                eventsHandler.onCameraDisconnected();
                stopCapture();
            }
        }

        @Override
        public void onCameraClosed(CameraSession session) {
            checkIsOnCameraThread();
            synchronized (stateLock) {
                if (session != currentSession && currentSession != null) {
                    Logging.d(TAG, "onCameraClosed from another session.");
                    return;
                }
                eventsHandler.onCameraClosed();
            }
        }

        @Override
        public void onFrameCaptured(CameraSession session, VideoFrame frame) {
            checkIsOnCameraThread();
            synchronized (stateLock) {
                if (session != currentSession) {
                    Logging.w(TAG, "onFrameCaptured from another session.");
                    return;
                }
                if (!firstFrameObserved) {
                    eventsHandler.onFirstFrameAvailable();
                    firstFrameObserved = true;
                }

                cameraStatistics.addFrame();
                capturerObserver.onFrameCaptured(frame);
            }
        }

        /**
         * Added by Murodjon 2020.03.10
         * This function returns the frame sequences for processing
         */
        @Override
        public void onProcessingFrame(byte[] data) {
            Logging.d(TAG, "onImageAvailable");
            if (eventsHandler!=null){
                eventsHandler.onProcessingFrame(width, height, 1, 1, data);
            }
//            if(processingRunnable!=null){
//                    processingRunnable.setNextFrame(data);
//            }

        }
    };

    private final Runnable openCameraTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            eventsHandler.onCameraError("Camera failed to start within timeout.");
        }
    };

    // Initialized on initialize
    // -------------------------
    private Handler cameraThreadHandler;
    private Context applicationContext;
    private org.webrtc.CapturerObserver capturerObserver;
    private SurfaceTextureHelper surfaceHelper;

    private final Object stateLock = new Object();
    private boolean sessionOpening; /* guarded by stateLock */
    private CameraSession currentSession; /* guarded by stateLock */
    private String cameraName; /* guarded by stateLock */
    private int width; /* guarded by stateLock */
    private int height; /* guarded by stateLock */
    private int framerate; /* guarded by stateLock */
    private int openAttemptsRemaining; /* guarded by stateLock */
    private SwitchState switchState = SwitchState.IDLE; /* guarded by stateLock */
    private CameraSwitchHandler switchEventsHandler; /* guarded by stateLock */
    // Valid from onDone call until stopCapture, otherwise null.
    private CameraStatistics cameraStatistics; /* guarded by stateLock */
    private boolean firstFrameObserved; /* guarded by stateLock */

    public CameraCapturer(String cameraName, CameraEventsHandler eventsHandler,
                          CameraEnumerator cameraEnumerator) {
//        setMachineLearningFrameProcessor(new FaceDetectionProcessor(faceDetecterListener));
        if (eventsHandler == null) {
            eventsHandler = new CameraEventsHandler() {
                @Override
                public void onCameraError(String errorDescription) {
                }

                @Override
                public void onCameraDisconnected() {
                }

                @Override
                public void onCameraFreezed(String errorDescription) {
                }

                @Override
                public void onCameraOpening(String cameraName) {
                }

                @Override
                public void onFirstFrameAvailable() {
                }

                @Override
                public void onCameraClosed() {
                }

                @Override
                public void onProcessingFrame(int width, int height, int rotation, int cameraFacing, byte[] data) {

                }
            };
        }

        this.eventsHandler = eventsHandler;
        this.cameraEnumerator = cameraEnumerator;
        this.cameraName = cameraName;
        uiThreadHandler = new Handler(Looper.getMainLooper());

        final String[] deviceNames = cameraEnumerator.getDeviceNames();

        if (deviceNames.length == 0) {
            throw new RuntimeException("No cameras attached.");
        }
        if (!Arrays.asList(deviceNames).contains(this.cameraName)) {
            throw new IllegalArgumentException(
                    "Camera name " + this.cameraName + " does not match any known camera device.");
        }
    }

    @Override
    public void initialize(SurfaceTextureHelper surfaceTextureHelper,
                           Context applicationContext, org.webrtc.CapturerObserver capturerObserver) {
        this.applicationContext = applicationContext;
        this.capturerObserver = capturerObserver;
        this.surfaceHelper = surfaceTextureHelper;
        this.cameraThreadHandler =
                surfaceTextureHelper == null ? null : surfaceTextureHelper.getHandler();
    }

    @Override
    public void startCapture(int width, int height, int framerate) {
        if (applicationContext == null) {
            throw new RuntimeException("CameraCapturer must be initialized before calling startCapture.");
        }

        synchronized (stateLock) {
            if (sessionOpening || currentSession != null) {
                Logging.w(TAG, "Session already open");
                return;
            }

            this.width = width;
            this.height = height;
            this.framerate = framerate;

            sessionOpening = true;
            openAttemptsRemaining = MAX_OPEN_CAMERA_ATTEMPTS;
            createSessionInternal(0);
        }
    }

    private void createSessionInternal(int delayMs) {
        uiThreadHandler.postDelayed(openCameraTimeoutRunnable, delayMs + OPEN_CAMERA_TIMEOUT);
        cameraThreadHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                createCameraSession(createSessionCallback, cameraSessionEventsHandler, applicationContext,
                        surfaceHelper, cameraName, width, height, framerate);
            }
        }, delayMs);
    }

    @Override
    public void stopCapture() {
        Logging.d(TAG, "Stop capture");

        synchronized (stateLock) {
            while (sessionOpening) {
                Logging.d(TAG, "Stop capture: Waiting for session to open");
                try {
                    stateLock.wait();
                } catch (InterruptedException e) {
                    Logging.w(TAG, "Stop capture interrupted while waiting for the session to open.");
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            if (currentSession != null) {
                Logging.d(TAG, "Stop capture: Nulling session");
                cameraStatistics.release();
                cameraStatistics = null;
                final CameraSession oldSession = currentSession;
                cameraThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        oldSession.stop();
                    }
                });
                currentSession = null;
                capturerObserver.onCapturerStopped();
            } else {
                Logging.d(TAG, "Stop capture: No session open");
            }
        }

        Logging.d(TAG, "Stop capture done");
    }

    @Override
    public void changeCaptureFormat(int width, int height, int framerate) {
        Logging.d(TAG, "changeCaptureFormat: " + width + "x" + height + "@" + framerate);
        synchronized (stateLock) {
            stopCapture();
            startCapture(width, height, framerate);
        }
    }

    @Override
    public void dispose() {
        Logging.d(TAG, "dispose");
        stopCapture();
    }

    @Override
    public void switchCamera(final CameraSwitchHandler switchEventsHandler) {
        Logging.d(TAG, "switchCamera");
        cameraThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                switchCameraInternal(switchEventsHandler);
            }
        });
    }

    @Override
    public boolean isScreencast() {
        return false;
    }

    public void printStackTrace() {
        Thread cameraThread = null;
        if (cameraThreadHandler != null) {
            cameraThread = cameraThreadHandler.getLooper().getThread();
        }
        if (cameraThread != null) {
            StackTraceElement[] cameraStackTrace = cameraThread.getStackTrace();
            if (cameraStackTrace.length > 0) {
                Logging.d(TAG, "CameraCapturer stack trace:");
                for (StackTraceElement traceElem : cameraStackTrace) {
                    Logging.d(TAG, traceElem.toString());
                }
            }
        }
    }

    private void reportCameraSwitchError(
            String error, CameraSwitchHandler switchEventsHandler) {
        Logging.e(TAG, error);
        if (switchEventsHandler != null) {
            switchEventsHandler.onCameraSwitchError(error);
        }
    }

    private void switchCameraInternal(final CameraSwitchHandler switchEventsHandler) {
        Logging.d(TAG, "switchCamera internal");

        final String[] deviceNames = cameraEnumerator.getDeviceNames();

        if (deviceNames.length < 2) {
            if (switchEventsHandler != null) {
                switchEventsHandler.onCameraSwitchError("No camera to switch to.");
            }
            return;
        }

        synchronized (stateLock) {
            if (switchState != SwitchState.IDLE) {
                reportCameraSwitchError("Camera switch already in progress.", switchEventsHandler);
                return;
            }
            if (!sessionOpening && currentSession == null) {
                reportCameraSwitchError("switchCamera: camera is not running.", switchEventsHandler);
                return;
            }

            this.switchEventsHandler = switchEventsHandler;
            if (sessionOpening) {
                switchState = SwitchState.PENDING;
                return;
            } else {
                switchState = SwitchState.IN_PROGRESS;
            }

            Logging.d(TAG, "switchCamera: Stopping session");
            cameraStatistics.release();
            cameraStatistics = null;
            final CameraSession oldSession = currentSession;
            cameraThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    oldSession.stop();
                }
            });
            currentSession = null;

            int cameraNameIndex = Arrays.asList(deviceNames).indexOf(cameraName);
            cameraName = deviceNames[(cameraNameIndex + 1) % deviceNames.length];

            sessionOpening = true;
            openAttemptsRemaining = 1;
            createSessionInternal(0);
        }
        Logging.d(TAG, "switchCamera done");
    }

    private void checkIsOnCameraThread() {
        if (Thread.currentThread() != cameraThreadHandler.getLooper().getThread()) {
            Logging.e(TAG, "Check is on camera thread failed.");
            throw new RuntimeException("Not on camera thread.");
        }
    }

    protected String getCameraName() {
        synchronized (stateLock) {
            return cameraName;
        }
    }

    abstract protected void createCameraSession(
            CameraSession.CreateSessionCallback createSessionCallback, CameraSession.Events events,
            Context applicationContext, SurfaceTextureHelper surfaceTextureHelper, String cameraName,
            int width, int height, int framerate);
}
