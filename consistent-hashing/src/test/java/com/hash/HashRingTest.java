
package com.hash;

import com.hash.domain.HashRing;
import com.hash.domain.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class HashRingTest {

    private HashRing hashRing;
    private ConsistentHashService hashService;
    private final int REPLICAS_VIRTUALES = 150;

    // Nodos de prueba reutilizables
    private final Node nodoA = new Node("192.168.1.10", "Servidor-A");
    private final Node nodoB = new Node("192.168.1.11", "Servidor-B");
    private final Node nodoC = new Node("192.168.1.12", "Servidor-C");
    private final Node nodoFallback = new Node("10.0.0.1", "Nodo-Contingencia-Mantenimiento");

    @BeforeEach
    public void setUp() {
        hashRing = new HashRing(REPLICAS_VIRTUALES);
        hashService = new ConsistentHashService(REPLICAS_VIRTUALES, nodoFallback);
    }

    @Test
    public void testAgregarYRemoverNodos() {
        hashRing.addNode(nodoA);
        hashRing.addNode(nodoB);

        assertEquals(300, hashRing.getRingSize(), "El anillo debería tener 300 puntos (150 por cada servidor)");

        hashRing.removeNode(nodoA);
        assertEquals(150, hashRing.getRingSize(), "Deberían quedar solo los 150 puntos del Servidor-B");
    }

    @Test
    public void testMismoServidorParaMismaClave() {
        hashRing.addNode(nodoA);
        hashRing.addNode(nodoB);
        hashRing.addNode(nodoC);

        String claveCliente = "cliente-carlos-123";
        Node servidorAsignadoPrimero = hashRing.getNode(claveCliente);

        for (int i = 0; i < 100; i++) {
            assertEquals(servidorAsignadoPrimero, hashRing.getNode(claveCliente),
                    "El ruteo falló: cambió el servidor asignado sin alterar el anillo");
        }
    }

    @Test
    public void testDefinirErroresFueraDeLaExistenciaConService() {
        // Intentamos rutear una petición cuando el clúster está completamente vacío
        // En lugar de lanzar una excepción, el Service debe interceptarlo y devolver el fallback de contingencia
        Node asignado = hashService.routeRequest("cualquier-id-de-tarjeta-o-transaccion");

        assertNotNull(asignado);
        assertEquals(nodoFallback, asignado, "Debería haber ruteado al nodo de contingencia seguro");
    }

    @Test
    public void testToleranciaAFallosYMinimoImpacto() {
        hashRing.addNode(nodoA);
        hashRing.addNode(nodoB);
        hashRing.addNode(nodoC);

        Map<String, Node> asignacionOriginal = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            String cliente = "cliente_id_" + i;
            asignacionOriginal.put(cliente, hashRing.getNode(cliente));
        }

        hashRing.removeNode(nodoB);

        int llavesAfectadas = 0;
        for (int i = 0; i < 1000; i++) {
            String cliente = "cliente_id_" + i;
            Node nodoOriginal = asignacionOriginal.get(cliente);
            Node nodoNuevo = hashRing.getNode(cliente);

            if (!nodoOriginal.equals(nodoNuevo)) {
                llavesAfectadas++;
                assertNotEquals(nodoB, nodoNuevo, "El cliente fue asignado a un nodo caído");
                assertTrue(nodoOriginal.equals(nodoB),
                        "Error crítico: Una llave que estaba en un nodo sano se reubicó innecesariamente");
            }
        }

        System.out.println("Llaves reubicadas tras caída de 1 de 3 servidores: " + llavesAfectadas + " de 1000");
        assertTrue(llavesAfectadas > 250 && llavesAfectadas < 400, "La redistribución de carga no fue óptima");
    }

    @Test
    public void testEstresConcurrenteSinCondicionesDeCarrera() throws InterruptedException {
        for (int i = 1; i <= 5; i++) {
            hashRing.addNode(new Node("192.168.1." + i, "Servidor-" + i));
        }

        int hilosLectores = 20;
        int peticionesPorHilo = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(hilosLectores + 2);
        ConcurrentHashMap<String, Long> conteoAsignaciones = new ConcurrentHashMap<>();

        for (int i = 0; i < hilosLectores; i++) {
            final int threadId = i;
            executor.submit(() -> {
                for (int j = 0; j < peticionesPorHilo; j++) {
                    String cliente = "hilo-" + threadId + "-req-" + j;
                    Node nodo = hashRing.getNode(cliente);
                    if (nodo != null) {
                        conteoAsignaciones.merge(nodo.name(), 1L, Long::sum);
                    }
                }
            });
        }

        executor.submit(() -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            hashRing.addNode(new Node("192.168.1.99", "Servidor-Nuevo-Elastic"));
        });

        executor.submit(() -> {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            hashRing.removeNode(new Node("192.168.1.1", "Servidor-1"));
        });

        executor.shutdown();
        boolean terminadoLimpio = executor.awaitTermination(5, TimeUnit.SECONDS);

        assertTrue(terminadoLimpio, "El test concurrente quedó colgado por un deadlock");
        assertFalse(conteoAsignaciones.isEmpty());

        System.out.println("Distribución de carga concurrente finalizada con éxito.");
        conteoAsignaciones.forEach((nodoName, cantidad) -> System.out.println(nodoName + " atendió: " + cantidad + " peticiones"));
    }
}
