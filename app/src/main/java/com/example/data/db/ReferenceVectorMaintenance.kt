
package com.example.data.db

import android.content.Context
import android.graphics.BitmapFactory
import com.example.cv.FeatureSchema
import com.example.cv.OpenCvPipeline
import com.example.cv.VectorFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Non-destructive compatibility maintenance for stored reference samples.
 *
 * Supports both legacy arrays and versioned VectorFormat JSON.
 * Re-extracts only missing, invalid, or dimension-incompatible vectors.
 * Preserves names, descriptions, image paths and existing metadata.
 */
object ReferenceVectorMaintenance {

    private val maintenanceMutex = Mutex()

    private val pipeline = OpenCvPipeline()

    /**
     * Checks stored reference vectors and migrates incompatible vectors
     * using their original reference images.
     */
    suspend fun ensureVectorsUpdated(
        context: Context,
        db: AppDatabase
    ) = withContext(Dispatchers.IO) {

        maintenanceMutex.withLock {

            val sampleDao = db.referenceSampleDao()
            val versionDao = db.referenceVersionDao()

            val samples = sampleDao.getAllSamplesDirect()

            for (sample in samples) {

                val parsedVector = parseVectorStrict(
                    sample.featureVectorJson
                )

                val isCurrent =
                    parsedVector != null &&
                    parsedVector.size == FeatureSchema.FEATURE_VECTOR_SIZE

                if (isCurrent) {
                    continue
                }

                val storedPath = sample.imagePath

                if (storedPath.isNullOrBlank()) {
                    continue
                }

                val originalFile = File(storedPath)

                val candidateFile = if (originalFile.exists()) {
                    originalFile
                } else {
                    File(
                        context.filesDir,
                        "reference_samples/${originalFile.name}"
                    )
                }

                if (!candidateFile.exists()) {
                    continue
                }

                try {

                    val bitmap = BitmapFactory.decodeFile(
                        candidateFile.absolutePath
                    ) ?: continue

                    try {

                        val newRawVector =
                            pipeline.extractReferenceVector(bitmap)

                        if (
                            newRawVector.size !=
                            FeatureSchema.FEATURE_VECTOR_SIZE
                        ) {
                            continue
                        }

                        if (newRawVector.any { !it.isFinite() }) {
                            continue
                        }

                        // Store using the same versioned format
                        // used by the rest of the application.
                        val newVectorJson = VectorFormat.serialize(
                            vector = newRawVector,
                            schema = FeatureSchema.SCHEMA_VERSION
                        )

                        val newVersion = sample.version + 1

                        val updatedSample = sample.copy(
                            featureVectorJson = newVectorJson,
                            featureVectorSize = newRawVector.size,
                            version = newVersion,
                            lastModifiedTimestamp = System.currentTimeMillis(),
                            lastModifiedBy = "System Migration (v3)"
                        )

                        sampleDao.insertOrUpdate(updatedSample)

                        versionDao.insertVersion(
                            ReferenceVersionEntity(
                                sampleId = sample.id,
                                versionNumber = newVersion,
                                name = sample.name,
                                icumsaValue = sample.icumsaValue,
                                description = sample.description,
                                featureVectorJson = newVectorJson,
                                imagePath = candidateFile.absolutePath,
                                timestamp = System.currentTimeMillis(),
                                operatorName = "Pipeline Migration v3",
                                changeDescription =
                                    "Feature vector re-extracted using the current 63-dimensional pipeline."
                            )
                        )

                    } finally {
                        bitmap.recycle()
                    }

                } catch (e: Exception) {

                    android.util.Log.e(
                        "ReferenceVectorMaintenance",
                        "Failed to update reference sample #${sample.id}",
                        e
                    )
                }
            }
        }
    }

    /**
     * Reads both legacy array format and versioned VectorFormat JSON.
     *
     * Returns null when the vector is invalid or does not match
     * the current feature schema.
     */
    fun parseVectorStrict(json: String?): DoubleArray? {

        if (json.isNullOrBlank()) {
            return null
        }

        return try {

            val parsed = VectorFormat.parse(json)
                ?: return null

            val vector = parsed.vector

            if (
                vector.size != FeatureSchema.FEATURE_VECTOR_SIZE
            ) {
                return null
            }

            if (vector.any { !it.isFinite() }) {
                return null
            }

            vector

        } catch (e: Exception) {

            null
        }
    }
}
