package com.example.reminderapp.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.example.reminderapp.R
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class PaddleGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Game objects
    private val userPaddle = RectF()
    private val aiPaddle = RectF()
    private val ball = RectF()

    // Game dimensions
    private val paddleWidth = 180f
    private val paddleHeight = 36f
    private val ballRadius = 24f

    // Game state
    private var ballSpeedX = 12f
    private var ballSpeedY = 12f
    private var baseSpeed = 12f
    private var maxBallSpeed = 35f // Maximum ball speed
    private var userScore = 0
    private var aiScore = 0
    private var gameStarted = false
    private var gameOver = false
    private var gameTime = 180000L // 3 minutes in milliseconds
    private var timeRemaining = gameTime
    private var waitingForTouch = false

    // AI difficulty
    private var aiDifficultyFactor = 0.80f // Higher value = more difficult (max 1.0)
    private var aiPredictionFactor = 0.9f // How well AI predicts ball trajectory

    // Visual elements
    private val aiIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_chat_bot_28)
    private val backgroundGradientTop = intArrayOf(Color.parseColor("#000000"), Color.parseColor("#222222"))
    private val backgroundGradientBottom = intArrayOf(Color.parseColor("#000000"), Color.parseColor("#222222"))
    private var backgroundGradientPaint = Paint()

    // Paint objects
    private val userPaddlePaint = Paint().apply {
        color = Color.parseColor("#4CAF50") // Green
        style = Paint.Style.FILL
        setShadowLayer(10f, 0f, 5f, Color.parseColor("#388E3C"))
    }

    private val aiPaddlePaint = Paint().apply {
        color = Color.parseColor("#3F51B5") // Indigo
        style = Paint.Style.FILL
        setShadowLayer(10f, 0f, 5f, Color.parseColor("#303F9F"))
    }

    private val ballPaint = Paint().apply {
        color = Color.parseColor("#FFC107") // Amber
        style = Paint.Style.FILL
        setShadowLayer(8f, 0f, 0f, Color.parseColor("#FFA000"))
    }

    private val ballGlowPaint = Paint().apply {
        color = Color.parseColor("#80FFC107") // Semi-transparent amber
        style = Paint.Style.FILL
        setShadowLayer(16f, 0f, 0f, Color.parseColor("#FFA000"))
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 60f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(5f, 0f, 2f, Color.BLACK)
    }

    private val timerPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
        setShadowLayer(3f, 0f, 1f, Color.BLACK)
    }

    private val centerLinePaint = Paint().apply {
        color = Color.parseColor("#80FFFFFF") // Semi-transparent white
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    // Game timer
    private var gameTimer: CountDownTimer? = null

    // Game loop
    private val handler = Handler(Looper.getMainLooper())
    private val gameLoop = object : Runnable {
        override fun run() {
            if (gameStarted && !gameOver) {
                updateGame()
                invalidate()
                handler.postDelayed(this, 16) // ~60 FPS
            }
        }
    }

    // Ball trail effect
    private val trailPositions = mutableListOf<Pair<Float, Float>>()
    private val maxTrailLength = 5

    // Game listener
    var gameListener: GameListener? = null

    init {
        // Set up the game timer
        setupGameTimer()
    }

    private fun setupGameTimer() {
        gameTimer = object : CountDownTimer(gameTime, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemaining = millisUntilFinished
                invalidate()
            }

            override fun onFinish() {
                endGame()
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // Set up background gradient
        backgroundGradientPaint.shader = LinearGradient(
            width / 2f, 0f,
            width / 2f, height.toFloat(),
            backgroundGradientTop[0], backgroundGradientTop[1],
            Shader.TileMode.CLAMP
        )

        // Initialize game objects based on view size
        userPaddle.set(
            (width - paddleWidth) / 2,
            height - paddleHeight - 100f,
            (width + paddleWidth) / 2,
            height - 100f
        )

        aiPaddle.set(
            (width - paddleWidth) / 2,
            100f,
            (width + paddleWidth) / 2,
            100f + paddleHeight
        )

        ball.set(
            (width - ballRadius * 2) / 2,
            (height - ballRadius * 2) / 2,
            (width + ballRadius * 2) / 2,
            (height + ballRadius * 2) / 2
        )


    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawRect(0f, 0f, width.toFloat(), height / 2f, backgroundGradientPaint)

        backgroundGradientPaint.shader = LinearGradient(
            width / 2f, height / 2f,
            width / 2f, height.toFloat(),
            backgroundGradientBottom[0], backgroundGradientBottom[1],
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, height / 2f, width.toFloat(), height.toFloat(), backgroundGradientPaint)

        // Calculate AI paddle center
        val aiPaddleCenterX = (aiPaddle.left + aiPaddle.right) / 2

        // Update AI icon bounds to follow AI paddle
        aiIcon?.setBounds(
            (aiPaddleCenterX - 50).toInt(),  // Center icon on paddle
            10,                             // Keep at top
            (aiPaddleCenterX + 50).toInt(),  // Center icon on paddle
            110                             // Keep height the same
        )

        // Draw AI icon behind AI paddle
        aiIcon?.alpha = 255 // transparent level
        aiIcon?.draw(canvas)

        canvas.drawLine(0f, height / 2f, width.toFloat(), height / 2f, centerLinePaint)

        for (i in trailPositions.indices) {
            val alpha = 150 * (i + 1) / trailPositions.size
            ballGlowPaint.alpha = alpha
            val trailSize = ballRadius * (0.5f + (i.toFloat() / trailPositions.size) * 0.5f)
            val (x, y) = trailPositions[i]
            canvas.drawCircle(x, y, trailSize, ballGlowPaint)
        }

        // Draw paddles with rounded corners
        val cornerRadius = paddleHeight / 2
        canvas.drawRoundRect(userPaddle, cornerRadius, cornerRadius, userPaddlePaint)
        canvas.drawRoundRect(aiPaddle, cornerRadius, cornerRadius, aiPaddlePaint)

        // Draw ball with glow effect
        val ballCenterX = (ball.left + ball.right) / 2
        val ballCenterY = (ball.top + ball.bottom) / 2
        canvas.drawCircle(ballCenterX, ballCenterY, ballRadius * 1.2f, ballGlowPaint)
        canvas.drawCircle(ballCenterX, ballCenterY, ballRadius, ballPaint)

        // Draw scores
        canvas.drawText(
            "$userScore",
            width / 2f,
            height * 0.75f,
            textPaint
        )

        canvas.drawText(
            "$aiScore",
            width / 2f,
            height * 0.25f,
            textPaint
        )

        // Draw timer on left side
        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeRemaining)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(timeRemaining) % 60
        val timeText = String.format("%02d:%02d", minutes, seconds)
        canvas.drawText(
            timeText,
            30f,
            height / 2f + 15f,
            timerPaint
        )

        // Draw instructions if game not started
        if (!gameStarted && !gameOver) {
            val instructionPaint = Paint().apply {
                color = Color.WHITE
                textSize = 40f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
                setShadowLayer(5f, 0f, 2f, Color.BLACK)
            }
            canvas.drawText(
                "Touch your paddle to start",
                width / 2f,
                height / 2f,
                instructionPaint
            )
        }

        if (waitingForTouch && gameStarted && !gameOver) {
            val instructionPaint = Paint().apply {
                color = Color.WHITE
                textSize = 40f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
                setShadowLayer(5f, 0f, 2f, Color.BLACK)
            }
            canvas.drawText(
                "Touch paddle to continue",
                width / 2f,
                height / 2f,
                instructionPaint
            )
        }

        // Draw game over message
        if (gameOver) {
            val gameOverPaint = Paint().apply {
                color = Color.WHITE
                textSize = 70f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
                setShadowLayer(10f, 0f, 5f, Color.BLACK)
            }

            val resultText = if (userScore > aiScore) {
                "You Win!"
            } else if (aiScore > userScore) {
                "AI Wins!"
            } else {
                "It's a Tie!"
            }

            // Semi-transparent background
            val bgPaint = Paint().apply {
                color = Color.parseColor("#80000000")
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, height / 2f - 150f, width.toFloat(), height / 2f + 150f, bgPaint)

            canvas.drawText(
                "Game Over",
                width / 2f,
                height / 2f - 60f,
                gameOverPaint
            )

            canvas.drawText(
                resultText,
                width / 2f,
                height / 2f + 60f,
                gameOverPaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // Move user paddle
                if (!gameOver) {
                    val x = event.x
                    val halfPaddleWidth = paddleWidth / 2

                    val newX = when {
                        x - halfPaddleWidth < 0 -> halfPaddleWidth
                        x + halfPaddleWidth > width -> width - halfPaddleWidth
                        else -> x
                    }

                    userPaddle.left = newX - halfPaddleWidth
                    userPaddle.right = newX + halfPaddleWidth

                    // Start the game on first touch if not started
                    if (!gameStarted && !gameOver) {
                        startGame()
                    }
                    // Resume after a point if waiting for touch
                    else if (waitingForTouch && gameStarted && !gameOver && event.action == MotionEvent.ACTION_DOWN) {
                        // Calculate new ball speeds
                        val speedMultiplier = 1.0f + (max(userScore, aiScore) * 0.05f)
                        val initialSpeed = min(baseSpeed * speedMultiplier, maxBallSpeed * 0.7f)

                        ballSpeedX = if (Math.random() > 0.5) initialSpeed else -initialSpeed
                        ballSpeedY = if (Math.random() > 0.5) initialSpeed else -initialSpeed

                        waitingForTouch = false
                    }

                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startGame() {
        gameStarted = true
        gameOver = false
        userScore = 0
        aiScore = 0
        timeRemaining = gameTime
        trailPositions.clear()

        // Reset ball position
        ball.set(
            (width - ballRadius * 2) / 2,
            (height - ballRadius * 2) / 2,
            (width + ballRadius * 2) / 2,
            (height + ballRadius * 2) / 2
        )

        // For initial game start, set ball in motion immediately
        waitingForTouch = false
        val initialSpeed = baseSpeed
        ballSpeedX = if (Math.random() > 0.5) initialSpeed else -initialSpeed
        ballSpeedY = if (Math.random() > 0.5) initialSpeed else -initialSpeed

        // Start game loop
        handler.post(gameLoop)

        // Start timer
        gameTimer?.start()
    }

    private fun updateGame() {

        val ballCenterX = (ball.left + ball.right) / 2
        val ballCenterY = (ball.top + ball.bottom) / 2
        trailPositions.add(0, Pair(ballCenterX, ballCenterY))
        if (trailPositions.size > maxTrailLength) {
            trailPositions.removeAt(trailPositions.size - 1)
        }


        ball.offset(ballSpeedX, ballSpeedY)


        if (ball.left <= 0 || ball.right >= width) {
            ballSpeedX = -ballSpeedX
        }


        if (ball.bottom >= userPaddle.top && ball.top <= userPaddle.bottom &&
            ball.right >= userPaddle.left && ball.left <= userPaddle.right) {
            // Ball hit user paddle
            // Increase speed only if below max speed
            val currentSpeed = abs(ballSpeedY)
            val newSpeed = if (currentSpeed < maxBallSpeed) {
                currentSpeed * 1.1f
            } else {
                maxBallSpeed
            }
            ballSpeedY = -newSpeed // Negative because it's going up

            // Adjust horizontal direction based on where the ball hit the paddle
            val paddleCenter = (userPaddle.left + userPaddle.right) / 2
            val ballCenter = (ball.left + ball.right) / 2
            val hitPosition = (ballCenter - paddleCenter) / (paddleWidth / 2)
            ballSpeedX = hitPosition * max(abs(ballSpeedY) * 0.8f, baseSpeed)

            // Cap horizontal speed as well
            if (abs(ballSpeedX) > maxBallSpeed * 0.8f) {
                ballSpeedX = if (ballSpeedX > 0) maxBallSpeed * 0.8f else -maxBallSpeed * 0.8f
            }

        } else if (ball.top <= aiPaddle.bottom && ball.bottom >= aiPaddle.top &&
            ball.right >= aiPaddle.left && ball.left <= aiPaddle.right) {
            // Ball hit AI paddle
            // Increase speed only if below max speed
            val currentSpeed = abs(ballSpeedY)
            val newSpeed = if (currentSpeed < maxBallSpeed) {
                currentSpeed * 1.1f
            } else {
                maxBallSpeed
            }
            ballSpeedY = newSpeed

            // Adjust horizontal direction based on where the ball hit the paddle
            val paddleCenter = (aiPaddle.left + aiPaddle.right) / 2
            val ballCenter = (ball.left + ball.right) / 2
            val hitPosition = (ballCenter - paddleCenter) / (paddleWidth / 2)
            ballSpeedX = hitPosition * max(abs(ballSpeedY) * 0.8f, baseSpeed)

            // Cap horizontal speed as well
            if (abs(ballSpeedX) > maxBallSpeed * 0.8f) {
                ballSpeedX = if (ballSpeedX > 0) maxBallSpeed * 0.8f else -maxBallSpeed * 0.8f
            }
        }

        // Check if ball went past paddles
        if (ball.top <= 0) {

            userScore++
            resetBall()
        } else if (ball.bottom >= height) {

            aiScore++
            resetBall()
        }

        moveAIPaddle()
    }

    private fun moveAIPaddle() {
        // Only move if ball is moving toward AI
        if (ballSpeedY < 0) {
            val aiPaddleCenter = (aiPaddle.left + aiPaddle.right) / 2

            // Predict where ball will be when it reaches AI paddle height
            val ballCenterX = (ball.left + ball.right) / 2
            val ballCenterY = (ball.top + ball.bottom) / 2

            // Calculate time for ball to reach AI paddle
            val distanceToAI = ballCenterY - (aiPaddle.bottom + ballRadius)
            val timeToReachAI = abs(distanceToAI / ballSpeedY)

            // Predict x position
            var predictedX = ballCenterX + (ballSpeedX * timeToReachAI)

            // Account for bounces off walls
            val bounces = (predictedX / width).toInt()
            if (bounces != 0) {
                predictedX = if (bounces % 2 == 0) {
                    predictedX % width
                } else {
                    width - (predictedX % width)
                }
            }

            // Add some randomness based on difficulty
            if (Math.random() > aiPredictionFactor) {
                predictedX += (Math.random() * 100 - 50).toFloat()
            }

            // Calculate target position with error margin based on difficulty
            val targetX = predictedX

            // Calculate AI speed based on difficulty and ball speed
            val aiSpeed = baseSpeed * aiDifficultyFactor * (1 + abs(ballSpeedY) / baseSpeed * 0.2f)

            // Move AI paddle toward target
            if (abs(aiPaddleCenter - targetX) > aiSpeed) {
                val direction = if (aiPaddleCenter < targetX) 1 else -1
                val newX = aiPaddleCenter + direction * aiSpeed

                // Ensure AI paddle stays within screen bounds
                val halfPaddleWidth = paddleWidth / 2
                val boundedX = when {
                    newX - halfPaddleWidth < 0 -> halfPaddleWidth
                    newX + halfPaddleWidth > width -> width - halfPaddleWidth
                    else -> newX
                }

                aiPaddle.left = boundedX - halfPaddleWidth
                aiPaddle.right = boundedX + halfPaddleWidth
            }
        }
    }

    private fun resetBall() {
        // Reset ball position
        ball.set(
            (width - ballRadius * 2) / 2,
            (height - ballRadius * 2) / 2,
            (width + ballRadius * 2) / 2,
            (height + ballRadius * 2) / 2
        )

        trailPositions.clear()

        // Set waiting flag instead of immediately launching the ball
        waitingForTouch = true

        // Temporarily pause the ball movement
        ballSpeedX = 0f
        ballSpeedY = 0f

        invalidate() // Redraw to show the waiting state


    }

    private fun endGame() {
        gameStarted = false
        gameOver = true
        handler.removeCallbacks(gameLoop)
        gameTimer?.cancel()

        gameListener?.onGameOver(userScore, aiScore)

        invalidate()
    }

    fun restartGame() {
        gameTimer?.cancel()
        setupGameTimer()

        // Reset ball position and wait for touch
        ball.set(
            (width - ballRadius * 2) / 2,
            (height - ballRadius * 2) / 2,
            (width + ballRadius * 2) / 2,
            (height + ballRadius * 2) / 2
        )

        waitingForTouch = true
        ballSpeedX = 0f
        ballSpeedY = 0f

        gameStarted = true
        gameOver = false
        userScore = 0
        aiScore = 0
        timeRemaining = gameTime
        trailPositions.clear()

        // Start game loop and timer
        handler.post(gameLoop)
        gameTimer?.start()
    }

    fun stopGame() {
        gameStarted = false
        gameOver = true
        handler.removeCallbacks(gameLoop)
        gameTimer?.cancel()
        invalidate()
    }

    interface GameListener {
        fun onGameOver(userScore: Int, aiScore: Int)
    }
}