package com.michael.situativemusicrecommender;

import android.content.Context;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

/**
 * Created by Michael on 22.09.2017.
 */

public class dictionary {
    public static String formatDateTime(Context context, String timeToFormat) {

        String finalDateTime = "";

        SimpleDateFormat iso8601Format = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss");

        Date date = null;
        if (timeToFormat != null) {
            try {
                date = iso8601Format.parse(timeToFormat);
            } catch (ParseException e) {
                date = null;
            }

            if (date != null) {
                long when = date.getTime();
                int flags = 0;
                flags |= android.text.format.DateUtils.FORMAT_SHOW_TIME;
                flags |= android.text.format.DateUtils.FORMAT_SHOW_DATE;
                flags |= android.text.format.DateUtils.FORMAT_ABBREV_MONTH;
                flags |= android.text.format.DateUtils.FORMAT_SHOW_YEAR;

                finalDateTime = android.text.format.DateUtils.formatDateTime(context,
                        when + TimeZone.getDefault().getOffset(when), flags);
            }
        }
        return finalDateTime;
    }

    public static long dateToMilliseconds(Date date){
        return date.getTime();
    }



    static int checkForTagId(Map.Entry entry) {
        int type = 0;
        switch ((String) entry.getKey()){
            case "leisure":
                switch ((String) entry.getValue()){
                    case "fitness_centre":
                    case "swimming_pool":
                    case "track":
                        type = 2;
                        break;
                    case "adult_gaming_centre":
                        type = 15;
                        break;
                    case "amusement_arcade":
                        type = 4;
                        break;
                    case "beach_resort":
                    case "bird_hide":
                    case "park":
                    case "summer_camp":
                        type = 5;
                        break;
                    case "bandstand":
                    case "dance":
                        type = 6;
                        break;
                    case "sports_centre":
                    case "stadium":
                        type = 7;
                        break;
                    case "common":
                        type = 9;
                        break;
                    default:
                        type = 1;
                        break;
                }
                break;
            case "amenity":
                switch ((String) entry.getValue()){
                    case "bar":
                    case "bbq":
                    case "cafe":
                    case "drinking_water":
                    case "fountain":
                    case "food_court":
                    case "ice_cream":
                    case "restaurant":
                    case "community_centre":
                        type = 9;
                        break;
                    case "biergarten":
                    case "nightclub":
                    case "pub":
                        type = 10;
                        break;
                    case "college":
                    case "library":
                    case "public_bookcase":
                    case "school":
                    case "language_school":
                    case "driving_school":
                    case "university":
                        type = 11;
                        break;
                    case "kindergarten":
                        type = 12;
                        break;
                    case "baby_hatch":
                    case "clinic":
                    case "dentist":
                    case "doctors":
                    case "hospital":
                    case "nursing_home":
                    case "pharmacy":
                    case "veterinary":
                        type = 13;
                        break;
                    case "arts_centre":
                    case "theatre":
                        type = 14;
                        break;
                    case "brothel":
                    case "casino":
                    case "gambling":
                    case "stripclub":
                    case "swingerclub":
                        type = 15;
                        break;
                    case "social_facility":
                        type = 16;
                        break;
                    case "place_of_worship":
                        type = 17;
                    default:
                        type = 8;
                        break;
                }
                break;
            case "natural":
                type = 5;
                break;
            case "building":
                switch ((String) entry.getValue()){
                    case "commercial":
                    case "industrial":
                    case "warehouse":
                        type = 19;
                        break;
                    case "retail":
                        type = 20;
                        break;
                    default:
                        type = 18;
                        break;
                }
                break;
            case "historic":
                type = 21;
                break;
            case "healthcare":
                type = 13;
                break;
        }
        return type;
    }
}
