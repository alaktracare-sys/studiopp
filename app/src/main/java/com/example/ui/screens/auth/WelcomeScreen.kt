package com.example.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun WelcomeScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToSignup: () -> Unit,
    onContinueAsGuest: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D1B2A),
                        AlaktraBackground,
                        AlaktraBackground
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.weight(0.8f))

            // Brand Logo / Icon Badge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(AlaktraMint, AlaktraCyan)))
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Alaktra Logo",
                    tint = Color(0xFF041C12),
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "ALAKTRA",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 6.sp
                ),
                color = AlaktraTextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Private high-fidelity streaming powered by your Tailscale mesh server",
                style = MaterialTheme.typography.bodyLarge,
                color = AlaktraTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.weight(1.2f))

            // Sign Up Button
            Button(
                onClick = onNavigateToSignup,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AlaktraMint,
                    contentColor = Color(0xFF041C12)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    text = "Get Started",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Login Button
            OutlinedButton(
                onClick = onNavigateToLogin,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AlaktraTextPrimary),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.linearGradient(listOf(AlaktraBorder, AlaktraBorder))
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    text = "Sign In with Existing Account",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Continue as guest
            TextButton(onClick = onContinueAsGuest) {
                Text(
                    text = "Continue Offline / Browse as Guest",
                    color = AlaktraTextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

