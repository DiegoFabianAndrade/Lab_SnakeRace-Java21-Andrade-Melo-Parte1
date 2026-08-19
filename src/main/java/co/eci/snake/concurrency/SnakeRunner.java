package co.eci.snake.concurrency;

import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.Snake;
import co.eci.snake.core.engine.GameClock;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tarea autónoma que controla el ciclo de vida y movimiento de una serpiente individual.
 *
 * Cada instancia se ejecuta de forma concurrente en su propio hilo virtual (Java 21 Virtual Thread).
 * En cada ciclo de simulación:
 * 1. Verifica si el juego está en pausa mediante {@link GameClock#checkPaused()} para suspenderse pasivamente sin consumir CPU.
 * 2. Valida si la serpiente sigue con vida.
 * 3. Ejecuta un paso en el tablero mediante {@link Board#step(Snake)}.
 * 4. Ajusta su temporización (sleep) según se encuentre en estado normal o turbo.
 */
public final class SnakeRunner implements Runnable {

  private final Snake snake;
  private final Board board;
  private final GameClock clock;
  private final int baseSleepMs = 80;
  private final int turboSleepMs = 40;
  private int turboTicks = 0;

  /**
   * Constructor principal con reloj de juego para coordinación de pausas.
   *
   * @param snake Serpiente controlada por este ejecutor.
   * @param board Tablero de juego compartido.
   * @param clock Reloj y monitor de sincronización del juego.
   */
  public SnakeRunner(Snake snake, Board board, GameClock clock) {
    this.snake = Objects.requireNonNull(snake, "snake");
    this.board = Objects.requireNonNull(board, "board");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Constructor de compatibilidad sin GameClock explícito.
   */
  public SnakeRunner(Snake snake, Board board) {
    this(snake, board, null);
  }

  @Override
  public void run() {
    try {
      while (!Thread.currentThread().isInterrupted() && snake.isAlive()) {
        // 1. Si el juego está en pausa, se suspende pasivamente mediante wait()
        if (clock != null) {
          clock.checkPaused();
        }

        // Si la serpiente falleció durante la pausa o en el paso anterior, finaliza el hilo
        if (!snake.isAlive()) {
          break;
        }

        // 2. Realiza giros autónomos aleatorios periódicos
        maybeTurn();

        // 3. Ejecuta el paso atómico en el tablero
        var res = board.step(snake);

        if (res == Board.MoveResult.HIT_OBSTACLE) {
          randomTurn();
        } else if (res == Board.MoveResult.ATE_TURBO) {
          turboTicks = 100;
        } else if (res == Board.MoveResult.DIED) {
          // La serpiente colisionó fatalmente y fue eliminada
          break;
        }

        // 4. Temporización del paso (velocidad estándar o turbo)
        int sleep = (turboTicks > 0) ? turboSleepMs : baseSleepMs;
        if (turboTicks > 0) {
          turboTicks--;
        }

        Thread.sleep(sleep);
      }
    } catch (InterruptedException ie) {
      // Manejo limpio de interrupción del hilo virtual
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Determina con una probabilidad controlada si la serpiente realiza un giro aleatorio.
   */
  private void maybeTurn() {
    double p = (turboTicks > 0) ? 0.05 : 0.10;
    if (ThreadLocalRandom.current().nextDouble() < p) {
      randomTurn();
    }
  }

  /**
   * Selecciona una dirección aleatoria y solicita el giro a la serpiente.
   */
  private void randomTurn() {
    var dirs = Direction.values();
    snake.turn(dirs[ThreadLocalRandom.current().nextInt(dirs.length)]);
  }
}
