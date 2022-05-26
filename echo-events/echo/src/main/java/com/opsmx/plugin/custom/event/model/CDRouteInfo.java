package com.opsmx.plugin.custom.event.model;

import com.opsmx.plugin.custom.event.enums.Action;
import com.opsmx.plugin.custom.event.enums.CDType;

public class CDRouteInfo {

    private CDType cdType;
    private Spinnaker cdTool;
    private Action action;

    public CDType getCdType() {
        return cdType;
    }

    public void setCdType(CDType cdType) {
        this.cdType = cdType;
    }

    public Spinnaker getCdTool() {
        return cdTool;
    }

    public void setCdTool(Spinnaker cdTool) {
        this.cdTool = cdTool;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    @Override
    public String toString() {
        return "CDRouteInfo{" +
                "cdType=" + cdType +
                ", cdTool=" + cdTool +
                ", action=" + action +
                '}';
    }
}
