package com.clean.aa
 
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.Locale

class QiblaA2 : AppCompatActivity(), LocationListener, SensorEventListener {

    private lateinit var client: FusedLocationProviderClient
    private lateinit var service: GitHubService
    private lateinit var manager: SensorManager
    private var sensor: Sensor? = null
    private var currentDegree = 0f
    private var qiblaDegree = 0.0
    private lateinit var degreeText: TextView
    private lateinit var locationManager: LocationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qibla)

        // Set Arabic locale
        val locale = Locale("ar")
        Locale.setDefault(locale)
        val config = Configuration()
        config.locale = locale
        baseContext.resources.updateConfiguration(config, baseContext.resources.displayMetrics)
        title = getString(R.string.qibla_compass_title)

        val toUse = findViewById<TextView>(R.id.to_use)
        val makeSure = findViewById<TextView>(R.id.make_sure)
        val gpsText = findViewById<TextView>(R.id.gps_txt)
        degreeText = findViewById(R.id.degree_text)

        toUse.text = getString(R.string.to_use_the_compass_properly)
        makeSure.text = getString(R.string.make_sure_your_connection_is_enabled)
        gpsText.text = getString(R.string.make_sure_your_location_is_enabled)

        val retrofit = Retrofit.Builder()
            .baseUrl("http://api.aladhan.com/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        service = retrofit.create(GitHubService::class.java)
        client = LocationServices.getFusedLocationProviderClient(this)

        Dexter.withContext(this)
            .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object : PermissionListener {
                override fun onPermissionGranted(response: PermissionGrantedResponse) {
                    if (ActivityCompat.checkSelfPermission(
                            this@QiblaA2,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(
                            this@QiblaA2,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }

                    locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    manager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
                    sensor = manager.getDefaultSensor(Sensor.TYPE_ORIENTATION)

                    if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        sensor?.let {
                            manager.registerListener(
                                this@QiblaA2,
                                it,
                                SensorManager.SENSOR_DELAY_NORMAL
                            )
                        }
                    } else {
                        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        startActivityForResult(intent, 4004)
                        Toast.makeText(
                            this@QiblaA2,
                            getString(R.string.make_sure_your_location_is_enabled),
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    val location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    if (location != null) {
                        Log.d("TAG", "onCreate: Location found")
                        onLocationChanged(location)
                    } else {
                        Log.d("TAG", "onCreate: Location null")
                    }
                }

                override fun onPermissionDenied(response: PermissionDeniedResponse) {
                    Toast.makeText(
                        this@QiblaA2,
                        "Permission denied. Cannot get location.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onPermissionRationaleShouldBeShown(
                    permission: PermissionRequest,
                    token: PermissionToken
                ) {
                    token.continuePermissionRequest()
                }
            }).check()
    }

    override fun onDestroy() {
        super.onDestroy()
        manager.unregisterListener(this, sensor)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val compass = findViewById<ImageView>(R.id.compass)
        val degree = event.values[0].toInt()
        degreeText.text = "${event.values[0]}°"

        val rotate = RotateAnimation(
            currentDegree,
            (-degree + qiblaDegree).toFloat(),
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        rotate.duration = 300
        rotate.fillAfter = true
        findViewById<ImageView>(R.id.compass).startAnimation(rotate)
        currentDegree = (-degree + qiblaDegree).toFloat()

        if (degree in (qiblaDegree - 3).toInt()..(qiblaDegree + 3).toInt()) {
            compass.setColorFilter(getColor(R.color.colorPrimaryDark))
        } else {
            compass.setColorFilter(getColor(android.R.color.black))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    interface GitHubService {
        @GET("qibla/{latitude}/{longitude}")
        fun getDegree(
            @Path("latitude") latitude: String,
            @Path("longitude") longitude: String
        ): Call<QiblaResponse>
    }

    override fun onLocationChanged(location: Location) {
        Log.d("TAG", "onLocationChanged: ${location.latitude}, ${location.longitude}")

        service.getDegree(location.latitude.toString(), location.longitude.toString())
            .enqueue(object : Callback<QiblaResponse> {
                override fun onResponse(call: Call<QiblaResponse>, response: Response<QiblaResponse>) {
                    val body = response.body()
                    if (body != null) {
                        qiblaDegree = body.data.direction
                        Log.d("TAG", "Qibla Direction: $qiblaDegree")
                    }
                }

                override fun onFailure(call: Call<QiblaResponse>, t: Throwable) {
                    Log.e("TAG", "API Error: ${t.message}")
                }
            })
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}

// -------------------- MODEL CLASSES --------------------

data class QiblaResponse(
    val code: Int,
    val status: String,
    val data: QiblaData
)

data class QiblaData(
    val latitude: Double,
    val longitude: Double,
    val direction: Double
)
