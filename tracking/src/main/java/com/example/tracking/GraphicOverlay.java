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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.google.android.gms.vision.CameraSource;

import java.util.ArrayList;
import java.util.List;

/**
 * A view which renders a series of custom graphics to be overlayed on top of an associated preview
 * (i.e., the camera preview). The creator can add graphics objects, update the objects, and remove
 * them, triggering the appropriate drawing and invalidation within the view.
 *
 * <p>Supports scaling and mirroring of the graphics relative the camera's preview properties. The
 * idea is that detection items are expressed in terms of a preview size, but need to be scaled up
 * to the full view size, and also mirrored in the case of the front-facing camera.
 *
 * <p>Associated {@link Graphic} items should use the following methods to convert to view
 * coordinates for the graphics that are drawn:
 *
 * <ol>
 *   <li>{@link Graphic#scaleX(float)} and {@link Graphic#scaleY(float)} adjust the size of the
 *       supplied value from the preview scale to the view scale.
 *   <li>{@link Graphic#translateX(float)} and {@link Graphic#translateY(float)} adjust the
 *       coordinate from the preview's coordinate system to the view coordinate system.
 * </ol>
 */
public class GraphicOverlay extends View {
  private final float SCROLL_THRESHOLD = 10;
  private final float MAX_CLICK_DURATION = 200;
  private float startClickTime;

  DETECTOR detection = DETECTOR.NONE;
  private final Object lock = new Object();
  private int previewWidth;
  private float widthScaleFactor = 1.0f;
  private int previewHeight;
  private float heightScaleFactor = 1.0f;
  private int facing = CameraSource.CAMERA_FACING_BACK;
  private final List<Graphic> graphics = new ArrayList<>();
  private boolean disableTouch = false;

  private boolean timerEnabled;
  private boolean pressed = false;
  private boolean movement = false;
  private boolean singleTap = false;

  private float startPointX = 0;
  private float startPointY = 0;
  private float endPointX = 0;
  private float endPointY = 0;


  private Paint prePaint;
  public Paint trackPaint;

//  private IObjectSelectListener mListener;


  private PinchDetector mPinchDetector;
  private boolean pinchScroll;

  public void setPinchDetector(PinchDetector mPinchDetector) {
    this.mPinchDetector = mPinchDetector;
  }


  /**
   * Base class for a custom graphics object to be rendered within the graphic overlay. Subclass
   * this and implement the {@link Graphic#draw(Canvas)} method to define the graphics element. Add
   * instances to the overlay using {@link GraphicOverlay#add(Graphic)}.
   */
  public abstract static class Graphic {
    private GraphicOverlay overlay;

    public Graphic(GraphicOverlay overlay) {
      this.overlay = overlay;
    }

    /**
     * Draw the graphic on the supplied canvas. Drawing should use the following methods to convert
     * to view coordinates for the graphics that are drawn:
     *
     * <ol>
     *   <li>{@link Graphic#scaleX(float)} and {@link Graphic#scaleY(float)} adjust the size of the
     *       supplied value from the preview scale to the view scale.
     *   <li>{@link Graphic#translateX(float)} and {@link Graphic#translateY(float)} adjust the
     *       coordinate from the preview's coordinate system to the view coordinate system.
     * </ol>
     *
     * @param canvas drawing canvas
     */
    public abstract void draw(Canvas canvas);

    public int previewWidth(){
      return overlay.getWidth();
    }

    public int previewHeight(){
      return overlay.getHeight();
    }

    /**
     * Adjusts a horizontal value of the supplied value from the preview scale to the view scale.
     */
    public float scaleX(float horizontal) {
      return horizontal * overlay.widthScaleFactor;
    }

    /** Adjusts a vertical value of the supplied value from the preview scale to the view scale. */
    public float scaleY(float vertical) {
      return vertical * overlay.heightScaleFactor;
    }

    /** Returns the application context of the app. */
    public Context getApplicationContext() {
      return overlay.getContext().getApplicationContext();
    }

    /**
     * Adjusts the x coordinate from the preview's coordinate system to the view coordinate system.
     */
    public float translateX(float x) {
      if (overlay.facing == CameraSource.CAMERA_FACING_FRONT) {
        return overlay.getWidth() - scaleX(x);
      } else {
        return scaleX(x);
      }
    }

    /**
     * Adjusts the x coordinate from the preview's coordinate system to the view coordinate system.
     */
    public float translateX2(float y) {
      if (overlay.facing == CameraSource.CAMERA_FACING_FRONT) {
        return overlay.getWidth() - scaleX(y);
      } else {
        return overlay.getWidth() - scaleX(y);
      }
    }

    public float translateX3(float y){
      if (overlay.facing == CameraSource.CAMERA_FACING_FRONT) {
        return scaleX(y);
      } else {
        return scaleX(y);
      }
    }

    /**
     * Adjusts the y coordinate from the preview's coordinate system to the view coordinate system.
     */
    public float translateY(float y) {
      return scaleY(y);
    }

    /**
     * Adjusts the y coordinate from the preview's coordinate system to the view coordinate system.
     */
    public float translateY2(float x) {
      if (overlay.facing == CameraSource.CAMERA_FACING_FRONT){
        return overlay.getHeight() - scaleY(x);
      }else {
        return scaleY(x);
      }
    }

    public float translateY3(float x) {
      if (overlay.facing == CameraSource.CAMERA_FACING_FRONT){
        return scaleY(x);
      }else {
        return overlay.getHeight() - scaleY(x);
      }
    }


    public void postInvalidate() {
      overlay.postInvalidate();
    }
  }

  public GraphicOverlay(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  private boolean isDrawingDisabled(){
    return disableTouch;
  }

  public void disableTouching(boolean disableTouch){
    this.disableTouch = disableTouch;
  }

  public void setTrackingMethod(DETECTOR type){
    this.detection = type;

    prePaint = new Paint();
    prePaint.setStyle(Paint.Style.STROKE);
    prePaint.setPathEffect(new DashPathEffect(new float[]{10, 20}, 0));
    prePaint.setColor(Color.BLUE);
    prePaint.setStrokeWidth(5);

    trackPaint = new Paint();
    trackPaint.setStyle(Paint.Style.STROKE);
    trackPaint.setColor(Color.RED);
    trackPaint.setStrokeWidth(5);
  }

  /** Removes all graphics from the overlay. */
  public void clear() {
    synchronized (lock) {
      graphics.clear();
    }
    postInvalidate();
  }

  /** Adds a graphic to the overlay. */
  public void add(Graphic graphic) {
    synchronized (lock) {
      graphics.add(graphic);
    }
    postInvalidate();
  }

  /** Removes a graphic from the overlay. */
  public void remove(Graphic graphic) {
    synchronized (lock) {
      graphics.remove(graphic);
    }
    postInvalidate();
  }

  /**
   * Sets the camera attributes for size and facing direction, which informs how to transform image
   * coordinates later.
   */
  public void setCameraInfo(int previewWidth, int previewHeight, int facing) {
    synchronized (lock) {
      this.previewWidth = previewWidth;
      this.previewHeight = previewHeight;
      this.facing = facing;
    }
    postInvalidate();
  }

//  public void setListener(IObjectSelectListener listener){
//    this.mListener = listener;
//  }

  public void setTimerEnabled(boolean timerEnabled){
    this.timerEnabled = timerEnabled;
  }

  public void reset(){
    endPointY = 0;
    endPointX = 0;

    startPointY = 0;
    startPointX = 0;

    movement = false;
    pressed = false;
  }


  /** Draws the overlay with its associated graphic objects. */
  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    synchronized (lock) {
      if ((previewWidth != 0) && (previewHeight != 0)) {
        widthScaleFactor = (float) canvas.getWidth() / (float) previewWidth;
        heightScaleFactor = (float) canvas.getHeight() / (float) previewHeight;
      }

      for (Graphic graphic : graphics) {
        graphic.draw(canvas);
      }
    }

    synchronized (lock) {
      if ((previewWidth != 0) && (previewHeight != 0)) {
        widthScaleFactor = (float) canvas.getWidth() / (float) previewWidth;
        heightScaleFactor = (float) canvas.getHeight() / (float) previewHeight;

      }

      if (detection == DETECTOR.OBJECT){
        if (pressed){
          RectF objRect;
          if (movement && !pinchScroll){
            objRect = new RectF(Math.min(startPointX, endPointX), Math.min(startPointY, endPointY), Math.max(startPointX, endPointX), Math.max(startPointY, endPointY));
            canvas.drawRect(objRect, prePaint);
          }else if (singleTap){
            objRect = new RectF(startPointX-getWidth()/6, startPointY - getWidth()/6, startPointX+getWidth()/6, startPointY+getWidth()/6);
            canvas.drawRect(objRect, timerEnabled?prePaint:trackPaint);
            singleTap = false;
          }

        }
      }

      for (Graphic graphic : graphics) {
        graphic.draw(canvas);
      }
    }
  }

  private boolean twoFingers;

  @Override
  public boolean onTouchEvent(MotionEvent event){
    mPinchDetector.onTouchEvent(event);
    twoFingers = event.getPointerCount()==2;

    if (detection == DETECTOR.OBJECT){
      switch (event.getAction()){
        case MotionEvent.ACTION_DOWN:

          pressed = true;
          if (!twoFingers) {
            pinchScroll =false;
            startPointX = event.getX();
            startPointY = event.getY();
            startClickTime = System.currentTimeMillis() / 1000;

            if (isOnEdgeOfScreen((int) startPointX, (int) startPointY) || isDrawingDisabled())
              return false;
//            if (mListener != null) {
//              // mListener.onReset();
//            }
          }

          break;
        case MotionEvent.ACTION_MOVE:

          if (!twoFingers){
            if (pressed && (Math.abs(startPointX-event.getX()) > SCROLL_THRESHOLD || Math.abs(startPointY-event.getY()) > SCROLL_THRESHOLD)){

              // clear();
              movement = true;
              endPointX = event.getX();
              endPointY = event.getY();
            }
          }
          else {
            pinchScroll = true;
          }

          break;
        case MotionEvent.ACTION_UP:
          if (!pinchScroll){
            if (System.currentTimeMillis()/1000 - startClickTime<MAX_CLICK_DURATION && !movement){
              singleTap = true;
            }
//            if (mListener!=null){
//              Rect objRect = getSmallestObjArea(new
//                      Rect((int)Math.min(startPointX, endPointX), (int)Math.min(startPointY, endPointY),
//                      (int)Math.max(startPointX, endPointX), (int)Math.max(startPointY, endPointY)));
//              if (singleTap){
//                objRect = getSmallestObjArea(new
//                        Rect((int)(startPointX-getWidth()/6), (int)(startPointY - getWidth()/6),
//                        (int)(startPointX+getWidth()/6), (int)(startPointY+getWidth()/6)));
//              }
//
//              mListener.onSelect(objRect, widthScaleFactor, heightScaleFactor);
//            }
          }

          break;
      }
      postInvalidate();
      return true;

    }else{
      return false;
    }
  }

  /**
   * This function is used to determine, starting position of tracking object.
   * If starting position X is less than 10 percentage of whole getWidth
   * if starting point x is larger than 90 percentage of whole view
   */
  private boolean isOnEdgeOfScreen(int startPointX, int startPointY){
    if (startPointX < getWidth()*0.1){
      return true;
    }else if (startPointX >  getWidth()*0.9){
      return true;
    }
    return false;
  }

  /**
   * This function is used to calculate object area, when remote button is clicked.
   */
  public void startTrackingFromCenter(){
//    if (mListener!=null){
//      startPointX = getWidth()/2;
//      startPointY = getHeight()/2;
//      endPointX = getWidth()/2;
//      endPointY = getHeight()/2;
//
//      Rect objRect = new
//              Rect((int)startPointX-getWidth()/6, (int)startPointY - getWidth()/6,
//              (int)endPointX + getWidth()/6, (int)endPointY + getWidth()/6);
//      pressed = true;
//      singleTap = true;
//      postInvalidate();
//      mListener.onSelect(objRect, widthScaleFactor, heightScaleFactor);
//    }
  }

  /**
   * This function calculates to be tracking object area. KCF tracking algorithm works without
   * any crashes if the following conditions matched.
   *
   * 1. object area should be at least (45x45)
   * 2. aspect ratio of object area is at most (1x5) or (5x1)
   */
  private Rect getSmallestObjArea(Rect roi){
    Rect newRoi = roi;
    if (roi.width()<100){
      newRoi = new Rect(newRoi.centerX()-50, newRoi.top, newRoi.centerX()+50, newRoi.bottom);
    }

    if (newRoi.height()<100){
      newRoi = new Rect(newRoi.left, newRoi.centerY()-50, newRoi.right, newRoi.centerY()+50);
    }

    if (newRoi.width()>newRoi.height()*5){
      newRoi = new Rect(newRoi.centerX()-5*newRoi.height()/2, newRoi.top, newRoi.centerX()+5*newRoi.height()/2, newRoi.bottom);
    }

    if (newRoi.height()>newRoi.width()*5){
      newRoi = new Rect(newRoi.left, newRoi.centerY()-5*newRoi.width()/2, newRoi.right, newRoi.centerY()+5*newRoi.width()/2);
    }
    return newRoi;
  }

}
