package com.slideindex.app.privilege.root



import android.system.ErrnoException

import android.system.Os

import android.util.Log

import com.slideindex.app.shizuku.ITaskManagerService

import java.io.DataInputStream

import java.io.DataOutputStream

import java.io.File

import java.net.InetSocketAddress

import java.net.ServerSocket

import java.net.Socket



internal object TaskManagerRpcServer {

    private const val TAG = "RootTaskRpc"



    fun start(portFilePath: String, service: ITaskManagerService): ServerSocket? {

        return runCatching {

            File(portFilePath).delete()

            val server = ServerSocket()

            server.reuseAddress = true

            server.bind(InetSocketAddress("127.0.0.1", 0))

            val port = server.localPort

            File(portFilePath).writeText(port.toString())

            chmodWorldReadable(portFilePath)

            Thread(

                {

                    Log.i(TAG, "listening on 127.0.0.1:$port file=$portFilePath")

                    while (true) {

                        val client = server.accept()

                        runCatching { handleClient(client, service) }

                            .onFailure { error -> Log.w(TAG, "rpc client: ${error.message}") }

                    }

                },

                "RootTaskRpc",

            ).apply {

                isDaemon = true

                start()

            }

            server

        }.onFailure { error ->

            Log.e(TAG, "rpc server start failed", error)

        }.getOrNull()

    }



    private fun handleClient(socket: Socket, service: ITaskManagerService) {

        socket.use { client ->

            client.tcpNoDelay = true

            val input = DataInputStream(client.getInputStream())

            val output = DataOutputStream(client.getOutputStream())

            val (op, args) = TaskManagerDaemonRpc.readRequest(input)

            val result = runCatching { TaskManagerDaemonRpc.handleRequest(service, op, args) }

                .getOrElse { RpcValue.Str(it.message ?: "rpc error") }

            TaskManagerDaemonRpc.writeValue(output, result)

        }

    }



    private fun chmodWorldReadable(path: String) {

        runCatching {

            Os.chmod(path, 438) // 0666

        }.onFailure { error ->

            if (error is ErrnoException) {

                Log.w(TAG, "chmod($path) errno=${error.errno}")

            }

        }

    }

}


