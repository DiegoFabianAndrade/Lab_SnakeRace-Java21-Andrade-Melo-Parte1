package co.eci.snake.core.engine;

import co.eci.snake.core.GameState;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Motor de tiempo y coordinador de estados de ejecución (GameClock).
 *
 * Administra la cadencia de actualización periódica de la interfaz gráfica y actúa
 * como monitor centralizado de sincronización para suspender (pausar) y reanudar
 * de forma pasiva tanto el reloj de dibujo como todos los hilos de las serpientes.
 *
 * Utiliza el patrón de monitores de Java mediante un cerrojo explícito (pauseLock),
 * garantizando que durante la pausa los hilos liberen CPU y esperen eficientemente (wait/notifyAll).
 */
public final class GameClock implements AutoCloseable {

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final long periodMillis;
  private final Runnable tick;
  private final AtomicReference<GameState> state = new AtomicReference<>(GameState.STOPPED);

  /** Cerrojo explícito utilizado como monitor para coordinar wait() y notifyAll() */
  private final Object pauseLock = new Object();

  public GameClock(long periodMillis, Runnable tick) {
    if (periodMillis <= 0) throw new IllegalArgumentException("periodMillis must be > 0");
    this.periodMillis = periodMillis;
    this.tick = Objects.requireNonNull(tick, "tick");
  }

  /**
   * Inicia el reloj del juego y programa la tarea de repintado a una tasa fija de ejecución.
   */
  public void start() {
    if (state.compareAndSet(GameState.STOPPED, GameState.RUNNING)) {
      scheduler.scheduleAtFixedRate(() -> {
        if (state.get() == GameState.RUNNING) {
          tick.run();
        }
      }, 0, periodMillis, TimeUnit.MILLISECONDS);
    }
  }

  /**
   * Suspende el avance del juego.
   * Cambia el estado atómicamente a PAUSED.
   */
  public void pause() {
    state.set(GameState.PAUSED);
  }

  /**
   * Reanuda la ejecución del juego.
   * Modifica el estado a RUNNING y despierta a todos los hilos durmientes
   * mediante notifyAll() sobre el monitor pauseLock.
   */
  public void resume() {
    state.set(GameState.RUNNING);
    synchronized (pauseLock) {
      pauseLock.notifyAll(); // Despierta a todos los hilos virtuales esperando en el monitor
    }
  }

  /**
   * Método invocado por los hilos de las serpientes (SnakeRunner) antes de cada paso.
   * Si el estado es PAUSED, el hilo entra en espera pasiva (wait) liberando recursos de CPU.
   *
   * @throws InterruptedException si el hilo es interrumpido durante la espera.
   */
  public void checkPaused() throws InterruptedException {
    if (state.get() == GameState.PAUSED) {
      synchronized (pauseLock) {
        // Se utiliza un bucle while para verificar la condición y proteger contra despertares espurios (spurious wakeups)
        while (state.get() == GameState.PAUSED) {
          pauseLock.wait();
        }
      }
    }
  }

  public GameState getState() {
    return state.get();
  }

  public boolean isPaused() {
    return state.get() == GameState.PAUSED;
  }

  public void stop() {
    state.set(GameState.STOPPED);
  }

  @Override
  public void close() {
    scheduler.shutdownNow();
  }
}
