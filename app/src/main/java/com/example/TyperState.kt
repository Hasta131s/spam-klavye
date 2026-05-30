package com.example

import kotlinx.coroutines.flow.MutableStateFlow

object TyperState {
    val activePhrase = MutableStateFlow<Phrase?>(null)
    val isRunning = MutableStateFlow(false)
    
    // Core functional options
    val autoSend = MutableStateFlow(true)
    val learnMode = MutableStateFlow(true)
    val floatingAlpha = MutableStateFlow(0.9f)
    val isMinimized = MutableStateFlow(false)
    
    // Guide/Tutorial State (0 refers to step 1, -1 means dismissed)
    val tutorialStep = MutableStateFlow(0)
    
    // Helper to store last learned string temporarily
    val lastLearnedText = MutableStateFlow("")
}
