package com.sengine.engine.gameplay

import kotlin.math.*

/**
 * S Engine Ultimate 6.1 [Quantum] - World Time & Weather System V2
 * Day/Night Cycle, Calendar, Seasons, Advanced Weather, Ocean FFT
 */
enum class Season { Spring, Summer, Autumn, Winter }
enum class WeatherTypeV2 { Clear, Cloudy, Rain, Thunderstorm, Snow, Fog, Sandstorm, Blizzard, Windy }
enum class MoonPhase { New, WaxingCrescent, FirstQuarter, WaxingGibbous, Full, WaningGibbous, LastQuarter, WaningCrescent }

data class CalendarDate(
    var year: Int = 2026,
    var month: Int = 1,
    var day: Int = 1,
    var hour: Int = 12,
    var minute: Int = 0,
    var second: Float = 0f
) {
    fun toStringFormatted(): String = "%04d-%02d-%02d %02d:%02d:%02d".format(year, month, day, hour, minute, second.toInt())
    fun dayOfYear(): Int {
        val daysInMonth = intArrayOf(31,28,31,30,31,30,31,31,30,31,30,31)
        var doy = day
        for (m in 0 until month-1) doy+=daysInMonth[m]
        return doy
    }
}

data class CelestialBody(
    var name: String = "Sun",
    var azimuth: Float = 0f, // degrees
    var altitude: Float = 45f, // degrees
    var intensity: Float = 1f,
    var color: Int = 0xFFFFFF,
    var angularDiameter: Float = 0.5f // degrees
)

class WorldTimeSystem {
    var date = CalendarDate()
    var timeScale: Float = 60f // 1 real second = 60 game seconds
    var dayLengthMinutes: Float = 24f // real minutes for full day
    var timeOfDay: Float = 12f // 0-24
    var paused: Boolean = false
    var season: Season = Season.Spring
    var seasonProgress: Float = 0f // 0-1 within season
    var yearProgress: Float = 0f // 0-1 within year

    // Sun & Moon
    var sun = CelestialBody(name="Sun", altitude=45f, intensity=1f, color=0xFFFFFF)
    var moon = CelestialBody(name="Moon", altitude=-10f, intensity=0.1f, color=0xCCCCFF)
    var moonPhase: MoonPhase = MoonPhase.Full
    var latitude: Float = 45f
    var longitude: Float = 0f

    // Weather
    var currentWeather: WeatherTypeV2 = WeatherTypeV2.Clear
    var targetWeather: WeatherTypeV2 = WeatherTypeV2.Clear
    var weatherTransition: Float = 0f // 0-1
    var weatherTransitionDuration: Float = 30f
    var weatherTimer: Float = 0f
    var rainIntensity: Float = 0f
    var snowIntensity: Float = 0f
    var fogDensity: Float = 0.01f
    var windSpeed: Float = 2f
    var windDirection: Float = 0f // degrees
    var temperature: Float = 20f // Celsius
    var humidity: Float = 0.5f

    fun update(dt: Float) {
        if (paused) return
        val scaledDt = dt * timeScale

        // Update time of day
        timeOfDay+=scaledDt/3600f // seconds to hours
        if (timeOfDay >= 24f) {
            timeOfDay-=24f
            date.day++
            if (date.day > 30) { date.day=1; date.month++; if (date.month > 12) { date.month=1; date.year++ } }
        }
        date.hour = timeOfDay.toInt()
        date.minute = ((timeOfDay-date.hour)*60f).toInt()
        date.second = ((timeOfDay-date.hour)*60f-date.minute)*60f

        // Update season
        val dayOfYear = date.dayOfYear()
        yearProgress = dayOfYear/365f
        season = when {
            dayOfYear < 80 || dayOfYear >= 355 -> Season.Winter
            dayOfYear < 172 -> Season.Spring
            dayOfYear < 264 -> Season.Summer
            else -> Season.Autumn
        }
        seasonProgress = when (season) {
            Season.Spring -> (dayOfYear-80)/92f
            Season.Summer -> (dayOfYear-172)/92f
            Season.Autumn -> (dayOfYear-264)/91f
            Season.Winter -> if (dayOfYear >= 355) (dayOfYear-355)/70f else (dayOfYear+10)/80f
        }.coerceIn(0f,1f)

        // Update sun position
        updateSunPosition()

        // Update moon
        updateMoonPosition()

        // Update weather
        updateWeather(dt)

        // Update temperature based on season and time
        updateTemperature()
    }

    fun updateSunPosition() {
        // Simplified solar position
        val hourAngle = (timeOfDay-12f)*15f // degrees per hour
        val declination = 23.45f * sin((360f/365f*(date.dayOfYear()-81))*PI.toFloat()/180f)
        val latRad = latitude*PI.toFloat()/180f
        val decRad = declination*PI.toFloat()/180f
        val hourRad = hourAngle*PI.toFloat()/180f

        val altitudeRad = asin(sin(latRad)*sin(decRad) + cos(latRad)*cos(decRad)*cos(hourRad))
        val azimuthRad = acos((sin(decRad)-sin(altitudeRad)*sin(latRad))/(cos(altitudeRad)*cos(latRad)))

        sun.altitude = altitudeRad*180f/PI.toFloat()
        sun.azimuth = azimuthRad*180f/PI.toFloat()
        sun.intensity = (sin(altitudeRad).coerceAtLeast(0f) * 1.2f).coerceIn(0f,1.5f)
    }

    fun updateMoonPosition() {
        // Moon is roughly opposite sun with phase offset
        moon.azimuth = (sun.azimuth+180f) % 360f
        moon.altitude = -sun.altitude*0.5f
        // Moon phase based on day
        val phaseDay = date.day % 28
        moonPhase = when (phaseDay) {
            in 0..2 -> MoonPhase.New
            in 3..6 -> MoonPhase.WaxingCrescent
            in 7..9 -> MoonPhase.FirstQuarter
            in 10..13 -> MoonPhase.WaxingGibbous
            in 14..16 -> MoonPhase.Full
            in 17..20 -> MoonPhase.WaningGibbous
            in 21..23 -> MoonPhase.LastQuarter
            else -> MoonPhase.WaningCrescent
        }
        moon.intensity = when (moonPhase) {
            MoonPhase.Full -> 0.3f
            MoonPhase.New -> 0.01f
            else -> 0.15f
        }
    }

    fun updateWeather(dt: Float) {
        if (currentWeather != targetWeather) {
            weatherTransition+=dt/weatherTransitionDuration
            if (weatherTransition >= 1f) {
                currentWeather=targetWeather
                weatherTransition=0f
            }
        }
        // Update intensities based on weather
        rainIntensity = when (currentWeather) {
            WeatherTypeV2.Rain -> 0.5f + weatherTransition*0.5f
            WeatherTypeV2.Thunderstorm -> 1f
            else -> 0f
        }
        snowIntensity = when (currentWeather) {
            WeatherTypeV2.Snow -> 0.5f
            WeatherTypeV2.Blizzard -> 1f
            else -> 0f
        }
        fogDensity = when (currentWeather) {
            WeatherTypeV2.Fog -> 0.05f
            WeatherTypeV2.Sandstorm -> 0.1f
            WeatherTypeV2.Clear -> 0.005f
            else -> 0.02f
        }
        windSpeed = when (currentWeather) {
            WeatherTypeV2.Windy -> 10f
            WeatherTypeV2.Thunderstorm -> 15f
            WeatherTypeV2.Sandstorm -> 20f
            WeatherTypeV2.Blizzard -> 12f
            else -> 2f
        }

        // Random weather changes
        weatherTimer+=dt
        if (weatherTimer > 300f) { // 5 minutes
            weatherTimer=0f
            if (Math.random().toFloat() < 0.3f) {
                targetWeather = WeatherTypeV2.values().random()
                weatherTransition=0f
            }
        }
    }

    fun updateTemperature() {
        val baseTemp = when (season) {
            Season.Spring -> 15f
            Season.Summer -> 25f
            Season.Autumn -> 10f
            Season.Winter -> -5f
        }
        val timeFactor = sin((timeOfDay-6f)/12f*PI.toFloat())*5f // warmer at noon
        temperature = baseTemp + timeFactor + (humidity-0.5f)*5f
    }

    @JvmName("setTimeOfDayExplicit")
    fun setTimeOfDay(hours: Float) { timeOfDay = hours.coerceIn(0f,24f) }

    fun setWeather(type: WeatherTypeV2, transitionDuration: Float = 10f) {
        targetWeather=type; weatherTransitionDuration=transitionDuration; weatherTransition=0f
    }

    fun isDay(): Boolean = sun.altitude > 0f
    fun isNight(): Boolean = sun.altitude <= 0f
    fun getSunDirection(): FloatArray {
        val altRad = sun.altitude*PI.toFloat()/180f; val azRad = sun.azimuth*PI.toFloat()/180f
        return floatArrayOf(cos(altRad)*sin(azRad), sin(altRad), cos(altRad)*cos(azRad))
    }

    fun getStats(): String = "Time:${"%02d:%02d".format(date.hour, date.minute)} Day:${date.day} Season:$season Weather:$currentWeather Temp:${temperature.toInt()}C Sun:${sun.altitude.toInt()}°"
}

// Ocean FFT System
data class OceanWave(
    var amplitude: Float = 1f,
    var frequency: Float = 1f,
    var directionX: Float = 1f,
    var directionZ: Float = 0f,
    var speed: Float = 1f,
    var steepness: Float = 0.5f
)

class OceanFFTSystem {
    var waves = mutableListOf<OceanWave>()
    var time: Float = 0f
    var windSpeed: Float = 5f
    var windDirection: Float = 0f
    var choppiness: Float = 1f
    var gravity: Float = 9.81f

    fun addWave(amplitude: Float, frequency: Float, dirX: Float, dirZ: Float, speed: Float = 1f): OceanWave {
        val wave = OceanWave(amplitude=amplitude, frequency=frequency, directionX=dirX, directionZ=dirZ, speed=speed)
        waves.add(wave); return wave
    }

    fun createDefaultOcean() {
        waves.clear()
        addWave(0.5f, 0.1f, 1f, 0.3f, 1f)
        addWave(0.3f, 0.2f, 0.8f, 0.6f, 1.2f)
        addWave(0.2f, 0.4f, -0.5f, 0.8f, 0.8f)
        addWave(0.1f, 0.8f, 0.3f, -0.9f, 1.5f)
    }

    fun getHeightAt(x: Float, z: Float): Float {
        var height = 0f
        for (wave in waves) {
            val k = wave.frequency
            val c = sqrt(gravity/k)
            val dot = x*wave.directionX + z*wave.directionZ
            val phase = k*dot - c*wave.speed*time
            height+=wave.amplitude*sin(phase)
        }
        return height
    }

    fun getNormalAt(x: Float, z: Float, epsilon: Float = 0.1f): FloatArray {
        val hL = getHeightAt(x-epsilon, z)
        val hR = getHeightAt(x+epsilon, z)
        val hD = getHeightAt(x, z-epsilon)
        val hU = getHeightAt(x, z+epsilon)
        val nx = hL-hR; val nz = hD-hU; val ny = 2f*epsilon
        val len = sqrt(nx*nx+ny*ny+nz*nz).coerceAtLeast(0.0001f)
        return floatArrayOf(nx/len, ny/len, nz/len)
    }

    fun update(dt: Float) { time+=dt }

    fun getStats(): String = "Ocean Waves:${waves.size} Time:${"%.1f".format(time)} Wind:$windSpeed"
}
