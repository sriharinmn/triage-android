package io.github.sriharinmn.triage.ui.welcome

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sriharinmn.triage.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where "Try with sample data" stands. */
enum class SampleDataStatus {
  Idle,
  Starting,
  Failed,
}

/** What Welcome and Connect need to show "Try with sample data". */
data class SampleDataButton(val status: SampleDataStatus, val onClick: () -> Unit)

/** Test tags for "Try with sample data". */
object SampleDataTags {
  const val BUTTON = "sample_data_button"
  const val ERROR = "sample_data_error"
}

/**
 * Starts the demo for the signed-out screens. It lives in their session, so once the demo's account is stored this
 * ViewModel goes with them and its state never needs to return to Idle.
 */
class SampleDataViewModel(private val start: suspend () -> Unit) : ViewModel() {
  private val _status = MutableStateFlow(SampleDataStatus.Idle)
  val status: StateFlow<SampleDataStatus> = _status.asStateFlow()

  fun onTrySampleData() {
    if (_status.value == SampleDataStatus.Starting) return
    _status.value = SampleDataStatus.Starting
    viewModelScope.launch {
      try {
        start()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // Nothing from the exception is shown; a full phone is the only likely cause.
        _status.value = SampleDataStatus.Failed
      }
    }
  }
}

/** "Try with sample data": outlined on the welcome, a text button on Connect. It shows progress, and a line if it failed. */
@Composable
fun SampleDataAction(button: SampleDataButton, outlined: Boolean, @StringRes label: Int, modifier: Modifier = Modifier) {
  val starting = button.status == SampleDataStatus.Starting
  Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    val content: @Composable RowScope.() -> Unit = {
      if (starting) {
        val description = stringResource(R.string.sample_data_starting)
        CircularProgressIndicator(modifier = Modifier.size(20.dp).semantics { contentDescription = description }, strokeWidth = 2.dp)
      } else {
        Text(stringResource(label))
      }
    }
    val buttonModifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(SampleDataTags.BUTTON)
    if (outlined) {
      OutlinedButton(onClick = button.onClick, enabled = !starting, modifier = buttonModifier, content = content)
    } else {
      TextButton(onClick = button.onClick, enabled = !starting, modifier = buttonModifier, content = content)
    }
    if (button.status == SampleDataStatus.Failed) {
      Text(
        stringResource(R.string.sample_data_failed),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag(SampleDataTags.ERROR).semantics { liveRegion = LiveRegionMode.Polite },
      )
    }
  }
}
