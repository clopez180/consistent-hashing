package com.hash.util;

import java.nio.charset.StandardCharsets;

public class HashFunction {

    /**
     * Implementación pura y optimizada de MurmurHash3 (32-bit). Algoritmo No Criptográfico
     * Proporciona una distribución uniforme excelente y velocidad ultra alta sin criptografía.
     */
    public static long hash(String key) {
        if (key == null) return 0;

        byte[] data = key.getBytes(StandardCharsets.UTF_8);
        int length = data.length;
        int seed = 0x9747b28c; // Semilla aleatoria estándar

        int c1 = 0xcc9e2d51; // numero del mismo algoritmo constante con propiedad de dispersion
        int c2 = 0x1b873593; // numero del mismo algoritmo constante con propiedad de dispersion
        int h1 = seed;

        int roundedEnd = (length & 0xfffffffc);  // Bloques de 4 bytes

        // 0xff truco para convertir byte con signo
        for (int i = 0; i < roundedEnd; i += 4) {
            int k1 = (data[i] & 0xff) |
                    ((data[i + 1] & 0xff) << 8) |
                    ((data[i + 2] & 0xff) << 16) |
                    (data[i + 3] << 24);

            k1 *= c1;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= c2;

            h1 ^= k1;
            h1 = Integer.rotateLeft(h1, 13);
            h1 = h1 * 5 + 0xe6546b64;
        }

        // Procesar los bytes restantes (cola del string)
        int k1 = 0;
        switch (length & 3) {
            case 3: k1 ^= (data[roundedEnd + 2] & 0xff) << 16;
            case 2: k1 ^= (data[roundedEnd + 1] & 0xff) << 8;
            case 1: k1 ^= (data[roundedEnd] & 0xff);
                k1 *= c1;
                k1 = Integer.rotateLeft(k1, 15);
                k1 *= c2;
                h1 ^= k1;
        }

        // Avalancha final para mezclar los bits
        h1 ^= length;     // Mezcla el tamaño del texto original
        h1 ^= h1 >>> 16;  // Desplaza bits a la derecha y los mezcla con XOR
        h1 *= 0x85ebca6b; // Multiplica por una constante de dispersión masiva
        h1 ^= h1 >>> 13;  // Vuelve a desplazar y mezclar
        h1 *= 0xc2b2ae35; // Otra constante matemática pulida por Appleby
        h1 ^= h1 >>> 16;

        return (long) h1 & 0xFFFFFFFFL; // Convertir a 32-bit unsigned en un Long
    }
}
