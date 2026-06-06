package com.hash.domain;
/**
 * Representa un servidor físico (nodo) dentro del clúster distribuidos.
 * Es inmutable para garantizar la seguridad de hilos (Thread-Safety).
 */
public record Node(String ip, String name) {
    
    // Constructor compacto fail-safe para validar los datos al instanciarse
    public Node {
        if (ip == null || ip.isBlank()) {
            throw new IllegalArgumentException("La IP del nodo no puede estar vacía");
        }
        if (name == null || name.isBlank()) {
            name = ip; // Si no viene nombre, usamos la IP por defecto
        }
    }

    /**
     * Devuelve una representación única del nodo para el log de la aplicación.
     */
    @Override
    public String toString() {
        return name + " (" + ip + ")";
    }
}
