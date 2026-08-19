# 📘 Guía de Estudio para Parcial: Monitores y Sincronización `wait/notify` en Java (PrimeFinder)

Esta guía explica detalladamente la teoría de concurrencia, el modelo de monitores de Java, los mecanismos de sincronización `wait()` / `notifyAll()`, la eliminación de *busy-waiting* y la arquitectura implementada para la **Parte I** del laboratorio.

---

# 📑 Tabla de Contenidos
1. [Conceptos Teóricos Clave para el Parcial](#1-conceptos-teóricos-clave-para-el-parcial)
2. [El Modelo de Monitores en Java](#2-el-modelo-de-monitores-en-java)
3. [Diferencias Críticas: `wait()` vs `sleep()` vs `join()`](#3-diferencias-críticas-wait-vs-sleep-vs-join)
4. [Análisis del Problema y Solución Paso a Paso](#4-análisis-del-problema-y-solución-paso-a-paso)
   - [El Monitor: `PrimeControl.java`](#el-monitor-primecontroljava)
   - [El Trabajador: `PrimeFinderThread.java`](#el-trabajador-primefinderthreadjava)
   - [El Coordinador: `PrimeFinderApp.java`](#el-coordinador-primefinderappjava)
5. [Preguntas Típicas de Examen y Respuestas Modelo](#5-preguntas-típicas-de-examen-y-respuestas-modelo)
6. [Resumen Rápido (Cheat Sheet)](#6-resumen-rápido-cheat-sheet)

---

# 1. Conceptos Teóricos Clave para el Parcial

### 1.1 Concurrencia vs. Paralelismo
- **Concurrencia**: Capacidad de estructurar un programa en múltiples tareas lógicas independientes que pueden ejecutarse en periodos de tiempo superpuestos (manejo de múltiples cosas a la vez).
- **Paralelismo**: Ejecución física simultánea de múltiples instrucciones en diferentes núcleos de CPU al mismo instante de tiempo.

### 1.2 Memoria Compartida y Condiciones de Carrera (*Race Conditions*)
- En Java, los hilos de un mismo proceso comparten el mismo espacio de memoria (*Heap*).
- Una **condición de carrera** ocurre cuando dos o más hilos acceden concurrentemente a una variable compartida y al menos uno escribe, sin un mecanismo de sincronización que garantice orden o atomicidad.
- **Consecuencia**: Pérdida de actualizaciones (ej. `count++` perdiendo incrementos), lecturas sucias o corrupción de memoria.

### 1.3 Espera Activa (*Busy-Waiting*) vs. Espera Pasiva (*Passive Waiting*)
- **Espera Activa (*Busy-Waiting*)**: El hilo se queda en un bucle infinito `while (isPaused) { /* nada */ }` consultando una bandera.
  - **Problema**: Consume el 100% de la capacidad de procesamiento de la CPU, satura los núcleos y genera gasto innecesario de energía e inanición (*starvation*).
- **Espera Pasiva**: El hilo invoca `wait()` sobre un monitor. La JVM y el SO cambian el estado del hilo a `WAITING` y lo remueven de la cola de ejecución de la CPU (consumo: 0% CPU) hasta que otro hilo lo despierte con `notify()` o `notifyAll()`.

---

# 2. El Modelo de Monitores en Java

Cada objeto en Java (`java.lang.Object`) posee intrínsecamente un **monitor** asociado compuesto por:
1. **Un Cerrojo de Exclusión Mutua (*Mutex Lock*)**: Solo un hilo puede poseer el cerrojo del monitor a la vez (`synchronized`).
2. **Un Conjunto de Entrada (*Entry Set*)**: Hilos bloqueados esperando adquirir el cerrojo para entrar al bloque `synchronized` (Estado `BLOCKED`).
3. **Un Conjunto de Espera (*Wait Set*)**: Hilos que ya poseían el cerrojo, llamaron a `wait()`, **liberaron el cerrojo** y entraron en suspensión (Estado `WAITING`).

```
                    ┌────────────────────────┐
                    │      Hilos Nuevos      │
                    └───────────┬────────────┘
                                │ Intentan entrar a synchronized
                                ▼
                    ┌────────────────────────┐
                    │       Entry Set        │ (Estado: BLOCKED)
                    └───────────┬────────────┘
                                │ Adquiere el Lock
                                ▼
                    ┌────────────────────────┐
                    │  Ejecución en Monitor  │
                    │   (Posee el Cerrojo)   │
                    └─────┬────────────▲─────┘
        Llama a wait()    │            │ Notificado vía notifyAll()
   (Libera el lock)       ▼            │ y re-adquiere el Lock
                    ┌────────────────────────┐
                    │        Wait Set        │ (Estado: WAITING)
                    └────────────────────────┘
```

### Reglas de Oro de los Monitores:
1. Para llamar a `wait()`, `notify()` o `notifyAll()`, el hilo **DEBE ser el dueño del monitor** (es decir, estar dentro de un bloque o método `synchronized` sobre ese mismo objeto). Si no, Java lanza `IllegalMonitorStateException`.
2. Al llamar a `wait()`, el hilo **libera automáticamente el cerrojo** y se duerme.
3. Al despertar por `notify()` o `notifyAll()`, el hilo **no continúa inmediatamente**: debe competir de nuevo para re-adquirir el cerrojo del monitor antes de salir de `wait()`.

---

# 3. Diferencias Críticas: `wait()` vs `sleep()` vs `join()`

| Característica | `wait()` | `Thread.sleep(ms)` | `thread.join()` |
| :--- | :--- | :--- | :--- |
| **Clase a la que pertenece** | `java.lang.Object` | `java.lang.Thread` (estático) | `java.lang.Thread` (instancia) |
| **¿Libera el cerrojo (*Lock*)?** | **SÍ**, libera el cerrojo del monitor actual. | **NO**, duerme manteniendo todos sus cerrojos. | **NO**, el hilo que llama espera a que el objetivo muera. |
| **¿Requiere `synchronized`?** | **SÍ**, de lo contrario lanza excepción. | **NO**. | **NO**. |
| **Forma de despertar** | Con `notify()`, `notifyAll()` o timeout. | Transcurrido el tiempo asignado. | Cuando el hilo objetivo termina su `run()`. |
| **Uso típico** | Coordinación y comunicación entre hilos. | Pausas temporales fijas. | Esperar finalización de hilos concurrentes. |

---

# 4. Análisis del Problema y Solución Paso a Paso

El requerimiento de la Parte I exige:
1. $N$ hilos buscando primos de forma simultánea.
2. Cada $t$ milisegundos, pausar todos los trabajadores.
3. Mostrar el total de primos calculados hasta ese instante.
4. Esperar a que el usuario presione `ENTER` en consola.
5. Reanudar a los hilos trabajadores sin perder ningún cálculo y sin *busy-waiting*.

---

### El Monitor: `PrimeControl.java`

Esta clase centraliza el cerrojo y la condición de suspensión:

```java
package co.eci.primefinder;

public class PrimeControl {
    private boolean paused = false;
    private final Object lock = new Object(); // Cerrojo explícito

    public void checkPaused() {
        synchronized (lock) {
            // Regla obligatoria: bucle WHILE para evitar despertares espurios
            while (paused) {
                try {
                    lock.wait(); // Suspende el hilo y libera el lock
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    public void pause() {
        synchronized (lock) {
            this.paused = true;
        }
    }

    public void resumeExecution() {
        synchronized (lock) {
            this.paused = false;
            lock.notifyAll(); // Despierta a todos los hilos en el wait-set
        }
    }
}
```

---

### El Trabajador: `PrimeFinderThread.java`

Cada trabajador divide el rango y consulta `control.checkPaused()` en cada número antes de computar:

```java
@Override
public void run() {
    for (int i = start; i <= end; i++) {
        control.checkPaused(); // Verifica si debe pausarse pasivamente

        if (Thread.currentThread().isInterrupted()) break;

        if (isPrime(i)) {
            synchronized (primes) { // Protege la colección compartida contra data races
                primes.add(i);
            }
        }
    }
}
```

---

### El Coordinador: `PrimeFinderApp.java`

El hilo principal duerme durante el intervalo $t$, luego activa la pausa, espera la interacción y reanuda:

```java
while (threads.stream().anyMatch(Thread::isAlive)) {
    Thread.sleep(timeInterval); // Deja trabajar a los hilos durante t ms

    control.pause(); // Señaliza la pausa
    Thread.sleep(50); // Tiempo para que los hilos alcancen wait()

    int totalPrimesFound;
    synchronized (sharedPrimesList) {
        totalPrimesFound = sharedPrimesList.size();
    }
    System.out.println("Primos encontrados hasta ahora: " + totalPrimesFound);
    System.out.println("Presione ENTER para continuar...");

    scanner.nextLine(); // Bloqueo de E/S por teclado

    control.resumeExecution(); // Despierta a los hilos trabajadores
}
```

---

# 5. Preguntas Típicas de Examen y Respuestas Modelo

### P1: ¿Qué es un *Spurious Wakeup* (Despertar Espurio) y cómo se previene en Java?
**Respuesta:** Un despertar espurio ocurre cuando un hilo que está en `wait()` despierta sin haber recibido una señal explícita de `notify()` o `notifyAll()`, debido a optimizaciones o señales internas del sistema operativo y la JVM. Se previene colocando siempre la llamada a `wait()` dentro de un bucle `while` que re-evalúe la condición lógica (`while (paused) { lock.wait(); }`), en lugar de usar una estructura simple `if`.

### P2: ¿Qué es un *Lost Wakeup* (Despertar Perdido)?
**Respuesta:** Ocurre cuando un hilo envía una notificación (`notify()` o `notifyAll()`) **antes** de que el hilo receptor haya entrado formalmente en el estado `wait()`. Dado que las señales de los monitores de Java no tienen memoria (no se acumulan como los permisos de un Semáforo), la notificación se pierde y el hilo receptor podría quedarse dormido indefinidamente si no se sincroniza sobre una variable de estado compartida.

### P3: ¿Por qué es un error grave usar `Thread.sleep()` dentro de un bloque `synchronized` para esperar un cambio de estado?
**Respuesta:** Porque `Thread.sleep()` **no libera el cerrojo del monitor**. Si el hilo que duerme mantiene el cerrojo adquirido, ningún otro hilo podrá entrar a los bloques `synchronized` sobre ese mismo objeto, impidiendo que el hilo productor modifique la condición de salida y causando un bloqueo general (*deadlock* o inanición). Debe usarse `wait()`, el cual sí libera el cerrojo mientras el hilo duerme.

### P4: Si se tiene una lista envuelta con `Collections.synchronizedList(list)`, ¿por qué sigue siendo necesario sincronizar manualmente al iterar sobre ella?
**Respuesta:** Porque `Collections.synchronizedList` sincroniza únicamente las operaciones atómicas individuales (como `add`, `get`, `remove`). Sin embargo, una iteración (`for (Integer x : list)`) consta de múltiples llamadas secuenciales (`hasNext()`, `next()`). Si otro hilo modifica la lista en medio de esa secuencia, se producirá `ConcurrentModificationException`. Por tanto, el bloque de iteración completo debe protegerse explícitamente con `synchronized (list)`.

---

# 6. Resumen Rápido (Cheat Sheet)

| Concepto | Regla / Patrón |
| :--- | :--- |
| **Sintaxis de Espera Pasiva** | `synchronized (lock) { while (condicion) { lock.wait(); } }` |
| **Sintaxis de Notificación** | `synchronized (lock) { condicion = false; lock.notifyAll(); }` |
| **Excepción si no hay Lock** | `IllegalMonitorStateException` |
| **Consumo de CPU en `wait()`** | 0% (El hilo pasa a estado `WAITING`) |
| **Multiplicidad de Notificación** | Usar siempre `notifyAll()` si múltiples hilos esperan la misma condición global. |
