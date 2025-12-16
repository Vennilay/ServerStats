package com.vennilay.serverstats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit


private const val SERVER_IP = "10.0.2.2"
private const val PORT = "61208"
private const val URL = "http://$SERVER_IP:$PORT/api/4/all"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SimpleMonitorScreen()
        }
    }
}

@Composable
fun SimpleMonitorScreen() {
    var displayText by remember { mutableStateOf("Подключение к $SERVER_IP...") }

    LaunchedEffect(Unit) {
        fetchDataLoop { text ->
            displayText = text
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(text = displayText, fontSize = 16.sp)
    }
}

private suspend fun fetchDataLoop(onUpdate: (String) -> Unit) {
    val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .build()

    withContext(Dispatchers.IO) {
        while (isActive) {
            val text = try {
                val json: String = fetchData(client)
                parseJsonToText(json)
            } catch (e: Exception) {
                "Ошибка:\n${e.message}\n"
            }
            onUpdate(text)
            delay(1000)
        }
    }
}

private fun fetchData(client: OkHttpClient): String {
    val request = Request.Builder().url(URL).build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw Exception("Code: ${response.code}")
        return response.body?.string() ?: "{}"
    }
}

private fun parseJsonToText(jsonString: String): String {
    val root = JSONObject(jsonString)
    val sb = StringBuilder()

    val system = root.optJSONObject("system")
    val hostname = if (system != null) system.optString("hostname", "PC") else root.optString("hostname", "PC")
    val osVersion = if (system != null) system.optString("os_version", "") else ""

    sb.append("Сервер: ").append(hostname).append('\n')
    if (osVersion.isNotEmpty()) {
        sb.append("Релиз: ").append(osVersion).append('\n')
    }
    sb.append("-------------------\n")

    val cpu = root.optJSONObject("cpu")
    if (cpu != null) {
        sb.append("CPU Load: ").append(cpu.optDouble("total")).append("%\n")
    }

    val mem = root.optJSONObject("mem")
    if (mem != null) {
        val used = mem.optLong("used") / 1024 / 1024 / 1024.0
        val total = mem.optLong("total") / 1024 / 1024 / 1024.0
        val percent = mem.optDouble("percent")
        sb.append(String.format("RAM: %.1f%% (%.2f / %.2f GB)\n", percent, used, total))
    }

    val fsArr = root.optJSONArray("fs")
    if (fsArr != null) {
        sb.append("\nДиски:\n")
        for (i in 0 until fsArr.length()) {
            val disk = fsArr.getJSONObject(i)
            val name = disk.optString("mnt_point")
            val size = disk.optLong("size") / 1024 / 1024 / 1024.0
            val usedPercent = disk.optDouble("percent")
            if (size > 1.0) {
                sb.append(String.format("%s: %.1f%% из %.0f GB\n", name, usedPercent, size))
            }
        }
    }

    return sb.toString()
}