package ru.mona.ai;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewParent;
import android.widget.ImageView;

/**
 * Reconstructed source for ru.inoui.ZoomImageView, based on the supplied
 * classes.jar bytecode. Uses framework Android APIs only (no AndroidX/AppCompat).
 * Java 8 lambdas are used for animator update callbacks.
 */
public class ZoomImageView extends ImageView
        implements ScaleGestureDetector.OnScaleGestureListener {

    private static final float MAX_SCALE = 8.0f;
    private static final float MIN_SCALE = 0.6f;

    private final int RESET_DURATION = 200;
    private boolean animateOnReset = true;
    private boolean autoCenter = true;
    private int autoResetMode = AutoResetMode.UNDER;
    private final RectF bounds = new RectF();
    private float calculatedMaxScale = MAX_SCALE;
    private float calculatedMinScale = MIN_SCALE;
    private int currentPointerCount = 0;
    private float currentScaleFactor = 1.0f;
    private boolean doubleTapDetected = false;
    private boolean doubleTapToZoom = true;
    private float doubleTapToZoomScaleFactor = 3.0f;
    private GestureDetector gestureDetector;
    private final GestureDetector.SimpleOnGestureListener gestureListener =
            new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDoubleTapEvent(MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        doubleTapDetected = true;
                    }
                    singleTapDetected = false;
                    return false;
                }

                @Override
                public boolean onDown(MotionEvent event) {
                    return true;
                }

                @Override
                public boolean onSingleTapConfirmed(MotionEvent event) {
                    singleTapDetected = false;
                    return false;
                }

                @Override
                public boolean onSingleTapUp(MotionEvent event) {
                    singleTapDetected = true;
                    return false;
                }
            };
    private PointF last = new PointF(0.0f, 0.0f);
    private Matrix matrix = new Matrix();
    private float[] matrixValues = new float[9];
    private float maxScale = MAX_SCALE;
    private float minScale = MIN_SCALE;
    private int previousPointerCount = 1;
    private ValueAnimator resetAnimator;
    private boolean restrictBounds = false;
    private float scaleBy = 1.0f;
    private ScaleGestureDetector scaleDetector;
    private boolean singleTapDetected = false;
    private Matrix startMatrix = new Matrix();
    private float startScale = 1.0f;
    private ImageView.ScaleType startScaleType;
    private float[] startValues;
    private boolean translatable = true;
    private boolean zoomable = true;

    public ZoomImageView(Context context) {
        super(context);
        init(context, null);
    }

    public ZoomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public ZoomImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        scaleDetector = new ScaleGestureDetector(context, this);
        gestureDetector = new GestureDetector(context, gestureListener);
        ImageView.ScaleType type = getScaleType();
        if (type == null) {
            type = ImageView.ScaleType.FIT_CENTER;
        }
        startScaleType = type;
        zoomable = true;
        translatable = true;
        animateOnReset = true;
        autoCenter = true;
        restrictBounds = false;
        doubleTapToZoom = true;
        minScale = MIN_SCALE;
        maxScale = MAX_SCALE;
        doubleTapToZoomScaleFactor = 3.0f;
        autoResetMode = AutoResetMode.UNDER;
        verifyScaleRange();
    }

    private void animateMatrixIndex(int index, float target) {
        ValueAnimator animator = ValueAnimator.ofFloat(matrixValues[index], target);
        animator.addUpdateListener(animation -> {
            Matrix current = new Matrix(getImageMatrix());
            float[] values = new float[9];
            current.getValues(values);
            values[index] = ((Float) animation.getAnimatedValue()).floatValue();
            current.setValues(values);
            setImageMatrix(current);
        });
        animator.setDuration(200L);
        animator.start();
    }

    private void animateScaleAndTranslationToMatrix(Matrix targetMatrix, int duration) {
        float[] targetValues = new float[9];
        targetMatrix.getValues(targetValues);

        Matrix beginMatrix = new Matrix(getImageMatrix());
        beginMatrix.getValues(matrixValues);
        final float beginXScale = matrixValues[Matrix.MSCALE_X];
        final float beginYScale = matrixValues[Matrix.MSCALE_Y];
        final float beginX = matrixValues[Matrix.MTRANS_X];
        final float beginY = matrixValues[Matrix.MTRANS_Y];
        final float diffX = targetValues[Matrix.MTRANS_X] - beginX;
        final float diffY = targetValues[Matrix.MTRANS_Y] - beginY;
        final float diffScaleX = targetValues[Matrix.MSCALE_X] - beginXScale;
        final float diffScaleY = targetValues[Matrix.MSCALE_Y] - beginYScale;
        final Matrix activeMatrix = new Matrix(getImageMatrix());
        final float[] values = new float[9];

        resetAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
        resetAnimator.addUpdateListener(animation -> {
            float fraction = ((Float) animation.getAnimatedValue()).floatValue();
            activeMatrix.set(beginMatrix);
            activeMatrix.getValues(values);
            values[Matrix.MTRANS_X] = values[Matrix.MTRANS_X] + diffX * fraction;
            values[Matrix.MTRANS_Y] = values[Matrix.MTRANS_Y] + diffY * fraction;
            values[Matrix.MSCALE_X] = values[Matrix.MSCALE_X] + diffScaleX * fraction;
            values[Matrix.MSCALE_Y] = values[Matrix.MSCALE_Y] + diffScaleY * fraction;
            activeMatrix.setValues(values);
            setImageMatrix(activeMatrix);
        });
        resetAnimator.addListener(new SimpleAnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setImageMatrix(targetMatrix);
            }
        });
        resetAnimator.setDuration(duration);
        resetAnimator.start();
    }

    private void animateToStartMatrix() {
        animateScaleAndTranslationToMatrix(startMatrix, RESET_DURATION);
    }

    private void animateTranslationX() {
        if (getCurrentDisplayedWidth() > getWidth()) {
            if (bounds.left > 0.0f) {
                animateMatrixIndex(Matrix.MTRANS_X, 0.0f);
                return;
            }
            if (bounds.right < getWidth()) {
                animateMatrixIndex(Matrix.MTRANS_X,
                        bounds.left + getWidth() - bounds.right);
            }
        } else {
            if (bounds.left < 0.0f) {
                animateMatrixIndex(Matrix.MTRANS_X, 0.0f);
                return;
            }
            if (bounds.right > getWidth()) {
                animateMatrixIndex(Matrix.MTRANS_X,
                        bounds.left + getWidth() - bounds.right);
            }
        }
    }

    private void animateTranslationY() {
        if (getCurrentDisplayedHeight() > getHeight()) {
            if (bounds.top > 0.0f) {
                animateMatrixIndex(Matrix.MTRANS_Y, 0.0f);
                return;
            }
            if (bounds.bottom < getHeight()) {
                animateMatrixIndex(Matrix.MTRANS_Y,
                        bounds.top + getHeight() - bounds.bottom);
            }
        } else {
            if (bounds.top < 0.0f) {
                animateMatrixIndex(Matrix.MTRANS_Y, 0.0f);
                return;
            }
            if (bounds.bottom > getHeight()) {
                animateMatrixIndex(Matrix.MTRANS_Y,
                        bounds.top + getHeight() - bounds.bottom);
            }
        }
    }

    private void center() {
        if (autoCenter) {
            animateTranslationX();
            animateTranslationY();
        }
    }

    private float getCurrentDisplayedHeight() {
        Drawable drawable = getDrawable();
        return drawable != null
                ? drawable.getIntrinsicHeight() * matrixValues[Matrix.MSCALE_Y]
                : 0.0f;
    }

    private float getCurrentDisplayedWidth() {
        Drawable drawable = getDrawable();
        return drawable != null
                ? drawable.getIntrinsicWidth() * matrixValues[Matrix.MSCALE_X]
                : 0.0f;
    }

    private float getRestrictedXDistance(float distance) {
        if (getCurrentDisplayedWidth() >= getWidth()) {
            if (bounds.left > 0.0f
                    || bounds.left + distance <= 0.0f
                    || scaleDetector.isInProgress()) {
                float result = distance;
                if (bounds.right >= getWidth()) {
                    result = distance;
                    if (bounds.right + distance < getWidth()) {
                        if (scaleDetector.isInProgress()) {
                            result = distance;
                        } else {
                            result = getWidth() - bounds.right;
                        }
                    }
                    return result;
                }
                if (scaleDetector.isInProgress()) {
                    return distance;
                }
                return -bounds.left;
            }
            return -bounds.left;
        }

        if (scaleDetector.isInProgress()) {
            return distance;
        }
        if (bounds.left < 0.0f || bounds.left + distance >= 0.0f) {
            if (bounds.right > getWidth()) {
                return distance;
            }
            if (bounds.right + distance <= getWidth()) {
                return distance;
            }
            return getWidth() - bounds.right;
        }
        return -bounds.left;
    }

    private float getRestrictedYDistance(float distance) {
        if (getCurrentDisplayedHeight() >= getHeight()) {
            if (bounds.top > 0.0f
                    || bounds.top + distance <= 0.0f
                    || scaleDetector.isInProgress()) {
                float result = distance;
                if (bounds.bottom >= getHeight()) {
                    result = distance;
                    if (bounds.bottom + distance < getHeight()) {
                        if (scaleDetector.isInProgress()) {
                            result = distance;
                        } else {
                            result = getHeight() - bounds.bottom;
                        }
                    }
                    return result;
                }
                if (scaleDetector.isInProgress()) {
                    return distance;
                }
                return -bounds.top;
            }
            return -bounds.top;
        }

        if (scaleDetector.isInProgress()) {
            return distance;
        }
        if (bounds.top < 0.0f || bounds.top + distance >= 0.0f) {
            if (bounds.bottom > getHeight()) {
                return distance;
            }
            if (bounds.bottom + distance <= getHeight()) {
                return distance;
            }
            return getHeight() - bounds.bottom;
        }
        return -bounds.top;
    }

    private float getXDistance(float x, float lastX) {
        float distance = x - lastX;
        float result = distance;
        if (restrictBounds) {
            result = getRestrictedXDistance(distance);
        }
        if (bounds.right + result < 0.0f) {
            return -bounds.right;
        }
        if (bounds.left + result > getWidth()) {
            return getWidth() - bounds.left;
        }
        return result;
    }

    private float getYDistance(float y, float lastY) {
        float distance = y - lastY;
        float result = distance;
        if (restrictBounds) {
            result = getRestrictedYDistance(distance);
        }
        if (bounds.bottom + result < 0.0f) {
            return -bounds.bottom;
        }
        if (bounds.top + result > getHeight()) {
            return getHeight() - bounds.top;
        }
        return result;
    }

    private boolean isAnimating() {
        return resetAnimator != null && resetAnimator.isRunning();
    }

    private void resetImage() {
        switch (autoResetMode) {
            case AutoResetMode.UNDER:
                if (matrixValues[Matrix.MSCALE_X] <= startValues[Matrix.MSCALE_X]) {
                    reset();
                } else {
                    center();
                }
                break;
            case AutoResetMode.OVER:
                if (matrixValues[Matrix.MSCALE_X] >= startValues[Matrix.MSCALE_X]) {
                    reset();
                } else {
                    center();
                }
                break;
            case AutoResetMode.ALWAYS:
                reset();
                break;
            case AutoResetMode.NEVER:
                center();
                break;
            default:
                break;
        }
    }

    private void setStartValues() {
        startValues = new float[9];
        startMatrix = new Matrix(getImageMatrix());
        startMatrix.getValues(startValues);
        calculatedMinScale = minScale * startValues[Matrix.MSCALE_X];
        calculatedMaxScale = maxScale * startValues[Matrix.MSCALE_X];
    }

    private void updateBounds(float[] values) {
        Drawable drawable = getDrawable();
        if (drawable != null) {
            bounds.set(
                    values[Matrix.MTRANS_X],
                    values[Matrix.MTRANS_Y],
                    drawable.getIntrinsicWidth() * values[Matrix.MSCALE_X]
                            + values[Matrix.MTRANS_X],
                    drawable.getIntrinsicHeight() * values[Matrix.MSCALE_Y]
                            + values[Matrix.MTRANS_Y]);
        }
    }

    private void verifyScaleRange() {
        if (minScale >= maxScale) {
            throw new IllegalStateException("minScale must be less than maxScale");
        }
        if (minScale < 0.0f) {
            throw new IllegalStateException("minScale must be greater than 0");
        }
        if (maxScale < 0.0f) {
            throw new IllegalStateException("maxScale must be greater than 0");
        }
        if (doubleTapToZoomScaleFactor > maxScale) {
            doubleTapToZoomScaleFactor = maxScale;
        }
        if (doubleTapToZoomScaleFactor < minScale) {
            doubleTapToZoomScaleFactor = minScale;
        }
    }

    protected boolean allowTranslate(MotionEvent event) {
        return translatable && currentScaleFactor > 1.0f;
    }

    protected boolean allowZoom(MotionEvent event) {
        return zoomable;
    }

    protected boolean disallowParentTouch(MotionEvent event) {
        boolean result = true;
        if (currentPointerCount <= 1 && currentScaleFactor <= 1.0f && !isAnimating()) {
            result = false;
        }
        return result;
    }

    public boolean getAnimateOnReset() { return animateOnReset; }
    public boolean getAutoCenter() { return autoCenter; }
    public int getAutoResetMode() { return autoResetMode; }
    public float getCurrentScaleFactor() { return currentScaleFactor; }
    public boolean getDoubleTapToZoom() { return doubleTapToZoom; }
    public float getDoubleTapToZoomScaleFactor() { return doubleTapToZoomScaleFactor; }
    public boolean getRestrictBounds() { return restrictBounds; }
    public boolean isTranslatable() { return translatable; }
    public boolean isZoomable() { return zoomable; }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        scaleBy = (startScale * detector.getScaleFactor())
                / matrixValues[Matrix.MSCALE_X];
        float scale = scaleBy * matrixValues[Matrix.MSCALE_X];
        if (scale < calculatedMinScale) {
            scaleBy = calculatedMinScale / matrixValues[Matrix.MSCALE_X];
            return false;
        }
        if (scale > calculatedMaxScale) {
            scaleBy = calculatedMaxScale / matrixValues[Matrix.MSCALE_X];
        }
        return false;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        startScale = matrixValues[Matrix.MSCALE_X];
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        scaleBy = 1.0f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isClickable() || !isEnabled() || (!zoomable && !translatable)) {
            return super.onTouchEvent(event);
        }

        if (getScaleType() != ImageView.ScaleType.MATRIX) {
            super.setScaleType(ImageView.ScaleType.MATRIX);
        }
        if (startValues == null) {
            setStartValues();
        }

        currentPointerCount = event.getPointerCount();
        matrix.set(getImageMatrix());
        matrix.getValues(matrixValues);
        updateBounds(matrixValues);
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        if (doubleTapToZoom && doubleTapDetected) {
            doubleTapDetected = false;
            singleTapDetected = false;
            if (matrixValues[Matrix.MSCALE_X] != startValues[Matrix.MSCALE_X]) {
                reset();
                return true;
            }

            Matrix targetMatrix = new Matrix(matrix);
            targetMatrix.postScale(
                    doubleTapToZoomScaleFactor,
                    doubleTapToZoomScaleFactor,
                    scaleDetector.getFocusX(),
                    scaleDetector.getFocusY());
            animateScaleAndTranslationToMatrix(targetMatrix, RESET_DURATION);
            return true;
        }

        if (!singleTapDetected) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                    || currentPointerCount != previousPointerCount) {
                last.set(scaleDetector.getFocusX(), scaleDetector.getFocusY());

                if (event.getActionMasked() == MotionEvent.ACTION_UP
                        || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    scaleBy = 1.0f;
                    resetImage();
                }

                ViewParent parent = getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(disallowParentTouch(event));
                }
                previousPointerCount = currentPointerCount;
            }

            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float focusX = scaleDetector.getFocusX();
                float focusY = scaleDetector.getFocusY();
                if (allowTranslate(event)) {
                    matrix.postTranslate(
                            getXDistance(focusX, last.x),
                            getYDistance(focusY, last.y));
                }
                if (allowZoom(event)) {
                    matrix.postScale(scaleBy, scaleBy, focusX, focusY);
                    currentScaleFactor = matrixValues[Matrix.MSCALE_X]
                            / startValues[Matrix.MSCALE_X];
                }
                setImageMatrix(matrix);
                last.set(focusX, focusY);
            }
        }
        return true;
    }

    public void reset() {
        reset(animateOnReset);
    }

    public void reset(boolean animate) {
        if (animate) {
            animateToStartMatrix();
        } else {
            setImageMatrix(startMatrix);
        }
    }

    public void setAnimateOnReset(boolean value) {
        animateOnReset = value;
    }

    public void setAutoCenter(boolean value) {
        autoCenter = value;
    }

    public void setAutoResetMode(int mode) {
        autoResetMode = mode;
    }

    public void setDoubleTapToZoom(boolean value) {
        doubleTapToZoom = value;
    }

    public void setDoubleTapToZoomScaleFactor(float factor) {
        doubleTapToZoomScaleFactor = factor;
        verifyScaleRange();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) {
            setScaleType(startScaleType);
        }
    }

    @Override
    public void setImageBitmap(Bitmap bitmap) {
        super.setImageBitmap(bitmap);
        setScaleType(startScaleType);
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        setScaleType(startScaleType);
    }

    @Override
    public void setImageResource(int resId) {
        super.setImageResource(resId);
        setScaleType(startScaleType);
    }

    @Override
    public void setImageURI(Uri uri) {
        super.setImageURI(uri);
        setScaleType(startScaleType);
    }

    public void setRestrictBounds(boolean value) {
        restrictBounds = value;
    }

    public void setScaleRange(float minimum, float maximum) {
        minScale = minimum;
        maxScale = maximum;
        startValues = null;
        verifyScaleRange();
    }

    @Override
    public void setScaleType(ImageView.ScaleType scaleType) {
        if (scaleType != null) {
            super.setScaleType(scaleType);
            startScaleType = scaleType;
            startValues = null;
        }
    }

    public void setTranslatable(boolean value) {
        translatable = value;
    }

    public void setZoomable(boolean value) {
        zoomable = value;
    }

    /** Mode values are kept identical to the class file. */
    public static final class AutoResetMode {
        public static final int UNDER = 0;
        public static final int OVER = 1;
        public static final int ALWAYS = 2;
        public static final int NEVER = 3;

        public AutoResetMode() { }

        public static class Parser {
            public Parser() { }

            public static int fromInt(int value) {
                switch (value) {
                    case 1: return 1;
                    case 2: return 2;
                    case 3: return 3;
                    default: return 0;
                }
            }
        }
    }

    class SimpleAnimatorListener implements Animator.AnimatorListener {
        @Override public void onAnimationCancel(Animator animation) { }
        @Override public void onAnimationEnd(Animator animation) { }
        @Override public void onAnimationRepeat(Animator animation) { }
        @Override public void onAnimationStart(Animator animation) { }
    }
}
