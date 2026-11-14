package com.example.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Local loopback HTTP bridge exposing Obscura's CDP `/json/version` discovery
 * and MCP `/mcp` JSON-RPC 2.0 endpoints on 127.0.0.1.
 */
class LocalMcpBridge(
    private val scope: CoroutineScope,
    private val onNavigate: suspend (String) -> String,
    private val onSnapshot: suspend () -> String,
    private val onEvaluate: suspend (String) -> String,
    private val onGetNetworkLogs: () -> String,
    private val onGetConsoleLogs: () -> String,
    private val onStateChange: (running: Boolean, port: Int, requestCount: Int, lastCall: String?, error: String?) -> Unit,
) {
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var requestCount = 0
    private val json = Json { ignoreUnknownKeys = true }

    fun start(port: Int) {
        stop()
        try {
            val loopback = InetAddress.getByName("127.0.0.1")
            val socket = ServerSocket(port, 16, loopback)
            serverSocket = socket
            requestCount = 0
            onStateChange(true, socket.localPort, 0, "Listening on 127.0.0.1:${socket.localPort}", null)

            acceptJob = scope.launch(Dispatchers.IO) {
                while (isActive && !socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    launch { handleClient(client, socket.localPort) }
                }
            }
        } catch (e: Exception) {
            onStateChange(false, port, requestCount, null, e.message ?: "Failed to bind port $port")
        }
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        onStateChange(false, 9222, requestCount, null, null)
    }

    private suspend fun handleClient(socket: Socket, port: Int) {
        socket.use { client ->
            client.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(' ')
            val method = parts.getOrNull(0)?.uppercase() ?: "GET"
            val path = parts.getOrNull(1) ?: "/"

            var contentLength = 0
            while (true) {
                val header = reader.readLine() ?: break
                if (header.isEmpty()) break
                if (header.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = header.substringAfter(':').trim().toIntOrNull() ?: 0
                }
            }

            val body = if (contentLength > 0 && contentLength < 1024 * 1024) {
                val chars = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = reader.read(chars, read, contentLength - read)
                    if (n <= 0) break
                    read += n
                }
                String(chars, 0, read)
            } else {
                ""
            }

            requestCount += 1
            val responseJson = when {
                method == "GET" && path.startsWith("/json/version") -> {
                    onStateChange(true, port, requestCount, "GET /json/version", null)
                    buildJsonObject {
                        put("Browser", "Obscura-Android/1.0")
                        put("Protocol-Version", "1.3")
                        put("User-Agent", "Obscura/1.0")
                        put("webSocketDebuggerUrl", "ws://127.0.0.1:$port/devtools/browser")
                    }.toString()
                }
                method == "GET" && path.startsWith("/health") -> {
                    onStateChange(true, port, requestCount, "GET /health", null)
                    """{"status":"ok","engine":"obscura-android"}"""
                }
                method == "POST" && path.startsWith("/mcp") -> {
                    handleMcpRpc(body, port)
                }
                else -> {
                    onStateChange(true, port, requestCount, "$method $path", null)
                    buildJsonObject {
                        put("name", "Obscura Local Bridge")
                        put("endpoints", buildJsonArray {
                            add(JsonPrimitive("GET /json/version"))
                            add(JsonPrimitive("POST /mcp"))
                            add(JsonPrimitive("GET /health"))
                        })
                    }.toString()
                }
            }

            val bodyBytes = responseJson.toByteArray(Charsets.UTF_8)
            val httpResponse = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: application/json; charset=utf-8\r\n")
                append("Content-Length: ${bodyBytes.size}\r\n")
                append("Connection: close\r\n\r\n")
            }
            val out = client.getOutputStream()
            out.write(httpResponse.toByteArray(Charsets.UTF_8))
            out.write(bodyBytes)
            out.flush()
        }
    }

    private suspend fun handleMcpRpc(rawBody: String, port: Int): String {
        val msg = runCatching { json.parseToJsonElement(rawBody).jsonObject }.getOrNull()
            ?: return rpcError(JsonPrimitive(0), -32700, "Parse error")
        val id = msg["id"] ?: JsonPrimitive(0)
        val method = msg["method"]?.jsonPrimitive?.content.orEmpty()
        val params = msg["params"] as? JsonObject ?: buildJsonObject {}

        onStateChange(true, port, requestCount, "MCP $method", null)

        return when (method) {
            "initialize" -> rpcOk(
                id,
                buildJsonObject {
                    put("protocolVersion", "2024-11-05")
                    put("serverInfo", buildJsonObject {
                        put("name", "obscura-android")
                        put("version", "1.0.0")
                    })
                    put("capabilities", buildJsonObject {
                        put("tools", buildJsonObject {})
                    })
                },
            )
            "tools/list" -> rpcOk(
                id,
                buildJsonObject {
                    put("tools", buildJsonArray {
                        add(toolDef("browser_navigate", "Navigate active stealth browser to a URL"))
                        add(toolDef("browser_snapshot", "Return interactive element refs and readable text"))
                        add(toolDef("browser_evaluate", "Evaluate a JavaScript expression on the active page"))
                        add(toolDef("browser_network_requests", "Return intercepted network & tracker logs"))
                        add(toolDef("browser_console_messages", "Return captured page console messages"))
                    })
                },
            )
            "tools/call" -> {
                val toolName = params["name"]?.jsonPrimitive?.content.orEmpty()
                val args = params["arguments"] as? JsonObject ?: buildJsonObject {}
                val textResult = when (toolName) {
                    "browser_navigate" -> onNavigate(args["url"]?.jsonPrimitive?.content.orEmpty())
                    "browser_snapshot" -> onSnapshot()
                    "browser_evaluate" -> onEvaluate(args["expression"]?.jsonPrimitive?.content.orEmpty())
                    "browser_network_requests" -> onGetNetworkLogs()
                    "browser_console_messages" -> onGetConsoleLogs()
                    else -> "Unknown tool: $toolName"
                }
                rpcOk(
                    id,
                    buildJsonObject {
                        put("content", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", textResult)
                            })
                        })
                    },
                )
            }
            else -> rpcError(id, -32601, "Unknown method: $method")
        }
    }

    private fun toolDef(name: String, description: String) = buildJsonObject {
        put("name", name)
        put("description", description)
    }

    private fun rpcOk(id: kotlinx.serialization.json.JsonElement, result: JsonObject): String =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("result", result)
        }.toString()

    private fun rpcError(id: kotlinx.serialization.json.JsonElement, code: Int, message: String): String =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("error", buildJsonObject {
                put("code", code)
                put("message", message)
            })
        }.toString()
}
