package com.voltmotors.e1_4;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;

import android.util.Log;

import com.example.google.nodejsmanager.nodejsmanager.ConnectionManager;
import com.example.google.nodejsmanager.nodejsmanager.SocketManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.socket.client.IO;
import io.socket.emitter.Emitter;
import io.socket.engineio.client.transports.WebSocket;

import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;

import static java.util.concurrent.Executors.newScheduledThreadPool;


public class MainActivity extends AppCompatActivity implements ConnectionManager.EventCallbackListener {

    DemoMCP2200 demo = null;

    PendingIntent pendingIntent = null;

    String cInCmd = "";

    private final String TAG = getClass().getSimpleName();
    private Context context;
    private String cVersion = "1.1";
    private boolean lPower, lBaliza, lLucesBajas, lLucesPosicion, lLucesAntiDelan, lLucesAntiTras, lAperturaPorton, lAperturaPuertas, lespejos, lespejosdrech, lespejosizq, lfrenomano;
    private ImageView espejosinactivos, fondocursor, lateralbaul, testfrenomano, cursores, marchas;
    private int nskin;
    private ImageButton oEspejosizq;
    private ConstraintLayout Iskin;

    // other
    private SocketManager socketManager;
    private int connectionIntents = 0;
    private ScheduledExecutorService schedulePingViot;
    //-------------------------------------------------------------------------------------------------------------------
    private String connectionId;
    private String enterpriseId;
    //-------------------------------------------------------------------------------------------------------------------

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    //----------------------------------------------------------------------------------------------------------------------------

    private Emitter.Listener onStreamReady = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            if (socketManager.getSocket() != null) {
                //ToDo Cada vez que hay stream desde VIoT se ejecutan los "on" para tener disponibles los ON que emiten cada status del auto desde VIoT.
                Log.d(TAG, "VIoT - Stream Ready on VIoT");
                socketManager.getSocket().on("onengine", onStartCar);
                socketManager.getSocket().on("ondoors", onLockDoors);
                socketManager.getSocket().on("onlights", onLights);
                socketManager.getSocket().emit("webee.stream.subscribe", "123456:" + enterpriseId + ":" + connectionId);
            }
        }
    };;

    private Emitter.Listener onStartCar = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            if (socketManager.getSocket() != null) {
                //ToDo Cada vez que se reciba un encendido de motor se ejectua esta seccion de codigo
                JSONObject engine = new JSONObject();
                for (Object value: args){
                    engine = (JSONObject) value;
                }
                Log.v(TAG, "VIoT - onStartEngine event received ..." + engine);
            }
        }
    };

    private Emitter.Listener onLockDoors = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            if (socketManager.getSocket() != null) {
                //ToDo Cada vez que se reciba un cambio en el lock de las puertas se ejectua esta seccion de codigo
                JSONObject doors = new JSONObject();
                for (Object value: args){
                    doors = (JSONObject) value;
                }
                Log.v(TAG, "VIoT - onLockDoors event received ..." + doors);
            }
        }
    };

    private Emitter.Listener onLights = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            if (socketManager.getSocket() != null) {
                //ToDo Cada vez que se reciba un cambio en luces se ejectua esta seccion de codigo
                JSONObject lights = new JSONObject();
                for (Object value: args){
                    lights = (JSONObject) value;
                }
                Log.v(TAG, "VIoT - onLights event received ..." + lights);
            }
        }
    };

    private Emitter.Listener onAuthenticated = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            Log.d(TAG, "VIoT - onAuthenticated");
            socketManager.getSocket().emit("lb-ping");
            if (schedulePingViot == null) {
                schedulePingViot = newScheduledThreadPool(5);
            }
            schedulePingViot.scheduleAtFixedRate(new Runnable() {
                public void run() {
                    try {
                        if (socketManager.getSocket() != null
                                && socketManager.getSocket().connected()) {
                            socketManager.getSocket().emit("lb-ping");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, 0, 15, TimeUnit.SECONDS);
        }
    };

    private Emitter.Listener onAndroidPongVIOT = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            if (socketManager.getSocket() != null) {
                Log.d(TAG, "VIoT - pong received ...");
            }
        }
    };


    private void connectSocketViot() {
        if (connectionIntents > 3) {
            showErrorMessage();
            return;
        }
        connectionIntents++;
        socketManager = new SocketManager(this);
        IO.Options opts = new IO.Options();
        opts.transports = new String[]{WebSocket.NAME};
        //opts.forceNew = true;
        socketManager.createSocket(Constants.VIOT_BASE_URL, opts);

        socketManager.getSocket().on("authenticated", onAuthenticated);
        socketManager.getSocket().on("lb-pong", onAndroidPongVIOT);
        //----------------------------------------------------------------------------------------------------
        socketManager.getSocket().on("webee.stream.ready", onStreamReady);
        //----------------------------------------------------------------------------------------------------

        if (socketManager.getSocket().connected()) {
            socketManager.getSocket().disconnect();
        }
        socketManager.getSocket().connect();
    }

    private void showErrorMessage() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "Socket disconnected", Toast.LENGTH_SHORT).show();
            }
        });
    }

    JSONObject getCredentials() {
        try {
            String path = "/api/connections/generateToken?api_key=%s&api_secret=%s";
            String[] APIs = new String[]{Constants.API_KEY, Constants.API_SECRET};
            String generateTokenApi = Constants.VIOT_BASE_URL + path;
            URL url = new URL(String.format(generateTokenApi, APIs[0],
                    APIs[1]));
            HttpURLConnection connection =
                    (HttpURLConnection) url.openConnection();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));
            StringBuilder json = new StringBuilder(1024);
            String tmp;
            while ((tmp = reader.readLine()) != null)
                json.append(tmp).append("\n");
            reader.close();
            return new JSONObject(json.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void onEventCallbackReceived(String event, String socketIdentifier) {
        switch (event) {
            case ConnectionManager.EVENT_CONNECT: {
                Log.d(TAG, "VIoT - onConnectEvent");
                if (socketManager.getSocket() != null) {
                    JSONObject json = getCredentials();
                    Log.v(TAG, json.toString());
                    //---------------------------------------------------------------------------------------------------
                    try {
                        enterpriseId = json.getString("enterpriseId");
                        connectionId = json.getString("connectionId");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    Log.v(TAG, enterpriseId);
                    Log.v(TAG, connectionId);
                    //---------------------------------------------------------------------------------------------------


                    try {
                        if (json != null) {
                            JSONObject requestJSONObject = new JSONObject();
                            requestJSONObject.put("id", json.getString("id"));
                            requestJSONObject.put("connectionId", json.getString("connectionId"));
                            requestJSONObject.put("agent", "hub");
                            requestJSONObject.put("uuid", "VOLTCARDEMO");
                            socketManager.getSocket().emit("webee-auth-strategy", requestJSONObject);
                            Log.i(TAG, "json: " + requestJSONObject);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
            case ConnectionManager.EVENT_DISCONNECT: {
                Log.d(TAG, "VIoT - onDisconnectEvent");
                connectSocketViot();
                break;
            }
        }
    }

    public void onReadDataCarensor(JsonObject message) {
        //ToDo cada vez que se envia informacion de sensores a VIoT hay que invocar esta funcion
        Log.v(TAG, "message" + message);
        socketManager.getSocket().emit("webee-hub-logger", message);
    }

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////7

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        lLucesPosicion = false;
        lLucesBajas = false;
        lBaliza = false;
        lAperturaPorton = false;
        lLucesAntiDelan = false;
        lLucesAntiTras = false;
        lAperturaPuertas = false;
        lespejos = false;
        lespejosdrech = false;
        lespejosizq = false;
        lfrenomano = false;
        nskin = 0;

        context = this;

        //--------------------- declaracion de controles -----------------------

        espejosinactivos = (ImageView) findViewById(R.id.EspejosInactivos);
        fondocursor = (ImageView) findViewById(R.id.FondoControlEspejos);
        cursores = (ImageView) findViewById(R.id.cursores);
        //skin = (ImageView) findViewById(R.id.skin);
        lateralbaul = (ImageView) findViewById(R.id.Lateralbaul);
        testfrenomano = (ImageView) findViewById(R.id.TestParking);
        oEspejosizq = (ImageButton) findViewById(R.id.ImgBtnEspejosIzq);
        Iskin = (ConstraintLayout) findViewById(R.id.skin);
        marchas = (ImageView) findViewById(R.id.ImgMarchas);

        // ------------- envio datos de socket--------------------
        JsonObject message = new JsonObject();
        try {

            message.addProperty("startEngine", lPower);
            message.addProperty("batteryPerc", 20);
            message.addProperty("mileage", 20);

        } catch (JsonIOException e) {
            e.printStackTrace();
        }


        Button BtnDirecta = (Button) findViewById(R.id.BtnDirecta);
        BtnDirecta.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                marchas.setBackgroundResource(R.drawable.marcha_d_on);
            }
        });

        Button BtnReversa = (Button) findViewById(R.id.BtnNeutro);
        BtnReversa.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                marchas.setBackgroundResource(R.drawable.marcha_n_on);
            }
        });

        Button BtnNeutro = (Button) findViewById(R.id.BtnReversa);
        BtnNeutro.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                marchas.setBackgroundResource(R.drawable.marcha_r_on);
            }
        });

        // -------- toogle button de Baliza ------------------------
        ImageButton oBaliza = (ImageButton) findViewById(R.id.ImgBtnBaliza);
        oBaliza.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!lBaliza) {
                    v.setBackgroundResource(R.drawable.baliza_on);
                } else {
                    v.setBackgroundResource(R.drawable.baliza_off);
                }
                lBaliza = !lBaliza;

            }
        });

        // -------- toogle button de Power ------------------------
        ImageButton oPower = (ImageButton) findViewById(R.id.ImgBtnEncendido);
        oPower.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!lPower) {
                    v.setBackgroundResource(R.drawable.encendido_on);
                } else {
                    v.setBackgroundResource(R.drawable.encendido_off);
                }
                lPower = !lPower;
            }
        });

        // -------- toogle button de Luz Baja ------------------------
        ImageButton oLuZBaja = (ImageButton) findViewById(R.id.ImgBtnLuzBaja);
        oLuZBaja.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!lLucesBajas) {
                    v.setBackgroundResource(R.drawable.luz_baja_on);
                } else {
                    v.setBackgroundResource(R.drawable.luz_baja_off);
                }
                lLucesBajas = !lLucesBajas;
            }
        });

        // -------- toogle button de Luz Posicion ------------------------
        ImageButton oLuzPosicion = (ImageButton) findViewById(R.id.ImgBtnLuzPosicion);
        oLuzPosicion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!lLucesPosicion) {
                    v.setBackgroundResource(R.drawable.luz_de_posicion_on);
                } else {
                    v.setBackgroundResource(R.drawable.luz_de_posicion_off);
                }
                lLucesPosicion = !lLucesPosicion;

            }
        });

        // -------- toogle button de Neblina Delantera ------------------------
        ImageButton oNeblinaDelantera = (ImageButton) findViewById(R.id.ImgBtnNieblaDelantera);
        oNeblinaDelantera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!lLucesAntiDelan) {
                    v.setBackgroundResource(R.drawable.luz_delantera_niebla_on);
                } else {
                    v.setBackgroundResource(R.drawable.luz_delantera_niebla_off);
                }

                lLucesAntiDelan = !lLucesAntiDelan;
            }
        });

        // -------- toogle button de Neblina Trasera ------------------------
        ImageButton oNeblinaTrasera = (ImageButton) findViewById(R.id.ImgBtnNieblaTrasera);
        oNeblinaTrasera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!lLucesAntiTras) {
                    v.setBackgroundResource(R.drawable.luz_trasera_niebla_on);
                } else {
                    v.setBackgroundResource(R.drawable.luz_trasera_niebla_off);
                }
                lLucesAntiTras = !lLucesAntiTras;
            }
        });

        // -------- toogle button de Freno de mano ------------------------
        ImageButton oFrenoMano = (ImageButton) findViewById(R.id.ImgBtnFrenoMano);
        oFrenoMano.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!lfrenomano) {
                    v.setBackgroundResource(R.drawable.bron_freno_mano_on);
                    testfrenomano.setImageResource(R.drawable.testigo_freno_de_mano_on);
                } else {
                    v.setBackgroundResource(R.drawable.bron_freno_mano_off);
                    testfrenomano.setImageResource(R.drawable.testigo_freno_de_mano_off);
                }
                lfrenomano = !lfrenomano;
            }
        });

        // -------- toogle button de Apertura de puertas ------------------------
        ImageButton oPuertas = (ImageButton) findViewById(R.id.ImgBtnAperturaPuertas);
        oPuertas.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!lAperturaPuertas) {
                    v.setBackgroundResource(R.drawable.puertas_lock);
                } else {
                    v.setBackgroundResource(R.drawable.puertas_unlock);
                }
                lAperturaPuertas = !lAperturaPuertas;
            }
        });

        // -------- toogle button de Apertura de porton ------------------------
        ImageButton oPorton = (ImageButton) findViewById(R.id.ImgBtnAperturaPorton);
        oPorton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!lAperturaPorton) {
                    v.setBackgroundResource(R.drawable.porton_trasero_lock);
                    lateralbaul.setImageResource(R.drawable.vista_lateral_baul_abierto);
                } else {
                    v.setBackgroundResource(R.drawable.porton_trasero_unlock);
                    lateralbaul.setImageResource(R.drawable.vista_lateral_baul_cerrado);
                }
                lAperturaPorton = !lAperturaPorton;
            }
        });

        // -------- toogle button de Espejos Derecha ------------------------
        final ImageButton oEspejosderch = (ImageButton) findViewById(R.id.ImgBtnEspejosDrch);
        oEspejosderch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!lespejos) {
                    lespejosdrech = !lespejosdrech;
                    if (!lespejosdrech) {
                        v.setBackgroundResource(R.drawable.seleccion_espejo_derecho_on);
                        oEspejosizq.setBackgroundResource(R.drawable.seleccion_espejo_izquierdo_off);
                    }
                }

            }
        });

        // -------- toogle button de Espejos Izquierda ------------------------
        final ImageButton oEspejosizq = (ImageButton) findViewById(R.id.ImgBtnEspejosIzq);
        oEspejosizq.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!lespejos) {
                    lespejosizq = !lespejosizq;
                    if (lespejosizq) {
                        v.setBackgroundResource(R.drawable.seleccion_espejo_izquierdo_on);
                        oEspejosderch.setBackgroundResource(R.drawable.seleccion_espejo_derecho_off);
                    }
                }

            }
        });

        // -------- toogle button de Espejos ------------------------
        ImageButton oEspejos = (ImageButton) findViewById(R.id.ImgBtnEspejos);
        oEspejos.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lespejos = !lespejos;
                if (!lespejos) {
                    v.setBackgroundResource(R.drawable.espejos_unlock);
                    oEspejosderch.setVisibility(View.VISIBLE);
                    oEspejosizq.setVisibility(View.VISIBLE);
                    espejosinactivos.setVisibility(View.INVISIBLE);
                    fondocursor.setVisibility(View.VISIBLE);
                    cursores.setVisibility(View.VISIBLE);
                } else {
                    v.setBackgroundResource(R.drawable.espejos_lock);
                    oEspejosderch.setVisibility(View.INVISIBLE);
                    oEspejosizq.setVisibility(View.INVISIBLE);
                    espejosinactivos.setVisibility(View.VISIBLE);
                    fondocursor.setVisibility(View.INVISIBLE);
                    cursores.setVisibility(View.INVISIBLE);
                }


            }
        });


        // -------- toogle button de cursor arriba ------------------------
        Button oArriba = (Button) findViewById(R.id.cursor_arriba);
        oArriba.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent theMotion) {
                if (!lespejos) {
                    switch (theMotion.getAction()) {
                        case MotionEvent.ACTION_DOWN: {
                            cursores.setBackgroundResource(R.drawable.cursor_arriba);
                        }
                        break;
                        case MotionEvent.ACTION_UP: {
                            cursores.setBackgroundResource(R.drawable.cursor_neutro);
                        }
                        break;
                    }
                }

                return false;
            }
        });

        // -------- toogle button de cursor abajo ------------------------
        Button oAbajo = (Button) findViewById(R.id.cursor_abajo);
        oAbajo.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent theMotion) {
                if (!lespejos) {
                    switch (theMotion.getAction()) {
                        case MotionEvent.ACTION_DOWN: {
                            cursores.setBackgroundResource(R.drawable.cursor_abajo);
                        }
                        break;
                        case MotionEvent.ACTION_UP: {
                            cursores.setBackgroundResource(R.drawable.cursor_neutro);
                        }
                        break;
                    }
                }

                return false;
            }
        });

        // -------- toogle button de cursor derecha ------------------------
        Button oDerecha = (Button) findViewById(R.id.cursor_derch);
        oDerecha.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent theMotion) {
                if (!lespejos) {
                    switch (theMotion.getAction()) {
                        case MotionEvent.ACTION_DOWN: {
                            cursores.setBackgroundResource(R.drawable.cursor_derecha);
                        }
                        break;
                        case MotionEvent.ACTION_UP: {
                            cursores.setBackgroundResource(R.drawable.cursor_neutro);
                        }
                        break;
                    }
                }

                return false;
            }
        });

        // -------- toogle button de cursor abajo ------------------------
        Button oIzquierda = (Button) findViewById(R.id.cursor_izq);
        oIzquierda.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent theMotion) {
                if (!lespejos) {
                    switch (theMotion.getAction()) {
                        case MotionEvent.ACTION_DOWN: {
                            cursores.setBackgroundResource(R.drawable.cursor_izquierda);
                        }
                        break;
                        case MotionEvent.ACTION_UP: {
                            cursores.setBackgroundResource(R.drawable.cursor_neutro);
                        }
                        break;
                    }
                }

                return false;
            }
        });

        // -------- toogle button de Skin------------------------
        ImageButton oSkin = (ImageButton) findViewById(R.id.ImgBtnCiclosSkin);
        oSkin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nskin += 2;
                if (nskin > 4) nskin = 0;

                if (nskin == 2) {// prende boton 1 reversa
                    Iskin.setBackgroundResource(R.drawable.skin_carbono);
                } else {
                    if (nskin == 4) {// prende boton 2 directa
                        Iskin.setBackgroundResource(R.drawable.skin_doble_acero);
                    } else {
                        //Apaga boton neutro
                        Iskin.setBackgroundResource(R.drawable.skin_acero);
                    }

                }
            }
        });

        // -------- toogle button de webee ------------------------
        ImageButton owebee = (ImageButton) findViewById(R.id.ImgBtnWebbe);
        owebee.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent theMotion) {
                switch (theMotion.getAction()) {
                    case MotionEvent.ACTION_DOWN: {
                        v.setBackgroundResource(R.drawable.webee_on);
                    }
                    break;
                    case MotionEvent.ACTION_UP: {
                        v.setBackgroundResource(R.drawable.webee_off);
                    }
                    break;
                }
                return false;
            }
        });


        // -------- toogle button de Ventanilla Derecha Bajar ------------------------
        ImageButton oVenDerchBaj = (ImageButton) findViewById(R.id.ImgBtnVenDerchBaj);
        oVenDerchBaj.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent theMotion) {
                switch (theMotion.getAction()) {
                    case MotionEvent.ACTION_DOWN: {
                        v.setBackgroundResource(R.drawable.levanta_vidrios_derecho_bajar_on);
                    }
                    break;

                    case MotionEvent.ACTION_UP: {
                        v.setBackgroundResource(R.drawable.levanta_vidrios_derecho_bajar_off);
                    }
                    break;

                }
                return false;
            }
        });

        // -------- toogle button de Ventanilla Derecha Subir ------------------------
        ImageButton oVenDerchSub = (ImageButton) findViewById(R.id.ImgBtnVenDerchSub);
        oVenDerchSub.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent theMotion) {
                switch (theMotion.getAction()) {
                    case MotionEvent.ACTION_DOWN: {
                        v.setBackgroundResource(R.drawable.levanta_vidrios_derecho_subir_on);
                    }
                    break;
                    case MotionEvent.ACTION_UP: {
                        v.setBackgroundResource(R.drawable.levanta_vidrios_derecho_subir_off);
                    }
                    break;
                }
                return false;
            }
        });

        // -------- toogle button de Ventanilla Izquierda Bajar ------------------------
        ImageButton oVenIzqBaj = (ImageButton) findViewById(R.id.ImgBtnVenIzqBaj);
        oVenIzqBaj.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent theMotion) {
                switch (theMotion.getAction()) {
                    case MotionEvent.ACTION_DOWN: {
                        v.setBackgroundResource(R.drawable.levanta_vidrios_izquierdo_bajar_on);
                    }
                    break;


                    case MotionEvent.ACTION_UP: {
                        v.setBackgroundResource(R.drawable.levanta_vidrios_izquierdo_bajar_off);
                    }
                    break;
                }
                return false;
            }
        });

        // -------- toogle button de Ventanilla Izquierda Subir ------------------------
        ImageButton oVenIzqSub = (ImageButton) findViewById(R.id.ImgBtnVenIzqSub);
        oVenIzqSub.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent theMotion) {
                switch (theMotion.getAction()) {
                    case MotionEvent.ACTION_DOWN: {
                        v.setBackgroundResource(R.drawable.levanta_vidrios_izquierdo_subir_on);
                    }
                    break;
                    case MotionEvent.ACTION_UP: {
                        v.setBackgroundResource(R.drawable.levanta_vidrios_izquierdo_subir_off);
                    }
                    break;
                }
                return false;
            }
        });

        connectSocketViot();

    } // final de onCreate

    @Override
    public void onResume() {
        super.onResume();

        ConnectionManager.subscribeToListener(this);
        /* Check to see if it was a USB device attach that caused the app to
         * start or if the user opened the program manually.
         */
        Intent intent = getIntent();
        String action = intent.getAction();

        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            /* This application is starting as a result of a device being attached.  Get
             * the device information that caused the app opening from the intent, and
             * load the demo that corresponds to that device. */
            UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            load(device);
        } else {
            /* This application is starting up by a user opening the app manually.  We
             * need to look through to see if there are any devices that are already
             * attached.
             */
            UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
            HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
            Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

            while (deviceIterator.hasNext()) {
                /* For each device that we found attached, see if we are able to load
                 * a demo for that device.
                 */
                if (load(deviceIterator.next())) {
                    break;
                }
            }
        }

        //Create a new filter to detect USB device events
        IntentFilter filter = new IntentFilter();

        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);

        registerReceiver(receiver, filter);

        pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(getPackageName() + ".USB_PERMISSION"), 0);
    }

    @Override
    protected void onDestroy() {

        //ToDo Es MUY IMPORTANTE desuscribirse del stream una vez que la Activity/Fragment o clase contenedora se destruye
        socketManager.getSocket().emit("webee.stream.unsubscribe", "123456:" + enterpriseId + ":" + connectionId);

    }

    @Override
    public void onPause() {
        /* If there is a demo running, close it */
        if (demo != null) {
            demo.close();
        }
        demo = null;

        /* unregister any receivers that we have */
        unregisterReceiver(receiver);

        super.onPause();

        ConnectionManager.unSubscribeToListener();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // clear screen on flag
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (socketManager.getSocket() != null)
            socketManager.getSocket().disconnect();
    }

    public boolean load(UsbDevice device) {
        boolean lOk;
        lOk = false;
        if ((device.getVendorId() == (int) 0x04D8) && (device.getProductId() == (int) 0x00DF)) {
            demo = new DemoMCP2200(this.getApplicationContext(), device, handler);
            setContentView(R.layout.activity_main);


            lLucesPosicion = false;
            lLucesBajas = false;
            lBaliza = false;
            lAperturaPorton = false;
            lLucesAntiDelan = false;
            lLucesAntiTras = false;
            lAperturaPuertas = false;
            lespejos = false;
            lespejosdrech = false;
            lespejosizq = false;
            lfrenomano = false;
            nskin = 0;

            espejosinactivos = (ImageView) findViewById(R.id.EspejosInactivos);
            fondocursor = (ImageView) findViewById(R.id.FondoControlEspejos);
            cursores = (ImageView) findViewById(R.id.cursores);
            //skin = (ImageView) findViewById(R.id.skin);
            lateralbaul = (ImageView) findViewById(R.id.Lateralbaul);
            testfrenomano = (ImageView) findViewById(R.id.TestParking);
            oEspejosizq = (ImageButton) findViewById(R.id.ImgBtnEspejosIzq);
            Iskin = (ConstraintLayout) findViewById(R.id.skin);
            marchas = (ImageView) findViewById(R.id.ImgMarchas);


            // -------- toogle button de Espejos Derecha ------------------------
            final ImageButton oEspejosderch = (ImageButton) findViewById(R.id.ImgBtnEspejosDrch);
            oEspejosderch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!lespejos) {
                        lespejosdrech = !lespejosdrech;
                        if (!lespejosdrech) {
                            v.setBackgroundResource(R.drawable.seleccion_espejo_derecho_on);
                            oEspejosizq.setBackgroundResource(R.drawable.seleccion_espejo_izquierdo_off);
                            ((DemoMCP2200) demo).sendString("SDC1\r");
                            ((DemoMCP2200) demo).sendString("SIZ0\r");
                        }

                    }

                }
            });

            // -------- toogle button de Espejos Izquierda ------------------------
            final ImageButton oEspejosizq = (ImageButton) findViewById(R.id.ImgBtnEspejosIzq);
            oEspejosizq.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!lespejos) {
                        lespejosizq = !lespejosizq;
                        if (lespejosizq) {
                            v.setBackgroundResource(R.drawable.seleccion_espejo_izquierdo_on);
                            oEspejosderch.setBackgroundResource(R.drawable.seleccion_espejo_derecho_off);
                            ((DemoMCP2200) demo).sendString("SIZ1\r");
                            ((DemoMCP2200) demo).sendString("SDC0\r");
                        }

                    }

                }
            });

            // -------- toogle button de Espejos ------------------------
            ImageButton oEspejos = (ImageButton) findViewById(R.id.ImgBtnEspejos);
            oEspejos.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    lespejos = !lespejos;
                    if (!lespejos) {
                        v.setBackgroundResource(R.drawable.espejos_unlock);
                        oEspejosderch.setVisibility(View.VISIBLE);
                        oEspejosizq.setVisibility(View.VISIBLE);
                        espejosinactivos.setVisibility(View.INVISIBLE);
                        fondocursor.setVisibility(View.VISIBLE);
                        cursores.setVisibility(View.VISIBLE);
                        ((DemoMCP2200) demo).sendString("SEP1\r");
                    } else {
                        v.setBackgroundResource(R.drawable.espejos_lock);
                        oEspejosderch.setVisibility(View.INVISIBLE);
                        oEspejosizq.setVisibility(View.INVISIBLE);
                        espejosinactivos.setVisibility(View.VISIBLE);
                        fondocursor.setVisibility(View.INVISIBLE);
                        cursores.setVisibility(View.INVISIBLE);
                        ((DemoMCP2200) demo).sendString("SEP0\r");
                    }


                }
            });

            // -------- toogle button de Skin------------------------
            ImageButton oSkin = (ImageButton) findViewById(R.id.ImgBtnCiclosSkin);
            oSkin.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    nskin += 2;
                    if (nskin > 4) nskin = 0;

                    if (nskin == 2) {// prende boton 1 reversa
                        Iskin.setBackgroundResource(R.drawable.skin_carbono);
                    } else {
                        if (nskin == 4) {// prende boton 2 directa
                            Iskin.setBackgroundResource(R.drawable.skin_doble_acero);
                        } else {
                            //Apaga boton neutro
                            Iskin.setBackgroundResource(R.drawable.skin_acero);
                        }

                    }

                }
            });

            // -------- toogle button de cursor arriba ------------------------
            Button oArriba = (Button) findViewById(R.id.cursor_arriba);
            oArriba.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent theMotion) {
                    if (!lespejos) {
                        switch (theMotion.getAction()) {
                            case MotionEvent.ACTION_DOWN: {
                                cursores.setBackgroundResource(R.drawable.cursor_arriba);
                                ((DemoMCP2200) demo).sendString("SEA1\r");
                            }
                            break;
                            case MotionEvent.ACTION_UP: {
                                cursores.setBackgroundResource(R.drawable.cursor_neutro);
                                ((DemoMCP2200) demo).sendString("SEA0\r");
                            }
                            break;
                        }
                    }

                    return false;
                }
            });

            // -------- toogle button de cursor abajo ------------------------
            Button oAbajo = (Button) findViewById(R.id.cursor_abajo);
            oAbajo.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent theMotion) {
                    if (!lespejos) {
                        switch (theMotion.getAction()) {
                            case MotionEvent.ACTION_DOWN: {
                                cursores.setBackgroundResource(R.drawable.cursor_abajo);
                                ((DemoMCP2200) demo).sendString("SEB1\r");
                            }
                            break;
                            case MotionEvent.ACTION_UP: {
                                cursores.setBackgroundResource(R.drawable.cursor_neutro);
                                ((DemoMCP2200) demo).sendString("SEB0\r");
                            }
                            break;
                        }
                    }

                    return false;
                }
            });

            // -------- toogle button de cursor derecha ------------------------
            Button oDerecha = (Button) findViewById(R.id.cursor_derch);
            oDerecha.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent theMotion) {
                    if (!lespejos) {
                        switch (theMotion.getAction()) {
                            case MotionEvent.ACTION_DOWN: {
                                cursores.setBackgroundResource(R.drawable.cursor_derecha);
                                ((DemoMCP2200) demo).sendString("SED1\r");
                            }
                            break;
                            case MotionEvent.ACTION_UP: {
                                cursores.setBackgroundResource(R.drawable.cursor_neutro);
                                ((DemoMCP2200) demo).sendString("SED0\r");
                            }
                            break;
                        }
                    }

                    return false;
                }
            });

            // -------- toogle button de cursor abajo ------------------------
            Button oIzquierda = (Button) findViewById(R.id.cursor_izq);
            oIzquierda.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent theMotion) {
                    if (!lespejos) {
                        switch (theMotion.getAction()) {
                            case MotionEvent.ACTION_DOWN: {
                                cursores.setBackgroundResource(R.drawable.cursor_izquierda);
                                ((DemoMCP2200) demo).sendString("SEI1\r");
                            }
                            break;
                            case MotionEvent.ACTION_UP: {
                                cursores.setBackgroundResource(R.drawable.cursor_neutro);
                                ((DemoMCP2200) demo).sendString("SEI0\r");
                            }
                            break;
                        }
                    }

                    return false;
                }
            });

            // -------- toogle button de webee ------------------------
            ImageButton owebee = (ImageButton) findViewById(R.id.ImgBtnWebbe);
            owebee.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent theMotion) {
                    switch (theMotion.getAction()) {
                        case MotionEvent.ACTION_DOWN: {
                            v.setBackgroundResource(R.drawable.webee_on);

                        }
                        break;
                        case MotionEvent.ACTION_UP: {
                            v.setBackgroundResource(R.drawable.webee_off);
                        }
                        break;
                    }
                    return false;
                }
            });


            // -------- toogle button de Ventanilla Derecha Bajar ------------------------
            ImageButton oVenDerchBaj = (ImageButton) findViewById(R.id.ImgBtnVenDerchBaj);
            oVenDerchBaj.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent theMotion) {
                    switch (theMotion.getAction()) {
                        case MotionEvent.ACTION_DOWN: {
                            v.setBackgroundResource(R.drawable.levanta_vidrios_derecho_bajar_on);
                            ((DemoMCP2200) demo).sendString("SBD1\r");
                        }
                        break;

                        case MotionEvent.ACTION_UP: {
                            v.setBackgroundResource(R.drawable.levanta_vidrios_derecho_bajar_off);
                            ((DemoMCP2200) demo).sendString("SBD0\r");
                        }
                        break;

                    }
                    return false;
                }
            });

            // -------- toogle button de Ventanilla Derecha Subir ------------------------
            ImageButton oVenDerchSub = (ImageButton) findViewById(R.id.ImgBtnVenDerchSub);
            oVenDerchSub.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent theMotion) {
                    switch (theMotion.getAction()) {
                        case MotionEvent.ACTION_DOWN: {
                            v.setBackgroundResource(R.drawable.levanta_vidrios_derecho_subir_on);
                            ((DemoMCP2200) demo).sendString("SSD1\r");
                        }
                        break;
                        case MotionEvent.ACTION_UP: {
                            v.setBackgroundResource(R.drawable.levanta_vidrios_derecho_subir_off);
                            ((DemoMCP2200) demo).sendString("SSD0\r");
                        }
                        break;
                    }
                    return false;
                }
            });

            // -------- toogle button de Ventanilla Izquierda Bajar ------------------------
            ImageButton oVenIzqBaj = (ImageButton) findViewById(R.id.ImgBtnVenIzqBaj);
            oVenIzqBaj.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent theMotion) {
                    switch (theMotion.getAction()) {
                        case MotionEvent.ACTION_DOWN: {
                            v.setBackgroundResource(R.drawable.levanta_vidrios_izquierdo_bajar_on);
                            ((DemoMCP2200) demo).sendString("SBI1\r");
                        }
                        break;


                        case MotionEvent.ACTION_UP: {
                            v.setBackgroundResource(R.drawable.levanta_vidrios_izquierdo_bajar_off);
                            ((DemoMCP2200) demo).sendString("SBI0\r");
                        }
                        break;
                    }
                    return false;
                }
            });

            // -------- toogle button de Ventanilla Izquierda Subir ------------------------
            ImageButton oVenIzqSub = (ImageButton) findViewById(R.id.ImgBtnVenIzqSub);
            oVenIzqSub.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent theMotion) {
                    switch (theMotion.getAction()) {
                        case MotionEvent.ACTION_DOWN: {
                            v.setBackgroundResource(R.drawable.levanta_vidrios_izquierdo_subir_on);
                            ((DemoMCP2200) demo).sendString("SSI1\r");
                        }
                        break;
                        case MotionEvent.ACTION_UP: {
                            v.setBackgroundResource(R.drawable.levanta_vidrios_izquierdo_subir_off);
                            ((DemoMCP2200) demo).sendString("SSI0\r");
                        }
                        break;
                    }
                    return false;
                }
            });

            // -------- toogle button de Velocidad Aire Acondicionado ------------------------

            Button BtnDirecta = (Button) findViewById(R.id.BtnDirecta);
            BtnDirecta.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    marchas.setBackgroundResource(R.drawable.marcha_d_on);
                    ((DemoMCP2200) demo).sendString("SDT1\r");
                    /*((DemoMCP2200) demo).sendString("SRV0\r");
                    ((DemoMCP2200) demo).sendString("SNR0\r");*/
                }
            });

            Button BtnReversa = (Button) findViewById(R.id.BtnNeutro);
            BtnReversa.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    marchas.setBackgroundResource(R.drawable.marcha_n_on);
                    ((DemoMCP2200) demo).sendString("SNR1\r");
                    /*((DemoMCP2200) demo).sendString("SRV0\r");
                    ((DemoMCP2200) demo).sendString("SDT0\r");*/
                }
            });

            Button BtnNeutro = (Button) findViewById(R.id.BtnReversa);
            BtnNeutro.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    marchas.setBackgroundResource(R.drawable.marcha_r_on);
                    ((DemoMCP2200) demo).sendString("SRV1\r");
                    /*((DemoMCP2200) demo).sendString("SNR0\r");
                    ((DemoMCP2200) demo).sendString("SDT0\r");*/
                }
            });

            setTitle(demo.getDeviceTitle());

            try {
                demo.setBaudRate("9600");
                //Tostada("Baud: 9600");
            } catch (Exception ex) {
                Tostada(ex.getMessage());
            }

            lOk = true;
        }

        return lOk;
    }

    public void balizaOnClick(View v) {
        synchronized (demo) {
            if (demo != null) {
                if (lBaliza) {
                    ((DemoMCP2200) demo).sendString("SBA1\r");
                    v.setBackgroundResource(R.drawable.baliza_on);
                } else {
                    ((DemoMCP2200) demo).sendString("SBA0\r");
                    v.setBackgroundResource(R.drawable.baliza_off);
                }
                lBaliza = !lBaliza;
            }
        }
    }

    public void powerOnClick(View v) {
        synchronized (demo) {
            if (demo != null) {
                if (!lPower) {
                    v.setBackgroundResource(R.drawable.encendido_on);
                    ((DemoMCP2200) demo).sendString("SPO1\r");
                } else {
                    v.setBackgroundResource(R.drawable.encendido_off);
                    ((DemoMCP2200) demo).sendString("SPO0\r");
                }
                lPower = !lPower;
            }
        }
    }

    public void luzbajaOnClick(View v) {
        synchronized (demo) {
            if (demo != null) {

                if (!lLucesBajas) {
                    v.setBackgroundResource(R.drawable.luz_baja_on);
                    ((DemoMCP2200) demo).sendString("SLB1\r");
                } else {
                    v.setBackgroundResource(R.drawable.luz_baja_off);
                    ((DemoMCP2200) demo).sendString("SLB0\r");
                }
                lLucesBajas = !lLucesBajas;
            }
        }
    }

    public void luzposicionOnClick(View v) {
        synchronized (demo) {
            if (demo != null) {

                if (!lLucesPosicion) {
                    v.setBackgroundResource(R.drawable.luz_de_posicion_on);
                    ((DemoMCP2200) demo).sendString("SLP1\r");
                } else {
                    v.setBackgroundResource(R.drawable.luz_de_posicion_off);
                    ((DemoMCP2200) demo).sendString("SLP0\r");
                }
                lLucesPosicion = !lLucesPosicion;
            }
        }
    }

    public void niebladelanteraOnClick(View v) {
        synchronized (demo) {
            if (demo != null) {

                if (!lLucesAntiDelan) {
                    v.setBackgroundResource(R.drawable.luz_delantera_niebla_on);
                    ((DemoMCP2200) demo).sendString("SND1\r");
                } else {
                    v.setBackgroundResource(R.drawable.luz_delantera_niebla_off);
                    ((DemoMCP2200) demo).sendString("SND0\r");
                }
                lLucesAntiDelan = !lLucesAntiDelan;
            }
        }
    }

    public void nieblatraseraOnClick(View v) {
        synchronized (demo) {
            if (demo != null) {

                if (!lLucesAntiTras) {
                    v.setBackgroundResource(R.drawable.luz_trasera_niebla_on);
                    ((DemoMCP2200) demo).sendString("SNT1\r");
                } else {
                    v.setBackgroundResource(R.drawable.luz_trasera_niebla_off);
                    ((DemoMCP2200) demo).sendString("SNT0\r");
                }
                lLucesAntiTras = !lLucesAntiTras;
            }
        }
    }

    public void frenomanoOnClick(View v) {
        synchronized (demo) {
            if (demo != null) {

                if (!lfrenomano) {
                    v.setBackgroundResource(R.drawable.bron_freno_mano_on);
                    testfrenomano.setImageResource(R.drawable.testigo_freno_de_mano_on);
                    ((DemoMCP2200) demo).sendString("SFM1\r");
                } else {
                    v.setBackgroundResource(R.drawable.bron_freno_mano_off);
                    testfrenomano.setImageResource(R.drawable.testigo_freno_de_mano_off);
                    ((DemoMCP2200) demo).sendString("SFM0\r");
                }
                lfrenomano = !lfrenomano;
            }
        }
    }

    public void aperturapuertasOnClick(View v) {
        synchronized (demo) {
            if (demo != null) {

                if (!lAperturaPuertas) {
                    v.setBackgroundResource(R.drawable.puertas_lock);
                    ((DemoMCP2200) demo).sendString("SAP1\r");
                } else {
                    v.setBackgroundResource(R.drawable.puertas_unlock);
                    ((DemoMCP2200) demo).sendString("SAP0\r");
                }
                lAperturaPuertas = !lAperturaPuertas;
            }
        }
    }

    public void aperturaportonOnClick(View v) {
        synchronized (demo) {
            if (demo != null) {
                if (!lAperturaPorton) {
                    v.setBackgroundResource(R.drawable.porton_trasero_lock);
                    lateralbaul.setImageResource(R.drawable.vista_lateral_baul_abierto);
                    ((DemoMCP2200) demo).sendString("SAT1\r");
                } else {
                    v.setBackgroundResource(R.drawable.porton_trasero_unlock);
                    lateralbaul.setImageResource(R.drawable.vista_lateral_baul_cerrado);
                    ((DemoMCP2200) demo).sendString("SAT0\r");
                }
                lAperturaPorton = !lAperturaPorton;
            }
        }
    }


    private void Tostada(String cMsg) {
        Toast.makeText(this.getBaseContext(), cMsg, Toast.LENGTH_SHORT).show();

    }

    BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            /* Get the information about what action caused this event */
            String action = intent.getAction();

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                /* If it was a USB device detach event, then get the USB device
                 * that cause the event from the intent.
                 */
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                if (device != null) {
                    /* Synchronize to demo here to make sure that the main GUI isn't
                     * doing something with the demo at the moment.
                     */
                    synchronized (demo) {
                        /* If the demo exits, close it down and free it up */
                        if (demo != null) {
                            demo.close();
                            demo = null;
                        }
                    }
                    //setContentView(R.layout.no_device);
                }
            }
        }
    };

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            /* Determine what type of message was sent. And process it accordingly */
            if (msg.obj.getClass().equals(MessageButton.class)) {
                //updateButton(R.id.button_status, ((MessageButton)msg.obj).isPressed);
            } else if (msg.obj.getClass().equals(MessagePotentiometer.class)) {
                //updatePotentiometer(R.id.potentiometer_status, ((MessagePotentiometer)msg.obj).percentage);
            } else if (msg.obj.getClass().equals(MessageText.class)) {
                AddComando(((MessageText) msg.obj).message);
            }
        } //handleMessage
    }; //handler

    private void updateButton(int id, boolean pressed) {
    }
        /*TextView textviewToUpdate;
        LinearLayout layoutToUpdate;
*/
    /* Get the requested TextView and its parent layout using
     * the passed in id.
     */
    /*    textviewToUpdate = (TextView)findViewById(id);
        layoutToUpdate = (LinearLayout)textviewToUpdate.getParent();
*/
    /* Set the background resource to 0 to clear out the existing
     * background information (removes artifacts).
     */
     /*   layoutToUpdate.setBackgroundResource(0);

        if(pressed == true)
        {
            textviewToUpdate.setText(R.string.button_pressed);
            textviewToUpdate.setBackgroundResource(R.color.button_pressed);

        } else {
            textviewToUpdate.setText(R.string.button_not_pressed);
            textviewToUpdate.setBackgroundResource(R.color.button_not_pressed);
       }
/*
    }
}

    private void updatePotentiometer(int id, int percentage) {
        /*ProgressBar bar;

        bar = (ProgressBar)findViewById(id);
        bar.setProgress(percentage);
        }*/


    private void updateTextView(String text) {
      /*  TextView box;

        box = (TextView)findViewById(R.id.basic_text_view);
        box.setText(box.getText()+text);

        box = (TextView)findViewById(R.id.button_status);
        box.setText(text);
    */
    }

    // agrega caracteres recibidos al comando. cuando encuentra un enter procesa comando
    private void AddComando(String cComando) {
        int nPos;

        // reemplaza el caracter de avance de linea
        cComando = cComando.replace("\n", "");

        // busca un caracter de retorno de carro
        nPos = cComando.indexOf(13);

        if (nPos == -1)
            cInCmd += cComando;
        else {
            // agrega comando hast el enter
            cInCmd += cComando.substring(0, nPos);
            // salva el resto del comando
            cComando = cComando.substring(nPos + 1);
            // envia a procesar comando
            ProcesarComando(cInCmd);
            // guarda resto del comando para proxima recepcion
            cInCmd = cComando;
        }
    }

    private void ProcesarComando(String cComando) {

        cComando = cComando.replace("\r", "");
        switch (cComando) {

            case "ILB0":
                setCmdDescripcion("Subir Vidrio OFF");
                break;
            case "ILB1":
                setCmdDescripcion("Subir Vidrio ON");
                break;
            case "ILA1":
                setCmdDescripcion("Luces Altas Prendidas");
                break;
            case "ILA0":
                setCmdDescripcion("Luces Altas Apagadas");
                //Tostada("Luces Altas Apagadas");
                break;
            default:
                setCmdDescripcion("Comando No Reconocido");
                //Tostada(cComando);
        }
        updateTextView(cComando);
    }

    private void setCmdDescripcion(String text) {

       /* TextView box;

        box = (TextView)findViewById(R.id.cmd_status);
        box.setText(text);
        */

    }
}
