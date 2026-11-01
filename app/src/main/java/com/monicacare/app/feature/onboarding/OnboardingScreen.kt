package com.monicacare.app.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monicacare.app.core.designsystem.theme.LightBlush
import com.monicacare.app.core.designsystem.theme.RosePinkPrimary
import com.monicacare.app.core.designsystem.theme.TealSecondary

data class OnboardingPage(
    val title: String,
    val description: String,
    val emojiIcon: String
)

private val onboardingPages = listOf(
    OnboardingPage(
        title = "Welcome to MonicaCare",
        description = "Empowering mothers with accessible, compassionate maternal and postnatal healthcare guidance.",
        emojiIcon = "🌸"
    ),
    OnboardingPage(
        title = "Track Your Pregnancy Journey",
        description = "Follow weekly baby growth, monitor milestones, and stay prepared for every trimester.",
        emojiIcon = "👶"
    ),
    OnboardingPage(
        title = "Your Health, Our Priority",
        description = "Manage antenatal care visits, log vital signs, and access trusted emergency guidance whenever needed.",
        emojiIcon = "🏥"
    )
)

@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit
) {
    var currentPage by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Skip Button Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            if (currentPage < onboardingPages.size - 1) {
                TextButton(onClick = onOnboardingComplete) {
                    Text(
                        text = "Skip",
                        style = MaterialTheme.typography.labelLarge,
                        color = TealSecondary
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }

        Spacer(modifier = Modifier.weight(0.5f))

        // Onboarding Content
        val page = onboardingPages[currentPage]

        Surface(
            shape = CircleShape,
            color = LightBlush,
            modifier = Modifier.size(160.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = page.emojiIcon,
                    fontSize = 72.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = RosePinkPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        // Page Indicator
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            onboardingPages.indices.forEach { index ->
                val isSelected = index == currentPage
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .height(8.dp)
                        .width(if (isSelected) 24.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) RosePinkPrimary else LightBlush)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentPage > 0) {
                OutlinedButton(
                    onClick = { currentPage-- },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Back")
                }
            } else {
                Spacer(modifier = Modifier.width(80.dp))
            }

            Button(
                onClick = {
                    if (currentPage < onboardingPages.size - 1) {
                        currentPage++
                    } else {
                        onOnboardingComplete()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = RosePinkPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (currentPage == onboardingPages.size - 1) "Get Started" else "Next")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
