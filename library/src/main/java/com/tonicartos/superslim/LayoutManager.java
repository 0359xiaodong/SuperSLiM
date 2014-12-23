package com.tonicartos.superslim;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v7.widget.LinearSmoothScroller;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

/**
 * A LayoutManager that lays out mSection headers with optional stickiness and uses a map of
 * sections
 * to view layout managers to layout items.
 */
public class LayoutManager extends RecyclerView.LayoutManager {

    public static final int HEADER_ALIGN_START = 0x02;

    public static final int HEADER_ALIGN_END = 0x03;

    public static final int HEADER_OVERLAY_START = 0x04;

    public static final int HEADER_OVERLAY_END = 0x05;

    public static final int HEADER_INLINE = 0x01;

    private static final int NO_POSITION_REQUEST = -1;

    private SlmFactory mSlmFactory = new SlmFactory() {
        @Override
        public SectionLayoutManager getSectionLayoutManager(LayoutManager layoutManager,
                int section) {
            return new LinearSectionLayoutManager(layoutManager);
        }
    };

    private Rect mRect = new Rect();

    private int mRequestPosition = NO_POSITION_REQUEST;

    private int mRequestPositionOffset = 0;

    private boolean mDisableStickyHeaderDisplay = false;

    /**
     * Works the same way as {@link android.widget.AbsListView#setSmoothScrollbarEnabled(boolean)}.
     * see {@link android.widget.AbsListView#setSmoothScrollbarEnabled(boolean)}
     *
     * However, it isn't very good if you are combining different types of layouts so is turned off
     * by default.
     */
    private boolean mSmoothScrollbarEnabled = false;

    public void setSlmFactory(SlmFactory factory) {
        mSlmFactory = factory;
    }

    @Override
    public boolean canScrollVertically() {
        return true;
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        int itemCount = state.getItemCount();
        if (itemCount == 0) {
            detachAndScrapAttachedViews(recycler);
            return;
        }

        final int anchorPosition;
        final int borderLine;

        if (mRequestPosition != NO_POSITION_REQUEST) {
            anchorPosition = mRequestPosition;
            mRequestPosition = NO_POSITION_REQUEST;
            borderLine = mRequestPositionOffset;
            mRequestPositionOffset = 0;
        } else {
            View anchorView = getAnchorChild(itemCount);
            anchorPosition = anchorView == null ? 0 : getPosition(anchorView);
            borderLine = getBorderLine(anchorView, Direction.END);
        }

        detachAndScrapAttachedViews(recycler);
        fill(recycler, state, anchorPosition, borderLine, true);
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        mRequestPosition = ((SavedState) state).anchorPosition;
        mRequestPositionOffset = ((SavedState) state).anchorOffset;
        requestLayout();
    }

    @Override
    public Parcelable onSaveInstanceState() {
        SavedState state = new SavedState();
        View view = getAnchorChild(getItemCount());
        if (view == null) {
            state.anchorPosition = 0;
            state.anchorOffset = 0;
        } else {
            state.anchorPosition = getPosition(view);
            state.anchorOffset = getDecoratedTop(view);
        }
        return state;
    }

    @Override
    public void scrollToPosition(int position) {
        if (position < 0 || getItemCount() <= position) {
            Log.e("SuperSLiM.LayoutManager", "Ignored scroll to " + position +
                    " as it is not within the item range 0 - " + getItemCount());
            return;
        }

        mRequestPosition = position;
        requestLayout();
    }

    @Override
    public void smoothScrollToPosition(final RecyclerView recyclerView, RecyclerView.State state,
            final int position) {
        if (position < 0 || getItemCount() <= position) {
            Log.e("SuperSLiM.LayoutManager", "Ignored smooth scroll to " + position +
                    " as it is not within the item range 0 - " + getItemCount());
            return;
        }

        // Temporarily disable sticky headers.
        mDisableStickyHeaderDisplay = true;
        requestLayout();

        recyclerView.getHandler().post(new Runnable() {
            @Override
            public void run() {
                LinearSmoothScroller smoothScroller = new LinearSmoothScroller(
                        recyclerView.getContext()) {
                    @Override
                    public PointF computeScrollVectorForPosition(int targetPosition) {
                        if (getChildCount() == 0) {
                            return null;
                        }

                        return new PointF(0, getDirectionToPosition(targetPosition));
                    }

                    @Override
                    protected void onStop() {
                        super.onStop();
                        // Turn sticky headers back on.
                        mDisableStickyHeaderDisplay = false;
                    }

                    @Override
                    protected int getVerticalSnapPreference() {
                        return LinearSmoothScroller.SNAP_TO_START;
                    }

                    @Override
                    public int calculateDyToMakeVisible(View view, int snapPreference) {
                        final RecyclerView.LayoutManager layoutManager = getLayoutManager();
                        if (!layoutManager.canScrollVertically()) {
                            return 0;
                        }
                        final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams)
                                view.getLayoutParams();
                        final int top = layoutManager.getDecoratedTop(view) - params.topMargin;
                        final int bottom = layoutManager.getDecoratedBottom(view)
                                + params.bottomMargin;
                        final int start = getPosition(view) == 0 ? layoutManager.getPaddingTop()
                                : 0;
                        final int end = layoutManager.getHeight() - layoutManager
                                .getPaddingBottom();
                        int dy = calculateDtToFit(top, bottom, start, end, snapPreference);
                        return dy == 0 ? 1 : dy;
                    }

                    @Override
                    protected void onChildAttachedToWindow(View child) {
                        super.onChildAttachedToWindow(child);
                    }
                };
                smoothScroller.setTargetPosition(position);
                startSmoothScroll(smoothScroller);
            }
        });
    }

    private int getDirectionToPosition(int targetPosition) {
        int startSection = ((LayoutParams) getChildAt(0).getLayoutParams()).section;
        SectionLayoutManager manager = mSlmFactory.getSectionLayoutManager(this, startSection);

        View startSectionFirstView = manager.getFirstView(startSection);
        return targetPosition < getPosition(startSectionFirstView) ? -1 : 1;
    }

    @Override
    public void onAdapterChanged(RecyclerView.Adapter oldAdapter, RecyclerView.Adapter newAdapter) {
        removeAllViews();
    }

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler,
            RecyclerView.State state) {
        int numChildren = getChildCount();//
        if (numChildren == 0) {
            return 0;
        }

        final int itemCount = state.getItemCount();

        /*
         * Strategy.
         *
         * The scroll has reached the start if the padded edge of the view is aligned with the top
         * edge of the first section's header, the section's highest edge, and that the section's
         * first view by adapter position is a child view.
         *
         * The end has been reached if the padded edge of the view is aligned with the bottom edge
         * of the last section's header or the section's lowest edge, and that the last adapter
         * position is a child view.
         */

        // Get start views.
        int startSection = ((LayoutParams) getChildAt(0).getLayoutParams()).section;
        SectionLayoutManager manager = mSlmFactory.getSectionLayoutManager(this, startSection);

        View startSectionFirstView = manager.getFirstView(startSection);
        View startHeaderView = findAttachedHeaderForSection(state.getItemCount(), startSection,
                Direction.END);
        int startSectionHighestEdge = manager.getHighestEdge(startSection);

        // Get end views.
        int endSection = ((LayoutParams) getChildAt(getChildCount() - 1).getLayoutParams()).section;
        manager = mSlmFactory.getSectionLayoutManager(this, endSection);

        View endSectionLastView = manager.getLastView(endSection);
        View endHeaderView = findAttachedHeaderForSection(state.getItemCount(), endSection,
                Direction.START);
        int endSectionLowestEdge = manager
                .getLowestEdge(endSection);

        //Work out if reached start.
        final boolean startDisplayed;
        final int firstEdge;
        final int recyclerViewStartEdge = getPaddingTop();
        if (startHeaderView == null) {
            startDisplayed = getPosition(startSectionFirstView) == 0;
            firstEdge = startSectionHighestEdge;
        } else {
            startDisplayed = getPosition(startSectionFirstView) == 1;
            final int headerStartEdge = getDecoratedTop(startHeaderView);
            firstEdge = startSectionHighestEdge < headerStartEdge ? startSectionHighestEdge
                    : headerStartEdge;
        }
        final boolean reachedStart = startDisplayed && firstEdge >= recyclerViewStartEdge;

        // Work out if reached end.
        final boolean endDisplayed;
        final int lastEdge;
        final int recyclerViewEndEdge = getHeight() - getPaddingBottom();
        if (endHeaderView == null) {
            endDisplayed = getPosition(endSectionLastView) == itemCount - 1;
            lastEdge = endSectionLowestEdge;
        } else {
            endDisplayed = getPosition(endSectionLastView) == itemCount - 1;
            final int headerEndEdge = getDecoratedBottom(endHeaderView);
            lastEdge = endSectionLowestEdge > headerEndEdge ? endSectionLowestEdge : headerEndEdge;
        }
        final boolean reachedEnd = endDisplayed && lastEdge <= recyclerViewEndEdge;

        // Check if scrolling is possible.
        if (reachedEnd && reachedStart) {
            return 0;
        }

        // Work out how far to scroll.
        int delta;
        if (dy > 0) {
            // Scrolling to end.
            if (endDisplayed) {
                delta = Math.max(-dy, recyclerViewEndEdge - lastEdge);
            } else {
                delta = -dy;
            }
        } else {
            // Scrolling to top.
            if (startDisplayed) {
                if (startHeaderView != null) {
                    LayoutParams params = (LayoutParams) startHeaderView.getLayoutParams();
                    if (params.headerAlignment == HEADER_INLINE) {
                        delta = Math.min(-dy, (recyclerViewStartEdge + getDecoratedMeasuredHeight(
                                startHeaderView)) - startSectionHighestEdge);
                    } else {
                        delta = Math.min(-dy, recyclerViewStartEdge - firstEdge);
                    }
                } else {
                    delta = Math.min(-dy, recyclerViewStartEdge - firstEdge);
                }
            } else {
                delta = -dy;
            }
        }

        offsetChildrenVertical(delta);

        if (delta > 0) {
            fill(recycler, state, getPosition(startSectionFirstView), 0, false);
        } else {
            fill(recycler, state, getPosition(endSectionLastView), 0, false);
        }

        return -delta;
    }

    /**
     * Find a view that is the header for the specified section. Looks in direction specified from
     * opposite end.
     *
     * @param itemCount Current number of items in adapter.
     * @param section   Section to look for header inside of. Search is expected to start inside
     *                  the
     *                  section so it must be at the matching end specified by the direction.
     * @param direction Direction to look in. Direction.END means to look from the start to the
     *                  end.
     * @return Null if no header found, otherwise the header view.
     */
    private View findAttachedHeaderForSection(final int itemCount, final int section,
            final Direction direction) {
        int position = direction == Direction.END ? 0 : getChildCount() - 1;
        int nextStep = direction == Direction.END ? 1 : -1;
        for (; 0 <= position && position < itemCount; position += nextStep) {
            View child = getChildAt(position);
            if (child == null) {
                continue;
            }
            LayoutParams params = (LayoutParams) child.getLayoutParams();
            if (params.section != section) {
                break;
            } else if (params.isHeader) {
                return child;
            }
        }
        return null;
    }

    private void fill(RecyclerView.Recycler recycler, RecyclerView.State rvs,
            final int anchorPosition, int scrappedBorderLine, boolean scrapped) {

        LayoutState state = new LayoutState(this, recycler, rvs);
        final int itemCount = state.recyclerState.getItemCount();
        final int recyclerViewHeight = getHeight();

        if (anchorPosition >= itemCount || anchorPosition < 0) {
            return;
        }

        state.detachAndCacheAllViews();

        // Borderline
        int borderline = scrapped ? scrappedBorderLine
                : getBorderLine(state, anchorPosition, Direction.END);

        // Prepare anchor section.
        SectionData section = new SectionData(this, state, Direction.NONE, anchorPosition,
                borderline);
        SectionLayoutManager sectionManager = section.loadManager(this, mSlmFactory);

        // Fill anchor section.
        FillResult anchorResult = sectionManager.fill(state, section);
        anchorResult = layoutAndAddHeader(state, section, anchorResult);

        // Fill sections before anchor to start.
        FillResult fillResult;
        fillResult = fillSections(state, anchorResult, recyclerViewHeight, Direction.START);
        final int finalStartMarker = fillResult.markerStart;
        final int finalStartPosition = fillResult.positionStart;

        // Fill sections after anchor to end.
        fillResult = fillSections(state, anchorResult, recyclerViewHeight, Direction.END);
        final int finalEndMarker = fillResult.markerEnd;
        final int finalEndPosition = fillResult.positionEnd;

        fixOverscroll(state, finalStartMarker, finalStartPosition, finalEndMarker,
                finalEndPosition);

        state.recycleCache();
    }

    /**
     * Make sure there is no over-scroll. This means the last section is not inappropriately above
     * the end edge, and the start section is not below the start edge.
     *
     * @param state              Layout state.
     * @param finalStartMarker   Marker line for start of filled out area.
     * @param finalStartPosition Start position of filled out views.
     * @param finalEndMarker     Marker line for end of filled out area.
     * @param finalEndPosition   End position of filled out views.
     */
    private void fixOverscroll(LayoutState state, int finalStartMarker, int finalStartPosition,
            int finalEndMarker, int finalEndPosition) {
        final int recyclerViewHeight = getHeight();
        final int recyclerPaddedStartEdge = getPaddingTop();
        final int recyclerPaddedEndEdge = recyclerViewHeight - getPaddingBottom();

        if (finalStartMarker > recyclerPaddedStartEdge) {
            // Shunt all children up to the start edge and then refill from start to end.
            offsetChildrenVertical(recyclerPaddedStartEdge - finalStartMarker);

            state.detachAndCacheAllViews();
            // Build a fake fill state to trigger new fill from start to end.
            FillResult fakeIt = new FillResult();
            fakeIt.markerEnd = recyclerPaddedStartEdge;
            fakeIt.positionEnd = finalStartPosition - 1;

            fillSections(state, fakeIt, recyclerViewHeight, Direction.END);
        } else if (!dataSetFullyLaidOut(state, finalStartPosition, finalEndPosition)
                && finalEndMarker < recyclerPaddedEndEdge) {
            // Shunt all children to end edge and then refill from end to start.
            offsetChildrenVertical(recyclerPaddedEndEdge - finalEndMarker);

            state.detachAndCacheAllViews();
            // Build a fake fill state to trigger new fill from end to start.
            FillResult fakeIt = new FillResult();
            fakeIt.markerStart = recyclerPaddedEndEdge;
            fakeIt.positionStart = finalEndPosition + 1;

            fillSections(state, fakeIt, recyclerViewHeight, Direction.START);
        }
    }

    private FillResult fillSections(LayoutState layoutState, FillResult fillState,
            int recyclerViewHeight, Direction direction) {
        while (true) {
            final int anchor;
            final SectionData section;
            if (direction == Direction.END) {
                anchor = fillState.positionEnd + 1;
                if (fillState.markerEnd >= recyclerViewHeight
                        || anchor >= layoutState.recyclerState.getItemCount()) {
                    break;
                }
                section = new SectionData(this, layoutState, direction, anchor,
                        fillState.markerEnd);
            } else {
                anchor = fillState.positionStart - 1;
                if (fillState.markerStart <= 0 || anchor < 0) {
                    break;
                }
                section = new SectionData(this, layoutState, direction, anchor,
                        fillState.markerStart);
            }

            SectionLayoutManager sectionManager = section.loadManager(this, mSlmFactory);
            fillState = sectionManager.fill(layoutState, section);
            fillState = layoutAndAddHeader(layoutState, section, fillState);
        }
        return fillState;
    }

    private boolean dataSetFullyLaidOut(LayoutState state, int finalStartPosition,
            int finalEndPosition) {
        final boolean fillFromFirst = finalStartPosition == 0;
        final boolean filledToLast = finalEndPosition == state.recyclerState.getItemCount() - 1;
        return fillFromFirst && filledToLast;
    }

    /**
     * Check to see if, after fill in the views, the parent area cannot be filled by all the child
     * views.
     *
     * @return True if the parent cannot be filled.
     */
    private boolean cannotFillParent(LayoutState state) {

        return false;
    }

    public FillResult layoutAndAddHeader(LayoutState state, SectionData section,
            FillResult fillResult) {
        final LayoutState.View header = section.getSectionHeader();
        final LayoutParams params = header.getLayoutParams();
        final int width = getDecoratedMeasuredWidth(header.view);
        final int height = getDecoratedMeasuredHeight(header.view);

        // Adjust marker line if needed.
        if (params.headerAlignment == HEADER_INLINE) {
            fillResult.markerStart -= height;
        }

        // Check header if header is stuck.
        final boolean isStuck = params.isSticky && fillResult.markerStart < 0
                && !mDisableStickyHeaderDisplay;

        // Attach after section children if overlay, otherwise before.
        final int attachIndex;
        if (isStuck || params.headerAlignment == HEADER_OVERLAY_START
                || params.headerAlignment == HEADER_OVERLAY_END) {
            attachIndex = fillResult.firstChildIndex + fillResult.addedChildCount;
        } else {
            attachIndex = fillResult.firstChildIndex;
        }

        // Attach header.
        if (header.wasCached) {
            if ((params.isSticky && !mDisableStickyHeaderDisplay)
                    || getDecoratedBottom(header.view) >= 0) {
                attachView(header.view, attachIndex);
                state.decacheView(section.getFirstPosition());
                fillResult.positionStart -= 1;
            }
            if (!params.isSticky || mDisableStickyHeaderDisplay) {
                // Layout unneeded if the header is not sticky and was cached.
                return fillResult;
            }
        }

        // Do Layout

        Rect rect = setHeaderRectSides(state, section, width, params, mRect);
        rect = setHeaderRectTopAndBottom(state, fillResult, height, params, rect);
        if (rect.bottom < 0) {
            // Header is offscreen.
            return fillResult;
        } else if (!header.wasCached) {
            fillResult.positionStart -= 1;
            addView(header.view, attachIndex);
        }

        layoutDecorated(header.view, rect.left, rect.top, rect.right, rect.bottom);

        return fillResult;
    }

    private Rect setHeaderRectTopAndBottom(LayoutState state, FillResult fillResult, int height,
            LayoutParams params, Rect r) {
        r.top = fillResult.markerStart;
        if (params.headerAlignment != HEADER_INLINE && fillResult.headerOffset < 0) {
            r.top += fillResult.headerOffset;
        }
        r.bottom = r.top + height;

        if (params.isSticky && !mDisableStickyHeaderDisplay) {
            if (r.top < 0) {
                r.top = 0;
                r.bottom = height;
            }
            if (r.bottom > fillResult.markerEnd) {
                r.bottom = fillResult.markerEnd;
                r.top = r.bottom - height;
            }
        }

        return r;
    }

    private Rect setHeaderRectSides(LayoutState state, SectionData section, int width,
            LayoutParams params, Rect r) {
        if (params.headerAlignment == HEADER_OVERLAY_START) {
            r.left = getPaddingLeft();
            r.right = r.left + width;
        } else if (params.headerAlignment == HEADER_OVERLAY_END) {
            r.right = getWidth() - getPaddingRight();
            r.left = r.right - width;
        } else if (params.headerAlignment == HEADER_ALIGN_END) {
            // Align header with end margin or end edge of recycler view.
            if (!params.headerEndMarginIsAuto && section.getHeaderEndMargin() > 0) {
                r.left = getWidth() - section.getHeaderEndMargin() - getPaddingLeft();
                r.right = r.left + width;
            } else {
                r.right = getWidth() - getPaddingRight();
                r.left = r.right - width;
            }
        } else if (params.headerAlignment == HEADER_ALIGN_START) {
            // Align header with start margin or start edge of recycler view.
            if (!params.headerStartMarginIsAuto && section.getHeaderStartMargin() > 0) {
                r.right = section.getHeaderStartMargin() + getPaddingLeft();
                r.left = r.right - width;
            } else {
                r.left = getPaddingLeft();
                r.right = r.left + width;
            }
        } else {
            r.left = getPaddingLeft();
            r.right = r.left + width;
        }

        return r;
    }

    /**
     * Work out the borderline from the given anchor view and the intended direction to fill the
     * view hierarchy.
     *
     * @param anchorView Anchor view to determine borderline from.
     * @param direction  Direction fill will be taken towards.
     * @return Borderline.
     */
    private int getBorderLine(View anchorView, Direction direction) {
        int borderline;
        if (anchorView == null) {
            if (direction == Direction.START) {
                borderline = getPaddingBottom();
            } else {
                borderline = getPaddingTop();
            }
        } else if (direction == Direction.START) {
            borderline = getDecoratedBottom(anchorView);
        } else {
            borderline = getDecoratedTop(anchorView);
        }
        return borderline;
    }

    private int getBorderLine(LayoutState state, int anchorPosition,
            Direction direction) {
        int borderline;
        final android.view.View marker = state.getCachedView(anchorPosition);
        if (marker == null) {
            if (direction == Direction.START) {
                borderline = getPaddingBottom();
            } else {
                borderline = getPaddingTop();
            }
        } else if (direction == Direction.START) {
            borderline = getDecoratedBottom(marker);
        } else {
            borderline = getDecoratedTop(marker);
        }
        return borderline;
    }

    @Override
    public void onItemsUpdated(RecyclerView recyclerView, int positionStart, int itemCount) {
        super.onItemsUpdated(recyclerView, positionStart, itemCount);

        View first = getChildAt(0);
        View last = getChildAt(getChildCount() - 1);
        if (positionStart + itemCount <= getPosition(first)) {
            return;
        }

        if (positionStart <= getPosition(last)) {
            requestLayout();
        }
    }

    /**
     * When smooth scrollbar is enabled, the position and size of the scrollbar thumb is computed
     * based on the number of visible pixels in the visible items. This however assumes that all
     * list items have similar or equal widths or heights (depending on list orientation).
     * If you use a list in which items have different dimensions, the scrollbar will change
     * appearance as the user scrolls through the list. To avoid this issue,  you need to disable
     * this property.
     *
     * When smooth scrollbar is disabled, the position and size of the scrollbar thumb is based
     * solely on the number of items in the adapter and the position of the visible items inside
     * the adapter. This provides a stable scrollbar as the user navigates through a list of items
     * with varying widths / heights.
     *
     * @param enabled Whether or not to enable smooth scrollbar.
     * @see #setSmoothScrollbarEnabled(boolean)
     */
    public void setSmoothScrollbarEnabled(boolean enabled) {
        mSmoothScrollbarEnabled = enabled;
    }

    /**
     * Returns the current state of the smooth scrollbar feature. It is enabled by default.
     *
     * @return True if smooth scrollbar is enabled, false otherwise.
     * @see #setSmoothScrollbarEnabled(boolean)
     */
    public boolean isSmoothScrollbarEnabled() {
        return mSmoothScrollbarEnabled;
    }

    @Override
    public int computeVerticalScrollExtent(RecyclerView.State state) {
        if (getChildCount() == 0 || state.getItemCount() == 0) {
            return 0;
        }

        if (!mSmoothScrollbarEnabled) {
            return getChildCount();
        }

        return computeSScrollExtent(state);
    }

    private int computeSScrollExtent(RecyclerView.State state) {
        final int startSection = ((LayoutParams) getChildAt(0).getLayoutParams()).section;
        final SectionLayoutManager startManager = mSlmFactory.getSectionLayoutManager(this,
                startSection);
        final int firstViewPosition = getPosition(startManager.getFirstView(startSection));
        final int maybeHeaderPosition = getPosition(getChildAt(0));
        final int topExtent = startManager.getHighestEdge(startSection);

        // Get bottom position and extent.
        final int endSection = ((LayoutParams) getChildAt(getChildCount() - 1)
                .getLayoutParams()).section;
        final SectionLayoutManager endManager = mSlmFactory.getSectionLayoutManager(this,
                endSection);
        final int bottomExtent = endManager.getLowestEdge(endSection);

        return Math
                .min(getWidth() - getPaddingTop() - getPaddingBottom(), bottomExtent - topExtent);
    }

    @Override
    public int computeVerticalScrollRange(RecyclerView.State state) {
        if (getChildCount() == 0 || state.getItemCount() == 0) {
            return 0;
        }

        if (!mSmoothScrollbarEnabled) {
            return state.getItemCount();
        }

        return computeSScrollRange(state);
    }

    private int computeSScrollRange(RecyclerView.State state) {
        final int startSection = ((LayoutParams) getChildAt(0).getLayoutParams()).section;
        final SectionLayoutManager startManager = mSlmFactory
                .getSectionLayoutManager(this, startSection);
        final int firstViewPosition = getPosition(startManager.getFirstView(startSection));
        final int maybeHeaderPosition = getPosition(getChildAt(0));
        final int topPosition = firstViewPosition - maybeHeaderPosition == 1
                ? maybeHeaderPosition : firstViewPosition;
        final int topExtent = startManager.getHighestEdge(startSection);

        // Get bottom position and extent.
        final int endSection = ((LayoutParams) getChildAt(getChildCount() - 1)
                .getLayoutParams()).section;
        final SectionLayoutManager endManager = mSlmFactory.getSectionLayoutManager(this,
                endSection);
        final int bottomExtent = endManager.getLowestEdge(endSection);
        final int bottomPosition = getPosition(endManager.getLastView(endSection));

        final int range = bottomPosition - topPosition + 1;
        final int area = bottomExtent - topExtent;

        return (int) ((float) area / range * state.getItemCount());
    }

    @Override
    public int computeVerticalScrollOffset(RecyclerView.State state) {
        if (getChildCount() == 0) {
            return 0;
        }

        if (!mSmoothScrollbarEnabled) {
            return computeNSScrollOffset(state);
        }

        return computeSScrollOffset(state);
    }

    private int computeSScrollOffset(RecyclerView.State state) {
        // Get top position and extent.
        final int startSection = ((LayoutParams) getChildAt(0).getLayoutParams()).section;
        final SectionLayoutManager startManager = mSlmFactory
                .getSectionLayoutManager(this, startSection);
        final int firstViewPosition = getPosition(startManager.getFirstView(startSection));
        final int maybeHeaderPosition = getPosition(getChildAt(0));
        final int topPosition = firstViewPosition - maybeHeaderPosition == 1
                ? maybeHeaderPosition : firstViewPosition;
        final int topExtent = startManager.getHighestEdge(startSection);

        // Get bottom position and extent.
        final int endSection = ((LayoutParams) getChildAt(getChildCount() - 1)
                .getLayoutParams()).section;
        final SectionLayoutManager endManager = mSlmFactory.getSectionLayoutManager(this,
                endSection);
        final int bottomExtent = endManager.getLowestEdge(endSection);
        final int bottomPosition = getPosition(endManager.getLastView(endSection));

        final int range = bottomPosition - topPosition + 1;
        final int area = bottomExtent - topExtent;
        final float averagePerRow = (float) area / range;

        return Math.round(topPosition * averagePerRow + getPaddingTop() - topExtent);
    }

    private int computeNSScrollOffset(RecyclerView.State state) {
        View top = getChildAt(0);
        LayoutParams params = (LayoutParams) top.getLayoutParams();
        if (params.isHeader) {
            View second = getChildAt(1);
            LayoutParams p = (LayoutParams) second.getLayoutParams();
            if (getPosition(second) - getPosition(top) != 1) {
                top = second;
            }
        }
        return getPosition(top);
    }

    void measureHeader(LayoutState.View header) {
        if (header.wasCached) {
            return;
        }

        // Width to leave for the mSection to which this header belongs. Only applies if the
        // header is being laid out adjacent to the mSection.
        int unavailableWidth = 0;
        LayoutParams lp = (LayoutParams) header.view.getLayoutParams();
        int recyclerWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        if (lp.headerAlignment == HEADER_ALIGN_START && !lp.headerStartMarginIsAuto) {
            unavailableWidth = recyclerWidth - lp.headerStartMargin;
        } else if (lp.headerAlignment == HEADER_ALIGN_END && !lp.headerEndMarginIsAuto) {
            unavailableWidth = recyclerWidth - lp.headerEndMargin;
        }
        measureChildWithMargins(header.view, unavailableWidth, 0);
    }

    /**
     * Find the first view in the hierarchy that can act as an anchor.
     *
     * @param itemCount Number of items currently in the adapter.
     * @return The anchor view, or null if no view is a valid anchor.
     */
    private View getAnchorChild(final int itemCount) {
        if (getChildCount() > 0) {
            final int childCount = getChildCount();

            for (int i = 0; i < childCount; i++) {
                final View view = getChildAt(i);

                // Skip headers.
                if (((LayoutParams) view.getLayoutParams()).isHeader) {
                    //TODO: Handle empty sections with headers.
                    continue;
                }

                final int position = getPosition(view);
                if (position >= 0 && position < itemCount) {
                    return view;
                }
            }
        }
        return null;
    }

    @Override
    public LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        final LayoutParams newLp = new LayoutParams(lp);
        newLp.width = LayoutParams.MATCH_PARENT;
        newLp.height = LayoutParams.MATCH_PARENT;
        newLp.init(lp);
        return newLp;
    }

    @Override
    public RecyclerView.LayoutParams generateLayoutParams(Context c, AttributeSet attrs) {
        return new LayoutParams(c, attrs);
    }

    public enum Direction {
        START,
        END,
        NONE
    }

    public static class LayoutParams extends RecyclerView.LayoutParams {

        private static final boolean DEFAULT_IS_HEADER = false;

        private static final boolean DEFAULT_IS_STICKY = false;

        private static final int HEADER_NONE = -0x01;

        private static final int DEFAULT_HEADER_MARGIN = -0x01;

        public boolean isHeader;

        private static final int DEFAULT_HEADER_ALIGNMENT = HEADER_INLINE;

        public int headerAlignment;

        public int sectionFirstPosition;

        public boolean isSticky;

        public int section;

        public int headerEndMargin;

        public int headerStartMargin;

        public boolean headerStartMarginIsAuto;

        public boolean headerEndMarginIsAuto;

        public LayoutParams(int width, int height) {
            super(width, height);

            isHeader = DEFAULT_IS_HEADER;
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);

            TypedArray a = c.obtainStyledAttributes(attrs,
                    R.styleable.superslim_LayoutManager);
            isHeader = a.getBoolean(
                    R.styleable.superslim_LayoutManager_slm_isHeader,
                    false);
            headerAlignment = a.getInt(
                    R.styleable.superslim_LayoutManager_slm_alignHeader,
                    HEADER_INLINE);
            sectionFirstPosition = a.getInt(
                    R.styleable.superslim_LayoutManager_slm_sectionFirstPosition,
                    HEADER_NONE);
            isSticky = a.getBoolean(
                    R.styleable.superslim_LayoutManager_slm_isSticky,
                    false);
            section = a.getInt(
                    R.styleable.superslim_LayoutManager_slm_section,
                    0);

            // Header margin types can be dimension or integer (enum).
            if (a.getType(R.styleable.superslim_LayoutManager_slm_headerStartMargin) ==
                    TypedValue.TYPE_DIMENSION) {
                headerStartMarginIsAuto = false;
                headerStartMargin = a.getDimensionPixelSize(
                        R.styleable.superslim_LayoutManager_slm_headerStartMargin,
                        0);
            } else {
                headerStartMarginIsAuto = true;
            }
            if (a.getType(R.styleable.superslim_LayoutManager_slm_headerEndMargin) ==
                    TypedValue.TYPE_DIMENSION) {
                headerEndMarginIsAuto = false;
                headerEndMargin = a.getDimensionPixelSize(
                        R.styleable.superslim_LayoutManager_slm_headerEndMargin,
                        0);
            } else {
                headerEndMarginIsAuto = true;
            }

            a.recycle();
        }

        public LayoutParams(ViewGroup.LayoutParams other) {
            super(other);
            init(other);
        }

        private void init(ViewGroup.LayoutParams other) {
            if (other instanceof LayoutParams) {
                final LayoutParams lp = (LayoutParams) other;
                isHeader = lp.isHeader;
                headerAlignment = lp.headerAlignment;
                sectionFirstPosition = lp.sectionFirstPosition;
                isSticky = lp.isSticky;
                section = lp.section;
                headerEndMargin = lp.headerEndMargin;
                headerStartMargin = lp.headerStartMargin;
                headerEndMarginIsAuto = lp.headerEndMarginIsAuto;
                headerStartMarginIsAuto = lp.headerStartMarginIsAuto;
            } else {
                isHeader = DEFAULT_IS_HEADER;
                headerAlignment = DEFAULT_HEADER_ALIGNMENT;
                isSticky = DEFAULT_IS_STICKY;
                headerEndMargin = DEFAULT_HEADER_MARGIN;
                headerStartMargin = DEFAULT_HEADER_MARGIN;
                headerStartMarginIsAuto = true;
                headerEndMarginIsAuto = true;
            }
        }


    }

    public static abstract class SlmFactory {

        abstract public SectionLayoutManager getSectionLayoutManager(LayoutManager layoutManager,
                int section);
    }

    protected static class SavedState implements Parcelable {

        public static final Parcelable.Creator<SavedState> CREATOR
                = new Parcelable.Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };

        public int anchorPosition;

        public int anchorOffset;

        protected SavedState() {
        }

        protected SavedState(Parcel in) {
            anchorPosition = in.readInt();
            anchorOffset = in.readInt();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(anchorPosition);
            out.writeInt(anchorOffset);
        }
    }
}
