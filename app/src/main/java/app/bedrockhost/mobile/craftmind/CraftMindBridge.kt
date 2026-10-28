package app.bedrockhost.mobile.craftmind

const val CRAFTMIND_BRIDGE_PROTOCOL = "craftmind.bridge.v1"
const val CRAFTMIND_MAX_REQUEST_ID_CHARS = 128
const val CRAFTMIND_MAX_ARGUMENT_CHARS = 8_000
const val CRAFTMIND_MAX_OUTPUT_CHARS = 16_000

enum class CraftMindToolStatus { SUCCESS, REJECTED, NEEDS_CONFIRMATION, UNSUPPORTED, FAILED, TIMEOUT }

data class CraftMindToolRequest(
    val protocol: String = CRAFTMIND_BRIDGE_PROTOCOL,
    val requestId: String,
    val tool: String,
    val arguments: Map<String, String> = emptyMap()
)

data class CraftMindToolResult(
    val protocol: String = CRAFTMIND_BRIDGE_PROTOCOL,
    val requestId: String,
    val tool: String,
    val status: CraftMindToolStatus,
    val message: String,
    val verified: Boolean = false,
    val data: Map<String, String> = emptyMap(),
    val errorCode: String? = null
)

interface CraftMindDataSource {
    fun serverStatus(requestId: String): CraftMindToolResult
    fun listWorlds(requestId: String): CraftMindToolResult
    fun getWorld(requestId: String, worldId: String): CraftMindToolResult
    fun recentLogs(requestId: String, maxLines: Int): CraftMindToolResult
    fun listPlayers(requestId: String): CraftMindToolResult
}

/** Read-only dispatcher. No command execution or world/server mutations are exposed. */
class CraftMindReadOnlyBridge(private val dataSource: CraftMindDataSource) {
    fun execute(request: CraftMindToolRequest): CraftMindToolResult {
        if (request.protocol != CRAFTMIND_BRIDGE_PROTOCOL) return rejected(request, "UNSUPPORTED_PROTOCOL", "Unsupported CraftMind bridge protocol.")
        if (request.requestId.isBlank() || request.requestId.length > CRAFTMIND_MAX_REQUEST_ID_CHARS) return rejected(request, "INVALID_REQUEST_ID", "Request ID is missing or too long.")
        if (request.arguments.values.any { it.length > CRAFTMIND_MAX_ARGUMENT_CHARS }) return rejected(request, "ARGUMENT_TOO_LARGE", "A tool argument exceeds the allowed size.")
        return try {
            when (request.tool) {
                "server.status" -> noArguments(request) { dataSource.serverStatus(request.requestId) }
                "world.list" -> noArguments(request) { dataSource.listWorlds(request.requestId) }
                "world.get" -> {
                    if (request.arguments.keys != setOf("worldId")) rejected(request, "INVALID_ARGUMENTS", "world.get requires only a worldId argument.")
                    else {
                        val id = request.arguments["worldId"].orEmpty()
                        if (!PROFILE_ID.matches(id)) rejected(request, "INVALID_WORLD_ID", "World ID is invalid.")
                        else dataSource.getWorld(request.requestId, id)
                    }
                }
                "server.logs.explain" -> {
                    if (request.arguments.keys != setOf("maxLines")) rejected(request, "INVALID_ARGUMENTS", "server.logs.explain requires maxLines from 1 to 100.")
                    else {
                        val count = request.arguments["maxLines"]?.toIntOrNull()
                        if (count == null || count !in 1..100) rejected(request, "INVALID_ARGUMENTS", "maxLines must be between 1 and 100.")
                        else dataSource.recentLogs(request.requestId, count)
                    }
                }
                "players.list" -> noArguments(request) { dataSource.listPlayers(request.requestId) }
                else -> rejected(request, "TOOL_NOT_ALLOWED", "This tool is not enabled in the read-only bridge.")
            }
        } catch (_: Exception) {
            CraftMindToolResult(requestId = request.requestId, tool = request.tool, status = CraftMindToolStatus.FAILED, message = "CraftNode could not read this information. Please try again.", errorCode = "DATA_SOURCE_FAILURE")
        }.let { result ->
            if (result.data.values.any { it.length > CRAFTMIND_MAX_OUTPUT_CHARS }) result.copy(
                status = CraftMindToolStatus.FAILED,
                message = "The requested data exceeded the response size limit.",
                verified = false,
                data = emptyMap(),
                errorCode = "OUTPUT_TOO_LARGE"
            ) else result
        }
    }

    private fun noArguments(request: CraftMindToolRequest, action: () -> CraftMindToolResult): CraftMindToolResult =
        if (request.arguments.isNotEmpty()) rejected(request, "INVALID_ARGUMENTS", "This tool does not accept arguments.") else action()

    private fun rejected(request: CraftMindToolRequest, code: String, message: String) = CraftMindToolResult(
        requestId = request.requestId, tool = request.tool, status = CraftMindToolStatus.REJECTED, message = message, errorCode = code
    )

    companion object { private val PROFILE_ID = Regex("[A-Za-z0-9_-]{1,64}") }
}
