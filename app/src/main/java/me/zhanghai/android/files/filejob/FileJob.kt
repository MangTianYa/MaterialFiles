/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import me.zhanghai.android.files.util.showToast
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.atomic.AtomicInteger

abstract class FileJob {
    val id: Int = nextNonZeroId()

    internal lateinit var service: FileJobService
        private set

    fun runOn(service: FileJobService) {
        this.service = service
        try {
            run()
            // TODO: Toast
        } catch (e: InterruptedIOException) {
            // TODO
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
            service.showToast(e.toString())
        } finally {
            service.notificationManager.cancel(id)
        }
    }

    @Throws(IOException::class)
    protected abstract fun run()

    companion object {
        private val nextJobId = AtomicInteger(1)

        private fun nextNonZeroId(): Int {
            while (true) {
                val candidate = nextJobId.getAndAdd(2) and Int.MAX_VALUE
                if (candidate != 0) {
                    return candidate
                }
            }
        }
    }
}
