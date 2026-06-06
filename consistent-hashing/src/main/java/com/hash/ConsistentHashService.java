package com.hash;

import com.hash.domain.HashRing;
import com.hash.domain.Node;
import java.util.Collection;

/**
 * Servicio Fachada que expone las operaciones del clúster distribuidos.
 * Oculta la complejidad del anillo lógico y maneja fallbacks operativos.
 */
public class ConsistentHashService {

    private final HashRing hashRing;
    private final Node fallbackNode; // Nodo de contingencia por si el anillo queda vacío

    /**
     * Inicializa el servicio configurando las réplicas del anillo y un nodo de respaldo.
     */
    public ConsistentHashService(int virtualNodesCount, Node fallbackNode) {
        this.hashRing = new HashRing(virtualNodesCount);
        this.fallbackNode = fallbackNode;
    }

    /**
     * Inicializa el clúster con un conjunto de servidores iniciales.
     */
    public void bootstrapCluster(Collection<Node> initialNodes) {
        if (initialNodes == null) return;
        initialNodes.forEach(hashRing::addNode);
    }

    /**
     * Registra un nuevo servidor dinámicamente en el clúster.
     */
    public void registerNode(Node node) {
        hashRing.addNode(node);
    }

    /**
     * Remueve un servidor del clúster (por mantenimiento o baja).
     */
    public void decommissionNode(Node node) {
        hashRing.removeNode(node);
    }

    /**
     * Despacha una solicitud de datos al nodo correcto.
     * Principio APOSD: Si el clúster está vacío, no explota; redirige al fallback.
     */
    public Node routeRequest(String dataKey) {
        if (dataKey == null || dataKey.isBlank()) {
            return fallbackNode;
        }

        Node targetNode = hashRing.getNode(dataKey);

        // Si no hay nodos disponibles en el anillo, usamos el de contingencia
        return (targetNode != null) ? targetNode : fallbackNode;
    }

    /**
     * Devuelve el tamaño actual de la topología del anillo.
     */
    public int getClusterTokenSize() {
        return hashRing.getRingSize();
    }

}
