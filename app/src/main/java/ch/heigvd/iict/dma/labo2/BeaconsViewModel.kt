package ch.heigvd.iict.dma.labo2

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import ch.heigvd.iict.dma.labo2.models.PersistentBeacon


class BeaconsViewModel : ViewModel() {

    private val _nearbyBeacons = MutableLiveData(mutableListOf<PersistentBeacon>())

    /*
     *  Remarque
     *  Il est important que le contenu de la LiveData nearbyBeacons, écoutée par l'interface
     *  graphique, soit immutable. Si on réalise "juste" un cast de la MutableLiveData vers la
     *  LiveData, par ex:
     *  val nearbyBeacons : LiveData<MutableList<PersistentBeacon>> = _nearbyBeacons
     *  L'interface graphique disposera d'une référence vers la même instance de liste encapsulée
     *  dans la MutableLiveData et la LiveData, contenant les références vers les mêmes
     *  instances de PersistentBeacon.
     *
     *  Ce qui implique que lorsque nous mettrons à jour les données de _nearbyBeacons après une
     *  annonce de la librairie, la liste référencée dans l'adapteur de la RecyclerView (qui est
     *  la même) sera également modifiée, créant ainsi une désynchronisation entre les données
     *  affichées à l'écran et les données présentent dans l'adapteur. Les deux listes étant
     *  strictement les mêmes, DiffUtil ne détectera aucun changement et l'interface graphique ne
     *  sera pas mise à jour.
     *  La solution présentée ici est de réaliser une projection d'une MutableList vers une List et
     *  une copie profonde de toutes les instances de PersistentBeacon qu'elle contient.
     */
    private val BEACON_PERSISTENCE_TIME = 10 * 1000L // 10 seconds
    val nearbyBeacons : LiveData<List<PersistentBeacon>> = _nearbyBeacons.map { l -> l.toList().map { el -> el.copy() } }
    private val _closestBeacon = MutableLiveData<PersistentBeacon?>(null)
    val closestBeacon : LiveData<PersistentBeacon?> get() = _closestBeacon

    fun updateBeacons(beacons: List<PersistentBeacon>) {
        val currentList = _nearbyBeacons.value ?: mutableListOf()
        val currentTime = System.currentTimeMillis()

        // Update existing beacons or add new ones
        beacons.forEach { newBeacon ->
            // Try to find existing beacon with same UUID, major, minor
            val existingBeaconIndex = currentList.indexOfFirst {
                it.uuid == newBeacon.uuid && it.major == newBeacon.major && it.minor == newBeacon.minor
            }

            if (existingBeaconIndex >= 0) {
                // Update existing beacon
                val existingBeacon = currentList[existingBeaconIndex]
                existingBeacon.rssi = newBeacon.rssi
                existingBeacon.txPower = newBeacon.txPower
                existingBeacon.distance = newBeacon.distance
                existingBeacon.lastSeen = currentTime
            } else {
                // Add new beacon with current timestamp
                currentList.add(newBeacon.copy(lastSeen = currentTime))
            }
        }

        // Remove beacons that haven't been seen for BEACON_PERSISTENCE_TIME
        currentList.removeAll { (currentTime - it.lastSeen) > BEACON_PERSISTENCE_TIME }

        // Sort by distance
        currentList.sortBy { it.distance }

        _nearbyBeacons.postValue(currentList)
        _closestBeacon.postValue(if (currentList.isNotEmpty()) currentList[0] else null)
    }

    fun clearExpiredBeacons() {
        val currentList = _nearbyBeacons.value ?: mutableListOf()
        val currentTime = System.currentTimeMillis()

        // Remove beacons that haven't been seen for BEACON_PERSISTENCE_TIME
        val hadBeacons = currentList.isNotEmpty()
        currentList.removeAll { (currentTime - it.lastSeen) > BEACON_PERSISTENCE_TIME }
        currentList.sortBy { it.distance }

        if (hadBeacons) {
            _nearbyBeacons.postValue(currentList)
            _closestBeacon.postValue(if (currentList.isNotEmpty()) currentList[0] else null)
        }
    }

    val locationMap = mapOf(
        46 to "Salon (46)",
        73 to "Couloir (73)",
    )
}
