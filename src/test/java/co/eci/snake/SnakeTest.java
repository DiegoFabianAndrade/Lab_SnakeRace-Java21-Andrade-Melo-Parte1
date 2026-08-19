package co.eci.snake;

import co.eci.snake.core.Direction;
import co.eci.snake.core.Position;
import co.eci.snake.core.Snake;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para validar la seguridad en subprocesos (Thread Safety) y la lógica de Snake.
 */
class SnakeTest {

    @Test
    @DisplayName("Debe impedir giros directos de 180 grados")
    void testInvalidTurn() {
        Snake snake = Snake.of(1, 5, 5, Direction.UP);
        snake.turn(Direction.DOWN); // Intento de giro opuesto de 180°
        assertEquals(Direction.UP, snake.direction(), "No debe permitir giro directo de 180 grados");

        snake.turn(Direction.RIGHT);
        assertEquals(Direction.RIGHT, snake.direction());

        snake.turn(Direction.LEFT);
        assertEquals(Direction.RIGHT, snake.direction(), "No debe permitir giro opuesto de RIGHT a LEFT");
    }

    @Test
    @DisplayName("Debe gestionar correctamente el ciclo de vida y muerte de la serpiente")
    void testSnakeLifecycle() {
        Snake snake = Snake.of(1, 10, 10, Direction.RIGHT);
        assertTrue(snake.isAlive());
        assertEquals(-1, snake.getDeathTimestamp());

        snake.markDead();
        assertFalse(snake.isAlive());
        assertTrue(snake.getDeathTimestamp() > 0);
    }

    @Test
    @DisplayName("Debe ser seguro ante lecturas y escrituras concurrentes (sin ConcurrentModificationException)")
    void testConcurrentReadAndWrite() throws InterruptedException {
        Snake snake = Snake.of(1, 0, 0, Direction.RIGHT);
        int iterations = 10_000;
        AtomicBoolean errorOccurred = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(2);

        var executor = Executors.newFixedThreadPool(2);

        // Hilo escritor (simula SnakeRunner)
        executor.submit(() -> {
            try {
                for (int i = 0; i < iterations; i++) {
                    snake.advance(new Position(i % 50, (i + 1) % 50), i % 10 == 0);
                }
            } catch (Exception e) {
                errorOccurred.set(true);
            } finally {
                latch.countDown();
            }
        });

        // Hilo lector (simula Swing EDT en paintComponent)
        executor.submit(() -> {
            try {
                for (int i = 0; i < iterations; i++) {
                    var snapshot = snake.snapshot();
                    assertNotNull(snapshot);
                    assertTrue(snake.size() > 0);
                }
            } catch (Exception e) {
                errorOccurred.set(true);
            } finally {
                latch.countDown();
            }
        });

        boolean finished = latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "Las operaciones concurrentes debieron finalizar en el tiempo límite");
        assertFalse(errorOccurred.get(), "No deben ocurrir excepciones durante lectura/escritura concurrente");
    }
}
