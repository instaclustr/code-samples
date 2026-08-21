package com.example.a2a.kafka.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/** A2A Agent Card: agent identity, capabilities, and endpoint. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentCard {
    private String name;
    private String description;
    private String url;
    private String version;
    private List<String> defaultInputModes;
    private List<String> defaultOutputModes;
    private List<AgentSkill> skills;
    private AgentCapabilities capabilities;
    private Map<String, Object> securitySchemes;
    private List<Map<String, List<String>>> security;
    private String preferredTransport;
    private String iconUrl;
    private String documentationUrl;
    private AgentProvider provider;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public List<String> getDefaultInputModes() { return defaultInputModes; }
    public void setDefaultInputModes(List<String> defaultInputModes) { this.defaultInputModes = defaultInputModes; }
    public List<String> getDefaultOutputModes() { return defaultOutputModes; }
    public void setDefaultOutputModes(List<String> defaultOutputModes) { this.defaultOutputModes = defaultOutputModes; }
    public List<AgentSkill> getSkills() { return skills; }
    public void setSkills(List<AgentSkill> skills) { this.skills = skills; }
    public AgentCapabilities getCapabilities() { return capabilities; }
    public void setCapabilities(AgentCapabilities capabilities) { this.capabilities = capabilities; }
    public Map<String, Object> getSecuritySchemes() { return securitySchemes; }
    public void setSecuritySchemes(Map<String, Object> securitySchemes) { this.securitySchemes = securitySchemes; }
    public List<Map<String, List<String>>> getSecurity() { return security; }
    public void setSecurity(List<Map<String, List<String>>> security) { this.security = security; }
    public String getPreferredTransport() { return preferredTransport; }
    public void setPreferredTransport(String preferredTransport) { this.preferredTransport = preferredTransport; }
    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }
    public String getDocumentationUrl() { return documentationUrl; }
    public void setDocumentationUrl(String documentationUrl) { this.documentationUrl = documentationUrl; }
    public AgentProvider getProvider() { return provider; }
    public void setProvider(AgentProvider provider) { this.provider = provider; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AgentSkill {
        private String id;
        private String name;
        private String description;
        private List<String> tags;
        private List<String> examples;
        private List<String> inputModes;
        private List<String> outputModes;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
        public List<String> getExamples() { return examples; }
        public void setExamples(List<String> examples) { this.examples = examples; }
        public List<String> getInputModes() { return inputModes; }
        public void setInputModes(List<String> inputModes) { this.inputModes = inputModes; }
        public List<String> getOutputModes() { return outputModes; }
        public void setOutputModes(List<String> outputModes) { this.outputModes = outputModes; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AgentCapabilities {
        private Boolean streaming;
        private Boolean pushNotifications;
        private Boolean stateTransitionHistory;

        public Boolean getStreaming() { return streaming; }
        public void setStreaming(Boolean streaming) { this.streaming = streaming; }
        public Boolean getPushNotifications() { return pushNotifications; }
        public void setPushNotifications(Boolean pushNotifications) { this.pushNotifications = pushNotifications; }
        public Boolean getStateTransitionHistory() { return stateTransitionHistory; }
        public void setStateTransitionHistory(Boolean stateTransitionHistory) { this.stateTransitionHistory = stateTransitionHistory; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AgentProvider {
        private String organization;
        private String url;

        public String getOrganization() { return organization; }
        public void setOrganization(String organization) { this.organization = organization; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }
}
