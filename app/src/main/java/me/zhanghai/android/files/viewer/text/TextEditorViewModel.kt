/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.text

import android.content.Context
import android.os.Parcelable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java8.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.provider.common.size
import me.zhanghai.android.files.util.ActionState
import me.zhanghai.android.files.util.DataState
import me.zhanghai.android.files.util.isFinished
import me.zhanghai.android.files.util.isReady
import me.zhanghai.android.files.util.toError
import me.zhanghai.android.files.util.toLoading
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

class TextEditorViewModel(val filePath: Path) : ViewModel() {
    private val _file = MutableStateFlow(filePath)
    val file = _file.asStateFlow()

    private val _bytesState = MutableStateFlow<DataState<ByteArray>>(DataState.Loading())

    private var loadJob: Job? = null

    init {
        load()
    }

    fun reload() {
        load()
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            mapFileToBytesState(filePath)
            if (isActive) {
                loadJob = null
            }
        }
    }

    private suspend fun mapFileToBytesState(file: Path) {
        _bytesState.value = _bytesState.value.toLoading()
        try {
            val bytes = runInterruptible(Dispatchers.IO) {
                val reportedSize = file.size()
                if (reportedSize > MAX_FILE_SIZE) {
                    throw IOException("File size $reportedSize is too large")
                }
                file.newInputStream().use { input ->
                    val output = ByteArrayOutputStream(
                        reportedSize.coerceIn(0L, 64 * 1024L).toInt()
                    )
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) {
                            break
                        }
                        total += read
                        if (total > MAX_FILE_SIZE) {
                            throw IOException("File exceeds maximum size $MAX_FILE_SIZE")
                        }
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                }
            }
            if (isBinaryContent(bytes)) {
                throw IOException("Binary file cannot be opened as plain text")
            }
            currentCoroutineContext().ensureActive()
            _bytesState.value = DataState.Success(bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _bytesState.value = _bytesState.value.toError(e)
        }
    }

    private fun isBinaryContent(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        // Allow UTF-16 BOM text files even though they contain NUL bytes.
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if ((b0 == 0xFE && b1 == 0xFF) || (b0 == 0xFF && b1 == 0xFE)) {
                return false
            }
        }
        val sampleSize = bytes.size.coerceAtMost(BINARY_SNIFF_BYTES)
        for (i in 0 until sampleSize) {
            if (bytes[i].toInt() == 0) {
                return true
            }
        }
        return false
    }

    val encoding = MutableStateFlow(StandardCharsets.UTF_8)

    private val _textState = MutableStateFlow<DataState<String>>(DataState.Loading())
    val textState = _textState.asStateFlow()

    init {
        viewModelScope.launch {
            _bytesState.combine(encoding) { bytesState, encoding -> bytesState to encoding }
                .collectLatest { (bytesState, encoding) ->
                    when (bytesState) {
                        is DataState.Loading -> _textState.value = _textState.value.toLoading()
                        is DataState.Success -> {
                            _textState.value = _textState.value.toLoading()
                            try {
                                val text = withContext(Dispatchers.Default) {
                                    String(bytesState.data, encoding)
                                }
                                currentCoroutineContext().ensureActive()
                                _textState.value = DataState.Success(text)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                _textState.value = _textState.value.toError(e)
                            }
                        }
                        is DataState.Error ->
                            _textState.value = _textState.value.toError(bytesState.throwable)
                    }
                }
        }
    }

    val isTextChanged = MutableStateFlow(false)

    private val _writeFileState =
        MutableStateFlow<ActionState<Pair<Path, String>, Unit>>(ActionState.Ready())
    val writeFileState = _writeFileState.asStateFlow()

    fun writeFile(path: Path, text: String, context: Context) {
        if (!_writeFileState.value.isReady) {
            return
        }
        val argument = path to text
        _writeFileState.value = ActionState.Running(argument)
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.Default) {
                text.toByteArray(encoding.value)
            }
            FileJobService.write(path, bytes, context) { successful ->
                if (successful) {
                    loadJob?.cancel()?.also { loadJob = null }
                    _bytesState.value = DataState.Success(bytes)
                }
                _writeFileState.value = if (successful) {
                    ActionState.Success(argument, Unit)
                } else {
                    // The error will be toasted by service so we should never show it in UI, but we
                    // need a non-null value here.
                    ActionState.Error(argument, Throwable())
                }
            }
        }
    }

    fun finishWritingFile() {
        if (_writeFileState.value.isFinished) {
            _writeFileState.value = ActionState.Ready()
        }
    }

    private var editTextSavedState: Parcelable? = null

    fun setEditTextSavedState(editTextSavedState: Parcelable?) {
        this.editTextSavedState = editTextSavedState
    }

    fun removeEditTextSavedState(): Parcelable? {
        val savedState = editTextSavedState
        editTextSavedState = null
        return savedState
    }

    companion object {
        private const val MAX_FILE_SIZE = 1024 * 1024.toLong()
        private const val BINARY_SNIFF_BYTES = 4096
    }
}
