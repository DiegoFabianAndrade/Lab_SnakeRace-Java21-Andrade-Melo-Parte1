package co.eci.snake;

import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.Snake;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para Board validando movimientos, registro de muertes y robustez concurrente.
 */
class BoardTest {

    @Test
    @DisplayName("Debe registrar y retornar la primera serpiente fallecida (peor serpiente)")
    void testDeathTracking() {
        Board board = new Board(20, 20);
        Snake snake1 = Snake.of(1, 2, 2, Direction.RIGHT);
        Snake snake2 = Snake.of(2, 4, 4, Direction.DOWN);

        assertNull(board.getFirstDeadSnake(), "Al inicio ninguna serpiente ha muerto");

        board.registerDeath(snake1);
        assertEquals(1, board.getFirstDeadSnake().getId(), "La primera en morir debe ser la serpiente 1");

        board.registerDeath(snake2);
        assertEquals(1, board.getFirstDeadSnake().getId(), "La primera en morir debe mantenerse como la serpiente 1");
    }

    @Test
    @DisplayName("Debe soportar alta concurrencia de pasos con N >= 20 serpientes sin deadlocks ni excepciones")
    void testHighConcurrencyLoad() throws InterruptedException {
        int nSnakes = 25;
        Board board = new Board(40, 40);
        List<Snake> snakes = new ArrayList<>();

        for (int i = 0; i < nSnakes; i++) {
            snakes.add(Snake.of(i + 1, (i * 2) % 40, (i * 2) % 40, Direction.RIGHT));
        }

        int stepsPerSnake = 500;
        CountDownLatch latch = new CountDownLatch(nSnakes);
        var exec = Executors.newVirtualThreadPerTaskExecutor();

        for (Snake s : snakes) {
            exec.submit(() -> {
                try {
                    for (int step = 0; step < stepsPerSnake; step++) {
                        board.step(s);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "Todos los hilos de las 25 serpientes debieron completar sus pasos concurrentes sin deadlocks");
    }
}
