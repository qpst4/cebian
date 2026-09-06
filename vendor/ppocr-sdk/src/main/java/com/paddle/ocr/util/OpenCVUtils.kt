// Copyright (c) 2026 PaddlePaddle Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.paddle.ocr.util

import android.content.Context
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat

object OpenCVUtils {

    private const val TAG = "OpenCVUtils"
    private var initialized = false

    @Suppress("UNUSED_PARAMETER")
    fun init(context: Context): Boolean {
        if (initialized) return true
        initialized = OpenCVLoader.initLocal()
        if (!initialized) {
            // initLocal() 只从 APK jniLibs 加载；引擎包通过 System.load(path) 预加载时需探测 JNI。
            initialized = probeNativeReady()
            if (initialized) {
                Log.i(TAG, "OpenCV JNI ready via externally loaded native library")
            }
        }
        if (!initialized) {
            Log.e(TAG, "Failed to initialize OpenCV")
        }
        return initialized
    }

    private fun probeNativeReady(): Boolean {
        return try {
            Mat(1, 1, CvType.CV_8UC1).release()
            true
        } catch (t: Throwable) {
            Log.e(TAG, "OpenCV JNI probe failed", t)
            false
        }
    }
}
