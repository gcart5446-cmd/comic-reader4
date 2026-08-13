package com.example

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.awxkee.jxlcoder.coil.JxlDecoder
import com.example.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            val imageLoader = ImageLoader.Builder(applicationContext)
                .components {
                    add(JxlDecoder.Factory())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        add(ImageDecoderDecoder.Factory())
                    } else {
                        add(GifDecoder.Factory())
                    }
                }
                .memoryCache {
                    MemoryCache.Builder(applicationContext)
                        .maxSizePercent(0.35) // 35% RAM dedicated to bitmap caching
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(applicationContext.cacheDir.resolve("image_cache"))
                        .maxSizeBytes(512 * 1024 * 1024) // 512 MB disk cache
                        .build()
                }
                .allowHardware(true)
                .crossfade(false) // Instant image swapping for max reader speed
                .build()
            Coil.setImageLoader(imageLoader)
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        setContent {
            MyApplicationTheme {
                MainScreen()
            }
        }
    }
}



