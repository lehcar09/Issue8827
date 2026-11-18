package com.google.dps

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.remoteConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(
  private val sharedPrefs: SharedPreferences,
  private val remoteConfig: FirebaseRemoteConfig,
) : ViewModel() {

  private val _statusText = MutableStateFlow("")
  val statusText: StateFlow<String> = _statusText.asStateFlow()

  private val _lastUpdateText = MutableStateFlow("")
  val lastUpdateText: StateFlow<String> = _lastUpdateText.asStateFlow()

  private val _lastErrorText = MutableStateFlow("")
  val lastErrorText: StateFlow<String> = _lastErrorText.asStateFlow()

  private val _numFailedStreams = MutableStateFlow(0)
  val numFailedStreams: StateFlow<Int> = _numFailedStreams.asStateFlow()

  private val _backoffEndTimeMs = MutableStateFlow(0L)
  val backoffEndTimeMs: StateFlow<Long> = _backoffEndTimeMs.asStateFlow()

  fun initRC() {
    viewModelScope.launch {
      _statusText.value = "Listener registered. Waiting for connection / updates..."

      remoteConfig.addOnConfigUpdateListener(
        object : ConfigUpdateListener {
          override fun onUpdate(configUpdate: ConfigUpdate) {
            val updatedKeys = configUpdate.updatedKeys
            val msg = "Config updated! Keys: $updatedKeys at ${getCurrentTimeString()}"
            Log.d(TAG, msg)
            _lastUpdateText.value = msg
            _statusText.value = "Stream active & update received!"

            remoteConfig.activate().addOnCompleteListener { task ->
              if (task.isSuccessful) {
                Log.d(TAG, "Activated remote config successfully")
              } else {
                Log.w(TAG, "Failed to activate remote config", task.exception)
              }
            }
          }

          override fun onError(error: FirebaseRemoteConfigException) {
            val msg = "Config update error: ${error.message} (Code: ${error.code}) at ${getCurrentTimeString()}"
            Log.w(TAG, msg, error)
            _lastErrorText.value = msg
            _statusText.value = "Error received from RemoteConfig listener"
          }
        }
      )
    }
  }

  fun getCurrentTimeString(): String {
    val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    return sdf.format(Date())
  }

  fun getRefreshBackOffInfo() {
    viewModelScope.launch {
      val failedStreams = sharedPrefs.getInt("num_failed_realtime_streams", 0)
      val endTime = sharedPrefs.getLong("realtime_backoff_end_time_in_millis", -1L)
      _numFailedStreams.value = failedStreams
      _backoffEndTimeMs.value = endTime

      val currentEndTime = _backoffEndTimeMs.value
      val now = System.currentTimeMillis()
      val backoffMessage = if (currentEndTime > now) {
        "Active: ${(currentEndTime - now) / 1000}s remaining"
      } else {
        "Expired/None"
      }

      Log.d(
        TAG,
        "Backoff Info -> Failed Streams: ${_numFailedStreams.value}, Backoff End Time: $currentEndTime ($backoffMessage)"
      )
    }
  }

  companion object {
    const val TAG = "MainViewModel"

    fun Factory(sharedPreferences: SharedPreferences): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        MainViewModel(
          sharedPrefs = sharedPreferences,
          remoteConfig = Firebase.remoteConfig
        )
      }
    }
  }
}
