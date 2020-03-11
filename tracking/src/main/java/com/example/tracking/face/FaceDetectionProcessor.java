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

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.tracking.FrameMetadata;
import com.example.tracking.GraphicOverlay;
import com.example.tracking.VisionProcessorBase;
import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;

import java.io.IOException;
import java.util.List;

/**
 * Face Detector Demo.
 */
public class FaceDetectionProcessor extends VisionProcessorBase<List<FirebaseVisionFace>> {

    private static final String TAG = "FaceDetectionProcessor";

    private final FirebaseVisionFaceDetector detector;

    public FaceDetectionProcessor(IFaceDetecterListener listener) {
        super(listener);
        FirebaseVisionFaceDetectorOptions options =
                new FirebaseVisionFaceDetectorOptions.Builder()
                        .setMinFaceSize(0.1f)
                        .setClassificationMode(FirebaseVisionFaceDetectorOptions.NO_CLASSIFICATIONS)
                        .enableTracking()
                        .build();
        detector = FirebaseVision.getInstance().getVisionFaceDetector(options);
    }

    @Override
    public void stop() {
        try {
            detector.close();
        } catch (IOException e) {
            Log.e(TAG, "Exception thrown while trying to close Face Detector: " + e);
        }
    }


    @Override
    protected Task<List<FirebaseVisionFace>> detectInImage(FirebaseVisionImage image) {
        return detector.detectInImage(image);
    }

    public interface IFaceDetecterListener<T>{
        /**
         * Callback that executes with a successful detection result.
         *
         //     * @param originalCameraImage hold the original image from camera, used to draw the background
         //     *                            image.
         */
        void onSuccess(
                @NonNull T results,
                @NonNull FrameMetadata frameMetadata,
                @NonNull GraphicOverlay graphicOverlay);

        void onFailure(@NonNull Exception e);

        void onPreview();

        void onCameraFailed();
    }
}
