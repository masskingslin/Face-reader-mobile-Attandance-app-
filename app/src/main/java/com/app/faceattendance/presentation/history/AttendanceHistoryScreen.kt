package com.app.faceattendance.presentation.history

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.app.faceattendance.data.local.AttendanceRecordEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceHistoryScreen(
    viewModel: AttendanceHistoryViewModel,
    onExportCsv: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = state.selectedDateMillis ?: System.currentTimeMillis()
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Attendance History (90 Days)", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onExportCsv) {
                        Icon(Icons.Default.Share, contentDescription = "Export CSV Backup")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search by name or employee ID...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = state.selectedDateMillis != null,
                    onClick = { showDatePicker = true },
                    label = {
                        val label = state.selectedDateMillis?.let {
                            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it))
                        } ?: "Select Date"
                        Text(label)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    trailingIcon = {
                        if (state.selectedDateMillis != null) {
                            IconButton(
                                onClick = { viewModel.onDateSelected(null) },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear date")
                            }
                        }
                    }
                )

                FilterChip(
                    selected = state.selectedPunchFilter == FilterPunchType.IN,
                    onClick = {
                        viewModel.onPunchFilterChanged(
                            if (state.selectedPunchFilter == FilterPunchType.IN) FilterPunchType.ALL else FilterPunchType.IN
                        )
                    },
                    label = { Text("IN Only") }
                )

                FilterChip(
                    selected = state.selectedPunchFilter == FilterPunchType.OUT,
                    onClick = {
                        viewModel.onPunchFilterChanged(
                            if (state.selectedPunchFilter == FilterPunchType.OUT) FilterPunchType.ALL else FilterPunchType.OUT
                        )
                    },
                    label = { Text("OUT Only") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (state.records.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (state.isLoading) "Loading logs..." else "No attendance records found.",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.records, key = { it.id }) { record ->
                        AttendanceRecordCard(
                            record = record,
                            onThumbnailClick = { viewModel.onOpenPhotoPreview(record.imageUri) }
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onDateSelected(datePickerState.selectedDateMillis)
                        showDatePicker = false
                    }
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    state.previewImageUri?.let { uriString ->
        PhotoPreviewDialog(
            imageUri = uriString,
            onDismiss = viewModel::onClosePhotoPreview
        )
    }
}

@Composable
fun AttendanceRecordCard(
    record: AttendanceRecordEntity,
    onThumbnailClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPunchIn = record.type.equals("IN", ignoreCase = true)
    val badgeColor = if (isPunchIn) Color(0xFF00C853) else Color(0xFFD50000)
    val formattedDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(record.timestamp))
    val formattedTime = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(record.timestamp))

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.DarkGray)
                    .clickable(onClick = onThumbnailClick)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(Uri.parse(record.imageUri))
                        .crossfade(true)
                        .build(),
                    contentDescription = "Attendance Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.userName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "ID: ${record.userId}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$formattedDate • $formattedTime",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                color = badgeColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = record.type.uppercase(),
                    color = badgeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
fun PhotoPreviewDialog(
    imageUri: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Attendance Snapshot",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(Uri.parse(imageUri))
                        .crossfade(true)
                        .build(),
                    contentDescription = "Full Snapshot Preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}
