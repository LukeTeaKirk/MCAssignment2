package com.example.mcassignment2
import android.annotation.SuppressLint
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.*
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import kotlin.math.log

@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                getCurrentLocation()
            } else {
                // Handle permission denial, maybe update UI to show an error message
            }
        }
    private val currentLocation: MutableState<Location?> = mutableStateOf(null)
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        requestPermissionLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        setContent {
            WeatherApp()
        }
    }

    private fun getCurrentLocation() {
        fusedLocationProviderClient.lastLocation.addOnSuccessListener { location: Location? ->
            currentLocation.value = location
        }
    }

    @Composable
    fun WeatherApp() {
        val location by remember { currentLocation }
        val weatherData = remember { mutableStateOf<Pair<List<String>, List<String>>?>(null) }
        var errorMessage by remember { mutableStateOf("") }
        val coroutineScope = rememberCoroutineScope()

        location?.let {
            coroutineScope.launch {
                try {
                    weatherData.value = fetchWeather(it.latitude, it.longitude)
                } catch (e: Exception) {
                    errorMessage = "Failed to fetch weather data"
                    Log.d("WeatherApp", e.toString())
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Current Location: ${location?.latitude}, ${location?.longitude}")
            if (errorMessage.isNotEmpty()) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }
            weatherData.value?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Maximum Temperature: ${it.first[0]}°C")
                        Text("Minimum Temperature: ${it.first[1]}°C")
                        Text("Humidity: ${it.second[0]}%")
                        Text("Precipitation: ${it.second[1]}mm")
                        Text("Pressure: ${it.second[2]}hPa")
                        Text("UV Index: ${it.second[3]}")
                        Text("Visibility: ${it.second[4]}km")
                    }
                }
            }
        }
    }

    suspend fun fetchWeather(lat: Double, lon: Double): Pair<List<String>, List<String>> =
        withContext(Dispatchers.IO) {
            val currentDate = LocalDate.now()
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val formattedDate = currentDate.format(formatter)
            val (year, month, day) = formattedDate.split("-").map { it.toInt() }
            val formattedMonth = if (month < 10) "0$month" else "$month"
            val url =
                URL("https://weather.visualcrossing.com/VisualCrossingWebServices/rest/services/timeline/$lat,$lon/$year-$formattedMonth-$day/$year-$formattedMonth-$day?unitGroup=metric&include=days&key=VNJRVLJTXVJDMZ8FZKRVN7N4T&contentType=json")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(response)
            val daysArray = jsonResponse.getJSONArray("days")
            val dayInfo = daysArray.getJSONObject(0)

            val maxTemp = dayInfo.getDouble("feelslikemax").toString()
            val minTemp = dayInfo.getDouble("feelslikemin").toString()
            val humidity = dayInfo.getDouble("humidity").toString()
            val precip = dayInfo.getDouble("precip").toString()
            val pressure = dayInfo.getDouble("pressure").toString()
            val uvindex = dayInfo.getDouble("uvindex").toString()
            val visibility = dayInfo.getDouble("visibility").toString()

            connection.disconnect()

            Pair(listOf(maxTemp, minTemp), listOf(humidity, precip, pressure, uvindex, visibility))
        }
}
