package com.dds.skywebrtc;

import android.app.Application;
import android.content.Context;
import android.media.AudioManager;
import android.text.TextUtils;
import android.util.Log;

import com.dds.skywebrtc.render.ProxyVideoSink;
import com.example.tracking.FrameProcessingTask;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.NetworkMonitor;
import org.webrtc.NetworkMonitorAutoDetect;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by dds on 2019/8/19.
 * 会话层
 */
public class CallSession implements NetworkMonitor.NetworkObserver {
    public final static String TAG = "dds_CallSession";
    private WeakReference<CallSessionCallback> sessionCallback;
    private SkyEngineKit avEngineKit;
    public ExecutorService executor;

    public static final String VIDEO_TRACK_ID = "ARDAMSv0";
    public static final String AUDIO_TRACK_ID = "ARDAMSa0";
    public static final String VIDEO_CODEC_H264 = "H264";
    public static final int VIDEO_RESOLUTION_WIDTH = 1280;
    public static final int VIDEO_RESOLUTION_HEIGHT = 720;
    public static final int FPS = 20;

    public PeerConnectionFactory _factory;
    public MediaStream _localStream;
    private MediaStream _remoteStream;
    public VideoTrack _localVideoTrack;
    public AudioTrack _localAudioTrack;
    public VideoSource videoSource;
    public AudioSource audioSource;
    public VideoCapturer captureAndroid;
    public EglBase mRootEglBase;
    private Context mContext;
    private AudioManager audioManager;
    private NetworkMonitor networkMonitor;
    private Peer mPeer;
    // session参数
    public boolean mIsAudioOnly;
    public String mTargetId;
    public String mRoom;
    public String mMyId;
    public boolean mIsComing;
    public EnumType.CallState _callState = EnumType.CallState.Idle;
    private long startTime;

    private AudioDeviceModule audioDeviceModule;
    private boolean isSwitch = false; // Whether the camera is being switched



    private enum Role {Caller, Receiver,}

    private Role _role;

    public CallSession(SkyEngineKit avEngineKit, Context context, boolean audioOnly) {
        this.avEngineKit = avEngineKit;
        mRootEglBase = EglBase.create();
        executor = Executors.newSingleThreadExecutor();
        mContext = context;
        this.mIsAudioOnly = audioOnly;
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        networkMonitor = NetworkMonitor.getInstance();
    }


    // ----------------------------------------Various controls--------------------------------------------

    // Create room
    public void createHome(String room, int roomSize) {
        executor.execute(() -> {
            if (avEngineKit.mEvent != null) {
                avEngineKit.mEvent.createRoom(room, roomSize);
            }
        });
    }

    // Join the room
    public void joinHome() {
        executor.execute(() -> {
            _callState = EnumType.CallState.Connecting;
            if (avEngineKit.mEvent != null) {
                avEngineKit.mEvent.sendJoin(mRoom);
            }
        });

    }

    //Should ringing
    public void shouldStartRing() {
        if (avEngineKit.mEvent != null) {
            avEngineKit.mEvent.shouldStartRing(true);
        }
    }

    // Turn off htw bell
    public void shouldStopRing() {
        if (avEngineKit.mEvent != null) {
            avEngineKit.mEvent.shouldStopRing();
        }
    }

    // Send a ring reply
    public void sendRingBack(String targetId) {
        executor.execute(() -> {
            if (avEngineKit.mEvent != null) {
                avEngineKit.mEvent.sendRingBack(targetId);
            }
        });
    }

    // Send rejection signaling
    public void sendRefuse() {
        executor.execute(() -> {
            if (avEngineKit.mEvent != null) {
                // Cancel out
                avEngineKit.mEvent.sendRefuse(mTargetId, EnumType.RefuseType.Hangup.ordinal());
            }
        });

    }

    // Send cancellation signalling
    public void sendCancel() {
        executor.execute(() -> {
            if (avEngineKit.mEvent != null) {
                // cancel
                avEngineKit.mEvent.sendCancel(mTargetId);
            }
        });
        //added by Murodjon 2020.03.11
        release();
    }

    // Leave the room
    public void leave() {
        executor.execute(() -> {
            if (avEngineKit.mEvent != null) {
                avEngineKit.mEvent.sendLeave(mRoom, mMyId);
            }
        });
        release();
    }

    // Set mute
    public boolean muteAudio(boolean enable) {
//        if (_localAudioTrack != null) {
//            _localAudioTrack.setEnabled(enable);
//            return true;
//        }
        if (audioDeviceModule != null) {
            audioDeviceModule.setMicrophoneMute(enable);
            return true;
        }

        return false;

    }

    // Set up speaker
    public boolean toggleSpeaker(boolean enable) {
//        if (audioManager != null) {
//            if (enable) {
//                audioManager.setSpeakerphoneOn(true);
//                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL,
//                        audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL),
//                        AudioManager.STREAM_VOICE_CALL);
//            } else {
//                audioManager.setSpeakerphoneOn(false);
//                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL,
//                        audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL), AudioManager.STREAM_VOICE_CALL);
//            }
//
//            return true;
//        }

        if (audioDeviceModule != null) {
            audioDeviceModule.setSpeakerMute(enable);
            return true;
        }
        return false;
    }

    // adjust camera front and rear
    public void switchCamera() {
        if (isSwitch) return;
        isSwitch = true;
        if (captureAndroid == null) return;
        if (captureAndroid instanceof CameraVideoCapturer) {
            CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) captureAndroid;
            try {
                cameraVideoCapturer.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
                    @Override
                    public void onCameraSwitchDone(boolean isFrontCamera) {
                        isSwitch = false;
                    }

                    @Override
                    public void onCameraSwitchError(String errorDescription) {
                        isSwitch = false;
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            Log.d(TAG, "Will not switch camera, video caputurer is not a camera");
        }

    }

    private void release() {
        networkMonitor.removeObserver(this);
        executor.execute(() -> {
            if (audioManager != null) {
                audioManager.setMode(AudioManager.MODE_NORMAL);
            }
            // audio release
            if (audioSource != null) {
                audioSource.dispose();
                audioSource = null;
            }
            // video release
            if (videoSource != null) {
                videoSource.dispose();
                videoSource = null;
            }
            // release camera
            if (captureAndroid != null) {
                try {
                    captureAndroid.stopCapture();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                captureAndroid.dispose();
                captureAndroid = null;
            }
            if (_localStream!=null)_localStream.dispose();
            if (_remoteStream!=null)_remoteStream.dispose();
            // close peer
            if (mPeer != null && mPeer.pc != null) {
                mPeer.pc.close();
            }

            // Release canvas
            if (surfaceTextureHelper != null) {
                surfaceTextureHelper.dispose();
                surfaceTextureHelper = null;
            }
            // Release factory
            if (_factory != null) {
                _factory.dispose();
                _factory = null;
            }
            // Status is set Idle
            _callState = EnumType.CallState.Idle;

            //Interface callback
            if (sessionCallback.get() != null) {
                sessionCallback.get().didCallEndWithReason(null);
            }
        });
    }

    //------------------------------------receive---------------------------------------------------

    // Joined room successfully
    public void onJoinHome(String myId, String users) {
        startTime = 0;
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        networkMonitor.addObserver(this);
        executor.execute(() -> {
            mMyId = myId;
            // todo Multi-person conference
            if (!TextUtils.isEmpty(users)) {
                String[] split = users.split(",");
                List<String> strings = Arrays.asList(split);
                mTargetId = strings.get(0);
            }
            if (_factory == null) {
                _factory = createConnectionFactory();
            }
            if (_localStream == null) {
                createLocalStream();
            }
            if (mIsComing) {
                // Answering the call
                _role = Role.Caller;
                // Create peer
                mPeer = new Peer(mTargetId);
                // Adding a local stream
                mPeer.pc.addStream(_localStream);
                // Create offer
                mPeer.createOffer();

                // Turn off the bell
                if (avEngineKit.mEvent != null) {
                    avEngineKit.mEvent.shouldStopRing();
                }
                // Change interface
                _callState = EnumType.CallState.Connected;

                if (sessionCallback.get() != null) {
                    sessionCallback.get().didChangeState(_callState);
                    startTime = System.currentTimeMillis();
                }
            } else {
                avEngineKit.mEvent.sendInvite(mRoom, mTargetId, mIsAudioOnly);
            }

            // Start showing local screen
            if (!isAudioOnly()) {
                // Test video, turn off speech to prevent noise
//                if (BuildConfig.DEBUG) {
//                    muteAudio(false);
//                }
                if (sessionCallback.get() != null) {
                    sessionCallback.get().didCreateLocalVideoTrack();
                }

            }


        });
    }

    // New members enter
    public void newPeer(String userId) {
        executor.execute(() -> {
            if (_localStream == null) {
                createLocalStream();
            }
            try {
                mPeer = new Peer(userId);
                mPeer.pc.addStream(_localStream);
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
            // Turn off the bell
            if (avEngineKit.mEvent != null) {
                avEngineKit.mEvent.shouldStopRing();
            }

            // Switch interface
            _callState = EnumType.CallState.Connected;
            if (sessionCallback.get() != null) {
                sessionCallback.get().didChangeState(EnumType.CallState.Connected);
                startTime = System.currentTimeMillis();
            }

        });
    }

    // The other party has rejected
    public void onRefuse(String userId) {
        release();
    }

    // The other party has rang
    public void onRingBack(String userId) {
        if (avEngineKit.mEvent != null) {
            avEngineKit.mEvent.shouldStartRing(false);
        }
    }

    public void onReceiveOffer(String userId, String description) {
        executor.execute(() -> {
            _role = Role.Receiver;
            SessionDescription sdp = new SessionDescription(SessionDescription.Type.OFFER, description);
            if (mPeer != null) {
                mPeer.pc.setRemoteDescription(mPeer, sdp);
                if (_role == Role.Receiver) {
                    mPeer.createAnswer();
                }
            }


        });

    }

    public void onReceiverAnswer(String userId, String sdp) {
        Log.e("dds_test", "onReceiverAnswer:" + userId);
        executor.execute(() -> {
            SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
            if (mPeer != null && mPeer.pc != null) {
                mPeer.pc.setRemoteDescription(mPeer, sessionDescription);
            }
        });

    }

    public void onRemoteIceCandidate(String userId, String id, int label, String candidate) {
        executor.execute(() -> {
            if (mPeer != null && mPeer.pc != null) {
                IceCandidate iceCandidate = new IceCandidate(id, label, candidate);
                mPeer.addRemoteIceCandidate(iceCandidate);

            }
        });

    }

    // the other leave the room
    public void onLeave(String userId) {
        release();
    }


    // Each Session can contain multiple PeerConnection
    private class Peer implements SdpObserver, PeerConnection.Observer {
        private PeerConnection pc;
        private String userId;
        private List<IceCandidate> queuedRemoteCandidates;
        private SessionDescription localSdp;

        public Peer(String userId) {
            this.pc = createPeerConnection();
            this.userId = userId;
            queuedRemoteCandidates = new ArrayList<>();
        }

        private PeerConnection createPeerConnection() {
            // Pipeline connection abstract class implementation method
            PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(avEngineKit.getIceServers());
            return _factory.createPeerConnection(rtcConfig, this);
        }

        // Create offer
        private void createOffer() {
            if (pc == null) return;
            pc.createOffer(this, offerOrAnswerConstraint());
        }

        // Create answer
        private void createAnswer() {
            if (pc == null) return;
            pc.createAnswer(this, offerOrAnswerConstraint());

        }

        private void addRemoteIceCandidate(final IceCandidate candidate) {
            if (pc != null) {
                if (queuedRemoteCandidates != null) {
                    queuedRemoteCandidates.add(candidate);
                } else {
                    pc.addIceCandidate(candidate);
                }
            }
        }

        public void removeRemoteIceCandidates(final IceCandidate[] candidates) {
            if (pc == null) {
                return;
            }
            drainCandidates();
            pc.removeIceCandidates(candidates);
        }


        private void drainCandidates() {
            if (queuedRemoteCandidates != null) {
                Log.d(TAG, "Add " + queuedRemoteCandidates.size() + " remote candidates");
                for (IceCandidate candidate : queuedRemoteCandidates) {
                    pc.addIceCandidate(candidate);
                }
                queuedRemoteCandidates = null;
            }
        }

        //-------------Observer--------------------
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.i(TAG, "onSignalingChange: " + signalingState);
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
            Log.i(TAG, "onIceConnectionChange: " + newState.toString());
            if (_callState != EnumType.CallState.Connected) return;
            if (newState == PeerConnection.IceConnectionState.DISCONNECTED) {
//                createOffer();
            }
        }

        @Override
        public void onIceConnectionReceivingChange(boolean receiving) {
            Log.i(TAG, "onIceConnectionReceivingChange:" + receiving);
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
            Log.i(TAG, "onIceGatheringChange:" + newState.toString());
        }


        @Override
        public void onIceCandidate(IceCandidate candidate) {
            Log.i(TAG, "onIceCandidate:");
            // 发送IceCandidate
            executor.execute(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                avEngineKit.mEvent.sendIceCandidate(userId, candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp);
            });


        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] candidates) {
            Log.i(TAG, "onIceCandidatesRemoved:");
        }

        @Override
        public void onAddStream(MediaStream stream) {
            _remoteStream = stream;
            Log.i(TAG, "onAddStream:");
            if (stream.audioTracks.size() > 0) {
                stream.audioTracks.get(0).setEnabled(true);
            }
            if (sessionCallback.get() != null) {
                sessionCallback.get().didReceiveRemoteVideoTrack();
            }
        }

        @Override
        public void onRemoveStream(MediaStream stream) {
            Log.i(TAG, "onRemoveStream:");
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
            Log.i(TAG, "onDataChannel:");
        }

        @Override
        public void onRenegotiationNeeded() {
            Log.i(TAG, "onRenegotiationNeeded:");
        }

        @Override
        public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
            Log.i(TAG, "onAddTrack:");
        }


        //-------------SdpObserver--------------------
        @Override
        public void onCreateSuccess(SessionDescription origSdp) {
            Log.d(TAG, "sdp创建成功       " + origSdp.type);
            String sdpString = origSdp.description;
            final SessionDescription sdp = new SessionDescription(origSdp.type, sdpString);
            localSdp = sdp;
            executor.execute(() -> pc.setLocalDescription(Peer.this, sdp));
        }

        @Override
        public void onSetSuccess() {
            executor.execute(() -> {
                Log.d(TAG, "sdp连接成功   " + pc.signalingState().toString());
                if (pc == null) return;

                // 发送者
                if (_role == Role.Caller) {
                    if (pc.getRemoteDescription() == null) {
                        Log.d(TAG, "Local SDP set succesfully");
                        if (_role == Role.Receiver) {
                            //Receiver, Send Answer
                            avEngineKit.mEvent.sendAnswer(userId, localSdp.description);
                        } else if (_role == Role.Caller) {
                            //Sender, send offer
                            avEngineKit.mEvent.sendOffer(userId, localSdp.description);
                        }
                    } else {
                        Log.d(TAG, "Remote SDP set succesfully");

                        drainCandidates();
                    }

                } else {
                    if (pc.getLocalDescription() != null) {
                        Log.d(TAG, "Local SDP set succesfully");
                        if (_role == Role.Receiver) {
                            //Receiver, Send Answer
                            avEngineKit.mEvent.sendAnswer(userId, localSdp.description);
                        } else if (_role == Role.Caller) {
                            //Sender, send offer
                            avEngineKit.mEvent.sendOffer(userId, localSdp.description);
                        }

                        drainCandidates();
                    } else {
                        Log.d(TAG, "Remote SDP set succesfully");
                    }
                }
            });


        }

        @Override
        public void onCreateFailure(String error) {
            Log.i(TAG, " SdpObserver onCreateFailure:" + error);
        }

        @Override
        public void onSetFailure(String error) {
            Log.i(TAG, "SdpObserver onSetFailure:" + error);
        }
    }

    @Override
    public void onConnectionTypeChanged(NetworkMonitorAutoDetect.ConnectionType connectionType) {
        Log.e(TAG, "onConnectionTypeChanged" + connectionType.toString());
    }
    // --------------------------------界面显示相关-------------------------------------------------

    public long getStartTime() {
        return startTime;
    }

    public SurfaceViewRenderer createRendererView() {
        SurfaceViewRenderer renderer = new SurfaceViewRenderer(mContext);
        renderer.init(mRootEglBase.getEglBaseContext(), null);
        renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        //make it true, it reflects mirroring effect, but false is normal for back camera
        // to render camera frame correct without mirroring issue, put true for front camera
        // but for back camera false
        renderer.setMirror(true);
        return renderer;
    }

    public void setupRemoteVideo(SurfaceViewRenderer surfaceView) {
        ProxyVideoSink sink = new ProxyVideoSink();
        sink.setTarget(surfaceView);
        if (_remoteStream != null && _remoteStream.videoTracks.size() > 0) {
            _remoteStream.videoTracks.get(0).addSink(sink);
        }
    }

    public void setupLocalVideo(SurfaceViewRenderer SurfaceViewRenderer) {
        ProxyVideoSink sink = new ProxyVideoSink();
        sink.setTarget(SurfaceViewRenderer);
        if (_localStream.videoTracks.size() > 0) {
            _localStream.videoTracks.get(0).addSink(sink);
        }
    }

    //------------------------------------Various initializations---------------------------------------------

    public void createLocalStream() {
        _localStream = _factory.createLocalMediaStream("ARDAMS");
        // Audio
        audioSource = _factory.createAudioSource(createAudioConstraints());
        _localAudioTrack = _factory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
        _localStream.addTrack(_localAudioTrack);

        // Video
        if (!mIsAudioOnly) {
            captureAndroid = createVideoCapture();
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", mRootEglBase.getEglBaseContext());
            videoSource = _factory.createVideoSource(captureAndroid.isScreencast());
            captureAndroid.initialize(surfaceTextureHelper, mContext, videoSource.getCapturerObserver());
            captureAndroid.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS);
            _localVideoTrack = _factory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
            _localStream.addTrack(_localVideoTrack);
        }

    }

    public PeerConnectionFactory createConnectionFactory() {
        PeerConnectionFactory.initialize(PeerConnectionFactory
                .InitializationOptions
                .builder(mContext)
                .createInitializationOptions());

        final VideoEncoderFactory encoderFactory;
        final VideoDecoderFactory decoderFactory;

        encoderFactory = new DefaultVideoEncoderFactory(
                mRootEglBase.getEglBaseContext(),
                true,
                true);
        decoderFactory = new DefaultVideoDecoderFactory(mRootEglBase.getEglBaseContext());
        audioDeviceModule = JavaAudioDeviceModule.builder(mContext).createAudioDeviceModule();
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        return PeerConnectionFactory.builder()
                .setOptions(options)
                .setAudioDeviceModule(audioDeviceModule)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();
    }

    private SurfaceTextureHelper surfaceTextureHelper;

    private VideoCapturer createVideoCapture() {
        VideoCapturer videoCapturer;
        if (useCamera2()) {
            videoCapturer = createCameraCapture(new Camera2Enumerator(mContext));
        } else {
            videoCapturer = createCameraCapture(new Camera1Enumerator(true));
        }
        return videoCapturer;
    }

    private VideoCapturer createCameraCapture(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, eventsHandler);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, eventsHandler);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    private boolean useCamera2() {
        return Camera2Enumerator.isSupported(mContext);
    }

    // added by Murodjon. 2020.03.11
    CameraVideoCapturer.CameraEventsHandler eventsHandler = new CameraVideoCapturer.CameraEventsHandler() {
        @Override
        public void onCameraError(String errorDescription) {
            Logging.d(TAG, "onCameraError");
        }

        @Override
        public void onCameraDisconnected() {
            Logging.d(TAG, "onCameraDisconnected");
        }

        @Override
        public void onCameraFreezed(String errorDescription) {
            Logging.d(TAG, "onCameraFreezed");
        }

        @Override
        public void onCameraOpening(String cameraName) {
            Logging.d(TAG, "onCameraOpening");
            FrameProcessingTask.getInstance();
        }

        @Override
        public void onFirstFrameAvailable() {
            Logging.d(TAG, "onFirstFrameAvailable");
        }

        @Override
        public void onCameraClosed() {
            Logging.d(TAG, "onCameraClosed");
            FrameProcessingTask.getInstance().release();
        }

        @Override
        public void onProcessingFrame(int width, int height, int rotation, int cameraFacing, byte[] data) {
            Logging.d(TAG, "onProcessingFrame");
            FrameProcessingTask.getInstance()
                    .setCameraFacing(cameraFacing)
                    .setWidth(width)
                    .setHeight(height)
                    .setRotation(rotation)
                    .processFrame(data);
        }
    };

    //**************************************Various constraints******************************************/
    private static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";
    private static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl";
    private static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter";
    private static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";

    private MediaConstraints createAudioConstraints() {
        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "true"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "true"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "true"));
        return audioConstraints;
    }

    private MediaConstraints offerOrAnswerConstraint() {
        MediaConstraints mediaConstraints = new MediaConstraints();
        ArrayList<MediaConstraints.KeyValuePair> keyValuePairs = new ArrayList<>();
        keyValuePairs.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        keyValuePairs.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        mediaConstraints.mandatory.addAll(keyValuePairs);
        return mediaConstraints;
    }

    // ***********************************Various parameters******************************************/
    public void setIsAudioOnly(boolean _isAudioOnly) {
        this.mIsAudioOnly = _isAudioOnly;
    }

    public boolean isAudioOnly() {
        return mIsAudioOnly;
    }

    public void setTargetId(String targetIds) {
        this.mTargetId = targetIds;
    }

    public void setContext(Context context) {
        if (context instanceof Application) {
            this.mContext = context;
        } else {
            this.mContext = context.getApplicationContext();
        }

    }

    public void setIsComing(boolean isComing) {
        this.mIsComing = isComing;
    }

    public void setRoom(String _room) {
        this.mRoom = _room;
    }

    public EnumType.CallState getState() {
        return _callState;
    }

    public void setCallState(EnumType.CallState callState) {
        this._callState = callState;
    }

    public void setSessionCallback(CallSessionCallback sessionCallback) {
        this.sessionCallback = new WeakReference<>(sessionCallback);
    }

    public interface CallSessionCallback {
        void didCallEndWithReason(EnumType.CallEndReason var1);

        void didChangeState(EnumType.CallState var1);

        void didChangeMode(boolean isAudio);

        void didCreateLocalVideoTrack();

        void didReceiveRemoteVideoTrack();

        void didError(String error);

    }
}
