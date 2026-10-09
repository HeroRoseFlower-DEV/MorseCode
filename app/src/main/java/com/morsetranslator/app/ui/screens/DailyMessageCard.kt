package com.morsetranslator.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.morsetranslator.app.R
import com.morsetranslator.app.daily.DailyMessages
import com.morsetranslator.app.data.SettingsRepository
import com.morsetranslator.app.morse.MorseCode
import com.morsetranslator.app.morse.MorsePlayer
import com.morsetranslator.app.morse.Output
import com.morsetranslator.app.morse.PersianMorse
import com.morsetranslator.app.morse.PlaybackSettings
import com.morsetranslator.app.ui.rememberPlaybackController
import com.morsetranslator.app.ui.theme.CalmCard
import com.morsetranslator.app.ui.theme.CalmIconButton
import com.morsetranslator.app.ui.theme.MorseText
import com.morsetranslator.app.ui.theme.SectionTitle
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Offline daily motivational message.
 *
 * Shows the Persian message with its Morse encoding from the verified
 * Persian profile, using the same playback engine and timing as the
 * translator. The message is deterministic per local date (see
 * [DailyMessages]); dismissing hides it until the next day.
 */
@Composable
fun DailyMessageCard(
    repository: SettingsRepository,
    player: MorsePlayer,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val playback = rememberPlaybackController(player)

    val dismissedDate by repository.dailyDismissedDate.collectAsState(initial = null)
    val todayIso = remember { LocalDate.now().toString() }
    if (dismissedDate == todayIso) return

    val message = remember { DailyMessages.today() }
    val wpm by repository.wpm.collectAsState(initial = SettingsRepository.DEFAULT_WPM)
    val toneHz by repository.toneHz.collectAsState(initial = SettingsRepository.DEFAULT_TONE_HZ)

    val encoded = remember(message) {
        MorseCode.encodeDetailed(message.fa, PersianMorse.PROFILE).morse
    }

    CalmCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionTitle(stringResource(R.string.daily_title))
            IconButton(
                onClick = { scope.launch { repository.setDailyDismissed(todayIso) } }
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_dismiss)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            message.fa,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
        )
        Text(
            message.en,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        MorseText(
            encoded,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        if (playback.isPlaying) {
            LinearProgressIndicator(
                progress = { playback.progress },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CalmIconButton(
                icon = if (playback.isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                description = stringResource(
                    if (playback.isPlaying) R.string.action_stop else R.string.daily_play
                ),
                onClick = {
                    playback.play(
                        morse = encoded,
                        settings = PlaybackSettings(wpm, toneHz),
                        outputs = setOf(Output.SOUND),
                        profile = PersianMorse.PROFILE
                    )
                }
            )
            CalmIconButton(
                icon = Icons.Filled.ContentCopy,
                description = stringResource(R.string.action_copy),
                onClick = {
                    clipboard.setText(AnnotatedString("${message.fa}\n$encoded"))
                }
            )
            CalmIconButton(
                icon = Icons.Filled.Share,
                description = stringResource(R.string.action_share),
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "${message.fa}\n$encoded")
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }
            )
            Spacer(Modifier.width(4.dp))
            if (playback.isPlaying && playback.totalMs > 0) {
                Text(
                    stringResource(
                        R.string.progress_format,
                        MorseCode.formatDuration(playback.elapsedMs),
                        MorseCode.formatDuration(playback.totalMs)
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
