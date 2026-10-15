package app.bedrockhost.mobile

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Optional remote management for a compatible CraftNode/Pterodactyl Client API panel. */
object CraftNodeClient {
    data class Server(
        val identifier: String,
        val name: String,
        val state: String,
        val memoryMb: Long,
        val diskMb: Long,
        val cpuPercent: Double
    )

    private fun request(baseUrl: String, token: String, path: String, method: String = "GET", body: String? = null): String {
        val base = baseUrl.trim().trimEnd('/')
        require(base.startsWith("https://", true)) { "Use your panel's HTTPS URL." }
        require(token.isNotBlank()) { "Enter a Client API key." }
        val connection = URL("$base/api/client$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 12000
            connection.readTimeout = 15000
            connection.setRequestProperty("Authorization", "Bearer ${token.trim()}")
            connection.setRequestProperty("Accept", "application/vnd.pterodactyl.v1+json")
            connection.setRequestProperty("Content-Type", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val hint = when (status) {
                    401, 403 -> "API key rejected or Client API access is not enabled."
                    404 -> "Panel API endpoint not found. Check the panel URL."
                    else -> "Panel returned HTTP $status."
                }
                error("$hint ${response.take(180)}")
            }
            return response
        } finally { connection.disconnect() }
    }

    fun listServers(baseUrl: String, token: String): List<Server> {
        val data = JSONObject(request(baseUrl, token, "/")).optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { i ->
            val attrs = data.optJSONObject(i)?.optJSONObject("attributes") ?: return@mapNotNull null
            val res = attrs.optJSONObject("resources")
            Server(
                identifier = attrs.optString("identifier"),
                name = attrs.optString("name", "Minecraft server"),
                state = res?.optString("current_state", "unknown") ?: "unknown",
                memoryMb = (res?.optLong("memory_bytes", 0L) ?: 0L) / (1024L * 1024L),
                diskMb = (res?.optLong("disk_bytes", 0L) ?: 0L) / (1024L * 1024L),
                cpuPercent = res?.optDouble("cpu_absolute", 0.0) ?: 0.0
            )
        }.filter { it.identifier.isNotBlank() }
    }

    fun power(baseUrl: String, token: String, id: String, signal: String) {
        require(signal in setOf("start", "stop", "restart"))
        request(baseUrl, token, "/servers/${URLEncoder.encode(id, "UTF-8")}/power", "POST",
            JSONObject().put("signal", signal).toString())
    }

    fun command(baseUrl: String, token: String, id: String, command: String) {
        require(command.isNotBlank()) { "Enter a console command." }
        request(baseUrl, token, "/servers/${URLEncoder.encode(id, "UTF-8")}/command", "POST",
            JSONObject().put("command", command).toString())
    }
}
