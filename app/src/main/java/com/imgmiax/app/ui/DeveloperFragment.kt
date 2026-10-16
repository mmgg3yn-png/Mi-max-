package com.imgmiax.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.imgmiax.app.R

class DeveloperFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_developer, container, false)

        val cardEmail = v.findViewById<MaterialCardView>(R.id.cardEmail)
        val cardTelegram = v.findViewById<MaterialCardView>(R.id.cardTelegram)

        cardEmail.setOnClickListener {
            try {
                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:mmgg3yn@gmail.com")
                    putExtra(Intent.EXTRA_SUBJECT, "IMGmiax - استفسار أو اقتراح")
                }
                startActivity(emailIntent)
            } catch (e: Exception) {
                // Fallback to viewing mailto or copying
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:mmgg3yn@gmail.com"))
                    startActivity(browserIntent)
                } catch (ex: Exception) {
                    Toast.makeText(context, "البريد الإلكتروني: mmgg3yn@gmail.com", Toast.LENGTH_LONG).show()
                }
            }
        }

        cardTelegram.setOnClickListener {
            try {
                // Try opening Telegram app first
                val tgIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=Mi_Max12"))
                startActivity(tgIntent)
            } catch (e: Exception) {
                // Fallback to web link https://t.me/Mi_Max12
                try {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Mi_Max12"))
                    startActivity(webIntent)
                } catch (ex: Exception) {
                    Toast.makeText(context, "قناة تيليجرام: @Mi_Max12", Toast.LENGTH_LONG).show()
                }
            }
        }

        return v
    }
}
