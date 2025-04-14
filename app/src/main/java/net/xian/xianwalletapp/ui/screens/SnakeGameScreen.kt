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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
// NavController import removed
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.random.Random
// --- Arcade Colors ---
private val ArcadeBackground = Color.Black
private val ArcadeSnakeColor = Color.Green
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
    val snake: List<Point> = listOf(Point(GRID_SIZE / 2, GRID_SIZE / 2)),
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
                title = { Text("Snake Game - Score: ${gameState.score}") },
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
                val cellSize = size.width / GRID_SIZE // Calculate cell size based on canvas width
                // Draw background first
                drawRect(color = ArcadeBackground, size = size) // Use arcade background
                drawGrid(cellSize)
                drawFood(gameState.food, cellSize)
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
                    ) {
                        Text("Game Over!", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                        Text("Score: ${gameState.score}", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            gameState = GameState() // Reset game
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
        // Use retro colors - make head slightly darker than body for distinction
        // Use arcade colors - make head slightly brighter/different if desired, or keep it simple
        // val color = if (index == 0) Color.White else ArcadeSnakeColor // Example: White head
        val color = ArcadeSnakeColor // Simple: whole snake is green
        drawRect(
            color = color, // Apply arcade color
            topLeft = Offset(point.x * cellSize, point.y * cellSize),
            size = Size(cellSize, cellSize)
        )
    }
}

private fun DrawScope.drawFood(food: Point, cellSize: Float) {
    drawRect(
        color = ArcadeFoodColor, // Use arcade food color
        topLeft = Offset(food.x * cellSize, food.y * cellSize),
        size = Size(cellSize, cellSize)
    )
}