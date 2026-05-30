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
                    containerColor = Color(0xFF0C0B10)
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
    val isOverlayHidden by TyperState.isOverlayHidden.collectAsState()
    
    // Permission tracking states
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasAccessibilityPermission by remember {
        mutableStateOf(isAccessibilityServiceEnabled(context))
    }

    // Auto-prompt dialog state on entry if permissions are missing
    var showPermissionDialogOnEntry by remember { 
        mutableStateOf(!hasOverlayPermission || !hasAccessibilityPermission) 
    }

    // Refresh permissions when user resumes the application
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
                hasAccessibilityPermission = isAccessibilityServiceEnabled(context)
                if (hasOverlayPermission && hasAccessibilityPermission) {
                    showPermissionDialogOnEntry = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Interactive Guide Checklist Steps (Türkçe Öğretici)
    val guideSteps = listOf(
        Triple("1. İzinleri Etkinleştir", "Telefonunuzun güvenlik ayarlarında 'Üstte Gösterim' ve 'Erişilebilirlik' izinlerini aktif hale getirin.", Icons.Default.Lock),
        Triple("2. Metni Hazırlayın", "Spam olarak gönderilecek metni ve adet miktarını belirleyip 'SİSTEMİ BAŞLAT' butonuna dokunun.", Icons.Default.Edit),
        Triple("3. Öğrenme Modu (Listening)", "Dilerseniz herhangi bir oyuna veya sohbet penceresine girip bir metin yazıp gönderin; sistem anında kopyalar!", Icons.Default.Send),
        Triple("4. Play ile Başlatın", "Uygulamada yazım alanını seçip ekrandaki yüzer menüden 'PLAY' butonuna basın. Yazma ve gönderme işlemi anında otomatik başlar.", Icons.Default.PlayArrow)
    )
    val currentTutorialStep by TyperState.tutorialStep.collectAsState()

    // Full layout gradient background
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D0B12),
                        Color(0xFF14121F),
                        Color(0xFF0C0B0F)
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // --- 1. HEADER WITH GLOW EFFECTS ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AKILLI YAZICI & SPAM MOTORU",
                        color = Color(0xFFA58BFF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.8.sp
                    )
                    Text(
                        text = "AutoScribe Pro",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                
                // Tutorial reset button (Bulb)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF231E39))
                        .border(1.dp, Color(0xFFA58BFF).copy(alpha = 0.4f), CircleShape)
                        .clickable { TyperState.tutorialStep.value = 0 },
                    contentAlignment = Alignment.Center
                ) {
                    Text("💡", fontSize = 18.sp)
                }
            }

            // --- 2. POP PROACTIVE PERMISSION OVERLAY ON APP ENTRY ---
            if (showPermissionDialogOnEntry && (!hasOverlayPermission || !hasAccessibilityPermission)) {
                AlertDialog(
                    onDismissRequest = { showPermissionDialogOnEntry = false },
                    containerColor = Color(0xFF1B1627),
                    tonalElevation = 6.dp,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🔑 İzin Kurulum Portalı", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Uygulamanın ekran üzerinde çalışması ve kelimeleri otomatik yazabilmesi için aşağıdaki sistem izinlerini vermeniz gerekmektedir:",
                                color = Color(0xFFCAC4D0),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            PermissionPromptRow(
                                title = "Üstte Gösterim İzni (Overlay)",
                                description = "Ekranda yüzen kontrol paneli için gereklidir.",
                                approved = hasOverlayPermission,
                                onGrant = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }
                            )
                            
                            PermissionPromptRow(
                                title = "Erişilebilirlik İzni (Accessibility)",
                                description = "Otomatik yazma ve klavye algılama motoru için zorunludur.",
                                approved = hasAccessibilityPermission,
                                onGrant = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showPermissionDialogOnEntry = false }) {
                            Text("Kapat", color = Color(0xFFA58BFF), fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            // --- 3. DYNAMIC SLIDING IN-APP PERMISSIONS WARNING CARD ---
            AnimatedVisibility(
                visible = !hasOverlayPermission || !hasAccessibilityPermission,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(listOf(Color(0xFF4A1020), Color(0xFF330914))), 
                            RoundedCornerShape(20.dp)
                        )
                        .border(1.dp, Color(0xFFE31B4B).copy(0.4f), RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE31B4B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Uyarı",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sistem İzinleri Eksik!",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Otomatik klavye yazımı ve yüzer menü devre dışı. Lütfen izin verin.",
                                    color = Color(0xFFFFDAD6).copy(alpha = 0.8f),
                                    fontSize = 11.5.sp,
                                    lineHeight = 15.sp
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

            // --- 4. NEW INTEGRATED INTERACTIVE TUTORIAL STEPS ---
            AnimatedVisibility(
                visible = currentTutorialStep != -1,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .background(Color(0xFF191724), RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0xFFA58BFF).copy(0.2f), RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("🎓 Adım Adım Kullanım Kılavuzu", color = Color(0xFFA58BFF), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { TyperState.tutorialStep.value = -1 },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val step = guideSteps[currentTutorialStep]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF28243C)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(step.third, contentDescription = null, tint = Color(0xFFA58BFF), modifier = Modifier.size(18.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = step.first,
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = step.second,
                                    color = Color(0xFFCAC4D0),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${currentTutorialStep + 1} / ${guideSteps.size}",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (currentTutorialStep > 0) {
                                    TextButton(onClick = { TyperState.tutorialStep.value -= 1 }) {
                                        Text("Geri", color = Color(0xFFA58BFF), fontSize = 12.sp)
                                    }
                                }
                                if (currentTutorialStep < guideSteps.size - 1) {
                                    Button(
                                        onClick = { TyperState.tutorialStep.value += 1 },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38305A)),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("İleri", color = Color.White, fontSize = 12.sp)
                                    }
                                } else {
                                    Button(
                                        onClick = { TyperState.tutorialStep.value = -1 },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA58BFF)),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Anladım", color = Color(0xFF1B1627), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- 5. OVERLAY VISIBILITY REVIVAL BAR (GİZLEME KAPATMA) ---
            if (isOverlayHidden) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF23141F))
                        .border(1.dp, Color(0xFFE47E00).copy(0.3f), RoundedCornerShape(16.dp))
                        .clickable { TyperState.isOverlayHidden.value = false }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("⚠️", fontSize = 16.sp)
                            Column {
                                Text("Yüzen Menü Gizlendi!", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                Text("Ekrandaki dairesel menü geçici gizlendi.", color = Color(0xFFCAC4D0), fontSize = 10.sp)
                            }
                        }
                        Button(
                            onClick = { TyperState.isOverlayHidden.value = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE47E00)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("MENÜYÜ GÖSTER", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- 6. NAVIGATION CUSTOM TAB STRIP ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF15131C))
                    .border(1.dp, Color(0xFF282535), RoundedCornerShape(14.dp)),
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
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (active) Color(0xFFA58BFF) else Color.White.copy(0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = label,
                            color = if (active) Color(0xFFA58BFF) else Color.White.copy(0.35f),
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- 7. TAB VIEW ACTIVE CONTAINER ---
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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun PermissionPromptRow(
    title: String,
    description: String,
    approved: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (approved) Color(0xFF14291D) else Color(0xFF26181F))
            .border(1.dp, if (approved) Color(0xFF2ECC71).copy(0.3f) else Color(0xFFE31B4B).copy(0.3f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(description, color = Color(0xFFCAC4D0), fontSize = 10.sp, lineHeight = 13.sp)
        }
        
        if (approved) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Aktif", tint = Color(0xFF2ECC71), modifier = Modifier.size(24.dp))
        } else {
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE31B4B)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("İZİN VER", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
    }
}

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
            .background(if (approved) Color(0xFF14291D) else Color(0xFF1A1420))
            .border(
                1.dp,
                if (approved) Color(0xFF2ECC71).copy(0.3f) else Color(0xFF49454F),
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
                color = if (approved) Color(0xFF2ECC71) else Color(0xFFFFB4AB),
                fontSize = 10.sp
            )
        }
        if (!approved) {
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA58BFF)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("DÜZELT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F1240))
            }
        }
    }
}

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
    val lastLearnedText by TyperState.lastLearnedText.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Form panel Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF15131C))
                    .border(1.dp, Color(0xFF2E2A3A), RoundedCornerShape(24.dp))
                    .padding(18.dp)
            ) {
                Text(
                    text = "YAZILACAK METİN",
                    color = Color(0xFFA58BFF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                TextField(
                    value = newText,
                    onValueChange = onTextChange,
                    placeholder = { 
                        Text(
                            "Örn: SA, naber?, spam vb. Veya başka uygulamada yazın, otomatik ezberlesin!", 
                            color = Color.White.copy(0.35f), 
                            fontSize = 12.sp
                        ) 
                    },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF0F0D14),
                        unfocusedContainerColor = Color(0xFF0F0D14),
                        disabledContainerColor = Color(0xFF0F0D14),
                        focusedIndicatorColor = Color(0xFFA58BFF),
                        unfocusedIndicatorColor = Color(0xFF2E2A3A)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                )

                // Last learned diagnostic strip
                if (lastLearnedText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F2C1F))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🧠 Ezberlenen Son Kelime:", color = Color(0xFF2ECC71), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "\"$lastLearnedText\"", 
                            color = Color.White, 
                            fontSize = 11.sp, 
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

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
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF2E2A3A)),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text(
                                text = newCount,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(32.dp),
                                textAlign = TextAlign.Center
                            )
                            IconButton(
                                onClick = {
                                    val count = newCount.toIntOrNull() ?: 10
                                    onCountChange((count + 1).toString())
                                },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF2E2A3A)),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1.2f).padding(horizontal = 8.dp)) {
                        Text("Hız (Gecikme)", color = Color(0xFFCAC4D0), fontSize = 11.sp)
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
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF2E2A3A)),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("-", color = Color.White, fontSize = 12.sp)
                            }
                            Text(
                                text = "${newInterval}ms",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(60.dp),
                                textAlign = TextAlign.Center
                            )
                            IconButton(
                                onClick = {
                                    val speed = newInterval.toLongOrNull() ?: 500L
                                    onIntervalChange((speed + 100).toString())
                                },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF2E2A3A)),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("+", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }

                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFA58BFF),
                            contentColor = Color(0xFF1B1627)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("KAYDET", fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Instant speed preset buttons tags
                Text("Hızlı Hız Seçimleri:", color = Color.Gray, fontSize = 10.sp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf("100ms", "300ms", "500ms", "1000ms")
                    presets.forEach { preset ->
                        val valueStr = preset.replace("ms", "")
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (newInterval == valueStr) Color(0xFF38305A) else Color(0xFF1F1C28))
                                .clickable { onIntervalChange(valueStr) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(preset, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Active layout diagnostics
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF15131C))
                    .border(1.dp, Color(0xFFA58BFF).copy(0.15f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF211D35)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🚀", fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("AKTİF ÇALIŞTIRILACAK TASLAK", color = Color(0xFFA58BFF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (activePhrase != null) "\"${activePhrase.text}\" (Tekrar: ${activePhrase.repeatCount})" else "Ezberlenen veya aşağıdaki şablonlardan birini seçin.",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Section Title
        item {
            Text(
                text = "KAYDEDİLEN ŞABLONLAR",
                color = Color(0xFFA58BFF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        if (phrases.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .border(1.dp, Color(0xFF2E2A3A), RoundedCornerShape(20.dp))
                        .background(Color(0xFF15131C)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("🤖", fontSize = 32.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Kendi kendine Kopyalama Aktif",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Sesi başlatıp başka bir uygulamaya geçip bir şeyler yazın. Sistem burayı otomatik dolduracaktır.",
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
                val isActive = activePhrase?.id == phrase.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPhraseSelect(phrase) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) Color(0xFF262040) else Color(0xFF15131C)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        if (isActive) 1.5.dp else 1.dp,
                        if (isActive) Color(0xFFA58BFF) else Color(0xFF2E2A3A)
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
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Tekrar: ${phrase.repeatCount} kere  •  Gecikme: ${phrase.intervalMs}ms",
                                color = Color(0xFFCAC4D0),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!isActive) {
                                IconButton(
                                    onClick = { onPhraseSelect(phrase) },
                                    modifier = Modifier.size(32.dp),
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF2E2A3A))
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Seç", tint = Color(0xFFA58BFF), modifier = Modifier.size(16.dp))
                                }
                            }
                            IconButton(
                                onClick = { onPhraseDelete(phrase) },
                                modifier = Modifier.size(32.dp),
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF33141E))
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

@Composable
fun SettingsTabContainer() {
    val autoSend by TyperState.autoSend.collectAsState()
    val learnMode by TyperState.learnMode.collectAsState()
    val floatingAlpha by TyperState.floatingAlpha.collectAsState()
    val isMinimized by TyperState.isMinimized.collectAsState()
    val isOverlayHidden by TyperState.isOverlayHidden.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "ÇALIŞMA YAPILANDIRMASI",
                color = Color(0xFFA58BFF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }

        // Auto send toggle
        item {
            SettingToggleCard(
                title = "Metni Otomatik Gönder Gerekirse",
                description = "Metni yazdıktan sonra sohbet uygulamalarındaki Gönder (<) ikonuna otomatik tıklayarak spami gönderir.",
                enabled = autoSend,
                onCheckedChange = { TyperState.autoSend.value = it }
            )
        }

        // Live typing memorize learning trigger
        item {
            SettingToggleCard(
                title = "Otomatik Ezber Bir Şey Yazılınca",
                description = "Dinleme modu açıkken klavyeden girilen yazıyı anında yakalayıp spamlenecek aktif metin yapar.",
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
                    .background(Color(0xFF15131C))
                    .border(1.dp, Color(0xFF2E2A3A), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Yüzen Bar Görünürlük Şeffaflığı", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("Yandaki kontrol panelinin şeffaflık seviyesi", color = Color(0xFFCAC4D0), fontSize = 11.sp)
                    }
                    Text(
                        text = "${(floatingAlpha * 100).toInt()}%",
                        color = Color(0xFFA58BFF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Slider(
                    value = floatingAlpha,
                    onValueChange = { TyperState.floatingAlpha.value = it },
                    valueRange = 0.2f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFA58BFF),
                        activeTrackColor = Color(0xFFA58BFF),
                        inactiveTrackColor = Color(0xFF2E2A3A)
                    )
                )
            }
        }

        // Hide overlay menu
        item {
            SettingToggleCard(
                title = "Menüyü Tamamen Gizle",
                description = "Ekranda duran dairesel yüzen barı tamamen gizler. Kayıtlı bildirimden geri yükleyebilirsiniz.",
                enabled = isOverlayHidden,
                onCheckedChange = { TyperState.isOverlayHidden.value = it }
            )
        }

        // Quick overlay states triggers
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF15131C))
                    .border(1.dp, Color(0xFF2E2A3A), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Yüzen Menüyü Baloncuk Yap", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    Text("Menüyü ufak dairesel baloncuk boyutuna küçültür.", color = Color(0xFFCAC4D0), fontSize = 11.sp)
                }
                Button(
                    onClick = { TyperState.isMinimized.value = !isMinimized },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38305A))
                ) {
                    Text(if (isMinimized) "BÜYÜT" else "KÜÇÜLT", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
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
            .background(Color(0xFF15131C))
            .border(1.dp, Color(0xFF2E2A3A), RoundedCornerShape(16.dp))
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
                checkedThumbColor = Color(0xFF1B1627),
                checkedTrackColor = Color(0xFFA58BFF),
                uncheckedThumbColor = Color.LightGray,
                uncheckedTrackColor = Color(0xFF2E2A3A)
            )
        )
    }
}

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
                text = "YAZICI GÖNDERİM GEÇMİŞİ",
                color = Color(0xFFA58BFF),
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
                    .border(1.dp, Color(0xFF2E2A3A), RoundedCornerShape(20.dp))
                    .background(Color(0xFF15131C)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text("⌛", fontSize = 32.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Yazıcı Geçmişi Boş", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Arka planda gönderilen spamlere dair detaylar bittikten sonra buraya eklenir.", color = Color(0xFFCAC4D0), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
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
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF15131C)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF2E2A3A))
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
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${log.count} kere tekrarlandı  •  ${dateFormat.format(Date(log.timestamp))}",
                                    color = Color(0xFFCAC4D0),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            IconButton(
                                onClick = { onCopyPhrase(log.phraseText) },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF2E2A3A)),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Tekrar Seç", tint = Color(0xFFA58BFF), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    return enabledServices?.contains(context.packageName) == true
}
