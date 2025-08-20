# 📸 Guía Completa: Cache de Imágenes en Android con Coil

Basado en investigación de mejores prácticas y documentación oficial de Coil.

## 🎯 Conceptos Clave

### 1. **Single ImageLoader Pattern**
```kotlin
// ✅ CORRECTO: Un solo ImageLoader compartido en toda la app
val imageLoader = ImageLoader.Builder(context).build()

// ❌ INCORRECTO: Crear múltiples ImageLoaders
val imageLoader1 = ImageLoader.Builder(context).build()
val imageLoader2 = ImageLoader.Builder(context).build()
```

### 2. **Configuración de Cache Persistente**
```kotlin
val imageLoader = ImageLoader.Builder(context)
    .memoryCache {
        MemoryCache.Builder(context)
            .maxSizePercent(0.25) // 25% de memoria RAM
            .build()
    }
    .diskCache {
        DiskCache.Builder()
            .directory(context.filesDir.resolve("image_cache")) // Cache persistente
            .maxSizeBytes(50L * 1024 * 1024) // 50 MB
            .build()
    }
    .respectCacheHeaders(false) // Ignorar headers para persistencia
    .build()
```

## 🔄 Cómo Funciona el Cache

### **Memory Cache (RAM)**
- Almacena imágenes decodificadas listas para mostrar
- Más rápido pero limitado por memoria disponible
- Se pierde al cerrar la app

### **Disk Cache (Almacenamiento)**
- Almacena imágenes comprimidas (JPEG, PNG, etc.)
- Persistente entre sesiones de la app
- Más lento que memory cache pero más duradero

### **Flujo de Carga:**
1. Buscar en Memory Cache → Si existe: mostrar
2. Buscar en Disk Cache → Si existe: decodificar y mostrar
3. Descargar de red → Guardar en ambos caches → mostrar

## 🏗️ Implementación Recomendada

### **1. Configurar ImageLoader en Application Class**
```kotlin
class MyApplication : Application() {
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(filesDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
```

### **2. Usar en Composables**
```kotlin
@Composable
fun TokenImage(logoUrl: String) {
    val imageLoader = (LocalContext.current.applicationContext as MyApplication).imageLoader
    
    AsyncImage(
        model = logoUrl,
        imageLoader = imageLoader,
        contentDescription = "Token Logo",
        modifier = Modifier.size(40.dp)
    )
}
```

### **3. Verificar Cache**
```kotlin
suspend fun isImageCached(url: String, imageLoader: ImageLoader): Boolean {
    val request = ImageRequest.Builder(context)
        .data(url)
        .build()
    
    // Verificar memory cache
    request.memoryCacheKey?.let { key ->
        if (imageLoader.memoryCache?.get(key) != null) return true
    }
    
    // Verificar disk cache
    request.diskCacheKey?.let { key ->
        val snapshot = imageLoader.diskCache?.openSnapshot(key)
        return (snapshot != null).also { snapshot?.close() }
    }
    
    return false
}
```

### **4. Precargar Imágenes**
```kotlin
suspend fun preloadImage(url: String, imageLoader: ImageLoader) {
    val request = ImageRequest.Builder(context)
        .data(url)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .build()
    
    imageLoader.execute(request)
}
```

## 🚀 Optimizaciones Avanzadas

### **1. Para Apps con Muchas Imágenes Pequeñas (Logos)**
```kotlin
.memoryCache {
    MemoryCache.Builder(context)
        .maxSizePercent(0.30) // Más memoria para logos
        .build()
}
.diskCache {
    DiskCache.Builder()
        .maxSizeBytes(100L * 1024 * 1024) // 100 MB
        .build()
}
```

### **2. Para Apps con Imágenes Grandes (NFTs, Fotos)**
```kotlin
.memoryCache {
    MemoryCache.Builder(context)
        .maxSizePercent(0.15) // Menos memoria por imagen grande
        .build()
}
.diskCache {
    DiskCache.Builder()
        .maxSizeBytes(500L * 1024 * 1024) // 500 MB
        .build()
}
```

### **3. Configuración para Dispositivos con Poca Memoria**
```kotlin
.memoryCache {
    MemoryCache.Builder(context)
        .maxSizePercent(0.10) // Solo 10% de memoria
        .build()
}
.diskCache {
    DiskCache.Builder()
        .maxSizeBytes(25L * 1024 * 1024) // 25 MB
        .build()
}
```

## 🛠️ Mantenimiento del Cache

### **1. Limpiar Cache Cuando Sea Necesario**
```kotlin
// Limpiar todo
imageLoader.memoryCache?.clear()
imageLoader.diskCache?.clear()

// Limpiar imagen específica
imageLoader.memoryCache?.remove(memoryCacheKey)
imageLoader.diskCache?.remove(diskCacheKey)
```

### **2. Monitorear Estadísticas**
```kotlin
fun logCacheStats(imageLoader: ImageLoader) {
    val memoryCache = imageLoader.memoryCache
    val diskCache = imageLoader.diskCache
    
    Log.d("CacheStats", """
        Memory: ${memoryCache?.size}/${memoryCache?.maxSize} items
        Disk: ${diskCache?.size}/${diskCache?.maxSize} bytes
        Directory: ${diskCache?.directory}
    """)
}
```

### **3. Evitar OutOfMemoryError**
```kotlin
.memoryCache {
    MemoryCache.Builder(context)
        .maxSizePercent(0.20) // No más del 20%
        .weakReferencesEnabled(true) // Permitir GC cuando sea necesario
        .build()
}
```

## 🌐 Soporte Offline

### **Configuración para Funcionar Sin Internet**
```kotlin
.networkCachePolicy(CachePolicy.ENABLED)
.diskCachePolicy(CachePolicy.ENABLED)
.memoryCachePolicy(CachePolicy.ENABLED)
.respectCacheHeaders(false) // Ignorar headers de expiración
```

### **Verificar Estado de Red**
```kotlin
.okHttpClient {
    OkHttpClient.Builder()
        .addInterceptor { chain ->
            if (!isNetworkAvailable()) {
                // Forzar uso de cache offline
                val request = chain.request().newBuilder()
                    .cacheControl(CacheControl.FORCE_CACHE)
                    .build()
                chain.proceed(request)
            } else {
                chain.proceed(chain.request())
            }
        }
        .build()
}
```

## 📊 Métricas de Rendimiento

### **Tiempos Esperados:**
- **Memory Cache Hit**: < 1ms
- **Disk Cache Hit**: 10-50ms  
- **Network Download**: 100-2000ms

### **Indicadores de Salud:**
- Memory Cache Hit Rate: > 70%
- Disk Cache Hit Rate: > 90%
- Average Load Time: < 200ms

## ⚠️ Errores Comunes

### **1. ❌ Crear múltiples ImageLoaders**
```kotlin
// MALO
fun loadImage1() = ImageLoader.Builder(context).build()
fun loadImage2() = ImageLoader.Builder(context).build()
```

### **2. ❌ No configurar disk cache persistente**
```kotlin
// MALO - cache temporal
.diskCache {
    DiskCache.Builder()
        .directory(context.cacheDir) // Se borra al limpiar cache
}
```

### **3. ❌ No verificar cache antes de mostrar placeholder**
```kotlin
// MALO - siempre muestra placeholder
AsyncImage(
    model = url,
    placeholder = painterResource(R.drawable.placeholder)
)

// BUENO - deja que Coil maneje el placeholder
AsyncImage(model = url, imageLoader = customImageLoader)
```

## 🎯 Mejores Prácticas

1. **Un solo ImageLoader por app**
2. **Usar `context.filesDir` para cache persistente**
3. **Configurar límites apropiados de memoria y disco**
4. **Verificar cache antes de descargar**
5. **Ignorar headers de cache para persistencia**
6. **Monitorear estadísticas regularmente**
7. **Limpiar cache cuando sea necesario**
8. **Usar `CachePolicy.ENABLED` para offline**

## 📚 Referencias

- [Documentación Oficial de Coil](https://coil-kt.github.io/coil/)
- [Image Loaders Configuration](https://coil-kt.github.io/coil/image_loaders/)
- [Caching Strategies](https://coil-kt.github.io/coil/caching/)
- [Stack Overflow: Locally Store Images with Coil](https://stackoverflow.com/questions/69359140/)