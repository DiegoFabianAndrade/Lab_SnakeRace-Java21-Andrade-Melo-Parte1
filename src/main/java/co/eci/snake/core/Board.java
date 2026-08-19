package co.eci.snake.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tablero de juego donde interactúan las serpientes, ratones, obstáculos, teletransportadores y turbos.
 *
 * Esta clase es segura para subprocesos (Thread-Safe). Las operaciones de lectura y modificación
 * sobre los elementos del tablero se ejecutan de manera atómica sincronizada. Además,
 * mantiene un registro seguro y no bloqueante (mediante {@link ConcurrentLinkedQueue})
 * del orden cronológico en el que las serpientes fallecen durante la partida.
 */
public final class Board {

  private final int width;
  private final int height;

  /** Conjunto de posiciones con ratones que hacen crecer a las serpientes */
  private final Set<Position> mice = new HashSet<>();

  /** Conjunto de obstáculos que generan rebote */
  private final Set<Position> obstacles = new HashSet<>();

  /** Conjunto de ítems de velocidad turbo */
  private final Set<Position> turbo = new HashSet<>();

  /** Mapeo bidireccional de portales de teletransportación */
  private final Map<Position, Position> teleports = new HashMap<>();

  /** Cola concurrente no bloqueante que almacena las serpientes muertas en orden de defunción */
  private final Queue<Snake> deadSnakes = new ConcurrentLinkedQueue<>();

  /**
   * Resultados posibles de un intento de avance en el tablero.
   */
  public enum MoveResult {
    MOVED,
    ATE_MOUSE,
    HIT_OBSTACLE,
    ATE_TURBO,
    TELEPORTED,
    DIED
  }

  public Board(int width, int height) {
    if (width <= 0 || height <= 0) throw new IllegalArgumentException("Board dimensions must be positive");
    this.width = width;
    this.height = height;
    for (int i = 0; i < 6; i++) mice.add(randomEmpty());
    for (int i = 0; i < 4; i++) obstacles.add(randomEmpty());
    for (int i = 0; i < 3; i++) turbo.add(randomEmpty());
    createTeleportPairs(2);
  }

  public int width() { return width; }
  public int height() { return height; }

  /**
   * Retorna una copia defensiva del conjunto de posiciones de ratones.
   */
  public synchronized Set<Position> mice() {
    return new HashSet<>(mice);
  }

  /**
   * Retorna una copia defensiva del conjunto de posiciones de obstáculos.
   */
  public synchronized Set<Position> obstacles() {
    return new HashSet<>(obstacles);
  }

  /**
   * Retorna una copia defensiva del conjunto de posiciones con turbos.
   */
  public synchronized Set<Position> turbo() {
    return new HashSet<>(turbo);
  }

  /**
   * Retorna una copia defensiva del mapa de teletransportadores.
   */
  public synchronized Map<Position, Position> teleports() {
    return new HashMap<>(teleports);
  }

  /**
   * Retorna la primera serpiente que murió en la partida (la peor serpiente), o null si ninguna ha muerto.
   *
   * @return La primera serpiente fallecida.
   */
  public Snake getFirstDeadSnake() {
    return deadSnakes.peek();
  }

  /**
   * Registra la muerte de una serpiente y la añade al registro cronológico de defunciones.
   *
   * @param snake Serpiente que ha fallecido.
   */
  public void registerDeath(Snake snake) {
    if (snake != null && snake.isAlive()) {
      snake.markDead();
      deadSnakes.offer(snake);
    }
  }

  /**
   * Ejecuta un paso atómico de movimiento para una serpiente dada.
   *
   * @param snake Serpiente que intenta desplazarse.
   * @return El resultado de la acción de movimiento (MoveResult).
   */
  public synchronized MoveResult step(Snake snake) {
    Objects.requireNonNull(snake, "snake");
    if (!snake.isAlive()) {
      return MoveResult.DIED;
    }

    var head = snake.head();
    var dir = snake.direction();
    Position next = new Position(head.x() + dir.dx, head.y() + dir.dy).wrap(width, height);

    // Detección de colisión con obstáculos (rebote)
    if (obstacles.contains(next)) {
      return MoveResult.HIT_OBSTACLE;
    }

    // Detección de teletransportador
    boolean teleported = false;
    if (teleports.containsKey(next)) {
      next = teleports.get(next);
      teleported = true;
    }

    // Interacciones con consumibles
    boolean ateMouse = mice.remove(next);
    boolean ateTurbo = turbo.remove(next);

    // Avance físico del cuerpo de la serpiente
    snake.advance(next, ateMouse);

    // Si consumió un ratón, genera nuevos elementos dinámicamente
    if (ateMouse) {
      mice.add(randomEmpty());
      obstacles.add(randomEmpty());
      if (ThreadLocalRandom.current().nextDouble() < 0.2) {
        turbo.add(randomEmpty());
      }
    }

    if (ateTurbo) return MoveResult.ATE_TURBO;
    if (ateMouse) return MoveResult.ATE_MOUSE;
    if (teleported) return MoveResult.TELEPORTED;
    return MoveResult.MOVED;
  }

  private void createTeleportPairs(int pairs) {
    for (int i = 0; i < pairs; i++) {
      Position a = randomEmpty();
      Position b = randomEmpty();
      teleports.put(a, b);
      teleports.put(b, a);
    }
  }

  private Position randomEmpty() {
    var rnd = ThreadLocalRandom.current();
    Position p;
    int guard = 0;
    do {
      p = new Position(rnd.nextInt(width), rnd.nextInt(height));
      guard++;
      if (guard > width * height * 2) break;
    } while (mice.contains(p) || obstacles.contains(p) || turbo.contains(p) || teleports.containsKey(p));
    return p;
  }
}
