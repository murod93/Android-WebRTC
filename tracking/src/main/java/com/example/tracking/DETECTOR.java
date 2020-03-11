package com.example.tracking;

public enum DETECTOR{
    FACE(1),
    OBJECT(2),
    NONE(0);
    int type;
    DETECTOR(int type){
        this.type = type;
    }
}
