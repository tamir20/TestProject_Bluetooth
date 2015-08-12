package project;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Point;
import android.hardware.Camera;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;

import org.json.JSONException;
import org.webrtc.MediaStream;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;

import fr.pchab.androidrtc.R;
import Libraries.WebRtcClient;
import Libraries.PeerConnectionParameters;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import android.annotation.SuppressLint;

import javax.net.ssl.HttpsURLConnection;

import java.util.Timer;
import java.util.TimerTask;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Handler.Callback;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class RtcActivity extends Activity implements WebRtcClient.RtcListener {
    private final String FIREBASE_URL = "https://crackling-heat-6629.firebaseio.com/";
    private final static int VIDEO_CALL_SENT = 666;
    private static final String VIDEO_CODEC_VP9 = "VP9";
    private static final String AUDIO_CODEC_OPUS = "opus";
    // Local preview screen position before call is connected.
    private static final int LOCAL_X_CONNECTING = 0;
    private static final int LOCAL_Y_CONNECTING = 0;
    private static final int LOCAL_WIDTH_CONNECTING = 100;
    private static final int LOCAL_HEIGHT_CONNECTING = 100;
    // Local preview screen position after call is connected.
    private static final int LOCAL_X_CONNECTED = 72;
    private static final int LOCAL_Y_CONNECTED = 72;
    private static final int LOCAL_WIDTH_CONNECTED = 25;
    private static final int LOCAL_HEIGHT_CONNECTED = 25;
    // Remote video screen position
    private static final int REMOTE_X = 0;
    private static final int REMOTE_Y = 0;
    private static final int REMOTE_WIDTH = 100;
    private static final int REMOTE_HEIGHT = 100;
    private VideoRendererGui.ScalingType scalingType = VideoRendererGui.ScalingType.SCALE_ASPECT_FILL;
    private GLSurfaceView vsv;
    private VideoRenderer.Callbacks localRender;
    private VideoRenderer.Callbacks remoteRender;
    private WebRtcClient client;
    private String mSocketAddress;
    private String host;
    private String callerId;
    private Firebase mFirebaseRef;
    private String DATA;
    private int lock = 0;
    private String robot_id = "123456"; // instead of just ""
    private String callID = "";
    private WifiManager wifiManager;
    private MyTimerTask myTimerTask;

    private static final int REQUEST_ENABLE_BT = 105;
    public static final int MESSAGE_READ = 106;
    public static final String uuidStr= "00001101-0000-1000-8000-00805F9B34FB";
    private static ArrayAdapter<String> devicesListAdapter;
    private ArrayList<BluetoothDevice> devicesList;
    private TextView terminal;
    private EditText sendMessage;
    private BluetoothDevice currentDevice;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothConnection bConnection;
    public static UUID uuid = UUID.fromString(uuidStr);
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg)
        {  // readed message


            switch(msg.what)
            {
                case MESSAGE_READ:
                    // readed data from bluetooth
                    //toastMessage(R.string.SocketRead, Toast.LENGTH_SHORT);

                    byte[] buffer = new byte[msg.arg1];
                    buffer = (byte[])msg.obj;

                    CharSequence message = new String(buffer);
                    //smprintMessageOnTerminal(message);
                    break;

                default :
                    break;
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //had to do that before bluetooth init because it uses the mFirebaseRef
        //initialize firebase library
        Firebase.setAndroidContext(this);
        //initiate firebase reference
        mFirebaseRef = new Firebase(FIREBASE_URL);
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        //------------------------------BLUETOOTH INIT----------------------------------------
        currentDevice = null;
        bConnection = null;


        devicesList = new ArrayList<BluetoothDevice>();
        //prepare adapter for the spinner
        devicesListAdapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_spinner_item);
        //devicesListAdapter.setDropDownViewResource(R.layout.spinner_layout);
        devicesListAdapter.add(getString(R.string.defaultSpinnerMessage));
        // init spinner
		/*Spinner spinner = (Spinner) findViewById(R.id.bluetoothDevice);
		spinner.setOnItemSelectedListener(this);
		spinner.setAdapter(devicesListAdapter);*/

        // Register the BroadcastReceiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter);

		/*Button sendButton = (Button) findViewById(R.id.sendButton);
		sendButton.setOnClickListener(this);*/
        // bluetooth routines, init and find devices
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (null == mBluetoothAdapter) {
            //Bluetooth isn't supported
            //toastMessage(R.string.UnsupportedBluetooth, Toast.LENGTH_SHORT);

            // wait to show message, and exit
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            finish();
        } else {

            if (!mBluetoothAdapter.isEnabled()) {
                //switching on bluetooth
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else {
                // if bluetooth switched on search devices
                if(!getPairedDevices(devicesListAdapter)) {

                    if(!mBluetoothAdapter.startDiscovery()) {
                        //toastMessage(R.string.searchFailed, Toast.LENGTH_SHORT);
                    } else {
                        devicesListAdapter.clear();
                    }
                }
            }
        }
        try {
            currentDevice = devicesList.get(0);
            String name = currentDevice.getName();
            bConnection = new BluetoothConnection(currentDevice,name,uuid,mHandler,this, mFirebaseRef,robot_id);
            bConnection.start();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            String mes3 = "Exception";
            printMessageOnTerminal(mes3 + e.getMessage());
        }
        //-----------------------------END BLUETOOTH INIT-------------------------------------
        wifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        //this will be changed to the right one later on (automatically)
        host = "temp";
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(
                LayoutParams.FLAG_FULLSCREEN
                        | LayoutParams.FLAG_KEEP_SCREEN_ON
                        | LayoutParams.FLAG_DISMISS_KEYGUARD
                        | LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | LayoutParams.FLAG_TURN_SCREEN_ON);
        setContentView(R.layout.main);
        //this listener event is for determining 'host' value
        mFirebaseRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if(snapshot.child("host_ip").exists()) {
                    host = snapshot.child("host_ip").getValue().toString();
                    mFirebaseRef.child("users/try").setValue(host);
                    lock = 2;
                }
                /*for (DataSnapshot child : snapshot.getChildren()) {
                    host = snapshot.child("host_ip").getValue().toString();
                    mFirebaseRef.child("users/try").setValue(host);
                    lock = 2;
                }*/
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });
        mFirebaseRef.child("users/try").setValue("Temp1");
        mFirebaseRef.child("users/try").setValue("Temp2");
        mFirebaseRef.child("users/robot_"+robot_id+"/robot_response").setValue("CONNECTION_OK");
        //set the listener to handle "ISSUE_CONNECTION" and more
        mFirebaseRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                // test to get server_request
                /*Button b = (Button)findViewById(R.id.button);
                b.setText(snapshot.child("users/robot_" + robot_id + "/server_request").getValue().toString());*/
                if(snapshot.child("users/robot_"+robot_id+"/server_request").exists()) {
                    if (snapshot.child("users/robot_" + robot_id + "/server_request").getValue().toString().equals("ISSUE_CONNECTION")) {
                        setFirebase();
                        mFirebaseRef.child("users/robot_" + robot_id + "/server_request").setValue("");
                        mFirebaseRef.child("users/robot_" + robot_id + "/robot_response").setValue("CONNECTION_OK");
                    }
                    if (snapshot.child("users/robot_" + robot_id + "/server_request").getValue().toString().equals("GO_FORWARD")) {
                        if (null != currentDevice && null != bConnection) {
                            byte[] msg = charSequenceToByteArray("forward");
                            //byte[] msg = charSequenceToByteArray("8");
                            bConnection.write(msg);
                        }
                        mFirebaseRef.child("users/robot_" + robot_id + "/server_request").setValue("");
                        mFirebaseRef.child("users/robot_" + robot_id + "/robot_response").setValue("MOVEMENT_OK");
                    }
                    if (snapshot.child("users/robot_" + robot_id + "/server_request").getValue().toString().equals("GO_LEFT")) {
                        if (null != currentDevice && null != bConnection) {
                            byte[] msg = charSequenceToByteArray("left");
                            //byte[] msg = charSequenceToByteArray("4");
                            bConnection.write(msg);
                        }
                        mFirebaseRef.child("users/robot_" + robot_id + "/server_request").setValue("");
                        mFirebaseRef.child("users/robot_" + robot_id + "/robot_response").setValue("MOVEMENT_OK");
                    }
                    if (snapshot.child("users/robot_" + robot_id + "/server_request").getValue().toString().equals("GO_RIGHT")) {
                        if (null != currentDevice && null != bConnection) {
                            byte[] msg = charSequenceToByteArray("right");
                            //byte[] msg = charSequenceToByteArray("6");
                            bConnection.write(msg);
                        }
                        mFirebaseRef.child("users/robot_" + robot_id + "/server_request").setValue("");
                        mFirebaseRef.child("users/robot_" + robot_id + "/robot_response").setValue("MOVEMENT_OK");
                    }
                    if (snapshot.child("users/robot_" + robot_id + "/server_request").getValue().toString().equals("GO_BACK")) {
                        if (null != currentDevice && null != bConnection) {
                            byte[] msg = charSequenceToByteArray("back");
                            //byte[] msg = charSequenceToByteArray("2");
                            bConnection.write(msg);
                        }
                        mFirebaseRef.child("users/robot_" + robot_id + "/server_request").setValue("");
                        mFirebaseRef.child("users/robot_" + robot_id + "/robot_response").setValue("MOVEMENT_OK");
                    }
                    if (snapshot.child("users/robot_" + robot_id + "/server_request").getValue().toString().equals("GO_STOP")) {
                        if (null != currentDevice && null != bConnection) {
                            byte[] msg = charSequenceToByteArray("stop");
                            //byte[] msg = charSequenceToByteArray("5");
                            bConnection.write(msg);
                        }
                        mFirebaseRef.child("users/robot_" + robot_id + "/server_request").setValue("");
                        mFirebaseRef.child("users/robot_" + robot_id + "/robot_response").setValue("MOVEMENT_OK");
                    }
                    if (snapshot.child("users/robot_" + robot_id + "/server_request").getValue().toString().equals("RETURN_TO_BASE")) {
                        if (null != currentDevice && null != bConnection) {
                            byte[] msg = charSequenceToByteArray("return to base");
                            //byte[] msg = charSequenceToByteArray("5");
                            bConnection.write(msg);
                        }
                        mFirebaseRef.child("users/robot_" + robot_id + "/server_request").setValue("");
                        mFirebaseRef.child("users/robot_" + robot_id + "/robot_response").setValue("MOVEMENT_OK");
                    }
                    if (snapshot.child("users/robot_" + robot_id + "/server_request").getValue().toString().equals("SHUT_DOWN")) {
                        Button t = (Button) findViewById(R.id.button3);
                        t.setVisibility(View.VISIBLE);
                        mFirebaseRef.child("users/robot_" + robot_id + "/server_request").setValue("");
                        mFirebaseRef.child("users/robot_" + robot_id + "/robot_response").setValue("SHUT_DOWN_OK");
                    }
                    if (snapshot.child("users/robot_" + robot_id + "/server_request").getValue().toString().equals("GET_DATA")) {
                        if(snapshot.child("users/robot_"+robot_id+"/number1").exists()&&snapshot.child("users/robot_"+robot_id+"/number2").exists()&&snapshot.child("users/robot_"+robot_id+"/number3").exists()&&snapshot.child("users/robot_"+robot_id+"/char").exists()) {
                            String n1 = snapshot.child("users/robot_"+robot_id+"/number1").getValue().toString();
                            String n2 = snapshot.child("users/robot_"+robot_id+"/number2").getValue().toString();
                            String n3 = snapshot.child("users/robot_"+robot_id+"/number3").getValue().toString();
                            String character = snapshot.child("users/robot_"+robot_id+"/char").getValue().toString();
                            int num1 = ConStringToInt(n1);
                            int num2 = ConStringToInt(n2);
                            int num3 = ConStringToInt(n3);
                            char c = character.charAt(0);
                            if (null != currentDevice && null != bConnection) {
                                byte[] msg = charSequenceToByteArray("custom cmd: "+character+", "+n1+", "+n2+", "+n3);
                                bConnection.write(msg);
                            }
                        }
                        mFirebaseRef.child("users/robot_" + robot_id + "/server_request").setValue("");
                        mFirebaseRef.child("users/robot_" + robot_id + "/robot_response").setValue("GET_DATA_OK");
                    }
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });
        vsv = (GLSurfaceView) findViewById(R.id.glview_call);
        vsv.setPreserveEGLContextOnPause(true);
        vsv.setKeepScreenOn(true);
        VideoRendererGui.setView(vsv, new Runnable() {
            @Override
            public void run() {
                init();
            }
        });

        // local and remote render
        remoteRender = VideoRendererGui.create(
                REMOTE_X, REMOTE_Y,
                REMOTE_WIDTH, REMOTE_HEIGHT, scalingType, false);
        localRender = VideoRendererGui.create(
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING, scalingType, true);

        final Intent intent = getIntent();
        final String action = intent.getAction();

        if (Intent.ACTION_VIEW.equals(action)) {
            final List<String> segments = intent.getData().getPathSegments();
            callerId = segments.get(0);
        }
        //this is test for ConStringToInt function
        /*Button b = (Button)findViewById(R.id.button);
        String a = "NO";
        int num = ConStringToInt("2345");
        for(int i = 0; i < num; i++) {
            if (i == 2344) {
                a = "YES";
            }
            if (i < 2344) {
                a = "NO";
            }
            if (i > 2344) {
                a = "NO";
            }
        }
        b.setText(a);*/
        Timer timer = new Timer();
        myTimerTask = new MyTimerTask(mFirebaseRef,wifiManager,robot_id);
        timer.schedule(myTimerTask, 0, 2000);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            CharSequence message = "Finded device...";
            printMessageOnTerminal(message);

            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = (BluetoothDevice)intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                //ParcelUuid [] pui = (ParcelUuid [])intent.getParcelableExtra(BluetoothDevice.EXTRA_UUID)
                // Add the name and address to an array adapter to show in a ListView
                String name = device.getName();
                if(!name.isEmpty() && name.length() < 8)
                {
                    devicesListAdapter.add( name );
                    devicesList.add(device);
                }
                else
                {
                    printMessageOnTerminal("device name is empty");
                }

            }
        }
    };

    public void printMessageOnTerminal(CharSequence message) {
		/*terminal.append("\n#>");
		terminal.append(message);*/
    }

    public void smprintMessageOnTerminal(CharSequence message) {
		/*terminal.append("\n#>");
		for(int i=0; i< message.length(); i++)
		{
			int m = (int)message.charAt(i);
			terminal.append(String.valueOf(m));
			terminal.append(" ");
		}*/
    }

    public byte[] charSequenceToByteArray(CharSequence message)
    {

        int length = message.length();
        if(length == 0)
            return null;

        byte[] retval = new byte[length+1];

        for(int i=0; i<length; i++)
        {
            retval[i] = (byte) message.charAt(i);
        }
        //NEW CHANGE FOR ROBOT INTEGRATION!!
        retval[length] = (byte)'\n';
        //END OF NEW CHANGE
        return retval;
    }


    private boolean getPairedDevices(ArrayAdapter<String> devices) {

        boolean retval = false;
        CharSequence message = "Searching paired diveces";
        CharSequence message2 = "Search end";

        printMessageOnTerminal(message);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            retval = true;

            devices.clear();
            devices.add(getString(R.string.findedPairedDevices));

            for(BluetoothDevice device : pairedDevices) {
                devices.add(device.getName());
                devicesList.add(device);
            }
        } else {
            devices.clear();
            devices.insert(getString(R.string.noPairedDevices), 0);
        }

        printMessageOnTerminal(message2);

        return retval;
    }


    private void init() {
        Point displaySize = new Point();
        getWindowManager().getDefaultDisplay().getSize(displaySize);
        PeerConnectionParameters params = new PeerConnectionParameters(
                // 360 instead of displaySize.x and 480 instead of displaySize.y
                true, false, 360, 480, 30, 1, VIDEO_CODEC_VP9, true, 1, AUDIO_CODEC_OPUS, true);
        while(lock==0){}
        mSocketAddress = "http://" + host;
        client = new WebRtcClient(this, mSocketAddress, params, VideoRendererGui.getEGLContext());
    }

    @Override
    public void onPause() {
        super.onPause();
        vsv.onPause();
        if(client != null) {
            client.onPause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        vsv.onResume();
        if(client != null) {
            client.onResume();
        }
    }

    @Override
    public void onDestroy() {
        if(client != null) {
            client.onDestroy();
        }
        super.onDestroy();
    }

    @Override
    public void onCallReady(String callId) {
        if (callerId != null) {
            try {
                answer(callerId);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            call(callId);
        }
    }

    public void answer(String callerId) throws JSONException {
        client.sendMessage(callerId, "init", null);
        startCam();
        Button t = (Button) findViewById(R.id.button2);
        t.setVisibility(View.VISIBLE);
    }

    public void call(String callId) {

        //setFirebase(callId); was originally here but we want the robot's id to be constant, so
        // we determine its value to be the first call id
        //here we want the robot is to be 123456 only, but i want to save the option to make it
        //more general
        if(robot_id.equals("")){
            robot_id = "123456";// and not "callId"
        }
        callID = callId;
        setFirebase();
        startCam();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == VIDEO_CALL_SENT) {
            startCam();
        }
        switch(requestCode) {
            // bluetooth
            case REQUEST_ENABLE_BT:
                if(RESULT_OK == resultCode) {
                    // bluetooth is switched on now
                    if(!getPairedDevices(devicesListAdapter)) {
                        if(!mBluetoothAdapter.startDiscovery()) {
                            //toastMessage(R.string.searchFailed, Toast.LENGTH_SHORT);
                        }
                    }
                } else {
                    // bt isn't switched on
                    //toastMessage(R.string.notSwithcedOnBluetooth, Toast.LENGTH_SHORT);

                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    finish();
                }
                break;

            default:
                break;
        }
    }

    public void startCam() {
        // Camera settings
        client.start("Tele-Care Robot");
    }

    @Override
    public void onStatusChanged(final String newStatus) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), newStatus, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onLocalStream(MediaStream localStream) {
        localStream.videoTracks.get(0).addRenderer(new VideoRenderer(localRender));
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING,
                scalingType);
    }

    @Override
    public void onAddRemoteStream(MediaStream remoteStream, int endPoint) {
        remoteStream.videoTracks.get(0).addRenderer(new VideoRenderer(remoteRender));
        VideoRendererGui.update(remoteRender,
                REMOTE_X, REMOTE_Y,
                REMOTE_WIDTH, REMOTE_HEIGHT, scalingType);
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTED, LOCAL_Y_CONNECTED,
                LOCAL_WIDTH_CONNECTED, LOCAL_HEIGHT_CONNECTED,
                scalingType);
    }

    @Override
    public void onRemoveRemoteStream(int endPoint) {
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING,
                scalingType);
    }

    public void setFirebase() {
        //this line was commeted because first we have to check for "ISSUE_CONNECTION" value before
        //we can answer "CONNECTION_OK"
        //mFirebaseRef.child("users/robot_"+id+"/robot_response").setValue("CONNECTION_OK");

        //the robot_id is not changing to ensure only 1 robot id until the end of the application.
        //callID is changing every time a new call is created
        mFirebaseRef.child("users/robot_"+robot_id+"/rtsp_stream_url").setValue(mSocketAddress + "/" +callID);
    }

    public void ChangeID(View view) {
        String ID = "";
        ID = ((EditText)findViewById(R.id.editText)).getText().toString();
        if (!ID.equals("")) {
            robot_id = ID;
            myTimerTask.changeID(ID);
            mFirebaseRef.child("users/robot_" + robot_id + "/bluetooth").setValue("");
            mFirebaseRef.child("users/robot_" + robot_id + "/char").setValue("");
            mFirebaseRef.child("users/robot_" + robot_id + "/hosp_ip").setValue("");
            mFirebaseRef.child("users/robot_" + robot_id + "/number1").setValue("");
            mFirebaseRef.child("users/robot_" + robot_id + "/number2").setValue("");
            mFirebaseRef.child("users/robot_" + robot_id + "/number3").setValue("");
            mFirebaseRef.child("users/robot_" + robot_id + "/robot_response").setValue("");
            mFirebaseRef.child("users/robot_" + robot_id + "/rtsp_stream_url").setValue("");
            mFirebaseRef.child("users/robot_" + robot_id + "/server_request").setValue("");
            mFirebaseRef.child("users/robot_" + robot_id + "/signal").setValue("");
        }
    }

    public void Restart_Connection(View view) {
        Button t = (Button) findViewById(R.id.button3);
        t.setVisibility(View.VISIBLE);
    }

    public void CloseApp(View view) {
        System.exit(0);
    }

    public int ConStringToInt(String st){
        int num = 0;
        int n = st.length();
        for (int i = 0; i < n; i++){
            int temp = (int)st.charAt(i);
            temp-=48;
            int temp2 = (int)Math.pow(10,n-i-1);
            num+=temp*temp2;
        }
        return num;
    }
}

//***************************************** BLUETOOTH CLASS***********************************
class BluetoothConnection extends Thread {
    //private final BluetoothServerSocket mmServerSocket;
    private final BluetoothSocket mmServerSocket;
    private final Handler mHandler;
    private InputStream inStream;
    private OutputStream outStream;
    private RtcActivity act;
    private Firebase mFirebaseRef;
    private String robot_id;

    //public BluetoothConnection(BluetoothAdapter adapter, String Name, UUID uuid, Handler mHand, MainActivity ac) {
    public BluetoothConnection(BluetoothDevice adapter, String Name, UUID uuid, Handler mHand, RtcActivity ac,Firebase FIREBASE, String robot) {
        // Use a temporary object that is later assigned to mmServerSocket,
        // because mmServerSocket is final
        BluetoothSocket tmp = null;
        inStream = null;
        outStream = null;
        mHandler = mHand;
        act = ac;
        robot_id = robot;
        mFirebaseRef = FIREBASE;
        try {
            // MY_UUID is the app's UUID string, also used by the client code
            //tmp = adapter.listenUsingRfcommWithServiceRecord(Name, uuid);
            tmp = adapter.createRfcommSocketToServiceRecord(uuid);
        } catch (IOException e) {
            CharSequence message2 = "error socket creation";
            //act.printMessageOnTerminal(message2);
        }
        mmServerSocket = tmp;
    }
    public void run() {
        BluetoothSocket socket = null;

        CharSequence message = "Start socket listen";
        //act.printMessageOnTerminal(message);
        byte[] buffer = new byte[100];  // buffer store for the stream
        int bytes;                       // bytes returned from read()
        // Keep listening until exception occurs or a socket is returned
        while (true) {
            try {
                //socket = mmServerSocket.accept();
                mmServerSocket.connect();
            } catch (IOException e) {
                break;
            }
            // If a connection was accepted
            if (mmServerSocket != null) {
                // Do work to manage the connection (in a separate thread)
                //manageConnectedSocket(socket);
                // but for now do this work in this thread

                //CharSequence message5 = "Accepted";
                //act.printMessageOnTerminal(message5);

                try {
                    inStream = mmServerSocket.getInputStream();
                    outStream = mmServerSocket.getOutputStream();
                } catch (IOException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }

                if(inStream != null)
                {
                    while(true) {
                        try {
                            bytes = inStream.read(buffer);

                            //mHandler.obtainMessage(MainActivity.MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                            mFirebaseRef.child("users/robot_" + robot_id + "/bluetooth").setValue(bytes);
                        } catch (IOException e) {
                            break;
                        }
                    }


                } else {
                    //toastMessage(R.string.SocketError, Toast.LENGTH_SHORT);
                }

                try {
                    mmServerSocket.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                break;
            }
//			CharSequence message2 = "accept exception";
//			act.printMessageOnTerminal(message2);
        }
    }

    public void write(byte[] bytes) {

        if(outStream != null) {
            try {
                outStream.write(bytes);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                CharSequence message2 = "write exception";
                //act.printMessageOnTerminal(message2);
            }
        }

    }

    /** Will cancel the listening socket, and cause the thread to finish */
    public void cancel() {
        try {
            mmServerSocket.close();
        } catch (IOException e) { }
    }
}

class MyTimerTask extends TimerTask {

    private Firebase mFirebaseRef;
    private WifiManager wifiManager;
    private String robot_id;
    public MyTimerTask(Firebase FIREBASE, WifiManager WIFI, String ID) {
        mFirebaseRef=FIREBASE;
        wifiManager = WIFI;
        robot_id = ID;
    }

    public void changeID(String ID){
        robot_id = ID;
    }

    @Override
    public void run() {
        int numberOfLevels=5;
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int level=WifiManager.calculateSignalLevel(wifiInfo.getRssi(), numberOfLevels);
        String st = "";
        if(level == 0){
            st = "0";
        }
        if(level == 1){
            st = "1";
        }
        if(level == 2){
            st = "2";
        }
        if(level == 3){
            st = "3";
        }
        if(level == 4){
            st = "4";
        }
        if(level == 5){
            st = "5";
        }
        mFirebaseRef.child("users/robot_" + robot_id + "/signal").setValue(st);

    }
}

