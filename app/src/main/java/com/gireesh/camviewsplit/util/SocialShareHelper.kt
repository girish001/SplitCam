package com.gireesh.camviewsplit.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

enum class SocialPlatform(val displayName: String, val packageName: String) {
    INSTAGRAM("Instagram", "com.instagram.android"),
    FACEBOOK("Facebook", "com.facebook.katana"),
    YOUTUBE("YouTube", "com.google.android.youtube")
}

object SocialShareHelper {

    fun shareVideoToPlatform(context: Context, videoUri: Uri, platform: SocialPlatform) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, videoUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(platform.packageName)

            if (launchIntent != null) {
                shareIntent.setPackage(platform.packageName)
                context.startActivity(shareIntent)
            } else {
                Toast.makeText(
                    context,
                    "${platform.displayName} app is not installed. Opening Share Sheet...",
                    Toast.LENGTH_SHORT
                ).show()
                val chooser = Intent.createChooser(shareIntent, "Share video to ${platform.displayName}")
                context.startActivity(chooser)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open share dialog: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun shareVideoGeneric(context: Context, videoUri: Uri) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, videoUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Video")
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Share error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
