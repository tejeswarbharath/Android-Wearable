package com.example.android.sunshine.app;

/**
 * Created by android on 9/25/2016.
 */
public class Utility
{
    public static String getArtUrlForWeatherCondition(Long weatherId) {

        if (weatherId >= 200 && weatherId <= 232) {
            return "storm";
        } else if (weatherId >= 300 && weatherId <= 321) {
            return "light_rain";
        } else if (weatherId >= 500 && weatherId <= 504) {
            return "rain";
        } else if (weatherId == 511) {
            return "snow";
        } else if (weatherId >= 520 && weatherId <= 531) {
            return "rain";
        } else if (weatherId >= 600 && weatherId <= 622) {
            return "snow";
        } else if (weatherId >= 701 && weatherId <= 761) {
            return "fog";
        } else if (weatherId == 761 || weatherId == 781) {
            return "storm";
        } else if (weatherId == 800) {
            return "clear";
        } else if (weatherId == 801) {
            return "light_clouds";
        } else if (weatherId >= 802 && weatherId <= 804) {
            return "cloudy";
        }
        return null;
    }
}
