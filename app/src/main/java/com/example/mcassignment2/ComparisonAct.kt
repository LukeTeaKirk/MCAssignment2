package com.example.mcassignment2

import android.content.Intent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

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
        val backgroundRes = remember { mutableStateOf(R.drawable.default_background) } // Default background resource

        LaunchedEffect(Unit) {
            coroutineScope.launch {
                weatherDataCurrent.value = fetchWeather(lat, lon)
                weatherDataHistorical.value = fetchHistoricalWeather(lat, lon)
            }
        }

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Image(
                painter = painterResource(id = backgroundRes.value),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )

            Column(modifier = Modifier.padding(16.dp)) {
                Text("Weather Comparison at Location: Latitude $lat, Longitude $lon")
                DisplayWeatherTable(weatherDataCurrent.value, weatherDataHistorical.value)
                Button(onClick = { navigateToMain() }) {
                    Text("Compare Weather")
                }
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {}
        startActivity(intent)
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
    suspend fun fetchHistoricalWeather(lat: Double, lon: Double): List<String> = withContext(Dispatchers.IO) {
        val currentDate = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val (_, month, day) = currentDate.format(formatter).split("-").map { it.toInt() }
        val formattedMonth = month.toString().padStart(2, '0')
        val formattedDay = day.toString().padStart(2, '0')

        val weatherAttributesAccumulator = MutableList(7) { 0.0 }
        val yearsToFetch = 2

        for (yearOffset in 0 until yearsToFetch) {
            val year = currentDate.year - yearOffset
            val url = URL("https://weather.visualcrossing.com/VisualCrossingWebServices/rest/services/timeline/$lat,$lon/$year-$formattedMonth-$formattedDay/$year-$formattedMonth-$formattedDay?unitGroup=metric&include=days&key=VNJRVLJTXVJDMZ8FZKRVN7N4T&contentType=json")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(response)
            val dayInfo = jsonResponse.getJSONArray("days").getJSONObject(0)

            weatherAttributesAccumulator[0] += dayInfo.getDouble("feelslikemax")
            weatherAttributesAccumulator[1] += dayInfo.getDouble("feelslikemin")
            weatherAttributesAccumulator[2] += dayInfo.getDouble("humidity")
            weatherAttributesAccumulator[3] += dayInfo.getDouble("precip")
            weatherAttributesAccumulator[4] += dayInfo.getDouble("pressure")
            weatherAttributesAccumulator[5] += dayInfo.getDouble("uvindex")
            weatherAttributesAccumulator[6] += dayInfo.getDouble("visibility")

            connection.disconnect()
        }

        val averageWeatherAttributes = weatherAttributesAccumulator.map { (it / yearsToFetch).toString() }
        averageWeatherAttributes
    }

}
