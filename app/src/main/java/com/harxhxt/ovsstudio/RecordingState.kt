package com.harxhxt.ovsstudio

enum class RecordingStatus {
    IDLE,
    RECORDING,
    PAUSED,
    STOPPED,
    ERROR
}

data class RecordingState(
    val status: RecordingStatus = RecordingStatus.IDLE,
    val elapsedSeconds: Long = 0L,
    val message: String = ""
)
