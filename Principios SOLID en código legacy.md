# Solución Principios SOLID en código legacy : Refactorización de OrderService
## Aplicación de Principios SOLID y Patrones de Diseño

Este documento detalla el análisis y la refactorización de una implementación deficiente de un servicio de pedidos, transformándolo en un código mantenible, seguro y escalable.

---

### 1. Análisis de Violaciones a los Principios SOLID

El código original presentaba fallos críticos de diseño y seguridad. A continuación, se enumeran los principios violados:

#### SRP (Single Responsibility Principle)
La clase `OrderService` actuaba como un "God Object". Tenía la responsabilidad de:
- Gestionar la conexión a la base de datos.
- Ejecutar consultas SQL.
- Realizar llamadas HTTP a una API de pagos.
- Configurar y enviar correos electrónicos via SMTP.
- Gestionar la lógica de inventario.
**Consecuencia:** Cualquier cambio en el proveedor de email o en la tabla de la DB obligaba a modificar la clase principal.

#### OCP (Open/Closed Principle)
El código estaba cerrado a la extensión. Para cambiar la lógica de pagos (ej. pasar de una API REST a PayPal o Stripe), era necesario modificar el código fuente del método `processOrder`.
**Consecuencia:** Alto riesgo de introducir bugs en la lógica de pedidos al intentar cambiar un detalle técnico.

#### DIP (Dependency Inversion Principle)
El servicio de alto nivel (`OrderService`) dependía directamente de implementaciones de bajo nivel (`DriverManager`, `HttpURLConnection`, `Properties`). No existía ninguna capa de abstracción.
**Consecuencia:** Imposibilidad de realizar tests unitarios sin tener una base de datos y un servidor de mail reales funcionando.

#### ISP (Interface Segregation Principle)
Al no existir interfaces, el sistema obligaba a manejar todas las dependencias en un solo bloque monolítico. No había segregación de contratos para los diferentes dominios (Pagos, Notificaciones, Inventario).

#### Vulnerabilidades de Seguridad
El código presentaba una **SQL Injection** crítica mediante la concatenación de variables en la consulta: `"... WHERE id = " + orderId`.

---

### 2. Solución Refactorizada

Se aplicó la segregación de responsabilidades mediante la creación de interfaces y la implementación de **Inyección de Dependencias por Constructor**.

```java
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

// --- MODELOS DE DOMINIO ---
record Order(int id, double total) {}

// --- ABSTRACCIONES (PUERTOS) ---
interface OrderRepository {
    Order findById(int id);
}

interface PaymentService {
    void processPayment(double amount);
}

interface EmailService {
    void sendConfirmationEmail(Order order);
}

interface InventoryService {
    void decrementStock(int orderId);
}

// --- SERVICIO ORQUESTADOR (SRP APLICADO) ---
public class OrderService {
    private static final Logger logger = Logger.getLogger(OrderService.class.getName());
    
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final EmailService emailService;
    private final InventoryService inventoryService;

    // Inyección de dependencias por constructor (DIP)
    public OrderService(OrderRepository orderRepository, 
                        PaymentService paymentService, 
                        EmailService emailService, 
                        InventoryService inventoryService) {
        this.orderRepository = orderRepository;
        this.paymentService = paymentService;
        this.emailService = emailService;
        this.inventoryService = inventoryService;
    }

    public void processOrder(int orderId) {
        // 1. Recuperación de datos (Responsabilidad delegada al repositorio)
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            throw new OrderNotFoundException("Pedido no encontrado: " + orderId);
        }

        // 2. Procesamiento de Pago (Síncrono - Crítico)
        paymentService.processPayment(order.total());

        // 3. Actualización de Inventario (Síncrono - Crítico)
        inventoryService.decrementStock(orderId);

        // 4. Notificación (Asíncrono - No bloqueante)
        // Se utiliza CompletableFuture para evitar que la latencia del servidor de email 
        // afecte la respuesta al usuario final.
        CompletableFuture.runAsync(() -> {
            try {
                emailService.sendConfirmationEmail(order);
            } catch (Exception e) {
                logger.severe("Error enviando email para el pedido " + orderId + ": " + e.getMessage());
            }
        });

        logger.info("Pedido " + orderId + " procesado exitosamente.");
    }
}

class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String message) { super(message); }
}
```

---

### 3. Justificación de las Mejoras Técnicas

| Mejora | Descripción Técnica | Impacto |
| :--- | :--- | :--- |
| **Desacoplamiento** | Sustitución de implementaciones concretas por interfaces. | Permite cambiar proveedores (ej. de Gmail a SendGrid) sin tocar la lógica de negocio. |
| **Testabilidad** | Uso de Inyección de Dependencias (DI). | Permite el uso de **Mocks** y **Stubs** en pruebas unitarias, eliminando la dependencia de infraestructura. |
| **Rendimiento** | Implementación de flujo No Bloqueante para Emails. | Reduce el tiempo de respuesta del método `processOrder` al delegar el envío de mail a un hilo separado. |
| **Seguridad** | Eliminación de concatenación en SQL. | Al delegar al repositorio, se implementan `PreparedStatements`, eliminando el riesgo de SQL Injection. |
| **Mantenibilidad** | Aplicación estricta de SRP. | Cada clase tiene una única razón para cambiar, facilitando la lectura y el mantenimiento del código. |
