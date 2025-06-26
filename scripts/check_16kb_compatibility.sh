#!/bin/bash

# Script para verificar compatibilidad con páginas de 16 KB
echo "Verificando compatibilidad con páginas de 16 KB..."

APK_PATH="app/build/outputs/apk/debug/Xian Wallet-1.5.7-debug.apk"

if [ ! -f "$APK_PATH" ]; then
    echo "APK no encontrado en: $APK_PATH"
    echo "Ejecuta './gradlew assembleDebug' primero"
    exit 1
fi

# Extraer el APK
rm -rf temp_apk_extract
mkdir temp_apk_extract
unzip -q "$APK_PATH" -d temp_apk_extract

echo "Verificando alineación de bibliotecas nativas..."

# Verificar bibliotecas problemáticas
PROBLEMATIC_LIBS=(
    "temp_apk_extract/lib/arm64-v8a/libbarhopper_v3.so"
    "temp_apk_extract/lib/arm64-v8a/libimage_processing_util_jni.so"
)

for lib in "${PROBLEMATIC_LIBS[@]}"; do
    if [ -f "$lib" ]; then
        echo "Encontrada: $(basename $lib)"
        # Verificar alineación (esto es básico, Google usa herramientas más específicas)
        size=$(stat -f%z "$lib" 2>/dev/null || stat -c%s "$lib" 2>/dev/null)
        echo "  Tamaño: $size bytes"
        if (( size % 16384 == 0 )); then
            echo "  ✅ Posiblemente alineada a 16 KB"
        else
            echo "  ❌ Posiblemente NO alineada a 16 KB"
        fi
    else
        echo "No encontrada: $(basename $lib)"
    fi
done

# Limpiar
rm -rf temp_apk_extract

echo "Verificación completada."
echo "Nota: Esta es una verificación básica. Google usa herramientas más precisas."