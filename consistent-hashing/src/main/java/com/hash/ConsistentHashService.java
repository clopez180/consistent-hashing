package com.hash;

import com.hash.domain.HashRing;
import com.hash.domain.Node;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio Fachada que expone las operaciones del clúster distribuidos.
 * Oculta la complejidad del anillo lógico y maneja fallbacks operativos.
 */
public class ConsistentHashService {

    // Inicializamos el Logger nativo de Java para esta clase
    private static final Logger logger = Logger.getLogger(ConsistentHashService.class.getName());

    private final HashRing hashRing;
    private final Node fallbackNode; // Nodo de contingencia por si el anillo queda vacío

    /**
     * Inicializa el servicio configurando las réplicas del anillo y un nodo de respaldo.
     */
    public ConsistentHashService(int virtualNodesCount, Node fallbackNode) {
        this.hashRing = new HashRing(virtualNodesCount);
        this.fallbackNode = fallbackNode;
        logger.log(Level.INFO, "Servicio de Hash Consistente inicializado con {0} réplicas virtuales.", virtualNodesCount);
    }

    /**
     * Inicializa el clúster con un conjunto de servidores iniciales.
     */
    public void bootstrapCluster(Collection<Node> initialNodes) {
        if (initialNodes == null) return;
        logger.info("Ejecutando bootstrap del clúster con nodos iniciales...");
        initialNodes.forEach(hashRing::addNode);
    }

    /**
     * Registra un nuevo servidor dinámicamente en el clúster.
     */
    public void registerNode(Node node) {
        if (node == null) return;
        hashRing.addNode(node);
        logger.log(Level.INFO, "Infraestructura: Nodo {0} registrado exitosamente en el anillo.", node.name());
    }

    /**
     * Remueve un servidor del clúster (por mantenimiento o baja).
     */
    public void decommissionNode(Node node) {
        if (node == null) return;
        hashRing.removeNode(node);
        logger.log(Level.WARNING, "Infraestructura: Nodo {0} removido del clúster operativo.", node.name());
    }

    /**
     * Despacha una solicitud de datos al nodo correcto.
     * Principio APOSD: Si el clúster está vacío, no explota; redirige al fallback.
     */
    public Node routeRequest(String dataKey) {
        if (dataKey == null || dataKey.isBlank()) {
            logger.log(Level.FINE, "Petición con clave vacía o nula. Redirigiendo a fallback.");
            return fallbackNode;
        }

        Node targetNode = hashRing.getNode(dataKey);

        // Si no hay nodos disponibles en el anillo, usamos el de contingencia
        if (targetNode == null) {
            logger.log(Level.SEVERE, "¡Alerta Crítica! El anillo de hash está vacío. Despachando petición hacia el fallbackNode: {0}", fallbackNode.name());
            return fallbackNode;
        }

        return targetNode;
    }

    /**
     * Devuelve el tamaño actual de la topología del anillo.
     */
    public int getClusterTokenSize() {
        return hashRing.getRingSize();
    }

}
