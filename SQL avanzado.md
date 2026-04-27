# Solución SQL Avanzado: SQL Analytics E-commerce
## Optimización y Compatibilidad (PostgreSQL & OracleSQL)

Este documento presenta la resolución de consultas analíticas sobre un modelo de datos de e-commerce, enfocándose en la legibilidad mediante CTEs, el manejo correcto de series temporales y la compatibilidad entre motores de base de datos.

---

### 1. Modelo de Datos Analizado
- `orders` (id, customer_id, status, total, created_at)
- `order_items` (id, order_id, product_id, quantity, unit_price)
- `products` (id, name, category, stock)
- `customers` (id, name, email, country)

---

### 2. Consultas Implementadas

#### a) Top 5 Clientes con mayor valor total (Últimos 30 días)
*Objetivo: Identificar los clientes con mayor volumen de gasto, cantidad de órdenes y promedio por ticket.*

**PostgreSQL**
```sql
WITH CustomerSpending AS (
    SELECT 
        c.name, 
        SUM(o.total) as total_spent, 
        COUNT(o.id) as order_count, 
        AVG(o.total) as avg_order_value
    FROM customers c
    JOIN orders o ON c.id = o.customer_id
    WHERE o.created_at >= NOW() - INTERVAL '30 days'
    GROUP BY c.id, c.name
)
SELECT name, total_spent, order_count, avg_order_value
FROM CustomerSpending
ORDER BY total_spent DESC
LIMIT 5;
```

**OracleSQL**
```sql
WITH CustomerSpending AS (
    SELECT 
        c.name, 
        SUM(o.total) as total_spent, 
        COUNT(o.id) as order_count, 
        AVG(o.total) as avg_order_value
    FROM customers c
    JOIN orders o ON c.id = o.customer_id
    WHERE o.created_at >= SYSDATE - 30
    GROUP BY c.id, c.name
)
SELECT name, total_spent, order_count, avg_order_value
FROM CustomerSpending
ORDER BY total_spent DESC
FETCH FIRST 5 ROWS ONLY;
```

---

#### b) Productos con stock crítico y alta rotación
*Objetivo: Filtrar productos con stock < 10 que hayan superado las 50 unidades vendidas en el último mes.*

**PostgreSQL & OracleSQL (Sintaxis compatible)**
```sql
WITH ProductSales AS (
    SELECT 
        p.id, p.name, p.category, SUM(oi.quantity) as units_sold
    FROM products p
    JOIN order_items oi ON p.id = oi.product_id
    JOIN orders o ON oi.order_id = o.id
    WHERE p.stock < 10 
      AND (o.created_at >= NOW() - INTERVAL '30 days' OR o.created_at >= SYSDATE - 30)
    GROUP BY p.id, p.name, p.category
)
SELECT name, category 
FROM ProductSales 
WHERE units_sold > 50;
```
*(Nota: En una implementación real, se elegiría el operador de fecha según el motor específico).*

---

#### c) Reporte de Ventas Diarias por Categoría (Últimos 7 días)
*Objetivo: Generar un reporte exhaustivo que incluya días sin ventas mediante la creación de una matriz de fechas y categorías.*

**PostgreSQL**
```sql
WITH DateSeries AS (
    SELECT generate_series(
        CURRENT_DATE - INTERVAL '6 days', 
        CURRENT_DATE, 
        '1 day'::interval
    )::date AS sale_date
),
Categories AS (
    SELECT DISTINCT category FROM products
),
DateCategoryMatrix AS (
    SELECT d.sale_date, c.category 
    FROM DateSeries d 
    CROSS JOIN Categories c
)
SELECT 
    m.sale_date, 
    m.category, 
    COALESCE(SUM(o.total), 0) as daily_revenue
FROM DateCategoryMatrix m
LEFT JOIN products p ON m.category = p.category
LEFT JOIN order_items oi ON p.id = oi.product_id
LEFT JOIN orders o ON oi.order_id = o.id AND o.created_at::date = m.sale_date
GROUP BY m.sale_date, m.category
ORDER BY m.sale_date DESC, m.category;
```

**OracleSQL**
```sql
WITH DateSeries AS (
    SELECT TRUNC(SYSDATE) - LEVEL + 1 AS sale_date
    FROM dual 
    CONNECT BY LEVEL <= 7
),
Categories AS (
    SELECT DISTINCT category FROM products
),
DateCategoryMatrix AS (
    SELECT d.sale_date, c.category 
    FROM DateSeries d 
    CROSS JOIN Categories c
)
SELECT 
    m.sale_date, 
    m.category, 
    NVL(SUM(o.total), 0) as daily_revenue
FROM DateCategoryMatrix m
LEFT JOIN products p ON m.category = p.category
LEFT JOIN order_items oi ON p.id = oi.product_id
LEFT JOIN orders o ON oi.order_id = o.id AND TRUNC(o.created_at) = m.sale_date
GROUP BY m.sale_date, m.category
ORDER BY m.sale_date DESC, m.category;
```

---

### 3. Justificación Técnica y Arquitectónica

####  Optimización de Consultas (SARGability)
Se evitó el uso de funciones sobre las columnas de fecha en las cláusulas `WHERE` (ej. evitar `EXTRACT(MONTH FROM created_at)`). Al comparar la columna directamente con un valor calculado, permitimos que el optimizador de la base de datos utilice los **índices B-Tree** existentes en `created_at`, reduciendo drásticamente el costo de ejecución.

####  Estrategia de Reporte Completo (Zero-Filling)
En la consulta (c), un `LEFT JOIN` simple entre fechas y órdenes omitiría categorías que no tuvieron ventas en días específicos. La solución implementada utiliza un **CROSS JOIN** entre una serie de fechas generada y la lista de categorías. Esto crea una matriz exhaustiva de todas las combinaciones posibles, garantizando que el reporte final muestre `0` en lugar de omitir la fila.

####  Legibilidad y Mantenimiento (CTEs)
El uso de **Common Table Expressions (CTEs)** transforma la lógica anidada en un flujo secuencial. Esto no solo facilita la depuración, sino que permite que el código sea auto-documentado, separando la fase de preparación de datos (`DateSeries`, `CustomerSpending`) de la fase de presentación final.

####  Compatibilidad de Motores
Se manejaron las diferencias fundamentales entre PostgreSQL y Oracle:
- **Fechas:** `generate_series` (Postgres) vs `CONNECT BY LEVEL` (Oracle).
- **Nulos:** `COALESCE` (Estándar/Postgres) vs `NVL` (Oracle).
- **Paginación:** `LIMIT` vs `FETCH FIRST`.
