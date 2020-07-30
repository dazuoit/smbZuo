package com.dazuo.smb.bean;

import java.io.Serializable;

/**
 * Created by hudq on 2016/12/14.
 */

public class DeviceInfo implements Serializable {

    private String name;
    private String ip;

    public DeviceInfo(String name, String ip) {
        this.name = name;
        this.ip = ip;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }


}
