package com.opsmx.plugin.custom.event.model;

import java.util.Objects;


public class Spinnaker{

    private String name;
    private String hostUrl;

    public Spinnaker(){

    }

    public Spinnaker(String name, String hostUrl) {
        this.name = name;
        this.hostUrl = hostUrl;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHostUrl() {
        return hostUrl;
    }

    public void setHostUrl(String hostUrl) {
        this.hostUrl = hostUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Spinnaker spinnaker = (Spinnaker) o;
        return name.equals(spinnaker.name) && hostUrl.equals(spinnaker.hostUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, hostUrl);
    }
}
