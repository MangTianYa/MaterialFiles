/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import android.os.Environment
import android.os.StatFs
import java8.nio.file.Path
import java8.nio.file.Paths
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.provider.common.PosixFileStore
import me.zhanghai.android.files.provider.common.getFileStore
import me.zhanghai.android.files.provider.linux.LinuxPath
import me.zhanghai.android.files.provider.linux.LocalLinuxFileStore
import me.zhanghai.android.files.provider.linux.isLinuxPath
import me.zhanghai.android.files.provider.root.RootStrategy
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.valueCompat
import java.io.IOException
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class StorageSpaceInfo(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val isReadOnly: Boolean
) {
    val usagePercent: Int?
        get() = if (totalBytes > 0L) {
            ((usedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
        } else {
            null
        }
}

/**
 * Resolves filesystem capacity and mount read/write status with root-aware fallbacks for
 * system-as-root / virtual rootfs mounts where `/` would otherwise report 0 B free or RAM tmpfs.
 */
object StorageSpaceResolver {
    private val ROOT_VIRTUAL_FS_TYPES = setOf(
        "rootfs", "tmpfs", "ramfs", "sysfs", "proc", "devtmpfs", "devfs",
        "overlay", "erofs", "squashfs"
    )

    private data class CachedSpaceEntry(
        val info: StorageSpaceInfo,
        val timestampMs: Long
    )

    private const val CACHE_TTL_MS = 5_000L

    private val spaceCache = ConcurrentHashMap<String, CachedSpaceEntry>()
    @Volatile
    private var cachedRootDrawerSpace: CachedSpaceEntry? = null
    private val cacheGeneration = AtomicLong(0L)

    private val sizeDecimalFormat = ThreadLocal.withInitial {
        DecimalFormat("0.##", DecimalFormatSymbols(Locale.ROOT))
    }
    private val largeSizeDecimalFormat = ThreadLocal.withInitial {
        DecimalFormat("0.#", DecimalFormatSymbols(Locale.ROOT))
    }

    fun clearCache() {
        cacheGeneration.incrementAndGet()
        spaceCache.clear()
        cachedRootDrawerSpace = null
    }

    fun getCachedPathSpaceInfo(path: Path): StorageSpaceInfo? {
        if (!path.isLinuxPath) {
            return null
        }
        val now = System.currentTimeMillis()
        val linuxPath = path.toFile().path
        val entry = spaceCache[linuxPath] ?: if (linuxPath == FileSystemRoot.LINUX_PATH) {
            cachedRootDrawerSpace
        } else {
            null
        }
        return entry?.takeIf { now - it.timestampMs <= CACHE_TTL_MS }?.info
    }

    /**
     * Resolves full capacity and read-only status for a file list path (safe to run off main
     * thread; uses root file store when enabled and required).
     * Returns pair of (info, rootDrawerUpdated).
     */
    fun resolvePathSpaceInfo(
        path: Path,
        forceRefresh: Boolean = false
    ): Pair<StorageSpaceInfo?, Boolean> {
        if (!path.isLinuxPath) {
            val store = runCatching { path.getFileStore() }.getOrNull()
            val total = runCatching { store?.totalSpace ?: 0L }.getOrDefault(0L)
            val free = runCatching { store?.usableSpace ?: 0L }.getOrDefault(0L)
            val unallocated = runCatching { store?.unallocatedSpace ?: free }.getOrDefault(free)
            val used = computeUsedBytes(total, free, unallocated)
            val isReadOnly = runCatching { store?.isReadOnly }.getOrNull() ?: path.fileSystem.isReadOnly
            return StorageSpaceInfo(total, used, free, isReadOnly) to false
        }

        val linuxPath = path.toFile().path
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            spaceCache[linuxPath]?.takeIf { now - it.timestampMs <= CACHE_TTL_MS }?.let {
                return it.info to false
            }
        }
        val resolveGeneration = cacheGeneration.get()

        val store = runCatching { path.getFileStore() as? PosixFileStore }.getOrNull()
        val isRootPath = linuxPath == FileSystemRoot.LINUX_PATH
        var total = 0L
        var free = 0L
        var unallocated = 0L
        var isReadOnly = false
        var fsType = ""

        if (store != null) {
            total = runCatching { store.totalSpace }.getOrDefault(0L)
            free = runCatching { store.usableSpace }.getOrDefault(0L)
            unallocated = runCatching { store.unallocatedSpace }.getOrDefault(free)
            isReadOnly = runCatching { store.isReadOnly }.getOrDefault(false)
            fsType = runCatching { store.type().lowercase(Locale.ROOT) }.getOrDefault("")
        } else {
            val stat = readStatFsSpace(linuxPath)
            if (stat != null) {
                total = stat.totalBytes
                free = stat.freeBytes
                unallocated = (total - stat.usedBytes).coerceAtLeast(0L)
            }
            isReadOnly = !runCatching { path.toFile().canWrite() }.getOrDefault(true)
        }

        val isRootMemoryOrDummyFs = fsType in ROOT_VIRTUAL_FS_TYPES
        val isUnusableRootMirror = isRootPath && (
            total <= 0L ||
                isRootMemoryOrDummyFs ||
                (isReadOnly && free == 0L)
            )

        var rootCacheChanged = false
        val info = if (isUnusableRootMirror) {
            val dataSpace = resolveDataPartitionSpace(allowRoot = true)
            if (dataSpace != null) {
                if (cacheGeneration.get() == resolveGeneration) {
                    rootCacheChanged = updateRootDrawerCache(dataSpace, resolveGeneration)
                }
                StorageSpaceInfo(
                    totalBytes = dataSpace.totalBytes,
                    usedBytes = dataSpace.usedBytes,
                    freeBytes = dataSpace.freeBytes,
                    isReadOnly = isReadOnly
                )
            } else {
                val used = computeUsedBytes(total, free, unallocated)
                StorageSpaceInfo(total, used, free, isReadOnly)
            }
        } else {
            val used = computeUsedBytes(total, free, unallocated)
            StorageSpaceInfo(total, used, free, isReadOnly).also {
                if (isRootPath && it.totalBytes > 0L && cacheGeneration.get() == resolveGeneration) {
                    rootCacheChanged = updateRootDrawerCache(it, resolveGeneration)
                }
            }
        }

        if (info.totalBytes > 0L && cacheGeneration.get() == resolveGeneration) {
            spaceCache[linuxPath] = CachedSpaceEntry(info, System.currentTimeMillis())
        }
        return info to rootCacheChanged
    }

    /**
     * Resolves drawer card capacity synchronously without triggering interactive root prompts on
     * app startup.
     */
    fun resolveDrawerStorageSpace(linuxPath: String): StorageSpaceInfo? {
        if (linuxPath == FileSystemRoot.LINUX_PATH) {
            val now = System.currentTimeMillis()
            cachedRootDrawerSpace?.takeIf { now - it.timestampMs <= CACHE_TTL_MS }?.let {
                return it.info
            }
            val resolveGeneration = cacheGeneration.get()

            val localStore = runCatching {
                LocalLinuxFileStore(Paths.get(linuxPath) as LinuxPath)
            }.getOrNull()
            val localTotal = runCatching { localStore?.totalSpace }.getOrNull() ?: 0L
            val localFree = runCatching { localStore?.usableSpace }.getOrNull() ?: 0L
            val localUnalloc = runCatching { localStore?.unallocatedSpace }.getOrNull() ?: localFree
            val localReadOnly = runCatching { localStore?.isReadOnly }.getOrNull() ?: true
            val fsType = runCatching { localStore?.type()?.lowercase(Locale.ROOT) }.getOrNull().orEmpty()

            val isUnusableRoot = localTotal <= 0L ||
                fsType in ROOT_VIRTUAL_FS_TYPES ||
                (localReadOnly && localFree == 0L)

            if (!isUnusableRoot) {
                val info = StorageSpaceInfo(
                    totalBytes = localTotal,
                    usedBytes = computeUsedBytes(localTotal, localFree, localUnalloc),
                    freeBytes = localFree,
                    isReadOnly = localReadOnly
                )
                if (cacheGeneration.get() == resolveGeneration) {
                    cachedRootDrawerSpace = CachedSpaceEntry(info, System.currentTimeMillis())
                }
                return info
            }

            // Use /data (via app filesDir / primary external fallback) without blocking on su UI.
            val dataSpace = resolveDataPartitionSpace(allowRoot = false)
                ?: readStatFsSpace(Environment.getRootDirectory().path)
            if (dataSpace != null && cacheGeneration.get() == resolveGeneration) {
                cachedRootDrawerSpace = CachedSpaceEntry(dataSpace, System.currentTimeMillis())
            }
            return dataSpace
        }

        return readStatFsSpace(linuxPath)
    }

    private fun resolveDataPartitionSpace(allowRoot: Boolean): StorageSpaceInfo? {
        val dataDir = Environment.getDataDirectory().path
        if (allowRoot && Settings.ROOT_STRATEGY.valueCompat != RootStrategy.NEVER) {
            val dataPath = Paths.get(dataDir)
            val rootStore = runCatching { dataPath.getFileStore() as? PosixFileStore }.getOrNull()
            if (rootStore != null) {
                val total = runCatching { rootStore.totalSpace }.getOrDefault(0L)
                if (total > 0L) {
                    val free = runCatching { rootStore.usableSpace }.getOrDefault(0L)
                    val unallocated = runCatching { rootStore.unallocatedSpace }.getOrDefault(free)
                    val used = computeUsedBytes(total, free, unallocated)
                    val isReadOnly = runCatching { rootStore.isReadOnly }.getOrDefault(false)
                    return StorageSpaceInfo(total, used, free, isReadOnly)
                }
            }
        }

        readStatFsSpace(dataDir)?.let { return it }
        application.filesDir?.path?.let(::readStatFsSpace)?.let { return it }
        @Suppress("DEPRECATION")
        readStatFsSpace(Environment.getExternalStorageDirectory().path)?.let { return it }
        return null
    }

    private fun readStatFsSpace(path: String): StorageSpaceInfo? =
        try {
            val statFs = StatFs(path)
            val total = statFs.totalBytes
            if (total <= 0L) {
                null
            } else {
                val available = statFs.availableBytes
                val free = statFs.freeBytes
                val used = computeUsedBytes(total, available, free)
                StorageSpaceInfo(total, used, available, isReadOnly = false)
            }
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IOException) {
            null
        }

    private fun computeUsedBytes(total: Long, available: Long, unallocated: Long): Long {
        if (total <= 0L) {
            return 0L
        }
        return if (unallocated in 0L..total) {
            total - unallocated
        } else {
            (total - available).coerceAtLeast(0L)
        }
    }

    private fun updateRootDrawerCache(newInfo: StorageSpaceInfo, generation: Long): Boolean {
        if (cacheGeneration.get() != generation) {
            return false
        }
        val previous = cachedRootDrawerSpace?.info
        cachedRootDrawerSpace = CachedSpaceEntry(newInfo, System.currentTimeMillis())
        return previous == null ||
            previous.totalBytes != newInfo.totalBytes ||
            previous.usedBytes != newInfo.usedBytes ||
            previous.freeBytes != newInfo.freeBytes
    }

    fun formatCompactSize(bytes: Long): String {
        val safeBytes = bytes.coerceAtLeast(0L)
        if (safeBytes < 1000L) {
            return "${safeBytes}B"
        }
        val units = arrayOf("K", "M", "G", "T", "P")
        var value = safeBytes / 1000.0
        var unitIndex = 0
        while (value >= 999.95 && unitIndex < units.lastIndex) {
            value /= 1000.0
            unitIndex++
        }
        val formatted = if (value >= 100.0) {
            largeSizeDecimalFormat.get()!!.format(value)
        } else {
            sizeDecimalFormat.get()!!.format(value)
        }
        return "$formatted${units[unitIndex]}"
    }
}
