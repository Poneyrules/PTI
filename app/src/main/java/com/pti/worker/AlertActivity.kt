package com.pti.worker

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pti.worker.core.PtiState
import com.pti.worker.ui.theme.PtiRed
import com.pti.worker.ui.theme.PtiTheme

/** Écran minimal destiné à présenter immédiatement une pré-alerte ou une alerte. */
class AlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableAlertWindow()

        val ptiManager = (application as PtiApplication).ptiManager
        setContent {
            val state by ptiManager.stateMachine.state.collectAsState()
            val isAlertActive = state == PtiState.PRE_ALERT || state == PtiState.ALERT

            LaunchedEffect(isAlertActive) {
                if (!isAlertActive) finish()
            }

            PtiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (state == PtiState.PRE_ALERT) "PRÉ-ALERTE" else "ALERTE PTI",
                            color = PtiRed,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (state == PtiState.PRE_ALERT) {
                                "Confirmez que vous allez bien."
                            } else {
                                "Une alerte a été déclenchée. Appuyez pour l'acquitter et terminer l'appel."
                            },
                            modifier = Modifier.padding(top = 20.dp, bottom = 36.dp),
                            fontSize = 20.sp,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = {
                                ptiManager.acknowledgeAlert()
                                finish()
                            },
                            modifier = Modifier.widthIn(min = 240.dp)
                        ) {
                            Text(
                                text = if (state == PtiState.PRE_ALERT) "JE SUIS OK" else "ACQUITTER",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun enableAlertWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
