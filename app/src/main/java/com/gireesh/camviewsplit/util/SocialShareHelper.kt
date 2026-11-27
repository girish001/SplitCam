package com.gireesh.camviewsplit.util

import android.content.ActivityNotFoundException
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
        if (videoUri.scheme == "file") {
            Toast.makeText(context, "Cannot share raw file URI. Please save to Gallery first.", Toast.LENGTH_LONG).show()
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, videoUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            val targetedIntent = Intent(shareIntent).apply {
                setPackage(platform.packageName)
            }

            val pm = context.packageManager
            if (targetedIntent.resolveActivity(pm) != null) {
                Toast.makeText(
                    context,
                    "Opening ${platform.displayName}... Complete your post inside the app.",
                    Toast.LENGTH_LONG
                ).show()
                context.startActivity(targetedIntent)
            } else {
                Toast.makeText(
                    context,
                    "${platform.displayName} app is not installed. Opening system share options...",
                    Toast.LENGTH_SHORT
                ).show()
                val chooser = Intent.createChooser(shareIntent, "Share video to ${platform.displayName}")
                context.startActivity(chooser)
            }
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "${platform.displayName} app not found. Opening system share options...",
                Toast.LENGTH_SHORT
            ).show()
            val chooser = Intent.createChooser(shareIntent, "Share video to ${platform.displayName}")
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open share intent: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun shareVideoGeneric(context: Context, videoUri: Uri) {
        if (videoUri.scheme == "file") {
            Toast.makeText(context, "Cannot share raw file URI. Please save to Gallery first.", Toast.LENGTH_LONG).show()
            return
        }

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
