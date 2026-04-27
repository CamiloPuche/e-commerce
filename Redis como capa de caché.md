# Solución Redis como capa de caché

## 1. Análisis del Escenario
- **Carga**: 10,000 req/min (aprox. 166 req/seg).
- **Volatilidad**: Muy baja (cambios max. 1 vez por hora).
- **Problema**: El costo de lectura en disco (DB) es órdenes de magnitud más lento que en memoria (RAM). Sin caché, la DB se convierte en el cuello de botella del sistema.

## 2. Estrategia de Implementación: Cache-Aside

He seleccionado el patrón **Cache-Aside** (también conocido como Lazy Loading). 

### Flujo de Lectura:
1. El sistema busca la información en **Redis**.
2. **Cache Hit**: Si el dato existe, se devuelve inmediatamente (latencia < 1ms).
3. **Cache Miss**: Si el dato no existe, se consulta la base de datos H2, se almacena el resultado en Redis para futuras peticiones y se devuelve al cliente.

### Implementación Técnica (Spring Boot)

#### Dependencias Necesarias
```gradle
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
```

#### Configuración del Servicio
Aplicamos las anotaciones de Spring Cache para mantener el código limpio y separado de la infraestructura.

```java
@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;

    // Se cachea la respuesta. La llave es "products::" + id
    @Cacheable(value = "products", key = "#id")
    public ProductResponse getProductById(Long id) {
        return productRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    // Invalidación activa: Borramos la caché cuando el producto cambia
    @CacheEvict(value = "products", key = "#id")
    @Transactional
    public ProductResponse updateProduct(Long id, ProductUpdateRequest request) {
        // ... lógica de actualización ...
        return mapToResponse(updatedProduct);
    }

    @CacheEvict(value = "products", key = "#id")
    @Transactional
    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }
}
```

---

## 3. Respuestas a Preguntas Técnicas

### 🕒 ¿Qué TTL usarías y por qué?
Utilizaría un **TTL de 60 minutos (3600 segundos)**.

**Justificación**: 
El requerimiento indica que los datos cambian máximo una vez por hora. Al alinear el TTL con la frecuencia de cambio del negocio, maximizamos el **Cache Hit Ratio** y minimizamos la carga sobre la base de datos. Si el dato es casi estático, no tiene sentido borrarlo antes.

### 🔄 ¿Cómo manejas la invalidación?
Implemento una **Invalidación Activa** mediante `@CacheEvict`.
En lugar de esperar a que el TTL expire (invalidación pasiva), el sistema fuerza el borrado de la llave en Redis inmediatamente después de ejecutar un `PUT` o un `DELETE`. Esto garantiza la **consistencia fuerte**: el usuario nunca verá un precio viejo después de que haya sido actualizado.

### 💥 Mitigación de Cache Stampede (Thundering Herd)
El **Cache Stampede** ocurre cuando una llave muy solicitada expira y miles de requests concurrentes detectan el "cache miss" simultáneamente, colapsando la base de datos.

Para mitigarlo, propongo dos estrategias:
1. **Jitter (Randomized TTL)**: En lugar de un TTL exacto de 3600s, agrego un margen aleatorio (ej. entre 3300s y 3900s). Esto evita que todas las llaves expiren al mismo tiempo.
2. **Sincronización de Carga (Locking)**: Utilizar el parámetro `sync = true` en `@Cacheable`.
   ```java
   @Cacheable(value = "products", key = "#id", sync = true)
   ```
   Esto obliga a que solo un hilo realice la carga desde la DB mientras los demás esperan el resultado, evitando el colapso de la base de datos.

---

## ⚖️ Trade-offs Considerados

| Enfoque | Ventaja | Desventaja |
| :--- | :--- | :--- |
| **Write-Through** | Consistencia total inmediata | Mayor latencia en escrituras (espera a DB y Caché) |
| **Cache-Aside** | Mayor disponibilidad y simplicidad | Posible inconsistencia momentánea si falla la invalidación |
| **Sin Caché** | Consistencia absoluta | Colapso del sistema ante picos de tráfico |
