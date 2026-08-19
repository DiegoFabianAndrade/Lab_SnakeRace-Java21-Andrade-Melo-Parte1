package co.eci.snake;

import co.eci.snake.core.GameState;
import co.eci.snake.core.engine.GameClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para GameClock validando la coordinación de pausa y reanudación con wait/notify.
 */
class GameClockTest {

    @Test
    @DisplayName("Debe coordinar pausa y reanudación de hilos concurrentes correctamente")
    void testPauseAndResume() throws InterruptedException {
        try (GameClock clock = new GameClock(50, () -> {})) {
            clock.start();
            assertEquals(GameState.RUNNING, clock.getState());

            clock.pause();
            assertEquals(GameState.PAUSED, clock.getState());
            assertTrue(clock.isPaused());

            CountDownLatch waitingLatch = new CountDownLatch(1);
            CountDownLatch resumedLatch = new CountDownLatch(1);
            AtomicBoolean threadProceeded = new AtomicBoolean(false);

            var exec = Executors.newVirtualThreadPerTaskExecutor();
            exec.submit(() -> {
                try {
                    waitingLatch.countDown();
                    clock.checkPaused(); // Se suspende en wait()
                    threadProceeded.set(true);
                    resumedLatch.countDown();
                } catch (InterruptedException ignored) {}
            });

            waitingLatch.await(2, TimeUnit.SECONDS);
            Thread.sleep(100); // Tiempo para que el hilo virtual entre en wait()

            assertFalse(threadProceeded.get(), "El hilo debe permanecer suspendido mientras esté en pausa");

            clock.resume();
            assertEquals(GameState.RUNNING, clock.getState());
            assertFalse(clock.isPaused());

            boolean resumed = resumedLatch.await(2, TimeUnit.SECONDS);
            assertTrue(resumed, "El hilo debe reanudar inmediatamente al llamar clock.resume()");
            assertTrue(threadProceeded.get());
        }
    }
}
