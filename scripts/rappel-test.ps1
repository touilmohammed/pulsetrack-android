# Envoie un rappel de test à l'app installée sur l'émulateur (ou le téléphone branché), même app fermée.
# Le serveur range tout par identifiant d'appareil : il faut celui de l'app, pas celui de la PWA.
# Usage : powershell -ExecutionPolicy Bypass -File scripts\rappel-test.ps1
# Prérequis : version debug installée (run-as), rappels activés dans les Réglages de l'app.

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$pkg = "corsica.universita.pulsetrack"
$api = "https://pulsetrackisco.netlify.app/api/push/test"

# L'identifiant est dans le DataStore de l'app ; run-as n'est permis que sur une version debug.
$store = & $adb exec-out run-as $pkg cat files/datastore/settings.preferences_pb
$deviceId = [regex]::Match(($store -join ''), '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}').Value
if (-not $deviceId) { Write-Host "Identifiant introuvable : l'app est-elle installée (debug) et lancée une fois ?"; exit 1 }
Write-Host "Appareil : $deviceId"

try {
    $result = Invoke-RestMethod -Method Post -Uri $api -Headers @{ 'X-Device-Id' = $deviceId }
    Write-Host "Envoyés : $($result.sent), expirés : $($result.expired), en échec : $($result.failed)"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Write-Host "Refusé par le serveur (HTTP $code)."
    Write-Host "(404 = aucun abonnement : activez les rappels dans les Réglages de l'app.)"
}
