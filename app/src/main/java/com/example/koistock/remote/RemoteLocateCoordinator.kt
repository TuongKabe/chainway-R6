package com.example.koistock.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class RemoteLocateCoordinator(
    private val store: HandledCommandStore,
    private val scope: CoroutineScope,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
) {
    private val _commands = MutableSharedFlow<RemoteLocateCommand>(
        replay = 1,
        extraBufferCapacity = 4,
    )
    val commands: SharedFlow<RemoteLocateCommand> = _commands.asSharedFlow()

    suspend fun submit(command: RemoteLocateCommand): SubmissionResult {
        if (command.isExpired(nowEpochMs())) {
            return SubmissionResult.Rejected("Command expired")
        }
        if (store.isHandled(command.commandId)) {
            return SubmissionResult.Duplicate
        }
        store.markHandled(command.commandId)
        scope.launch {
            _commands.emit(command)
        }
        return SubmissionResult.Accepted
    }

    sealed class SubmissionResult {
        data object Accepted : SubmissionResult()
        data object Duplicate : SubmissionResult()
        data class Rejected(val reason: String) : SubmissionResult()
    }
}
