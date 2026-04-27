# Solución Diseño de arquitectura: Diseño de Módulo de Gestión de Pedidos
## Arquitectura Hexagonal (Ports & Adapters)

Este documento describe el diseño arquitectónico para el módulo de gestión de pedidos, siguiendo los principios de la Arquitectura Hexagonal para garantizar la mantenibilidad, la testabilidad y la independencia tecnológica del núcleo del negocio.

---

### 1. Definiciones Conceptuales

#### ¿Qué va en el Dominio?
El **Dominio** es la capa más interna y el corazón del sistema. Contiene la lógica de negocio pura, independiente de cualquier framework o detalle de infraestructura.
- **Entidades:** Objetos con identidad única y ciclo de vida (ej. `Order`).
- **Value Objects:** Objetos definidos por sus atributos, inmutables (ej. `OrderId`, `Address`, `Money`).
- **Domain Services:** Lógica de negocio que no pertenece a una sola entidad.
- **Excepciones de Dominio:** Errores específicos del negocio (ej. `InvalidOrderStateException`).

#### ¿Qué es un Puerto?
Un **Puerto** es una interfaz que define un contrato de comunicación entre la aplicación y el mundo exterior.
- **Puertos de Entrada (Driving Ports):** Interfaces que definen las operaciones que el mundo exterior puede solicitar a la aplicación (casos de uso).
- **Puertos de Salida (Driven Ports):** Interfaces que definen las necesidades técnicas que la aplicación requiere para funcionar (persistencia, mensajería, etc.), pero cuya implementación es delegada a un adaptador.

#### ¿Dónde viven los Casos de Uso?
Los **Casos de Uso** residen en la **Capa de Aplicación**. Actúan como orquestadores: reciben una solicitud a través de un puerto de entrada, coordinan la lógica del dominio y utilizan los puertos de salida para persistir datos o notificar eventos. No contienen lógica de negocio compleja, sino el flujo de ejecución.

---

### 2. Estructura de Paquetes

La organización de paquetes sigue una jerarquía de dependencias estrictamente hacia el centro (Dominio).

```text
com.empresa.orders
├── domain                          <-- Núcleo: Lógica de Negocio Pura
│   ├── model                       <-- Order, OrderItem, OrderStatus (Entidades y VOs)
│   ├── service                     <-- Domain Services
│   └── exception                   <-- OrderDomainException
├── application                     <-- Orquestación
│   ├── ports
│   │   ├── in                      <-- Puertos de Entrada (Interfaces de Casos de Uso)
│   │   └── out                     <-- Puertos de Salida (SPI - Service Provider Interfaces)
│   └── usecases                    <-- Implementación de la lógica de orquestación
└── infrastructure                  <-- Detalles Técnicos (Adaptadores)
    ├── adapters
    │   ├── in
    │   │   └── web                 <-- OrderRestController (Adaptador Primario)
    │   └── out
    │       ├── persistence         <-- OrderPostgresRepository (Adaptador Secundario)
    │       ├── messaging           <-- GcpPubSubPublisher (Adaptador Secundario)
    │       └── notifications       <-- EmailNotificationAdapter (Adaptador Secundario)
    └── config                      <-- Configuración de Inyección de Dependencias y Frameworks
```

---

### 3. Definición de Puertos

A continuación se definen los contratos principales del sistema.

#### Puertos de Entrada (Input Ports)
Definen las capacidades que la aplicación ofrece al mundo exterior.

```java
// Puerto para la creación de pedidos
public interface CreateOrderUseCase {
    OrderId execute(CreateOrderCommand command);
}

// Puerto para la confirmación de pedidos
public interface ConfirmOrderUseCase {
    void execute(OrderId orderId);
}
```

#### Puertos de Salida (Output Ports)
Definen las necesidades técnicas que la aplicación requiere.

```java
// Puerto para la persistencia de datos
public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(OrderId id);
}

// Puerto para la publicación de eventos de negocio
public interface OrderEventPublisher {
    void publishOrderConfirmed(Order order);
}
```

---

### 4. Justificación Técnica: GCP Pub/Sub como Adaptador Secundario

En la Arquitectura Hexagonal, la distinción entre adaptadores primarios y secundarios se basa en **quién inicia la acción**:

1. **Adaptador Primario (Driving):** Es el disparador. El `OrderRestController` es primario porque es quien inicia el flujo al recibir una petición HTTP.
2. **Adaptador Secundario (Driven):** Es una herramienta utilizada por la aplicación.

**GCP Pub/Sub es un adaptador secundario porque:**
- **Es un detalle de infraestructura:** la aplicación necesita "notificar que un pedido fue confirmado", pero no le interesa el medio técnico. El puerto `OrderEventPublisher` abstrae esta necesidad.
- **Dependencia Inversa:** El núcleo de la aplicación no depende de las librerías de Google Cloud; es el adaptador de infraestructura el que implementa el puerto y depende de la SDK de GCP.
- **Sustituibilidad:** Si en el futuro se decide migrar a Apache Kafka o AWS SNS, solo es necesario crear un nuevo adaptador secundario que implemente `OrderEventPublisher`, sin modificar una sola línea de código en la capa de aplicación o dominio.
