package com.gireesh.camviewsplit.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gireesh.camviewsplit.model.DualVideoCompositionConfig
import com.gireesh.camviewsplit.ui.theme.CyanPrimary
import com.gireesh.camviewsplit.ui.theme.DarkBorder
import com.gireesh.camviewsplit.ui.theme.DarkSurface
import com.gireesh.camviewsplit.ui.theme.DarkSurfaceElevated
import com.gireesh.camviewsplit.ui.theme.TealAccent
import com.gireesh.camviewsplit.ui.theme.TextPrimary
import com.gireesh.camviewsplit.ui.theme.TextSecondary
import com.gireesh.camviewsplit.util.SocialPlatform
import com.gireesh.camviewsplit.util.SocialShareHelper

@Composable
fun SocialMediaPanel(
    exportedVideoUri: Uri?,
    topMediaUri: Uri? = null,
    bottomMediaUri: Uri? = null,
    config: DualVideoCompositionConfig? = null,
    onExportWithDimensions: ((width: Int, height: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Social Media Export & Sharing",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Platform Cards with Direct Share Flow
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SocialPlatformShareCard(
                platform = SocialPlatform.INSTAGRAM,
                exportedVideoUri = exportedVideoUri,
                onShareClick = {
                    if (exportedVideoUri != null) {
                        SocialShareHelper.shareVideoToPlatform(context, exportedVideoUri, SocialPlatform.INSTAGRAM)
                    } else {
                        Toast.makeText(context, "Please record or export a video first.", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            SocialPlatformShareCard(
                platform = SocialPlatform.FACEBOOK,
                exportedVideoUri = exportedVideoUri,
                onShareClick = {
                    if (exportedVideoUri != null) {
                        SocialShareHelper.shareVideoToPlatform(context, exportedVideoUri, SocialPlatform.FACEBOOK)
                    } else {
                        Toast.makeText(context, "Please record or export a video first.", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            SocialPlatformShareCard(
                platform = SocialPlatform.YOUTUBE,
                exportedVideoUri = exportedVideoUri,
                onShareClick = {
                    if (exportedVideoUri != null) {
                        SocialShareHelper.shareVideoToPlatform(context, exportedVideoUri, SocialPlatform.YOUTUBE)
                    } else {
                        Toast.makeText(context, "Please record or export a video first.", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Generic Share Sheet Button
        Button(
            onClick = {
                if (exportedVideoUri != null) {
                    SocialShareHelper.shareVideoGeneric(context, exportedVideoUri)
                } else {
                    Toast.makeText(context, "Please record or export a video first.", Toast.LENGTH_SHORT).show()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = TealAccent,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Share via System Share Sheet",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Notice Note
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurfaceElevated, RoundedCornerShape(10.dp))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = CyanPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Secure Sharing: SplitCam uses Android's native share flow to target official platform apps. Password entry is never required.",
                color = TextSecondary,
                fontSize = 10.sp,
                lineHeight = 12.sp
            )
        }
    }
}

@Composable
private fun SocialPlatformShareCard(
    platform: SocialPlatform,
    exportedVideoUri: Uri?,
    onShareClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(DarkSurface, CircleShape)
                    .border(1.dp, CyanPrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = platform.displayName,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (exportedVideoUri != null) "Ready for Sharing" else "Record a video to share",
                    color = if (exportedVideoUri != null) TealAccent else Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Button(
            onClick = onShareClick,
            enabled = exportedVideoUri != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanPrimary,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Share to ${platform.displayName}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
