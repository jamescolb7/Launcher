package com.jamescolb.launcher.ui.signin

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.jamescolb.launcher.LauncherViewModel
import com.jamescolb.launcher.R
import com.jamescolb.launcher.SignInStep
import com.jamescolb.launcher.ui.components.AvatarTile
import com.jamescolb.launcher.ui.components.launcherButtonColors
import com.jamescolb.launcher.ui.components.launcherOutlinedButtonColors
import com.jamescolb.launcher.ui.components.DrawableIcon
import com.jamescolb.launcher.ui.components.LauncherTextField
import com.jamescolb.launcher.ui.theme.Inter
import com.jamescolb.launcher.ui.theme.LocalAccent
import com.jamescolb.launcher.ui.theme.TextMuted
import com.jamescolb.launcher.ui.theme.TextPrimary
import com.jamescolb.launcher.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/** Add a Jellyfin server and/or user: address -> pick user -> password or Quick Connect. */
@Composable
fun SignInScreen(viewModel: LauncherViewModel, onClose: () -> Unit) {
    val step by viewModel.signIn.collectAsStateWithLifecycle()

    BackHandler { if (!viewModel.signInBack()) onClose() }
    LaunchedEffect(step) { if (step is SignInStep.Done) onClose() }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = step,
            contentKey = { it::class },
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "signInStep",
        ) { current ->
            when (current) {
                is SignInStep.EnterServer -> ServerStep(current, onConnect = viewModel::connect)
                is SignInStep.ChooseUser -> UserStep(
                    step = current,
                    avatarUrl = { user ->
                        user.primaryImageTag?.let { viewModel.client.userImageUrl(current.server.url, user.id, it) }
                    },
                    onUser = viewModel::choosePublicUser,
                    onManual = viewModel::chooseManualLogin,
                    onQuickConnect = viewModel::startQuickConnect,
                )
                is SignInStep.Password -> PasswordStep(current, onSignIn = viewModel::signInWithPassword)
                is SignInStep.QuickConnect -> QuickConnectStep(current, onRetry = viewModel::startQuickConnect)
                SignInStep.Done -> Unit
            }
        }
    }
}

@Composable
private fun StepPanel(title: String, subtitle: String?, content: @Composable () -> Unit) {
    Column(
        Modifier
            .width(560.dp)
            .padding(vertical = 24.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp), color = TextPrimary)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Spacer(Modifier.height(28.dp))
        content()
    }
}

@Composable
private fun ErrorText(error: String?) {
    if (error != null) {
        Spacer(Modifier.height(12.dp))
        Text(error, style = MaterialTheme.typography.bodyMedium, color = androidx.compose.ui.graphics.Color(0xFFFF8A80))
    }
}

@Composable
private fun AutoFocus(requester: FocusRequester) {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
}

@Composable
private fun ServerStep(step: SignInStep.EnterServer, onConnect: (String) -> Unit) {
    var url by remember { mutableStateOf(step.url) }
    val focus = remember { FocusRequester() }
    AutoFocus(focus)
    StepPanel(
        title = "Connect to Jellyfin",
        subtitle = "Enter your server's address, for example 192.168.1.10:8096 or https://jellyfin.example.com",
    ) {
        LauncherTextField(
            value = url,
            onValueChange = { url = it },
            label = "Server address",
            placeholder = "http://",
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
            onImeAction = { onConnect(url) },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        ErrorText(step.error)
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { onConnect(url) }, enabled = !step.busy, colors = launcherButtonColors()) { Text("Connect") }
            if (step.busy) {
                Spacer(Modifier.width(16.dp))
                Text("Connecting…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun UserStep(
    step: SignInStep.ChooseUser,
    avatarUrl: (com.jamescolb.launcher.data.jellyfin.UserDto) -> String?,
    onUser: (com.jamescolb.launcher.data.jellyfin.UserDto) -> Unit,
    onManual: () -> Unit,
    onQuickConnect: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    AutoFocus(focus)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Who's signing in?", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp), color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            text = listOfNotNull(step.server.name, step.version?.let { "Jellyfin $it" }, step.server.url).joinToString("  •  "),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(36.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        ) {
            items(step.publicUsers, key = { it.id }) { user ->
                AvatarTile(
                    label = user.name,
                    imageUrl = avatarUrl(user),
                    onClick = { onUser(user) },
                    modifier = if (user == step.publicUsers.first()) Modifier.focusRequester(focus) else Modifier,
                )
            }
            if (step.quickConnectEnabled) {
                item(key = "qc") {
                    AvatarTile(
                        label = "Quick Connect",
                        onClick = onQuickConnect,
                        modifier = if (step.publicUsers.isEmpty()) Modifier.focusRequester(focus) else Modifier,
                        icon = { DrawableIcon(R.drawable.ic_link, tint = TextPrimary, size = 36.dp) },
                    )
                }
            }
            item(key = "manual") {
                AvatarTile(
                    label = "Enter username",
                    onClick = onManual,
                    modifier = if (step.publicUsers.isEmpty() && !step.quickConnectEnabled) {
                        Modifier.focusRequester(focus)
                    } else {
                        Modifier
                    },
                    icon = { DrawableIcon(R.drawable.ic_person, tint = TextPrimary, size = 36.dp) },
                )
            }
        }
        ErrorText(step.error)
        if (!step.quickConnectEnabled) {
            Spacer(Modifier.height(20.dp))
            Text(
                "Quick Connect is turned off on this server (Dashboard → General → Quick Connect).",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
    }
}

@Composable
private fun PasswordStep(step: SignInStep.Password, onSignIn: (String, String) -> Unit) {
    var username by remember { mutableStateOf(step.username) }
    var password by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    AutoFocus(focus)
    StepPanel(
        title = if (step.fixedUser) "Hi, ${step.username}" else "Sign in",
        subtitle = step.server.name,
    ) {
        if (!step.fixedUser) {
            LauncherTextField(
                value = username,
                onValueChange = { username = it },
                label = "Username",
                imeAction = ImeAction.Next,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Spacer(Modifier.height(16.dp))
        }
        LauncherTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            password = true,
            imeAction = ImeAction.Done,
            onImeAction = { onSignIn(username, password) },
            modifier = Modifier
                .fillMaxWidth()
                .then(if (step.fixedUser) Modifier.focusRequester(focus) else Modifier),
        )
        ErrorText(step.error)
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { onSignIn(username, password) }, enabled = !step.busy, colors = launcherButtonColors()) { Text("Sign in") }
            if (step.busy) {
                Spacer(Modifier.width(16.dp))
                Text("Signing in…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun QuickConnectStep(step: SignInStep.QuickConnect, onRetry: () -> Unit) {
    val accent = LocalAccent.current
    var dots by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            dots = (dots + 1) % 4
        }
    }
    val retryFocus = remember { FocusRequester() }
    StepPanel(
        title = "Quick Connect",
        subtitle = "On a device already signed in to ${step.server.name}, open your profile → Quick Connect and enter this code.",
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(accent.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(vertical = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = step.code?.chunked(3)?.joinToString("  ") ?: "···  ···",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = Inter,
                    fontWeight = FontWeight.Bold,
                    fontSize = 48.sp,
                    letterSpacing = 6.sp,
                ),
                color = accent,
            )
        }
        Spacer(Modifier.height(14.dp))
        if (step.error == null) {
            Text(
                text = if (step.code == null) "Requesting a code…" else "Waiting for approval" + ".".repeat(dots),
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
            )
        } else {
            ErrorText(step.error)
            Spacer(Modifier.height(20.dp))
            AutoFocus(retryFocus)
            OutlinedButton(onClick = onRetry, modifier = Modifier.focusRequester(retryFocus), colors = launcherOutlinedButtonColors()) { Text("Try again") }
        }
    }
}
