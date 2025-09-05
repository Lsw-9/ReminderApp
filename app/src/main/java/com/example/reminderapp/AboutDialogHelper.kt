package com.example.reminderapp

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.Window
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.Toast
import android.content.Intent
import android.net.Uri

class AboutDialogHelper {
    fun showAboutDialog(context: Context) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_about, null)

        val tvEmail = view.findViewById<TextView>(R.id.tv_email)
        tvEmail.setOnClickListener {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:${tvEmail.text}")
            }
            try {
                context.startActivity(emailIntent)
            } catch (e: Exception) {
                Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.setContentView(view)

        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        dialog.show()
    }
}
