package io.github.sriharinmn.triage.ui.detail

import androidx.annotation.StringRes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sriharinmn.triage.R

/** The tabs below the detail's top. Trace, Breadcrumbs and Details are about the shown event; Tags is about the issue. */
enum class DetailTab {
  Trace,
  Breadcrumbs,
  Tags,
  Details,
}

@StringRes
internal fun DetailTab.labelRes(): Int =
  when (this) {
    DetailTab.Trace -> R.string.detail_tab_trace
    DetailTab.Breadcrumbs -> R.string.detail_breadcrumbs
    DetailTab.Tags -> R.string.detail_tags
    DetailTab.Details -> R.string.detail_details
  }

/** Scrollable, so no tab name is cut off at large font sizes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailTabRow(selected: DetailTab, onSelected: (DetailTab) -> Unit, modifier: Modifier = Modifier) {
  SecondaryScrollableTabRow(selectedTabIndex = selected.ordinal, modifier = modifier, edgePadding = 8.dp) {
    DetailTab.entries.forEach { tab ->
      Tab(
        selected = tab == selected,
        onClick = { onSelected(tab) },
        text = { Text(stringResource(tab.labelRes()), maxLines = 1) },
        modifier = Modifier.testTag(DetailTags.tab(tab)),
      )
    }
  }
}
