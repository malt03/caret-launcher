/*
 * Copyright (C) 2025 The Android Open Source Project
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
 * Modified for TextLauncher: a stand-in that keeps only what TextLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */

package com.android.launcher3.model.data

import android.util.SparseArray
import androidx.core.util.putAll
import androidx.core.util.valueIterator
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream
import java.util.stream.StreamSupport

/**
 * An immutable representation of all the workspace items (shortcuts, folders, widgets and predicted
 * items)
 *
 * TextLauncher: one page and no predictions, so no screens or predicted contents to collect.
 */
sealed class WorkspaceData : Iterable<ItemInfo> {

    /** Returns the [ItemInfo] associated with the [id] or null */
    abstract operator fun get(id: Int): ItemInfo?

    fun stream(): Stream<ItemInfo> = StreamSupport.stream(spliterator(), false)

    /** Version determines the uniqueness per model load cycle */
    abstract val version: Int
    /** Number of times, this data has been modified */
    abstract val modificationId: Int

    /** Returns an immutable copy of the dataset */
    abstract fun copy(): WorkspaceData

    override fun equals(other: Any?): Boolean {
        return other is WorkspaceData &&
            other.version == version &&
            other.modificationId == modificationId
    }

    /** A mutable implementation of [WorkspaceData] */
    class MutableWorkspaceData : WorkspaceData() {

        val itemsIdMap = SparseArray<ItemInfo>()

        override var version: Int = VERSION_COUNTER.incrementAndGet()

        override var modificationId: Int = 0

        override fun iterator() = itemsIdMap.valueIterator()

        override fun get(id: Int): ItemInfo? = itemsIdMap.get(id)

        /** Replaces the existing dataset with [items] */
        fun replaceDataMap(items: SparseArray<ItemInfo>) {
            itemsIdMap.clear()
            itemsIdMap.putAll(items)
            version = VERSION_COUNTER.incrementAndGet()
            modificationId = 0
        }

        /** Performs some modification on the dataset and updates the [modificationId] */
        inline fun modifyItems(block: SparseArray<ItemInfo>.() -> Unit) {
            block.invoke(itemsIdMap)
            modificationId++
        }

        override fun copy(): WorkspaceData =
            ImmutableWorkspaceData(version, modificationId, itemsIdMap)
    }

    /** An immutable implementation of [WorkspaceData] */
    class ImmutableWorkspaceData(
        override val version: Int,
        override val modificationId: Int,
        items: SparseArray<ItemInfo>,
    ) : WorkspaceData() {

        private val itemsIdMap = items.clone()

        override fun iterator() = itemsIdMap.valueIterator()

        override fun get(id: Int): ItemInfo? = itemsIdMap.get(id)

        override fun copy(): WorkspaceData = this
    }

    companion object {
        private val VERSION_COUNTER = AtomicInteger(1)
    }
}
