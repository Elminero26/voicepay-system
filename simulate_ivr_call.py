import urllib.request
import json
import time

# Configuraciones
USER_SERVICE_URL = "http://localhost:8080/auth/login"
IVR_SERVICE_CALL = "http://localhost:8082/ivr/call"
IVR_SERVICE_CONFIRM = "http://localhost:8082/ivr/confirm"

# Credenciales para obtener el JWT token seguro
credentials = {
    "email": "admin@voicepay.com",
    "password": "password123"
}

print("🔑 Step 1: Obteniendo token JWT seguro desde user-service...")
req_login = urllib.request.Request(
    USER_SERVICE_URL,
    data=json.dumps(credentials).encode("utf-8"),
    headers={"Content-Type": "application/json"},
    method="POST"
)

try:
    with urllib.request.urlopen(req_login) as response:
        res_data = json.loads(response.read().decode("utf-8"))
        jwt_token = res_data["token"]
        print("✅ Token JWT obtenido con éxito.")
except Exception as e:
    print("❌ Error al iniciar sesión en el backend:", e)
    exit(1)

# Cabeceras autorizadas
headers = {
    "Content-Type": "application/json",
    "Authorization": f"Bearer {jwt_token}"
}

print("\n📞 Step 2: Simulando llamada entrante de Pedro (+34655443322)...")
call_payload = {
    "from": "+34655443322"
}

req_call = urllib.request.Request(
    IVR_SERVICE_CALL,
    data=json.dumps(call_payload).encode("utf-8"),
    headers=headers,
    method="POST"
)

try:
    with urllib.request.urlopen(req_call) as response:
        res_call = json.loads(response.read().decode("utf-8"))
        print("\n🔊 Respuesta del BOT IVR:")
        print(f"👉 \"{res_call['message']}\"")
        print("\n🌟 ¡Llamada activa! Ve a tu navegador (http://localhost:5173/ivr-flow).")
        print("💡 Verás cómo el nodo 'Incoming Call', 'Authentication' y 'Payment Inquiry' se iluminan en verde.")
        print("💡 El nodo 'User Selection' estará pulsando en azul, esperando tu decisión.")
except Exception as e:
    print("❌ Error al simular llamada entrante:", e)
    exit(1)

print("\n⏳ Esperando 8 segundos para que puedas observar la animación en el navegador...")
for i in range(8, 0, -1):
    print(f"{i} segundos restantes...", end="\r")
    time.sleep(1)
print("¡Hora de decidir!                          ")

print("\n⌨️ Step 3: Simulando que el usuario pulsa '1' (Confirmar Pago)...")
confirm_payload = {
    "userId": 71,
    "option": "1"
}

req_confirm = urllib.request.Request(
    IVR_SERVICE_CONFIRM,
    data=json.dumps(confirm_payload).encode("utf-8"),
    headers=headers,
    method="POST"
)

try:
    with urllib.request.urlopen(req_confirm) as response:
        res_confirm = json.loads(response.read().decode("utf-8"))
        print("\n🔊 Respuesta final del BOT IVR:")
        print(f"👉 \"{res_confirm['message']}\"")
        print("\n🎉 ¡Pago Procesado! Observa tu pantalla:")
        print("💡 El flujo se redirigirá al nodo 'Payment Status' y se completará en verde.")
        print("💡 Los microservicios involucrados habrán parpadeado transmitiendo datos de forma segura y encriptada.")
except Exception as e:
    print("❌ Error al procesar la opción del IVR:", e)
