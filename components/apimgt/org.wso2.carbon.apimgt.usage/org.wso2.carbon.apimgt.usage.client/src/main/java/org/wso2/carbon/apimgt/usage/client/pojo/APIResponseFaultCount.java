package org.wso2.carbon.apimgt.usage.client.pojo;

/**
 * Created by rukshan on 10/9/15.
 */
public class APIResponseFaultCount {
    private String apiName;
    private String apiVersion;
    private String context;

    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public long getFaultCount() {
        return faultCount;
    }

    public void setFaultCount(long faultCount) {
        this.faultCount = faultCount;
    }

    private long faultCount;
}
