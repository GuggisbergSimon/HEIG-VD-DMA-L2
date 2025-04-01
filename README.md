# HEIG-VD-DMA-L2

Authors :

- Patrick Furrer
- Simon Guggisberg
- Jonas Troeltsch

## Partie 1

### Implémentation

Nous filtrons d'abord les balises détectées pour ne garder que celles qui nous concernent.
Puis nous créons des balises persistentes selon les valeurs des beacons correspondants.
Enfin, nous les passons au viewModel pour qu'il puisse mettre à jour la liste.

La liste est mise à jour en supprimant les balises qui ne sont plus valables (qui n'ont pas été vues
depuis plus de 10 secondes) et en ajoutant les nouvelles balises détectées.
Les valeurs des balises existantes sont également mises à jour (uniquement celles-ci sujettes à
variations telle que rssi, txPower, distance).
Puis la liste est triée, selon la distance, de la plus proche à la plus éloignée, avec le beacon le
plus proche en tête de liste.

De plus, toutes les 30 secondes, les balises qui ne sont plus valables (qui n'ont pas été vues
depuis plus de 10 secondes) sont purgées de la liste.

### Questions d’approfondissement

> 1.1.1 Est-ce que toutes les balises à proximité sont présentes dans toutes les annonces de la
> librairie ?
> Que faut-il mettre en place pour permettre de « lisser » les annonces et ne pas perdre
> momentanément certaines balises ?

Non, toutes les balises ne sont pas présentes à chaque fois.
Pour remédier à ce problème, il convient de rendre les `PersistentBeacon` persistants.
Pour ce faire, nous avons eu deux idées d'implémentation :

- un timeout de x minutes pour chaque, à la suite duquel, si il n'a pas été entre temps reporté par
  une nouvelle détection du Beacon, le Beacon se retrouvera supprimé de la liste.
- une valeur `lastSeen` qui permet de tracer le dernier moment où le Beacon a été détecté.
  De plus, toutes les x minutes, on va filtrer la liste des Beacons pour ne garder que ceux
  correspondant au critère, càd celles qui datent de moins de y minutes.

> 1.1.2 Nous souhaitons effectuer un positionnement en arrière-plan, à quel moment faut-il démarrer
> et éteindre le monitoring des balises ?
> Sans le mettre en place, que faudrait-il faire pour pouvoir continuer le monitoring alors que
> l’activité n’est plus active ?

Le démarrage et l'arrêt du monitoring des balises se fait dans les méthodes `onCreate` et
`onDestroy`, respectivement.

Pour se faire il faut que l'application soit lancée une fois au moins, et que l'application soit
installée dans la mémoire interne (pas sur la carte SD).
Ensuite, le monitoring aura lieu en background, même après reboot du téléphone.

Extraits de la documentation expliquant l'implémentation d'une telle fonctionnalité :

```xml

<application android:name="com.example.MyApplicationName" android:allowBackup="true"
    android:icon="@drawable/ic_launcher" android:label="@string/app_name"
    android:theme="@style/AppTheme">
    ...
</application>
```

```kotlin
public class MyApplication extends Application {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monitoring)

        // TODO: Add code to obtain permissions from user        

        val beaconManager = BeaconManager.getInstanceForApplication(this)
        // If you wish to detect a different type of beacon than AltBeacon, use a different beacon parser for that beacon type in the line below       
        val region = BeaconRegion("wildcard altbeacon", AltBeaconParser(), null, null, null)
        // Set up a Live Data observer so this Activity can get monitoring callbacks 
        // observer will be called each time the monitored regionState changes (inside vs. outside region)
        beaconManager.getRegionViewModel(region).regionState.observeForever(monitoringObserver)
        beaconManager.startMonitoring(region)
    }

    val monitoringObserver = Observer<Int> { state ->
        if (state == MonitorNotifier.INSIDE) {
            Log.d(TAG, "Beacons detected")
        } else {
            Log.d(TAG, "No beacons detected")
        }
    }
    ...
}
```

> 1.1.3 On souhaite trier la liste des balises détectées de la plus proche à la plus éloignée,
> quelles sont les valeurs présentes dans les annonces reçues qui nous permettraient de le faire ?
> Comment sont-elles calculées et quelle est leur fiabilité ?

La valeur `distance` d'un Beacon est une estimation de la distance basé sur le `rssi` (Received
Signal Strength Indicator) et `txPower` la puissance de transmission du Beacon.

Il serait possible, sans utiliser `distance`, d'utiliser `rssi` et `txPower`, ce qui nous
permettrait
d'arriver à un résultat similaire que `distance`.

La formule utilisée est la suivante : distance = A * (rssi / t) ^ B + C
Avec t qui est la référence RSSI à 1 mètre et A, B, C qui sont des constantes.

La distance est estimée en utilisant une moyenne glissante du RSSI et la valeur de calibration de la
puissance de transmission incluse dans l'annonce du Beacon.

La fiabilité, en proche distance de 1m, les estimations de distance se situent entre 0.5 et 2m.
Il y a plus de fluctuation plus la distance augmente.
À 20m, cela peut varier de 10-40m.
Ces variations sont produites en raison du bruit sur la mesure du signal, d'éventuelles réflexions
et d'obstructions de celui-ci.

```kotlin
val sortedBeacons = beacons.sortedBy { it.distance }
// De plus, le beacon le plus proche est plus facilement trouvé :
_closestBeacon.postValue(sortedBeacons.firstOrNull())
```

> Hint : N’hésitez pas à mettre en place un filtre pour limiter la détection uniquement aux iBeacons
> de votre groupe, le numéro mineur des balises est indiqué sur celles-ci.

Ceci a été fait, de cette manière :

```kotlin
private val listId3 = arrayOf(46, 73)
beacons.filter { it.id3.toInt() in listId3 }
```

## Partie 2

### Questions d’approfondissement

> 2.1.1 Comment pouvons-nous déterminer notre position ?
> Est-ce uniquement basé sur notion de proximité étudiée dans la question 1.1.3, selon vous est-ce
> que d’autres paramètres peuvent être pertinents ?

Nous pouvons déterminer notre position à la balise le plus proche et en l'associant à une
position pré-enregistrée dans une base de données.

Dans notre implémentation, nous avons décidé de créer une Map qui associe un ID mineur de balise à
une chambre :

```kotlin
val locationMap = mapOf(
    46 to "Salon (46)",
    73 to "Couloir (73)",
)
```

Une possible amélioration serait d'utiliser un algorithme de trilatération, qui permettrait de
déterminer une position plus précise en utilisant plusieurs balises à la fois.
Ceci demanderait de connaître la position précise de chaque balise et de représenter les points dans
un espace 3D, ce qui sort du cadre de ce laboratoire de notre point de vue.

Un autre paramètre pertinent serait la direction de la balise grâce à l'angle (BLE >v5.1)


> 2.1.2 Les iBeacons sont conçus pour permettre du positionnement en intérieur.
> D’après l’expérience que vous avez acquise sur cette technologie dans ce laboratoire, quels sont
> les cas d’utilisation pour lesquels les iBeacons sont pleinement adaptés (minimum deux) ?
> Est-ce que vous voyez des limitations qui rendraient difficile leur utilisation pour certaines
> applications ?

Les iBeacons sont adaptés pour des applications de localisation en intérieur, comme par exemple :

- La navigation dans un bâtiment (ex. hôpital, musée, etc.) où l'on peut se déplacer librement et
  avoir besoin d'informations contextuelles sur les lieux.
- Pour des applications de marketing de proximité, où les utilisateurs peuvent recevoir des
  notifications contextuelles en fonction de leur position dans un magasin ou un centre commercial.

Les limitations principales sont :

- La précision de la localisation, qui peut varier en fonction de l'environnement (ex. murs, objets
  métalliques, etc.)
- La portée limitée des iBeacons, qui peut rendre difficile la localisation dans de grands espaces
