package com.morsetranslator.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.morsetranslator.app.R
import com.morsetranslator.app.morse.IssueReason
import com.morsetranslator.app.morse.Output
import com.morsetranslator.app.morse.OutputIssue

/**
 * Localized, human-readable description of a playback issue.
 * Always names the affected output — issues are never reported by
 * color or icon alone.
 */
@Composable
fun issueText(issue: OutputIssue): String {
    val outputName = when (issue.output) {
        Output.SOUND -> stringResource(R.string.output_sound)
        Output.FLASH -> stringResource(R.string.output_flash)
        Output.VIBRATION -> stringResource(R.string.output_vibration)
    }
    val reason = when (issue.reason) {
        IssueReason.NO_SIGNALS -> stringResource(R.string.pb_no_signals)
        IssueReason.NO_FLASH_HARDWARE -> stringResource(R.string.pb_no_flash_hw)
        IssueReason.FLASH_PERMISSION_DENIED -> stringResource(R.string.pb_flash_denied)
        IssueReason.FLASH_ERROR -> stringResource(R.string.pb_flash_error)
        IssueReason.AUDIO_ERROR -> stringResource(R.string.pb_audio_error)
        IssueReason.NO_VIBRATOR -> stringResource(R.string.pb_no_vibrator)
        IssueReason.VIBRATION_TOO_LONG -> stringResource(R.string.pb_vibration_too_long)
    }
    return "$outputName: $reason"
}
