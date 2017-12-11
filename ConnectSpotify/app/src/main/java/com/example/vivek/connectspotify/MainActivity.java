package com.example.vivek.connectspotify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Error;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerEvent;
import com.spotify.sdk.android.player.Spotify;
import com.spotify.sdk.android.player.SpotifyPlayer;
import com.wrapper.spotify.Api;
import com.wrapper.spotify.methods.AlbumRequest;
import com.wrapper.spotify.models.Album;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.SortedSet;
import java.util.TreeSet;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.PlaylistsPager;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class MainActivity extends Activity implements
        SpotifyPlayer.NotificationCallback, ConnectionStateCallback
{

    // TODO: Replace with your client ID
    private static final String CLIENT_ID = "e6933ce00e42496d8d8db078ea9488ca";
    // TODO: Replace with your redirect URI
    private static final String REDIRECT_URI = "connectspotify://callback";

    private Player mPlayer;

    private Api api;

    private SpotifyApi spotifyApi;

    // Request code that will be used to verify if the result comes from correct activity
    // Can be any integer
    private static final int REQUEST_CODE = 1337;

    private static final String LOG_TAG = "[Using ESA]";

    public static final String ESA_BROADCAST_SAVED_PRED_FILE = "edu.ucsd.calab.extrasensory.broadcast.saved_prediction_file";
    public static final String ESA_BROADCAST_EXTRA_KEY_TIMESTAMP = "timestamp";

    private String _uuidPrefix = null;
    private String _timestamp = null;
    private static final String NO_USER = "no user";
    private static final String NO_TIMESTAMP = "no timestamp";
    //Shaida
    private String _mostProbLabel_act = null;
    private String _mostProbLabel_prev_act = null;

    private String _mostProbLabel_loc = null;
    private String _mostProbLabel_prev_loc = null;

    private double maxP = 0;
    private String maxLabel = "";

    private double maxP_prev = 0;
    private String maxLabel_prev = "";

    private Queue<Pair<String,Double>> mostProbableHist = new LinkedList<Pair<String,Double>>();
    private int histSize = 2;

    private static String [] activities = {
            "Lying down", "Sitting","Walking","Running","Bicycling","Sleeping","Lab work"
            ,"Drive - I'm the driver","Drive - I'm a passenger","Exercise","Cooking","Shopping",
            "Strolling","Drinking (alcohol)","Bathing - shower","Cleaning","Doing laundry",
            "Washing dishes","Watching TV","Surfing the internet","Singing","Talking",
            "Computer work","Eating","Grooming","Dressing","Stairs - going up","Stairs - going down",
            "Elevator","Standing",
    };


    private String [] locs = {"In class","In a meeting","At work","Indoors","Outside","In a car",
            "On a bus","At home","At a restaurant","At a party","At a bar",
            "At the beach","Toilet", "At the gym", "At school",
            "With co-workers","With friends" };

    HashSet<String> activity_set = new HashSet<String >(Arrays.asList(activities));
    HashSet<String> loc_set = new HashSet<String >(Arrays.asList(locs));

    private BroadcastReceiver _broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ESA_BROADCAST_SAVED_PRED_FILE.equals(intent.getAction())) {
                String newTimestamp = intent.getStringExtra(ESA_BROADCAST_EXTRA_KEY_TIMESTAMP);
                Log.d(LOG_TAG,"Caught broadcast for new timestamp: " + newTimestamp);
                _timestamp = newTimestamp;
                chooseMusic();

            }
        }
    };

    // Shaida: comparator for pq of <label,prob>
    Comparator<Pair<String, Double>> labelComp = new Comparator<Pair<String, Double>>(){
        public int compare(Pair<String, Double> p1, Pair<String, Double> p2){
            int res =  p1.second <= p2.second ? 1:-1;
            return res;
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                AuthenticationResponse.Type.TOKEN,
                REDIRECT_URI);
        builder.setScopes(new String[]{"user-read-private", "streaming"});
        AuthenticationRequest request = builder.build();

        AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);
        //chooseMusic();
        //getContextChange();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(LOG_TAG,"registring for broadcast: " + ESA_BROADCAST_SAVED_PRED_FILE);
        this.registerReceiver(_broadcastReceiver,new IntentFilter(ESA_BROADCAST_SAVED_PRED_FILE));
    }

    @Override
    public void onPause() {
        //this.unregisterReceiver(_broadcastReceiver);
        //Log.d(LOG_TAG,"Unregistered broadcast: " + ESA_BROADCAST_SAVED_PRED_FILE);
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        // Check if result comes from the correct activity
        if (requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
            if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                spotifyApi = new SpotifyApi();
                spotifyApi.setAccessToken(response.getAccessToken());
                chooseMusic();
                getContextChange();
                Config playerConfig = new Config(this, response.getAccessToken(), CLIENT_ID);
                Spotify.getPlayer(playerConfig, this, new SpotifyPlayer.InitializationObserver() {
                    @Override
                    public void onInitialized(SpotifyPlayer spotifyPlayer) {
                        mPlayer = spotifyPlayer;
                        mPlayer.addConnectionStateCallback(MainActivity.this);
                        mPlayer.addNotificationCallback(MainActivity.this);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        Log.e("MainActivity", "Could not initialize player: " + throwable.getMessage());
                    }
                });
            }
        }
    }

    @Override
    protected void onDestroy() {
        Spotify.destroyPlayer(this);
        super.onDestroy();
    }

    @Override
    public void onPlaybackEvent(PlayerEvent playerEvent) {
        Log.d("MainActivity", "Playback event received: " + playerEvent.name());
        switch (playerEvent) {
            // Handle event type as necessary
            default:
                break;
        }
    }

    @Override
    public void onPlaybackError(Error error) {
        Log.d("MainActivity", "Playback error received: " + error.name());
        switch (error) {
            // Handle error type as necessary
            default:
                break;
        }
    }

    @Override
    public void onLoggedIn() {
        Log.d("MainActivity", "User logged in");
        //chooseMusic();
    }

    @Override
    public void onLoggedOut() {
        Log.d("MainActivity", "User logged out");
    }

    @Override
    public void onLoginFailed(Error error) {
        Log.d("MainActivity", "Login failed");
    }

    @Override
    public void onTemporaryError() {
        Log.d("MainActivity", "Temporary error occurred");
    }

    @Override
    public void onConnectionMessage(String message) {
        Log.d("MainActivity", "Received connection message: " + message);
    }

    private void chooseMusic() {
        boolean haveUsers = true;
        List<String> uuidPrefixes = getUsers();
        if ((uuidPrefixes == null) || (uuidPrefixes.isEmpty())) {
            uuidPrefixes = new ArrayList<>();
            uuidPrefixes.add(NO_USER);
            haveUsers = false;
        }

         _uuidPrefix = uuidPrefixes.get(0);
        boolean haveTimestamps = true;
        List<String> timestamps = getTimestampsForUser(_uuidPrefix);
         //_timestamp = timestamps.get(0);

        // check if the user has any timestamps at all:
        if ((timestamps == null) || (timestamps.isEmpty())) {
            timestamps = new ArrayList<>();
            timestamps.add(NO_TIMESTAMP);
            haveTimestamps = false;
        }


        String textToPresent;

        if (_uuidPrefix == null || _uuidPrefix == NO_USER) {
            textToPresent = "There is no ExtraSensory user on this phone.";


            Toast.makeText(getApplicationContext()," 1st if cases " + _uuidPrefix ,
                    Toast.LENGTH_SHORT).show();
        }
        else if (_timestamp == null || _timestamp == NO_TIMESTAMP) {
            textToPresent = "User with UUID prefix " + _uuidPrefix + " has no saved recognition files.";

            Toast.makeText(getApplicationContext()," else if cases" ,
                    Toast.LENGTH_SHORT).show();
        }
        else {

            Toast.makeText(getApplicationContext(),"inside else stmt" ,
                    Toast.LENGTH_SHORT).show();

            String fileContent = readESALabelsFileForMinute(_uuidPrefix, _timestamp, true);
            PriorityQueue<Pair<String, Double>> labelsAndProbs = parseServerPredictionLabelProbabilities(fileContent);

           // _mostProbLabel_prev_act = _mostProbLabel_act;
           // _mostProbLabel_prev_loc = _mostProbLabel_loc;

            boolean found_act = false;
            boolean found_loc = false;
            double p_loc = 0;
            double p_act = 0;
            while(!found_act || !found_loc){
                Toast.makeText(getApplicationContext(),"inside while loop for finding loc and act" ,
                        Toast.LENGTH_SHORT).show();

                String top= labelsAndProbs.poll().first;
                double p =labelsAndProbs.poll().second;

                if(activity_set.contains(top) && !found_act){
                    _mostProbLabel_act = top;
                    p_loc = p;
                    found_act = true;
                }
                else if(loc_set.contains(top) && !found_loc){
                    _mostProbLabel_loc = top;
                    p_act = p;
                    found_loc = true;
                }

            }
            // populating most probale history queue
            Pair<String, Double> pair = new Pair<String, Double>(_mostProbLabel_act+ "/"+_mostProbLabel_loc, p_loc+ p_act );
            if(mostProbableHist.size() <histSize){
                mostProbableHist.add(pair);
            }
            else{
                mostProbableHist.poll();
                mostProbableHist.add(pair);
            }

            //System.out.println(_mostProbLabel_act);
            //_mostProbLabel= labelsAndProbs.peek().first;
            Toast.makeText(getApplicationContext(),"You are doing "+_mostProbLabel_act + " while you are " + _mostProbLabel_loc,
                    Toast.LENGTH_LONG).show();



        }



        //TextView scrolledText = (TextView)findViewById(R.id.the_scrolled_text);
        //scrolledText.setText(textToPresent);

    }

    Handler mHandler;

    protected void getContextChange() {

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                //Location l = (Location) msg.obj;
                Toast.makeText(getApplicationContext(), "made change: " + maxLabel,
                        Toast.LENGTH_SHORT).show();
                if (maxLabel != null) {
                    String[] labels = maxLabel.split("/");
                    playMusic1(labels[0], labels[1]);
                }
            }
        };

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(5000);
                        mHandler.post(new Runnable() {

                            @Override
                            public void run() {
                                //Location l = BasicReceiver.fieldLocation;
                                //_mostProbLabel_prev = "ddd";
                                //_mostProbLabel = "abs"+ Math.random();
                                //maxP_prev = maxP;
                                maxLabel_prev = maxLabel;

                                if(mostProbableHist.size() == histSize){
                                    maxP = 0;
                                    maxLabel = null;
                                    for(Pair<String, Double> item : mostProbableHist){
                                        if(item.second > maxP) {
                                            maxP = item.second;
                                            maxLabel = item.first;
                                        }
                                    }

                                    if( !maxLabel_prev.equals(maxLabel) ){
                                        Message msg = new Message();
                                        //msg.obj = l;
                                        mHandler.sendMessage(msg);
                                    }

                                }


                                /*if( maxLabel_prev != maxLabel){
                                    Message msg = new Message();
                                    //msg.obj = l;
                                    mHandler.sendMessage(msg);
                                }*/

                            }
                        });
                    } catch (Exception e) {
                    }
                }
            }
        }).start();
    }

    private void playMusic1(String activity, String location) {
        final String act = activity;
        SpotifyService spotifyService = spotifyApi.getService();
        Log.d("MainActivity", "PlayMusic1: Activity Location: " + activity + " " + location);
        spotifyService.searchPlaylists(activity + " " + location, new retrofit.Callback<PlaylistsPager>() {
            @Override
            public void success(PlaylistsPager playlistsPager, Response response) {
                //mPlayer.playUri(null, playlistsPager.playlists.items.get(1).uri, 0, 0);
                if (playlistsPager.playlists.items.size() > 0) {
                    Intent intent = new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH);
                    intent.setData(Uri.parse(playlistsPager.playlists.items.get(1).uri));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    System.out.println("output:" + playlistsPager.playlists.items.get(1).uri);
                } else {
                    playMusic2(act);
                }

            }

            @Override
            public void failure(RetrofitError error) {
                Log.d("failed to get query", error.toString());
            }
        });

    }

    private void playMusic2(String activity) {
        SpotifyService spotifyService = spotifyApi.getService();
        Log.d("MainActivity", "PlayMusic2: Activity: " + activity);
        spotifyService.searchPlaylists(activity, new retrofit.Callback<PlaylistsPager>() {
            @Override
            public void success(PlaylistsPager playlistsPager, Response response) {
                //mPlayer.playUri(null, playlistsPager.playlists.items.get(1).uri, 0, 0);
                if (playlistsPager.playlists.items.size() > 0) {
                    Intent intent = new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH);
                    intent.setData(Uri.parse(playlistsPager.playlists.items.get(1).uri));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    System.out.println("output:" + playlistsPager.playlists.items.get(1).uri);
                } else {
                    playMusic3();
                }

            }

            @Override
            public void failure(RetrofitError error) {
                Log.d("failed to get query", error.toString());
            }
        });

    }

    private void playMusic3() {
        SpotifyService spotifyService = spotifyApi.getService();
        Log.d("MainActivity", "PlayMusic3: default is being played");
        spotifyService.searchPlaylists("hype", new retrofit.Callback<PlaylistsPager>() {
            @Override
            public void success(PlaylistsPager playlistsPager, Response response) {
                //mPlayer.playUri(null, playlistsPager.playlists.items.get(1).uri, 0, 0);
                Intent intent = new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH);
                intent.setData(Uri.parse(playlistsPager.playlists.items.get(1).uri));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                System.out.println("output:" + playlistsPager.playlists.items.get(1).uri);
            }

            @Override
            public void failure(RetrofitError error) {
                Log.d("failed to get query", error.toString());
            }
        });

    }

    private static final String SERVER_PREDICTIONS_FILE_SUFFIX = ".server_predictions.json";
    private static final String USER_REPORTED_LABELS_FILE_SUFFIX = ".user_reported_labels.json";
    private static final String UUID_DIR_PREFIX = "extrasensory.labels.";

    /**
     * Return the super-directory, where a users' ExtraSensory-App label files should be
     * @return The users' files' directory
     * @throws PackageManager.NameNotFoundException
     */
    private File getUsersFilesDirectory() throws PackageManager.NameNotFoundException {
        // Locate the ESA saved files directory, and the specific minute-example's file:
        Context extraSensoryAppContext = getApplicationContext().createPackageContext("edu.ucsd.calab.extrasensory",0);
        File esaFilesDir = extraSensoryAppContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (!esaFilesDir.exists()) {
            return null;
        }
        return esaFilesDir;
    }

    /**
     * Return the directory, where a user's ExtraSensory-App label files should be
     * @param uuidPrefix The prefix (8 characters) of the user's UUID
     * @return The user's files' directory
     * @throws PackageManager.NameNotFoundException
     */
    private File getUserFilesDirectory(String uuidPrefix) throws PackageManager.NameNotFoundException {
        // Locate the ESA saved files directory, and the specific minute-example's file:
        Context extraSensoryAppContext = getApplicationContext().createPackageContext("edu.ucsd.calab.extrasensory",0);
        File esaFilesDir = new File(extraSensoryAppContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),UUID_DIR_PREFIX + uuidPrefix);
        if (!esaFilesDir.exists()) {
            return null;
        }
        return esaFilesDir;
    }

    /**
     * Get the list of users (UUID prefixes).
     * @return List of UUID prefixes (strings).
     * In case the user's directory was not found, null will be returned.
     */
    private List<String> getUsers() {
        try {
            File esaFilesDir = getUsersFilesDirectory();
            if (esaFilesDir == null) {
                return null;
            }
            String[] filenames = esaFilesDir.list(new FilenameFilter() {
                @Override
                public boolean accept(File file, String s) {
                    return s.startsWith(UUID_DIR_PREFIX);
                }
            });

            SortedSet<String> usersSet = new TreeSet<>();
            for (String filename : filenames) {
                String uuidPrefix = filename.replace(UUID_DIR_PREFIX,"");
                usersSet.add(uuidPrefix);
            }

            List<String> uuidPrefixes = new ArrayList<>(usersSet);
            return uuidPrefixes;
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Get the list of timestamps, for which this user has saved files from ExtraSensory App.
     * @param uuidPrefix The prefix (8 characters) of the user's UUID
     * @return List of timestamps (strings), each representing a minute that has a file for this user.
     * The list will be sorted from earliest to latest.
     * In case the user's directory was not found, null will be returned.
     */
    private List<String> getTimestampsForUser(String uuidPrefix) {
        try {
            File esaFilesDir = getUserFilesDirectory(uuidPrefix);
            if (esaFilesDir == null) {
                return null;
            }
            String[] filenames = esaFilesDir.list(new FilenameFilter() {
                @Override
                public boolean accept(File file, String s) {
//                    return s.endsWith(SERVER_PREDICTIONS_FILE_SUFFIX) || s.endsWith(USER_REPORTED_LABELS_FILE_SUFFIX);
                    return s.endsWith(SERVER_PREDICTIONS_FILE_SUFFIX);
                }
            });

            SortedSet<String> userTimestampsSet = new TreeSet<>();
            for (String filename : filenames) {
                String timestamp = filename.substring(0,10); // The timestamps always occupy 10 characters
                userTimestampsSet.add(timestamp);
            }

            List<String> userTimestamps = new ArrayList<>(userTimestampsSet);
            return userTimestamps;
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Read text from the label file saved by ExtraSensory App, for a particualr minute-example.
     * @param uuidPrefix The prefix (8 characters) of the user's UUID
     * @param timestamp The timestamp of the desired minute example
     * @param serverOrUser Read the server-predictions if true, and the user-reported labels if false
     * @return The text inside the file, or null if had trouble finding or reading the file
     */
    private String readESALabelsFileForMinute(String uuidPrefix,String timestamp,boolean serverOrUser) {
        try {
            File esaFilesDir = getUserFilesDirectory(uuidPrefix);
            if (esaFilesDir == null) {
                // Cannot find the directory where the label files should be
                return null;
            }
            String fileSuffix = serverOrUser ? SERVER_PREDICTIONS_FILE_SUFFIX : USER_REPORTED_LABELS_FILE_SUFFIX;
            File minuteLabelsFile = new File(esaFilesDir,timestamp + fileSuffix);

            // Check if file exists:
            if (!minuteLabelsFile.exists()) {
                return null;
            }

            // Read the file:
            StringBuilder text = new StringBuilder();
            BufferedReader bufferedReader = new BufferedReader(new FileReader(minuteLabelsFile));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
            bufferedReader.close() ;
            return text.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static final String JSON_FIELD_LABEL_NAMES = "label_names";
    private static final String JSON_FIELD_LABEL_PROBABILITIES = "label_probs";

    /**
     * Prse the content of a minute's server-prediction file to extract the labels and probabilities assigned to the labels.
     * @param predictionFileContent The content of a specific minute server-prediction file
     * @return List of label name and probability pairs, or null if had trouble.
     */
    // changed list to pq
    private PriorityQueue<Pair<String,Double>> parseServerPredictionLabelProbabilities(String predictionFileContent) {
        if (predictionFileContent == null) {
            return null;
        }
        try {
            JSONObject jsonObject = new JSONObject(predictionFileContent);
            JSONArray labelArray = jsonObject.getJSONArray(JSON_FIELD_LABEL_NAMES);
            Log.d("labless",labelArray.toString());
            JSONArray probArray = jsonObject.getJSONArray(JSON_FIELD_LABEL_PROBABILITIES);
            // Make sure both arrays have the same size:
            if (labelArray == null || probArray == null || labelArray.length() != probArray.length()) {
                return null;
            }
            PriorityQueue<Pair<String,Double>> labelsAndProbabilities = new PriorityQueue<>(labelArray.length(), labelComp);
            for (int i = 0; i < labelArray.length(); i ++) {
                String label = labelArray.getString(i);
                Double prob = probArray.getDouble(i);
                labelsAndProbabilities.add(new Pair<String, Double>(label,prob));
            }
            return labelsAndProbabilities;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }
}
