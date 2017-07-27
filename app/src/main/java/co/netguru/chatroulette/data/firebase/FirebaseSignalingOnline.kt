package co.netguru.chatroulette.data.firebase

import co.netguru.chatroulette.app.App
import co.netguru.chatroulette.data.model.RouletteConnectionFirebase
import com.google.firebase.database.*
import io.reactivex.Completable
import io.reactivex.Maybe
import timber.log.Timber
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseSignalingOnline @Inject constructor(private val firebaseDatabase: FirebaseDatabase) {

    companion object {
        private const val ONLINE_DEVICES_PATH = "online_devices/"
    }

    fun connectAndRetrieveRandomDevice(): Maybe<String> = Completable.create {
        firebaseDatabase.goOnline()
        val firebaseOnlineReference = firebaseDatabase.getReference("online_devices/${App.CURRENT_DEVICE_UUID}")
        with(firebaseOnlineReference) {
            onDisconnect().removeValue()
            setValue(RouletteConnectionFirebase())
        }
        it.onComplete()
    }.andThen(chooseRandomDevice())

    fun disconnect(): Completable = Completable.create {
        firebaseDatabase.goOffline()

        it.onComplete()
    }

    fun chooseRandomDevice(): Maybe<String> = Maybe.create {
        var lastUuid: String? = null

        firebaseDatabase.getReference("online_devices").runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                lastUuid = null
                val genericTypeIndicator = object : GenericTypeIndicator<MutableMap<String, RouletteConnectionFirebase>>() {}
                val availableDevices = mutableData.getValue(genericTypeIndicator) ?:
                        return Transaction.success(mutableData)

                val removedSelfValue = availableDevices.remove(App.CURRENT_DEVICE_UUID)

                if (removedSelfValue != null && !availableDevices.isEmpty()) {
                    lastUuid = deleteRandomDevice(availableDevices)
                    mutableData.value = availableDevices
                }

                return Transaction.success(mutableData)
            }

            fun deleteRandomDevice(availableDevices: MutableMap<String, RouletteConnectionFirebase>): String {
                val devicesCount = availableDevices.count()
                val randomDevicePosition = SecureRandom().nextInt(devicesCount)
                val randomDeviceToRemoveUuid = availableDevices.keys.toList()[randomDevicePosition]
                Timber.d("Device number $randomDevicePosition from $devicesCount devices was chosen.")
                availableDevices.remove(randomDeviceToRemoveUuid)
                return randomDeviceToRemoveUuid
            }

            override fun onComplete(databaseError: DatabaseError?, completed: Boolean, p2: DataSnapshot?) {
                if (databaseError != null) {
                    it.onError(databaseError.toException())
                } else if (completed && lastUuid != null) {
                    it.onSuccess(lastUuid as String)
                }
                it.onComplete()
            }
        })
    }
}