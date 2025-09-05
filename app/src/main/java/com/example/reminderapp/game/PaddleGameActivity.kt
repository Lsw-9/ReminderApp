package com.example.reminderapp.game

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.reminderapp.R
import com.example.reminderapp.AIChatActivity
import com.example.reminderapp.databinding.ActivityPaddleGameBinding
import android.view.WindowManager

class PaddleGameActivity : AppCompatActivity(), PaddleGameView.GameListener {

    private lateinit var binding: ActivityPaddleGameBinding
    private var gameResult: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaddleGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.gameView.gameListener = this

        showInstructionsDialog()
    }

    private fun showInstructionsDialog() {
        val dialog = Dialog(this, R.style.DialogTheme)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setContentView(R.layout.dialog_game_instructions)


        val window = dialog.window
        window?.let {
            val layoutParams = WindowManager.LayoutParams()
            layoutParams.copyFrom(it.attributes)
            layoutParams.width = (resources.displayMetrics.widthPixels * 0.9).toInt()
            it.attributes = layoutParams
            it.setBackgroundDrawableResource(android.R.color.transparent)
        }

        val okButton = dialog.findViewById<Button>(R.id.okButton)
        okButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onGameOver(userScore: Int, aiScore: Int) {
        // Determine game result
        gameResult = when {
            userScore > aiScore -> "WIN"
            aiScore > userScore -> "LOSE"
            else -> "TIE"
        }

        val dialog = Dialog(this, R.style.DialogTheme)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setContentView(R.layout.dialog_game_over)

        val window = dialog.window
        window?.let {
            val layoutParams = WindowManager.LayoutParams()
            layoutParams.copyFrom(it.attributes)
            layoutParams.width = (resources.displayMetrics.widthPixels * 0.9).toInt()
            it.attributes = layoutParams
            it.setBackgroundDrawableResource(android.R.color.transparent)
        }


        val resultText = dialog.findViewById<TextView>(R.id.resultText)
        val resultIcon = dialog.findViewById<ImageView>(R.id.resultIcon)


        val userScoreText = dialog.findViewById<TextView>(R.id.userScoreText)
        val aiScoreText = dialog.findViewById<TextView>(R.id.aiScoreText)
        userScoreText.text = userScore.toString()
        aiScoreText.text = aiScore.toString()

        // Set result message and icon
        when {
            userScore > aiScore -> {
                resultText.text = "You Win!\nScore: $userScore - $aiScore"
                resultIcon.setImageResource(android.R.drawable.btn_star_big_on)
            }
            aiScore > userScore -> {
                resultText.text = "AI Wins!\nScore: $userScore - $aiScore"
                resultIcon.setImageResource(R.drawable.ic_chat_bot_28)
            }
            else -> {
                resultText.text = "It's a Tie!\nScore: $userScore - $aiScore"
                resultIcon.setImageResource(android.R.drawable.ic_menu_share)
                resultIcon.setColorFilter(ContextCompat.getColor(this, R.color.blue_200))
            }
        }


        val continueButton = dialog.findViewById<Button>(R.id.continueButton)
        val exitButton = dialog.findViewById<Button>(R.id.exitButton)

        continueButton.setOnClickListener {
            dialog.dismiss()
            binding.gameView.restartGame()
            // Reset game result since we're continuing
            gameResult = ""
        }

        exitButton.setOnClickListener {
            dialog.dismiss()
            returnToChat()
        }

        dialog.show()
    }

    private fun returnToChat() {
        // Return to chat activity with game result
        val intent = Intent(this, AIChatActivity::class.java)
        intent.putExtra("GAME_RESULT", gameResult)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        intent.putExtra("PRESERVE_ICON", true)

        startActivity(intent)
        finish()
    }

    override fun onPause() {
        super.onPause()
        binding.gameView.stopGame()
    }

    override fun onBackPressed() {
        // Show confirmation dialog
        val dialog = Dialog(this, R.style.DialogTheme)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setContentView(R.layout.dialog_exit_game)

        val window = dialog.window
        window?.let {
            val layoutParams = WindowManager.LayoutParams()
            layoutParams.copyFrom(it.attributes)
            layoutParams.width = (resources.displayMetrics.widthPixels * 0.9).toInt()
            it.attributes = layoutParams
            it.setBackgroundDrawableResource(android.R.color.transparent)
        }

        val yesButton = dialog.findViewById<Button>(R.id.yesButton)
        val noButton = dialog.findViewById<Button>(R.id.noButton)

        yesButton.setOnClickListener {
            dialog.dismiss()
            returnToChat()
        }

        noButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}