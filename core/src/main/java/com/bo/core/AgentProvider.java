package com.bo.core;
import org.json.JSONObject;
/** Implement in the host app for a local model or a server-side agent. Runs off the UI thread. */
public interface AgentProvider {
    JSONObject analyze(String task, JSONObject snapshot) throws Exception;
}
