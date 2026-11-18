package com.google.dps

import android.content.Context.MODE_PRIVATE
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.dps.ui.theme.DPSTheme
import com.google.firebase.Firebase
import com.google.firebase.options
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      DPSTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          RemoteConfigTestScreen(
            modifier = Modifier.padding(innerPadding)
          )
        }
      }
    }
  }
}

@Composable
fun RemoteConfigTestScreen(
  modifier: Modifier = Modifier
) {

  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val appId = Firebase.options.applicationId
  val prefsName = "frc_${appId}_firebase_settings"
  val sharedPref = remember {
    context.getSharedPreferences(prefsName, MODE_PRIVATE)
  }

  val myViewModel: MainViewModel = viewModel(factory = MainViewModel.Factory(sharedPref))

  val statusText by myViewModel.statusText.collectAsState()
  val lastUpdateText by myViewModel.lastUpdateText.collectAsState()
  val lastErrorText by myViewModel.lastErrorText.collectAsState()
  val numFailedStreams by myViewModel.numFailedStreams.collectAsState()
  val backoffEndTimeMs by myViewModel.backoffEndTimeMs.collectAsState()

  var remainingSeconds by remember { mutableLongStateOf(0L) }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_CREATE) {
        myViewModel.initRC()
      } else if (event == Lifecycle.Event.ON_RESUME) {
        myViewModel.getRefreshBackOffInfo()
      }
    }

    lifecycleOwner.lifecycle.addObserver(observer)

    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  LaunchedEffect(backoffEndTimeMs) {
    while (true) {
      val now = System.currentTimeMillis()
      remainingSeconds = if (backoffEndTimeMs > now) (backoffEndTimeMs - now) / 1000 else 0L
      delay(1000.milliseconds)
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = "Remote Config Realtime Test",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold
    )

    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Status:", fontWeight = FontWeight.Bold)
        Text(text = statusText, fontSize = 14.sp)

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = "Backoff Metadata:", fontWeight = FontWeight.Bold)
        Text(text = "Failed Streams: $numFailedStreams")
        if (remainingSeconds > 0) {
          Text(
            text = "Backoff Active: $remainingSeconds seconds remaining",
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold
          )
        } else if (backoffEndTimeMs > 0) {
          Text(text = "Backoff Ended (End time was ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(backoffEndTimeMs))})")
        } else {
          Text(text = "No Backoff Active")
        }
      }
    }

    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Last Config Update:", fontWeight = FontWeight.Bold)
        Text(text = lastUpdateText, fontSize = 14.sp)
      }
    }

    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Last Error:", fontWeight = FontWeight.Bold)
        Text(text = lastErrorText, fontSize = 14.sp)
      }
    }

    Button(
      onClick = {
        myViewModel.getRefreshBackOffInfo()
      }
    ) {
      Text(text = "Refresh Backoff Info")
    }
  }
}
