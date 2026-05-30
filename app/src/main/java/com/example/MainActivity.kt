package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainViewModel(private val dao: PhraseDao) : ViewModel() {
    val phrases = dao.getAllPhrases()
    fun addPhrase(text: String, count: Int, interval: Long) {
        viewModelScope.launch {
            dao.insert(Phrase(text = text, repeatCount = count, intervalMs = interval))
        }
    }
    fun deletePhrase(phrase: Phrase) {
        viewModelScope.launch {
            dao.delete(phrase)
        }
    }
}

class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(AppDatabase.getDatabase(context).phraseDao()) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(context))
    val phrases by viewModel.phrases.collectAsState(initial = emptyList())
    var newText by remember { mutableStateOf("") }
    var newCount by remember { mutableStateOf("10") }
    var newInterval by remember { mutableStateOf("500") }
    
    val activePhrase by TyperState.activePhrase.collectAsState()

    // Permission tracking states
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasAccessibilityPermission by remember {
        mutableStateOf(
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )?.contains(context.packageName) == true
        )
    }

    // Refresh permission status when user resumes the activity
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
                hasAccessibilityPermission = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )?.contains(context.packageName) == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- Header Block (Polished & Styled) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AUTO-AUTOMATION",
                    color = Color(0xFFD0BCFF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "AutoScribe Pro",
                    color = Color(0xFFE6E1E5),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4F378B)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .border(2.dp, Color.White, RoundedCornerShape(2.dp))
                )
            }
        }

        // --- Alert Box for Permissions (If missing) ---
        if (!hasOverlayPermission || !hasAccessibilityPermission) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .background(Color(0xFF31111D), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFF93000A), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFB4AB)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = Color(0xFF690005),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "İzin Gerekli",
                            color = Color(0xFFFFDAD6),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Ekran üzerine yazma ve erişilebilirlik izni verin.",
                            color = Color(0xFFFFDAD6).copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                    Button(
                        onClick = {
                            if (!hasOverlayPermission) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } else if (!hasAccessibilityPermission) {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color(0xFFFFB4AB)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "İZİN VER",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        } else {
            // Success State bar
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2B2930))
                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color.Green, CircleShape)
                )
                Text(
                    text = "Servis Arka Planda Çalışıyor",
                    color = Color(0xFFE6E1E5),
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "AKTİF",
                    color = Color(0xFFD0BCFF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // --- Main Config / Card Panel ---
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF2B2930))
                .border(1.dp, Color(0xFF49454F), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Text(
                text = "KAYDEDİLECEK METİN",
                color = Color(0xFFD0BCFF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            TextField(
                value = newText,
                onValueChange = { newText = it },
                placeholder = { Text("Buraya otomatik yazılacak metni girin...", color = Color(0xFFCAC4D0).copy(alpha = 0.5f)) },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color(0xFFE6E1E5),
                    unfocusedTextColor = Color(0xFFE6E1E5),
                    focusedContainerColor = Color(0xFF1C1B1F),
                    unfocusedContainerColor = Color(0xFF1C1B1F),
                    disabledContainerColor = Color(0xFF1C1B1F),
                    focusedIndicatorColor = Color(0xFFD0BCFF),
                    unfocusedIndicatorColor = Color(0xFF49454F)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Repeat Count Stepper
                Column {
                    Text(
                        text = "Tekrar Sayısı",
                        color = Color(0xFFCAC4D0),
                        fontSize = 12.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val current = newCount.toIntOrNull() ?: 10
                                if (current > 1) {
                                    newCount = (current - 1).toString()
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color(0xFF49454F)
                            ),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        
                        Text(
                            text = newCount,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(36.dp)
                        )

                        IconButton(
                            onClick = {
                                val current = newCount.toIntOrNull() ?: 10
                                newCount = (current + 1).toString()
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color(0xFF49454F)
                            ),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }

                // Systems Start Input Button
                Button(
                    onClick = {
                        val count = newCount.toIntOrNull() ?: 10
                        val interval = newInterval.toLongOrNull() ?: 500
                        if (newText.isNotBlank()) {
                            viewModel.addPhrase(newText, count, interval)
                            // Auto select newly added
                            TyperState.activePhrase.value = Phrase(text = newText, repeatCount = count, intervalMs = interval)
                            newText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD0BCFF),
                        contentColor = Color(0xFF381E72)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = "SİSTEMİ BAŞLAT",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // Active State indicator
        if (activePhrase != null) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .background(Color(0xFF2B2930), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(0xFFD0BCFF), CircleShape)
                )
                Text(
                    text = "Hazır Metin: \"${activePhrase?.text}\"",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Phrase list / Record state description ---
        Text(
            text = "KAYDEDİLEN METİNLER",
            color = Color(0xFFD0BCFF),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )

        if (phrases.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(24.dp))
                    .background(Color(0xFF1C1B1F)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Empty state indicator
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2B2930)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📜", fontSize = 28.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Ezberleme Modu Pasif",
                        color = Color(0xFFE6E1E5),
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Sistemi başlattıktan sonra başka bir uygulamaya girip ilk girişi yapmanız yeterlidir.",
                        color = Color(0xFFCAC4D0),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(phrases) { phrase ->
                    val isActive = activePhrase?.id == phrase.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { TyperState.activePhrase.value = phrase },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) Color(0xFF4F378B).copy(alpha = 0.4f) else Color(0xFF2B2930)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = borderStrokeHelper(isActive)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    phrase.text, 
                                    color = Color.White,
                                    fontSize = 15.sp, 
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Tekrar: ${phrase.repeatCount} | Gecikme: ${phrase.intervalMs}ms", 
                                    color = Color(0xFFCAC4D0),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (!isActive) {
                                    IconButton(
                                        onClick = { TyperState.activePhrase.value = phrase },
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = Color(0xFF49454F).copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.PlayArrow, 
                                            contentDescription = "Set Active",
                                            tint = Color(0xFFD0BCFF)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.deletePhrase(phrase) },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = Color(0xFF31111D)
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Delete, 
                                        contentDescription = "Delete",
                                        tint = Color(0xFFFFB4AB)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom styled tab indicator bar matching the spec
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(Color(0xFF2B2930))
                .border(1.dp, Color(0xFF49454F)),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(modifier = Modifier.width(48.dp).height(3.dp).background(Color(0xFFD0BCFF), RoundedCornerShape(1.5.dp)))
                Spacer(modifier = Modifier.height(6.dp))
                Text("PANEL", color = Color(0xFFD0BCFF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(modifier = Modifier.width(48.dp).height(3.dp).background(Color.Transparent))
                Spacer(modifier = Modifier.height(6.dp))
                Text("AYARLAR", color = Color(0xFFE6E1E5).copy(alpha = 0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(modifier = Modifier.width(48.dp).height(3.dp).background(Color.Transparent))
                Spacer(modifier = Modifier.height(6.dp))
                Text("GEÇMİŞ", color = Color(0xFFE6E1E5).copy(alpha = 0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun borderStrokeHelper(isActive: Boolean): androidx.compose.foundation.BorderStroke {
    return if (isActive) {
        androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFD0BCFF))
    } else {
        androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF49454F))
    }
}

