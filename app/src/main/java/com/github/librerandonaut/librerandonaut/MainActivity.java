package com.github.librerandonaut.librerandonaut;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

import com.github.librerandonaut.librerandonaut.randomness.LoadRandomProviderResult;
import com.github.librerandonaut.librerandonaut.randomness.RandomDotOrgEntropyManager;
import java.text.DecimalFormat;

import com.github.librerandonaut.librerandonaut.attractor.Attractor;
import com.github.librerandonaut.librerandonaut.attractor.AttractorGeneratorFactory;
import com.github.librerandonaut.librerandonaut.attractor.AttractorGeneratorType;
import com.github.librerandonaut.librerandonaut.attractor.Coordinates;
import com.github.librerandonaut.librerandonaut.attractor.IAttractorGenerator;
import com.github.librerandonaut.librerandonaut.attractor.RandomPointsProvider;
import com.github.librerandonaut.librerandonaut.randomness.IRandomProvider;
import com.github.librerandonaut.librerandonaut.randomness.AnuEntropyManager;
import com.github.librerandonaut.librerandonaut.randomness.RandomSource;
import com.github.librerandonaut.librerandonaut.rngdevice.IProgressHandler;

public class MainActivity extends AppCompatActivity implements LocationListener {
    static final String TAG = "MainActivity";
    private Button buttonGenerate;
    private Button buttonOpen;
    private TextView labelLocation;
    private TextView labelAttractor;
    private TextView labelAttractorData;
    private TextView labelRandomData;
    private RadioButton radioButtonAnu;
    private RadioButton radioButtonRandomDotOrg;
    private EditText textBoxRadius;
    private SharedPreferences sharedPref;
    private Coordinates coordinates;
    private Attractor attractor;

    private static int roundDecimals = 7;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initComponents();
        handlePermissions();
        requestLocation();
    }

    private void requestLocation() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            labelLocation.setText("GPS is not enabled.");
            new AlertDialog.Builder(MainActivity.this)
                    .setMessage("Enable GPS in Settings?")
                    .setPositiveButton("Yes", (paramDialogInterface, paramInt) -> startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), 1))
                    .setNegativeButton("Skip", null)
                    .show();
        } else {
            labelLocation.setText("Fetching coordinates...");
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if( resultCode != RESULT_OK)
            return;

        switch (requestCode)
        {
            case 1:
                requestLocation();
                break;

            case 2:
                // TODO: Allow filling in coordinates manually in case of denied GPS permission.
                break;
        }
    }


    private void initComponents() {
        labelRandomData = findViewById(R.id.labelRandomData);
        labelRandomData.setMovementMethod(new ScrollingMovementMethod());
        labelLocation = findViewById(R.id.labelLocation);
        labelAttractor = findViewById(R.id.labelAttractor);
        radioButtonAnu = findViewById(R.id.radioButtonAnu);
        radioButtonRandomDotOrg = findViewById(R.id.radioButtonRandomDotOrg);
        textBoxRadius = findViewById(R.id.textBoxRadius);
        labelAttractorData = findViewById(R.id.labelAttractorData);
        labelAttractorData.setMovementMethod(new ScrollingMovementMethod());

        buttonGenerate = findViewById(R.id.buttonGenerate);
        coordinates = new Coordinates(48, 8.5);

        buttonOpen = findViewById(R.id.buttonOpen);
        buttonOpen.setEnabled(false);

        labelLocation.setText("Loading ...");
        labelAttractor.setText("");

        sharedPref = getPreferences(Context.MODE_PRIVATE);
    }

    private void handlePermissions() {
        // GPS
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 666);
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 666);
        }
    }

    public void onLabelAttractorDataTouch(View view) throws Exception {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("AttractorData", labelAttractorData.getText());
        clipboard.setPrimaryClip(clip);
    }

    public void onButtonGenerateTouch(View view) throws Exception {

        int radius;
        try {
            radius = Integer.valueOf(textBoxRadius.getText().toString());
        } catch (Exception e) {
            radius = 1000;
        }
        textBoxRadius.setText(String.valueOf(radius));

        labelAttractor.setText("");
        labelAttractorData.setText("");
        labelRandomData.setText("");

        RandomSource randomSource = RandomSource.Anu;

        if (radioButtonRandomDotOrg.isChecked()) {
            randomSource = RandomSource.RandomDotOrg;
        }

        AttractorGeneratorType generatorType;

        // TODO: Disabled kde2 because its library (libs/kde/target/bits_kde.jar) does not build correctly with f-droid. Complete Removal awaits.
        generatorType = AttractorGeneratorType.Kde1;
        GenerateAsyncTask asyncTask = new GenerateAsyncTask();
        AttractorGenerationRequest request = new AttractorGenerationRequest();
        request.coordinates = coordinates;
        request.radius = radius;
        request.randomSource = randomSource;
        request.attractorGeneratorType = generatorType;
        asyncTask.execute(request);
    }

    private void showAttractorInformation(AttractorGenerationResult result) {
        if (result.attractor == null) {
            labelAttractorData.setText("");
            labelAttractor.setText("");
        } else {
            Coordinates location = result.attractor.getCoordinates();
            labelAttractor.setText(round(location.getLatitude()) + ", " + round(location.getlongitude()));
            String s = "approximateRadius: " + result.attractor.getAttractorTest().getApproximateRadius();
            s += "\n";
            s += "relativeDensity: " + result.attractor.getAttractorTest().getRelativeDensity();
            s += "\n";
            s += "nearestDistance: " + result.attractor.getAttractorTest().getNearestDistance();
            s += "\n";
            s += "usedBytes: " + (result.byteIndexAfter - result.byteIndexBefore) + ", bytesLeft: " + result.bytesLeft;
            if(result.randomDotOrgQuota > 0) {
                s += "\n";
                double quotaPercent = (double) result.randomDotOrgQuota / (double) RandomDotOrgEntropyManager.REQUEST_ENTROPY_MAX_SIZE * 100;
                s += "random.org quota: " + new DecimalFormat("#.#").format(quotaPercent) + "%";
            }
            labelAttractorData.setText(s);
        }

        if( result.entropyAsString != null)
            labelRandomData.setText(result.entropyAsString);
        else
            labelRandomData.setText("");
    }

    public void onButtonOpenTouch(View view) throws Exception {
        if (attractor == null)
            return;

        double lon = attractor.getCoordinates().getlongitude();
        double lat = attractor.getCoordinates().getLatitude();

        // TODO: Culture format
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + lat + "," + lon + ""));
        startActivity(intent);
    }

    private static double round(double value) {
        if (roundDecimals < 0) throw new IllegalArgumentException();

        long factor = (long) Math.pow(10, roundDecimals);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }


    @Override
    public void onLocationChanged(@NonNull Location location) {
        labelLocation.setText(round(location.getLatitude())  + ", " + round(location.getLongitude()));
        coordinates = new Coordinates(location.getLatitude(), location.getLongitude());
        buttonGenerate.setEnabled(true);
    }

    public class GenerateAsyncTask extends AsyncTask<AttractorGenerationRequest, Integer, AttractorGenerationResult>
            implements IProgressHandler {
        private final static String ENTROPY_BYTE_INDEX_PREFIX = "entropy_byte_index_";
        private final static String RANDOM_DOT_ORG_QUOTA = "random_dot_org_quota";
        private final static String RANDOM_DOT_ORG_QUOTA_TIMESTAMP = "random_dot_org_quota_timestamp";

        @Override
        protected AttractorGenerationResult doInBackground(AttractorGenerationRequest... requests) {

            AttractorGenerationResult result = new AttractorGenerationResult();
            int entropyUsage = RandomPointsProvider.getEntropyUsage(requests[0].radius);
            Log.v(TAG, "entropyUsage =" + entropyUsage);
            LoadRandomProviderResult loadRandomProviderResult = getLoadRandomProviderResult(result, entropyUsage, requests);

            if (loadRandomProviderResult != null && loadRandomProviderResult.getRandomProvider() != null) {
                Log.v(TAG, "getByteIndex =" + loadRandomProviderResult.getRandomProvider().getByteIndex());
                Log.v(TAG, "getEntropyPoolSize =" + loadRandomProviderResult.getRandomProvider().getEntropyPoolSize());
            }

            try {
                if(loadRandomProviderResult == null)
                {
                    result.status = "failed to initialize source";
                    return result;
                }
                if (!loadRandomProviderResult.getStatus())
                {
                    result.status = "loading random data failed: " + loadRandomProviderResult.getMessage();
                    return result;
                }
                IRandomProvider randomProvider = loadRandomProviderResult.getRandomProvider();
                if (randomProvider != null && randomProvider.hasEntropyLeft(entropyUsage)) {
                    Log.v(TAG, "generating attractor");

                    generateAttractor(result, entropyUsage, randomProvider, requests);

                } else if (randomProvider != null) {
                    result.status = "no entropy left";
                } else {
                    result.status = "failed to initialize source (2)";
                }
            } catch (Exception e) {
                Log.w(TAG, e);
            }
            buttonGenerate.setEnabled(true);
            return result;
        }

        private void generateAttractor(AttractorGenerationResult result, int entropyUsage, IRandomProvider randomProvider, AttractorGenerationRequest[] requests) {
            int byteIndexBefore = randomProvider.getByteIndex();
            IAttractorGenerator generator = AttractorGeneratorFactory.getAttractorGenerator(requests[0].attractorGeneratorType, randomProvider);

            try {
                result.attractor = generator.getAttractor(requests[0].coordinates, requests[0].radius);
                result.entropyAsString = randomProvider.getUsedEntropyAsString(byteIndexBefore, entropyUsage);
                Log.v(TAG, "attractor generated");
            } catch (Exception e) {
                Log.v(TAG, "attractor generation failed. " + e);
            }

            int byteIndexAfter = randomProvider.getByteIndex();
            Log.v(TAG, "byteIndexAfter =" + byteIndexAfter);

            result.byteIndexAfter = byteIndexAfter;
            result.byteIndexBefore = byteIndexBefore;
            result.bytesLeft = randomProvider.getEntropyPoolSize() - byteIndexAfter;
        }

        @Nullable
        private LoadRandomProviderResult getLoadRandomProviderResult(AttractorGenerationResult result, int entropyUsage, AttractorGenerationRequest[] requests) {
            LoadRandomProviderResult loadRandomProviderResult = null;
            try {
                switch (requests[0].randomSource) {
                    case Anu:
                        loadRandomProviderResult = new AnuEntropyManager(this).loadRandomProvider(entropyUsage);
                        break;
                    default:
                    case RandomDotOrg:
                        int randomDotOrgQuota = sharedPref.getInt(RANDOM_DOT_ORG_QUOTA, 0);
                        long randomDotOrgQuotaTimestamp = sharedPref.getLong(RANDOM_DOT_ORG_QUOTA_TIMESTAMP, 0);
                        long currentTimestamp = System.currentTimeMillis();

                        /* TODO: This is only a simple quota handling with a reset every 24h.
                            A better handling would be to check for errors from the http request and
                            then set a cool down timeout of 4-5 hours. See random.org FAQ.
                        */
                        if (currentTimestamp - randomDotOrgQuotaTimestamp > 24 * 60 * 60 * 1000) // 24 hours
                        {
                            randomDotOrgQuota = 0;
                        }

                        if (randomDotOrgQuota >= (RandomDotOrgEntropyManager.REQUEST_ENTROPY_MAX_SIZE))
                        {
                            // Entropy quota was used up
                            Log.w(TAG, "Entropy quota was used up. Quota = " + randomDotOrgQuota);
                            loadRandomProviderResult = null;
                            result.randomDotOrgQuota = randomDotOrgQuota;
                            result.randomDotOrgQuotaTimestamp = randomDotOrgQuotaTimestamp;
                        }
                        else
                        {
                            loadRandomProviderResult = new RandomDotOrgEntropyManager(this).loadRandomProvider(entropyUsage);
                            {
                                IRandomProvider randomProvider = loadRandomProviderResult.getRandomProvider();
                                if (randomProvider != null) {
                                    randomDotOrgQuota += randomProvider.getEntropyPoolSize();
                                    result.randomDotOrgQuota = randomDotOrgQuota;
                                    result.randomDotOrgQuotaTimestamp = currentTimestamp;
                                }
                            }
                        }
                        break;
                }
            } catch (Exception e) {
                Log.w(TAG, e);
            }
            return loadRandomProviderResult;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            MainActivity.this.buttonGenerate.setEnabled(false);
        }

        @Override
        protected void onPostExecute(AttractorGenerationResult result) {
            super.onPostExecute(result);
            MainActivity.this.buttonGenerate.setEnabled(true);
            MainActivity.this.attractor = result.attractor;

            buttonGenerate.setEnabled(true);
            buttonOpen.setEnabled(true);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putInt(ENTROPY_BYTE_INDEX_PREFIX + result.bytesHashCode, result.byteIndexAfter);
            editor.putInt(RANDOM_DOT_ORG_QUOTA, result.randomDotOrgQuota);
            editor.putLong(RANDOM_DOT_ORG_QUOTA_TIMESTAMP, result.randomDotOrgQuotaTimestamp);
            Log.w(TAG, "Saved byte index " + result.byteIndexAfter + " for hash " + result.bytesHashCode);
            Log.w(TAG, "Saved randomDotOrgQuota " + result.randomDotOrgQuota + " for timestamp " + result.randomDotOrgQuotaTimestamp);
            editor.commit();

            int usedBytes = result.byteIndexAfter - result.byteIndexBefore;
            int bytesLeft = result.bytesLeft;

            if (result.attractor != null) {
                showAttractorInformation(result);
            } else {
                String s = "attractor generation failed.";
                s += "\n";
                s += "usedBytes: " + usedBytes;
                s += "\n";
                s += "bytesLeft: " + bytesLeft;

                if (result.status != null) {
                    s += "\n";
                    s += "status: " + result.status;
                }

                labelAttractorData.setText(s);
            }
            buttonGenerate.setEnabled(true);
        }

        @Override
        public void updateProgress(int percent) {
            publishProgress(percent);
        }
    }
}
