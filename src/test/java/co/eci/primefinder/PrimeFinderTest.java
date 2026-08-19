package co.eci.primefinder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para validar la lógica y la sincronización de PrimeFinder.
 */
class PrimeFinderTest {

    @Test
    @DisplayName("Debe identificar correctamente números primos conocidos")
    void testIsPrime() {
        assertFalse(PrimeFinderThread.isPrime(0));
        assertFalse(PrimeFinderThread.isPrime(1));
        assertTrue(PrimeFinderThread.isPrime(2));
        assertTrue(PrimeFinderThread.isPrime(3));
        assertFalse(PrimeFinderThread.isPrime(4));
        assertTrue(PrimeFinderThread.isPrime(5));
        assertTrue(PrimeFinderThread.isPrime(13));
        assertFalse(PrimeFinderThread.isPrime(15));
        assertTrue(PrimeFinderThread.isPrime(97));
        assertFalse(PrimeFinderThread.isPrime(100));
    }

    @Test
    @DisplayName("Debe pausar y reanudar hilos correctamente sin perder datos")
    void testPauseAndResume() throws InterruptedException {
        int max = 5000;
        int nThreads = 4;
        List<Integer> primes = Collections.synchronizedList(new ArrayList<>());
        PrimeControl control = new PrimeControl();
        List<PrimeFinderThread> threads = new ArrayList<>();

        int step = max / nThreads;
        for (int i = 0; i < nThreads; i++) {
            int start = i * step + 1;
            int end = (i == nThreads - 1) ? max : (i + 1) * step;
            PrimeFinderThread t = new PrimeFinderThread(start, end, primes, control);
            threads.add(t);
            t.start();
        }

        // Simular pausa
        Thread.sleep(20);
        control.pause();
        assertTrue(control.isPaused());

        // Permitir que los hilos entren en espera
        Thread.sleep(50);
        int countDuringPause1 = primes.size();

        // Verificar que no se sigan agregando primos durante la pausa
        Thread.sleep(50);
        int countDuringPause2 = primes.size();
        assertEquals(countDuringPause1, countDuringPause2, "Los hilos no deben avanzar mientras estén pausados");

        // Reanudar
        control.resumeExecution();
        assertFalse(control.isPaused());

        for (PrimeFinderThread t : threads) {
            t.join(5000);
        }

        // Hay exactamente 669 números primos entre 1 y 5000
        assertEquals(669, primes.size(), "El total de números primos calculados debe ser 669 para el rango [1, 5000]");
    }
}
