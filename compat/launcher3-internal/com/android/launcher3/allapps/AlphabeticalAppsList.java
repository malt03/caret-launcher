/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Modified for CaretLauncher: a stand-in that keeps only what CaretLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */
package com.android.launcher3.allapps;

import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_BOTTOM_VIEW_TO_SCROLL_TO;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;

import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.LabelComparator;
import com.android.launcher3.views.ActivityContext;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The alphabetically sorted list of applications.
 *
 * <p>Stand-in made from Launcher3's AlphabeticalAppsList (android-17.0.0_r1) by deleting the work
 * profile and the private space, which CaretLauncher does not have. Changes are marked with
 * "CaretLauncher:".
 */
public class AlphabeticalAppsList implements AllAppsStore.OnUpdateListener {

    public static final String TAG = "AlphabeticalAppsList";
    // CaretLauncher: no work profile or private space.

    /**
     * Info about a fast scroller section, depending if sections are merged, the fast scroller
     * sections will not be the same set as the section headers.
     */
    public static class FastScrollSectionInfo {
        // The section name
        public final CharSequence sectionName;
        // The item position
        public final int position;
        // The view id associated with this section
        public int id = -1;

        public FastScrollSectionInfo(CharSequence sectionName, int position) {
            this.sectionName = sectionName;
            this.position = position;
        }

        public void setId(int id) {
            this.id = id;
        }
    }


    private final ActivityContext mActivityContext;

    // The set of apps from the system
    private final List<AppInfo> mApps = new ArrayList<>();
    @Nullable
    private final AllAppsStore mAllAppsStore;

    // The number of results in current adapter
    private int mAccessibilityResultsCount = 0;
    // The current set of adapter items
    private final ArrayList<AdapterItem> mAdapterItems = new ArrayList<>();
    // The set of sections that we allow fast-scrolling to (includes non-merged sections)
    private final List<FastScrollSectionInfo> mFastScrollerSections = new ArrayList<>();

    // The of ordered component names as a result of a search query
    private final ArrayList<AdapterItem> mSearchResults = new ArrayList<>();
    private BaseAllAppsAdapter mAdapter;
    private AppInfoComparator mAppNameComparator;
    private int mNumAppsPerRowAllApps;
    private int mNumAppRowsInAdapter;
    private Predicate<ItemInfo> mItemFilter;

    public AlphabeticalAppsList(ActivityContext activityContext, @Nullable AllAppsStore appsStore) {
        mAllAppsStore = appsStore;
        mActivityContext = activityContext;
        Context context = activityContext.asContext();
        mAppNameComparator = new AppInfoComparator(context);
        mNumAppsPerRowAllApps =
                mActivityContext.getDeviceProfile().getAllAppsProfile().getNumShownAllAppsColumns();
        if (mAllAppsStore != null) {
            mAllAppsStore.addUpdateListener(this);
        }
        // CaretLauncher: no private space badges.
    }

    public void setNumAppsPerRowAllApps(int numAppsPerRow) {
        mNumAppsPerRowAllApps = numAppsPerRow;
    }

    public void updateItemFilter(Predicate<ItemInfo> itemFilter) {
        this.mItemFilter = itemFilter;
        onAppsUpdated();
    }

    /**
     * Sets the adapter to notify when this dataset changes.
     */
    public void setAdapter(BaseAllAppsAdapter adapter) {
        mAdapter = adapter;
    }

    /**
     * Returns fast scroller sections of all the current filtered applications.
     */
    public List<FastScrollSectionInfo> getFastScrollerSections() {
        return mFastScrollerSections;
    }

    /**
     * Returns the current filtered list of applications broken down into their sections.
     */
    public List<AdapterItem> getAdapterItems() {
        return mAdapterItems;
    }

    /**
     * Returns the child adapter item with IME launch focus.
     */
    public AdapterItem getFocusedChild() {
        if (mAdapterItems.size() == 0 || getFocusedChildIndex() == -1) {
            return null;
        }
        return mAdapterItems.get(getFocusedChildIndex());
    }

    /**
     * Returns the index of the child with IME launch focus.
     */
    public int getFocusedChildIndex() {
        for (AdapterItem item : mAdapterItems) {
            if (item.isCountedForAccessibility()) {
                return mAdapterItems.indexOf(item);
            }
        }
        return -1;
    }

    /**
     * Returns the number of rows of applications
     */
    public int getNumAppRows() {
        return mNumAppRowsInAdapter;
    }

    /**
     * Returns the number of applications in this list.
     */
    public int getNumFilteredApps() {
        return mAccessibilityResultsCount;
    }

    /**
     * Returns whether there are search results which will hide the A-Z list.
     */
    public boolean hasSearchResults() {
        return !mSearchResults.isEmpty();
    }

    /**
     * Sets results list for search
     */
    public boolean setSearchResults(ArrayList<AdapterItem> results) {
        if (Objects.equals(results, mSearchResults)) {
            return false;
        }
        mSearchResults.clear();
        if (results != null) {
            mSearchResults.addAll(results);
        }
        updateAdapterItems();
        return true;
    }

    /**
     * Updates internals when the set of apps are updated.
     */
    @Override
    public void onAppsUpdated() {
        // CaretLauncher: no private profile animations.
        if (mAllAppsStore == null) {
            return;
        }
        // Sort the list of apps
        mApps.clear();

        // CaretLauncher: no private space.
        Stream<AppInfo> appSteam = Stream.of(mAllAppsStore.getApps());

        if (!hasSearchResults() && mItemFilter != null) {
            appSteam = appSteam.filter(mItemFilter);
        }
        appSteam = appSteam.sorted(mAppNameComparator);

        // As a special case for some languages (currently only Simplified Chinese), we may need to
        // coalesce sections
        Locale curLocale = mActivityContext.asContext().getResources().getConfiguration().locale;
        boolean localeRequiresSectionSorting = curLocale.equals(Locale.SIMPLIFIED_CHINESE);
        if (localeRequiresSectionSorting) {
            // Compute the section headers. We use a TreeMap with the section name comparator to
            // ensure that the sections are ordered when we iterate over it later
            appSteam = appSteam.collect(Collectors.groupingBy(
                    info -> info.sectionName,
                    () -> new TreeMap<>(new LabelComparator()),
                    Collectors.toCollection(ArrayList::new)))
                    .values()
                    .stream()
                    .flatMap(ArrayList::stream);
        }

        appSteam.forEachOrdered(mApps::add);
        // Recompose the set of adapter items from the current set of apps
        if (mSearchResults.isEmpty()) {
            updateAdapterItems();
        }
    }

    /**
     * Updates the set of filtered apps with the current filter. At this point, we expect
     * mCachedSectionNames to have been calculated for the set of all apps in mApps.
     */
    public void updateAdapterItems() {
        List<AdapterItem> oldItems = new ArrayList<>(mAdapterItems);
        // Prepare to update the list of sections, filtered apps, etc.
        mFastScrollerSections.clear();
        Log.d(TAG, "Clearing FastScrollerSections.");
        mAdapterItems.clear();
        mAccessibilityResultsCount = 0;

        // Recreate the filtered and sectioned apps (for convenience for the grid layout) from the
        // ordered set of sections
        if (hasSearchResults()) {
            mAdapterItems.addAll(mSearchResults);
        } else {
            int position = 0;
            // CaretLauncher: no work profile or private space.
            position = addAppsWithSections(mApps, position);
            if (!mFastScrollerSections.isEmpty()) {
                // After all the adapterItems are added, add a view to the bottom so that user can
                // scroll all the way down.
                mAdapterItems.add(new AdapterItem(VIEW_TYPE_BOTTOM_VIEW_TO_SCROLL_TO));
                mFastScrollerSections.add(new FastScrollSectionInfo(
                        mFastScrollerSections.get(mFastScrollerSections.size() - 1).sectionName,
                        position++));
                Log.d(TAG, "Adding FastScrollSection duplicate to scroll to the bottom.");
            }
        }
        mAccessibilityResultsCount = (int) mAdapterItems.stream()
                .filter(AdapterItem::isCountedForAccessibility).count();

        if (mNumAppsPerRowAllApps != 0) {
            // Update the number of rows in the adapter after we do all the merging (otherwise, we
            // would have to shift the values again)
            int numAppsInSection = 0;
            int numAppsInRow = 0;
            int rowIndex = -1;
            for (AdapterItem item : mAdapterItems) {
                item.rowIndex = 0;
                // CaretLauncher: no private space.
                if (BaseAllAppsAdapter.isDividerViewType(item.viewType)) {
                    numAppsInSection = 0;
                } else if (BaseAllAppsAdapter.isIconViewType(item.viewType)) {
                    if (numAppsInSection % mNumAppsPerRowAllApps == 0) {
                        numAppsInRow = 0;
                        rowIndex++;
                    }
                    item.rowIndex = rowIndex;
                    item.rowAppIndex = numAppsInRow;
                    numAppsInSection++;
                    numAppsInRow++;
                }
            }
            mNumAppRowsInAdapter = rowIndex + 1;
        }

        if (mAdapter != null) {
            DiffUtil.calculateDiff(new MyDiffCallback(oldItems, mAdapterItems), false)
                    .dispatchUpdatesTo(mAdapter);
        }
    }

    private int addAppsWithSections(List<AppInfo> appList, int startPosition) {
        String lastSectionName = null;
        int position = startPosition;
        // CaretLauncher: no private apps.
        Log.d(TAG, "Adding apps with sections.");
        for (int i = 0; i < appList.size(); i++) {
            AppInfo info = appList.get(i);
            mAdapterItems.add(AdapterItem.asApp(info));

            String sectionName = info.sectionName;
            // Create a new section if the section names do not match
            if (!sectionName.equals(lastSectionName)) {
                Log.d(TAG, "addAppsWithSections: adding sectionName: " + sectionName
                    + " with appInfoTitle: " + info.title);
                lastSectionName = sectionName;
                FastScrollSectionInfo sectionInfo = new FastScrollSectionInfo(sectionName, position);
                mFastScrollerSections.add(sectionInfo);
            }
            position++;
        }
        return position;
    }

    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "SectionInfo[] size: " + mFastScrollerSections.size());
        for (int i = 0; i < mFastScrollerSections.size(); i++) {
            writer.println("\tFastScrollSection: " + mFastScrollerSections.get(i).sectionName);
        }
    }

    private static class MyDiffCallback extends DiffUtil.Callback {

        private final List<AdapterItem> mOldList;
        private final List<AdapterItem> mNewList;

        MyDiffCallback(List<AdapterItem> oldList, List<AdapterItem> newList) {
            mOldList = oldList;
            mNewList = newList;
        }

        @Override
        public int getOldListSize() {
            return mOldList.size();
        }

        @Override
        public int getNewListSize() {
            return mNewList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return mOldList.get(oldItemPosition).isSameAs(mNewList.get(newItemPosition));
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return mOldList.get(oldItemPosition).isContentSame(mNewList.get(newItemPosition));
        }
    }

}