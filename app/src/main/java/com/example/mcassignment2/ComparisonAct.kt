package com.example.mcassignment2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ComparisonAct : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val latitude = intent.getDoubleExtra("latitude", 0.0)
        val longitude = intent.getDoubleExtra("longitude", 0.0)

        setContent {
            WeatherComparisonScreen(latitude, longitude)
        }
    }

    @Composable
    fun WeatherComparisonScreen(lat: Double, lon: Double) {
        val coroutineScope = rememberCoroutineScope()
        val weatherDataCurrent = remember { mutableStateOf<List<String>?>(null) }
        val weatherDataHistorical = remember { mutableStateOf<List<String>?>(null) }

        LaunchedEffect(Unit) {
            coroutineScope.launch {
                weatherDataCurrent.value = fetchWeather(lat, lon)
                weatherDataHistorical.value = fetchWeather(lat, lon)  // This will be replaced with historical fetch logic later
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text("Weather Comparison at Location: Latitude $lat, Longitude $lon")
            DisplayWeatherTable(weatherDataCurrent.value, weatherDataHistorical.value)
        }
    }

    @Composable
    fun DisplayWeatherTable(currentWeather: List<String>?, historicalWeather: List<String>?) {
        if (currentWeather != null && historicalWeather != null) {
            val attributes = listOf("Maximum Temperature", "Minimum Temperature", "Humidity", "Precipitation", "Pressure", "UV Index", "Visibility")
            attributes.forEachIndexed { index, attribute ->
                Text("$attribute: Current - ${currentWeather[index]}°C, Historical - ${historicalWeather[index]}°C")
            }
        }
    }

    suspend fun fetchWeather(lat: Double, lon: Double): List<String> = withContext(Dispatchers.IO) {
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

        val weatherAttributes = listOf(
            dayInfo.getDouble("feelslikemax").toString(),
            dayInfo.getDouble("feelslikemin").toString(),
            dayInfo.getDouble("humidity").toString(),
            dayInfo.getDouble("precip").toString(),
            dayInfo.getDouble("pressure").toString(),
            dayInfo.getDouble("uvindex").toString(),
            dayInfo.getDouble("visibility").toString()
        )

        connection.disconnect()
        weatherAttributes
    }
}
