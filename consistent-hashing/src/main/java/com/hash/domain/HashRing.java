package com.hash.domain;

import com.hash.util.HashFunction;
import java.util.SortedMap;
import java.util.TreeMap;

public class HashRing {

    // volatile asegura que el reemplazo atómico del mapa sea visible instantáneamente por otros hilos
    private volatile TreeMap<Long, Node> ring = new TreeMap<>();
    private final int virtualNodesCount;

    public HashRing(int virtualNodesCount) {
        this.virtualNodesCount = virtualNodesCount;
    }

    /**
     * Agrega un nodo físico aplicando Copy-On-Write (Bloqueo cero para lectores).
     */
    public synchronized void addNode(Node node) {
        if (node == null) return;

        // 1. Clonamos el mapa actual (Hacemos la copia en una variable local)
        TreeMap<Long, Node> newRing = new TreeMap<>(this.ring);

        // 2. Modificamos la copia de forma segura aislados del mundo exterior
        for (int i = 0; i < virtualNodesCount; i++) {
            String virtualNodeKey = node.ip() + "-V" + i; //aprovechamos el efecto avalancha de MurmurHash3
            long hash = HashFunction.hash(virtualNodeKey);
            newRing.put(hash, node);
        }

        // 3. Operación Atómica: Reemplazamos el mapa viejo por el nuevo de un solo golpe
        this.ring = newRing;
    }

    /**
     * Remueve un nodo físico aplicando Copy-On-Write.
     */
    public synchronized void removeNode(Node node) {
        if (node == null) return;

        TreeMap<Long, Node> newRing = new TreeMap<>(this.ring);

        for (int i = 0; i < virtualNodesCount; i++) {
            String virtualNodeKey = node.ip() + "-V" + i;
            long hash = HashFunction.hash(virtualNodeKey);
            newRing.remove(hash);
        }

        this.ring = newRing;
    }

    /**
     * BUSQUEDA CON BLOQUEO CERO ABSOLUTO.
     * Mil hilos pueden leer este método en paralelo sin frenarse jamás entre sí.
     */
    public Node getNode(String key) {
        if (key == null) return null;

        // Capturamos una referencia local del mapa actual (Garantiza consistencia durante este método)
        TreeMap<Long, Node> currentRing = this.ring;

        if (currentRing.isEmpty()) {
            return null;
        }

        long hash = HashFunction.hash(key);
        SortedMap<Long, Node> tailMap = currentRing.tailMap(hash); //tailMap devuelve una vista indexada (un puntero) del mapa original.
        long nodeHash = tailMap.isEmpty() ? currentRing.firstKey() : tailMap.firstKey();

        return currentRing.get(nodeHash);
    }

    /**
     * Devuelve el tamaño actual. No requiere sincronización por lectura directa de la referencia.
     */
    public int getRingSize() {
        return this.ring.size();
    }
}