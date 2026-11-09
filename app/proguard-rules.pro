# Règles R8 propres à PulseTrack (build release).
# Retrofit, OkHttp, kotlinx.serialization, Room, WorkManager et Firebase fournissent déjà les leurs.

# Les traces de plantage restent lisibles (fichier et ligne) ; le mapping.txt du build les décode.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
