
package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.cv.*
import com.example.data.db.*
import com.example.data.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppRepository(
    private val db: AppDatabase,
    private val preferences: AppPreferences,
    private val context: Context? = null,
    private val pipeline: OpenCvPipeline = OpenCvPipeline()
) {

    // --------------------------------------------------
    // DATABASE FLOWS
    // --------------------------------------------------

    val allReferences: Flow<List<ReferenceSampleEntity>> =
        db.referenceSampleDao().getAllSamples()

    val allHistory: Flow<List<AnalysisHistoryEntity>> =
        db.analysisHistoryDao().getAllHistory()

    val favoriteHistory: Flow<List<AnalysisHistoryEntity>> =
        db.analysisHistoryDao().getFavorites()

    val calibrationProfiles: Flow<List<CalibrationProfileEntity>> =
        db.calibrationProfileDao().getAllProfiles()

    // --------------------------------------------------
    // PREFERENCE FLOWS
    // --------------------------------------------------

    val languageFlow = preferences.languageFlow
    val themeFlow = preferences.themeFlow
    val normalizationFlow = preferences.normalizationFlow
    val similarityThresholdFlow = preferences.similarityThresholdFlow
    val developerModeFlow = preferences.developerModeFlow
    val securityPinFlow = preferences.securityPinFlow
    val biometricEnabledFlow = preferences.biometricEnabledFlow
    val screenshotProtectedFlow = preferences.screenshotProtectedFlow

    // --------------------------------------------------
    // ANALYZE IMAGE
    // --------------------------------------------------

    suspend fun analyzeImage(
        bitmap: Bitmap,
        batchId: String? = null
    ): Pair<ClassificationResult, PipelineOutput> =
        withContext(Dispatchers.Default) {

            val references =
                db.referenceSampleDao().getAllSamplesDirect()

            val normMethodStr =
                preferences.normalizationFlow.first()

            val threshold =
                preferences.similarityThresholdFlow.first()

            val normMethod =
                if (normMethodStr == "MIN_MAX_SCALER") {
                    NormalizationMethod.MIN_MAX_SCALER
                } else {
                    NormalizationMethod.STANDARD_SCALER
                }

            val featureScaler = FeatureScaler()

            // Only accept valid vectors matching the active 63-dimensional schema.
            val storedRefs = references.mapNotNull { reference ->

                try {
                    val parsed =
                        VectorFormat.parse(reference.featureVectorJson)

                    if (parsed == null) {
                        Log.w(
                            "AppRepository",
                            "Invalid vector for reference #${reference.id}"
                        )
                        return@mapNotNull null
                    }

                    if (
                        parsed.vector.size !=
                        FeatureSchema.FEATURE_VECTOR_SIZE
                    ) {
                        Log.w(
                            "AppRepository",
                            "Skipping reference #${reference.id}: " +
                                "expected ${FeatureSchema.FEATURE_VECTOR_SIZE}, " +
                                "found ${parsed.vector.size}"
                        )
                        return@mapNotNull null
                    }

                    if (parsed.vector.any { !it.isFinite() }) {
                        Log.w(
                            "AppRepository",
                            "Non-finite feature vector for reference #${reference.id}"
                        )
                        return@mapNotNull null
                    }

                    val normalizedVector =
                        featureScaler.scale(parsed.vector, normMethod)

                    StoredReferenceVector(
                        sampleId = reference.id,
                        sampleName = reference.name,
                        icumsaValue = reference.icumsaValue,
                        vector = normalizedVector
                    )

                } catch (e: Exception) {

                    Log.e(
                        "AppRepository",
                        "Failed to load reference #${reference.id}",
                        e
                    )

                    null
                }
            }

            // Execute the current image-processing pipeline.
            val (classification, output) = pipeline.processImage(
                originalBitmap = bitmap,
                referenceSamples = storedRefs,
                normalizationMethod = normMethod,
                similarityThresholdPct = threshold
            )

            // Save a copy of the processed image for history.
            val now = Date()

            val dateFmt =
                SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    .format(now)

            val timeFmt =
                SimpleDateFormat("HH:mm:ss", Locale.US)
                    .format(now)

            val matchedReference =
                references.firstOrNull {
                    it.id == classification.nearestSampleId
                }

            var savedImagePath: String? = null

            try {
                val appContext = context

                if (appContext != null) {

                    val directory = File(
                        appContext.filesDir,
                        "analysis_history"
                    )

                    if (!directory.exists()) {
                        directory.mkdirs()
                    }

                    val imageFile = File(
                        directory,
                        "analysis_${now.time}.jpg"
                    )

                    FileOutputStream(imageFile).use { stream ->
                        output.processedBitmap.compress(
                            Bitmap.CompressFormat.JPEG,
                            90,
                            stream
                        )
                    }

                    savedImagePath = imageFile.absolutePath
                }

            } catch (e: Exception) {

                Log.e(
                    "AppRepository",
                    "Could not save analysis image",
                    e
                )
            }

            // Store the raw extracted feature vector, not the normalized
            // classification vector. This keeps history data interpretable.
            val rawFeatureVector =
                output.completeFeatureSet.concatenatedVector

            val labMeans =
                output.completeFeatureSet.labMeans

            val historyEntity = AnalysisHistoryEntity(
                timestamp = now.time,
                dateFormatted = dateFmt,
                timeFormatted = timeFmt,
                imagePath = savedImagePath,

                matchedSampleId = classification.nearestSampleId,
                matchedSampleName = classification.nearestSampleName,

                confidenceScorePct = classification.confidenceScorePct,
                confidenceLevel = classification.confidenceLevel.name,
                isUnknown = classification.isUnknown,

                euclideanDistance =
                    classification.topMatch.euclideanDistance,

                // The current classifier does not provide a separate
                // Manhattan-distance value. Zero means unavailable;
                // it must not be interpreted as a measured distance.
                manhattanDistance = 0.0,

                cosineSimilarity =
                    classification.topMatch.cosineSimilarity,

                featureVectorJson = VectorFormat.serialize(
                    vector = rawFeatureVector,
                    lab = labMeans,
                    schema = FeatureSchema.SCHEMA_VERSION
                ),

                qualityScore = output.qualityResult.overallScore,
                processingTimeMs = classification.processingTimeMs,

                batchId = batchId,
                normalizationMethod = normMethod.name,
                algorithmVersion = "v3.0.0",

                matchedImagePath = matchedReference?.imagePath
            )

            db.analysisHistoryDao().insert(historyEntity)

            Pair(classification, output)
        }

    // --------------------------------------------------
    // UPDATE REFERENCE SAMPLE
    // --------------------------------------------------

    suspend fun updateReferenceSample(
        id: Int,
        name: String,
        icumsa: Int,
        description: String,
        newBitmap: Bitmap?,
        operatorName: String = "Administrator",
        sourceType: String = "USER_IMAGE"
    ) = withContext(Dispatchers.IO) {

        require(name.isNotBlank()) {
            "Reference name cannot be blank"
        }

        require(icumsa > 0) {
            "Reference ICUMSA label must be positive"
        }

        val sampleDao = db.referenceSampleDao()
        val versionDao = db.referenceVersionDao()

        val existing = sampleDao.getSampleById(id)
            ?: throw IllegalArgumentException(
                "Reference sample #$id was not found"
            )

        val now = System.currentTimeMillis()
        val newVersion = existing.version + 1

        var newVectorJson = existing.featureVectorJson
        var newVectorSize = existing.featureVectorSize
        var savedImagePath = existing.imagePath

        if (newBitmap != null) {

            val (_, output) = pipeline.processImage(
                originalBitmap = newBitmap,
                referenceSamples = emptyList()
            )

            try {

                if (!output.qualityResult.isPassed) {
                    throw IllegalArgumentException(
                        "Reference update rejected: " +
                            (output.qualityResult.rejectionReason
                                ?: "Image quality validation failed")
                    )
                }

                val rawVector =
                    output.completeFeatureSet.concatenatedVector

                require(
                    rawVector.size == FeatureSchema.FEATURE_VECTOR_SIZE
                ) {
                    "Extracted vector has ${rawVector.size} dimensions; " +
                        "expected ${FeatureSchema.FEATURE_VECTOR_SIZE}"
                }

                require(rawVector.all { it.isFinite() }) {
                    "Extracted reference vector contains invalid values"
                }

                newVectorJson = VectorFormat.serialize(
                    vector = rawVector,
                    lab = output.completeFeatureSet.labMeans,
                    schema = FeatureSchema.SCHEMA_VERSION
                )

                newVectorSize = rawVector.size

            } finally {

                // Release temporary processing-stage bitmaps.
                output.stages.forEach { stage ->
                    if (!stage.bitmap.isRecycled) {
                        stage.bitmap.recycle()
                    }
                }
            }

            // Save the new original reference image.
            val appContext = context
                ?: throw IllegalStateException(
                    "Application context is required to save a reference image"
                )

            val directory = File(
                appContext.filesDir,
                "reference_samples"
            )

            if (!directory.exists() && !directory.mkdirs()) {
                throw IllegalStateException(
                    "Cannot create reference image directory"
                )
            }

            val imageFile = File(
                directory,
                "sample_${id}_${now}.jpg"
            )

            FileOutputStream(imageFile).use { stream ->
                val saved = newBitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    92,
                    stream
                )

                if (!saved) {
                    throw IllegalStateException(
                        "Failed to compress reference image"
                    )
                }
            }

            savedImagePath = imageFile.absolutePath
        }

        // Copy preserves fields that are not being changed,
        // including any existing LAB metadata.
        val updatedEntity = existing.copy(
            name = name,
            icumsaValue = icumsa,
            description = description,
            featureVectorJson = newVectorJson,
            imagePath = savedImagePath,
            version = newVersion,
            lastModifiedTimestamp = now,
            lastModifiedBy = operatorName,
            featureVectorSize = newVectorSize
        )

        sampleDao.insertOrUpdate(updatedEntity)

        // Keep reference version history.
        versionDao.insertVersion(
            ReferenceVersionEntity(
                sampleId = id,
                versionNumber = newVersion,
                name = name,
                icumsaValue = icumsa,
                description = description,
                featureVectorJson = newVectorJson,
                imagePath = savedImagePath,
                timestamp = now,
                operatorName = operatorName,
                changeDescription =
                    if (newBitmap != null) {
                        "Reference image and 63-dimensional features updated. Source: $sourceType"
                    } else {
                        "Reference metadata updated. Existing image and features preserved."
                    }
            )
        )
    }

    // --------------------------------------------------
    // REFERENCE VERSION HISTORY
    // --------------------------------------------------

    suspend fun getReferenceVersions(
        sampleId: Int
    ) = db.referenceVersionDao().getVersionsForSample(sampleId)

    // --------------------------------------------------
    // HISTORY MANAGEMENT
    // --------------------------------------------------

    suspend fun toggleFavoriteHistory(
        id: Long,
        isFavorite: Boolean
    ) {
        db.analysisHistoryDao().setFavorite(id, isFavorite)
    }

    suspend fun deleteHistoryRecord(id: Long) {
        db.analysisHistoryDao().deleteById(id)
    }

    suspend fun clearAllHistory() {
        db.analysisHistoryDao().deleteAll()
    }

    // --------------------------------------------------
    // CALIBRATION
    // --------------------------------------------------

    suspend fun saveCalibrationProfile(
        profile: CalibrationProfileEntity
    ) {
        db.calibrationProfileDao().insert(profile)
    }

    // --------------------------------------------------
    // SUBSCRIPTION FLOWS
    // --------------------------------------------------

    val subActivated: Flow<Boolean> =
        preferences.subActivatedFlow

    val subStartTime: Flow<Long> =
        preferences.subStartTimeFlow

    val subExpirationTime: Flow<Long> =
        preferences.subExpirationTimeFlow

    val subDurationDays: Flow<Int> =
        preferences.subDurationDaysFlow

    // --------------------------------------------------
    // ACTIVATE SUBSCRIPTION
    // --------------------------------------------------

    suspend fun activateSubscription(durationDays: Int) {

        require(durationDays > 0) {
            "Subscription duration must be positive"
        }

        val startTime = System.currentTimeMillis()

        val expirationTime =
            startTime + durationDays.toLong() * 24L * 3600L * 1000L

        preferences.setSubscriptionData(
            activated = true,
            startTime = startTime,
            expirationTime = expirationTime,
            durationDays = durationDays
        )
    }

    // --------------------------------------------------
    // EXTEND SUBSCRIPTION
    // --------------------------------------------------

    suspend fun extendSubscription(
        additionalDays: Int,
        currentExpTime: Long,
        currentDuration: Int
    ) {

        require(additionalDays > 0) {
            "Additional subscription days must be positive"
        }

        val now = System.currentTimeMillis()

        val baseTime =
            if (currentExpTime > now) currentExpTime else now

        val expirationTime =
            baseTime + additionalDays.toLong() * 24L * 3600L * 1000L

        preferences.setSubscriptionData(
            activated = true,
            startTime = now,
            expirationTime = expirationTime,
            durationDays = currentDuration + additionalDays
        )
    }

    // --------------------------------------------------
    // PREFERENCE SETTERS
    // --------------------------------------------------

    suspend fun setLanguage(lang: String) =
        preferences.setLanguage(lang)

    suspend fun setTheme(theme: String) =
        preferences.setTheme(theme)

    suspend fun setNormalization(method: String) =
        preferences.setNormalization(method)

    suspend fun setSimilarityThreshold(threshold: Double) =
        preferences.setSimilarityThreshold(threshold)

    suspend fun setDeveloperMode(enabled: Boolean) =
        preferences.setDeveloperMode(enabled)

    suspend fun setSecurityPin(pin: String) =
        preferences.setSecurityPin(pin)

    suspend fun setBiometricEnabled(enabled: Boolean) =
        preferences.setBiometricEnabled(enabled)

    suspend fun setScreenshotProtected(protected: Boolean) =
        preferences.setScreenshotProtected(protected)

    // --------------------------------------------------
    // VECTOR JSON PARSER
    // --------------------------------------------------

    fun parseVectorJson(json: String?): DoubleArray {

        if (json.isNullOrBlank()) {
            throw IllegalArgumentException(
                "Feature vector JSON string is empty"
            )
        }

        val parsed = VectorFormat.parse(json)
            ?: throw IllegalArgumentException(
                "Invalid or corrupted feature vector"
            )

        return parsed.vector
    }
}
