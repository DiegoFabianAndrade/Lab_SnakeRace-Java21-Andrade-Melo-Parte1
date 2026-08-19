# 📘 Guía de Estudio para Parcial: Concurrencia y Sincronización en Java 21 (SnakeRace)

Esta guía explica detalladamente la arquitectura, los problemas de concurrencia identificados, los patrones de diseño aplicados y los conceptos teóricos clave para resolver la **Parte II** del laboratorio de Arquitecturas de Software.

---

# 📑 Tabla de Contenidos
1. [Conceptos Teóricos Clave para el Parcial](#1-conceptos-teóricos-clave-para-el-parcial)
2. [Diagnóstico de Fallos de Concurrencia en SnakeRace Inicial](#2-diagnóstico-de-fallos-de-concurrencia-en-snakerace-inicial)
3. [Solución Paso a Paso y Arquitectura](#3-solución-paso-a-paso-y-arquitectura)
   - [Paso 1: Thread-Safety y Snapshot en `Snake.java`](#paso-1-thread-safety-y-snapshot-en-snakejava)
   - [Paso 2: Monitor de Pausa Pasiva en `GameClock.java`](#paso-2-monitor-de-pausa-pasiva-en-gameclockjava)
   - [Paso 3: Ciclo de Vida y Pausa en `SnakeRunner.java`](#paso-3-ciclo-de-vida-y-pausa-en-snakerunnerjava)
   - [Paso 4: Colecciones Concurrentes y Estadísticas en `Board.java`](#paso-4-colecciones-concurrentes-y-estadísticas-en-boardjava)
   - [Paso 5: Consistencia de UI sin Tearing en `SnakeApp.java`](#paso-5-consistencia-de-ui-sin-tearing-en-snakeappjava)
4. [Preguntas Típicas de Examen y Respuestas Modelo](#4-preguntas-típicas-de-examen-y-respuestas-modelo)
5. [Resumen Rápido (Cheat Sheet)](#5-resumen-rápido-cheat-sheet)

---

# 1. Conceptos Teóricos Clave para el Parcial

### 1.1 Hilos de Plataforma vs. Virtual Threads (Java 21)
- **Hilos de Plataforma (Platform Threads)**: Son mapeados 1 a 1 con hilos del Sistema Operativo (hilos pesados). Crear miles consume gigabytes de memoria y genera alto costo por cambio de contexto (*context switching*).
- **Hilos Virtuales (Virtual Threads)**: Introducidos en Java 21 (Project Loom). Son administrados por la JVM y montados sobre un pool de hilos portadores (*carrier threads*). Son extremadamente ligeros (se pueden crear cientos de miles simultáneamente).
- **En SnakeRace**: Se utiliza `Executors.newVirtualThreadPerTaskExecutor()` para que cada serpiente corra en su propio hilo virtual autónomo.

### 1.2 Condiciones de Carrera (*Race Conditions* / *Data Races*)
Ocurren cuando dos o más hilos acceden simultáneamente a una misma posición de memoria compartida, y **al menos uno de ellos realiza una operación de escritura**, sin la sincronización adecuada.

### 1.3 Visibilidad vs. Atomicidad
- **`volatile`**: Garantiza **visibilidad** (la variable se lee y escribe directamente en memoria principal, evitando que los hilos lean valores viejos cacheados en los registros de la CPU), pero **NO** garantiza **atomicidad** en operaciones compuestas (ej. `count++` no es atómico).
- **`synchronized`**: Garantiza **atomicidad** (exclusión mutua: solo un hilo ejecuta el bloque a la vez) y **visibilidad** (al salir del bloque se vuelcan los cambios a memoria).
- **Clases Atómicas (`AtomicReference`, `AtomicBoolean`, `AtomicInteger`)**: Utilizan instrucciones a nivel de hardware tipo CAS (*Compare-And-Swap*) para lograr atomicidad sin bloqueos pesados.

### 1.4 Colecciones No Seguras vs. Concurrentes
- `ArrayDeque`, `ArrayList`, `HashMap`, `HashSet` **NO son thread-safe**. Si un hilo las modifica mientras otro las recorre o lee, lanzan `ConcurrentModificationException` o corrompen sus apuntadores internos.
- Soluciones: Sincronizar sus accesos con bloques `synchronized`, envolverlas con `Collections.synchronizedList()` o usar colecciones especializadas como `ConcurrentLinkedQueue` o `ConcurrentHashMap`.

### 1.5 Fenómeno de *Tearing* (Lecturas Inconsistentes)
Ocurre cuando un hilo lee el estado de un objeto mientras otro hilo lo está modificando a medias, observando una combinación inválida de estados viejos y nuevos. Para evitar el *tearing*, las lecturas deben obtener una instantánea atómica (*snapshot*).

---

# 2. Diagnóstico de Fallos de Concurrencia en SnakeRace Inicial

| Componente | Código Original | Problema Identificado | Consecuencia en Ejecución |
| :--- | :--- | :--- | :--- |
| **`Snake.java`** | `private final Deque<Position> body = new ArrayDeque<>();`<br>`public Deque<Position> snapshot() { return new ArrayDeque<>(body); }` | `ArrayDeque` no es thread-safe. El hilo Swing EDT llamaba a `snapshot()` mientras el hilo virtual de la serpiente llamaba a `advance()` (`addFirst`/`removeLast`). | **Data Race**: `ConcurrentModificationException` o corrupción del cuerpo de la serpiente en la pantalla. |
| **`GameClock.java`** | `public void pause() { state.set(GameState.PAUSED); }` | Solo detenía la invocación de `tick.run()` (el repintado). **No existía ningún mecanismo de aviso o monitor para los hilos de las serpientes**. | **Falsa Pausa**: Los hilos virtuales de las serpientes seguían corriendo a máxima velocidad en segundo plano. |
| **`SnakeRunner.java`** | `while (!Thread.currentThread().isInterrupted()) { ... }` | No consultaba el estado de pausa. Si se hubiera hecho `while(isPaused) {}`, habría sido **busy-waiting** (100% de CPU). | Desincronización y falta de control de pausa. |
| **`Board.java`** | No registraba orden de muertes ni el estado `alive`/`dead`. | No era posible calcular *"la primera serpiente en morir (la peor serpiente)"*. | Incumplimiento del requisito funcional del laboratorio. |

---

# 3. Solución Paso a Paso y Arquitectura

```
                        ┌───────────────────────────────┐
                        │      SnakeApp (Swing EDT)     │
                        └──────────────┬────────────────┘
                                       │ Repinta UI / Lee Snapshots
                                       ▼
        ┌─────────────────────────────────────────────────────────────┐
        │                         GameClock                           │
        │  - state: AtomicReference<GameState>                        │
        │  - pauseLock: Object (Monitor: wait() / notifyAll())        │
        └──────────────┬──────────────────────────────┬───────────────┘
                       │                              │
         Consulta si   │ checkPaused()                │ checkPaused()
         está pausado  ▼                              ▼
        ┌──────────────────────────────┐ ┌─────────────────────────────┐
        │  Virtual Thread (Snake #1)   │ │  Virtual Thread (Snake #2)  │
        │  SnakeRunner 1               │ │  SnakeRunner 2              │
        └──────────────┬───────────────┘ └─────────────┬───────────────┘
                       │ step(snake)                   │ step(snake)
                       ▼                               ▼
        ┌─────────────────────────────────────────────────────────────┐
        │                           Board                             │
        │  - mice, obstacles, turbo, teleports (synchronized)         │
        │  - deadSnakes: ConcurrentLinkedQueue<Snake> (FIFO)          │
        └─────────────────────────────────────────────────────────────┘
```

---

### Paso 1: Thread-Safety y Snapshot en `Snake.java`

Para proteger la cola `ArrayDeque<Position> body` sin introducir cuellos de botella:
1. Sincronizamos todos los métodos que mutan o leen `body`: `advance()`, `snapshot()`, `head()` y `size()`.
2. `snapshot()` clona la cola dentro de un bloque `synchronized`, garantizando que Swing EDT reciba una copia estática e inmutable en ese instante de tiempo.
3. Declaramos `direction` y `alive` como `volatile` para garantizar visibilidad inmediata entre hilos.

```java
public synchronized Deque<Position> snapshot() {
    return new ArrayDeque<>(body); // Copia atómica defensiva
}

public synchronized void advance(Position newHead, boolean grow) {
    if (!alive) return;
    body.addFirst(newHead);
    if (grow) maxLength++;
    while (body.size() > maxLength) {
        body.removeLast();
    }
}
```

---

### Paso 2: Monitor de Pausa Pasiva en `GameClock.java`

Para suspender los hilos sin consumir CPU (*sin busy-waiting*):
1. Usamos un objeto explícito `pauseLock` como monitor.
2. En `pause()`, se cambia el estado a `PAUSED`.
3. En `checkPaused()`, si el estado es `PAUSED`, el hilo entra en `pauseLock.wait()`.
4. En `resume()`, se cambia el estado a `RUNNING` y se llama a `pauseLock.notifyAll()`.

```java
public void checkPaused() throws InterruptedException {
    if (state.get() == GameState.PAUSED) {
        synchronized (pauseLock) {
            // Se usa while para evitar despertares espurios (spurious wakeups)
            while (state.get() == GameState.PAUSED) {
                pauseLock.wait(); // El hilo libera el lock y se duerme (0% CPU)
            }
        }
    }
}

public void resume() {
    state.set(GameState.RUNNING);
    synchronized (pauseLock) {
        pauseLock.notifyAll(); // Despierta a todos los hilos en espera
    }
}
```

---

### Paso 3: Ciclo de Vida y Pausa en `SnakeRunner.java`

Cada hilo virtual en su ciclo de ejecución:
1. Llama a `clock.checkPaused()` antes de calcular o moverse.
2. Si la serpiente muere (`!snake.isAlive()`), sale del ciclo y el hilo virtual termina de forma natural.

```java
@Override
public void run() {
    try {
        while (!Thread.currentThread().isInterrupted() && snake.isAlive()) {
            clock.checkPaused(); // Pausa pasiva coordinada

            if (!snake.isAlive()) break;

            maybeTurn();
            var res = board.step(snake);

            if (res == Board.MoveResult.HIT_OBSTACLE) {
                randomTurn();
            } else if (res == Board.MoveResult.ATE_TURBO) {
                turboTicks = 100;
            } else if (res == Board.MoveResult.DIED) {
                break; // Terminación limpia
            }

            int sleep = (turboTicks > 0) ? turboSleepMs : baseSleepMs;
            if (turboTicks > 0) turboTicks--;
            Thread.sleep(sleep);
        }
    } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
    }
}
```

---

### Paso 4: Colecciones Concurrentes y Estadísticas en `Board.java`

Para registrar de forma cronológica la primera serpiente fallecida (y las sucesivas):
- Usamos una cola concurrente `ConcurrentLinkedQueue<Snake> deadSnakes`.
- `deadSnakes.offer(snake)` inserta en el extremo final sin requerir bloqueos pesados.
- `deadSnakes.peek()` retorna la cabeza de la cola (la primera en morir) en tiempo $O(1)$.

```java
private final Queue<Snake> deadSnakes = new ConcurrentLinkedQueue<>();

public Snake getFirstDeadSnake() {
    return deadSnakes.peek(); // La primera en morir
}

public void registerDeath(Snake snake) {
    if (snake != null && snake.isAlive()) {
        snake.markDead();
        deadSnakes.offer(snake);
    }
}
```

---

### Paso 5: Consistencia de UI sin Tearing en `SnakeApp.java`

Al presionar el botón de pausa o la barra espaciadora:
1. `clock.pause()` suspende los hilos.
2. En el hilo gráfico de Swing (`SwingUtilities.invokeLater`), se leen de forma consistente:
   - La **serpiente viva más larga**: `snakes.stream().filter(Snake::isAlive).max(Comparator.comparingInt(Snake::size)).orElse(null)`.
   - La **peor serpiente**: `board.getFirstDeadSnake()`.
3. Se actualiza la barra de estado y se repinta la ventana.

---

# 4. Preguntas Típicas de Examen y Respuestas Modelo

### P1: ¿Por qué no se debe usar `while (isPaused) {}` para pausar los hilos?
**Respuesta:** Porque genera **espera activa (*busy-waiting*)**, lo cual mantiene a los núcleos de la CPU al 100% de uso ejecutando un ciclo vacío. Esto provoca sobrecalentamiento, desperdicio de energía e inanición (*starvation*) de otros hilos. La forma correcta es usar **espera pasiva** con `wait()` y `notifyAll()` o primitivas de sincronización (`Lock`/`Condition`), donde el sistema operativo o la JVM suspenden el hilo hasta que ocurra el evento.

### P2: ¿Por qué `wait()` debe invocarse dentro de un bucle `while` y no de un `if`?
**Respuesta:** Por dos razones fundamentales:
1. **Despertares Espurios (*Spurious Wakeups*)**: La especificación de la JVM y los sistemas operativos permite que un hilo despierte de `wait()` sin que nadie haya llamado a `notify()` o `notifyAll()`.
2. **Re-evaluación de la condición**: Si múltiples hilos despiertan con `notifyAll()`, uno de ellos puede cambiar la condición compartida antes de que los demás adquieran el cerrojo. El bucle `while` obliga al hilo a verificar nuevamente la condición antes de continuar.

### P3: ¿Por qué se necesita `synchronized` en `snapshot()` de `Snake` si solo está leyendo?
**Respuesta:** Porque `ArrayDeque` no es seguro para subprocesos. Si el hilo gráfico lee o itera sobre los nodos de la cola mientras el hilo de la serpiente está ejecutando `body.addFirst()` o `body.removeLast()`, la estructura de datos interna se encuentra en un estado transitorio inconsistente, lo que produce `ConcurrentModificationException` o lecturas de memoria corruptas (*data races*). `synchronized` garantiza una lectura atómica y consistente.

### P4: ¿Cuál es la diferencia entre `notify()` y `notifyAll()`? ¿Cuándo usar cada uno?
**Respuesta:**
- `notify()` despierta a **un solo hilo arbitrario** que esté esperando en el monitor. Si ese hilo no puede proceder y no notifica a los demás, puede causar un bloqueo permanente (*deadlock* / *lost wakeup*).
- `notifyAll()` despierta a **todos los hilos** en espera sobre ese monitor.
- En situaciones donde múltiples hilos esperan por una señal global (como reanudar un juego o una pausa), se **debe usar `notifyAll()`** para garantizar que todas las serpientes se reactiven.

---

# 5. Resumen Rápido (Cheat Sheet)

| Concepto | Regla de Oro en Java |
| :--- | :--- |
| **Exclusión Mutua** | Usar `synchronized` sobre un objeto compartido (*lock/monitor*). |
| **Pausa Pasiva** | `synchronized(lock) { while(condicion) lock.wait(); }` |
| **Reanudación** | `synchronized(lock) { condicion = false; lock.notifyAll(); }` |
| **Variables Compartidas Simples** | `volatile` para visibilidad de banderas booleanas o referencias. |
| **Operaciones Compuestas Thread-Safe** | `AtomicInteger`, `AtomicReference`, o bloques `synchronized`. |
| **Colecciones FIFO Concurrentes** | `ConcurrentLinkedQueue` para inserción/extracción no bloqueante. |
| **Interfaz Gráfica Swing** | Toda modificación o repintado de UI debe ir en el Swing EDT con `SwingUtilities.invokeLater()`. |
