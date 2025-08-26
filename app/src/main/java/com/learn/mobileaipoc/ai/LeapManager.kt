package com.learn.mobileaipoc.ai

import ai.liquid.leap.Conversation
import ai.liquid.leap.LeapClient
import ai.liquid.leap.ModelRunner
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object LeapManager {
    private const val TAG = "LeapManager"
    private const val DEFAULT_MODEL_PATH = "/data/local/tmp/llm/model.bundle"

    // Types from Leap SDK; keep as Any to avoid hard compile dependency in scaffolding.
    private lateinit var modelRunner: ModelRunner

    suspend fun ensureModelLoaded(modelPath: String = DEFAULT_MODEL_PATH): Boolean {
        return withContext(Dispatchers.IO) {
         try {
                modelRunner = LeapClient.loadModel(modelPath)
                true
            } catch (ex: Exception) {
                ex.printStackTrace()
                false
            }



        }
    }

    suspend fun ensureModelLoadedFromFilesDir(
        context: Context,
        preferredName: String? = null
    ): Boolean {
//        val path = findModelInFilesDir(context, preferredName) ?: return false
        return ensureModelLoaded()
    }

//    fun findModelInFilesDir(context: Context, preferredName: String? = null): String? {
//        val filesDir: File = context.filesDir
//        if (preferredName != null) {
//            val preferred = File(filesDir, preferredName)
//            if (preferred.exists()) return preferred.absolutePath
//        }
//        val anyBundle = filesDir.listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".bundle") }
//        return anyBundle?.absolutePath
//    }

    fun createConversationOrNull(): Conversation? {
        val SYSTEM_PROMT = "You are Text Summariser, share text in plain english without any formatting. Share simple text that i can display directly to user "
        return try {
            modelRunner.createConversation(SYSTEM_PROMT)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create conversation: ${e.message}", e)
            null
        }
    }
}

