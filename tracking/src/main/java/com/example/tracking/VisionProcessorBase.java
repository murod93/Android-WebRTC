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
package com.example.tracking;

import android.media.Image;
import android.util.Log;

import androidx.annotation.GuardedBy;

import com.example.tracking.face.FaceDetectionProcessor;
import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;

import java.nio.ByteBuffer;
import java.util.logging.Logger;

/**
 * Abstract base class for ML Kit frame processors. Subclasses need to implement {@link
// * #onSuccess(Bitmap, Object, FrameMetadata, GraphicOverlay)} to define what they want to with
 * the detection results and {@link #detectInImage(FirebaseVisionImage)} to specify the detector
 * object.
 *
 * @param <T> The type of the detected feature.
 */
public abstract class VisionProcessorBase<T> implements VisionImageProcessor {

    private String TAG = VisionProcessorBase.class.getSimpleName();

    // To keep the latest images and its metadata.
    @GuardedBy("this")
    private ByteBuffer latestImage;

    @GuardedBy("this")
    private FrameMetadata latestImageMetaData;

    // To keep the images and metadata in process.
    @GuardedBy("this")
    private ByteBuffer processingImage;

    @GuardedBy("this")

    private FrameMetadata processingMetaData;

    FaceDetectionProcessor.IFaceDetecterListener mListener;

    public VisionProcessorBase() {
    }

    public VisionProcessorBase(FaceDetectionProcessor.IFaceDetecterListener listener) {
        this.mListener = listener;
    }

    @Override
    public synchronized void process(
            byte[] data, final FrameMetadata frameMetadata, final GraphicOverlay
            graphicOverlay) {
        Log.e(TAG, "process()");
        latestImage = ByteBuffer.wrap(data);
        latestImageMetaData = frameMetadata;
        if (processingImage == null && processingMetaData == null) {
            Log.e(TAG, "processLatestImage()");
            processLatestImage(graphicOverlay);
        }
    }

    private synchronized void processLatestImage(final GraphicOverlay graphicOverlay) {
        processingImage = latestImage;
        processingMetaData = latestImageMetaData;
        latestImage = null;
        latestImageMetaData = null;
        if (processingImage != null && processingMetaData != null) {
            Log.e(TAG, "processLatestImage() if");
            processImage(processingImage, processingMetaData, graphicOverlay);
        }
    }

    @Override
    public synchronized void process(
            ByteBuffer data, final FrameMetadata frameMetadata, final GraphicOverlay
            graphicOverlay) {
        latestImage = data;
        latestImageMetaData = frameMetadata;
        if (processingImage == null && processingMetaData == null) {
            processLatestImage(graphicOverlay);
        }
    }

    @Override
    public void process(Image data, FrameMetadata frameMetadata, GraphicOverlay graphicOverlay) {
    }

    private void processImage(
            ByteBuffer data, final FrameMetadata frameMetadata,
            final GraphicOverlay graphicOverlay) {
        FirebaseVisionImageMetadata metadata =
                new FirebaseVisionImageMetadata.Builder()
                        .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
                        .setWidth(frameMetadata.getWidth())
                        .setHeight(frameMetadata.getHeight())
                        .setRotation(frameMetadata.getRotation())
                        .build();

        detectInVisionImage( FirebaseVisionImage.fromByteBuffer(data, metadata), frameMetadata,
                graphicOverlay);
    }

    private void detectInVisionImage(
            FirebaseVisionImage image,
            final FrameMetadata metadata,
            final GraphicOverlay graphicOverlay) {

        detectInImage(image)
                .addOnSuccessListener(
                        results -> {
                            mListener.onSuccess(results, metadata, graphicOverlay);
                            processLatestImage(graphicOverlay);
                        })
                .addOnFailureListener(
                        e -> {
                            Log.e(TAG, "onFace detection error");
                            mListener.onFailure(e);
                        });
    }

    @Override
    public void stop() {
    }

    protected abstract Task<T> detectInImage(FirebaseVisionImage image);
}
