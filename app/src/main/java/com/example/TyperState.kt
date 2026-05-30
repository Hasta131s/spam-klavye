package com.example

import kotlinx.coroutines.flow.MutableStateFlow

object TyperState {
    val activePhrase = MutableStateFlow<Phrase?>(null)
    val isRunning = MutableStateFlow(false)
}
