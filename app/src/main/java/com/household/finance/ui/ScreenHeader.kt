package com.household.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.household.finance.ui.theme.InkRaised
import com.household.finance.ui.theme.Violet

/** Shared top row used on every screen: avatar + greeting on the left, a static bucket badge on the right. */
@Composable
fun ScreenHeader(nameMe: String, bucketLabel: String = "Joint") {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).background(Violet, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(nameMe.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Good to see you", style = MaterialTheme.typography.bodySmall)
                Text(nameMe, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Box(
            modifier = Modifier
                .background(InkRaised, RoundedCornerShape(999.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(bucketLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}
