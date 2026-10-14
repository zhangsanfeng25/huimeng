package com.huimeng.bookshop.core.local;
import org.json.JSONObject;
public final class LocalOperation {
    public final String operationId,spaceId,resource,entityId,action,state;
    public final JSONObject payload;
    public final long baseVersion,nextAttemptAt;
    public final int attempts;
    public LocalOperation(JSONObject value){operationId=value.optString("operationId");spaceId=value.optString("spaceId");resource=value.optString("resource");entityId=value.optString("entityId");action=value.optString("action");state=value.optString("state");payload=value.optJSONObject("payload");baseVersion=value.optLong("baseVersion");attempts=value.optInt("attempts");nextAttemptAt=value.optLong("nextAttemptAt");}
}
