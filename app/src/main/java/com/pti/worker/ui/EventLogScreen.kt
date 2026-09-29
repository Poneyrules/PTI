package com.pti.worker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pti.worker.data.db.entities.EventEntity
import com.pti.worker.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventLogScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val events by viewModel.events.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Journal des événements") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            items(events, key = { it.id }) { event -> EventItem(event) }
        }
    }
}

@Composable
private fun EventItem(event: EventEntity) {
    val dateFormat = SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE)
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(event.type, style = MaterialTheme.typography.titleLarge)
            Text(dateFormat.format(Date(event.timestamp)))
            event.message?.let { Text(it) }
            if (event.latitude != null && event.longitude != null) {
                Text("%.5f, %.5f".format(event.latitude, event.longitude))
            }
        }
    }
}
