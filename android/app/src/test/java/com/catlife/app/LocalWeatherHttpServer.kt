package com.catlife.app

import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.Executor
import java.util.concurrent.CopyOnWriteArrayList

/** Minimal real HTTP test fixture; no Android or JDK-internal server classes. */
internal class LocalWeatherHttpServer private constructor(private val server: ServerSocket) {
    val address: InetSocketAddress get() = server.localSocketAddress as InetSocketAddress
    lateinit var executor: Executor
    @Volatile private var handler: ((Exchange) -> Unit)? = null
    private val clients = CopyOnWriteArrayList<Socket>()

    fun createContext(path: String, callback: (Exchange) -> Unit) {
        require(path == "/forecast")
        handler = callback
    }

    fun start() {
        executor.execute {
            while (!server.isClosed) {
                val client = try { server.accept() } catch (_: Exception) { break }
                clients.add(client)
                executor.execute {
                    try {
                        client.use {
                            val reader = it.getInputStream().bufferedReader()
                            val request = reader.readLine() ?: return@use
                            while (!reader.readLine().isNullOrBlank()) { }
                            handler?.invoke(Exchange(client, URI(request.split(' ')[1])))
                        }
                    } catch (_: Exception) {
                        // A timeout/cancelled client is expected in error tests.
                    } finally { clients.remove(client) }
                }
            }
        }
    }

    fun stop(delay: Int) {
        require(delay == 0)
        server.close()
        clients.forEach { runCatching { it.close() } }
    }

    class Exchange(private val socket: Socket, val requestURI: URI) {
        val responseBody: OutputStream get() = socket.getOutputStream()
        fun sendResponseHeaders(status: Int, length: Long) {
            val headers = "HTTP/1.1 $status Test\r\nContent-Type: application/json\r\nContent-Length: $length\r\nConnection: close\r\n\r\n"
            responseBody.write(headers.toByteArray(Charsets.US_ASCII))
            responseBody.flush()
        }
        fun close() = socket.close()
    }

    companion object {
        fun create(address: InetSocketAddress, backlog: Int): LocalWeatherHttpServer =
            LocalWeatherHttpServer(ServerSocket().apply { bind(address, backlog) })
    }
}
