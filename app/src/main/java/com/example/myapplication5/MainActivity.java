package com.example.myapplication5;

import static org.opencv.imgproc.Imgproc.THRESH_BINARY;
import static org.opencv.imgproc.Imgproc.threshold;
import com.googlecode.tesseract.android.TessBaseAPI;
import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import androidx.annotation.NonNull;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE = 1000;

    private MediaProjectionManager mProjectionManager;
    private static int DISPLAY_WIDTH = 1080; // ширина и высота экрана по умолчанию
    private static int DISPLAY_HEIGHT = 2265;
    private int DISPLAY_DPI = 480;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplayImg;
    private MediaProjectionCallback mMediaProjectionCallback;
    private ToggleButton mToggleButton;
    private MediaRecorder mMediaRecorder;
    private ImageReader imageReader;
    private static final int REQUEST_PERMISSIONS = 10;
    private Intent serviceIntent;

    //+++++++++++++ bluetooth   ++++++++++++++++++++++++++++++++++++++++++++++++++++
    private static final int REQUEST_ENABLE_BT = 1; 
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private OutputStream outStream = null;

    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // UUID для HC-05
    private static String address = "20:15:04:14:00:91";  // адрес HC-05

    // ++++++++++++++++++++++++++  HC-05 watchdog  ++++++++++++++++++++++++++++++
    private volatile boolean btConnected = false;      // RFCOMM link to HC-05 is up
    private volatile boolean sessionActive = false;    // capture consented and not stopped
    private volatile boolean btMonitorRunning = false; // watchdog thread should keep looping
    private volatile boolean btLostNotified = false;   // avoid duplicate "lost" handling
    private Thread btMonitorThread;
    private static final long BT_CHECK_INTERVAL_MS = 3000; // liveness/retry period
    // If a running session cannot find the HC-05 controller for this long, the
    // app shuts itself down completely (saves battery / stops screen capture).
    private static final long BT_ABSENT_TIMEOUT_MS = 30 * 60 * 1000L;
    private long btAbsentSince = 0;  // ms timestamp when HC-05 went missing (watchdog thread only)
    private final Object btLock = new Object();
    private TextView btStatusText;   // "HC-05: подключён / нет связи" indicator
    private View btStatusDot;
    private int frameCount = 0;      // DIAG: processed frames counter

    // ++++++++++++++++++++++++++  NFC  ++++++++++++++++++++++++++++++++++++++++
    // UID of the transport card kept in the car holder. Only this tag will
    // launch the app; any other MIFARE tag is ignored.
    static final String TARGET_NFC_UID = "041B889A201D90";
    // NDEF URI to write on a writable tag (NTAG215). Must match the intent
    // filter declared for NDEF_DISCOVERED in AndroidManifest.xml.
    static final String NFC_TRIGGER_URI = "kia-bridge://go";
    // Set by NfcLaunchActivity when the app is opened by the vehicle tag.
    static final String EXTRA_AUTO_START = "extra_auto_start";
    // Mirrors sessionStarting/sessionActive for NfcLaunchActivity (same process).
    private static volatile boolean sSessionBusy = false;
    private NfcAdapter nfcAdapter;
    private boolean launchedFromNfc = false;  // opened by tapping the car-holder tag
    private boolean autoStartPending = false; // NFC auto-start is waiting for onResume
    private boolean sessionStarting = false;  // a capture-consent request is in flight
    private boolean isForeground = false;     // activity is currently resumed
    // A car-holder tag stays in the field and can be re-detected several times;
    // repeats within this window are ignored.
    private static final long NFC_DEBOUNCE_MS = 10000;
    private static long lastNfcTriggerAt = 0;

    TessBaseAPI tessBaseApi;   // будем использовать для распознавания текста
    Mat temple_arrow_right;    // шаблоны, по которым будем искать объекты
    Mat temple_arrow_left;
    Mat temple_arrow_top_right;
    Mat temple_arrow_top_left;
    Mat temple_arrow_finish;
    Mat temple_arrow_u_turn;

    Mat temple_arrow_ring;
    Mat temple_speed_cam_day_yandex;
    Mat temple_speed_cam_night_yandex;

    Mat temple_speed_cam_mapcam;

    private CheckBox mcheckBox_speed;

    private CheckBox mcheckBox_turns;
    private CheckBox mcheckBox_speed_cams_yandex;
    private CheckBox mcheckBox_speed_cams_mapcam;

    private CheckBox mcheckBox_start_yandex_navi;

    private CheckBox mcheckBox_start_mapcam;
    private int Speed_is_checked;
    private int Turns_is_checked;
    private int Speed_cams_ya_is_checked;
    private int Speed_cams_mapcam_is_checked;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // NfcLaunchActivity validates the tag and asks for an auto-start.
        launchedFromNfc = getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_AUTO_START, false);
        if (launchedFromNfc) {
            lastNfcTriggerAt = System.currentTimeMillis();
        }

        //+++++++++++++++++ проверяем доступность BT адаптера +++++++++++++++++++++++++++++++++++++++++++++++++++++
        btAdapter = BluetoothAdapter.getDefaultAdapter();  
        checkBTState();
        //+++++++++++++++  запускаем фоновый сервис   ++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        setContentView(R.layout.activity_main);
        serviceIntent = new Intent(this, ForegroundService.class);
        serviceIntent.putExtra("inputExtra", "Foreground Service Example in Android");
        ContextCompat.startForegroundService(this, serviceIntent);

        // NFC reader for tag diagnostics / triggering
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            Log.w(TAG, "NFC: не поддерживается устройством");
        } else if (!nfcAdapter.isEnabled()) {
            Log.w(TAG, "NFC: выключен в настройках");
        }
        //++++++++++++++++++++++++++   ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        display.getMetrics(metrics);
        DISPLAY_WIDTH = metrics.widthPixels;
        DISPLAY_HEIGHT = metrics.heightPixels;
        DISPLAY_DPI = metrics.densityDpi;
        Log.e(TAG, "...width..." + DISPLAY_WIDTH);
        Log.e(TAG, "...height..." + DISPLAY_HEIGHT);
        Log.e(TAG, "...DISPLAY_DPI..." + DISPLAY_DPI);

       //+**********************************************************************************************************
        imageReader = ImageReader.newInstance(DISPLAY_WIDTH, DISPLAY_HEIGHT, PixelFormat.RGBA_8888, 2); // 0x1 - формат RGBA_8888I mageFormat.JPEG
        mProjectionManager = (MediaProjectionManager) getSystemService
                (Context.MEDIA_PROJECTION_SERVICE);
        mcheckBox_speed = findViewById(R.id.checkBox_speed);
        mcheckBox_turns = findViewById(R.id.checkBox_turns);
        mcheckBox_speed_cams_yandex = findViewById(R.id.checkBox_speed_cams_yandex);
        mcheckBox_speed_cams_mapcam = findViewById(R.id.checkBox_speed_mapcams);
        mcheckBox_start_yandex_navi = findViewById(R.id.checkBox_start_ya_navi);
        mcheckBox_start_mapcam = findViewById(R.id.checkBox_start_mapcam);
        mToggleButton = findViewById(R.id.toggle);
        btStatusText = findViewById(R.id.btStatusText);
        btStatusDot = findViewById(R.id.btStatusDot);
        updateBtStatus(false);
        mToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                beginSession();
            }
        });

        // Keep the HC-05 link alive for the whole lifetime of the activity.
        startBtMonitor();

        // Auto-start when the app was opened by the vehicle tag. It is deferred to
        // onResume(), because requesting the screen-capture consent requires the
        // activity to be in the foreground already.
        autoStartPending = launchedFromNfc;
    }

    /**
     * Starts the capture/recognition/transmission session. Shared by the START
     * button and by the NFC auto-start.
     */
    private void beginSession() {
        // Guard against re-entry: while the consent dialog is on screen the
        // session is neither active nor stopped, so repeated NFC triggers (the
        // tag stays in the field) would otherwise start it over and over.
        if (sessionStarting || sessionActive) {
            Log.d(TAG, "beginSession(): сессия уже запущена или запрашивается - пропускаем");
            return;
        }
        sessionStarting = true;
        sSessionBusy = true;
        if (!mToggleButton.isChecked()) {
            mToggleButton.setChecked(true);
        }
        if(mcheckBox_speed.isChecked()){   // проверяем какие опции распознавания выбраны на стартовом экране
            Speed_is_checked = 1;
        }
        if(mcheckBox_turns.isChecked()){
            Turns_is_checked = 1;
        }
        if(mcheckBox_speed_cams_yandex.isChecked()){
            Speed_cams_ya_is_checked = 1;
        }
        if(mcheckBox_speed_cams_mapcam.isChecked()){
            Speed_cams_mapcam_is_checked = 1;
        }
        // проверяем разрешения и запускаем получение картинки через интент
        serviceIntent.putExtra("inputExtra", "Screen Recording in Progress");
        ContextCompat.startForegroundService(this, serviceIntent);
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // The session cannot start without storage access; allow a later retry.
            sessionStarting = false;
            sSessionBusy = false;
            if (ActivityCompat.shouldShowRequestPermissionRationale
                    (this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                mToggleButton.setChecked(false);

                Snackbar.make(findViewById(android.R.id.content), R.string.label_permissions,
                        Snackbar.LENGTH_INDEFINITE).setAction("ENABLE",
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                ActivityCompat.requestPermissions(MainActivity.this,
                                        new String[]{Manifest.permission
                                                .WRITE_EXTERNAL_STORAGE},
                                        REQUEST_PERMISSIONS);
                            }
                        }).show();
            } else {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission
                                .WRITE_EXTERNAL_STORAGE},
                        REQUEST_PERMISSIONS);
            }
        } else {
            onToggleSave(mToggleButton); // обработчик картинки
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isForeground = true;
        if (nfcAdapter != null) {
            Bundle options = new Bundle();
            options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250);
            nfcAdapter.enableReaderMode(this,
                    new NfcAdapter.ReaderCallback() {
                        @Override
                        public void onTagDiscovered(Tag tag) {
                            handleNfcTag(tag);
                        }
                    },
                    NfcAdapter.FLAG_READER_NFC_A
                            | NfcAdapter.FLAG_READER_NFC_B
                            | NfcAdapter.FLAG_READER_NFC_F
                            | NfcAdapter.FLAG_READER_NFC_V,
                    options);
            Log.d(TAG, "NFC: reader mode enabled");
        }

        // Auto-start once the activity is actually resumed (cold start from tag).
        if (autoStartPending) {
            autoStartPending = false;
            Log.d(TAG, "NFC: автозапуск сессии");
            beginSession();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isForeground = false;
        // NOTE: reader mode is intentionally NOT disabled here. While it stays
        // enabled the tag is delivered to onTagDiscovered instead of being
        // re-dispatched as an NDEF intent. That re-dispatch used to relaunch this
        // activity on top of the screen-capture consent dialog and dismiss it.
    }

    /** Reader-mode entry point: logs the tag and reacts to the configured one. */
    private void handleNfcTag(Tag tag) {
        logNfcTag(tag);
        if (isTriggerTag(tag)) {
            onNfcTriggerDetected();
        } else {
            Log.d(TAG, "NFC: не наша метка (reader mode) - игнорируем");
        }
    }

    /** True when the tag is either our NTAG (trigger URI) or the transport card. */
    private boolean isTriggerTag(Tag tag) {
        String uid = bytesToHex(tag.getId());
        if (TARGET_NFC_UID.equalsIgnoreCase(uid)) {
            return true;
        }
        Ndef ndef = Ndef.get(tag);
        if (ndef != null) {
            try {
                ndef.connect();
                NdefMessage msg = ndef.getNdefMessage();
                if (msg != null) {
                    for (NdefRecord r : msg.getRecords()) {
                        Uri uri = r.toUri();
                        if (uri != null && NFC_TRIGGER_URI.equals(uri.toString())) {
                            return true;
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                try {
                    ndef.close();
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }

    /**
     * Single place that decides what to do when the vehicle tag is seen. Shared
     * by the reader-mode callback and by the NDEF intent path.
     */
    private void onNfcTriggerDetected() {
        long now = System.currentTimeMillis();
        boolean duplicate = (now - lastNfcTriggerAt) < NFC_DEBOUNCE_MS;
        lastNfcTriggerAt = now;

        if (sessionStarting) {
            // The capture-consent dialog is on screen - leave it alone.
            Log.d(TAG, "NFC: запрос разрешения уже показан, касание игнорируется");
            return;
        }

        if (sessionActive) {
            // The session is already running - never steal focus from whatever is
            // on screen (for example the navigator started by auto-start).
            Log.d(TAG, "NFC: сессия уже идёт, касание игнорируется");
            if (!isForeground) {
                moveTaskToBack(true);
            }
            return;
        }

        if (duplicate) {
            Log.d(TAG, "NFC: повторное срабатывание, игнорируем");
            if (!isForeground) {
                moveTaskToBack(true);
            }
            return;
        }

        Log.d(TAG, "NFC: автозапуск сессии");
        beginSession();
    }

    /** DIAG: logs the UID, technologies and NDEF details of a tapped tag. */
    private void logNfcTag(Tag tag) {
        StringBuilder uid = new StringBuilder();
        for (byte b : tag.getId()) {
            uid.append(String.format("%02X", b));
        }
        StringBuilder tech = new StringBuilder();
        for (String t : tag.getTechList()) {
            tech.append(t.replace("android.nfc.tech.", "")).append(' ');
        }

        StringBuilder ndefInfo = new StringBuilder("none");
        Ndef ndef = Ndef.get(tag);
        if (ndef != null) {
            try {
                ndef.connect();
                NdefMessage msg = ndef.getNdefMessage();
                ndefInfo = new StringBuilder();
                ndefInfo.append("writable=").append(ndef.isWritable())
                        .append(" maxSize=").append(ndef.getMaxSize())
                        .append(" type=").append(ndef.getType())
                        .append(" records=").append(msg == null ? 0 : msg.getRecords().length);
                if (msg != null) {
                    for (NdefRecord r : msg.getRecords()) {
                        ndefInfo.append(" [tnf=").append(r.getTnf());
                        Uri uri = r.toUri();
                        if (uri != null) {
                            ndefInfo.append(" URI=").append(uri);
                        }
                        ndefInfo.append(" payload=").append(bytesToHex(r.getPayload()))
                                .append("]");
                    }
                }
            } catch (Exception e) {
                ndefInfo = new StringBuilder("error:" + e.getMessage());
            } finally {
                try {
                    ndef.close();
                } catch (Exception ignored) {
                }
            }
        }

        Log.e(TAG, "DIAG NFC tag UID=" + uid + " tech=[" + tech.toString().trim() + "] ndef=" + ndefInfo);
    }

    private static String bytesToHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /** Used by NfcLaunchActivity: a session exists or is being started. */
    static boolean isSessionBusy() {
        return sSessionBusy;
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // NfcLaunchActivity validated the tag already; it only asks for a start.
        if (intent.getBooleanExtra(EXTRA_AUTO_START, false)) {
            Log.d(TAG, "NFC onNewIntent: EXTRA_AUTO_START");
            onNfcTriggerDetected();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CODE) {
            Log.e(TAG, "Unknown request code: " + requestCode);
            return;
        }
        if (resultCode != RESULT_OK) {
            Toast.makeText(this,
                    "Screen Cast Permission Denied", Toast.LENGTH_SHORT).show();
            mToggleButton.setChecked(false);
            sessionStarting = false;
            sSessionBusy = false;

            return;
        }
        if(mcheckBox_start_yandex_navi.isChecked()){
            launchYandexNavigator();
        }
        if(mcheckBox_start_mapcam.isChecked()) {
            launch_mapcam_droid();
        }


        mMediaProjectionCallback = new MediaProjectionCallback();
        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
        mMediaProjection.registerCallback(mMediaProjectionCallback, null);
        // NOTE: the VirtualDisplay is created by resumeCapture() as soon as the
        // HC-05 link is up (see the HC-05 watchdog below). This keeps capture,
        // recognition and transmission tied to a live controller.

        tessBaseApi = new TessBaseAPI();
        tessBaseApi.init(Environment.getExternalStorageDirectory().getAbsolutePath(), "Rus");
        //tessBaseApi.setPageSegMode(TessBaseAPI.PageSegMode.PSM_RAW_LINE);
        //tessBaseApi.setVariable(TessBaseAPI, "OEM_LSTM_ONLY");
        tessBaseApi.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "_АБВГДЕЁЖЗИКЛМНОПРСТУФХЦЧЪЬЭЮЯабвгдеёжзилнопрстуфхцъьэюя");
        tessBaseApi.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "!?@#$%&*()<>-+=/:;.,0123456789мк6ч");
        //tessBaseApi.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789");

        if (OpenCVLoader.initLocal()) {
            Log.i(TAG, "OpenCV loaded successfully");
        } else {
            Log.e(TAG, "OpenCV initialization failed!");
            return;
        }
        //System.out.println("Out_OpenCV loaded successfully");
        temple_arrow_right = Imgcodecs.imread("/storage/emulated/0/Pictures/Screenshots/arrow_right.png");
        Imgproc.cvtColor(temple_arrow_right, temple_arrow_right, Imgproc.COLOR_BGR2GRAY);

        temple_arrow_left = Imgcodecs.imread("/storage/emulated/0/Pictures/Screenshots/arrow_left.png");
        Imgproc.cvtColor(temple_arrow_left, temple_arrow_left, Imgproc.COLOR_BGR2GRAY);

        temple_arrow_top_right = Imgcodecs.imread("/storage/emulated/0/Pictures/Screenshots/arrow_top_right.png");
        Imgproc.cvtColor(temple_arrow_top_right, temple_arrow_top_right, Imgproc.COLOR_BGR2GRAY);

        temple_arrow_top_left = Imgcodecs.imread("/storage/emulated/0/Pictures/Screenshots/arrow_top_left.png");
        Imgproc.cvtColor(temple_arrow_top_left, temple_arrow_top_left, Imgproc.COLOR_BGR2GRAY);

        temple_arrow_finish = Imgcodecs.imread("/storage/emulated/0/Pictures/Screenshots/arrow_finish.png");
        Imgproc.cvtColor(temple_arrow_finish, temple_arrow_finish, Imgproc.COLOR_BGR2GRAY);

        temple_arrow_u_turn = Imgcodecs.imread("/storage/emulated/0/Pictures/Screenshots/arrow_u_turn.png");
        Imgproc.cvtColor(temple_arrow_u_turn, temple_arrow_u_turn, Imgproc.COLOR_BGR2GRAY);

        temple_arrow_ring = Imgcodecs.imread("/storage/emulated/0/Pictures/Screenshots/arrow_ring.png");
        Imgproc.cvtColor(temple_arrow_ring, temple_arrow_ring, Imgproc.COLOR_BGR2GRAY);

        temple_speed_cam_day_yandex = Imgcodecs.imread("/storage/emulated/0/Pictures/Screenshots/speed_cam_day_yandex.png");
        Imgproc.cvtColor(temple_speed_cam_day_yandex, temple_speed_cam_day_yandex, Imgproc.COLOR_BGR2GRAY);

        temple_speed_cam_night_yandex = Imgcodecs.imread("/storage/emulated/0/Pictures/Screenshots/speed_cam_night_yandex.png");
        Imgproc.cvtColor(temple_speed_cam_night_yandex, temple_speed_cam_night_yandex, Imgproc.COLOR_BGR2GRAY);

        temple_speed_cam_mapcam = Imgcodecs.imread("/storage/emulated/0/Pictures/Screenshots/speed_cam_mapcam_big.png");
        Imgproc.cvtColor(temple_speed_cam_mapcam, temple_speed_cam_mapcam, Imgproc.COLOR_BGR2GRAY);

        // The HC-05 watchdog (started in onCreate) connects to the controller off
        // the UI thread and creates the VirtualDisplay once the link is up.
        sessionActive = true;
        sessionStarting = false;
        if (btConnected) {
            resumeCapture();
        } else {
            Log.d(TAG, "...Ждём подключения HC-05 перед началом захвата...");
        }
    }
    public void onToggleSave(View view) {

        //System.out.println("Out_onToggleSave");
        if (((ToggleButton) view).isChecked()) {
            //if (mMediaProjection == null & btSocket.isConnected()) {
            if (mMediaProjection == null) {
                startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
            }
            imageReader.setOnImageAvailableListener(reader -> {
                Bitmap bitmap = null;
                frameCount++;
                if (frameCount % 30 == 0) {
                    Log.d(TAG, "DIAG frame " + frameCount + " btConnected=" + btConnected);
                }
                try (Image image = imageReader.acquireLatestImage()) {
                    final Image.Plane[] planes = image.getPlanes();
                    final ByteBuffer buffer = planes[0].getBuffer();
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * DISPLAY_WIDTH;
                    // create bitmap
                    bitmap = Bitmap.createBitmap(DISPLAY_WIDTH + rowPadding / pixelStride, DISPLAY_HEIGHT, Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(buffer);
                    image.close();
//*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-* OPenCV *-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*
                    Mat img = new Mat();
                    Utils.bitmapToMat(bitmap, img);

                    // Crop the image
                    int height = img.rows();
                    int width = img.cols();
                    if (frameCount % 60 == 0) {
                        Log.d(TAG, "DIAG width " + width + " - " + height);
                    }
                    Mat croppedImg = img.submat(80, height / 6, 0, width);

                    // Resize the image
                    height = croppedImg.rows();
                    width = croppedImg.cols();
                    int resizeCoeff = 2;
                    Size newSize = new Size((double) width / resizeCoeff, (double) height / resizeCoeff);
                    Mat resizedImg = new Mat();
                    Imgproc.resize(croppedImg, resizedImg, newSize, 0, 0, Imgproc.INTER_AREA);

//yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy seeking speed   yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy
                    if(Speed_is_checked > 0) {
                        //extract red and blur

                        Mat croppedImg_rSide = resizedImg.submat(0, resizedImg.rows(), 2 * resizedImg.cols() / 3, resizedImg.cols());
                        Mat hsv_croppedImg_rSide = new Mat();
                        Imgproc.cvtColor(croppedImg_rSide, hsv_croppedImg_rSide, Imgproc.COLOR_RGB2HSV);


                        Scalar hsv_min1 = new Scalar(1, 150, 180);
                        Scalar hsv_max1 = new Scalar(5, 255, 255);
                        Mat only_RED = new Mat();
                        Core.inRange(hsv_croppedImg_rSide, hsv_min1, hsv_max1, only_RED);

                        //Imgproc.blur(only_RED, only_RED, new Size(5, 5));


                        // detect circles
                        Mat circles = new Mat();
                        Imgproc.HoughCircles(only_RED, circles, Imgproc.CV_HOUGH_GRADIENT, 3, 100, 20, 100);

                        // The circle detection may legitimately find nothing. In that case
                        // circles is empty and circles.get(0,0) is null, so guard against it
                        // instead of throwing a NullPointerException that aborts the frame.
                        if (circles.cols() > 0) {
                            // define region of interest
                            int x = (int) Math.round(circles.get(0, 0)[0]);
                            int y = (int) Math.round(circles.get(0, 0)[1]);
                            int r = (int) Math.round(circles.get(0, 0)[2]);
                            Rect roi2 = new Rect(x - r + 10, y - r / 2, 2 * r - 20, r);
                            //System.out.println("Out_reader" + roi2);
                            Mat roi_img = new Mat(croppedImg_rSide, roi2);


                            // convert to grayscale
                            Mat gray_roi = new Mat();
                            Imgproc.cvtColor(roi_img, gray_roi, Imgproc.COLOR_RGB2GRAY);

                            // convert to black and white
                            Mat binary_roi = new Mat();
                            threshold(gray_roi, binary_roi, 127, 255, THRESH_BINARY);

                            // Create the TextRecognizer
                            //System.out.println("Out_start_TextRecognizer_speed");
                            int speed = get_speed(binary_roi);
                            //System.out.println("Out_тессер                  " + speed);
                            if (speed > 10) {
                                send_speedToController(speed);
                            }
                        }
                    }

//yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy end seeking speed   yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy

//yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy seeking turns  yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy
                    if(Turns_is_checked  > 0) {

                        //+++++    выделяем синее +++++++++
                        Mat croppedImg_lSide = resizedImg.submat(0, resizedImg.rows(), 0, 2 * resizedImg.cols() / 3);
                        Mat hsv_croppedImg_lSide = new Mat();
                        Imgproc.cvtColor(croppedImg_lSide, hsv_croppedImg_lSide, Imgproc.COLOR_RGB2HSV);
                        Scalar hsv_min = new Scalar(106, 190, 240);
                        Scalar hsv_max = new Scalar(113, 255, 255);
                        Mat onlyBlue = new Mat();
                        Core.inRange(hsv_croppedImg_lSide, hsv_min, hsv_max, onlyBlue);

                        // ищем прямоугольники
                        Mat threshold = new Mat();
                        threshold(onlyBlue, threshold, 190, 255, THRESH_BINARY);
                        // apply morphology
                        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
                        Mat clean = new Mat();
                        Imgproc.morphologyEx(onlyBlue, clean, Imgproc.MORPH_OPEN, kernel);
                        kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(20, 20));
                        Imgproc.morphologyEx(onlyBlue, clean, Imgproc.MORPH_CLOSE, kernel);
                        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
                        Imgproc.findContours(clean, contours, new Mat(), 0, 1);

                        // delete small shapes
                        int rect_x = 0;
                        int rect_y = 0;
                        int rect_width = 0;
                        int rect_height = 0;
                        for (int i = 0; i < contours.size(); i++) {

                            if (Imgproc.contourArea(contours.get(i)) > 50) {
                                MatOfPoint2f mMOP2f1 = new MatOfPoint2f();
                                MatOfPoint2f mMOP2f2 = new MatOfPoint2f();
                                contours.get(i).convertTo(mMOP2f1, CvType.CV_32FC2);
                                Imgproc.approxPolyDP(mMOP2f1, mMOP2f2, 3, true);
                                mMOP2f2.convertTo(contours.get(i), CvType.CV_32S);
                                Rect rect = Imgproc.boundingRect(contours.get(i));
                                if ((rect.width > 50) & (rect.height > 50)) {  // берем только большие прямоугольники
                                    rect_x = rect.x;
                                    rect_y = rect.y;
                                    rect_width = rect.width;
                                    rect_height = rect.height;
                                    //System.out.println("rectangle = " + rect.x + "," + rect.y + "," + rect.height + "," + rect.width);

                                }
                            }
                        }

                        // _____________ определяем регион интересов
                        if (rect_height > 100) {
                            rect_height = 5 + rect_height / 2;
                        }
                        // When no blue turn rectangle is found on the frame the ROI bounds would
                        // be invalid; skip the turns branch instead of throwing a CvException that
                        // aborts the whole frame (and any pending transmission).
                        boolean turnsRoiValid = rect_width > 20 && rect_height > 10
                                && (rect_x + rect_width - 10) > (rect_x + 10)
                                && (rect_y + rect_height) <= croppedImg_lSide.rows()
                                && (rect_x + rect_width - 10) <= croppedImg_lSide.cols();
                        if (turnsRoiValid) {
                            Mat roi = croppedImg_lSide.submat(rect_y + 10, rect_y + rect_height, rect_x + 10, rect_x + rect_width - 10);

                            // __________ переводим в серое _________________
                            Imgproc.cvtColor(roi, roi, Imgproc.COLOR_RGB2GRAY);
                            Mat binary_roi2 = new Mat();
                            threshold(roi, binary_roi2, 220, 255, THRESH_BINARY);
                            Core.bitwise_not(binary_roi2, binary_roi2);
                            Imgproc.blur(binary_roi2, binary_roi2, new Size(5, 5));
                            if (roi.cols() > 50) {
                                Mat roi2_cropped = binary_roi2.submat(0, roi.rows(), 50, roi.cols());

                                //------------------- get distance turn --------------------------------------------------
                                Object[] array;
                                array = get_turn_dist(roi2_cropped);
                                int dist_H = (int) array[0];
                                int dist_L = (int) array[1];
                                int dist_type = (int) array[2];
                                int decimal = (int) array[3];
                                System.out.println("Out_decimal     " + decimal);

                                // __________ get turn type --------------------------------------------------- _________________
                                Mat roi_leftSide;
                                roi_leftSide = binary_roi2.submat(0, binary_roi2.rows(), 0, binary_roi2.cols() / 3);
                                int turns_type = get_turn_type(roi_leftSide);
                                //---------------- send data to controller ------------------------------------------------------
                                if (dist_L > 0) {
                                    send_turnsToController(turns_type, dist_H, dist_L, dist_type, decimal);
                                }
                            }
                        }
                    }

//yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy end seeking turns  yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy
                    //yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy seeking speed camera yandex yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy
                    if(Speed_cams_ya_is_checked > 0 ) {
                        //+++++    get roi +++++++++
                        // Crop the image
                        //Log.e(TAG, "width " + String.valueOf(width) + " - " + height);
                        Mat croppedImg_speedCam = img.submat(345, 450, 723, 1030);

                        // Resize the image
                        height = croppedImg_speedCam.rows();
                        width = croppedImg_speedCam.cols();
                        Size newSize_speedCam = new Size((double) width / resizeCoeff, (double) height / resizeCoeff);
                        Mat resizedImg_speedCam = new Mat();
                        Imgproc.resize(croppedImg_speedCam, resizedImg_speedCam, newSize_speedCam, 0, 0, Imgproc.INTER_AREA);
                        // __________ переводим в серое _________________
                        Imgproc.cvtColor(resizedImg_speedCam, resizedImg_speedCam, Imgproc.COLOR_RGB2GRAY);
                        Mat binary_roi_speedCam = new Mat();
                        threshold(resizedImg_speedCam, binary_roi_speedCam, 127, 255, THRESH_BINARY);
                        int cam_type_yandex = get_cam_type_yandex(binary_roi_speedCam);
                        //System.out.println("Out_cam_type_yandex    " + cam_type_yandex);
                        if (cam_type_yandex > 0) {
                            Object[]    array = get_turn_dist(binary_roi_speedCam);
                            int dist_H = (int) array[0];
                            int dist_L = (int) array[1];
                            int dist_type = (int) array[2];
                            int decimal = (int) array[3];
                            System.out.println("Out_cam_dist_L    " + dist_L);
                            send_speed_cam_dist(dist_H, dist_L, 0);
                        }
                    }

                    if(Speed_cams_mapcam_is_checked > 0) {
                        // Crop the image
                        croppedImg = img.submat(img.rows() / 2, 4 * img.rows()/5, img.cols() / 2, img.cols());
                        Imgproc.cvtColor(croppedImg, resizedImg, Imgproc.COLOR_RGB2BGR);
                        // Определяем диапазон красного цвета в формате BGR
                        Scalar lowerRed = new Scalar(0, 0, 100); // Нижняя граница красного цвета
                        Scalar upperRed = new Scalar(100, 100, 255); // Верхняя граница красного цвета

                        // Создаем маску для красных пикселей
                        Mat mask = new Mat();
                        Core.inRange(resizedImg, lowerRed, upperRed, mask);

                        // Заменяем красные пиксели на белые
                        resizedImg.setTo(new Scalar(255, 255, 255), mask);

                        Imgproc.cvtColor(resizedImg, resizedImg, Imgproc.COLOR_BGR2GRAY);
                        Mat binary_roi3 = new Mat();
                        threshold(resizedImg, binary_roi3, 140, 255, THRESH_BINARY);

                        Object[] ret = seeking_mapcam(binary_roi3);
                        int x = (int) ret[0];
                        int y = (int) ret[1];
                        if (x > 0) {       // if mapcam widget found
                            Mat croppedImg_1 = binary_roi3.submat(y, y + (binary_roi3.cols() - x) / 2, x, 7 * binary_roi3.cols() / 8);
                            //////+++++++++++++ seek speed  ++++++++++++++++++++++++++++++
                            Mat roi_speed = croppedImg_1.submat(croppedImg_1.rows() / 20, 9 * croppedImg_1.rows() / 20, croppedImg_1.cols() / 2, 10 * croppedImg_1.cols() / 11);
                            Imgproc.blur(roi_speed, roi_speed, new Size(8, 8));
                            threshold(roi_speed, roi_speed, 80, 255, THRESH_BINARY);
                            int speed = get_speed(roi_speed);
                            // ++++++++++++++   end seek speed +++++++++++++++++++++++++++++++++++
                            // +++++++++++++++   distance seeking ++++++++++++++++++++++++++++++++
                            Mat roi_dist = croppedImg_1.submat(3 * croppedImg_1.rows() / 4, croppedImg_1.rows(), 0, 4 * croppedImg_1.cols() / 10);
                            //uuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu
                           /* FileOutputStream fos = null;
                            Date currentTime = Calendar.getInstance().getTime();
                            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "screenshot " + currentTime + ".png");
                            fos = new FileOutputStream(file);
                            Bitmap bitmap1 = null;
                            bitmap1 = Bitmap.createBitmap(roi_dist.width(), roi_dist.height(), Bitmap.Config.ARGB_8888);

                            Utils.matToBitmap(roi_dist, bitmap1);
                            bitmap1.compress(Bitmap.CompressFormat.JPEG, 100, fos);*/
                            //tttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu
                            Object[] array_mapcam_dist = get_turn_dist(roi_dist);
                            int dist_H = (int) array_mapcam_dist[0];
                            int dist_L = (int) array_mapcam_dist[1];
                            int dist_type = (int) array_mapcam_dist[2];
                            int decimal = (int) array_mapcam_dist[3];
                            if (dist_L > 0){
                                send_speed_cam_dist(dist_H, dist_L, 0);
                                //System.out.println("Out_cam_dist_L    " + dist_L);
                                //System.out.println("Out_cam_dist_type    " + dist_type);
                            }

                        }
                    }
                    // Thread.sleep(300);
                    //System.out.println("Out_end_cycle ");
//yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy end seeking speed cams  yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy

                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, "DIAG processing exception", e);
                } finally {
                    if (bitmap != null)
                        bitmap.recycle();

                }
            }, null);


        } else {
            stopScreenSharing();
        }
    }

    //*********************************************************************************************************************************************************
    public Object[] get_mapcam_data(Mat roi2_cropped){
        int dist_L = 0;
        int dist_H = 0;
        int dist_type = 0;
        int speed = 0;
        Bitmap imageBitmap = Bitmap.createBitmap(roi2_cropped.cols(), roi2_cropped.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(roi2_cropped, imageBitmap); // преобразуем в битмап для поиска текста
        tessBaseApi.setImage(imageBitmap);
        String extractedText = tessBaseApi.getUTF8Text();
        extractedText = extractedText.replaceAll("\\p{C}", "|");
        System.out.println("Out_tes_turns_extractedText  " + extractedText);
        String final_output = extractedText.replace("?", "");
        final_output = final_output.replace("*", "");
        final_output = final_output.replace("@", "");
        final_output = final_output.replace("#", "");
        final_output = final_output.replace("(", "");
        final_output = final_output.replace(")", "");
        final_output = final_output.replace(">", "");
        final_output = final_output.replace("$", "");
        final_output = final_output.replace("!", "");
        final_output = final_output.replace("+", "");
        final_output = final_output.replace("К", "");
        final_output = final_output.replace(". ", "");
        final_output = final_output.replace(", ", "");
        final_output = final_output.replace(" м", "м");
        final_output = final_output.replace(" км", "км");
        final_output = final_output.replace("к ", "");
        final_output = final_output.replaceAll(" ", "");

        if (final_output.charAt(0) == '0') {
            final_output = final_output.substring(1);
        }
        if (final_output.charAt(0) == '.') {
            final_output = final_output.substring(1);
        }
        if (final_output.charAt(0) == ',') {
            final_output = final_output.substring(1);
        }
        if (final_output.charAt(0) == 'к') {
            final_output = final_output.substring(1);
        }
        if (final_output.charAt(0) == '|') {
            final_output = final_output.substring(1);
        }
        //System.out.println("Out_tes_turns_gggggggggggggggggg  " + final_output);
        //final_output = final_output.replaceAll("\\p{C}", "|");
        //System.out.println("Out_tes_turns_iiiiiiiiiiiiiiiii  " + final_output);
        if (final_output.contains("|")) {
            String test = final_output.substring(0, final_output.indexOf('|'));
            if (test.contains("м")) {
                final_output = test;
            } else {
                final_output = final_output.substring(final_output.indexOf('|') + 1);
            }
        }
        if (final_output.contains("|")) {
            String test = final_output.substring(0, final_output.indexOf('|'));
            if (test.contains("м")) {
                final_output = test;
            } else {
                final_output = final_output.substring(final_output.indexOf('|') + 1);
            }
        }
        //System.out.println("Out_tes_turns12_  " + final_output);

        if (final_output.contains("км")) {  // проверяем километры или метры
            dist_type = 16;
            //System.out.println("Out_Dist_type_             км");
        } else if (final_output.contains("м")) {
            dist_type = 1;
            //System.out.println("Out_Dist_type_              м");
        }
        final_output = final_output.substring(0, final_output.indexOf("м")+1);
        //System.out.println("Out_distance_sssss            " + final_output);
        final_output = final_output.replace(",", ".");
        final_output = final_output.replaceAll("[^0-9.,]", "");  //оставляем только числа в строке
        // OCR may leave nothing to parse (e.g. no distance digits on screen).
        float distance = 0;
        try {
            distance = Float.parseFloat(final_output);
        } catch (NumberFormatException e) {
            distance = 0;
        }
        //System.out.println("Out_distance_            " + distance);
        if (distance > 9999) {distance = 0;};   //remove big numbers
        //System.out.println("Out_tes_turns_distance  " + distance);
        //int distance_round = Math.round(distance);  // округляем расстояние
        if (distance > 999 & distance < 9999){   // delete first "1" in the start of distance
            int temp = (int) (distance/100);
            int temp1 = temp*100;
            if (temp1 == distance){
                distance = temp1 % 1000;   // take last 3 digits
            }
        }
        int decimal = 0;  // get number after point
        if (dist_type == 16 & distance < 255){
            decimal = (int) ((distance - (int) distance)*10);
        }
        System.out.println("Out_tes_turns_distance  " + distance);

        ByteBuffer b = ByteBuffer.allocate(4);
        b.putInt((int) distance);
        byte[] result = b.array();
        dist_L = Byte.toUnsignedInt(result[3]);    // выделяем страрший и младший байт для проектора
        dist_H = Byte.toUnsignedInt(result[2]);
        Object[] array = new Object[5];
        array[0] = dist_H;
        array[1] = dist_L;
        array[2] = dist_type;
        array[3] = decimal;
        return array;


    }
    public int get_speed(Mat binary_roi){
        Bitmap imageBitmap = Bitmap.createBitmap(binary_roi.cols(), binary_roi.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(binary_roi, imageBitmap);
        //System.out.println("Out_init_TextRecognizer_speed");
        tessBaseApi.setImage(imageBitmap);
        String extractedText = tessBaseApi.getUTF8Text();
        //extractedText = extractedText.replaceAll("\\p{C}", "|");
        System.out.println("Out_тессер  extractedText                " + extractedText);
        String final_output1 = extractedText.replace("(", "");
        final_output1 = final_output1.replaceAll(" ", "");
        final_output1 = final_output1.replace("(", "");
        final_output1 = final_output1.replace(")", "");
        final_output1 = final_output1.replace("?", "");
        final_output1 = final_output1.replace("!", "");
        final_output1 = final_output1.replace(";", "");
        final_output1 = final_output1.replace(":", "");
        final_output1 = final_output1.replace("*", "");
        final_output1 = final_output1.replace(" ", "");
        final_output1 = final_output1.replace("-", "");
        final_output1 = final_output1.replace(",", "");
        final_output1 = final_output1.replace(".", "");
        // OCR may return nothing usable; bail out instead of crashing the frame.
        if (final_output1.isEmpty()) {
            return 0;
        }
        if (final_output1.charAt(final_output1.length() - 1) == '1') {
            final_output1 = final_output1.substring(0, final_output1.length() - 1);
        }
        //System.out.println("Out_тессер  raw                " + final_output1);
        int speed = 0;
        try {
            speed = Integer.parseInt(final_output1);
        } catch (NumberFormatException e) {
            return 0;
        }
        if (speed > 130 & speed < 200) {
            speed = speed - 100;
        }
        if (speed > 199) {
            speed = speed / 10;
        }
        if (speed == 120) {
            speed = 20;
        }
        return speed;
    }
    public Object[] seeking_mapcam(Mat binary_roi_speedCam){
        Mat res = new Mat();
        double thresholdValue = 0.7;
        Imgproc.matchTemplate(binary_roi_speedCam, temple_speed_cam_mapcam, res, Imgproc.TM_CCOEFF_NORMED); // сравниваем темплейт с регионом интересов
        Core.MinMaxLocResult mmr = Core.minMaxLoc(res);
        if (mmr.maxVal >= thresholdValue) {   // берем только если вероятность превышает thresholdValue
            //System.out.println("Out_maxLoc     " +    mmr.maxLoc);
            Object[] array = new Object[3];
            array[0] = (int) mmr.maxLoc.x;
            array[1] = (int) mmr.maxLoc.y;
            return array;

        }
        return new int[][]{new int[]{0}};
    }
    public int get_cam_type_yandex(Mat binary_roi_speedCam){
        Mat res = new Mat();
        double thresholdValue = 0.7;
        Imgproc.matchTemplate(binary_roi_speedCam, temple_speed_cam_day_yandex, res, Imgproc.TM_CCOEFF_NORMED); // сравниваем темплейт с регионом интересов
        Core.MinMaxLocResult mmr = Core.minMaxLoc(res);
        if (mmr.maxVal >= thresholdValue) {   // берем только если вероятность превышает thresholdValue
            return 1;
        }
        Imgproc.matchTemplate(binary_roi_speedCam, temple_speed_cam_night_yandex, res, Imgproc.TM_CCOEFF_NORMED); // сравниваем темплейт с регионом интересов
        mmr = Core.minMaxLoc(res);
        if (mmr.maxVal >= thresholdValue) {   // берем только если вероятность превышает thresholdValue
            return 2;
        }
        return 0;
    }

    public Object[] get_turn_dist(Mat roi2_cropped){
        int dist_L = 0;
        int dist_H = 0;
        int dist_type = 0;
        Bitmap imageBitmap = Bitmap.createBitmap(roi2_cropped.cols(), roi2_cropped.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(roi2_cropped, imageBitmap); // преобразуем в битмап для поиска текста
        tessBaseApi.setImage(imageBitmap);
        String extractedText = tessBaseApi.getUTF8Text();
        extractedText = extractedText.replaceAll("\\p{C}", "|");
        //System.out.println("Out_tes_turns_extractedText  " + extractedText);
        String final_output = extractedText.replace("?", "");
        final_output = final_output.replace("*", "");
        final_output = final_output.replace("@", "");
        final_output = final_output.replace("#", "");
        final_output = final_output.replace("(", "");
        final_output = final_output.replace(")", "");
        final_output = final_output.replace(">", "");
        final_output = final_output.replace("$", "");
        final_output = final_output.replace("!", "");
        final_output = final_output.replace("+", "");
        final_output = final_output.replace("К", "");
        final_output = final_output.replace(". ", "");
        final_output = final_output.replace(", ", "");
        final_output = final_output.replace(" м", "м");
        final_output = final_output.replace(" км", "км");
        final_output = final_output.replace("к ", "");
        final_output = final_output.replaceAll(" ", "");

        if (final_output.charAt(0) == '0') {
            final_output = final_output.substring(1);
        }
        if (final_output.charAt(0) == '.') {
            final_output = final_output.substring(1);
        }
        if (final_output.charAt(0) == ',') {
            final_output = final_output.substring(1);
        }
        if (final_output.charAt(0) == 'к') {
            final_output = final_output.substring(1);
        }
        if (final_output.charAt(0) == '|') {
            final_output = final_output.substring(1);
        }
        //System.out.println("Out_tes_turns_gggggggggggggggggg  " + final_output);
        //final_output = final_output.replaceAll("\\p{C}", "|");
        //System.out.println("Out_tes_turns_iiiiiiiiiiiiiiiii  " + final_output);
        if (final_output.contains("|")) {
            String test = final_output.substring(0, final_output.indexOf('|'));
            if (test.contains("м")) {
                final_output = test;
            } else {
                final_output = final_output.substring(final_output.indexOf('|') + 1);
            }
        }
        if (final_output.contains("|")) {
            String test = final_output.substring(0, final_output.indexOf('|'));
            if (test.contains("м")) {
                final_output = test;
            } else {
                final_output = final_output.substring(final_output.indexOf('|') + 1);
            }
        }
        //System.out.println("Out_tes_turns12_  " + final_output);

        if (final_output.contains("км")) {  // проверяем километры или метры
            dist_type = 16;
            //System.out.println("Out_Dist_type_             км");
        } else if (final_output.contains("м")) {
            dist_type = 1;
            //System.out.println("Out_Dist_type_              м");
        }
        final_output = final_output.substring(0, final_output.indexOf("м")+1);
        //System.out.println("Out_distance_sssss            " + final_output);
        final_output = final_output.replace(",", ".");
        final_output = final_output.replaceAll("[^0-9.,]", "");  //оставляем только числа в строке
        // OCR may leave nothing to parse (e.g. no distance digits on screen).
        float distance = 0;
        try {
            distance = Float.parseFloat(final_output);
        } catch (NumberFormatException e) {
            distance = 0;
        }
        //System.out.println("Out_distance_            " + distance);
        if (distance > 9999) {distance = 0;};   //remove big numbers
        //System.out.println("Out_tes_turns_distance  " + distance);
        //int distance_round = Math.round(distance);  // округляем расстояние
        if (distance > 999 & distance < 9999){   // delete first "1" in the start of distance
            int temp = (int) (distance/100);
            int temp1 = temp*100;
            if (temp1 == distance){
                distance = temp1 % 1000;   // take last 3 digits
            }
        }
        int decimal = 0;  // get number after point
        if (dist_type == 16 & distance < 255){
            decimal = (int) ((distance - (int) distance)*10);
        }
        System.out.println("Out_tes_turns_distance  " + distance);

        ByteBuffer b = ByteBuffer.allocate(4);
        b.putInt((int) distance);
        byte[] result = b.array();
        dist_L = Byte.toUnsignedInt(result[3]);    // выделяем страрший и младший байт для проектора
        dist_H = Byte.toUnsignedInt(result[2]);
        Object[] array = new Object[5];
        array[0] = dist_H;
        array[1] = dist_L;
        array[2] = dist_type;
        array[3] = decimal;
        return array;


    }
    public void send_speed_cam_dist (int dist_H, int dist_L, int dist_type){
        sendDBytes((byte) 51);
        sendDBytes((byte) 51);
        sendDBytes((byte) 51);
        sendDBytes((byte) 0xA4); // 0x115
        sendDBytes((byte) dist_H);
        sendDBytes((byte) dist_L);
        sendDBytes((byte) dist_type);
        sendDBytes((byte) 0);
        sendDBytes((byte) 0);
        sendDBytes((byte) 0);
        sendDBytes((byte) 0); //
        sendDBytes((byte) 0);
        sendDBytes((byte) 52);
        sendDBytes((byte) 52);
        sendDBytes((byte) 52);
    }
    public void send_turnsToController (int turns_type, int dist_H, int dist_L, int dist_type, int decimal){
        sendDBytes((byte) 51);
        sendDBytes((byte) 51);
        sendDBytes((byte) 51);
        sendDBytes((byte) 0xA3); // 0x115
        sendDBytes((byte) turns_type);
        sendDBytes((byte) 0);
        sendDBytes((byte) 0);
        sendDBytes((byte) 0);
        sendDBytes((byte) dist_H);
        sendDBytes((byte) dist_L);
        sendDBytes((byte) dist_type); //
        sendDBytes((byte) decimal);
        sendDBytes((byte) 52);
        sendDBytes((byte) 52);
        sendDBytes((byte) 52);
    }
    public void send_speedToController (int speed){
        sendDBytes((byte) 51);
        sendDBytes((byte) 51);
        sendDBytes((byte) 51);
        sendDBytes((byte) 0xA1); // 1E5
        sendDBytes((byte) 0);
        sendDBytes((byte) 0);
        sendDBytes((byte) 0);
        sendDBytes((byte) 0);
        sendDBytes((byte) 0);
        sendDBytes((byte) 0);
        sendDBytes((byte) 0x30); // blue ограничение скорости
        sendDBytes((byte) speed);  //ограничение скорости
        sendDBytes((byte) 52);
        sendDBytes((byte) 52);
        sendDBytes((byte) 52);
    }
    public int get_turn_type(Mat roi_leftSide){
        //jjjjjjjjjjjjjjjjjjj    right turn jjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjj
        Mat res = new Mat();
        double thresholdValue = 0.7;
        Imgproc.matchTemplate(roi_leftSide, temple_arrow_right, res, Imgproc.TM_CCOEFF_NORMED); // сравниваем темплейт с регионом интересов
        Core.MinMaxLocResult mmr = Core.minMaxLoc(res);
        if (mmr.maxVal >= thresholdValue) {   // берем только если вероятность превышает thresholdValue
            System.out.println("Out_Arrow_           right ");
            return 67;
        }
        //jjjjjjjjjjjjjjjjjjj    top right_turn jjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjj
        Imgproc.matchTemplate(roi_leftSide, temple_arrow_top_right, res, Imgproc.TM_CCOEFF_NORMED); // сравниваем темплейт с регионом интересов
        mmr = Core.minMaxLoc(res);
        if (mmr.maxVal >= thresholdValue) {   // берем только если вероятность превышает thresholdValue
            System.out.println("Out_              top right ");
            return 66;
        }
        //jjjjjjjjjjjjjjjjjjj    top left turn jjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjj
        Imgproc.matchTemplate(roi_leftSide, temple_arrow_top_left, res, Imgproc.TM_CCOEFF_NORMED); // сравниваем темплейт с регионом интересов
        mmr = Core.minMaxLoc(res);
        if (mmr.maxVal >= thresholdValue) {   // берем только если вероятность превышает thresholdValue
            System.out.println("Out_Arrow_         top left ");
            return 71;
        }
        //jjjjjjjjjjjjjjjjjjj    left turn jjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjj
        Imgproc.matchTemplate(roi_leftSide, temple_arrow_left, res, Imgproc.TM_CCOEFF_NORMED); // сравниваем темплейт с регионом интересов
        mmr = Core.minMaxLoc(res);
        if (mmr.maxVal >= thresholdValue) {   // берем только если вероятность превышает thresholdValue
            System.out.println("Out_Arrow_        left ");
            return 70;
        }
        //jjjjjjjjjjjjjjjjjjj    ring   jjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjj
        Imgproc.matchTemplate(roi_leftSide, temple_arrow_ring, res, Imgproc.TM_CCOEFF_NORMED); // сравниваем темплейт с регионом интересов
        mmr = Core.minMaxLoc(res);
        if (mmr.maxVal >= thresholdValue) {   // берем только если вероятность превышает thresholdValue
            System.out.println("Out_Arrow_        ring ");
            return 96;
        }
        //jjjjjjjjjjjjjjjjjjj    left u_turn jjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjj
        Imgproc.matchTemplate(roi_leftSide, temple_arrow_u_turn, res, Imgproc.TM_CCOEFF_NORMED); // сравниваем темплейт с регионом интересов
        mmr = Core.minMaxLoc(res);
        if (mmr.maxVal >= thresholdValue) {   // берем только если вероятность превышает thresholdValue
            System.out.println("Out_Arrow_      left_u_turn ");
            return 72;
        }
        //jjjjjjjjjjjjjjjjjjj    finish     jjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjj
        Imgproc.matchTemplate(roi_leftSide, temple_arrow_finish, res, Imgproc.TM_CCOEFF_NORMED); // сравниваем темплейт с регионом интересов
        mmr = Core.minMaxLoc(res);
        if (mmr.maxVal >= thresholdValue) {   // берем только если вероятность превышает thresholdValue
            System.out.println("Out_Arrow_l          eft_finish ");
            return 3;
        }
        return 0;
    }
    public Bitmap toBmp(Mat mat) {
        Bitmap bmp = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, bmp);
        return bmp;
    }
    private void sendDBytes(byte messageBytes) {
        // Silent no-op while the HC-05 controller is absent.
        if (!btConnected || outStream == null) {
            return;
        }
        Log.d(TAG, "...Bluetooth. Посылаем данные: " + messageBytes);
        try {
            outStream.write(messageBytes);
            Log.d(TAG, "...Bluetooth. данные sended: " + messageBytes);
        } catch (IOException e) {
            // Controller disappeared - pause the pipeline and start re-connecting.
            handleBtFailure(e);
        }
    }
    @SuppressLint("QueryPermissionsNeeded")
    private void launchYandexNavigator() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("yandexnavi://"));
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); // Перевести приложение на передний план, если оно уже запущено

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            // Если приложение Яндекс.Навигатор не установлено, предложить установить его
            Intent marketIntent = new Intent(Intent.ACTION_VIEW);
            marketIntent.setData(Uri.parse("market://details?id=ru.yandex.yandexnavi"));
            startActivity(marketIntent);
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    private void launch_mapcam_droid() {
        String packageName = "info.mapcam.droid"; // Замените на реальный пакет
        String className = "info.mapcam.droid.MainActivity"; // Замените на реальный класс активности

        Intent intent = new Intent();
        intent.setClassName(packageName, className);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        Log.e(TAG, "mapcam_on start _ try");
        try {
            startActivity(intent);
        } catch (Exception e) {
            // Если приложение не найдено, покажем сообщение
            Toast.makeText(this, "Приложение MapCam.Droid не установлено", Toast.LENGTH_SHORT).show();
        }
    }

    private void errorExit(String title, String message){
        Toast.makeText(getBaseContext(), title + " - " + message, Toast.LENGTH_LONG).show();
        finish();
    }
    @SuppressLint("MissingPermission")
    private void checkBTState() {
        // Check for Bluetooth support and then check to make sure it is turned on
        // Emulator doesn't support Bluetooth and will return null
        if(btAdapter==null) {
            Log.d(TAG,"Fatal Error Bluetooth не поддерживается");
        } else {
            if (btAdapter.isEnabled()) {
                Log.d(TAG, "...Bluetooth включен...");
            } else {
                //Prompt user to turn on Bluetooth
                Intent enableBtIntent = new Intent(btAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }
      private class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            Log.w(TAG, "DIAG projection.onStop (system stopped the projection)");
            if (mToggleButton.isChecked()) {
                mToggleButton.setChecked(false);

                Log.v(TAG, "Recording Stopped");
            }
            mMediaProjection = null;
            stopScreenSharing();
        }
    }

    // =========================================================================
    //  HC-05 connection watchdog
    // =========================================================================

    /**
     * Background thread that keeps the HC-05 link alive for the activity's whole
     * lifetime. While disconnected it retries every {@link #BT_CHECK_INTERVAL_MS}
     * ms; while connected it watches for a lost link and pauses the pipeline.
     */
    private void startBtMonitor() {
        if (btMonitorRunning) {
            return;
        }
        btMonitorRunning = true;
        btMonitorThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (btMonitorRunning) {
                    if (!btConnected) {
                        if (tryConnectBt()) {
                            btConnected = true;
                            btAbsentSince = 0;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    onBtRestored();
                                }
                            });
                        }
                    } else if (btSocket == null || !btSocket.isConnected()) {
                        btConnected = false;
                        closeBtSocket();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                onBtLost();
                            }
                        });
                    }

                    // While a session is running but the controller is absent, count how
                    // long it has been missing and give up after BT_ABSENT_TIMEOUT_MS.
                    if (sessionActive && !btConnected) {
                        long now = System.currentTimeMillis();
                        if (btAbsentSince == 0) {
                            btAbsentSince = now;
                            Log.w(TAG, "HC-05 отсутствует. Автовыключение через "
                                    + (BT_ABSENT_TIMEOUT_MS / 60000) + " мин.");
                        } else if (now - btAbsentSince >= BT_ABSENT_TIMEOUT_MS) {
                            btMonitorRunning = false;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    onBtAbsentTimeout();
                                }
                            });
                            break;
                        }
                    } else {
                        btAbsentSince = 0;
                    }

                    try {
                        Thread.sleep(BT_CHECK_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }, "HC05-watchdog");
        btMonitorThread.start();
    }

    private void stopBtMonitor() {
        btMonitorRunning = false;
        if (btMonitorThread != null) {
            btMonitorThread.interrupt();
            btMonitorThread = null;
        }
    }

    /** Blocking attempt to (re)connect to HC-05. Always called off the UI thread. */
    @SuppressLint("MissingPermission")
    private boolean tryConnectBt() {
        synchronized (btLock) {
            if (btAdapter == null || !btAdapter.isEnabled()) {
                return false;
            }
            closeBtSocket();
            try {
                BluetoothDevice device = btAdapter.getRemoteDevice(address);
                btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
                btAdapter.cancelDiscovery();
                btSocket.connect();
                outStream = btSocket.getOutputStream();
                Log.d(TAG, "DIAG tryConnectBt: connected, outStream=" + (outStream != null));
                return true;
            } catch (Exception e) {
                Log.e(TAG, "...HC-05 connect failed: " + e.getMessage());
                closeBtSocket();
                return false;
            }
        }
    }

    /** Silent close of the socket/stream. Safe to call repeatedly. */
    private void closeBtSocket() {
        synchronized (btLock) {
            try {
                if (outStream != null) {
                    outStream.close();
                }
            } catch (IOException ignored) {
            }
            outStream = null;
            try {
                if (btSocket != null) {
                    btSocket.close();
                }
            } catch (IOException ignored) {
            }
            btSocket = null;
        }
    }

    /** Reflects the HC-05 link state in the on-screen indicator. */
    private void updateBtStatus(boolean connected) {
        if (btStatusText == null || btStatusDot == null) {
            return;
        }
        int color = ContextCompat.getColor(this, connected ? R.color.status_ok : R.color.status_off);
        btStatusText.setText(connected ? R.string.bt_status_connected : R.string.bt_status_disconnected);
        btStatusText.setTextColor(color);
        btStatusDot.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    /** UI thread: the HC-05 link disappeared - stop capture/recognition/transfer. */
    private void onBtLost() {
        if (btLostNotified) {
            return;
        }
        btLostNotified = true;
        Log.e(TAG, "...HC-05 потерян. Захват и распознавание приостановлены...");
        updateBtStatus(false);
        pauseCapture();
        if (sessionActive) {
            Toast.makeText(getBaseContext(), "HC-05 потерян. Ожидание подключения...", Toast.LENGTH_SHORT).show();
        }
    }

    /** UI thread: the HC-05 link is back - resume the full pipeline. */
    private void onBtRestored() {
        btLostNotified = false;
        Log.d(TAG, "...HC-05 подключён. Продолжаю захват, распознавание и передачу...");
        updateBtStatus(true);
        if (sessionActive) {
            resumeCapture();
            Toast.makeText(getBaseContext(), "HC-05 подключён. Продолжаю работу.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * UI thread: a running session could not find HC-05 for {@link #BT_ABSENT_TIMEOUT_MS}.
     * Shut the whole app down.
     */
    private void onBtAbsentTimeout() {
        Log.e(TAG, "HC-05 не найден " + (BT_ABSENT_TIMEOUT_MS / 60000) + " мин. Завершаю приложение.");
        Toast.makeText(getBaseContext(),
                "HC-05 не найден. Приложение выключается.", Toast.LENGTH_LONG).show();
        shutdownApp();
    }

    /** Full shutdown: stop capture, service, socket and finish the activity. */
    private void shutdownApp() {
        sessionActive = false;
        sSessionBusy = false;
        btConnected = false;
        stopBtMonitor();
        pauseCapture();
        stopService(serviceIntent);
        destroyMediaProjection();
        closeBtSocket();
        if (tessBaseApi != null) {
            tessBaseApi.end();
            tessBaseApi = null;
        }
        finishAndRemoveTask();
        // Terminate the process as well so nothing keeps running in the background.
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    /** Called from a failed write - the controller is gone. */
    private void handleBtFailure(Exception e) {
        Log.e(TAG, "...Bluetooth write failed: " + e.getMessage());
        // Do not close the socket here (it would block); the watchdog reconnects.
        btConnected = false;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onBtLost();
            }
        });
    }

    /** Releases the VirtualDisplay (stops frames) but keeps the MediaProjection alive. */
    private void pauseCapture() {
        if (mVirtualDisplayImg != null) {
            mVirtualDisplayImg.release();
            mVirtualDisplayImg = null;
            Log.d(TAG, "DIAG pauseCapture: VirtualDisplay released");
        }
    }

    /** Re-creates the VirtualDisplay from the still-valid MediaProjection. */
    private void resumeCapture() {
        if (mMediaProjection == null || mVirtualDisplayImg != null) {
            Log.d(TAG, "DIAG resumeCapture skipped: projection=" + (mMediaProjection != null)
                    + " display=" + (mVirtualDisplayImg != null));
            return;
        }
        mVirtualDisplayImg = mMediaProjection.createVirtualDisplay("MainActivity",
                DISPLAY_WIDTH, DISPLAY_HEIGHT, DISPLAY_DPI,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null /*Callbacks*/, null /*Handler*/);
        Log.d(TAG, "DIAG resumeCapture: VirtualDisplay created = " + (mVirtualDisplayImg != null));
    }

    /** Full stop of the capture session (user pressed STOP or projection ended). */
    private void stopScreenSharing() {
        sessionActive = false;
        sessionStarting = false;
        sSessionBusy = false;
        pauseCapture();
        stopService(serviceIntent);
        destroyMediaProjection();
        if (tessBaseApi != null) {
            tessBaseApi.end();
            tessBaseApi = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (nfcAdapter != null) {
            try {
                nfcAdapter.disableReaderMode(this);
            } catch (Exception ignored) {
            }
        }
        sessionActive = false;
        sSessionBusy = false;
        btConnected = false;
        stopBtMonitor();
        stopService(serviceIntent);
        destroyMediaProjection();
        closeBtSocket();
        if (tessBaseApi != null) {
            tessBaseApi.end();
            tessBaseApi = null;
        }
    }

    private void destroyMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection.unregisterCallback(mMediaProjectionCallback);
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        Log.i(TAG, "MediaProjection Stopped");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = grantResults.length > 0;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                mToggleButton.setChecked(false);
                Snackbar.make(findViewById(android.R.id.content), R.string.label_permissions,
                        Snackbar.LENGTH_INDEFINITE).setAction("ENABLE",
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Intent intent = new Intent();
                                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.addCategory(Intent.CATEGORY_DEFAULT);
                                intent.setData(Uri.parse("package:" + getPackageName()));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                                intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                                startActivity(intent);
                            }
                        }).show();
            }
        }
    }
}
