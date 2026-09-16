package com.slideindex.app.privilege.root



import android.os.Looper

import android.os.Process

import android.util.Log

import com.slideindex.app.shizuku.TaskManagerUserService

import kotlin.system.exitProcess



/** Shell [app_process] entry: [TaskManagerUserService] + loopback RPC (+ ServiceManager when allowed). */

object TaskManagerRootDaemonMain {

    private const val TAG = "RootTaskDaemon"



    @JvmStatic

    fun main(args: Array<String>) {

        if (args.isEmpty()) {

            Log.e(TAG, "usage: <packageName>")

            exitProcess(1)

        }

        val packageName = args[0]

        val serviceName = RootBinderPublish.serviceName(packageName)

        val portFile = RootTaskManagerServiceHost.portFilePath(packageName)

        val uid = Process.myUid()

        Log.i(TAG, "starting uid=$uid package=$packageName")

        if (uid != 0) {

            Log.e(TAG, "refusing non-root daemon (expected uid=0)")

            exitProcess(3)

        }

        Looper.prepareMainLooper()

        val context = RootDaemonPackageContext.create(packageName)

        if (context == null) {

            Log.e(TAG, "no context")

            exitProcess(2)

        }

        val service = TaskManagerUserService(context)

        RootBinderPublish.publish(serviceName, service.asBinder())

        if (TaskManagerRpcServer.start(portFile, service) == null) {

            Log.e(TAG, "rpc server failed")

            runCatching { service.destroy() }

            exitProcess(4)

        }

        Log.i(TAG, "ready service=$serviceName portFile=$portFile")

        Looper.loop()

    }

}


