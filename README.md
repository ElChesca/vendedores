# Gestion de Vendedores en la Calle

Aplicacion Android nativa para que cada vendedor inicie su jornada, consulte y confirme las comunicaciones del dia, prepare su recorrido por zona, registre visitas y oportunidades, y comparta su actividad con administracion. El ERP Baboons sera la consola de administracion y el backend de la app.

## Alcance inicial

- App del vendedor para Android.
- API de Baboons como backend para usuarios, jornada, ubicaciones, mensajes, proyectado y visitas.
- Administracion de mensajes, clientes, zonas, objetivos y seguimiento desde Baboons.
- Odoo 19 queda como ERP conectado a Baboons, sujeto a confirmar que datos y operaciones ya sincroniza.
- Contrato de endpoints en [`docs/api-baboons-vendedores-calle.md`](docs/api-baboons-vendedores-calle.md); alcance funcional en [`docs/alcance-mvp.md`](docs/alcance-mvp.md).

## Android implementado

- Inicio de sesion con JWT de Baboons.
- Apertura/cierre de jornada, mensajes con acuse, proyectado de clientes habilitados, registro de visitas y alta de potenciales.
- Seguimiento de ubicacion con notificacion visible, cola local para sincronizacion y mapa nativo de Google Maps.
- Indicadores diarios de cobertura, conversion, venta y avance del objetivo.

El mapa usa Maps SDK for Android. Para mostrarlo hay que habilitar el SDK en Google Cloud, vincular facturacion y restringir una API key al paquete `com.baboons.vendedorescalle` y a los certificados SHA-1 de desarrollo y produccion. La app lee `MAPS_API_KEY` desde las propiedades de Gradle o desde la variable de entorno del mismo nombre. En Windows se puede agregar a `%USERPROFILE%\.gradle\gradle.properties` como `MAPS_API_KEY=...`; no guardes la clave en este repositorio. La clave se incluye en la app instalada, por lo que las restricciones de Google son obligatorias.

La migracion de Baboons ya esta mergeada y aplicada en Neon. La busqueda de clientes fuera de zona para agregar excepciones y las pantallas administrativas de mapa/tablero en Baboons siguen pendientes de concretar.

## Decisiones iniciales propuestas

1. Android nativo con Kotlin y Jetpack Compose.
2. La app habla con la API de Baboons, nunca directamente con Odoo.
3. Ubicacion enviada durante la jornada, con almacenamiento local temporal si no hay conexion.
4. Reutilizar la API actual de Baboons donde aplique y extenderla para jornada, mensajes, proyectado y productividad.

Estas decisiones son una base de trabajo y deben ajustarse a la infraestructura y operacion de la empresa.
