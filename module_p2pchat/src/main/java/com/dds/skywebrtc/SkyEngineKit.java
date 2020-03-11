package com.dds.skywebrtc;

import android.content.Context;
import android.util.Log;

import com.dds.skywebrtc.except.NotInitializedException;
import com.dds.skywebrtc.inter.ISkyEvent;

import org.webrtc.PeerConnection;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by dds on 2019/8/19.
 */
public class SkyEngineKit {
    private final static String TAG = "dds_AVEngineKit";
    private static SkyEngineKit avEngineKit;
    private CallSession mCurrentCallSession;
    public ISkyEvent mEvent;
    private List<PeerConnection.IceServer> iceServers = new ArrayList<>();

    public static SkyEngineKit Instance() {
        SkyEngineKit var;
        if ((var = avEngineKit) != null) {
            return var;
        } else {
            throw new NotInitializedException();
        }
    }

    // initialization
    public static void init(ISkyEvent iSocketEvent) {
        if (avEngineKit == null) {
            avEngineKit = new SkyEngineKit();
            avEngineKit.mEvent = iSocketEvent;

            // Initialize some stun and turn addresses
            PeerConnection.IceServer var1 = PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                    .createIceServer();
            avEngineKit.iceServers.add(var1);

            PeerConnection.IceServer var11 = PeerConnection.IceServer.builder("stun:47.93.186.97:3478?transport=udp")
                    .createIceServer();
            avEngineKit.iceServers.add(var11);

            PeerConnection.IceServer var12 = PeerConnection.IceServer.builder("turn:47.93.186.97:3478?transport=udp")
                    .setUsername("ddssingsong")
                    .setPassword("123456")
                    .createIceServer();
            avEngineKit.iceServers.add(var12);

            PeerConnection.IceServer var13 = PeerConnection.IceServer.builder("turn:47.93.186.97:3478?transport=tcp")
                    .setUsername("ddssingsong")
                    .setPassword("123456")
                    .createIceServer();
            avEngineKit.iceServers.add(var13);
        }
    }


    // dial number
    public boolean startOutCall(Context context,
                                final String room,
                                final String targetId,
                                final boolean audioOnly) {
        // Uninitialized
        if (avEngineKit == null) {
            Log.e(TAG, "startOutCall error,please init first");
            return false;
        }
        // Busy
        if (mCurrentCallSession != null && mCurrentCallSession.getState() != EnumType.CallState.Idle) {
            Log.i(TAG, "startCall error,currentCallSession is exist");
            return false;
        }
        // init session
        mCurrentCallSession = new CallSession(avEngineKit, context, audioOnly);
        mCurrentCallSession.setContext(context);
        mCurrentCallSession.setIsAudioOnly(audioOnly);
        mCurrentCallSession.setRoom(room);
        mCurrentCallSession.setTargetId(targetId);
        mCurrentCallSession.setIsComing(false);
        mCurrentCallSession.setCallState(EnumType.CallState.Outgoing);
        // create room
        mCurrentCallSession.createHome(room, 2);
        return true;
    }

    // answer the phone
    public boolean startInCall(Context context,
                               final String room,
                               final String targetId,
                               final boolean audioOnly) {
        if (avEngineKit == null) {
            Log.e(TAG, "startInCall error,init is not set");
            return false;
        }
        // Busy
        if (mCurrentCallSession != null && mCurrentCallSession.getState() != EnumType.CallState.Idle) {
            if (mEvent != null) {
                // sending busy...
                Log.i(TAG, "startInCall busy,currentCallSession is exist,start sendRefuse!");
                mEvent.sendRefuse(targetId, EnumType.RefuseType.Busy.ordinal());
            }
            return false;
        }
        // init session
        mCurrentCallSession = new CallSession(avEngineKit, context, audioOnly);
        mCurrentCallSession.setIsAudioOnly(audioOnly);
        mCurrentCallSession.setRoom(room);
        mCurrentCallSession.setTargetId(targetId);
        mCurrentCallSession.setContext(context);
        mCurrentCallSession.setIsComing(true);
        mCurrentCallSession.setCallState(EnumType.CallState.Incoming);

        // Start ringing and reply
        mCurrentCallSession.shouldStartRing();
        mCurrentCallSession.sendRingBack(targetId);


        return true;
    }

    // Hang up the session
    public void endCall() {
        if (mCurrentCallSession != null) {
            // stop ringing
            mCurrentCallSession.shouldStopRing();

            if (mCurrentCallSession.mIsComing) {
                if (mCurrentCallSession.getState() == EnumType.CallState.Incoming) {
                    // Received invitation, did not agree, send rejection
                    mCurrentCallSession.sendRefuse();
                } else {
                    // Already connected, hang up
                    mCurrentCallSession.leave();
                }
            } else {
                if (mCurrentCallSession.getState() == EnumType.CallState.Outgoing) {
                    mCurrentCallSession.sendCancel();
                } else {
                    // Already connected, hang up
                    mCurrentCallSession.leave();
                }
            }
            mCurrentCallSession.setCallState(EnumType.CallState.Idle);
        }
    }

    // Get conversation instance
    public CallSession getCurrentSession() {
        return this.mCurrentCallSession;
    }

    // --------------------------------iceServers------------------------------------

    // Add turn and stun
    public void addIceServer(String host, String username, String pwd) {
        SkyEngineKit var = this;
        PeerConnection.IceServer var4 = PeerConnection.IceServer.builder(host)
                .setUsername(username)
                .setPassword(pwd)
                .createIceServer();
        var.iceServers.add(var4);
    }

    public List<PeerConnection.IceServer> getIceServers() {
        return iceServers;
    }


}
