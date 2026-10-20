package com.studibox.app.ui.lms

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Size
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.studibox.app.ui.common.AppStatePlaceholder
import com.studibox.app.ui.common.LoadingState
import com.studibox.app.ui.theme.StudiboxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Открытый PDF. PdfRenderer не потокобезопасен, поэтому вся работа с ним
 * идёт под одним мьютексом; отрисованные страницы держатся в LRU-кэше.
 */
private class PdfDocument(file: File) {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    private val mutex = Mutex()
    @Volatile private var closed = false
    private val cache = object : LruCache<Int, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }

    /** Размеры страниц в пунктах — чтобы заранее зарезервировать место под каждую. */
    val pageSizes: List<Size> = (0 until renderer.pageCount).map { index ->
        renderer.openPage(index).use { Size(it.width, it.height) }
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!closed) { "Документ закрыт" }
            cache[index]?.takeIf { it.width == widthPx }?.let { return@withLock it }
            renderer.openPage(index).use { page ->
                val height = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                cache.put(index, bitmap)
                bitmap
            }
        }.also { if (closed) closeNow() }
    }

    /** Если страница сейчас рисуется — документ закроется сразу после неё. */
    fun close() {
        closed = true
        if (mutex.tryLock()) {
            try {
                closeNow()
            } finally {
                mutex.unlock()
            }
        }
    }

    private fun closeNow() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(path: String, title: String, onBack: () -> Unit) {
    val colors = StudiboxTheme.colors

    val document by produceState<Result<PdfDocument>?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { runCatching { PdfDocument(File(path)) } }
    }
    DisposableEffect(document) {
        onDispose { document?.getOrNull()?.close() }
    }

    val listState = rememberLazyListState()
    val pageCount = document?.getOrNull()?.pageSizes?.size ?: 0
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.textPrimary,
                    navigationIconContentColor = colors.textSecondary
                ),
                title = {
                    androidx.compose.foundation.layout.Column {
                        Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (pageCount > 0) {
                            Text(
                                text = "Страница $currentPage из $pageCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textTertiary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        val result = document
        when {
            result == null -> LoadingState("Открываем лекцию…", modifier)
            result.isFailure -> AppStatePlaceholder(
                icon = Icons.Filled.ErrorOutline,
                title = "Не удалось открыть PDF",
                description = "Файл повреждён или защищён паролем.",
                modifier = modifier
            )
            else -> PdfPages(result.getOrThrow(), listState, modifier)
        }
    }
}

@Composable
private fun PdfPages(
    document: PdfDocument,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = modifier
            .background(Color(0xFF8A8A8A))
            // Двумя пальцами — масштаб и сдвиг; одним пальцем список листается как обычно.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } >= 2) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 4f)
                            val maxShift = size.width * (scale - 1f) / 2f
                            offsetX = (offsetX + event.calculatePan().x).coerceIn(-maxShift, maxShift)
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scale = 1f
                    offsetX = 0f
                })
            }
    ) {
        val density = LocalDensity.current
        // Рендерим с запасом по разрешению, чтобы текст оставался чётким при увеличении.
        val renderWidthPx = with(density) { (maxWidth * 2f).toPx() }.toInt().coerceIn(600, 2400)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                },
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(document.pageSizes.size) { index ->
                val pageSize = document.pageSizes[index]
                val bitmap by produceState<Bitmap?>(initialValue = null, index, renderWidthPx) {
                    value = runCatching { document.render(index, renderWidthPx) }.getOrNull()
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(pageSize.width.toFloat() / pageSize.height.coerceAtLeast(1))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    val current = bitmap
                    if (current == null) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    } else {
                        Image(
                            bitmap = current.asImageBitmap(),
                            contentDescription = "Страница ${index + 1}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
