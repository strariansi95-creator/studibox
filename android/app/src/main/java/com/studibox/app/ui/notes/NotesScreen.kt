package com.studibox.app.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studibox.app.R
import com.studibox.app.ui.common.EmptyState
import com.studibox.app.ui.common.NoteCard
import com.studibox.app.ui.theme.AppShapes
import com.studibox.app.ui.theme.Spacing
import com.studibox.app.ui.theme.StudiboxTheme

@Composable
fun NotesScreen(
    onAddNote: () -> Unit,
    onOpenNote: (Long) -> Unit,
    viewModel: NotesViewModel = viewModel()
) {
    val colors = StudiboxTheme.colors
    val notes by viewModel.notes.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenHorizontal)
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.tab_notes),
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary
            )
            Box(
                modifier = Modifier
                    .clip(AppShapes.pill)
                    .background(colors.accentContainer)
                    .clickable(onClick = onAddNote)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.add_note),
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (notes.isEmpty()) {
            EmptyState(
                text = stringResource(R.string.no_notes),
                actionLabel = stringResource(R.string.add_note),
                onAction = onAddNote
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = Spacing.screenHorizontal,
                end = Spacing.screenHorizontal,
                top = 14.dp,
                bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards)
        ) {
            items(notes, key = { it.id }) { note ->
                NoteCard(
                    note = note,
                    onClick = { onOpenNote(note.id) },
                    onDelete = { viewModel.delete(note.id) }
                )
            }
        }
    }
}
