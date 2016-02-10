/**
 * MainActivityFragment.java
 * Implements the main fragment of the main activity
 * This fragment is a list view of radio stations
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import org.y20k.transistor.core.Collection;
import org.y20k.transistor.helpers.CollectionAdapter;
import org.y20k.transistor.helpers.DialogAddStation;
import org.y20k.transistor.helpers.ImageHelper;
import org.y20k.transistor.helpers.Logger;
import org.y20k.transistor.helpers.StationDownloader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;


/**
 * MainActivityFragment class
 */
public final class MainActivityFragment extends Fragment {

    /* Define log tag */
    private static final String LOG_TAG = MainActivityFragment.class.getSimpleName();

    /* Keys */
    private static final String ACTION_COLLECTION_CHANGED = "org.y20k.transistor.action.COLLECTION_CHANGED";
    private static final String ACTION_PLAYBACK_STARTED = "org.y20k.transistor.action.PLAYBACK_STARTED";
    private static final String ACTION_PLAYBACK_PAUSED = "org.y20k.transistor.action.PLAYBACK_PAUSED";
    private static final String ACTION_PLAYBACK_STOPPED = "org.y20k.transistor.action.PLAYBACK_STOPPED";
    private static final String ACTION_IMAGE_CHANGE_REQUESTED = "org.y20k.transistor.action.IMAGE_CHANGE_REQUESTED";
    private static final String LIST_STATE = "ListState";
    private static final String STREAM_URI = "streamUri";
    private static final String STATION_NAME = "stationName";
    private static final String STATION_ID = "stationID";
    private static final String STATION_ID_CURRENT = "stationIDCurrent";
    private static final String STATION_ID_LAST = "stationIDLast";
    private static final String PLAYBACK = "playback";
    private static final String TITLE = "title";
    private static final String CONTENT = "content";
    private static final int REQUEST_LOAD_IMAGE = 1;
    private static final int PERMISSION_REQUEST_READ_EXTERNAL_STORAGE = 1;

    /* Main class variables */
    private Application mApplication;
    private Activity mActivity;
    private Collection mCollection;
    private CollectionAdapter mCollectionAdapter = null;
    private File mFolder;
    private LinkedList<String> mStationNames;
    private LinkedList<Bitmap> mStationImages;
    private View mRootView;
    private ListView mListView;
    private Parcelable mListState;
    private int mTempStationImageID;
    private PlayerService mPlayerService;

    /* Logger */
    private Logger log;


    /* Constructor (default) */
    public MainActivityFragment() {}


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // get activity and application contexts
        mActivity = getActivity();
        mApplication = mActivity.getApplication();

        // initiate playback service
        mPlayerService = new PlayerService();

        // set list state null
        mListState = null;

        // initialize temporary station image id
        mTempStationImageID = -1;

        // init Logger
        AppApplication aa = new AppApplication();
        log = new Logger(mActivity, aa.isDebugEnabled(), LOG_TAG);

        // get Station file
        File file = mActivity.getExternalFilesDir("Collection");
        if (file != null) {
            mFolder = file;
        } else {
            // notify user and log exception
            log.error(mActivity.getString(R.string.toastalert_no_external_storage), true);
            // finish activity
            mActivity.finish();
        }

        // fragment has options menu
        setHasOptionsMenu(true);

        // create adapter for collection
        mStationNames = new LinkedList<>();
        mStationImages = new LinkedList<>();
        mCollectionAdapter = new CollectionAdapter(mActivity, mStationNames, mStationImages);

        // listen for data change in mCollection adapter
        mCollectionAdapter.setCollectionChangedListener(new CollectionAdapter.CollectionChangedListener() {
            @Override
            public void collectionChanged() {
                refreshStationList();
            }
        });

        // initialize broadcast receivers
        initializeBroadcastReceivers();
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // get list state from saved instance
        if (savedInstanceState != null) {
            mListState = savedInstanceState.getParcelable(MainActivityFragment.LIST_STATE);
        }

        // inflate rootview from xml
        mRootView = inflater.inflate(R.layout.fragment_main, container, false);

        // get reference to list view from inflated root view
        mListView = (ListView) mRootView.findViewById(R.id.main_listview_collection);

        // attach adapter to list view
        mListView.setAdapter(mCollectionAdapter);

        // attach OnItemClickListener to mListView  (single tap)
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            // inner method override for OnItemClickListener
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (mCollection != null) {
                    // get station name and URL from position
                    String stationName = mCollection.getStations().get((Integer) mCollectionAdapter.getItem(position)).getStationName();
                    String streamUri = mCollection.getStations().get((Integer) mCollectionAdapter.getItem(position)).getStreamUri().toString();

                    // add name, url and id of station to intent
                    Intent intent = new Intent(mActivity, PlayerActivity.class);
                    intent.putExtra(STATION_NAME, stationName);
                    intent.putExtra(STREAM_URI, streamUri);
                    intent.putExtra(STATION_ID, position);

                    // start activity with intent
                    startActivity(intent);
                }
            }
        });

        // attach OnItemLongClickListener to mListView (tap and hold)
        mListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                handleLongClick(position);
                return true;
            }
        });

        // return list view
        return mRootView;
    }


    @Override
    public void onResume() {
        super.onResume();
        // handle incoming intent
        handleNewStationIntent();
    }


    @Override
    public void onStart() {
        super.onStart();
        // fill collection adapter with stations
        refreshStationList();
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        String title;
        String content;
        Intent intent;

        switch (item.getItemId()) {
            case R.id.menu_add:
                DialogAddStation dialog = new DialogAddStation(mActivity);
                dialog.show();
                return true;

            case R.id.menu_about:
                // get title and content
                title = mActivity.getString(R.string.header_about);
                content = mActivity.getString(R.string.html_about);
                // create intent
                intent = new Intent(mActivity, InfosheetActivity.class);
                // put title and content to intent
                intent.putExtra(TITLE, title);
                intent.putExtra(CONTENT, content);
                // start activity
                startActivity(intent);
                return true;

            case R.id.menu_howto:
                // get title and content
                title = mActivity.getString(R.string.header_howto);
                content = mActivity.getString(R.string.html_howto);
                // create intent
                intent = new Intent(mActivity, InfosheetActivity.class);
                // put title and content to intent
                intent.putExtra(TITLE, title);
                intent.putExtra(CONTENT, content);
                // start activity
                startActivity(intent);
                return true;

            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // save list view position
        mListState = mListView.onSaveInstanceState();
        outState.putParcelable(LIST_STATE, mListState);
    }


    /* Fills collection adapter */
    private void fillCollectionAdapter() {
        Bitmap stationImage;
        Bitmap stationImageSmall;
        String stationName;
        ImageHelper imageHelper;

        // create collection
        log.verbose("Create collection of stations (folder:" + mFolder.toString() + ").", false);
        mCollection = new Collection(mFolder);

        // put stations into collection adapter
        for (int i = 0; i < mCollection.getStations().size(); i++) {
            // set name of station
            stationName = mCollection.getStations().get(i).getStationName();
            // add name to linked list of names
            mStationNames.add(stationName);

            // get image for station
            if (mCollection.getStations().get(i).getStationImageFile().exists()) {
                // station image
                stationImageSmall = BitmapFactory.decodeFile(mCollection.getStations().get(i).getStationImageFile().toString());
            } else {
                // default image
                stationImageSmall = BitmapFactory.decodeResource(mActivity.getResources(), R.drawable.ic_notesymbol);
            }

            // set image for station
            imageHelper = new ImageHelper(stationImageSmall, mActivity);
            imageHelper.setBackgroundColor(R.color.transistor_grey_lighter);
            stationImage = imageHelper.createCircularFramedImage(192);

            // add image to linked list of images
            mStationImages.add(stationImage);
        }

        mCollectionAdapter.setCollection(mCollection);
        mCollectionAdapter.notifyDataSetChanged();
    }


    /* (Re-)fills collection adapter with stations */
    private void refreshStationList() {
        // clear and refill mCollection adapter
        if (!mStationNames.isEmpty() && !mStationImages.isEmpty()) {
            mStationNames.clear();
            mStationImages.clear();
        }

        fillCollectionAdapter();

        // show call to action, if necessary
        View actioncall = mRootView.findViewById(R.id.main_actioncall_layout);
        if (mCollectionAdapter.isEmpty()) {
            actioncall.setVisibility(View.VISIBLE);
        } else {
            actioncall.setVisibility(View.GONE);
        }

        log.verbose("Refreshing list of stations", false);
    }


    /* handles external taps on streaming links */
    private void handleNewStationIntent() {
        // get intent
        Intent intent = mActivity.getIntent();

        // check for intent of tyoe VIEW
        if (intent.getAction().equals(Intent.ACTION_VIEW)) {

            // set new station URL
            String newStationURL;
            // mime type check
            if (intent.getType() != null && intent.getType().startsWith("audio/")) {
                newStationURL = intent.getDataString();
            } else {
                // no mime type
                newStationURL = intent.getDataString();
            }

            // clear the intent
            intent.setAction("");

            // check for null
            if (newStationURL != null) {
                // download and add new station
                StationDownloader stationDownloader = new StationDownloader(newStationURL, mActivity);
                stationDownloader.execute();

                // send local broadcast
                Intent i = new Intent();
                i.setAction(ACTION_COLLECTION_CHANGED);
                LocalBroadcastManager.getInstance(mActivity).sendBroadcast(i);

            } else {
                // unsuccessful - log failure
                log.verbose("Received an empty intent", false);
            }
        }
    }


    /* Initializes broadcast receivers fot onCreate */
    private void initializeBroadcastReceivers() {
        // broadcast receiver: player service stopped playback
        BroadcastReceiver playbackStoppedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshStationList();
            }
        };
        IntentFilter playbackStoppedIntentFilter = new IntentFilter(ACTION_PLAYBACK_STOPPED);
        LocalBroadcastManager.getInstance(mApplication).registerReceiver(playbackStoppedReceiver, playbackStoppedIntentFilter);

        // broadcast receiver: player service paused playback
        BroadcastReceiver playbackPausedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshStationList();
            }
        };
        IntentFilter playbackPausedIntentFilter = new IntentFilter(ACTION_PLAYBACK_PAUSED);
        LocalBroadcastManager.getInstance(mApplication).registerReceiver(playbackPausedReceiver, playbackPausedIntentFilter);

        // broadcast receiver: player service started playback
        BroadcastReceiver playbackStartedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshStationList();
            }
        };
        IntentFilter playbackStartedIntentFilter = new IntentFilter(ACTION_PLAYBACK_STARTED);
        LocalBroadcastManager.getInstance(mApplication).registerReceiver(playbackStartedReceiver, playbackStartedIntentFilter);

        // broadcast receiver: station added, deleted, or changed
        BroadcastReceiver collectionChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshStationList();
            }
        };
        IntentFilter collectionChangedIntentFilter = new IntentFilter(ACTION_COLLECTION_CHANGED);
        LocalBroadcastManager.getInstance(mApplication).registerReceiver(collectionChangedReceiver, collectionChangedIntentFilter);

        // broadcast receiver: listen for request to change station image
        BroadcastReceiver imageChangeRequestReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // get station id and save it
                mTempStationImageID = intent.getIntExtra(STATION_ID, -1);
                // start image picker
                selectFromImagePicker();
            }
        };
        IntentFilter imageChangeRequestIntentFilter = new IntentFilter(ACTION_IMAGE_CHANGE_REQUESTED);
        LocalBroadcastManager.getInstance(mApplication).registerReceiver(imageChangeRequestReceiver, imageChangeRequestIntentFilter);

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case PERMISSION_REQUEST_READ_EXTERNAL_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission granted - get system picker for images
                    Intent pickImageIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    startActivityForResult(pickImageIntent, REQUEST_LOAD_IMAGE);
                } else {
                    // permission denied
                    log.error(mActivity.getString(R.string.toastalert_permission_denied) + " READ_EXTERNAL_STORAGE", true);
                }
            }
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_LOAD_IMAGE && resultCode == Activity.RESULT_OK && null != data) {
            // retrieve selected image from image picker
            processNewImage(data.getData());
        }
    }


    /* Processes new image and saves it to storage */
    private void processNewImage(Uri newImageUri) {
        ImageHelper imageHelper = new ImageHelper(newImageUri, mActivity);
        Bitmap newImage = imageHelper.getInputImage();

        if (newImage != null) {
            // write image to storage
            File stationImageFile = mCollection.getStations().get(mTempStationImageID).getStationImageFile();
            try (FileOutputStream out = new FileOutputStream(stationImageFile)) {
                newImage.compress(Bitmap.CompressFormat.PNG, 100, out);
            } catch (IOException e) {
                log.error("Unable to save: " + newImage.toString(), true);
            }
        } else {
            log.error("Unable to get image from media picker: " + newImageUri.toString(), true);
        }
    }


    /* Check permissions and start image picker */
    private void selectFromImagePicker() {
        // permission to read external storage granted
        if (ActivityCompat.checkSelfPermission(mActivity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {

            // get system picker for images
            Intent pickImageIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            mActivity.startActivityForResult(pickImageIntent, REQUEST_LOAD_IMAGE);
        } else {
            // permission to read external storage not granted
            if (ActivityCompat.shouldShowRequestPermissionRationale(mActivity, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                // ask for permission and explain why
                Snackbar snackbar = Snackbar.make(mRootView, R.string.snackbar_request_storage_access, Snackbar.LENGTH_INDEFINITE);
                snackbar.setAction(R.string.dialog_generic_button_okay, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        ActivityCompat.requestPermissions(mActivity,
                                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                                PERMISSION_REQUEST_READ_EXTERNAL_STORAGE);
                    }
                });
                snackbar.show();

            } else {
                // ask for permission without explanation
                ActivityCompat.requestPermissions(mActivity,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_READ_EXTERNAL_STORAGE);
            }
        }
    }


    /* Handles long click on list item */
    private void handleLongClick(int position) {
        int stationIDCurrent;
        int stationIDLast;

        // get current playback state
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mActivity);
        stationIDCurrent = settings.getInt(STATION_ID_CURRENT, -1);
        boolean playback = settings.getBoolean(PLAYBACK, false);

        if (playback && position == stationIDCurrent ) {
            // stop playback service
            mPlayerService.startActionStop(mActivity);

            // set playback state
            stationIDLast = stationIDCurrent;
            playback = false;

            // inform user
            log.debug(mActivity.getString(R.string.toastmessage_long_press_playback_stopped), true);
        } else {
            // start playback service
            String stationName = mCollection.getStations().get((Integer) mCollectionAdapter.getItem(position)).getStationName();
            String streamUri = mCollection.getStations().get((Integer) mCollectionAdapter.getItem(position)).getStreamUri().toString();
            mPlayerService.startActionPlay(mActivity, streamUri, stationName);

            // set playback state
            stationIDLast = stationIDCurrent;
            stationIDCurrent = position;
            playback = true;

            // inform user
            log.debug(mActivity.getString(R.string.toastmessage_long_press_playback_started), true);
        }

        // vibrate 50 milliseconds
        Vibrator v = (Vibrator) mActivity.getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(50);

        // Save station name and ID
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt(STATION_ID_CURRENT, stationIDCurrent);
        editor.putInt(STATION_ID_LAST, stationIDLast);
        editor.putBoolean(PLAYBACK, playback);
        editor.apply();

        // refresh view
        refreshStationList();
    }
}
