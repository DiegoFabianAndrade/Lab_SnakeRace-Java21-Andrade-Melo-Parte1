package co.eci.primefinder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

/**
 * Aplicación principal para la Parte I del laboratorio.
 *
 * Coordina la búsqueda de números primos en paralelo distribuyendo un rango numérico
 * entre N hilos trabajadores. Cada t milisegundos, el hilo principal suspende todos los
 * trabajadores mediante el monitor PrimeControl, imprime la cantidad de primos encontrados
 * hasta el momento, espera a que el usuario presione ENTER y luego reanuda la búsqueda.
 */
public class PrimeFinderApp {

    private static final int DEFAULT_MAX = 30_000_000;
    private static final int DEFAULT_THREADS = 4;
    private static final long DEFAULT_TIME_MILLIS = 5000; // 5 segundos de intervalo

    public static void main(String[] args) {
        int max = DEFAULT_MAX;
        int nThreads = DEFAULT_THREADS;
        long timeInterval = DEFAULT_TIME_MILLIS;

        System.out.println("==========================================================");
        System.out.println("  Iniciando Búsqueda de Números Primos (PrimeFinder)");
        System.out.println("  Rango: [1 - " + max + "] | Hilos: " + nThreads + " | Pausa cada: " + timeInterval + " ms");
        System.out.println("==========================================================");

        List<Integer> sharedPrimesList = Collections.synchronizedList(new ArrayList<>());
        PrimeControl control = new PrimeControl();
        List<PrimeFinderThread> threads = new ArrayList<>();

        // Particionamiento del rango de búsqueda entre los hilos
        int step = max / nThreads;
        for (int i = 0; i < nThreads; i++) {
            int start = i * step + 1;
            int end = (i == nThreads - 1) ? max : (i + 1) * step;
            PrimeFinderThread thread = new PrimeFinderThread(start, end, sharedPrimesList, control);
            threads.add(thread);
            thread.start();
        }

        Scanner scanner = new Scanner(System.in);

        // Bucle de coordinación: se ejecuta mientras al menos un hilo siga activo
        while (threads.stream().anyMatch(Thread::isAlive)) {
            try {
                // Espera el tiempo de ejecución t antes de pausar
                Thread.sleep(timeInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            // Si todos los hilos ya terminaron durante el sleep, salimos del ciclo
            if (threads.stream().noneMatch(Thread::isAlive)) {
                break;
            }

            // 1. Solicita la pausa a través del monitor compartido
            control.pause();

            // Breve espera para permitir que los hilos alcancen su siguiente verificación y entren en wait()
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {}

            // 2. Consulta y muestra la cantidad de números primos encontrados
            int totalPrimesFound;
            synchronized (sharedPrimesList) {
                totalPrimesFound = sharedPrimesList.size();
            }

            System.out.println("\n----------------------------------------------------------");
            System.out.println(" [PAUSA ACTIVADA] Primos encontrados hasta ahora: " + totalPrimesFound);
            System.out.println(" Presione ENTER en la consola para reanudar la ejecución...");
            System.out.println("----------------------------------------------------------");

            // 3. Espera la acción del usuario
            if (scanner.hasNextLine()) {
                scanner.nextLine();
            }

            // 4. Reanuda todos los hilos que estaban en espera pasiva
            System.out.println(">>> Reanudando hilos trabajadores...\n");
            control.resumeExecution();
        }

        // Aseguramos que todos los hilos finalicen su ejecución
        for (PrimeFinderThread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("\n==========================================================");
        System.out.println("  Búsqueda finalizada con éxito.");
        System.out.println("  Total de números primos encontrados: " + sharedPrimesList.size());
        System.out.println("==========================================================");
    }
}
