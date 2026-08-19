package co.eci.primefinder;

/**
 * Clase que actúa como monitor de sincronización para coordinar la pausa y reanudación
 * de los hilos de búsqueda de números primos (PrimeFinderThread).
 *
 * Utiliza el mecanismo de monitores de Java (synchronized, wait, notifyAll)
 * para evitar el consumo innecesario de recursos de CPU (busy-waiting) y prevenir
 * problemas de concurrencia como condiciones de carrera y despertares espurios (spurious wakeups).
 */
public class PrimeControl {

    /**
     * Bandera booleana que indica el estado actual de ejecución.
     * true: los hilos deben pausarse y esperar.
     * false: los hilos pueden continuar ejecutando sus tareas.
     */
    private boolean paused = false;

    /**
     * Objeto explícito que funge como cerrojo (lock / monitor).
     */
    private final Object lock = new Object();

    /**
     * Método invocado periódicamente por los hilos trabajadores antes de evaluar cada número.
     * Si el sistema está en estado de pausa, el hilo entra en espera pasiva (wait)
     * liberando el cerrojo hasta recibir una notificación de reanudación.
     */
    public void checkPaused() {
        synchronized (lock) {
            // Se utiliza un ciclo while en lugar de if para protegerse contra despertares espurios (spurious wakeups)
            // y garantizar que la condición de pausa siga siendo evaluada al despertar.
            while (paused) {
                try {
                    lock.wait(); // Suspende el hilo y libera el lock hasta que se llame notifyAll()
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restaura el estado de interrupción del hilo
                    break;
                }
            }
        }
    }

    /**
     * Señaliza la pausa del procesamiento.
     * Cambia el estado para que los hilos se detengan en su próxima verificación.
     */
    public void pause() {
        synchronized (lock) {
            this.paused = true;
        }
    }

    /**
     * Reanuda la ejecución de todos los hilos que se encuentren en estado de espera (WAITING).
     * Modifica el estado a false y despierta a todos los hilos durmientes mediante notifyAll().
     */
    public void resumeExecution() {
        synchronized (lock) {
            this.paused = false;
            lock.notifyAll(); // Despierta a todos los hilos esperando en este monitor
        }
    }

    /**
     * Consulta si el sistema se encuentra actualmente en pausa.
     *
     * @return true si la ejecución está pausada, false en caso contrario.
     */
    public boolean isPaused() {
        synchronized (lock) {
            return paused;
        }
    }
}
