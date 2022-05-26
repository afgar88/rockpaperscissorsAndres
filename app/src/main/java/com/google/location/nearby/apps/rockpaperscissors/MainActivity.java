package com.google.location.nearby.apps.rockpaperscissors;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SimpleArrayMap;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;

import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate.Status;
import com.google.android.gms.nearby.connection.Strategy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Activity controlling the Rock Paper Scissors game
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "RockPaperScissors";

    private static String[] getRequiredPerms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_SCAN,
            };
        } else {
            return new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }
    }


    private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 1;

    private static final Strategy STRATEGY = Strategy.P2P_STAR;

    private enum GameChoice {
        ROCK,
        PAPER,
        SCISSORS;

        boolean beats(GameChoice other) {
            return (this == GameChoice.ROCK && other == GameChoice.SCISSORS)
                    || (this == GameChoice.SCISSORS && other == GameChoice.PAPER)
                    || (this == GameChoice.PAPER && other == GameChoice.ROCK);
        }
    }

    // Our handle to Nearby Connections
    private ConnectionsClient connectionsClient;

    // Our randomly generated name
    private final String codeName = CodenameGenerator.generate();

    private String opponentEndpointId;
    private String opponentName;
    private int opponentScore;
    private GameChoice opponentChoice;

    private int myScore;
    private GameChoice myChoice;

    private Button findOpponentButton;
    private Button disconnectButton;
    private Button rockButton;
    private Button paperButton;
    private Button scissorsButton;
    private Button sendButton;

    private TextView opponentText;
    private TextView statusText;
    private TextView scoreText;
    ImageView image_view;
    boolean isImage = false;
    private static final int READ_REQUEST_CODE = 1;
    private static final String ENDPOINT_ID_EXTRA = "com.foo.myapp.EndpointId";

    public final SimpleArrayMap<Long, Payload> incomingFilePayloads = new SimpleArrayMap<>();
    private final SimpleArrayMap<Long, Payload> completedFilePayloads = new SimpleArrayMap<>();
    private final SimpleArrayMap<Long, String> filePayloadFilenames = new SimpleArrayMap<>();

    // Callbacks for receiving payloads
    private final PayloadCallback payloadCallback =
            new PayloadCallback() {
                @Override
                public void onPayloadReceived(String endpointId, Payload payload) {
                    if (payload.getType() == Payload.Type.BYTES) {
                        String data = new String(payload.asBytes(), UTF_8);
                        if (data.equals("ROCK") || data.equals("SCISSORS") || data.equals("PAPER")) {
                            opponentChoice = GameChoice.valueOf(data);
                        } else {
                            long payloadId = addPayloadFilename(data);
                            processFilePayload(payloadId);

                        }

                    } else if (payload.getType() == Payload.Type.FILE) {

                        incomingFilePayloads.put(payload.getId(), payload);
                    }

                    isImage = false;
                }

                @Override
                public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
                    if (update.getStatus() == Status.SUCCESS) {
                        if (myChoice != null && opponentChoice != null) {
                            finishRound();
                        } else {
                            long payloadId = update.getPayloadId();
                            Payload payload = incomingFilePayloads.remove(payloadId);
                            completedFilePayloads.put(payloadId, payload);
                            if (payload != null && payload.getType() == Payload.Type.FILE) {
                                processFilePayload(payloadId);
                            }
                        }
                    }

                }
            };

    private long addPayloadFilename(String payloadFilenameMessage) {
        String[] parts = payloadFilenameMessage.split(":");
        long payloadId = Long.parseLong(parts[0]);
        String filename = parts[1];
        filePayloadFilenames.put(payloadId, filename);
        return payloadId;
    }


    private void processFilePayload(long payloadId) {
        Payload filePayload = completedFilePayloads.get(payloadId);
        String filename = filePayloadFilenames.get(payloadId);
        if (filePayload != null && filename != null) {
            completedFilePayloads.remove(payloadId);
            filePayloadFilenames.remove(payloadId);
            Uri uri = filePayload.asFile().asUri();
            try {
                InputStream in = MainActivity.this.getContentResolver().openInputStream(uri);


               Drawable d= Drawable.createFromStream(in,filename);
                image_view.setImageDrawable(d);

                copyStream(in, new FileOutputStream(new File(MainActivity.this.getCacheDir(), filename)));
            } catch (IOException e) {
            } finally {
                MainActivity.this.getContentResolver().delete(uri, null, null);
            }
        }
    }

    /**
     * Copies a stream from one location to another.
     */
    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        try {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        } finally {
            in.close();
            out.close();
        }
    }


    // Callbacks for finding other devices
    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
                    Log.i(TAG, "onEndpointFound: endpoint found, connecting");
                    connectionsClient
                            .requestConnection(codeName, endpointId, connectionLifecycleCallback)
                            .addOnSuccessListener(unused -> {
                                Log.d(TAG, "onEndpointFound: success discovery");
                            })
                            .addOnFailureListener(e -> {
                                Log.d(TAG, "onEndpointFound: failure discovery" + e.getLocalizedMessage());
                            });
                }

                @Override
                public void onEndpointLost(String endpointId) {
                    Log.i(TAG, "onEndpointFound: endpoint failure, " + endpointId);
                }
            };

    // Callbacks for connections to other devices
    private final ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
                    Log.i(TAG, "onConnectionInitiated: accepting connection");

                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Accept connection to " + connectionInfo.getEndpointName())
                            .setMessage("Devices connected: " + connectionInfo.getEndpointName())
                            .setPositiveButton("ACCEPT", (dialogInterface, i) -> {
                                opponentName = connectionInfo.getEndpointName();
                                connectionsClient.acceptConnection(endpointId, payloadCallback);
                                dialogInterface.dismiss();
                            })
                            .setNegativeButton("CANCEL", (dialogInterface, i) -> {
                                connectionsClient.rejectConnection(endpointId);
                                dialogInterface.dismiss();
                                findOpponentButton.setEnabled(true);
                            }).create().show();
                }

                @Override
                public void onConnectionResult(String endpointId, ConnectionResolution result) {
                    if (result.getStatus().isSuccess()) {
                        Log.i(TAG, "onConnectionResult: connection successful");

                        connectionsClient.stopDiscovery();
                        connectionsClient.stopAdvertising();

                        opponentEndpointId = endpointId;
                        setOpponentName(opponentName);
                        setStatusText(getString(R.string.status_connected));
                        setButtonState(true);
                    } else {
                        Log.i(TAG, "onConnectionResult: connection failed");
                    }
                }

                @Override
                public void onDisconnected(String endpointId) {
                    Log.i(TAG, "onDisconnected: disconnected from the opponent");
                    resetGame();
                }
            };

    @Override
    protected void onCreate(@Nullable Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_main);


        findOpponentButton = findViewById(R.id.find_opponent);
        disconnectButton = findViewById(R.id.disconnect);
        rockButton = findViewById(R.id.rock);
        paperButton = findViewById(R.id.paper);
        scissorsButton = findViewById(R.id.scissors);
        sendButton = findViewById(R.id.send_img);
        image_view = findViewById(R.id.main_image);


        opponentText = findViewById(R.id.opponent_name);
        statusText = findViewById(R.id.status);
        scoreText = findViewById(R.id.score);

        TextView nameView = findViewById(R.id.name);
        nameView.setText(getString(R.string.codename, codeName));

        connectionsClient = Nearby.getConnectionsClient(this);

        resetGame();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!hasPermissions(this, getRequiredPerms())) {
            requestPermissions(getRequiredPerms(), REQUEST_CODE_REQUIRED_PERMISSIONS);
        }
    }

    @Override
    protected void onStop() {
        if (!isImage) {
            connectionsClient.stopAllEndpoints();
            resetGame();

        }
        super.onStop();


    }

    /**
     * Returns true if the app was granted all the permissions. Otherwise, returns false.
     */
    private static boolean hasPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Handles user acceptance (or denial) of our permission request.
     */
    @CallSuper
    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != REQUEST_CODE_REQUIRED_PERMISSIONS) {
            return;
        }

        for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(this, R.string.error_missing_permissions, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        }
        recreate();
    }

    /**
     * Finds an opponent to play the game with using Nearby Connections.
     */
    public void findOpponent(View view) {
        startAdvertising();
        startDiscovery();
        setStatusText(getString(R.string.status_searching));
        findOpponentButton.setEnabled(false);
    }

    /**
     * Disconnects from the opponent and reset the UI.
     */
    public void disconnect(View view) {

        Log.d("Disconnect", "");
        connectionsClient.disconnectFromEndpoint(opponentEndpointId);
        resetGame();
    }


    /**
     * Sends a {@link GameChoice} to the other player.
     */
    public void makeMove(View view) {
        if (view.getId() == R.id.rock) {
            sendGameChoice(GameChoice.ROCK);
        } else if (view.getId() == R.id.paper) {
            sendGameChoice(GameChoice.PAPER);
        } else if (view.getId() == R.id.scissors) {
            sendGameChoice(GameChoice.SCISSORS);
        }
    }

    /**
     * Starts looking for other players using Nearby Connections.
     */
    private void startDiscovery() {
        // Note: Discovery may fail. To keep this demo simple, we don't handle failures.
        connectionsClient.startDiscovery(
                getPackageName(), endpointDiscoveryCallback,
                new DiscoveryOptions.Builder().setStrategy(STRATEGY).build());
    }

    /**
     * Broadcasts our presence using Nearby Connections so other players can find us.
     */
    private void startAdvertising() {
        // Note: Advertising may fail. To keep this demo simple, we don't handle failures.
        connectionsClient.startAdvertising(
                codeName, getPackageName(), connectionLifecycleCallback,
                new AdvertisingOptions.Builder().setStrategy(STRATEGY).build());
    }

    /**
     * Wipes all game state and updates the UI accordingly.
     */
    private void resetGame() {
        opponentEndpointId = null;
        opponentName = null;
        opponentChoice = null;
        opponentScore = 0;
        myChoice = null;
        myScore = 0;

        setOpponentName(getString(R.string.no_opponent));
        setStatusText(getString(R.string.status_disconnected));
        updateScore(myScore, opponentScore);
        setButtonState(false);
    }

    /**
     * Sends the user's selection of rock, paper, or scissors to the opponent.
     */
    private void sendGameChoice(GameChoice choice) {
        myChoice = choice;
        connectionsClient.sendPayload(
                opponentEndpointId, Payload.fromBytes(choice.name().getBytes(UTF_8)));

        setStatusText(getString(R.string.game_choice, choice.name()));
        // No changing your mind!
        setGameChoicesEnabled(false);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == READ_REQUEST_CODE
                && resultCode == Activity.RESULT_OK
                && resultData != null) {
            // The URI of the file selected by the user.
            Uri uri = resultData.getData();

            Payload filePayload;
            try {
                // Open the ParcelFileDescriptor for this URI with read access.
                ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
                filePayload = Payload.fromFile(pfd);
            } catch (FileNotFoundException e) {
                Log.e("MyApp", "File not found", e);
                return;
            }

            // Construct a simple message mapping the ID of the file payload to the desired filename.
            String filenameMessage = filePayload.getId() + ":" + uri.getLastPathSegment();

            //Send the filename message as a bytes payload.
            Payload filenameBytesPayload =
                    Payload.fromBytes(filenameMessage.getBytes(StandardCharsets.UTF_8));
            connectionsClient.sendPayload(opponentEndpointId, filenameBytesPayload);

            // Finally, send the file payload.
            connectionsClient.sendPayload(opponentEndpointId, filePayload);


        }
    }


    public void sendImage(View view) {
        isImage = true;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(ENDPOINT_ID_EXTRA, opponentEndpointId);
        startActivityForResult(intent, READ_REQUEST_CODE);

    }

    /**
     * Determines the winner and update game state/UI after both players have chosen.
     */
    private void finishRound() {
        if (myChoice.beats(opponentChoice)) {
            // Win!
            setStatusText(getString(R.string.win_message, myChoice.name(), opponentChoice.name()));
            myScore++;
        } else if (myChoice == opponentChoice) {
            // Tie, same choice by both players
            setStatusText(getString(R.string.tie_message, myChoice.name()));
        } else {
            // Loss
            setStatusText(getString(R.string.loss_message, myChoice.name(), opponentChoice.name()));
            opponentScore++;
        }

        myChoice = null;
        opponentChoice = null;

        updateScore(myScore, opponentScore);

        // Ready for another round
        setGameChoicesEnabled(true);
    }

    /**
     * Enables/disables buttons depending on the connection status.
     */
    private void setButtonState(boolean connected) {
        findOpponentButton.setEnabled(true);
        findOpponentButton.setVisibility(connected ? View.GONE : View.VISIBLE);
        disconnectButton.setVisibility(connected ? View.VISIBLE : View.GONE);

        setGameChoicesEnabled(connected);
    }

    /**
     * Enables/disables the rock, paper, and scissors buttons.
     */
    private void setGameChoicesEnabled(boolean enabled) {
        rockButton.setEnabled(enabled);
        paperButton.setEnabled(enabled);
        scissorsButton.setEnabled(enabled);
        sendButton.setEnabled(enabled);
    }

    /**
     * Shows a status message to the user.
     */
    private void setStatusText(String text) {
        statusText.setText(text);
    }

    /**
     * Updates the opponent name on the UI.
     */
    private void setOpponentName(String opponentName) {
        opponentText.setText(getString(R.string.opponent_name, opponentName));
    }

    /**
     * Updates the running score ticker.
     */
    private void updateScore(int myScore, int opponentScore) {
        scoreText.setText(getString(R.string.game_score, myScore, opponentScore));
    }
}
