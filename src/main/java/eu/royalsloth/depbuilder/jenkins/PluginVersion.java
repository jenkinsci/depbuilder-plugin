package eu.royalsloth.depbuilder.jenkins;

/**
 * Determines which version of the plugin the user is using.
 */
public enum PluginVersion {
    PRO,
    COMMUNITY;

    /**
     * A global variable that determines the version of the plugin
     */
    public static volatile PluginVersion type = COMMUNITY;

    /**
     * Defines the version of the plugin
     */
    public static final String version = getPluginVersion();

    public static String getPluginVersion() {
        try {
            String version = JenkinsUtil.getPluginVersion("depbuilder");
            return version;
        } catch (Exception e) {
            return "";
        }
    }

    public static void setCommunity() {
        type = COMMUNITY;
    }

    public static void setPro() {
        type = PRO;
    }

    public static boolean isCommunity() {
        boolean isCommunity = type == COMMUNITY;
        return isCommunity;
    }

    public static boolean isPro() {
        boolean isEnterprise = type == PRO;
        return isEnterprise;
    }
}
