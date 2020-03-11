package com.dds.skywebrtc.inter;

/**
 * Created by dds on 2019/8/21.
 * android_shuai@163.com
 */
public interface ISkyEvent {

    // create room
    void createRoom(String room, int roomSize);

    // Send single invitation
    void sendInvite(String room, String userId, boolean audioOnly);

    // Initiate a meeting invitation
    void sendMeetingInvite(String userList);

    void sendRefuse(String inviteId, int refuseType);

    void sendCancel(String toId);

    void sendJoin(String room);

    void sendRingBack(String targetId);

    void sendLeave(String room, String userId);

    // sendOffer
    void sendOffer(String userId, String sdp);

    // sendAnswer
    void sendAnswer(String userId, String sdp);

    // sendIceCandidate
    void sendIceCandidate(String userId, String id, int label, String candidate);


    void shouldStartRing(boolean isComing);

    void shouldStopRing();


}
