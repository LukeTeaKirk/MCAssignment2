package com.example.mcassignment2
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.app.ActivityCompat
import androidx.room.Dao
import androidx.room.Database
import com.example.mcassignment2.ui.theme.MCAssignment2Theme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeParseException
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity
data class WeatherData(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val maxTemp: String,
    val minTemp: String,
    val latitude: Double,
    val longitude: Double
)
@Dao
interface WeatherDao {
    @Insert
    suspend fun insertWeatherData(weatherData: WeatherData)
    @Query("SELECT * FROM WeatherData WHERE strftime('%m-%d', date) = strftime('%m-%d', :date) AND latitude = :latitude AND longitude = :longitude ORDER BY date DESC LIMIT 10")
    suspend fun getLastTenYearsData(date: String, latitude: Double, longitude: Double): List<WeatherData>

    @Query("SELECT * FROM WeatherData WHERE date = :date AND latitude = :latitude AND longitude = :longitude LIMIT 1")
    suspend fun queryWeatherData(date: String, latitude: Double, longitude: Double): WeatherData?
}
@Database(entities = [WeatherData::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun weatherDao(): WeatherDao
}

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
        } else {
        }
    }
    private val currentLocation: MutableState<Location?> = mutableStateOf(null)
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        requestLocationUpdate()
        setContent {
            MCAssignment2Theme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    WeatherApp(this@MainActivity, currentLocation)
                }
            }
        }
    }

    private fun requestLocationUpdate() {
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
            interval = 10000
            fastestInterval = 5000
        }
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val newLocation = locationResult.lastLocation
                currentLocation.value = newLocation
            }
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }
}

@SuppressLint("MissingPermission")
@Composable
fun WeatherApp(context: Context, locationState: State<Location?>) {
    val location by remember { locationState }
    var dateInput by remember { mutableStateOf("") }
    var maxTemp by remember { mutableStateOf("Not Available") }
    var minTemp by remember { mutableStateOf("Not Available") }
    var errorMessage by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = dateInput,
            onValueChange = {
                dateInput = it
                errorMessage = ""
            },
            label = { Text("Enter date (YYYY-MM-DD)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        if (errorMessage.isNotEmpty()) {
        Text(errorMessage, color = MaterialTheme.colorScheme.error)
        }
        Button(onClick = {
            try {
                val enteredDate = LocalDate.parse(dateInput)
                val currentDate = LocalDate.now()
                if (enteredDate.isBefore(currentDate)) {
                    coroutineScope.launch {
                        location?.let {
                            try {
                                val weatherData = fetchWeather(dateInput, it.latitude, it.longitude)
                                maxTemp = weatherData.first
                                minTemp = weatherData.second
                            } catch(e: Exception) {
                                errorMessage = "Failed to fetch weather data"
                                Log.e("weather", "Failed to fetch weather data", e)
                            }
                        }
                    }
                } else {
                    errorMessage = "Date must be before today"
                }
            } catch (e: DateTimeParseException) {
                errorMessage = "Invalid date format"
            }
        }) {
            Text("Get Weather")
        }
        Button(onClick = {
            coroutineScope.launch {
                location?.let { loc ->
                    val weatherData = WeatherData(
                        date = dateInput,
                        maxTemp = maxTemp,
                        minTemp = minTemp,
                        latitude = loc.latitude,
                        longitude = loc.longitude
                    )
                    val db = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java, "weather-database"
                    ).build()
                    db.weatherDao().insertWeatherData(weatherData)
                }
            }
        }) {
            Text("Save Weather Data")
        }
        Button(onClick = {
            val enteredDate = LocalDate.parse(dateInput)
            val currentDate = LocalDate.now()
            if (enteredDate.isAfter(currentDate)) {
                coroutineScope.launch(Dispatchers.IO) {
                    location?.let { loc ->
                        try {
                            val db = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "weather-database").build()
                            val weatherDataPoints = db.weatherDao().getLastTenYearsData(dateInput, loc.latitude, loc.longitude)
                            if (weatherDataPoints.size == 10) {
                                val averageMaxTemp = weatherDataPoints.map { it.maxTemp.toDouble() }.average()
                                val averageMinTemp = weatherDataPoints.map { it.minTemp.toDouble() }.average()
                                maxTemp = String.format("%.2f", averageMaxTemp)
                                minTemp = String.format("%.2f", averageMinTemp)
                            } else {
                                errorMessage = "No historical data found"
                            }
                        } catch (e: Exception) {
                            errorMessage = "Error querying historical data"
                            Log.e("WeatherApp", "Error querying historical data", e)
                        }
                    }
                }
            } else {
                coroutineScope.launch(Dispatchers.IO) {
                    location?.let { loc ->
                        try {
                            val db = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "weather-database").build()
                            val queriedData = db.weatherDao()
                                .queryWeatherData(dateInput, loc.latitude, loc.longitude)
                            if (queriedData != null) {
                                maxTemp = queriedData.maxTemp
                                minTemp = queriedData.minTemp
                            } else {
                                errorMessage = "No data found for this date and location"
                            }
                        } catch (e: Exception) {
                            errorMessage = "Error querying the database"
                            Log.e("WeatherApp", "Error querying the database", e)
                        }
                    }
                }
            }
        }) {
            Text("Query Weather Data")
        }
        if (errorMessage.isNotEmpty()) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error)
        }

        Text("Maximum Temperature: $maxTemp°C")
        Text("Minimum Temperature: $minTemp°C")
    }
}
suspend fun fetchWeather(date: String, lat: Double, lon: Double): Pair<String, String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
    val (year, month, day) = date.split("-").map { it.toInt() }
    val url = URL("https://weather.visualcrossing.com/VisualCrossingWebServices/rest/services/timeline/$lat,$lon/$year-$month-$day/$year-$month-$day?unitGroup=metric&include=days&key=VNJRVLJTXVJDMZ8FZKRVN7N4T&contentType=json")
    Log.d("weather", "fetchWeather: $url")
    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    val response = connection.inputStream.bufferedReader().use { it.readText() }
    val jsonResponse = JSONObject(response)
    val daysArray = jsonResponse.getJSONArray("days")
    val dayInfo = daysArray.getJSONObject(0)
    val maxTemp = dayInfo.getDouble("feelslikemax").toString()
    val minTemp = dayInfo.getDouble("feelslikemin").toString()
    connection.disconnect()
    return@withContext Pair(maxTemp, minTemp)
}