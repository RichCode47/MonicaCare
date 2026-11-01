package com.monicacare.app.feature.education

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monicacare.app.core.designsystem.component.MonicaCareCard
import com.monicacare.app.core.designsystem.theme.LightBlush
import com.monicacare.app.core.designsystem.theme.RosePinkPrimary
import com.monicacare.app.core.designsystem.theme.TealSecondary

data class EducationCategory(val name: String, val articleCount: Int)

private val categories = listOf(
    EducationCategory("Nutrition & Diet", 12),
    EducationCategory("Exercise & Wellness", 8),
    EducationCategory("Antenatal Care Visits", 10),
    EducationCategory("Pregnancy Symptoms", 15),
    EducationCategory("Labour & Birth Prep", 9),
    EducationCategory("Postnatal Recovery", 7),
    EducationCategory("Newborn Care & Feeding", 14)
)

@Composable
fun EducationScreen() {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Pregnancy Education",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        categories.forEach { category ->
            MonicaCareCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { /* Open Category */ }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = LightBlush
                    ) {
                        Text(
                            text = "${category.articleCount} articles",
                            style = MaterialTheme.typography.labelSmall,
                            color = RosePinkPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
