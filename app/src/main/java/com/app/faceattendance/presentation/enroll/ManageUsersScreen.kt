package com.app.faceattendance.presentation.enroll

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.faceattendance.data.local.AttendanceDao
import com.app.faceattendance.data.local.UserEntity
import com.app.faceattendance.data.ml.FaceNetModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageUsersScreen(
    dao: AttendanceDao,
    faceNetModel: FaceNetModel,
    onNavigateToEnroll: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val users by dao.getAllUsersFlow().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var userPendingDelete by remember { mutableStateOf<UserEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enrolled Employees (${users.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToEnroll) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add New Employee")
                    }
                }
            )
        }
    ) { padding ->
        if (users.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No employees enrolled yet.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onNavigateToEnroll) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enroll First Employee")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(users, key = { it.id }) { user ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("ID: ${user.id}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { userPendingDelete = user }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete ${user.name}", tint = Color(0xFFD32F2F))
                            }
                        }
                    }
                }
            }
        }
    }

    userPendingDelete?.let { user ->
        AlertDialog(
            onDismissRequest = { userPendingDelete = null },
            title = { Text("Remove ${user.name}?") },
            text = { Text("This will delete their enrolled face data. They will need to be re-enrolled to punch in/out again.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        dao.deleteUser(user.id)
                        val remaining = dao.getAllUsers()
                        faceNetModel.loadUsers(remaining)
                    }
                    userPendingDelete = null
                }) {
                    Text("Delete", color = Color(0xFFD32F2F))
                }
            },
            dismissButton = {
                TextButton(onClick = { userPendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}