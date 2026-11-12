# Integracion de vendedores con Baboons ERP

## Estado

- El PR #427 con las rutas del MVP fue mergeado en Baboons.
- El equipo ejecuto en Neon `migrations/vendedores_calle_mvp.sql`.
- Odoo 19 es el ERP conectado a Baboons. Android se comunica solamente con la API de Baboons.
- Contrato vigente de rutas: [API Baboons para vendedores en la calle](api-baboons-vendedores-calle.md).

## Flujo de datos

- Autenticacion Android con `POST /api/login`, credenciales Baboons y JWT.
- Los clientes, asignaciones de zona, mensajes, jornadas, proyectados, visitas, objetivos y GPS se gestionan a traves de Baboons.
- Las ventas y los datos maestros sincronizados desde Odoo se leen desde Baboons. La app no contiene credenciales de Odoo.
- Los potenciales se guardan como leads en Baboons y el servidor envia el aviso por correo a administracion.
- Las ubicaciones se registran en el telefono durante la jornada, se encolan sin conexion y se reintentan en lotes.

## Funcionalidad Android creada

- Login y almacenamiento local del JWT.
- Apertura/cierre de jornada y consulta de mensajes con respuesta/aceptacion.
- Seleccion de clientes habilitados para la zona y dia; guardado del proyectado.
- Registro de resultado de visita sobre clientes proyectados.
- Alta de potencial con nombre, telefono, email, CUIT, direccion, provincia, localidad y notas.
- Servicio de ubicacion visible durante la jornada, almacenamiento SQLite y sincronizacion.
- Vista diaria de indicadores y mapa nativo de Google Maps del recorrido local y clientes proyectados con coordenadas.

## Trabajo de integracion pendiente

1. Agregar a la API un buscador de clientes fuera de zona para que la excepcion manual pueda seleccionar un cliente sin conocer su ID.
2. Implementar o confirmar en la interfaz de Baboons la administracion diaria de mensajes, asignaciones, objetivos y la vista 360 de mapa/tablero. La API expone las rutas de gestion, pero este trabajo no esta en el cliente Android.
3. Confirmar los nombres y formatos exactos de los campos JSON de respuesta con una cuenta de vendedor y revisar que coincidan con los modelos Android.
4. Definir la duracion del JWT y el flujo de renovacion; la app debe volver a iniciar sesion cuando el token venza si no existe endpoint de refresh.
5. Confirmar que datos y operaciones sincroniza hoy Baboons con Odoo 19 y que documento de Odoo determina una venta computable.
6. Acordar frecuencia y retencion de GPS, reglas de privacidad y tratamiento de ubicaciones cuando una jornada queda abierta sin red.
7. Habilitar Maps SDK for Android en el proyecto Google Cloud elegido, vincular una de las cuentas de facturacion y restringir la API key al paquete Android y sus certificados SHA-1.

## Seguridad y operacion

- Toda operacion de negocio debe validar el `negocio_id` y el rol desde el JWT en Baboons.
- El GPS debe aceptarse solo durante la jornada activa, deduplicarse por dispositivo/secuencia y conservar hora de captura y hora de recepcion.
- La ubicacion se activa con permiso del sistema y una notificacion visible de servicio en primer plano. Android no permite que esta app abra por la fuerza en el telefono del vendedor; Baboons puede exigir jornada abierta antes de permitir el proyectado y las visitas.
- La API key de Google Maps queda restringida al identificador Android y a los certificados de firma autorizados; no se habilitan Places, Routes ni Geocoding hasta que alguna funcion los requiera.
