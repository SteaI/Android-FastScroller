package com.steal;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.ColorInt;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.content.res.AppCompatResources;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.SectionIndexer;

import com.steal.common.Property;

import java.util.ArrayList;
import java.util.List;

public class FastScroller extends View {
    private static final boolean DEBUG = false;

    private static final int TOUCH_IDLE = 0;
    private static final int TOUCH_DOWN = 1;
    private static final int TOUCH_SCROLL = 2;

    // property
    private float mSpacing = 0;
    private float mTextSize = 0;
    private int mTextColor = 0;
    private SectionIndexer mIndexer;
    private float mSectionWidth;
    private float mSectionHeight;

    // cache
    private boolean initialized;
    private String[] sectionCache;
    private boolean sectionCacheDirty;
    private int sectionIndex = -1;
    private Property<String> stringProperty;
    private Property<Paint> paintProperty;

    // measure
    private float measuredTextHeight = 0;
    private float measuredSpacing = 0;
    private float measuredSectionWidth;
    private float measuredSectionHeight;

    // render
    private TextPaint textPaint;
    private Paint debugPaint;
    private RectF debugRect;

    // touch
    private int touchState = TOUCH_IDLE;
    private int downY;
    private int touchSlop;

    // event
    private List<OnSectionScrolledListener> listeners;
    private List<DecorationItem> decorations;

    public FastScroller(Context context) {
        super(context);
        initialize(context, null, 0);
    }

    public FastScroller(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialize(context, attrs, 0);
    }

    public FastScroller(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context, attrs, defStyleAttr);
    }

    private void initialize(Context context, AttributeSet attrs, int defStyleAttrs) {
        setWillNotDraw(false);

        listeners = new ArrayList<>();
        decorations = new ArrayList<>();
        stringProperty = new Property<>(null);
        paintProperty = new Property<>(null);

        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.BLACK);

        if (DEBUG && BuildConfig.DEBUG) {
            debugPaint = new Paint();
            debugPaint.setColor(Color.RED);
            debugPaint.setStrokeWidth((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, context.getResources().getDisplayMetrics()));
            debugPaint.setStyle(Paint.Style.STROKE);

            debugRect = new RectF();
        }

        float defaultTextSize = getResources().getDimension(R.dimen.fast_scroller_text_size);
        float defaultSpacing = getResources().getDimension(R.dimen.fast_scroller_spacing);
        int defaultTextColor = ContextCompat.getColor(getContext(), R.color.fast_scroller_text);

        if (attrs != null) {
            TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.FastScroller, defStyleAttrs, 0);

            setTextSize(array.getDimension(R.styleable.FastScroller_fs_textSize, defaultTextSize));
            setTextColor(array.getColor(R.styleable.FastScroller_fs_textColor, defaultTextColor));
            setSpacing(array.getDimension(R.styleable.FastScroller_fs_spacing, defaultSpacing));
            setSectionWidth(array.getDimension(R.styleable.FastScroller_fs_sectionWidth, -1));
            setSectionHeight(array.getDimension(R.styleable.FastScroller_fs_sectionHeight, -1));

            overrideDefaultAttributes(context, array);

            array.recycle();
        }

        // 프리뷰
        if (isInEditMode()) {
            setSectionIndexer(new PreviewSectionIndexer());
        }

        // touch
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        initialized = true;
        invalidateMeasure();
    }

    private void overrideDefaultAttributes(Context context, TypedArray array) {
        // background

        final int resId = array.getResourceId(R.styleable.FastScroller_android_background, R.drawable.background_round);
        if (resId != 0) {
            Drawable drawable = AppCompatResources.getDrawable(context, resId);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                setBackground(drawable);
            } else {
                setBackgroundDrawable(drawable);
            }
        }

        // padding

        final int defaultPadding = getResources().getDimensionPixelSize(R.dimen.fast_scroller_padding);

        int paddingLeft;
        int paddingRight;
        final int paddingTop;
        final int paddingBottom;

        final int padding = array.getDimensionPixelSize(R.styleable.FastScroller_android_padding, -1);

        if (padding == -1) {
            final int paddingVertical = array.getDimensionPixelSize(R.styleable.FastScroller_android_paddingVertical, -1);
            final int paddingHorizontal = array.getDimensionPixelSize(R.styleable.FastScroller_android_paddingHorizontal, -1);

            if (paddingVertical != -1) {
                paddingTop = paddingVertical;
                paddingBottom = paddingVertical;
            } else {
                paddingTop = array.getDimensionPixelSize(R.styleable.FastScroller_android_paddingTop, defaultPadding);
                paddingBottom = array.getDimensionPixelSize(R.styleable.FastScroller_android_paddingBottom, defaultPadding);
            }

            if (paddingHorizontal != -1) {
                paddingLeft = paddingHorizontal;
                paddingRight = paddingHorizontal;
            } else {
                final boolean reverse = ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_RTL;

                paddingLeft = array.getDimensionPixelSize(R.styleable.FastScroller_android_paddingLeft, defaultPadding);
                paddingRight = array.getDimensionPixelSize(R.styleable.FastScroller_android_paddingRight, defaultPadding);
                final int paddingStart = array.getDimensionPixelSize(R.styleable.FastScroller_android_paddingStart, -1);
                final int paddingEnd = array.getDimensionPixelSize(R.styleable.FastScroller_android_paddingEnd, -1);

                if (paddingStart != -1) {
                    if (reverse) {
                        paddingRight = paddingStart;
                    } else {
                        paddingLeft = paddingStart;
                    }
                }

                if (paddingEnd != -1) {
                    if (reverse) {
                        paddingLeft = paddingEnd;
                    } else {
                        paddingRight = paddingEnd;
                    }
                }
            }
        } else {
            paddingLeft = defaultPadding;
            paddingTop = defaultPadding;
            paddingRight = defaultPadding;
            paddingBottom = defaultPadding;
        }

        setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
    }

    public void setSectionIndexer(SectionIndexer indexer) {
        if (mIndexer == indexer) {
            return;
        }

        if (indexer != null && indexer.getSections() != null) {
            Object[] sections = indexer.getSections();
            sectionCache = new String[sections.length];

            for (int i = 0; i < sectionCache.length; i++) {
                sectionCache[i] = String.valueOf(sections[i]);
            }
        } else {
            sectionCache = null;
        }

        sectionCacheDirty = true;
        mIndexer = indexer;

        invalidateMeasure();
        requestLayout();
    }

    public void setTextSize(float textSize) {
        if (mTextSize != textSize) {
            mTextSize = textSize;
            textPaint.setTextSize(textSize);
            invalidateMeasure();
            requestLayout();
        }
    }

    public float getTextSize() {
        return mTextSize;
    }

    public void setTextColor(@ColorInt int textColor) {
        if (mTextColor != textColor) {
            mTextColor = textColor;
            invalidate();
        }
    }

    public int getTextColor() {
        return textPaint.getColor();
    }

    public void setSpacing(float spacing) {
        if (mSpacing != spacing) {
            mSpacing = spacing;
            invalidateMeasure();
            requestLayout();
        }
    }

    public float getSpacing() {
        return mSpacing;
    }

    public float getSectionWidth() {
        return mSectionWidth;
    }

    public void setSectionWidth(float sectionWidth) {
        mSectionWidth = sectionWidth;
        invalidateMeasure();
        requestLayout();
    }

    public float getSectionHeight() {
        return mSectionHeight;
    }

    public void setSectionHeight(float sectionHeight) {
        mSectionHeight = sectionHeight;
        invalidateMeasure();
        requestLayout();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        invalidateMeasure();
        invalidate();
    }

    @Override
    public void invalidate() {
        if (initialized) {
            super.invalidate();
        }
    }

    @Override
    public void requestLayout() {
        if (initialized) {
            super.requestLayout();
        }
    }

    private void invalidateMeasure() {
        measuredTextHeight = -textPaint.descent() - textPaint.ascent();
        measuredSectionHeight = measuredTextHeight;

        if (mSectionHeight >= 0) {
            measuredSectionHeight = mSectionHeight;
        }

        if (sectionCache != null) {
            float spacingHeight = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();

            spacingHeight -= sectionCache.length * measuredSectionHeight;
            spacingHeight -= (sectionCache.length - 1) * mSpacing;

            measuredSpacing = spacingHeight / (sectionCache.length * 2);

            if (!sectionCacheDirty) {
                return;
            }

            sectionCacheDirty = false;

            if (mSectionWidth >= 0) {
                measuredSectionWidth = mSectionWidth;
                return;
            }

            measuredSectionWidth = 0;

            textPaint.setTextSize(mTextSize);
            textPaint.setTypeface(Typeface.DEFAULT);

            for (String section : sectionCache) {
                measuredSectionWidth = Math.max(measuredSectionWidth, textPaint.measureText(section));
            }
        } else {
            measuredSpacing = 0;
            measuredSectionWidth = Math.max(mSectionWidth, 0);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int y = (int) event.getY();

        y -= getPaddingTop();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downY = y;
                touchState = TOUCH_DOWN;
                break;

            case MotionEvent.ACTION_MOVE:
                if (touchState == TOUCH_DOWN) {
                    if (Math.abs(downY - y) > touchSlop) {
                        touchState = TOUCH_SCROLL;
                    }
                } else {
                    float groupHeight = measuredSpacing * 2 + measuredSectionHeight + mSpacing;
                    int index = (int) Math.floor(y / groupHeight);

                    if (index < 0 || index >= sectionCache.length || index == sectionIndex) {
                        break;
                    }

                    y -= groupHeight * index;

                    if (y <= groupHeight - mSpacing) {
                        sectionIndex = index;
                        onSectionChanged(index);
                        raiseOnSectionScrolledListener(index);
                        invalidate();
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touchState = TOUCH_IDLE;
                sectionIndex = -1;
                invalidate();
                break;
        }

        return true;
    }

    protected void onSectionChanged(int index) {
        // TODO: virtual method
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        int length = 0;

        if (sectionCache != null) {
            length = sectionCache.length;
        }

        if (widthMode != MeasureSpec.EXACTLY) {
            int measuredWidth = (int) (measuredSectionWidth + getPaddingLeft() + getPaddingRight());

            if (widthMode == MeasureSpec.AT_MOST) {
                width = Math.min(measuredWidth, width);
            } else {
                width = measuredWidth;
            }
        }

        if (heightMode != MeasureSpec.EXACTLY) {
            int measuredHeight = getPaddingTop() + getPaddingBottom();
            measuredHeight += length * measuredSectionHeight;
            measuredHeight += (length - 1) * mSpacing;

            if (heightMode == MeasureSpec.AT_MOST) {
                height = Math.min(measuredHeight, height);
            } else {
                height = measuredHeight;
            }
        }

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (sectionCache == null) {
            return;
        }

        canvas.translate(getPaddingLeft(), getPaddingTop());

        float width = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
        float centerX = width / 2f;
        float y = measuredSpacing;

        textPaint.setColor(mTextColor);
        textPaint.setTextSize(mTextSize);
        textPaint.setTypeface(Typeface.DEFAULT);

        for (int i = 0; i < sectionCache.length; i++) {
            if (debugPaint != null) {
                debugRect.set(0, y - measuredSpacing, width - 1, y + measuredSpacing + measuredSectionHeight);
                canvas.drawRect(debugRect, debugPaint);
            }

            float offset = (measuredSectionHeight + measuredTextHeight) / 2;

            if (touchState == TOUCH_SCROLL) {
                paintProperty.setValue(textPaint);
                stringProperty.setValue(sectionCache[i]);

                for (DecorationItem decoration : decorations) {
                    decoration.onDraw(canvas, stringProperty, paintProperty, i, Math.abs(i - sectionIndex));
                }

                canvas.drawText(stringProperty.getValue(), centerX, y + offset, paintProperty.getValue());
            } else {
                canvas.drawText(sectionCache[i], centerX, y + offset, textPaint);
            }

            y += measuredSectionHeight;
            y += measuredSpacing * 2 + mSpacing;
        }
    }

    private void raiseOnSectionScrolledListener(int section) {
        for (OnSectionScrolledListener listener : listeners) {
            listener.onSectionScrolled(mIndexer, section);
        }
    }

    public void addDecoration(DecorationItem decoration) {
        decorations.add(decoration);
        invalidate();
    }

    public void removeDecoration(DecorationItem decoration) {
        decorations.remove(decoration);
        invalidate();
    }

    public void addOnSectionScrolledListener(OnSectionScrolledListener listener) {
        listeners.add(listener);
    }

    public void removeOnSectionScrolledListener(OnSectionScrolledListener listener) {
        listeners.remove(listener);
    }

    public interface OnSectionScrolledListener {
        void onSectionScrolled(SectionIndexer indexer, int section);
    }

    public interface DecorationItem {
        void onDraw(Canvas canvas, Property<String> text, Property<Paint> paint, int index, int distance);
    }

    private class PreviewSectionIndexer implements SectionIndexer {
        private Object[] sections;

        public PreviewSectionIndexer() {
            sections = new Object[10];

            for (int i = 0; i < sections.length; i++) {
                sections[i] = String.valueOf(i);
            }
        }

        @Override
        public Object[] getSections() {
            return sections;
        }

        @Override
        public int getPositionForSection(int sectionIndex) {
            return 0;
        }

        @Override
        public int getSectionForPosition(int position) {
            return 0;
        }
    }
}