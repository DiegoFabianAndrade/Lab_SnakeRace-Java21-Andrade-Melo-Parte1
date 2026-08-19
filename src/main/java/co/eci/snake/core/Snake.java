package co.eci.snake.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Representa una serpiente dentro del juego SnakeRace.
 *
 * Esta clase es segura para subprocesos (Thread-Safe). La estructura de datos interna
 * {@code body} (que es un {@link ArrayDeque}) está protegida mediante sincronización en sus
 * métodos de lectura y mutación, evitando así condiciones de carrera (data races)
 * y excepciones de tipo {@link java.util.ConcurrentModificationException} cuando el hilo
 * gráfico (Swing EDT) toma instantáneas (snapshots) al mismo tiempo que el Virtual Thread
 * de la serpiente avanza en el tablero.
 */
public final class Snake {

  /** Identificador único numérico de la serpiente (ej. 1, 2, 3...) */
  private final int id;

  /** Estructura que almacena los segmentos del cuerpo de la serpiente */
  private final Deque<Position> body = new ArrayDeque<>();

  /** Dirección actual de desplazamiento de la serpiente */
  private volatile Direction direction;

  /** Longitud máxima que puede alcanzar la serpiente antes de cortar su cola */
  private int maxLength = 5;

  /** Estado de vida de la serpiente */
  private volatile boolean alive = true;

  /** Marca de tiempo (en nanosegundos) de la muerte de la serpiente para registrar orden de fallecimiento */
  private volatile long deathTimestamp = -1;

  /**
   * Constructor privado de la serpiente.
   *
   * @param id Identificador de la serpiente.
   * @param start Posición inicial en el tablero.
   * @param dir Dirección de inicio de movimiento.
   */
  private Snake(int id, Position start, Direction dir) {
    this.id = id;
    this.body.addFirst(Objects.requireNonNull(start, "start"));
    this.direction = Objects.requireNonNull(dir, "dir");
  }

  /**
   * Método de fábrica para instanciar una nueva serpiente.
   *
   * @param id Identificador único de la serpiente.
   * @param x Coordenada X inicial.
   * @param y Coordenada Y inicial.
   * @param dir Dirección inicial.
   * @return Nueva instancia de Snake.
   */
  public static Snake of(int id, int x, int y, Direction dir) {
    return new Snake(id, new Position(x, y), dir);
  }

  /**
   * Sobrecarga de compatibilidad para creación sin ID explícito (asigna ID = 0 por defecto).
   */
  public static Snake of(int x, int y, Direction dir) {
    return new Snake(0, new Position(x, y), dir);
  }

  public int getId() {
    return id;
  }

  public boolean isAlive() {
    return alive;
  }

  public long getDeathTimestamp() {
    return deathTimestamp;
  }

  /**
   * Marca a la serpiente como muerta de forma atómica y registra el instante de su deceso.
   */
  public synchronized void markDead() {
    if (this.alive) {
      this.alive = false;
      this.deathTimestamp = System.nanoTime();
    }
  }

  public Direction direction() {
    return direction;
  }

  /**
   * Cambia la dirección de desplazamiento de la serpiente, validando que no intente
   * un giro opuesto de 180 grados sobre sí misma.
   *
   * @param dir Nueva dirección solicitada.
   */
  public void turn(Direction dir) {
    if (dir == null || !alive) return;
    Direction cur = this.direction;
    if ((cur == Direction.UP && dir == Direction.DOWN) ||
        (cur == Direction.DOWN && dir == Direction.UP) ||
        (cur == Direction.LEFT && dir == Direction.RIGHT) ||
        (cur == Direction.RIGHT && dir == Direction.LEFT)) {
      return;
    }
    this.direction = dir;
  }

  /**
   * Retorna la posición actual de la cabeza de la serpiente.
   *
   * @return Posición de la cabeza.
   */
  public synchronized Position head() {
    return body.peekFirst();
  }

  /**
   * Retorna una copia defensiva e inmutable de la secuencia de posiciones del cuerpo.
   * Al estar sincronizado, garantiza que la copia se realice de forma atómica sin
   * interferencias de modificaciones concurrentes desde otros hilos.
   *
   * @return Nueva copia del cuerpo como Deque.
   */
  public synchronized Deque<Position> snapshot() {
    return new ArrayDeque<>(body);
  }

  /**
   * Retorna la cantidad actual de segmentos que componen la serpiente.
   *
   * @return Longitud actual del cuerpo.
   */
  public synchronized int size() {
    return body.size();
  }

  /**
   * Desplaza la serpiente insertando la nueva cabeza y retirando la cola
   * si no ha crecido.
   *
   * @param newHead Nueva posición a la que avanza la cabeza.
   * @param grow true si la serpiente ingirió comida y debe incrementar su tamaño máximo.
   */
  public synchronized void advance(Position newHead, boolean grow) {
    if (!alive) return;
    body.addFirst(Objects.requireNonNull(newHead, "newHead"));
    if (grow) maxLength++;
    while (body.size() > maxLength) {
      body.removeLast();
    }
  }
}
