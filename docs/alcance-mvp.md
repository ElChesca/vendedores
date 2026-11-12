# Alcance funcional inicial

## Perfiles

- **Vendedor:** inicia y cierra su jornada; lee y acepta mensajes; arma el proyectado diario; visita clientes; registra resultado, venta y potenciales.
- **Administrador:** gestiona vendedores, zonas, clientes y objetivos desde Baboons; publica mensajes y recibe respuestas; consulta el mapa y los indicadores de todos los vendedores.

La app se autentica contra Baboons. Odoo 19 se integra a traves del ERP Baboons; la app no accede directamente a Odoo. Queda por precisar que datos y operaciones ya sincroniza la instalacion actual de Baboons con Odoo.

## Flujo del vendedor

1. Inicia sesion y pulsa **Comenzar jornada**. La app muestra mensajes obligatorios del dia y registra la aceptacion individual. Se propone impedir el inicio del proyectado hasta confirmar esos mensajes; una contingencia debe poder ser habilitada por administracion y quedar auditada.
2. La app obtiene la zona asignada al vendedor para la fecha y muestra solamente los clientes habilitados para esa zona. El vendedor arma su proyectado con esos clientes. Las visitas fuera de zona se agregan como excepciones, con motivo obligatorio y trazabilidad.
3. Durante la jornada, la app registra ubicacion con hora, precision y estado de jornada. Las ubicaciones se guardan temporalmente en el dispositivo cuando falta conexion y se sincronizan luego, conservando su hora de captura.
4. En cada visita, registra resultado y observaciones; la venta se vincula a los datos confirmados por ERP cuando esten disponibles. Puede dar de alta un potencial con los mismos campos de cliente; el backend notifica automaticamente a administracion por correo.
5. Cierra jornada. La app presenta proyectado, visitado y vendido, junto con el avance del objetivo.

## Funciones de administracion

- Crear mensajes para todos o para vendedores seleccionados, marcar si requieren aceptacion, definir vigencia y consultar lecturas/respuestas.
- Administrar usuarios, asignaciones de zona/dia, clientes, objetivos y excepciones.
- Consultar mapa por vendedor, fecha y estado, con recorrido y eventos de visita.
- Ver tablero individual y consolidado: importe vendido/proyectado, avance del objetivo y eficiencia del periodo.
- Revisar potenciales nuevos clientes y estado de gestion.

## Metricas acordadas como punto de partida

- **Cobertura del proyectado:** clientes visitados / clientes proyectados.
- **Conversion:** clientes con venta / clientes visitados.
- **Cumplimiento de objetivo:** ventas netas / objetivo de venta para el mismo periodo.
- **Venta contra proyectado:** importe vendido / importe proyectado, si cada renglon del proyectado incluye importe estimado.

Definir con negocio si el objetivo y el proyectado se miden en importe, unidades, clientes u otra unidad; no mezclar unidades en un mismo porcentaje.

## Fuera del primer corte

- Optimizacion automatica de rutas y geocercas.
- Edicion libre de clientes fuera de las excepciones autorizadas.
- Operacion del ERP directamente desde Android.

## Decisiones pendientes para cerrar diseno

- Modulos de Odoo 19 instalados y datos/operaciones que Baboons ya sincroniza con Odoo (API estandar, conector existente o desarrollo).
- Campos de cliente, zonas, calendario de zonas y definicion de vendedor activo.
- Que constituye una venta valida y de donde obtenerla: pedido, factura, remito u otro documento.
- Frecuencia y regla para capturar GPS, retencion de recorridos y tratamiento de jornada sin senal.
- Campos obligatorios para alta de potencial y destinatarios de los correos.
- Unidad de objetivo y de proyectado; zona horaria y reglas para feriados.
