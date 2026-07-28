package com.slideindex.app.translate

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.slideindex.app.nativeengine.NativeEnginePackCoordinator
import com.slideindex.app.nativeengine.NativeEnginePackIds
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class MlKitTranslateEngine @Inject constructor(
    private val modelInstaller: MlKitTranslateModelInstaller,
    private val nativeEnginePackCoordinator: NativeEnginePackCoordinator,
) {
    private val languageIdentifier: LanguageIdentifier by lazy {
        LanguageIdentification.getClient()
    }

    suspend fun translate(text: String, targetLang: String): TranslateResult = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext TranslateResult.Failure("empty_text")
        if (!nativeEnginePackCoordinator.ensurePackReady(NativeEnginePackIds.TRANSLATE)) {
            return@withContext TranslateResult.Failure("translate_engine_not_installed")
        }
        val targetMlKit = modelInstaller.toMlKitLanguage(targetLang)
            ?: return@withContext TranslateResult.Failure("unsupported_target_language")

        val sourceMlKit = detectSourceLanguage(text)
            ?: return@withContext TranslateResult.Failure("language_detect_failed")

        if (sourceMlKit == targetMlKit) {
            return@withContext TranslateResult.Success(
                translatedText = text,
                detectedSourceLanguage = sourceMlKit,
            )
        }

        if (!modelInstaller.isModelDownloaded(targetLang)) {
            return@withContext TranslateResult.Failure("target_model_not_installed")
        }
        val sourceLangCode = mlKitToCatalogCode(sourceMlKit)
        if (sourceLangCode != null && !modelInstaller.isModelDownloaded(sourceLangCode)) {
            return@withContext TranslateResult.Failure("source_model_not_installed")
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceMlKit)
            .setTargetLanguage(targetMlKit)
            .build()
        val translator = Translation.getClient(options)
        try {
            val downloaded = awaitModelReady(translator)
            if (!downloaded) {
                return@withContext TranslateResult.Failure("model_download_required")
            }
            val translated = awaitTranslate(translator, text)
            TranslateResult.Success(
                translatedText = translated,
                detectedSourceLanguage = sourceMlKit,
            )
        } catch (error: Exception) {
            TranslateResult.Failure(error.message ?: "mlkit_translate_failed")
        } finally {
            translator.close()
        }
    }

    private suspend fun detectSourceLanguage(text: String): String? = suspendCancellableCoroutine { continuation ->
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { languageCode ->
                val mapped = when {
                    languageCode == "und" -> guessLanguageFromText(text)
                    else -> languageCode
                }
                if (continuation.isActive) {
                    continuation.resume(mapped)
                }
            }
            .addOnFailureListener {
                if (continuation.isActive) {
                    continuation.resume(guessLanguageFromText(text))
                }
            }
    }

    private fun guessLanguageFromText(text: String): String? {
        val hasCjk = text.any { char ->
            Character.UnicodeBlock.of(char) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                Character.UnicodeBlock.of(char) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
        }
        return if (hasCjk) {
            com.google.mlkit.nl.translate.TranslateLanguage.CHINESE
        } else {
            com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH
        }
    }

    private suspend fun awaitModelReady(translator: Translator): Boolean =
        suspendCancellableCoroutine { continuation ->
            translator.downloadModelIfNeeded()
                .addOnSuccessListener {
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                }
                .addOnFailureListener {
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
        }

    private suspend fun awaitTranslate(translator: Translator, text: String): String =
        suspendCancellableCoroutine { continuation ->
            translator.translate(text)
                .addOnSuccessListener { translated ->
                    if (continuation.isActive) {
                        continuation.resume(translated)
                    }
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.failure(error))
                    }
                }
        }

    private fun mlKitToCatalogCode(mlKitCode: String): String? =
        TranslateLanguageCatalog.options.firstOrNull { option ->
            modelInstaller.toMlKitLanguage(option.code) == mlKitCode
        }?.code
}
