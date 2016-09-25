/**
 * Copyright 2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * You may not use this file except in compliance with the License. A copy of the License is located the "LICENSE.txt"
 * file accompanying this source. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing permissions and limitations
 * under the License.
 */
package com.amazon.alexa.avs.app.headless;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazon.alexa.avs.AVSAudioPlayerFactory;
import com.amazon.alexa.avs.AVSController;
import com.amazon.alexa.avs.AlertManagerFactory;
import com.amazon.alexa.avs.DialogRequestIdAuthority;
import com.amazon.alexa.avs.ExpectSpeechListener;
import com.amazon.alexa.avs.PlaybackAction;
import com.amazon.alexa.avs.RecordingRMSListener;
import com.amazon.alexa.avs.RequestListener;
import com.amazon.alexa.avs.SystemOutVisualizer;
import com.amazon.alexa.avs.Visualizer;
import com.amazon.alexa.avs.auth.AccessTokenListener;
import com.amazon.alexa.avs.auth.AuthSetup;
import com.amazon.alexa.avs.auth.companionservice.RegCodeDisplayHandler;
import com.amazon.alexa.avs.config.DeviceConfig;
import com.amazon.alexa.avs.config.DeviceConfigUtils;
import com.amazon.alexa.avs.http.AVSClientFactory;

public class AVSConsoleApp implements ExpectSpeechListener, RecordingRMSListener,
        RegCodeDisplayHandler, AccessTokenListener {

    private static final Logger log = LoggerFactory.getLogger(AVSConsoleApp.class);

    private static final String PREVIOUS_LABEL = "\u21E4";
    private static final String NEXT_LABEL = "\u21E5";
    private final AVSController controller;
    private Visualizer visualizer;
    private Thread autoEndpoint = null; // used to auto-endpoint while listening
    private final DeviceConfig deviceConfig;
    // minimum audio level threshold under which is considered silence
    private static final int ENDPOINT_THRESHOLD = 5;
    private static final int ENDPOINT_SECONDS = 2; // amount of silence time before endpointing
    private String accessToken;
    private final RecordingRMSListener _rmsListener;
    
    private static volatile boolean _isListening = false;

    private AuthSetup authSetup;

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            new AVSConsoleApp(args[0]);
        } else {
            new AVSConsoleApp();
        }
    }

    public AVSConsoleApp() throws Exception {
        this(DeviceConfigUtils.readConfigFile());
    }

    public AVSConsoleApp(String configName) throws Exception {
        this(DeviceConfigUtils.readConfigFile(configName));
    }

    private AVSConsoleApp(DeviceConfig config) throws Exception {
        deviceConfig = config;
        controller = new AVSController(this, new AVSAudioPlayerFactory(), new AlertManagerFactory(),
                getAVSClientFactory(deviceConfig), DialogRequestIdAuthority.getInstance());

        authSetup = new AuthSetup(config, this);
        authSetup.addAccessTokenListener(this);
        authSetup.addAccessTokenListener(controller);
        authSetup.startProvisioningThread();

        System.out.println("Starting APP");
        printDeviceInfo();
        addTokenField();
        addVisualizer();
        _rmsListener = this;
        addPlaybackButtons();
        
        controller.startHandlingDirectives();
    }

    private String getAppVersion() {
        final Properties properties = new Properties();
        try (final InputStream stream = getClass().getResourceAsStream("/res/version.properties")) {
            properties.load(stream);
            if (properties.containsKey("version")) {
                return properties.getProperty("version");
            }
        } catch (IOException e) {
            log.warn("version.properties file not found on classpath");
        }
        return null;
    }

    protected AVSClientFactory getAVSClientFactory(DeviceConfig config) {
        return new AVSClientFactory(config);
    }

    private void printDeviceInfo() {
    	System.out.println("Product ID: "+deviceConfig.getProductId());
        System.out.println("DSN: "+deviceConfig.getDsn());
    }

    private void addTokenField() {
    	//TODO: React on token changes
    	/*
    	tokenTextField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                controller.onUserActivity();
                authSetup.onAccessTokenReceived(tokenTextField.getText());
            }
        });
		*/
        if (accessToken != null) {
            System.out.println("Current access token: "+accessToken);
        }
    }

    private void addVisualizer() {
        visualizer = new SystemOutVisualizer();
    }

    private void actionPerformed() {
    	controller.onUserActivity();
        if (!_isListening) { // if in idle mode
        	_isListening = true;

            RequestListener requestListener = new RequestListener() {

                @Override
                public void onRequestSuccess() {
                    finishProcessing();
                }

                @Override
                public void onRequestError(Throwable e) {
                    System.out.println("An error occured creating speech request: ");
                    e.printStackTrace();
                    _isListening = false;
                    finishProcessing();
                }
            };

            controller.startRecording(_rmsListener, requestListener);
        } else { // else we must already be in listening
        	_isListening = true;
            visualizer.setIndeterminate(true);
            controller.stopRecording(); // stop the recording so the request can complete
        }
    }
    
    /**
     * Respond to a music button press event
     *
     * @param action
     *            Playback action to handle
     */
    private void musicButtonPressedEventHandler(final PlaybackAction action) {
    	Runnable runnable = new Runnable() {
			@Override
			public void run() {
				try {
					visualizer.setIndeterminate(true);
					controller.handlePlaybackAction(action);
				} finally {
					visualizer.setIndeterminate(false);
				}
			}
		};
		new Thread(runnable).start();
    }

    private void createMusicButton(String label, final PlaybackAction action) {
        System.out.println("Press "+label+" to do "+action);
        //TODO: Attach the listener to something so that music can be played
        ActionListener listener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                controller.onUserActivity();
                musicButtonPressedEventHandler(action);
            }
        };
    }

    /**
     * Add music control buttons
     */
    private void addPlaybackButtons() {
    	ActionListener playPauseActionListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                controller.onUserActivity();
                if (controller.isPlaying()) {
                    musicButtonPressedEventHandler(PlaybackAction.PAUSE);
                } else {
                    musicButtonPressedEventHandler(PlaybackAction.PLAY);
                }
            }
        };

        createMusicButton(PREVIOUS_LABEL, PlaybackAction.PREVIOUS);

        createMusicButton(NEXT_LABEL, PlaybackAction.NEXT);
    }

    public void finishProcessing() {
    	_isListening = false;
        visualizer.setIndeterminate(false);
        controller.processingFinished();
    }

    @Override
    public void rmsChanged(int rms) { // AudioRMSListener callback
        // if greater than threshold or not recording, kill the autoendpoint thread
        if ((rms == 0) || (rms > ENDPOINT_THRESHOLD)) {
            if (autoEndpoint != null) {
                autoEndpoint.interrupt();
                autoEndpoint = null;
            }
        } else if (rms < ENDPOINT_THRESHOLD) {
            // start the autoendpoint thread if it isn't already running
            if (autoEndpoint == null) {
                autoEndpoint = new Thread() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(ENDPOINT_SECONDS * 1000);
                            actionPerformed();
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                };
                autoEndpoint.start();
            }
        }

        visualizer.setValue(rms); // update the visualizer
    }

    @Override
    public void onExpectSpeechDirective() {
        Thread thread = new Thread() {
            @Override
            public void run() {
                while (_isListening) {
                    try {
                        Thread.sleep(500);
                    } catch (Exception e) {
                    }
                }
                actionPerformed();
            }
        };
        thread.start();

    }

    public void showDialog(String message) {
        System.out.println(message);
    }

    @Override
    public void displayRegCode(String regCode) {
        String regUrl =
                deviceConfig.getCompanionServiceInfo().getServiceUrl() + "/provision/" + regCode;
        showDialog("Please register your device by visiting the following website on "
                + "any system and following the instructions:\n" + regUrl
                + "\n\n Hit OK once completed.");
    }

    @Override
    public synchronized void onAccessTokenReceived(String accessToken) {
        this.accessToken = accessToken;
        System.out.println("New access token: "+accessToken);
    }

}
