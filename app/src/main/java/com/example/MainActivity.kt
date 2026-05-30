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
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(private val dao: PhraseDao) : ViewModel() {
    val phrases = dao.getAllPhrases()
    val logs = dao.getAllLogs()

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

    fun clearAllLogs() {
        viewModelScope.launch {
            dao.clearLogs()
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

enum class ActiveTab {
    PANEL, AYARLAR, GECMIS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(context))
    
    val phrases by viewModel.phrases.collectAsState(initial = emptyList())
    val logs by viewModel.logs.collectAsState(initial = emptyList())
    
    var activeTab by remember { mutableStateOf(ActiveTab.PANEL) }

    // Steppers / Configuration inputs
    var newText by remember { mutableStateOf("") }
    var newCount by remember { mutableStateOf("10") }
    var newInterval by remember { mutableStateOf("500") }
    
    val activePhrase by TyperState.activePhrase.collectAsState()
    
    // Permission tracking states
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasAccessibilityPermission by remember {
        mutableStateOf(isAccessibilityServiceEnabled(context))
    }

    // Refresh permissions when user resumes the application
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
                hasAccessibilityPermission = isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Interactive Guide Checklist Steps
    val guideSteps = listOf(
        Pair("1. İzin Yetkileri", "Telefonunuzda üstte gösterim (Overlay) ve Erişilebilirlik (Accessibility) izinlerini aktif edin. İki izin verilince bu alan kaybolacaktır."),
        Pair("2. Metni Hazırlayın", "Yazılacak metni, tekrar sayısını ve yazım hızını belirleyin; ardından 'SİSTEMİ BAŞLAT' butonuna dokunun."),
        Pair("3. Kendi Kendine Öğrenme (REC)", "Sistem kopyalama modu aktiftir. Başka uygulamada (örn. oyun içi sohbet) bir metin yazıp gönderdiğinizde, servis o metni otomatik ezberler!"),
        Pair("4. Arka Planda Çalıştırma", "Oyuna veya sohbet penceresine girip yazım alanını seçin. Ekrandaki yuvarlak floating barın 'PLAY' butonuna basın, otomatik yazma ve gönderme başlayacaktır.")
    )
    val currentTutorialStep by TyperState.tutorialStep.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- 1. HEADER (POLISHED ACCORDING TO SPEC) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AUTO-TYPER UTILITY",
                    color = Color(0xFFD0BCFF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp
                )
                Text(
                    text = "AutoScribe Pro",
                    color = Color(0xFFE6E1E5),
                    fontSize = 25.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            // Pulse circle status icon representing active background runner
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4F378B))
                    .clickable {
                        // Reset guide index
                        TyperState.tutorialStep.value = 0
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("💡", fontSize = 18.sp)
            }
        }

        // --- 2. GUIDED TUTORIAL CONTAINER (Turkish interactive helper) ---
        if (currentTutorialStep != -1) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .background(Color(0xFF2B2930), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFFD0BCFF).copy(0.3f), RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🚀 Öğretici & Kullanım Kılavuzu",
                            color = Color(0xFFD0BCFF),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { TyperState.tutorialStep.value = -1 },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val stepData = guideSteps[currentTutorialStep]
                    Text(
                        text = "Adım ${currentTutorialStep + 1}: ${stepData.first}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stepData.second,
                        color = Color(0xFFCAC4D0),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${currentTutorialStep + 1} / ${guideSteps.size}",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (currentTutorialStep > 0) {
                                TextButton(onClick = { TyperState.tutorialStep.value -= 1 }) {
                                    Text("Geri", color = Color(0xFFD0BCFF), fontSize = 12.sp)
                                }
                            }
                            if (currentTutorialStep < guideSteps.size - 1) {
                                Button(
                                    onClick = { TyperState.tutorialStep.value += 1 },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F378B)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                                ) {
                                    Text("İleri", color = Color.White, fontSize = 12.sp)
                                }
                            } else {
                                Button(
                                    onClick = { TyperState.tutorialStep.value = -1 },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                                ) {
                                    Text("Anladım", color = Color(0xFF381E72), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 3. DYNAMIC PERMISSIONS CARD BLOCK (Hidden when permissions are granted!) ---
        if (!hasOverlayPermission || !hasAccessibilityPermission) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .fillMaxWidth()
                    .background(Color(0xFF31111D), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFF93000A), RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFB4AB)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Uyarı",
                                tint = Color(0xFF690005),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Gerekli Sistem İzinleri Eksik",
                                color = Color(0xFFFFDAD6),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Otomatik klavye yazımı ve ekran üstü panel için izin verin.",
                                color = Color(0xFFFFDAD6).copy(alpha = 0.75f),
                                fontSize = 11.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PermissionHelperRow(
                            title = "Üstte Gösterim İzni (Overlay)",
                            approved = hasOverlayPermission,
                            onGrant = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        )
                        PermissionHelperRow(
                            title = "Erişilebilirlik İzni (Accessibility)",
                            approved = hasAccessibilityPermission,
                            onGrant = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- 4. NAVIGATION TAB STRIP ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2B2930)),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val tabs = listOf(
                Triple(ActiveTab.PANEL, "Panel", Icons.Default.List),
                Triple(ActiveTab.AYARLAR, "Ayarlar", Icons.Default.Settings),
                Triple(ActiveTab.GECMIS, "Geçmiş", Icons.Default.Info)
            )
            tabs.forEach { (tab, label, icon) ->
                val active = activeTab == tab
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { activeTab = tab }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (active) Color(0xFFD0BCFF) else Color.White.copy(0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = label,
                        color = if (active) Color(0xFFD0BCFF) else Color.White.copy(0.4f),
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- 5. TAB VIEW CONTENT HOST ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            when (activeTab) {
                ActiveTab.PANEL -> PanelTabContainer(
                    phrases = phrases,
                    newText = newText,
                    onTextChange = { newText = it },
                    newCount = newCount,
                    onCountChange = { newCount = it },
                    newInterval = newInterval,
                    onIntervalChange = { newInterval = it },
                    activePhrase = activePhrase,
                    onPhraseSelect = { TyperState.activePhrase.value = it },
                    onPhraseDelete = { viewModel.deletePhrase(it) },
                    onStart = {
                        val count = newCount.toIntOrNull() ?: 10
                        val interval = newInterval.toLongOrNull() ?: 500
                        if (newText.isNotBlank()) {
                            viewModel.addPhrase(newText, count, interval)
                            TyperState.activePhrase.value = Phrase(text = newText, repeatCount = count, intervalMs = interval)
                            newText = ""
                        }
                    }
                )
                
                ActiveTab.AYARLAR -> SettingsTabContainer()
                
                ActiveTab.GECMIS -> HistoryTabContainer(
                    logs = logs,
                    onClear = { viewModel.clearAllLogs() },
                    onCopyPhrase = { text ->
                        newText = text
                        activeTab = ActiveTab.PANEL
                    }
                )
            }
        }

        // Space at the very bottom
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// Permission grant row helper widget
@Composable
fun PermissionHelperRow(
    title: String,
    approved: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (approved) Color(0xFF1E3A1E) else Color(0xFF1C1B1F))
            .border(
                1.dp,
                if (approved) Color.Green.copy(0.3f) else Color(0xFF49454F),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (approved) "İzin Verildi ✓" else "İzin Verilmedi ✗",
                color = if (approved) Color.Green else Color(0xFFFFB4AB),
                fontSize = 10.sp
            )
        }
        if (!approved) {
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F378B)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("DÜZELT", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// --- PANEL TAB CONTAINER ---
@Composable
fun PanelTabContainer(
    phrases: List<Phrase>,
    newText: String,
    onTextChange: (String) -> Unit,
    newCount: String,
    onCountChange: (String) -> Unit,
    newInterval: String,
    onIntervalChange: (String) -> Unit,
    activePhrase: Phrase?,
    onPhraseSelect: (Phrase) -> Unit,
    onPhraseDelete: (Phrase) -> Unit,
    onStart: () -> Unit
) {
    val autoSend by TyperState.autoSend.collectAsState()
    val learnMode by TyperState.learnMode.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Step helper card representing setup inputs
        item {
            Column(
                modifier = Modifier
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
                    onValueChange = onTextChange,
                    placeholder = { Text("Buraya otomatik yazılacak kelimeyi veya cümleyi girin...", color = Color(0xFFCAC4D0).copy(alpha = 0.5f), fontSize = 13.sp) },
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
                        .height(80.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tekrar Sayısı", color = Color(0xFFCAC4D0), fontSize = 11.sp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val count = newCount.toIntOrNull() ?: 10
                                    if (count > 1) onCountChange((count - 1).toString())
                                },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF49454F)),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text(
                                text = newCount,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(28.dp),
                                textAlign = TextAlign.Center
                            )
                            IconButton(
                                onClick = {
                                    val count = newCount.toIntOrNull() ?: 10
                                    onCountChange((count + 1).toString())
                                },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF49454F)),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1.2f).padding(horizontal = 8.dp)) {
                        Text("Yazma Hızı (Gecikme ms)", color = Color(0xFFCAC4D0), fontSize = 11.sp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val speed = newInterval.toLongOrNull() ?: 500L
                                    if (speed > 100) onIntervalChange((speed - 100).toString())
                                },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF49454F)),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("-", color = Color.White, fontSize = 12.sp)
                            }
                            Text(
                                text = "${newInterval}ms",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(55.dp),
                                textAlign = TextAlign.Center
                            )
                            IconButton(
                                onClick = {
                                    val speed = newInterval.toLongOrNull() ?: 500L
                                    onIntervalChange((speed + 100).toString())
                                },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF49454F)),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("+", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }

                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD0BCFF),
                            contentColor = Color(0xFF381E72)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("SİSTEMİ BAŞLAT", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // Active selection diagnostic block
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2B2930))
                    .border(1.dp, Color(0xFFD0BCFF).copy(0.15f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4F378B)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("💡", fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("ŞU AN AKTİF YAPILANDIRMA", color = Color(0xFFD0BCFF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (activePhrase != null) "\"${activePhrase.text}\" (Tekrar: ${activePhrase.repeatCount})" else "Henüz bir şey seçilmedi! Aşağıdakilerden birine dokunun.",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        item {
            Text(
                text = "KAYDEDİLEN TASLAKLAR",
                color = Color(0xFFD0BCFF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        if (phrases.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .border(1.dp, Color(0xFF49454F), RoundedCornerShape(24.dp))
                        .background(Color(0xFF2B2930)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("✍️", fontSize = 32.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Ezberleme Modu Aktif",
                            color = Color(0xFFE6E1E5),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Sistemi başlattıktan sonra başka bir uygulamaya girip ilk girişi yapmanız yeterlidir, anında kaydedilir.",
                            color = Color(0xFFCAC4D0),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            items(phrases) { phrase ->
                val isActive = activePhrase?.text == phrase.text
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPhraseSelect(phrase) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) Color(0xFF4F378B).copy(alpha = 0.35f) else Color(0xFF2B2930)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        if (isActive) 2.dp else 1.dp,
                        if (isActive) Color(0xFFD0BCFF) else Color(0xFF49454F)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                phrase.text,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Tekrar: ${phrase.repeatCount} kere  |  Yazım Hızı: ${phrase.intervalMs}ms",
                                color = Color(0xFFCAC4D0),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!isActive) {
                                IconButton(
                                    onClick = { onPhraseSelect(phrase) },
                                    modifier = Modifier.size(32.dp),
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF49454F))
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Seç", tint = Color(0xFFD0BCFF), modifier = Modifier.size(16.dp))
                                }
                            }
                            IconButton(
                                onClick = { onPhraseDelete(phrase) },
                                modifier = Modifier.size(32.dp),
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF31111D))
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Sil", tint = Color(0xFFFFB4AB), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- SETTINGS TAB CONTAINER ---
@Composable
fun SettingsTabContainer() {
    val autoSend by TyperState.autoSend.collectAsState()
    val learnMode by TyperState.learnMode.collectAsState()
    val floatingAlpha by TyperState.floatingAlpha.collectAsState()
    val isMinimized by TyperState.isMinimized.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "ÇALIŞMA AYARLARI",
                color = Color(0xFFD0BCFF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }

        // Auto send toggle
        item {
            SettingToggleCard(
                title = "Metni Otomatik Gönder",
                description = "Metni yazdıktan sonra sohbet uygulamasındaki Gönder butonuna basmayı dener (Örn. WhatsApp, Telegram, Instagram vb.)",
                enabled = autoSend,
                onCheckedChange = { TyperState.autoSend.value = it }
            )
        }

        // Live typing memorize learning trigger
        item {
            SettingToggleCard(
                title = "Otomatik Ezber Modu (Listen)",
                description = "Arka planda siz yazı yazarken onu algılayıp sisteme otomatik taslak olarak kopyalar. Tekrar elle yazmanıza gerek kalmaz.",
                enabled = learnMode,
                onCheckedChange = { TyperState.learnMode.value = it }
            )
        }

        // Floating opacity controller
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2B2930))
                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Ekran Üstü Panel Şeffaflığı", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("Panelin görünürlüğünü ayarlar", color = Color(0xFFCAC4D0), fontSize = 11.sp)
                    }
                    Text(
                        text = "${(floatingAlpha * 100).toInt()}%",
                        color = Color(0xFFD0BCFF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Slider(
                    value = floatingAlpha,
                    onValueChange = { TyperState.floatingAlpha.value = it },
                    valueRange = 0.2f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFD0BCFF),
                        activeTrackColor = Color(0xFFD0BCFF),
                        inactiveTrackColor = Color(0xFF49454F)
                    )
                )
            }
        }

        // Quick overlay states triggers
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2B2930))
                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Geçici Olarak Panel Görünümünü Küçült", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    Text("Paneli ufak bir yuvarlak baloncuk haline getirir.", color = Color(0xFFCAC4D0), fontSize = 11.sp)
                }
                Button(
                    onClick = { TyperState.isMinimized.value = !isMinimized },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F378B))
                ) {
                    Text(if (isMinimized) "GÖSTER" else "KÜÇÜLT", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun SettingToggleCard(
    title: String,
    description: String,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF2B2930))
            .border(1.dp, Color(0xFF49454F), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = Color(0xFFCAC4D0), fontSize = 11.sp, lineHeight = 14.sp)
        }
        Switch(
            checked = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF381E72),
                checkedTrackColor = Color(0xFFD0BCFF),
                uncheckedThumbColor = Color.LightGray,
                uncheckedTrackColor = Color(0xFF49454F)
            )
        )
    }
}

// --- HISTORY TAB CONTAINER ---
@Composable
fun HistoryTabContainer(
    logs: List<TyperLog>,
    onClear: () -> Unit,
    onCopyPhrase: (String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss (dd.MM)", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "YAZMA GEÇMİŞİ",
                color = Color(0xFFD0BCFF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            if (logs.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text("Tümünü Temizle", color = Color(0xFFFFB4AB), fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(20.dp))
                    .background(Color(0xFF2B2930)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text("⌛", fontSize = 32.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Henüz Hiç Gönderim Yapılmadı", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Sistem başladığında gönderilen metin logları burada gösterilir.", color = Color(0xFFCAC4D0), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF49454F))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    log.phraseText,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${log.count} tekrar gönderildi  •  ${dateFormat.format(Date(log.timestamp))}",
                                    color = Color(0xFFCAC4D0),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            IconButton(
                                onClick = { onCopyPhrase(log.phraseText) },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF49454F)),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Kopyala", tint = Color(0xFFD0BCFF), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper to reliably scan active enabled accessibility status
private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    return enabledServices?.contains(context.packageName) == true
}
