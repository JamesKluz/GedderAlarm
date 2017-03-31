/*
 * USER: mslm
 * DATE: 3/27/17
 */

package com.gedder.gedderalarm.model;


import android.os.Bundle;

import com.gedder.gedderalarm.google.JsonParser;
import com.gedder.gedderalarm.google.UrlGenerator;
import com.gedder.gedderalarm.util.HttpRequest;
import com.gedder.gedderalarm.util.except.GoogleMapsAPIException;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

/**
 * The engine goes through the following pipeline:
 *
 * <ol>
 *     <li>Get or reuse as input (Origin, Destination, Arrival Time, Prep Time, Upper Bound Time)
 *     to query Google Maps API.</li>
 *     <li>Generate a URL to query Google Maps API with the above parameters in whatever
 *     fashion befits the URL requirements or the engine's requirement for certain information.</li>
 *     <li>Query Google Maps API using the generated URL.</li>
 *     <li>Store the response JSON for analysis.</li>
 *     <li>Analyze the response JSON.</li>
 *     <li>Decide on what to do.</li>
 * </ol>
 *
 * After analyzing the response JSON, it'll determine what action to take. It may do one of the
 * following:
 *
 * <ul>
 *     <li>Reschedule itself to flow through the pipeline for some default set time in the
 *     future.</li>
 *     <li>Reschedule itself to flow through the pipeline for some adjusted time in the future.</li>
 *     <li>Trigger the alarm.</li>
 * </ul>
 *
 * In the first case, the engine sees no reason for concern, and just carries on rescheduling
 * itself for future analysis at some default rate.
 *
 * In the second case, the engine picks up a possible delay, but it is not urgent enough that
 * the user needs to wake up right now. It'll reschedule itself for future analysis, but at a
 * tighter bound (how much tighter depends on the severity of the delay relative to the "wish"
 * alarm time).
 *
 * In the last case, the engine makes the decision to wake the user up right now: it caught a delay
 * significant enough that if the user doesn't take action now, they'll be late to their
 * destination.
 *
 * Each time the engine sets itself up for a future flow through the pipeline, it checks how close
 * it is to the currently planned alarm time, or the new predicted alarm time in case of a delay.
 * The closer it gets, the more often it queries for updates on traffic and possible delays. This
 * carries on until the engine cannot make any more room for future analyses, and must trigger the
 * alarm earlier than expected, because the delay isn't going away.
 *
 * To sum, the engine can either reschedule itself for a default rate of analysis, or a tighter
 * rate, or trigger the alarm, all depending on information it has previously received and analyzed.
 *
 * In this way, the engine ensures it's awake and aware of any delays at all times, and it becomes
 * increasingly active when there's a concern, i.e. when delays are present.
 */
public final class GedderEngine {
    private static final String TAG = GedderEngine.class.getSimpleName();

    public static final String RESULT_DURATION = "__GEDDER_ENGINE_RESULT_DURATION__";
    public static final String RESULT_DURATION_IN_TRAFFIC =
            "__GEDDER_ENGINE_RESULT_DURATION_IN_TRAFFIC__";
    public static final String RESULT_WARNINGS = "__GEDDER_ENGINE_WARNINGS__";

    // We get this in some secret way. For development, just keep a local API key in a file that
    // is ignored by git.
    private static final String apiKey = "qweoihuihuyhuyfbyu this isn't an api key";

    private GedderEngine() {}

    public static Bundle start(String origin, String destination,
                               long arrivalTime, long prepTime, long upperBoundTime) {
        String url  = ""
             , json = ""
             , err  = "";
        int duration          = -1
          , durationInTraffic = -1;
        ArrayList<String> warnings;

        // Generate URL
        url = new UrlGenerator.UrlBuilder(origin, destination, apiKey)
                .arrivalTime(arrivalTime)
                .departureTime(upperBoundTime + prepTime)
                .build().toString();

        // Query API
        try {
            json = new HttpRequest().execute(url).get();
        } catch (ExecutionException e) {
            // TODO: Handle exception.
        } catch (InterruptedException e) {
            // TODO: Handle exception.
        }

        // Parse the relevant variables.
        JsonParser parser = new JsonParser(json);
        err = parser.errorMessage();
        if (err.equals(""))
            throw new GoogleMapsAPIException();

        duration = parser.duration();
        durationInTraffic = parser.durationInTraffic();
        warnings = parser.warnings();

        Bundle results = new Bundle();
        results.putInt(RESULT_DURATION, duration);
        results.putInt(RESULT_DURATION_IN_TRAFFIC, durationInTraffic);
        results.putSerializable(RESULT_WARNINGS, warnings);
        return results;
    }
}
