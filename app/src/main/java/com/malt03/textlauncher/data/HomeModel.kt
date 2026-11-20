package com.malt03.textlauncher.data

import java.util.concurrent.Executor

/** Something placed on the home screen (docs/spec.md 5.2). */
sealed class HomeItem(val id: Long) {
    var cellX = 0
    var cellY = 0
    var spanX = 1
    var spanY = 1

    class App(id: Long, val appKey: String) : HomeItem(id)

    /** A folder has no name (docs/spec.md 5.2). */
    class Folder(id: Long, val appKeys: MutableList<String>) : HomeItem(id)

    class Widget(id: Long, val appWidgetId: Int, val provider: String) : HomeItem(id)
}

/**
 * The home layout, kept in memory on the main thread and saved as a whole to
 * [LauncherDatabase] after every change.
 */
class HomeModel(private val db: LauncherDatabase, private val ioExecutor: Executor) {
    val items = mutableListOf<HomeItem>()
    private var nextId = 1L

    /** Called on a background thread once, before the model is used. */
    fun loadBlocking(): List<HomeItem> {
        val rows = db.loadHomeRows()
        val loaded = mutableListOf<HomeItem>()
        val folders = HashMap<Long, HomeItem.Folder>()
        for (row in rows.filter { it.container == HomeRow.CONTAINER_HOME }) {
            val item = when (row.type) {
                HomeRow.TYPE_APP -> row.appKey?.let { HomeItem.App(row.id, it) }
                HomeRow.TYPE_FOLDER -> HomeItem.Folder(row.id, mutableListOf()).also { folders[row.id] = it }
                HomeRow.TYPE_WIDGET -> if (row.widgetId != null && row.provider != null) {
                    HomeItem.Widget(row.id, row.widgetId, row.provider)
                } else {
                    null
                }
                else -> null
            } ?: continue
            item.cellX = row.cellX; item.cellY = row.cellY
            item.spanX = row.spanX; item.spanY = row.spanY
            loaded += item
        }
        for (row in rows.filter { it.container != HomeRow.CONTAINER_HOME }.sortedBy { it.rank }) {
            val key = row.appKey ?: continue
            folders[row.container]?.appKeys?.add(key)
        }
        return loaded
    }

    fun setLoaded(loaded: List<HomeItem>) {
        items.clear()
        items += loaded
        nextId = (items.maxOfOrNull { it.id } ?: 0L) + 1
        // Folder ids live in the same id space as their contents' rows.
        nextId += items.filterIsInstance<HomeItem.Folder>().sumOf { it.appKeys.size }.toLong()
    }

    fun newId(): Long = nextId++

    fun save() {
        val rows = mutableListOf<HomeRow>()
        var contentId = (items.maxOfOrNull { it.id } ?: 0L) + 1
        for (item in items) {
            rows += when (item) {
                is HomeItem.App -> HomeRow(item.id, HomeRow.TYPE_APP, HomeRow.CONTAINER_HOME, item.cellX, item.cellY, item.spanX, item.spanY, 0, item.appKey, null, null)
                is HomeItem.Folder -> HomeRow(item.id, HomeRow.TYPE_FOLDER, HomeRow.CONTAINER_HOME, item.cellX, item.cellY, item.spanX, item.spanY, 0, null, null, null)
                is HomeItem.Widget -> HomeRow(item.id, HomeRow.TYPE_WIDGET, HomeRow.CONTAINER_HOME, item.cellX, item.cellY, item.spanX, item.spanY, 0, null, item.appWidgetId, item.provider)
            }
            if (item is HomeItem.Folder) {
                item.appKeys.forEachIndexed { rank, key ->
                    rows += HomeRow(contentId++, HomeRow.TYPE_APP, item.id, 0, 0, 1, 1, rank, key, null, null)
                }
            }
        }
        nextId = maxOf(nextId, contentId)
        ioExecutor.execute { db.replaceHomeRows(rows) }
    }
}
