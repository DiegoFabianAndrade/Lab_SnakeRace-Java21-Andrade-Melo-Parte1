package co.eci.primefinder;

import java.util.List;

/**
 * Hilo trabajador que se encarga de calcular y recopilar números primos
 * dentro de un rango numérico específico [start, end].
 *
 * En cada iteración, consulta al monitor PrimeControl para pausarse pasivamente
 * si así se le solicita, y almacena los primos encontrados de forma segura en la lista compartida.
 */
public class PrimeFinderThread extends Thread {

    private final int start;
    private final int end;
    private final List<Integer> primes;
    private final PrimeControl control;

    /**
     * Constructor del hilo de búsqueda.
     *
     * @param start Límite inferior del rango a evaluar (inclusivo).
     * @param end Límite superior del rango a evaluar (inclusivo).
     * @param primes Lista compartida donde se agregan los números primos encontrados.
     * @param control Monitor de sincronización para coordinar la pausa/reanudación.
     */
    public PrimeFinderThread(int start, int end, List<Integer> primes, PrimeControl control) {
        this.start = start;
        this.end = end;
        this.primes = primes;
        this.control = control;
    }

    @Override
    public void run() {
        for (int i = start; i <= end; i++) {
            // Verificación previa a la evaluación para pausar la ejecución si el monitor lo indica
            control.checkPaused();

            // Si el hilo fue interrumpido durante la pausa o ejecución, termina su labor
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            if (isPrime(i)) {
                // Se sincroniza el acceso a la lista compartida para evitar condiciones de carrera (data races)
                synchronized (primes) {
                    primes.add(i);
                }
            }
        }
    }

    /**
     * Algoritmo de primalidad optimizado para verificar si un número entero es primo.
     *
     * @param n Número a evaluar.
     * @return true si el número es primo, false en caso contrario.
     */
    public static boolean isPrime(int n) {
        if (n <= 1) return false;
        if (n <= 3) return true;
        if (n % 2 == 0 || n % 3 == 0) return false;
        for (int i = 5; (long) i * i <= n; i += 6) {
            if (n % i == 0 || n % (i + 2) == 0) return false;
        }
        return true;
    }
}
