package io.jenkins.plugins.jobfailure;

import hudson.Extension;
import hudson.ExtensionList;
import jenkins.model.GlobalConfiguration;
import org.kohsuke.stapler.DataBoundSetter;

/** Global settings for failure tracking and notifications. */
@Extension
public class JobFailuresConfiguration extends GlobalConfiguration {
    private int recentBuildCount = 5;
    private boolean emailNotificationsEnabled;
    private String emailRecipients = "";
    private String emailSchedule = "H 8 * * *";
    private long lastEmailNotificationMinute;

    public JobFailuresConfiguration() {
        load();
    }

    public static JobFailuresConfiguration get() {
        return ExtensionList.lookupSingleton(JobFailuresConfiguration.class);
    }

    public int getRecentBuildCount() {
        return Math.max(1, recentBuildCount);
    }

    @DataBoundSetter
    public void setRecentBuildCount(int recentBuildCount) {
        this.recentBuildCount = Math.max(1, recentBuildCount);
        save();
    }

    public boolean isEmailNotificationsEnabled() {
        return emailNotificationsEnabled;
    }

    @DataBoundSetter
    public void setEmailNotificationsEnabled(boolean emailNotificationsEnabled) {
        this.emailNotificationsEnabled = emailNotificationsEnabled;
        save();
    }

    public String getEmailSchedule() {
        return emailSchedule == null || emailSchedule.isBlank() ? "H 8 * * *" : emailSchedule;
    }

    @DataBoundSetter
    public void setEmailSchedule(String emailSchedule) {
        this.emailSchedule = emailSchedule == null || emailSchedule.isBlank() ? "H 8 * * *" : emailSchedule.trim();
        save();
    }

    public long getLastEmailNotificationMinute() {
        return lastEmailNotificationMinute;
    }

    public void setLastEmailNotificationMinute(long minute) {
        this.lastEmailNotificationMinute = minute;
        save();
    }

    public String getEmailRecipients() {
        return emailRecipients == null ? "" : emailRecipients;
    }

    @DataBoundSetter
    public void setEmailRecipients(String emailRecipients) {
        this.emailRecipients = emailRecipients == null ? "" : emailRecipients.trim();
        save();
    }
}
