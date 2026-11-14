package io.github.sriharinmn.triage.ui.inbox

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.sriharinmn.triage.R
import io.github.sriharinmn.triage.data.issues.IssueSort

private enum class FilterSheet {
  Project,
  Environment,
  Sort,
}

/** Project, environment and sort chips; each opens a sheet where one tap picks a value. */
@Composable
internal fun InboxFilterRow(
  state: InboxUiState,
  onProjectSelected: (String?) -> Unit,
  onEnvironmentSelected: (String?) -> Unit,
  onSortSelected: (IssueSort) -> Unit,
  modifier: Modifier = Modifier,
) {
  var openSheet by rememberSaveable { mutableStateOf<FilterSheet?>(null) }
  val allProjects = stringResource(R.string.inbox_filter_all_projects)
  val allEnvironments = stringResource(R.string.inbox_filter_all_environments)
  val sortLabels = IssueSort.entries.associateWith { stringResource(it.labelRes()) }

  Row(
    modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    DropdownChip(
      // A remembered project that hasn't loaded yet still shows something truthful: its id.
      label = state.selectedProject?.slug ?: state.filter.projectId ?: allProjects,
      active = state.filter.projectId != null,
      onClick = { openSheet = FilterSheet.Project },
      modifier = Modifier.testTag(InboxTags.PROJECT_FILTER),
    )
    DropdownChip(
      label = state.filter.environment ?: allEnvironments,
      active = state.filter.environment != null,
      onClick = { openSheet = FilterSheet.Environment },
      modifier = Modifier.testTag(InboxTags.ENVIRONMENT_FILTER),
    )
    DropdownChip(
      label = sortLabels.getValue(state.filter.sort),
      active = false,
      onClick = { openSheet = FilterSheet.Sort },
      modifier = Modifier.testTag(InboxTags.SORT_FILTER),
    )
  }

  val dismiss = { openSheet = null }
  when (openSheet) {
    FilterSheet.Project ->
      ChoiceSheet(
        title = stringResource(R.string.inbox_filter_project_title),
        options = listOf<String?>(null) + state.projects.map { it.id },
        selected = state.filter.projectId,
        label = { id -> state.projects.firstOrNull { it.id == id }?.slug ?: allProjects },
        onSelect = onProjectSelected,
        onDismiss = dismiss,
      )
    FilterSheet.Environment ->
      ChoiceSheet(
        title = stringResource(R.string.inbox_filter_environment_title),
        options = listOf<String?>(null) + state.environments,
        selected = state.filter.environment,
        label = { it ?: allEnvironments },
        onSelect = onEnvironmentSelected,
        onDismiss = dismiss,
      )
    FilterSheet.Sort ->
      ChoiceSheet(
        title = stringResource(R.string.inbox_filter_sort_title),
        options = IssueSort.entries,
        selected = state.filter.sort,
        label = { sortLabels.getValue(it) },
        onSelect = onSortSelected,
        onDismiss = dismiss,
      )
    null -> Unit
  }
}

@Composable
private fun DropdownChip(label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  FilterChip(
    selected = active,
    onClick = onClick,
    // The row scrolls sideways, so a chip always shows its whole label, even at large font sizes.
    label = { Text(label, maxLines = 1) },
    trailingIcon = {
      Icon(
        painterResource(R.drawable.ic_arrow_drop_down),
        contentDescription = null,
        modifier = Modifier.size(FilterChipDefaults.IconSize),
      )
    },
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceSheet(
  title: String,
  options: List<T>,
  selected: T,
  label: (T) -> String,
  onSelect: (T) -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
    // The last option stays clear of the gesture bar.
    LazyColumn(modifier = Modifier.selectableGroup().navigationBarsPadding()) {
      items(options) { option ->
        val isSelected = option == selected
        ListItem(
          headlineContent = { Text(label(option)) },
          leadingContent = { RadioButton(selected = isSelected, onClick = null) },
          colors = ListItemDefaults.colors(containerColor = Color.Transparent),
          modifier =
            Modifier.fillMaxWidth().selectable(
              selected = isSelected,
              role = Role.RadioButton,
              onClick = {
                onSelect(option)
                onDismiss()
              },
            ),
        )
      }
    }
  }
}
