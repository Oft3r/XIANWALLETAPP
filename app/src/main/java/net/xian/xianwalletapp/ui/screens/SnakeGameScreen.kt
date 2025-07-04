package net.xian.xianwalletapp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import net.xian.xianwalletapp.R
// NavController import removed
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.random.Random
// --- Arcade Colors ---
private val ArcadeBackground = Color.Black
private val ArcadeSnakeColor = Color.Green // Changed back to green
private val ArcadeFoodColor = Color.Red
private val ArcadeBorderColor = Color.DarkGray // Or Color.White for higher contrast
// --- Game Constants ---
private const val GRID_SIZE = 20 // Number of cells in width/height
private const val INITIAL_GAME_SPEED_MS = 300L
private const val MIN_GAME_SPEED_MS = 100L
private const val SPEED_INCREMENT_MS = 10L

// --- Data Classes ---
data class Point(val x: Int, val y: Int)

enum class Direction {
    UP, DOWN, LEFT, RIGHT
}

data class GameState(
    val snake: List<Point> = listOf(
        Point(GRID_SIZE / 2, GRID_SIZE / 2),      // Head
        Point(GRID_SIZE / 2 - 1, GRID_SIZE / 2)  // Body segment behind head
    ),
    val food: Point = Point(Random.nextInt(GRID_SIZE), Random.nextInt(GRID_SIZE)),
    val direction: Direction = Direction.RIGHT,
    val score: Int = 0,
    val isGameOver: Boolean = false,
    val gameSpeed: Long = INITIAL_GAME_SPEED_MS
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnakeGameScreen(onBack: () -> Unit) { // Changed parameter
    var gameState by remember { mutableStateOf(GameState()) }
    var currentDirection by remember { mutableStateOf(Direction.RIGHT) } // Separate state for input
    
    // Load XIAN logo
    val xianLogo = ImageBitmap.imageResource(R.drawable.xian_logo)

    // --- Game Loop ---
    LaunchedEffect(key1 = gameState.isGameOver) { // Relaunch if game restarts
        if (!gameState.isGameOver) {
            while (isActive) {
                delay(gameState.gameSpeed)
                gameState = updateGame(gameState, currentDirection)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                title = {
                    Surface(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = "Snake Game - Score: ${gameState.score}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { // Use onBack lambda
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(8.dp), // Add some padding around the game area
            contentAlignment = Alignment.Center
        ) {
            // --- Game Canvas ---
            Canvas(
                modifier = Modifier
                    .fillMaxWidth() // Take available width
                    .aspectRatio(1f) // Maintain square aspect ratio
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val (x, y) = dragAmount
                                // Basic swipe detection (horizontal priority)
                                if (abs(x) > abs(y)) {
                                    if (x > 0 && gameState.direction != Direction.LEFT) {
                                        currentDirection = Direction.RIGHT
                                    } else if (x < 0 && gameState.direction != Direction.RIGHT) {
                                        currentDirection = Direction.LEFT
                                    }
                                } else {
                                     if (y > 0 && gameState.direction != Direction.UP) {
                                         currentDirection = Direction.DOWN
                                     } else if (y < 0 && gameState.direction != Direction.DOWN) {
                                         currentDirection = Direction.UP
                                     }
                                }
                            }
                        )
                    }
            ) {
                val cellSize = size.width / GRID_SIZE // Calculate cell size based on canvas width                // Draw background first
                drawRect(color = ArcadeBackground, size = size) // Use arcade background
                drawGrid(cellSize)
                drawFood(gameState.food, cellSize, xianLogo)
                drawSnake(gameState.snake, cellSize)
            }

            // --- Game Over Overlay ---
            if (gameState.isGameOver) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {                        Text("Game Over!", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                        Text("Score: ${gameState.score}", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            gameState = GameState() // Reset game with initial 2-square snake
                            currentDirection = Direction.RIGHT // Reset direction
                        }) {
                            Text("Play Again")
                        }
                    }
                }
            }
        }
    }
}

// --- Game Logic ---
private fun updateGame(currentState: GameState, inputDirection: Direction): GameState {
    if (currentState.isGameOver) return currentState

    // Update direction based on input, preventing 180-degree turns
    val newDirection = when {
        inputDirection == Direction.UP && currentState.direction != Direction.DOWN -> Direction.UP
        inputDirection == Direction.DOWN && currentState.direction != Direction.UP -> Direction.DOWN
        inputDirection == Direction.LEFT && currentState.direction != Direction.RIGHT -> Direction.LEFT
        inputDirection == Direction.RIGHT && currentState.direction != Direction.LEFT -> Direction.RIGHT
        else -> currentState.direction // Keep current direction if input is invalid
    }

    val head = currentState.snake.first()
    val newHead = when (newDirection) {
        Direction.UP -> head.copy(y = head.y - 1)
        Direction.DOWN -> head.copy(y = head.y + 1)
        Direction.LEFT -> head.copy(x = head.x - 1)
        Direction.RIGHT -> head.copy(x = head.x + 1)
    }

    // Check for collisions
    if (newHead.x < 0 || newHead.x >= GRID_SIZE ||
        newHead.y < 0 || newHead.y >= GRID_SIZE ||
        currentState.snake.contains(newHead)
    ) {
        return currentState.copy(isGameOver = true)
    }

    // Check for food
    val ateFood = newHead == currentState.food
    val newSnake = mutableListOf(newHead)
    newSnake.addAll(currentState.snake)
    if (!ateFood) {
        newSnake.removeAt(newSnake.lastIndex) // Remove tail if no food eaten (Lint fix for removeLast)
    }

    val newFood = if (ateFood) {
        // Generate new food, ensuring it's not on the snake
        var potentialFood: Point
        do {
            potentialFood = Point(Random.nextInt(GRID_SIZE), Random.nextInt(GRID_SIZE))
        } while (newSnake.contains(potentialFood))
        potentialFood
    } else {
        currentState.food
    }

    val newScore = if (ateFood) currentState.score + 1 else currentState.score
    val newSpeed = if (ateFood) {
        (currentState.gameSpeed - SPEED_INCREMENT_MS).coerceAtLeast(MIN_GAME_SPEED_MS)
    } else {
        currentState.gameSpeed
    }


    return currentState.copy(
        snake = newSnake,
        food = newFood,
        direction = newDirection,
        score = newScore,
        gameSpeed = newSpeed
    )
}

// --- Drawing Functions ---
private fun DrawScope.drawGrid(cellSize: Float) {
    // Only draw the outer border for arcade style
     // Draw border
    drawRect(
        color = ArcadeBorderColor, // Use arcade border color
        topLeft = Offset.Zero,
        size = Size(size.width, size.height),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f) // Use Stroke style
    )
}

private fun DrawScope.drawSnake(snake: List<Point>, cellSize: Float) {
    snake.forEachIndexed { index, point ->
        // Basic snake body
        val color = if (index == 0) ArcadeSnakeColor.copy(alpha = 0.9f) else ArcadeSnakeColor
        drawRect(
            color = color,
            topLeft = Offset(point.x * cellSize, point.y * cellSize),
            size = Size(cellSize, cellSize)
        )

        // Add eyes and tongue only to the snake's head
        if (index == 0) {
            val head = snake[0]
            val direction = if (snake.size > 1) {
                // Determine direction based on position of head vs next segment
                val neck = snake[1]
                when {
                    head.x > neck.x -> Direction.RIGHT
                    head.x < neck.x -> Direction.LEFT
                    head.y > neck.y -> Direction.DOWN
                    else -> Direction.UP
                }
            } else {
                Direction.RIGHT // Default direction if snake has only one segment
            }
            
            // Draw eyes based on direction
            val eyeRadius = cellSize * 0.15f
            val eyeOffset = cellSize * 0.25f
            
            // Left eye
            val leftEyeX: Float
            val leftEyeY: Float
            
            // Right eye
            val rightEyeX: Float
            val rightEyeY: Float
            
            when (direction) {
                Direction.UP -> {
                    leftEyeX = point.x * cellSize + eyeOffset
                    leftEyeY = point.y * cellSize + eyeOffset
                    rightEyeX = point.x * cellSize + cellSize - eyeOffset - eyeRadius
                    rightEyeY = point.y * cellSize + eyeOffset
                }
                Direction.DOWN -> {
                    leftEyeX = point.x * cellSize + eyeOffset
                    leftEyeY = point.y * cellSize + cellSize - eyeOffset - eyeRadius
                    rightEyeX = point.x * cellSize + cellSize - eyeOffset - eyeRadius
                    rightEyeY = point.y * cellSize + cellSize - eyeOffset - eyeRadius
                }
                Direction.LEFT -> {
                    leftEyeX = point.x * cellSize + eyeOffset
                    leftEyeY = point.y * cellSize + eyeOffset
                    rightEyeX = point.x * cellSize + eyeOffset
                    rightEyeY = point.y * cellSize + cellSize - eyeOffset - eyeRadius
                }
                Direction.RIGHT -> {
                    leftEyeX = point.x * cellSize + cellSize - eyeOffset - eyeRadius
                    leftEyeY = point.y * cellSize + eyeOffset
                    rightEyeX = point.x * cellSize + cellSize - eyeOffset - eyeRadius
                    rightEyeY = point.y * cellSize + cellSize - eyeOffset - eyeRadius
                }
            }
            
            // Draw eyes
            drawCircle(
                color = Color.Black,
                radius = eyeRadius,
                center = Offset(leftEyeX, leftEyeY)
            )
            drawCircle(
                color = Color.Black,
                radius = eyeRadius,
                center = Offset(rightEyeX, rightEyeY)
            )
            
            // Draw tongue
            val tongueWidth = cellSize * 0.1f
            val tongueLength = cellSize * 0.3f
            val tongueOffset = cellSize * 0.5f
            
            when (direction) {
                Direction.UP -> {
                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(point.x * cellSize + tongueOffset - tongueWidth/2, point.y * cellSize - tongueLength),
                        size = Size(tongueWidth, tongueLength)
                    )
                    // Forked tongue
                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(point.x * cellSize + tongueOffset - tongueWidth/2 - tongueWidth, point.y * cellSize - tongueLength/2),
                        size = Size(tongueWidth, tongueLength/2)
                    )
                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(point.x * cellSize + tongueOffset + tongueWidth/2, point.y * cellSize - tongueLength/2),
                        size = Size(tongueWidth, tongueLength/2)
                    )
                }
                Direction.DOWN -> {
                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(point.x * cellSize + tongueOffset - tongueWidth/2, point.y * cellSize + cellSize),
                        size = Size(tongueWidth, tongueLength)
                    )
                    // Forked tongue
                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(point.x * cellSize + tongueOffset - tongueWidth/2 - tongueWidth, point.y * cellSize + cellSize + tongueLength/2),
                        size = Size(tongueWidth, tongueLength/2)
                    )
                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(point.x * cellSize + tongueOffset + tongueWidth/2, point.y * cellSize + cellSize + tongueLength/2),
                        size = Size(tongueWidth, tongueLength/2)
                    )
                }
                Direction.LEFT -> {
                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(point.x * cellSize - tongueLength, point.y * cellSize + tongueOffset - tongueWidth/2),
                        size = Size(tongueLength, tongueWidth)
                    )
                    // Forked tongue
                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(point.x * cellSize - tongueLength/2, point.y * cellSize + tongueOffset - tongueWidth/2 - tongueWidth),
                        size = Size(tongueLength/2, tongueWidth)
                    )
                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(point.x * cellSize - tongueLength/2, point.y * cellSize + tongueOffset + tongueWidth/2),
                        size = Size(tongueLength/2, tongueWidth)
                    )
                }
                Direction.RIGHT -> {
                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(point.x * cellSize + cellSize, point.y * cellSize + tongueOffset - tongueWidth/2),
                        size = Size(tongueLength, tongueWidth)
                    )
                    // Forked tongue
                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(point.x * cellSize + cellSize + tongueLength/2, point.y * cellSize + tongueOffset - tongueWidth/2 - tongueWidth),
                        size = Size(tongueLength/2, tongueWidth)
                    )
                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(point.x * cellSize + cellSize + tongueLength/2, point.y * cellSize + tongueOffset + tongueWidth/2),
                        size = Size(tongueLength/2, tongueWidth)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawFood(food: Point, cellSize: Float, xianLogo: ImageBitmap) {
    // Draw red border around the food first
    drawRect(
        color = Color.Red,
        topLeft = Offset(food.x * cellSize, food.y * cellSize),
        size = Size(cellSize, cellSize),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
    )
    
    // Draw the XIAN logo as food inside the red border
    val padding = 4f // Small padding to ensure logo doesn't overlap with border
    drawImage(
        image = xianLogo,
        dstOffset = androidx.compose.ui.unit.IntOffset(
            x = (food.x * cellSize + padding).toInt(),
            y = (food.y * cellSize + padding).toInt()
        ),
        dstSize = androidx.compose.ui.unit.IntSize(
            width = (cellSize - padding * 2).toInt(),
            height = (cellSize - padding * 2).toInt()
        )
    )
}