
package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cv.*
import com.example.data.db.*
import com.example.data.preferences.AppPreferences
import com.example.data.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val preferences = AppPreferences(application)

    val repository = AppRepository(
        db,
        preferences,
        context = application
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            AppDatabase.ensureSamplesUpdated(application, db)
        }
    }

    // --------------------------------------------------
    // UI STATES
    // --------------------------------------------------

    val language = repository.languageFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "fa"
    )

    val theme = repository.themeFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "DARK"
    )

    val normalization = repository.normalizationFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "STANDARD_SCALER"
    )

    val similarityThreshold = repository.similarityThresholdFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        65.0
    )

    val isDeveloperMode = repository.developerModeFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    val securityPin = repository.securityPinFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ""
    )

    val isBiometricEnabled = repository.biometricEnabledFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    val isScreenshotProtected = repository.screenshotProtectedFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    // --------------------------------------------------
    // SUBSCRIPTION STATES
    // --------------------------------------------------

    val subActivated = repository.subActivated.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    val subStartTime = repository.subStartTime.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0L
    )

    val subExpirationTime = repository.subExpirationTime.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0L
    )

    val subDurationDays = repository.subDurationDays.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        30
    )

    // --------------------------------------------------
    // DATABASE STATES
    // --------------------------------------------------

    val references = repository.allReferences.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val history = repository.allHistory.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val favorites = repository.favoriteHistory.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val calibrationProfiles = repository.calibrationProfiles.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // --------------------------------------------------
    // CURRENT IMAGE STATE
    // --------------------------------------------------

    private val _currentBitmap = MutableStateFlow<Bitmap?>(null)

    val currentBitmap: StateFlow<Bitmap?> =
        _currentBitmap.asStateFlow()

    // --------------------------------------------------
    // CLASSIFICATION RESULT
    // --------------------------------------------------

    private val _classificationResult =
        MutableStateFlow<ClassificationResult?>(null)

    val classificationResult: StateFlow<ClassificationResult?> =
        _classificationResult.asStateFlow()

    // --------------------------------------------------
    // PIPELINE OUTPUT
    // PipelineOutput is a top-level data class in com.example.cv
    // --------------------------------------------------

    private val _pipelineOutput =
        MutableStateFlow<PipelineOutput?>(null)

    val pipelineOutput: StateFlow<PipelineOutput?> =
        _pipelineOutput.asStateFlow()

    // --------------------------------------------------
    // ANALYSIS STATUS
    // --------------------------------------------------

    private val _isAnalyzing = MutableStateFlow(false)

    val isAnalyzing: StateFlow<Boolean> =
        _isAnalyzing.asStateFlow()

    private val _analysisErrorMessage =
        MutableStateFlow<String?>(null)

    val analysisErrorMessage: StateFlow<String?> =
        _analysisErrorMessage.asStateFlow()

    // --------------------------------------------------
    // BATCH ANALYSIS
    // --------------------------------------------------

    private val _batchResults =
        MutableStateFlow<
            List<Pair<ClassificationResult, PipelineOutput>>
        >(emptyList())

    val batchResults: StateFlow<
        List<Pair<ClassificationResult, PipelineOutput>>
    > = _batchResults.asStateFlow()

    // --------------------------------------------------
    // CAPTURE IMAGE
    // --------------------------------------------------

    fun setCapturedBitmap(bitmap: Bitmap?) {
        _currentBitmap.value = bitmap
        _analysisErrorMessage.value = null
    }

    // --------------------------------------------------
    // ANALYZE CURRENT IMAGE
    // --------------------------------------------------

    fun analyzeCurrentBitmap() {

        val bitmap = _currentBitmap.value ?: return

        viewModelScope.launch {

            _isAnalyzing.value = true
            _analysisErrorMessage.value = null

            try {

                val (classification, output) =
                    repository.analyzeImage(bitmap)

                if (!output.qualityResult.isPassed) {
                    _analysisErrorMessage.value =
                        output.qualityResult.rejectionReason
                }

                _classificationResult.value = classification
                _pipelineOutput.value = output

                _batchResults.value =
                    _batchResults.value + Pair(classification, output)

            } catch (e: Exception) {

                _analysisErrorMessage.value =
                    "خطا در پردازش تصویر: ${e.localizedMessage}"

            } finally {

                _isAnalyzing.value = false
            }
        }
    }

    // --------------------------------------------------
    // CLEAR BATCH RESULTS
    // --------------------------------------------------

    fun clearBatch() {
        _batchResults.value = emptyList()
    }

    // --------------------------------------------------
    // UPDATE REFERENCE SAMPLE
    // --------------------------------------------------

    fun updateReferenceSample(
        id: Int,
        name: String,
        icumsa: Double,
        description: String,
        newBitmap: Bitmap?
    ) {

        viewModelScope.launch {

            if (
                !icumsa.isFinite() ||
                icumsa <= 0.0 ||
                icumsa > Int.MAX_VALUE.toDouble() ||
                icumsa % 1.0 != 0.0
            ) {

                _analysisErrorMessage.value =
                    "مقدار ICUMSA باید عدد صحیح و بزرگ‌تر از صفر باشد."

                return@launch
            }

            try {

                repository.updateReferenceSample(
                    id = id,
                    name = name,
                    icumsa = icumsa.toInt(),
                    description = description,
                    newBitmap = newBitmap
                )

                _analysisErrorMessage.value = null

            } catch (e: Exception) {

                _analysisErrorMessage.value =
                    "خطا در به‌روزرسانی نمونه مرجع: ${e.localizedMessage}"
            }
        }
    }

    // --------------------------------------------------
    // HISTORY MANAGEMENT
    // --------------------------------------------------

    fun toggleFavorite(id: Long, isFav: Boolean) {
        viewModelScope.launch {
            repository.toggleFavoriteHistory(id, isFav)
        }
    }

    fun deleteHistoryRecord(id: Long) {
        viewModelScope.launch {
            repository.deleteHistoryRecord(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
        }
    }

    // --------------------------------------------------
    // CALIBRATION
    // --------------------------------------------------

    fun saveCalibration(profile: CalibrationProfileEntity) {
        viewModelScope.launch {
            repository.saveCalibrationProfile(profile)
        }
    }

    // --------------------------------------------------
    // SETTINGS
    // --------------------------------------------------

    fun setLanguage(lang: String) =
        viewModelScope.launch {
            repository.setLanguage(lang)
        }

    fun setTheme(theme: String) =
        viewModelScope.launch {
            repository.setTheme(theme)
        }

    fun setNormalization(method: String) =
        viewModelScope.launch {
            repository.setNormalization(method)
        }

    fun setSimilarityThreshold(threshold: Double) =
        viewModelScope.launch {
            repository.setSimilarityThreshold(threshold)
        }

    fun setDeveloperMode(enabled: Boolean) =
        viewModelScope.launch {
            repository.setDeveloperMode(enabled)
        }

    fun setSecurityPin(pin: String) =
        viewModelScope.launch {
            repository.setSecurityPin(pin)
        }

    fun setBiometricEnabled(enabled: Boolean) =
        viewModelScope.launch {
            repository.setBiometricEnabled(enabled)
        }

    fun setScreenshotProtected(protected: Boolean) =
        viewModelScope.launch {
            repository.setScreenshotProtected(protected)
        }

    // --------------------------------------------------
    // SUBSCRIPTION PASSWORD
    // --------------------------------------------------

    // Subscription password verification is disabled
    // until secure credential validation is implemented.

    fun verifySubscriptionPasswords(
        pass1: String,
        pass2: String
    ): Boolean {
        return false
    }

    // --------------------------------------------------
    // ACTIVATE SUBSCRIPTION
    // --------------------------------------------------

    fun activateSubscription(days: Int) =
        viewModelScope.launch {

            if (days <= 0) {
                return@launch
            }

            repository.activateSubscription(days)
        }

    // --------------------------------------------------
    // EXTEND SUBSCRIPTION
    // --------------------------------------------------

    fun extendSubscription(days: Int) =
        viewModelScope.launch {

            if (days <= 0) {
                return@launch
            }

            repository.extendSubscription(
                days,
                subExpirationTime.value,
                subDurationDays.value
            )
        }

    // --------------------------------------------------
    // CHECK SUBSCRIPTION VALIDITY
    // --------------------------------------------------

    fun isSubscriptionValid(): Boolean {

        return subActivated.value &&
            System.currentTimeMillis() < subExpirationTime.value
    }

    // --------------------------------------------------
    // ADMIN PIN VERIFICATION
    // --------------------------------------------------

    fun verifyAdminPin(enteredPin: String): Boolean {

        return enteredPin.isNotBlank() &&
            enteredPin == securityPin.value
    }
}
