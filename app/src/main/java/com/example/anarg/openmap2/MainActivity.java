package com.example.anarg.openmap2;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    private static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;
    private MapView map = null;
    private IMapController mapController;
    private MyLocationNewOverlay myLocationoverlay;
    private GpsMyLocationProvider gp;
    private BackEnd backend;
    private HashMap<String, GeoPoint> geoPointHashMap;
    private ArrayList<Marker> allMarkers;
    private ArrayList<Marker> currentMarkers;
    private ArrayList<Signal> signals;
    private ArrayList<String> req;
    private ThreadControl threadControl;
    private String param;
    private static final String reqURl = "http://anarghya321.pythonanywhere.com/api/railwaysignals.json";
    private static final String govURl = "http://tms.affineit.com:4445/SignalAhead/Json/SignalAhead";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //handle permissions first, before map is created. not depicted here
            // Write you code here if permission already given.
            //load/initialize the osmdroid configuration, this can be done
            allMarkers = new ArrayList<>();
            currentMarkers= new ArrayList<>();
            geoPointHashMap = new HashMap<>();
            signals=new ArrayList<>();
            req=new ArrayList<>();
            Context ctx = getApplicationContext();
            Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
            //setting this before the layout is inflated is a good idea
            //it 'should' ensure that the map has a writable location for the map cache, even without permissions
            //if no tiles are displayed, you can try overriding the cache path using Configuration.getInstance().setCachePath
            //see also StorageUtils
            //note, the load method also sets the HTTP User Agent to your application's package name, abusing osm's tile servers will get you banned based on this string
            //inflate and create the map
            setContentView(R.layout.activity_main);
            map = findViewById(R.id.map);
            map.setTileSource(TileSourceFactory.MAPNIK);
            map.setBuiltInZoomControls(true);
            map.setMultiTouchControls(true);
            backend = new BackEnd();
            threadControl=new ThreadControl();
            Intent i= getIntent();
            param= i.getStringExtra("Signal");
            new RequestTask(backend,this,threadControl,param).execute(reqURl,govURl);
            if (permissionsCheck()){
                gp = new GpsMyLocationProvider(getApplicationContext());
                myLocationoverlay = new MyLocationNewOverlay(gp, map);
                myLocationoverlay.enableMyLocation();
                map.getOverlays().add(myLocationoverlay);
            }else {
                exceptionRaised("Location Permission Not Granted!");
            }
    }

    public ArrayList<String> getReq(){
        return req;
    }

    public void setReq(ArrayList<String> r){
        this.req=r;
    }

        private Handler mHandler = new Handler();
        private Runnable timerTask = new Runnable() {
            @Override
            public void run() {
                new RequestTask(backend, MainActivity.this,threadControl,param).execute("", govURl);
                mHandler.postDelayed(timerTask, 1);
            }};

    public void setMapCenter(){
        mapController = map.getController();
        mapController.setZoom(15.6f);
        GeoPoint g=new GeoPoint(22.578802, 88.365743);
        mapController.setCenter(g);
    }

    public void onResume() {
        super.onResume();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        if (map!=null) {
            map.onResume(); //needed for compass, my location overlays, v6.0.0 and up
            threadControl.resume();
            mHandler.post(timerTask);
        }
    }

    public void onPause() {
        super.onPause();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().save(this, prefs);
        if (map!=null) {
            map.onPause();  //needed for compass, my location overlays, v6.0.0 and up
            threadControl.pause();
            mHandler.removeCallbacks(timerTask);
        }
    }

    private Marker configMarker(GeoPoint gp, String description, Signal s) {
            Marker marker = new Marker(map);
            marker.setPosition(gp);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setTitle(description);
            addColorSignal(s, marker);
            return marker;
//        map.getOverlays().clear();
//        map.invalidate();
    }

    private void addColorSignal(Signal so,Marker marker){

        if (so==null){
            marker.setIcon(getResources().getDrawable(R.drawable.empty));
            marker.setId("empty");
        } else if (so.getSignalAspect().equals("Red")){
            marker.setIcon(getResources().getDrawable(R.drawable.red));
            marker.setId("Red");
        }else if (so.getSignalAspect().equals("Green")) {
            marker.setIcon(getResources().getDrawable(R.drawable.green));
            marker.setId("Green");
        } else if (so.getSignalAspect().equals("Yellow")) {
            marker.setIcon(getResources().getDrawable(R.drawable.yellow));
            marker.setId("Yellow");
        } else if (so.getSignalAspect().equals("YellowYellow")) {
            marker.setIcon(getResources().getDrawable(R.drawable.yellowyellow));
            marker.setId("YellowYellow");
        }

    }

//    public  void updateMarker(ArrayList<Signal> s){
//        int c=0;
//        for (Signal so: s){
//            if(markerUpdateCheck(so)!=null){
//                c++;
//                addColorSignal(so,markerUpdateCheck(so));
//            }
//        }
//        if (c!=0) {
//            Toast.makeText(this, "Updated " + Integer.toString(c) + " markers!", Toast.LENGTH_SHORT).show();
//        }
//    }
//    //Helper Method for updateMarker
//    private Marker markerUpdateCheck(Signal s) {
//        Log.d("sig", s.toString());
//        for (Marker m: markerCounter){
//            if(m.getTitle().equals(s.getSignalID())){
//                return m;
//            }
//        }
//        return null;
//    }
//
//    public void addSignals(HashMap<String, GeoPoint> gp, ArrayList<Signal> sg) {
//        for(String s: gp.keySet()){
//            if(check(s,sg)!=null) {
//                addMarker(gp.get(s), s, check(s,sg));
//            }else{
//                addMarker(gp.get(s),s,check(s,sg));
//            }
//        }
//        if (markerCounter.size()==0){
//            Toast.makeText(this, "No signals were found for this Train!", Toast.LENGTH_SHORT).show();
//            threadControl.pause();
//            mHandler.removeCallbacks(timerTask);
//        }
//    }

    //Helper Method for addSignals
    private Signal check(String id, ArrayList<Signal> s){
        if(s!=null) {
            for (Signal so : s) {
                if (so.getSignalID().equals(id)) {
                    return so;
                }
            }
            return null;
        }
        else {
            return null;
        }
    }

        private boolean permissionsCheck() {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_LOCATION);
                }
                return false;
            } else {
//                gp = new GpsMyLocationProvider(getApplicationContext());
                return true;
            }
        }

    public void sync(View view) {
        Button b= findViewById(R.id.button);
        if(b.getText().equals("Pause Sync")){
            threadControl.pause();
            mHandler.removeCallbacks(timerTask);
            b.setText("Resume Sync");
            Toast.makeText(this,"Sync Paused!",Toast.LENGTH_SHORT).show();
        }
        else if (b.getText().equals("Resume Sync")){
            mHandler.post(timerTask);
            threadControl.resume();
            b.setText("Pause Sync");
            Toast.makeText(this,"Sync Resumed!",Toast.LENGTH_SHORT).show();
        }
    }

    public void populateMarkers(HashMap<String, GeoPoint> h) {
        for (String s: h.keySet()){
            allMarkers.add(configMarker(h.get(s),s,null));
        }
    }

    public void addMarker(Marker marker){ map.getOverlays().add(marker); }

    public void removeMarkers(ArrayList<Marker> marker){
        for (Marker m: marker) {
            map.getOverlays().remove(m);
        }
    }

    public void addInitialSignals(ArrayList<Signal> signals) {
        for (Signal s: signals){
            if (checkSignalWithMarker(s)!=null){
                addMarker(checkSignalWithMarker(s));
            }
        }
    }

    private Marker checkSignalWithMarker(Signal s) {
            for (Marker m : allMarkers) {
                if (m.getTitle().equals(s.getSignalID())) {
                    currentMarkers.add(m);
                    return m;
                }
            }
        return null;
    }


    public void updateSignals(ArrayList<Signal> signals) {
        removeMarkers(currentMarkers);
        currentMarkers.clear();
        addInitialSignals(signals);
        Toast.makeText(this,"Updated "+currentMarkers.size()+" Markers!",Toast.LENGTH_SHORT).show();
    }
    public void exceptionRaised(String s) {
        AlertDialog.Builder builder=new AlertDialog.Builder(this);
        builder.setMessage(s)
                .setTitle("Error");
        builder.setNegativeButton("Exit", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }
}